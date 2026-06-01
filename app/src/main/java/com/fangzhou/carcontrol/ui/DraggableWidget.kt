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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
    offsetX: Float,       // normalized 0~1
    offsetY: Float,       // normalized 0~1
    onOffsetChange: (Float, Float) -> Unit,
    parentSize: IntSize,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // 拖拽累积像素偏移，用 stable key 不重置
    var dragPxX by remember { mutableFloatStateOf(0f) }
    var dragPxY by remember { mutableFloatStateOf(0f) }

    // 拖拽开始时的基准 offset 快照
    var baseOffX by remember { mutableFloatStateOf(offsetX) }
    var baseOffY by remember { mutableFloatStateOf(offsetY) }

    // 外部 offset 变化时同步基准值并清零拖拽
    if (offsetX != baseOffX || offsetY != baseOffY) {
        baseOffX = offsetX
        baseOffY = offsetY
        dragPxX = 0f
        dragPxY = 0f
    }

    val currentOnOffsetChange by rememberUpdatedState(onOffsetChange)
    val currentParentSize by rememberUpdatedState(parentSize)

    val visualPxX = (baseOffX * parentSize.width + dragPxX).toInt()
    val visualPxY = (baseOffY * parentSize.height + dragPxY).toInt()

    Box(
        modifier = modifier
            .offset { IntOffset(visualPxX, visualPxY) }
            .then(
                if (isEditing) {
                    Modifier
                        .border(2.dp, Color(0xFF00FF88), RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    // 开始拖拽时锁定当前基准
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
                                    val ps = currentParentSize
                                    val newOffX = (baseOffX + dragPxX / ps.width)
                                        .coerceIn(0f, 0.9f)
                                    val newOffY = (baseOffY + dragPxY / ps.height)
                                        .coerceIn(0f, 0.9f)
                                    currentOnOffsetChange(newOffX, newOffY)
                                    dragPxX = 0f
                                    dragPxY = 0f
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
