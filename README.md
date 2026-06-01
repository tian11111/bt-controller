# 蓝牙控制 - 方舟小车 Android App

基于 Jetpack Compose 的蓝牙遥控 Android 应用，配合 STM32 方舟小车使用。

## 功能

- **蓝牙 SPP 连接** — 扫描配对设备，一键连接 HC-05/06
- **虚拟摇杆** — 移动摇杆（推幅=速度）+ 转向摇杆
- **夹爪控制** — 垂直滑块控制升降，按钮控制开闭
- **状态监视** — 实时显示四电机转速、接收日志、RX 指示灯
- **自定义命令** — 手动发送任意协议指令
- **协议编辑器** — 查看内置协议、添加自定义命令（带参数模板）
- **布局编辑** — 编辑模式下拖拽控件位置，支持缩放，配置持久化
- **动画 + 震动** — Compose 动画过渡 + 按钮震动反馈

## 协议格式

```
发送: [command,param1,param2,...]\r\n
接收: [command,param1,param2,...]
```

### 内置命令

| 命令 | 格式 | 说明 |
|------|------|------|
| `joystick` | `[joystick,lx,ly,rx,ry]` | 移动控制，值 -100~100 |
| `gripper` | `[gripper,x_speed,y_speed]` | 夹爪控制，值 -300~300 RPM |
| `query` | `[query]` | 查询电机转速 |
| `pid` | `[pid,motor,kp*100,ki*100,kd*100]` | PID 参数调节 |
| `plot` | `[plot]` | 一次性获取速度数据 |
| `auto` | `[auto]` | 开始自动发送 (100ms) |
| `stop` | `[stop]` | 停止自动发送 |

### 响应格式

```
[s,speed0,speed1,speed2,speed3]     — query 响应
[p,d0,d1,d2,d3]                     — plot/auto 数据
[rx:<原始指令>]                      — 回显
```

## 技术栈

- Kotlin + Jetpack Compose
- Material 3 + Compose Animation
- Bluetooth Classic SPP (RFCOMM)
- Gson 序列化
- SharedPreferences 持久化

## 编译

用 Android Studio 打开项目目录，Sync Gradle 后直接 Run。

```
minSdk: 26 (Android 8.0)
targetSdk: 33
```

## 自定义协议扩展

```kotlin
// 代码方式注册
val engine = ProtocolEngine()
engine.registerHandler(object : ProtocolHandler {
    override val command = "motor"
    override fun parse(params: List<String>) = ...
    override fun serialize(message: ProtocolMessage) = ...
})

// 或通过 App 内协议编辑器 UI 添加
```

## 目录结构

```
app/src/main/java/com/fangzhou/carcontrol/
├── MainActivity.kt              # 入口
├── MainViewModel.kt             # 状态管理
├── bluetooth/
│   ├── BluetoothManager.kt      # 蓝牙连接
│   ├── ProtocolEngine.kt        # 协议解析引擎
│   └── ProtocolCommandStore.kt  # 协议命令持久化
├── layout/
│   └── LayoutConfig.kt          # 控件布局配置
└── ui/
    ├── ControlPanel.kt          # 主控制面板
    ├── JoystickPad.kt           # 虚拟摇杆
    ├── DraggableWidget.kt       # 可拖拽容器
    ├── StatusDisplay.kt         # 状态显示
    ├── BtConnectionDialog.kt    # 蓝牙设备选择
    ├── ProtocolEditorScreen.kt  # 协议编辑器
    ├── CustomCommandDialog.kt   # 自定义命令
    └── HapticManager.kt         # 震动反馈
```

## License

MIT
