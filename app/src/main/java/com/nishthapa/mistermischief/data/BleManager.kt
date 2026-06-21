package com.nishthapa.mistermischief.data

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import com.nishthapa.mistermischief.core.ConnectionStatus
import com.nishthapa.mistermischief.core.RobotConnection
import com.nishthapa.mistermischief.domain.TeleopCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) : RobotConnection {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    private val _connectionState = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionState = _connectionState.asStateFlow()

    // 1. Add the telemetry state and parser at the top of the class
    private val _telemetryState = MutableStateFlow(com.nishthapa.mistermischief.domain.RobotTelemetry())
    override val telemetryState = _telemetryState.asStateFlow()
    private val telemetryParser = BinaryTelemetryParser(_telemetryState)

    // Default custom serial UUID matching standard ESP32 BLE UART configurations
    // 2. Add the CCCD UUID (required to enable BLE notifications)
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val CHAR_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    // Add the new UUID for requesting/accepting manual driving token
    private val TOKEN_CHAR_UUID = UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb")
    private var tokenCharacteristic: BluetoothGattCharacteristic? = null

    /* * IMPORTANT: Inside your existing BluetoothGattCallback's onServicesDiscovered,
     * make sure to capture the token characteristic like this:
     * tokenCharacteristic = service.getCharacteristic(TOKEN_CHAR_UUID)
     */

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = ConnectionStatus.CONNECTED
                Log.d("BLE_ENGINE", "Connected. Requesting 512 Byte MTU...")

                // 🚨 CRITICAL FIX: Request large MTU so packets aren't chopped!
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    gatt.requestMtu(512)
                } else {
                    gatt.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ConnectionStatus.DISCONNECTED
                bluetoothGatt?.close()
                bluetoothGatt = null
                writeCharacteristic = null
                tokenCharacteristic = null
            }
        }

        // Wait for the MTU to expand before opening the channels!
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d("BLE_ENGINE", "MTU successfully expanded to $mtu bytes. Discovering services...")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    // 1. Bind the high-speed joystick channel
                    writeCharacteristic = service.getCharacteristic(CHAR_UUID)

                    // 2. Bind the Token for manual control Authority channel!
                    tokenCharacteristic = service.getCharacteristic(TOKEN_CHAR_UUID)
                    Log.d("BLE_ENGINE", "Mister Mischief Control Channel Active.")
                    // --- NEW: Enable Telemetry Notifications! ---
                    val notifyChar = service.getCharacteristic(CHAR_UUID)
                    if (notifyChar != null) {
                        gatt.setCharacteristicNotification(notifyChar, true)
                        val descriptor = notifyChar.getDescriptor(CCCD_UUID)
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }
                    }
                }
            }
        }

        // 🚨 MOVED INSIDE THE CALLBACK: For older Android versions
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == CHAR_UUID) {
                characteristic.value?.let { telemetryParser.processIncomingBytes(it) }
            }
        }

        // 🚨 MOVED INSIDE THE CALLBACK: For Android 13+
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == CHAR_UUID) {
                telemetryParser.processIncomingBytes(value)
            }
        }
    }

    override fun connect(deviceAddress: String) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _connectionState.value = ConnectionStatus.ERROR
            return
        }
//
        try {
            _connectionState.value = ConnectionStatus.CONNECTING

            // Normalize the MAC address (Uppercase is required by Android)
            val cleanAddress = deviceAddress.trim().uppercase()
            val device = bluetoothAdapter.getRemoteDevice(cleanAddress)

            // Log the attempt so you can see it in Logcat
            Log.d("BLE_ENGINE", "Attempting connection to: $cleanAddress")

            bluetoothGatt = device.connectGatt(context, false, gattCallback)

        } catch (e: IllegalArgumentException) {
            Log.e("BLE_ENGINE", "Invalid MAC Address format: $deviceAddress")
            _connectionState.value = ConnectionStatus.ERROR
        } catch (e: SecurityException) {
            Log.e("BLE_ENGINE", "Permissions missing!")
            _connectionState.value = ConnectionStatus.ERROR
        }
    }

    // NEW: Implement the Token Authority Write
    override fun sendControlToken(claim: Boolean) {
        if (_connectionState.value != ConnectionStatus.CONNECTED) return
        val characteristic = tokenCharacteristic ?: return

        // 1 means Claim Token, 0 means Release Token
        val payload = byteArrayOf(if (claim) 1.toByte() else 0.toByte())

        // Tokens are critical state changes, so we DO want a hardware response/acknowledgment
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt?.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            bluetoothGatt?.writeCharacteristic(characteristic)
        }
    }

    override fun disconnect() {
        bluetoothGatt?.disconnect()
        _connectionState.value = ConnectionStatus.DISCONNECTED
        bluetoothGatt?.close()
        bluetoothGatt = null
        writeCharacteristic = null
        tokenCharacteristic = null
    }

    override fun sendCommand(command: TeleopCommand) {
        if (_connectionState.value != ConnectionStatus.CONNECTED) return
        val characteristic = writeCharacteristic ?: return
        val payload = command.toByteArray()

        // Configures asynchronous fire-and-forget packets to bypass transmission latency
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt?.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            )
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            bluetoothGatt?.writeCharacteristic(characteristic)
        }
    }
}