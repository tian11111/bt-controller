package com.fangzhou.carcontrol.bluetooth

/**
 * 方舟协议解析引擎 - 可扩展的协议解析器
 *
 * 协议格式: [command,param1,param2,...]
 * 发送时加上 \r\n 结尾
 *
 * 设计为可扩展架构：
 * - ProtocolHandler: 每个协议命令的处理器接口
 * - ProtocolEngine: 协议引擎，管理所有注册的处理器
 * - 提供内置的方舟协议处理器（joystick, gripper, query, pid, plot）
 */

/**
 * 协议处理器接口 - 实现此接口以添加自定义协议命令
 */
interface ProtocolHandler {
    /** 该处理器能处理的命令名 */
    val command: String

    /**
     * 解析并处理接收到的数据
     * @param params 命令后的参数列表（逗号分隔后的内容）
     * @return 解析结果，null 表示无法处理
     */
    fun parse(params: List<String>): ProtocolMessage?

    /**
     * 生成发送数据
     * @param message 要发送的协议消息
     * @return 完整的发送字符串（包含 [ ] 和 \r\n）
     */
    fun serialize(message: ProtocolMessage): String?
}

/**
 * 协议消息基类
 */
sealed class ProtocolMessage {
    data class Joystick(val lx: Int, val ly: Int, val rx: Int, val ry: Int) : ProtocolMessage()
    data class Gripper(val xSpeed: Int, val ySpeed: Int) : ProtocolMessage()
    data class MotorStatus(val speeds: List<Int>) : ProtocolMessage()
    data class PlotData(val data: List<Int>) : ProtocolMessage()
    data class PidConfig(val motorIndex: Int, val kp: Float, val ki: Float, val kd: Float) : ProtocolMessage()
    data class Raw(val command: String, val params: List<String>) : ProtocolMessage()
    data class Text(val content: String) : ProtocolMessage()
}

/**
 * 方舟内置协议 - Joystick 命令处理器
 */
class JoystickHandler : ProtocolHandler {
    override val command = "joystick"

    override fun parse(params: List<String>): ProtocolMessage.Joystick? {
        if (params.size < 4) return null
        return try {
            ProtocolMessage.Joystick(
                lx = params[0].trim().toInt(),
                ly = params[1].trim().toInt(),
                rx = params[2].trim().toInt(),
                ry = params[3].trim().toInt()
            )
        } catch (_: NumberFormatException) { null }
    }

    override fun serialize(message: ProtocolMessage): String? {
        if (message !is ProtocolMessage.Joystick) return null
        return "[joystick,${message.lx},${message.ly},${message.rx},${message.ry}]\r\n"
    }
}

/**
 * 方舟内置协议 - Gripper 命令处理器
 */
class GripperHandler : ProtocolHandler {
    override val command = "gripper"

    override fun parse(params: List<String>): ProtocolMessage.Gripper? {
        if (params.size < 2) return null
        return try {
            ProtocolMessage.Gripper(
                xSpeed = params[0].trim().toInt(),
                ySpeed = params[1].trim().toInt()
            )
        } catch (_: NumberFormatException) { null }
    }

    override fun serialize(message: ProtocolMessage): String? {
        if (message !is ProtocolMessage.Gripper) return null
        return "[gripper,${message.xSpeed},${message.ySpeed}]\r\n"
    }
}

/**
 * 方舟内置协议 - Motor Status 查询/响应处理器
 */
class MotorStatusHandler : ProtocolHandler {
    override val command = "s"

    override fun parse(params: List<String>): ProtocolMessage.MotorStatus? {
        if (params.isEmpty()) return null
        return try {
            ProtocolMessage.MotorStatus(params.map { it.trim().toInt() })
        } catch (_: NumberFormatException) { null }
    }

    override fun serialize(message: ProtocolMessage): String? {
        if (message !is ProtocolMessage.MotorStatus) return null
        val speeds = message.speeds.joinToString(",")
        return "[s,$speeds]\r\n"
    }
}

/**
 * 方舟内置协议 - Plot 数据处理器
 */
class PlotHandler : ProtocolHandler {
    override val command = "p"

    override fun parse(params: List<String>): ProtocolMessage.PlotData? {
        if (params.isEmpty()) return null
        return try {
            ProtocolMessage.PlotData(params.map { it.trim().toInt() })
        } catch (_: NumberFormatException) { null }
    }

    override fun serialize(message: ProtocolMessage): String? {
        if (message !is ProtocolMessage.PlotData) return null
        val data = message.data.joinToString(",")
        return "[p,$data]\r\n"
    }
}

/**
 * 方舟内置协议 - PID 配置处理器
 */
class PidHandler : ProtocolHandler {
    override val command = "pid"

    override fun parse(params: List<String>): ProtocolMessage.PidConfig? {
        if (params.size < 4) return null
        return try {
            ProtocolMessage.PidConfig(
                motorIndex = params[0].trim().toInt(),
                kp = params[1].trim().toFloat() / 100f,
                ki = params[2].trim().toFloat() / 100f,
                kd = params[3].trim().toFloat() / 100f
            )
        } catch (_: NumberFormatException) { null }
    }

