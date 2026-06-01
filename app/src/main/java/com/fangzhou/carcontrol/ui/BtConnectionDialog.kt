package com.fangzhou.carcontrol.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@SuppressLint("MissingPermission")
@Composable
fun BtConnectionDialog(
    devices: List<BluetoothDevice>,
    onRefresh: () -> Unit,
    onConnect: (BluetoothDevice) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = rememberHaptic()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1E3F))
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "选择蓝牙设备",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { haptic.light(); onRefresh() }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = Color(0xFF4FC3F7)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Text(
                    text = "未发现已配对设备，请先在系统设置中配对",
                    color = Color(0xFF888899),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    items(devices) { device ->
                        DeviceItem(
                            device = device,
                            modifier = Modifier.animateItem(),
                            onClick = {
                                haptic.medium()
                                onConnect(device)
                                onDismiss()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("取消", color = Color(0xFF888899))
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceItem(device: BluetoothDevice, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Bluetooth,
            contentDescription = null,
            tint = Color(0xFF4FC3F7)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = device.name ?: "未知设备",
                color = Color.White,
                fontSize = 15.sp
            )
            Text(
                text = device.address,
                color = Color(0xFF888899),
                fontSize = 12.sp
            )
        }
    }
}
