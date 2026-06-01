package com.fangzhou.carcontrol.bluetooth

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 自定义协议命令定义
 * @param name 命令名，如 "motor"
 * @param description 描述
 * @param params 参数名列表，如 ["index", "speed", "direction"]
 * @param template 发送模板，如 "[motor,{0},{1},{2}]"  用 {n} 表示第n个参数
 * @param responseTemplate 响应解析模板，如 "[motor_ack,{0}]"  可选
 * @param isBuiltIn 是否内置（不可删除）
 */
data class ProtocolCommandDef(
    val name: String,
    val description: String = "",
    val params: List<String> = emptyList(),
    val template: String = "",
    val responseTemplate: String = "",
    val isBuiltIn: Boolean = false
) {
    /**
     * 根据参数值生成发送字符串
     */
    fun buildSendString(paramValues: List<String>): String {
        if (template.isNotBlank()) {
            var result = template
            paramValues.forEachIndexed { i, v ->
                result = result.replace("{$i}", v)
            }
            return "$result\r\n"
        }
        // 默认格式: [name,param1,param2,...]
        val paramsStr = if (paramValues.isNotEmpty()) ",${paramValues.joinToString(",")}" else ""
        return "[$name$paramsStr]\r\n"
    }

    /**
     * 获取带示例参数的预览
     */
    fun preview(): String {
        if (template.isNotBlank()) {
            var result = template
            params.forEachIndexed { i, p ->
                result = result.replace("{$i}", "<$p>")
            }
            return result
        }
        val paramsStr = if (params.isNotEmpty()) params.joinToString(", ") { "<$it>" } else ""
        return "[$name${if (paramsStr.isNotEmpty()) ",$paramsStr" else ""}]"
    }
}

class ProtocolCommandStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("fangzhou_protocol", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_COMMANDS = "custom_commands"

        val BUILT_IN_COMMANDS = listOf(
            ProtocolCommandDef("joystick", "摇杆控制", listOf("lx", "ly", "rx", "ry"),
                "[joystick,{0},{1},{2},{3}]", isBuiltIn = true),
            ProtocolCommandDef("gripper", "夹爪控制", listOf("x_speed", "y_speed"),
                "[gripper,{0},{1}]", isBuiltIn = true),
            ProtocolCommandDef("query", "状态查询", emptyList(), "[query]",
                "[s,<s0>,<s1>,<s2>,<s3>]", isBuiltIn = true),
            ProtocolCommandDef("pid", "PID调参", listOf("motor", "kp*100", "ki*100", "kd*100"),
                "[pid,{0},{1},{2},{3}]", isBuiltIn = true),
            ProtocolCommandDef("plot", "绘图数据", emptyList(), "[plot]",
                "[p,<d0>,<d1>,<d2>,<d3>]", isBuiltIn = true),
            ProtocolCommandDef("auto", "自动发送", emptyList(), "[auto]",
                "[auto:start]", isBuiltIn = true),
            ProtocolCommandDef("stop", "停止自动", emptyList(), "[stop]",
                "[auto:stop]", isBuiltIn = true),
        )
    }

    fun loadAll(): List<ProtocolCommandDef> {
        val builtIn = BUILT_IN_COMMANDS
        val custom = loadCustom()
        return builtIn + custom
    }

    fun loadCustom(): List<ProtocolCommandDef> {
        val json = prefs.getString(KEY_COMMANDS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ProtocolCommandDef>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun saveCustom(commands: List<ProtocolCommandDef>) {
        prefs.edit().putString(KEY_COMMANDS, gson.toJson(commands)).apply()
    }

    fun addCustom(def: ProtocolCommandDef) {
        val current = loadCustom().toMutableList()
        current.removeAll { it.name == def.name }
        current.add(def)
        saveCustom(current)
    }

    fun removeCustom(name: String) {
        val current = loadCustom().toMutableList()
        current.removeAll { it.name == name }
        saveCustom(current)
    }
}
