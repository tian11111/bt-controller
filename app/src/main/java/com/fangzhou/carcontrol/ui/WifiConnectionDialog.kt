package com.fangzhou.carcontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.sp
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
    initialConfig: WifiConfig = WifiConfig(),
    onConnect: (WifiConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var ipAddress by remember { mutableStateOf(initialConfig.ip) }
    var port by remember { mutableStateOf(initialConfig.port.toString()) }
    var baudRate by remember { mutableStateOf(initialConfig.baudRate) }
    var expanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
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
                            port = "9000"
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
                            port = "9000"
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
                    placeholder = { Text("9000", color = Color.Gray) },
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

                // 串口波特率（仅记录/提示，App 不会自动修改 DT-06 配置）
                Box(modifier = Modifier.fillMaxWidth()) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = "$baudRate",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("串口波特率", color = Color.Gray) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF2196F3),
                                unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            containerColor = Color(0xFF2E2E2E)
                        ) {
                            listOf(9600, 19200, 38400, 57600, 115200, 230400, 460800).forEach { rate ->
                                DropdownMenuItem(
                                    text = { Text("$rate", color = Color.White) },
                                    onClick = { baudRate = rate; expanded = false }
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "提示：需与 DT-06 / 下位机串口波特率一致。App 不会自动改写模块配置。",
                    color = Color.Gray,
                    fontSize = 11.sp
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
                            val portNum = port.toIntOrNull() ?: 9000
                            onConnect(WifiConfig(ipAddress, portNum, baudRate))
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
