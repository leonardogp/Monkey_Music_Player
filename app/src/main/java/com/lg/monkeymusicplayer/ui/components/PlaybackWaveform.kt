package com.lg.monkeymusicplayer.ui.components.core

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lg.monkeymusicplayer.ui.theme.PrimaryOrange
import kotlin.random.Random

@Composable
fun PlaybackWaveform(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = PrimaryOrange,
    barCount: Int = 32
) {
    val transition = rememberInfiniteTransition(label = "waveform")

    val animations: List<State<Float>> = List(barCount) { index ->

        val duration = remember(index) { Random.nextInt(450, 950) }
        val delay = remember(index) { Random.nextInt(0, 600) }
        val peak = remember(index) { Random.nextFloat().coerceIn(0.6f, 1f) }

        if (isPlaying) {
            transition.animateFloat(
                initialValue = 0.15f,
                targetValue = peak,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = duration,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(delay)
                ),
                label = "bar_$index"
            )
        } else {
            animateFloatAsState(
                targetValue = 0.12f,
                animationSpec = tween(500),
                label = "idle_$index"
            )
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val barWidth = size.width / (barCount * 1.6f)
        val spacing = barWidth * 0.6f

        animations.forEachIndexed { index, anim ->
            val height = size.height * anim.value
            val x = index * (barWidth + spacing)

            drawRoundRect(
                color = accent,
                topLeft = Offset(x, (size.height - height) / 2),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth, barWidth)
            )
        }
    }
}