    override fun serialize(message: ProtocolMessage): String? {
        if (message !is ProtocolMessage.PidConfig) return null
        val kp = (message.kp * 100).toInt()
        val ki = (message.ki * 100).toInt()
        val kd = (message.kd * 100).toInt()
        return "[pid,${message.motorIndex},$kp,$ki,$kd]\r\n"
    }
}

/**
 * 协议引擎 - 管理所有协议处理器，负责解析和序列化
 */
class ProtocolEngine {

    private val handlers = mutableMapOf<String, ProtocolHandler>()

    init {
        // 注册内置处理器
        registerHandler(JoystickHandler())
        registerHandler(GripperHandler())
        registerHandler(MotorStatusHandler())
        registerHandler(PlotHandler())
        registerHandler(PidHandler())
    }

    /**
     * 注册自定义协议处理器
     */
    fun registerHandler(handler: ProtocolHandler) {
        handlers[handler.command.lowercase()] = handler
    }

    /**
     * 移除协议处理器
     */
    fun removeHandler(command: String) {
        handlers.remove(command.lowercase())
    }

    /**
     * 获取所有已注册的命令名
     */
    fun getRegisteredCommands(): Set<String> = handlers.keys.toSet()

    /**
     * 解析一帧协议数据
     * 帧格式: [command,param1,param2,...]
     *
     * @param frame 去掉 [] 后的内容，如 "joystick,0,50,0,0"
     * @return 解析后的 ProtocolMessage
     */
    fun parseFrame(frame: String): ProtocolMessage {
        val parts = frame.split(",")
        if (parts.isEmpty()) return ProtocolMessage.Text(frame)

        val command = parts[0].trim().lowercase()
        val params = parts.drop(1)

        val handler = handlers[command]
        return handler?.parse(params) ?: ProtocolMessage.Raw(command, params)
    }

    /**
     * 从原始接收数据中提取所有帧并解析
     * 支持粘包处理：一串数据可能包含多个 [xxx] 帧
     *
     * @param raw 从蓝牙接收到的原始字符串
     * @return 解析出的所有协议消息
     */
    fun parseRawData(raw: String): List<ProtocolMessage> {
        val results = mutableListOf<ProtocolMessage>()
        var remaining = raw

        while (remaining.isNotEmpty()) {
            val start = remaining.indexOf('[')
            if (start == -1) break

            val end = remaining.indexOf(']', start)
            if (end == -1) break

            val frameContent = remaining.substring(start + 1, end)
            if (frameContent.isNotEmpty()) {
                results.add(parseFrame(frameContent))
            }
            remaining = remaining.substring(end + 1)
        }

        return results
    }

    /**
     * 序列化协议消息为发送字符串
     */
    fun serialize(message: ProtocolMessage): String? {
        for (handler in handlers.values) {
            val result = handler.serialize(message)
            if (result != null) return result
        }

        // 降级处理
        return when (message) {
            is ProtocolMessage.Raw -> {
                val params = if (message.params.isNotEmpty()) ",${message.params.joinToString(",")}" else ""
                "[${message.command}$params]\r\n"
            }
            is ProtocolMessage.Text -> if (message.content.endsWith("\r\n")) message.content else "${message.content}\r\n"
            else -> null
        }
    }

    // ===== 便捷发送方法 =====

    fun createJoystick(lx: Int, ly: Int, rx: Int = 0, ry: Int = 0): String {
        return "[joystick,$lx,$ly,$rx,$ry]\r\n"
    }

    // 短格式摇杆指令 - 优化延迟
    fun createJoystickShort(lx: Int, ly: Int, rx: Int): String {
        // 只发送非零值，减少数据量
        // 格式: J,x,y,r
        return "J,$lx,$ly,$rx"
    }

    fun createGripper(xSpeed: Int, ySpeed: Int): String {
        return "[gripper,$xSpeed,$ySpeed]\r\n"
    }

    // 短格式夹爪指令 - 优化延迟
    fun createGripperShort(ySpeed: Int): String {
        // 格式: G,speed
        return "G,$ySpeed"
    }

    fun createQuery(): String {
        return "[query]\r\n"
    }

    fun createPlot(): String {
        return "[plot]\r\n"
    }

    fun createAutoStart(): String {
        return "[auto]\r\n"
    }

    fun createAutoStop(): String {
        return "[stop]\r\n"
    }

    fun createPid(motorIndex: Int, kp: Float, ki: Float, kd: Float): String {
        val kpi = (kp * 100).toInt()
        val kii = (ki * 100).toInt()
        val kdi = (kd * 100).toInt()
        return "[pid,$motorIndex,$kpi,$kii,$kdi]\r\n"
    }

    /**
     * 创建任意自定义命令
     */
    fun createCustom(command: String, vararg params: Any): String {
        val paramStr = if (params.isNotEmpty()) ",${params.joinToString(",")}" else ""
        return "[$command$paramStr]\r\n"
    }
}
