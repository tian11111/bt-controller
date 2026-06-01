package com.fangzhou.carcontrol.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 虚拟摇杆组件
 *
 * @param size 摇杆直径
 * @param label 摇杆标签（显示在中央）
 * @param onValueChange 回调 (x, y)，范围 -1 ~ 1
 */
@Composable
fun JoystickPad(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    label: String = "",
    activeColor: Color = Color(0xFF4FC3F7),
    onDragStart: () -> Unit = {},
    onValueChange: (Float, Float) -> Unit = { _, _ -> }
) {
    var centerX by remember { mutableFloatStateOf(0f) }
    var centerY by remember { mutableFloatStateOf(0f) }
    var knobX by remember { mutableFloatStateOf(0f) }
    var knobY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var radius by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier = modifier
            .size(size)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        onDragStart()
                        centerX = size.toPx() / 2f
                        centerY = size.toPx() / 2f
                        radius = centerX * 0.8f
                        knobX = offset.x
                        knobY = offset.y
                        val dx = (knobX - centerX) / radius
                        val dy = (knobY - centerY) / radius
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist <= 1f) {
                            onValueChange(dx.coerceIn(-1f, 1f), dy.coerceIn(-1f, 1f))
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        knobX += dragAmount.x
                        knobY += dragAmount.y

                        val dx = knobX - centerX
                        val dy = knobY - centerY
                        val dist = sqrt(dx * dx + dy * dy)

                        if (dist > radius) {
                            knobX = centerX + dx / dist * radius
                            knobY = centerY + dy / dist * radius
                        }

                        val nx = ((knobX - centerX) / radius).coerceIn(-1f, 1f)
                        val ny = ((knobY - centerY) / radius).coerceIn(-1f, 1f)
                        onValueChange(nx, ny)
                    },
                    onDragEnd = {
                        isDragging = false
                        knobX = centerX
                        knobY = centerY
                        onValueChange(0f, 0f)
                    },
                    onDragCancel = {
                        isDragging = false
                        knobX = centerX
                        knobY = centerY
                        onValueChange(0f, 0f)
                    }
                )
            }
    ) {
        val s = this.size.width
        centerX = s / 2f
        centerY = s / 2f
        radius = centerX * 0.8f

        if (!isDragging) {
            knobX = centerX
            knobY = centerY
        }

        // 外圈
        drawCircle(
            color = Color(0xFF333355),
            radius = radius,
            center = Offset(centerX, centerY)
        )
        drawCircle(
            color = Color(0xFF555577),
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // 十字线
        drawLine(Color(0xFF444466), Offset(centerX - radius, centerY), Offset(centerX + radius, centerY))
        drawLine(Color(0xFF444466), Offset(centerX, centerY - radius), Offset(centerX, centerY + radius))

        // 内圈指示方向
        val dx = knobX - centerX
        val dy = knobY - centerY
        val dist = sqrt(dx * dx + dy * dy)
        if (dist > 1f) {
            val angle = atan2(dy, dx)
            val arrowLen = radius * 0.6f
            drawLine(
                color = activeColor.copy(alpha = 0.3f),
                start = Offset(centerX, centerY),
                end = Offset(centerX + cos(angle) * arrowLen, centerY + sin(angle) * arrowLen),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 摇杆手柄
        val knobRadius = radius * 0.35f
        val knobColor = if (isDragging) activeColor else activeColor.copy(alpha = 0.7f)
        drawCircle(
            color = knobColor,
            radius = knobRadius,
            center = Offset(knobX, knobY)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.3f),
            radius = knobRadius * 0.4f,
            center = Offset(knobX, knobY)
        )

        // 标签
        if (label.isNotEmpty()) {
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 12.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    alpha = 180
                }
                drawText(label, centerX, centerY + radius + 18.dp.toPx(), paint)
            }
        }
    }
}
