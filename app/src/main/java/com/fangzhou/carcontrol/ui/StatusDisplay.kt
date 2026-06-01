package com.fangzhou.carcontrol.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun StatusDisplay(
    motorSpeeds: List<Int>,
    lastReceived: String,
    logMessages: List<String>,
    connectionLabel: String,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // 收到数据时闪烁指示灯
    var rxFlash by remember { mutableStateOf(false) }
    LaunchedEffect(lastReceived) {
        if (lastReceived.isNotEmpty()) {
            rxFlash = true
            delay(200)
            rxFlash = false
        }
    }
    val rxAlpha by animateFloatAsState(
        targetValue = if (rxFlash) 1f else 0.3f,
        animationSpec = tween(150),
        label = "rxFlash"
    )

    // 连接状态颜色动画
    val statusColor by animateColorAsState(
        targetValue = when {
            connectionLabel.contains("已连接") -> Color(0xFF66BB6A)
            connectionLabel.contains("连接中") -> Color(0xFFFFCA28)
            else -> Color(0xFFEF5350)
        },
        animationSpec = tween(400),
        label = "statusColor"
    )

    LaunchedEffect(logMessages.size) {
        if (logMessages.isNotEmpty()) {
            listState.animateScrollToItem(logMessages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A2E))
            .padding(8.dp)
    ) {
        // 状态标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // RX 指示灯
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .graphicsLayer { alpha = rxAlpha }
                        .clip(CircleShape)
                        .background(Color(0xFF66BB6A))
                )
                Text(
                    text = " 状态监视",
                    color = Color(0xFF4FC3F7),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = connectionLabel,
                color = statusColor,
                fontSize = 11.sp
            )
        }

        // 电机速度 - 颜色随数值变化
        if (motorSpeeds.size >= 4) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val labels = listOf("FL", "FR", "BL", "BR")
                motorSpeeds.take(4).forEachIndexed { i, speed ->
                    val speedColor by animateColorAsState(
                        targetValue = if (kotlin.math.abs(speed) > 50) Color(0xFFFF8A65) else Color(0xFFB0BEC5),
                        animationSpec = tween(200),
                        label = "speedColor$i"
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(labels[i], color = Color(0xFF888899), fontSize = 10.sp)
                        Text(
                            "$speed",
                            color = speedColor,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 最后接收
        if (lastReceived.isNotEmpty()) {
            Text(
                text = "RX: $lastReceived",
                color = Color(0xFF81C784),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        // 日志
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0D0D1A))
                .padding(4.dp)
        ) {
            if (logMessages.isEmpty()) {
                // 等待数据 - 呼吸动画
                val infiniteTransition = rememberInfiniteTransition(label = "breath")
                val breathAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "breathAlpha"
                )
                Text(
                    text = "等待数据...",
                    color = Color(0xFF555566).copy(alpha = breathAlpha),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                LazyColumn(state = listState) {
                    items(logMessages) { msg ->
                        Text(
                            text = msg,
                            color = Color(0xFFB0BEC5),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }
    }
}
