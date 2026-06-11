package com.fangzhou.carcontrol.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun DraggableWidget(
    isEditing: Boolean,
    offsetX: Float,
    offsetY: Float,
    onOffsetChange: (Float, Float) -> Unit,
    parentSize: IntSize,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var dragPxX by remember { mutableStateOf(0f) }
    var dragPxY by remember { mutableStateOf(0f) }
    var baseOffX by remember { mutableStateOf(offsetX) }
    var baseOffY by remember { mutableStateOf(offsetY) }

    // 外部 offset 更新时同步基准值并清零拖拽增量
    if (offsetX != baseOffX || offsetY != baseOffY) {
        baseOffX = offsetX
        baseOffY = offsetY
        dragPxX = 0f
        dragPxY = 0f
    }

    val pxX = (baseOffX * parentSize.width + dragPxX).toInt()
    val pxY = (baseOffY * parentSize.height + dragPxY).toInt()

    Box(
        modifier = modifier
            .offset { IntOffset(pxX, pxY) }
            .then(
                if (isEditing) {
                    Modifier
                        .border(2.dp, Color(0xFF00FF88), RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    baseOffX = offsetX
                                    baseOffY = offsetY
                                    dragPxX = 0f
                                    dragPxY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragPxX += dragAmount.x
                                    dragPxY += dragAmount.y
                                },
                                onDragEnd = {
                                    if (parentSize.width > 0 && parentSize.height > 0) {
                                        val newOffX = (baseOffX + dragPxX / parentSize.width)
                                            .coerceIn(0f, 0.9f)
                                        val newOffY = (baseOffY + dragPxY / parentSize.height)
                                            .coerceIn(0f, 0.9f)
                                        onOffsetChange(newOffX, newOffY)
                                    }
                                    // 不清零 dragPxX/Y，等外部 offset 更新时自然同步
                                },
                                onDragCancel = {
                                    dragPxX = 0f
                                    dragPxY = 0f
                                }
                            )
                        }
                } else {
                    Modifier
                }
            )
            .padding(4.dp),
        content = content
    )
}
