package com.nishthapa.mistermischief.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class RenderComponent(val zDepth: Float, val drawCommand: () -> Unit)

@Composable
fun RobotVisualizer(
    pitchDeg: Float,
    rollDeg: Float,
    yawDeg: Float,
    yawOffset: Float, // Ensure this parameter exists
    sonarDistanceCm: Float,
    leftPwm: Int,
    rightPwm: Int,
    modifier: Modifier = Modifier
) {
    val currentLeftPwm by rememberUpdatedState(leftPwm)
    val currentRightPwm by rememberUpdatedState(rightPwm)

    var leftPhase by remember { mutableFloatStateOf(0f) }
    var rightPhase by remember { mutableFloatStateOf(0f) }

    // Calculate inverted, relative values
    val relativePitch = -pitchDeg
    val relativeRoll = -rollDeg
    val relativeYaw = -yawDeg + yawOffset

    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(Unit) {
        var lastTime = withFrameNanos { it }
        while (true) {
            val currentTime = withFrameNanos { it }
            val dt = (currentTime - lastTime) / 1_000_000_000f
            lastTime = currentTime
            val maxSpeedRadsPerSec = 15f
            leftPhase += (currentLeftPwm / 1023f) * maxSpeedRadsPerSec * dt
            rightPhase += (currentRightPwm / 1023f) * maxSpeedRadsPerSec * dt
        }
    }

    Canvas(modifier = modifier.aspectRatio(1.3f)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val scale = size.width / 2.8f

        // Matrix rotation using inverted values + 180 offset for back-facing default
        val pitch = (relativePitch * PI / 180f).toFloat()
        val roll = (relativeRoll * PI / 180f).toFloat()
        val yaw = ((relativeYaw + 180f) * PI / 180f).toFloat()

        val cp = cos(pitch); val sp = sin(pitch)
        val cr = cos(roll);  val sr = sin(roll)
        val cosYaw = cos(yaw); val sinYaw = sin(yaw)

        fun transform(x: Float, y: Float, z: Float): FloatArray {
            val yy = y * cp - z * sp
            val zy = y * sp + z * cp
            val xz = x * cr - yy * sr
            val yz = x * sr + yy * cr
            val xFinal = xz * cosYaw + zy * sinYaw
            val zFinal = -xz * sinYaw + zy * cosYaw
            return floatArrayOf(cx + xFinal * scale, cy + yz * scale, zFinal)
        }

        fun mappedOffset(f: FloatArray) = Offset(f[0], f[1])

        fun draw3DDisk(xCenter: Float, yCenter: Float, zCenter: Float, r: Float, color: Color) {
            val p = Path()
            val segments = 16
            for (i in 0 until segments) {
                val a = (i * 2 * PI / segments).toFloat()
                val pt = transform(xCenter + r * cos(a), yCenter + r * sin(a), zCenter)
                if (i == 0) p.moveTo(pt[0], pt[1]) else p.lineTo(pt[0], pt[1])
            }
            p.close()
            drawPath(p, color)
        }

        fun drawYZDisk(xCenter: Float, yCenter: Float, zCenter: Float, r: Float, color: Color, edgeColor: Color? = null, edgeWidth: Float = 2f) {
            val p = Path()
            val segments = 16
            for (i in 0 until segments) {
                val a = (i * 2 * PI / segments).toFloat()
                val pt = transform(xCenter, yCenter + r * sin(a), zCenter + r * cos(a))
                if (i == 0) p.moveTo(pt[0], pt[1]) else p.lineTo(pt[0], pt[1])
            }
            p.close()
            drawPath(p, color)
            if (edgeColor != null) {
                drawPath(p, edgeColor, style = Stroke(width = edgeWidth))
            }
        }

        val allComponents = mutableListOf<RenderComponent>()

        fun addQuad(p1: FloatArray, p2: FloatArray, p3: FloatArray, p4: FloatArray, color: Color, edgeColor: Color? = null, zBias: Float = 0f) {
            val zAvg = (p1[2] + p2[2] + p3[2] + p4[2]) / 4f + zBias
            allComponents.add(RenderComponent(zAvg) {
                val path = Path().apply {
                    moveTo(p1[0], p1[1]); lineTo(p2[0], p2[1]); lineTo(p3[0], p3[1]); lineTo(p4[0], p4[1]); close()
                }
                drawPath(path, color)
                if (edgeColor != null) drawPath(path, edgeColor, style = Stroke(width = 1f))
            })
        }

        // ====================================================================
        // VOLUMETRIC TRACK BUILDER
        // ====================================================================
        fun buildTrack(xCenter: Float, phase: Float, isFrontSprocket: Boolean, isRearSprocket: Boolean) {
            val trackWidth = 0.28f
            val rOut = 0.28f
            val rIn = 0.22f

            val zFront = 0.645f
            val zBack = -0.645f
            val straightLenBase = zFront - zBack
            val curveLenBase = PI.toFloat() * rOut

            val xInner = xCenter - trackWidth / 2f
            val xOuter = xCenter + trackWidth / 2f

            val beltOuterColor = Color(0xFF151515)
            val beltInnerColor = beltOuterColor
            val sideWallColor = beltOuterColor
            val wheelColor = Color(0xFFB0BEC5)

            // 1. Core Track Body Polygons
            val curveSegments = 12
            val straightSegments = 10
            val outLoop = mutableListOf<Pair<Float, Float>>()
            val inLoop = mutableListOf<Pair<Float, Float>>()

            for (i in 0..straightSegments) {
                val t = i.toFloat() / straightSegments
                val z = zBack + t * straightLenBase
                outLoop.add(Pair(-rOut, z))
                inLoop.add(Pair(-rIn, z))
            }
            for (i in 1..curveSegments) {
                val a = -PI.toFloat()/2f + (i * PI / curveSegments).toFloat()
                outLoop.add(Pair(rOut * sin(a), zFront + rOut * cos(a)))
                inLoop.add(Pair(rIn * sin(a), zFront + rIn * cos(a)))
            }
            for (i in 1..straightSegments) {
                val t = i.toFloat() / straightSegments
                val z = zFront - t * straightLenBase
                outLoop.add(Pair(rOut, z))
                inLoop.add(Pair(rIn, z))
            }
            for (i in 1 until curveSegments) {
                val a = PI.toFloat()/2f + (i * PI / curveSegments).toFloat()
                outLoop.add(Pair(rOut * sin(a), zBack + rOut * cos(a)))
                inLoop.add(Pair(rIn * sin(a), zBack + rIn * cos(a)))
            }

            val nodeCount = outLoop.size
            for (i in 0 until nodeCount) {
                val j = (i + 1) % nodeCount
                val ptOut1In = transform(xInner, outLoop[i].first, outLoop[i].second)
                val ptOut1Out = transform(xOuter, outLoop[i].first, outLoop[i].second)
                val ptOut2In = transform(xInner, outLoop[j].first, outLoop[j].second)
                val ptOut2Out = transform(xOuter, outLoop[j].first, outLoop[j].second)

                val ptIn1In = transform(xInner, inLoop[i].first, inLoop[i].second)
                val ptIn1Out = transform(xOuter, inLoop[i].first, inLoop[i].second)
                val ptIn2In = transform(xInner, inLoop[j].first, inLoop[j].second)
                val ptIn2Out = transform(xOuter, inLoop[j].first, inLoop[j].second)

                addQuad(ptOut1In, ptOut1Out, ptOut2Out, ptOut2In, beltOuterColor, null)
                addQuad(ptIn1In, ptIn2In, ptIn2Out, ptIn1Out, beltInnerColor, null)
                addQuad(ptOut1In, ptOut2In, ptIn2In, ptIn1In, sideWallColor, null)
                addQuad(ptOut1Out, ptIn1Out, ptIn2Out, ptOut2Out, sideWallColor, null)
            }

            // 2. Synchronized 3D Volumetric Treads
            val numTreads = 51
            val pitchRadius = 0.265f
            val curveLenPitch = PI.toFloat() * pitchRadius
            val totalPitchLen = 2 * straightLenBase + 2 * curveLenPitch

            fun getTrackPoint(d: Float, r: Float): FloatArray {
                var localD = d % totalPitchLen
                if (localD < 0) localD += totalPitchLen

                val ty: Float; val tz: Float
                if (localD < straightLenBase) {
                    ty = -r; tz = zBack + localD
                } else if (localD < straightLenBase + curveLenPitch) {
                    val angle = (localD - straightLenBase) / pitchRadius
                    ty = -r * cos(angle); tz = zFront + r * sin(angle)
                } else if (localD < 2 * straightLenBase + curveLenPitch) {
                    ty = r; tz = zFront - (localD - (straightLenBase + curveLenPitch))
                } else {
                    val angle = (localD - (2 * straightLenBase + curveLenPitch)) / pitchRadius
                    ty = r * cos(angle); tz = zBack - r * sin(angle)
                }
                return floatArrayOf(ty, tz)
            }

            val treadSpacing = totalPitchLen / numTreads
            val rawOffset = (phase * pitchRadius) % treadSpacing
            val positiveOffset = if (rawOffset < 0) rawOffset + treadSpacing else rawOffset

            val treadW = 0.03f
            val rTreadIn = rIn
            val rTreadOut = rOut + 0.025f
            val xTreadInner = xInner - 0.005f
            val xTreadOuter = xOuter + 0.005f

            for (i in 0 until numTreads) {
                val dCenter = i * treadSpacing + positiveOffset
                val d1 = dCenter - treadW / 2f
                val d2 = dCenter + treadW / 2f

                val ptO1 = getTrackPoint(d1, rTreadOut); val ptO2 = getTrackPoint(d2, rTreadOut)
                val ptI1 = getTrackPoint(d1, rTreadIn);  val ptI2 = getTrackPoint(d2, rTreadIn)

                val ptTreadO1In = transform(xTreadInner, ptO1[0], ptO1[1]); val ptTreadO1Out = transform(xTreadOuter, ptO1[0], ptO1[1])
                val ptTreadO2In = transform(xTreadInner, ptO2[0], ptO2[1]); val ptTreadO2Out = transform(xTreadOuter, ptO2[0], ptO2[1])
                val ptTreadI1In = transform(xTreadInner, ptI1[0], ptI1[1]); val ptTreadI1Out = transform(xTreadOuter, ptI1[0], ptI1[1])
                val ptTreadI2In = transform(xTreadInner, ptI2[0], ptI2[1]); val ptTreadI2Out = transform(xTreadOuter, ptI2[0], ptI2[1])

                val tBaseColor = Color(0xFF777777)
                val tSideColor = Color(0xFF555555)
                val tEdgeColor = Color(0xFF333333)
                val tBias = 0.005f

                addQuad(ptTreadO1In, ptTreadO1Out, ptTreadO2Out, ptTreadO2In, tBaseColor, tEdgeColor, tBias)
                addQuad(ptTreadO2In, ptTreadO2Out, ptTreadI2Out, ptTreadI2In, tSideColor, tEdgeColor, tBias)
                addQuad(ptTreadO1In, ptTreadI1In, ptTreadI1Out, ptTreadO1Out, tSideColor, tEdgeColor, tBias)
                addQuad(ptTreadO1In, ptTreadO2In, ptTreadI2In, ptTreadI1In, tSideColor, tEdgeColor, tBias)
                addQuad(ptTreadO1Out, ptTreadI1Out, ptTreadI2Out, ptTreadO2Out, tSideColor, tEdgeColor, tBias)

                val dLink = dCenter + treadSpacing / 2f
                val ptLink = getTrackPoint(dLink, rOut)
                val pLinkIn = transform(xInner, ptLink[0], ptLink[1])
                val pLinkOut = transform(xOuter, ptLink[0], ptLink[1])

                val zLink = (pLinkIn[2] + pLinkOut[2]) / 2f + 0.01f
                allComponents.add(RenderComponent(zLink) {
                    drawLine(Color(0xFF070707), mappedOffset(pLinkIn), mappedOffset(pLinkOut), strokeWidth = 3f)
                })
            }

            // 3. Multi-Component Wheels
            fun buildVolumetricWheel(zCenter: Float, isSprocket: Boolean) {
                val wRadius = rIn - 0.015f
                val xOutW = xOuter + 0.005f
                val xInW = xInner - 0.005f
                val spacerRadius = wRadius * 0.55f
                val uniformWheelThickness = 24f
                val cWidth = 0.28f
                val chassisAttachX = if (xCenter > 0) cWidth else -cWidth

                fun drawSegmentedAxle(x1: Float, x2: Float, yC: Float, zC: Float, color: Color, stroke: Float) {
                    val segments = 4
                    for (k in 0 until segments) {
                        val t1 = k.toFloat() / segments
                        val t2 = (k + 1).toFloat() / segments
                        val currX1 = x1 + t1 * (x2 - x1)
                        val currX2 = x1 + t2 * (x2 - x1)
                        val p1 = transform(currX1, yC, zC)
                        val p2 = transform(currX2, yC, zC)
                        val zAvg = (p1[2] + p2[2]) / 2f
                        allComponents.add(RenderComponent(zAvg - 0.005f) {
                            drawLine(color, mappedOffset(p1), mappedOffset(p2), strokeWidth = stroke, cap = StrokeCap.Round)
                        })
                    }
                }

                drawSegmentedAxle(chassisAttachX, xInW, 0f, zCenter, Color(0xFFE0E0E0), 28f)
                drawSegmentedAxle(xInW, xOutW, 0f, zCenter, Color(0xFF0077D4), 16f)

                for (i in 0 until 3) {
                    val angle = phase + (i * 2 * PI / 3).toFloat()
                    val ty = spacerRadius * sin(angle)
                    val tz = zCenter + spacerRadius * cos(angle)
                    drawSegmentedAxle(xInW, xOutW, ty, tz, Color(0xFFCFB53B), 12f)
                }

                fun drawDisc(xW: Float) {
                    if (isSprocket) {
                        val numTeeth = 20
                        val rBase = wRadius - 0.01f
                        val rTip = pitchRadius
                        val thetaWide = 0.11f
                        val thetaNarrow = 0.055f
                        val toothHalfThick = 0.012f

                        for (i in 0 until numTeeth) {
                            val angle = phase + (i * 2 * PI / numTeeth).toFloat()

                            val xInT = xW - toothHalfThick
                            val xOutT = xW + toothHalfThick

                            val p1_in = transform(xInT, rBase * sin(angle - thetaWide), zCenter + rBase * cos(angle - thetaWide))
                            val p2_in = transform(xInT, rBase * sin(angle + thetaWide), zCenter + rBase * cos(angle + thetaWide))
                            val p3_in = transform(xInT, rTip * sin(angle + thetaNarrow), zCenter + rTip * cos(angle + thetaNarrow))
                            val p4_in = transform(xInT, rTip * sin(angle - thetaNarrow), zCenter + rTip * cos(angle - thetaNarrow))

                            val p1_out = transform(xOutT, rBase * sin(angle - thetaWide), zCenter + rBase * cos(angle - thetaWide))
                            val p2_out = transform(xOutT, rBase * sin(angle + thetaWide), zCenter + rBase * cos(angle + thetaWide))
                            val p3_out = transform(xOutT, rTip * sin(angle + thetaNarrow), zCenter + rTip * cos(angle + thetaNarrow))
                            val p4_out = transform(xOutT, rTip * sin(angle - thetaNarrow), zCenter + rTip * cos(angle - thetaNarrow))

                            val tColor = wheelColor
                            val tSideColor = Color(0xFF90A4AE)
                            val tEdgeColor = Color(0xFF546E7A)

                            addQuad(p1_in, p2_in, p3_in, p4_in, tColor, tEdgeColor)
                            addQuad(p1_out, p4_out, p3_out, p2_out, tColor, tEdgeColor)
                            addQuad(p4_in, p3_in, p3_out, p4_out, tSideColor, tEdgeColor)
                            addQuad(p1_in, p4_in, p4_out, p1_out, tSideColor, tEdgeColor)
                            addQuad(p2_in, p1_in, p1_out, p2_out, tSideColor, tEdgeColor)
                            addQuad(p3_in, p2_in, p2_out, p3_out, tSideColor, tEdgeColor)
                        }
                    }

                    val wheelSegments = 16
                    for (i in 0 until wheelSegments) {
                        val a1 = (i * 2 * PI / wheelSegments).toFloat()
                        val a2 = ((i + 1) * 2 * PI / wheelSegments).toFloat()

                        val p1 = transform(xW, wRadius * sin(a1), zCenter + wRadius * cos(a1))
                        val p2 = transform(xW, wRadius * sin(a2), zCenter + wRadius * cos(a2))

                        val zAvg = (p1[2] + p2[2]) / 2f - 0.01f
                        allComponents.add(RenderComponent(zAvg) {
                            drawLine(wheelColor, mappedOffset(p1), mappedOffset(p2), strokeWidth = uniformWheelThickness, cap = StrokeCap.Round)
                        })
                    }

                    val centerPt = transform(xW, 0f, zCenter)
                    for (i in 0 until 3) {
                        val angle = phase + (i * 2 * PI / 3).toFloat()
                        val spokeEnd = transform(xW, wRadius * sin(angle), zCenter + wRadius * cos(angle))
                        val zAvg = (centerPt[2] + spokeEnd[2]) / 2f - 0.01f
                        allComponents.add(RenderComponent(zAvg) {
                            drawLine(wheelColor, mappedOffset(centerPt), mappedOffset(spokeEnd), strokeWidth = uniformWheelThickness, cap = StrokeCap.Round)
                        })
                    }

                    val hubZ = transform(xW, 0f, zCenter)[2] - 0.01f
                    allComponents.add(RenderComponent(hubZ) {
                        drawYZDisk(xW, 0f, zCenter, 0.05f, wheelColor)
                    })

                    val signDir = if (xW > xCenter) 1f else -1f

                    fun drawIndependentlySortedScrew(sX: Float, sY: Float, sZ: Float, r: Float) {
                        val screwSlices = 4
                        val protrusion = 0.025f
                        for (k in 0 until screwSlices) {
                            val t = k.toFloat() / screwSlices
                            val currX = sX + signDir * (t * protrusion)

                            val sliceTransformed = transform(currX, sY, sZ)
                            val zSlice = sliceTransformed[2] + 0.02f

                            val sColor = if (k == screwSlices) Color(0xFF777777) else Color(0xFF555555)
                            val sEdge = Color(0xFF333333)

                            allComponents.add(RenderComponent(zSlice) {
                                drawYZDisk(currX, sY, sZ, r, sColor, sEdge, 1f)
                            })
                        }
                    }

                    drawIndependentlySortedScrew(xW, 0f, zCenter, 0.02f)

                    for (i in 0 until 3) {
                        val angle = phase + (i * 2 * PI / 3).toFloat()
                        val ty = spacerRadius * sin(angle)
                        val tz = zCenter + spacerRadius * cos(angle)
                        drawIndependentlySortedScrew(xW, ty, tz, 0.015f)
                    }
                }
                drawDisc(xInW)
                drawDisc(xOutW)
            }

            buildVolumetricWheel(zCenter = zFront, isSprocket = isFrontSprocket)
            buildVolumetricWheel(zCenter = zBack, isSprocket = isRearSprocket)
        }

        buildTrack(xCenter = -0.55f, phase = leftPhase, isFrontSprocket = false, isRearSprocket = true)
        buildTrack(xCenter = 0.55f, phase = rightPhase, isFrontSprocket = true, isRearSprocket = false)

        // ====================================================================
        // MACRO-COMPONENTS
        // ====================================================================
        fun drawChassisGroup() {
            val cWidth = 0.28f; val cTop = -0.15f; val cBottom = 0.15f
            val cFront = 0.8f;  val cBack = -0.75f
            val segments = 5

            for (i in 0 until segments) {
                val zB = cBack + (i.toFloat() / segments) * (cFront - cBack)
                val zF = cBack + ((i + 1).toFloat() / segments) * (cFront - cBack)

                val v = arrayOf(
                    transform(-cWidth, cTop, zB),    transform(cWidth, cTop, zB),
                    transform(cWidth, cBottom, zB),  transform(-cWidth, cBottom, zB),
                    transform(-cWidth, cTop, zF),    transform(cWidth, cTop, zF),
                    transform(cWidth, cBottom, zF),  transform(-cWidth, cBottom, zF)
                )

                addQuad(v[0], v[1], v[5], v[4], Color(0xFFECEFF1), null)
                addQuad(v[3], v[2], v[6], v[7], Color(0xFF78909C), null)
                addQuad(v[0], v[3], v[7], v[4], Color(0xFF90A4AE), null)
                addQuad(v[1], v[2], v[6], v[5], Color(0xFF90A4AE), null)
                if (i == 0) addQuad(v[0], v[1], v[2], v[3], Color(0xFFB0BEC5), null)
                if (i == segments - 1) addQuad(v[4], v[5], v[6], v[7], Color(0xFFB0BEC5), null)

                val edgeColor = Color(0xFF546E7A)
                fun addEdgeLine(pt1: FloatArray, pt2: FloatArray) {
                    val zAvg = (pt1[2] + pt2[2]) / 2f
                    allComponents.add(RenderComponent(zAvg) {
                        drawLine(edgeColor, mappedOffset(pt1), mappedOffset(pt2), strokeWidth = 2f)
                    })
                }

                addEdgeLine(v[0], v[4]); addEdgeLine(v[1], v[5])
                addEdgeLine(v[2], v[6]); addEdgeLine(v[3], v[7])

                if (i == 0) {
                    addEdgeLine(v[0], v[1]); addEdgeLine(v[1], v[2])
                    addEdgeLine(v[2], v[3]); addEdgeLine(v[3], v[0])
                }
                if (i == segments - 1) {
                    addEdgeLine(v[4], v[5]); addEdgeLine(v[5], v[6])
                    addEdgeLine(v[6], v[7]); addEdgeLine(v[7], v[4])
                }
            }

            val pcbZ = (transform(0f, 0f, -0.5f)[2] + transform(0f, 0f, 0.3f)[2]) / 2f
            allComponents.add(RenderComponent(pcbZ) {
                val pcbPath = Path().apply {
                    val p0 = transform(-0.25f, -0.17f, -0.5f); val p1 = transform(0.25f, -0.17f, -0.5f)
                    val p2 = transform(0.25f, -0.17f, 0.3f);   val p3 = transform(-0.25f, -0.17f, 0.3f)
                    moveTo(p0[0], p0[1]); lineTo(p1[0], p1[1]); lineTo(p2[0], p2[1]); lineTo(p3[0], p3[1]); close()
                }
                drawPath(pcbPath, Color(0xFF2E7D32))
                drawPath(pcbPath, Color(0xFF1B5E20), style = Stroke(width = 2f))
            })
        }
        drawChassisGroup()

        fun buildEye(xOffset: Float) {
            val cFront = 0.8f
            val tipZ = cFront + 0.15f
            val eyeRadius = 0.08f

            val slices = 20
            for (i in 0..slices) {
                val t = i.toFloat() / slices
                val z = cFront + t * (tipZ - cFront)
                val shade = 160 + (t * 60).toInt()
                val zAvg = transform(xOffset, 0f, z)[2]
                allComponents.add(RenderComponent(zAvg) {
                    draw3DDisk(xOffset, 0f, z, eyeRadius, Color(shade, shade, shade))
                })
            }
            allComponents.add(RenderComponent(transform(xOffset, 0f, tipZ + 0.001f)[2]) {
                draw3DDisk(xOffset, 0f, tipZ + 0.001f, eyeRadius * 0.85f, Color.Black)
            })
            allComponents.add(RenderComponent(transform(xOffset, 0f, tipZ + 0.002f)[2]) {
                draw3DDisk(xOffset, 0f, tipZ + 0.002f, eyeRadius * 0.35f, Color(0xFF555555))
            })
        }
        buildEye(xOffset = -0.14f)
        buildEye(xOffset = 0.14f)

        fun buildBeam() {
            val beamStartZ = 0.8f + 0.15f
            val maxVisualLength = 3.0f
            val beamZ = beamStartZ + (sonarDistanceCm.coerceIn(0f, 200f) / 200f) * maxVisualLength
            if (sonarDistanceCm > 0f) {
                val zAvg = (transform(0f, 0f, beamStartZ)[2] + transform(0f, 0f, beamZ)[2]) / 2f
                allComponents.add(RenderComponent(zAvg) {
                    val frontCenter = transform(0f, 0f, beamStartZ)
                    val sonarEnd = transform(0f, 0f, beamZ)
                    drawLine(color = Color.Green.copy(alpha = 0.5f), start = mappedOffset(frontCenter), end = mappedOffset(sonarEnd), strokeWidth = 8f)
                    draw3DDisk(0f, 0f, beamZ, 0.04f, Color.Green)
                })
            }
        }
        buildBeam()

        // 1. Render all 3D geometry
        allComponents.sortedBy { it.zDepth }.forEach { it.drawCommand() }

        // 2. Absolute Priority Override
        val center = transform(0f, 0f, 0f)
        val axisRoll = transform(0.9f, 0f, 0f)
        val axisPitch = transform(0f, 0.9f, 0f)
        val axisYaw = transform(0f, 0f, 0.9f)

        // Text display values
        val pStr = ((relativePitch * 10).toInt() / 10f).toString()
        val yStr = ((relativeYaw * 10).toInt() / 10f).toString()
        val rStr = ((relativeRoll * 10).toInt() / 10f).toString()

        drawLine(color = Color(0xFFE53935), start = mappedOffset(center), end = mappedOffset(axisRoll), strokeWidth = 6f)
        drawText(textMeasurer, "X (Roll)\n$rStr", topLeft = mappedOffset(floatArrayOf(axisRoll[0] + 5f, axisRoll[1])), style = TextStyle(color = Color(0xFFE53935), fontWeight = FontWeight.Bold, fontSize = 14.sp))

        drawLine(color = Color(0xFF43A047), start = mappedOffset(center), end = mappedOffset(axisPitch), strokeWidth = 6f)
        drawText(textMeasurer, "Y (Pitch)\n$pStr", topLeft = mappedOffset(floatArrayOf(axisPitch[0] + 5f, axisPitch[1] - 15f)), style = TextStyle(color = Color(0xFF43A047), fontWeight = FontWeight.Bold, fontSize = 14.sp))

        drawLine(color = Color(0xFF1E88E5), start = mappedOffset(center), end = mappedOffset(axisYaw), strokeWidth = 6f)
        drawText(textMeasurer, "Z (Yaw)\n$yStr", topLeft = mappedOffset(floatArrayOf(axisYaw[0] + 5f, axisYaw[1])), style = TextStyle(color = Color(0xFF1E88E5), fontWeight = FontWeight.Bold, fontSize = 14.sp))
    }
}