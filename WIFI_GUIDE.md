# 方舟控制 Android App - WiFi/蓝牙双模式

## 更新内容

### 新增功能
1. **WiFi TCP 连接支持**
   - 支持 DT-06 WiFi 串口模块透传
   - AP 模式和 STA 模式均支持
   - TCP Server 模式连接

2. **蓝牙/WiFi 二选一控制**
   - 统一的连接管理器
   - 自动切换连接类型
   - 状态图标区分显示

### 使用说明

#### 蓝牙连接（保持原有功能）
1. 点击工具栏左侧的 **蓝牙图标**（搜索图标）
2. 选择已配对的蓝牙设备
3. 连接成功后可以控制小车

#### WiFi 连接（新增）
1. 点击工具栏左侧的 **WiFi 图标**
2. 在弹出的对话框中配置：
   - **DT-06 AP 模式**（默认）：IP `192.168.4.1`，端口 `8080`
   - **STA 模式**：根据路由器分配的 IP 配置
3. 点击"连接"按钮

#### DT-06 配置说明

##### AP 模式（热点模式）
- DT-06 作为热点，手机连接到模块创建的 WiFi
- 默认 SSID：`DT-06_XXXXXX`
- 默认 IP：`192.168.4.1`
- 默认端口：`8080`
- 适用场景：无外部路由器，点对点控制

##### STA 模式（客户端模式）
- DT-06 连接到现有 WiFi 路由器
- IP 地址由路由器 DHCP 分配
- 需要通过串口 AT 指令配置模块连接路由器
- 适用场景：多设备共享网络

### 连接状态指示
- **蓝牙图标**：灰色（未使用）/ 彩色（已连接）
- **WiFi 图标**：灰色（未使用）/ 彩色（已连接）
- **标题栏**：显示当前连接类型（"蓝牙控制" 或 "WiFi控制"）
- **状态文字**：显示连接状态（未连接/连接中/已连接/错误）

### 协议说明
无论是蓝牙还是 WiFi 连接，使用的通信协议完全相同：
- 格式：`[command,param1,param2,...]`
- 例如：`[joystick,50,30,-20,0]` - 摇杆控制
- 例如：`[gripper,100,0]` - 夹爪控制
- 例如：`[query]` - 查询状态

### STM32 端配置

#### 硬件连接
```
DT-06 模块    <-->   STM32
  TX         <-->   USART3_RX (PB11)
  RX         <-->   USART3_TX (PB10)
  VCC        <-->   3.3V/5V
  GND        <-->   GND
```

#### 串口配置
- 波特率：9600（可通过 AT 指令修改）
- 数据位：8
- 停止位：1
- 校验位：无

### 技术实现

#### 架构变更
```
原架构：
MainActivity -> MainViewModel -> BluetoothManager

新架构：
MainActivity -> MainViewModel -> ConnectionManager
                                     ├─ BluetoothManager
                                     └─ WifiManager
```

#### 新增文件
- `wifi/WifiManager.kt` - WiFi TCP 连接管理
- `connection/ConnectionManager.kt` - 统一连接接口
- `ui/WifiConnectionDialog.kt` - WiFi 配置对话框

#### 修改文件
- `MainViewModel.kt` - 使用 ConnectionManager
- `MainActivity.kt` - 添加 WiFi 对话框
- `ui/ControlPanel.kt` - 双按钮选择连接方式
- `AndroidManifest.xml` - 添加网络权限

### 权限说明
新增以下 Android 权限：
- `INTERNET` - 网络访问
- `ACCESS_WIFI_STATE` - WiFi 状态查询
- `CHANGE_WIFI_STATE` - WiFi 状态修改
- `ACCESS_NETWORK_STATE` - 网络状态查询

### 故障排查

#### WiFi 无法连接
1. 检查手机是否连接到 DT-06 的 WiFi（AP 模式）
2. 检查 IP 地址和端口是否正确
3. 确认 DT-06 模块已上电且工作正常
4. 尝试 ping `192.168.4.1` 测试网络连通性

#### 蓝牙无法连接
1. 确认蓝牙模块已配对
2. 检查蓝牙权限是否授予
3. 尝试重新配对设备

#### 连接成功但无法控制
1. 检查 STM32 端串口配置是否正确
2. 查看 App 日志确认数据是否发送
3. 使用串口助手测试 DT-06 透传是否正常

### 开发信息
- 最低 Android 版本：Android 6.0 (API 23)
- 目标 Android 版本：Android 14 (API 34)
- 开发语言：Kotlin
- UI 框架：Jetpack Compose

---

**版本**：v2.0
**更新日期**：2026-06-08
