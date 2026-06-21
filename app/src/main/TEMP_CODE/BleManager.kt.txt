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

    // Default custom serial UUID matching standard ESP32 BLE UART configurations
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
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    _connectionState.value = ConnectionStatus.CONNECTED
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _connectionState.value = ConnectionStatus.DISCONNECTED
                    gatt.close()
                }
            } else {
                _connectionState.value = ConnectionStatus.ERROR
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    writeCharacteristic = service.getCharacteristic(CHAR_UUID)
                    Log.d("BLE_ENGINE", "Mister Mischief Control Channel Active.")
                }
            }
        }
    }

    override fun connect(deviceAddress: String) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _connectionState.value = ConnectionStatus.ERROR
            return
        }

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