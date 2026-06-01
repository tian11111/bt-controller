package com.fangzhou.carcontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fangzhou.carcontrol.bluetooth.ProtocolCommandDef
import com.fangzhou.carcontrol.bluetooth.ProtocolCommandStore

@Composable
fun ProtocolEditorScreen(
    store: ProtocolCommandStore,
    onBack: () -> Unit,
    onSendCommand: (String) -> Unit
) {
    var commands by remember { mutableStateOf(store.loadAll()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var expandedCommand by remember { mutableStateOf<String?>(null) }
    var testParamValues by remember { mutableStateOf(mutableMapOf<String, List<String>>()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F23))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF16162E))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回", tint = Color(0xFF4FC3F7))
                    }
                    Text("协议引擎编辑器", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, "添加命令", tint = Color(0xFF66BB6A))
                }
            }

            // 提示
            Text(
                "内置命令不可删除，自定义命令可编辑/删除。点击命令可展开测试发送。",
                color = Color(0xFF888899),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // 命令列表
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(commands) { cmd ->
                    val isExpanded = expandedCommand == cmd.name
                    val paramValues = testParamValues[cmd.name] ?: cmd.params.map { "" }

                    CommandCard(
                        cmd = cmd,
                        isExpanded = isExpanded,
                        paramValues = paramValues,
                        onToggleExpand = {
                            expandedCommand = if (isExpanded) null else cmd.name
                        },
                        onParamChange = { index, value ->
                            val newValues = paramValues.toMutableList()
                            if (index < newValues.size) newValues[index] = value
                            testParamValues = testParamValues.toMutableMap().apply {
                                put(cmd.name, newValues)
                            }
                        },
                        onSend = {
                            val sendStr = cmd.buildSendString(paramValues)
                            onSendCommand(sendStr)
                        },
                        onDelete = {
                            if (!cmd.isBuiltIn) {
                                store.removeCustom(cmd.name)
                                commands = store.loadAll()
                            }
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(60.dp)) }
            }
        }

        // 添加命令对话框
        if (showAddDialog) {
            AddCommandDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { def ->
                    store.addCustom(def)
                    commands = store.loadAll()
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
private fun CommandCard(
    cmd: ProtocolCommandDef,
    isExpanded: Boolean,
    paramValues: List<String>,
    onToggleExpand: () -> Unit,
    onParamChange: (Int, String) -> Unit,
    onSend: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (cmd.isBuiltIn) Color(0xFF1A1A2E) else Color(0xFF1A2E1A))
            .clickable(onClick = onToggleExpand)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "[${cmd.name}]",
                    color = if (cmd.isBuiltIn) Color(0xFF4FC3F7) else Color(0xFF66BB6A),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                if (cmd.description.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(cmd.description, color = Color(0xFF888899), fontSize = 12.sp)
                }
                if (cmd.isBuiltIn) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("内置", color = Color(0xFF555566), fontSize = 10.sp)
                }
            }
            Row {
                if (!cmd.isBuiltIn) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "删除", tint = Color(0xFFEF5350))
                    }
                }
            }
        }

        // 预览格式
        Text(
            "格式: ${cmd.preview()}",
            color = Color(0xFFB0BEC5),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (cmd.params.isNotEmpty()) {
            Text(
                "参数: ${cmd.params.joinToString(", ")}",
                color = Color(0xFF888899),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // 展开区域：参数输入 + 发送
        if (isExpanded) {
            Spacer(modifier = Modifier.height(8.dp))

            cmd.params.forEachIndexed { i, paramName ->
                OutlinedTextField(
                    value = paramValues.getOrElse(i) { "" },
                    onValueChange = { onParamChange(i, it) },
                    label = { Text(paramName, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF4FC3F7),
                        unfocusedBorderColor = Color(0xFF333355),
                        cursorColor = Color(0xFF4FC3F7),
                        focusedLabelColor = Color(0xFF4FC3F7),
                        unfocusedLabelColor = Color(0xFF888899)
                    ),
                    singleLine = true,
                    textStyle = FontFamily.Monospace.let { androidx.compose.ui.text.TextStyle(fontFamily = it, fontSize = 13.sp) }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 预览发送内容
            val preview = cmd.buildSendString(paramValues)
            Text(
                "发送: $preview",
                color = Color(0xFF81C784),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onSend,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.Send, "发送", tint = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text("发送", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AddCommandDialog(
    onDismiss: () -> Unit,
    onAdd: (ProtocolCommandDef) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var paramsStr by remember { mutableStateOf("") }
    var template by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E3F),
        title = { Text("添加自定义命令", color = Color.White, fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.lowercase().replace(" ", "_") },
                    label = { Text("命令名 (如 motor)") },
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
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") },
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
                    value = paramsStr,
                    onValueChange = { paramsStr = it },
                    label = { Text("参数名 (逗号分隔，如 idx,spd)") },
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
                    value = template,
                    onValueChange = { template = it },
                    label = { Text("发送模板 (可选，如 [motor,{0},{1}])") },
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
                Text(
                    "不填模板则默认格式: [命令名,参数1,参数2,...]",
                    color = Color(0xFF888899), fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        val params = paramsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        onAdd(ProtocolCommandDef(
                            name = name.trim(),
                            description = description.trim(),
                            params = params,
                            template = template.trim()
                        ))
                    }
                }
            ) { Text("添加", color = Color(0xFF4FC3F7)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF888899)) }
        }
    )
}
