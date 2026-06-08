package com.fangzhou.carcontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fangzhou.carcontrol.wifi.WifiConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiConnectionDialog(
    onConnect: (WifiConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var ipAddress by remember { mutableStateOf("192.168.4.1") }
    var port by remember { mutableStateOf("8080") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "WiFi TCP 连接",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )

                Divider(color = Color.Gray.copy(alpha = 0.3f))

                // 预设配置
                Text(
                    text = "预设配置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            ipAddress = "192.168.4.1"
                            port = "8080"
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3)
                        )
                    ) {
                        Text("DT-06 AP模式", color = Color.White)
                    }

                    Button(
                        onClick = {
                            ipAddress = "192.168.1.100"
                            port = "8080"
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("STA模式", color = Color.White)
                    }
                }

                // IP 地址输入
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("IP 地址", color = Color.Gray) },
                    placeholder = { Text("192.168.4.1", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF2196F3),
                        focusedBorderColor = Color(0xFF2196F3),
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 端口输入
                OutlinedTextField(
                    value = port,
                    onValueChange = { 
                        if (it.all { char -> char.isDigit() } && it.length <= 5) {
                            port = it
                        }
                    },
                    label = { Text("端口", color = Color.Gray) },
                    placeholder = { Text("8080", color = Color.Gray) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF2196F3),
                        focusedBorderColor = Color(0xFF2196F3),
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Divider(color = Color.Gray.copy(alpha = 0.3f))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.Gray
                        )
                    ) {
                        Text("取消")
                    }

                    Button(
                        onClick = {
                            val portNum = port.toIntOrNull() ?: 8080
                            onConnect(WifiConfig(ipAddress, portNum))
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = ipAddress.isNotBlank() && port.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3),
                            disabledContainerColor = Color.Gray
                        )
                    ) {
                        Text("连接", color = Color.White)
                    }
                }
            }
        }
    }
}
