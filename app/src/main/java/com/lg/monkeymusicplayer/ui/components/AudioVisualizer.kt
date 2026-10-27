package com.lg.monkeymusicplayer.ui.components

import android.media.audiofx.Visualizer
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lg.monkeymusicplayer.util.PermissionHelper
import timber.log.Timber
import kotlin.math.hypot

// Spectrum Glow Ultra Pro - Paleta Naranja Fija
private val OrangeMain = Color(0xFFFF9800)
private val OrangeLight = Color(0xFFFFB74D)
private val OrangeDark = Color(0xFFE65100)
private val WarmWhiteHighlight = Color(0xFFFFF8E1).copy(alpha = 0.65f)

@Composable
fun AudioVisualizer(
    audioSessionId: Int,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") accentColor: Color = OrangeMain
) {
    val context = LocalContext.current
    var rawMagnitudes by remember { mutableStateOf(FloatArray(32)) }
    
    val smoothedMagnitudes = remember { FloatArray(32) }
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
    )

    val barCount = 32
    // Precalculamos los límites de bins (startBin y endBin) y ganancias para las 32 bandas logarítmicas
    val bandStartBins = remember { IntArray(barCount) }
    val bandEndBins = remember { IntArray(barCount) }
    val bandGains = remember { FloatArray(barCount) }

    remember {
        val minBin = 1 // Omitimos el bin 0 (Componente DC) para evitar el cuelgue en pausa
        val maxBin = 63 // Máximo bin complejo utilizable para captureSize = 128
        val totalBins = maxBin - minBin + 1
        for (i in 0 until barCount) {
            val ratioStart = Math.pow(i.toDouble() / barCount, 1.5)
            val ratioEnd = Math.pow((i + 1).toDouble() / barCount, 1.5)
            val start = minBin + kotlin.math.floor(ratioStart * totalBins).toInt().coerceIn(0, totalBins - 1)
            val end = (minBin + kotlin.math.floor(ratioEnd * totalBins).toInt() - 1).coerceIn(start, maxBin)
            
            bandStartBins[i] = start
            bandEndBins[i] = end
            // Ganancia progresiva entre 1.0f (bajos) y 2.2f (agudos) para equilibrar la respuesta espectral
            bandGains[i] = 1.0f + (i.toFloat() / (barCount - 1)) * 1.2f
        }
        true
    }

    DisposableEffect(audioSessionId) {
        if (audioSessionId <= 0) {
            return@DisposableEffect onDispose {}
        }

        if (!PermissionHelper.hasRecordAudioPermission(context)) {
            Timber.w("AudioVisualizer: RECORD_AUDIO permission not granted, skipping Visualizer initialization")
            return@DisposableEffect onDispose {}
        }
        
        var visualizer: Visualizer? = null
        try {
            visualizer = Visualizer(audioSessionId).apply {
                enabled = false
                val captureSizeRange = Visualizer.getCaptureSizeRange()
                captureSize = 128.coerceIn(captureSizeRange[0], captureSizeRange[1])
                
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                    
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        val newMagnitudes = FloatArray(barCount)
                        val fftSize = fft?.size ?: 0

                        if (fft != null && fftSize >= 68) {
                            val maxAvailableBin = (fftSize / 2) - 1
                            for (i in 0 until barCount) {
                                val start = bandStartBins[i].coerceAtMost(maxAvailableBin)
                                val end = bandEndBins[i].coerceAtMost(maxAvailableBin)
                                var maxMagInBand = 0f

                                for (k in start..end) {
                                    val r = fft[k * 2].toInt()
                                    val j = fft[k * 2 + 1].toInt()
                                    val mag = hypot(r.toDouble(), j.toDouble()).toFloat()
                                    if (mag.isFinite() && mag > maxMagInBand) {
                                        maxMagInBand = mag
                                    }
                                }

                                val normalizedMag = maxMagInBand * bandGains[i]
                                newMagnitudes[i] = if (normalizedMag.isFinite()) normalizedMag else 0f
                            }
                        }
                        rawMagnitudes = newMagnitudes
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                
                enabled = true
            }
        } catch (e: SecurityException) {
            Timber.e(e, "AudioVisualizer: SecurityException initializing Visualizer")
            visualizer?.release()
            visualizer = null
        } catch (e: IllegalStateException) {
            Timber.e(e, "AudioVisualizer: IllegalStateException initializing Visualizer")
            visualizer?.release()
            visualizer = null
        } catch (e: Exception) {
            Timber.e(e, "AudioVisualizer: Exception initializing Visualizer")
            visualizer?.release()
            visualizer = null
        }

        onDispose {
            try {
                visualizer?.enabled = false
                visualizer?.release()
            } catch (e: Exception) {
                Timber.e(e, "AudioVisualizer: Error releasing Visualizer")
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val barCount = 32
        val totalSpacingRatio = 0.4f
        val barWidth = width / (barCount * (1f + totalSpacingRatio))
        val spacing = barWidth * totalSpacingRatio
        val totalWidth = barCount * barWidth + (barCount - 1) * spacing
        val startX = (width - totalWidth) / 2f

        for (i: Int in 0 until barCount) {
            val target = (rawMagnitudes[i] * 1.8f).coerceAtMost(height)
            val currentSmoothed = smoothedMagnitudes[i]
            if (target > currentSmoothed) {
                // Ataque rápido (50% de interpolación por cuadro)
                smoothedMagnitudes[i] = currentSmoothed + (target - currentSmoothed) * 0.50f
            } else {
                // Caída progresiva (12% de interpolación por cuadro / 88% retención)
                smoothedMagnitudes[i] = currentSmoothed + (target - currentSmoothed) * 0.12f
            }
            
            val currentBarHeight = smoothedMagnitudes[i].coerceAtLeast(6.dp.toPx())
            val x = startX + i * (barWidth + spacing)
            val y = height - currentBarHeight

            // 1. Capa de resplandor exterior (Glow) suave
            drawRoundRect(
                color = OrangeMain.copy(alpha = glowAlpha * 0.35f),
                topLeft = Offset(x - 2.dp.toPx(), y - 2.dp.toPx()),
                size = Size(barWidth + 4.dp.toPx(), currentBarHeight + 4.dp.toPx()),
                cornerRadius = CornerRadius((barWidth + 4.dp.toPx()) / 2f, (barWidth + 4.dp.toPx()) / 2f)
            )

            // 2. Barra vertical principal con gradiente vertical premium en paleta naranja fija
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        OrangeLight,
                        OrangeMain,
                        OrangeDark
                    ),
                    startY = y,
                    endY = height
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, currentBarHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )

            // 3. Brillo superior (Cap / Reflejo cálido translúcido)
            drawRoundRect(
                color = WarmWhiteHighlight,
                topLeft = Offset(x, y),
                size = Size(barWidth, 3.dp.toPx()),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
