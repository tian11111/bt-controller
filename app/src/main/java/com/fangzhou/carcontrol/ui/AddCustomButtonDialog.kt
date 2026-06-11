package com.fangzhou.carcontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val COLOR_PRESETS = listOf(
    0xFF4FC3F7L to "蓝",
    0xFF66BB6AL to "绿",
    0xFFEF5350L to "红",
    0xFFFFD54FL to "黄",
    0xFFAB47BCL to "紫",
    0xFFFF8A65L to "橙",
    0xFF26C6DAL to "青",
    0xFFEC407AL to "粉",
)

@Composable
fun AddCustomButtonDialog(
    onDismiss: () -> Unit,
    onAdd: (label: String, command: String, colorHex: Long) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(COLOR_PRESETS[0].first) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E3F),
        title = { Text("添加自定义按钮", color = Color.White, fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("按钮名称") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF4FC3F7), unfocusedBorderColor = Color(0xFF333355),
                        cursorColor = Color(0xFF4FC3F7),
                        focusedLabelColor = Color(0xFF4FC3F7), unfocusedLabelColor = Color(0xFF888899)
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("发送命令 (如 [motor,1,100])") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF4FC3F7), unfocusedBorderColor = Color(0xFF333355),
                        cursorColor = Color(0xFF4FC3F7),
                        focusedLabelColor = Color(0xFF4FC3F7), unfocusedLabelColor = Color(0xFF888899)
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("颜色", color = Color(0xFF888899), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    COLOR_PRESETS.forEach { (colorHex, _) ->
                        val isSelected = selectedColor == colorHex
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex))
                                .then(
                                    if (isSelected) Modifier
                                        .padding(3.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.4f))
                                    else Modifier
                                )
                                .clickable { selectedColor = colorHex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (label.isNotBlank() && command.isNotBlank()) {
                        onAdd(label.trim(), command.trim(), selectedColor)
                        onDismiss()
                    }
                }
            ) { Text("添加", color = Color(0xFF4FC3F7)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF888899)) }
        }
    )
}
