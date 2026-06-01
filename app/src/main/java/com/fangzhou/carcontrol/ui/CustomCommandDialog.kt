package com.fangzhou.carcontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun CustomCommandDialog(
    onSend: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var command by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1E3F))
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                "自定义命令",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "输入完整协议指令，例如: [joystick,0,50,0,0]",
                color = Color(0xFF888899),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("[command,param1,param2]", color = Color(0xFF555566)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF4FC3F7),
                    unfocusedBorderColor = Color(0xFF555566),
                    cursorColor = Color(0xFF4FC3F7)
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = {
                    if (command.isNotBlank()) {
                        onSend(command)
                        onDismiss()
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("发送", color = Color(0xFF4FC3F7))
            }
        }
    }
}
