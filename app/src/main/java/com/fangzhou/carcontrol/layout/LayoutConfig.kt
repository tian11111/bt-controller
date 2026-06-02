package com.fangzhou.carcontrol.layout

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 控件位置和大小配置
 */
data class WidgetLayout(
    val id: String,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1.0f,
    val visible: Boolean = true
)

/**
 * 全局布局配置
 */
data class LayoutConfig(
    val widgets: List<WidgetLayout> = emptyList(),
    val isEditing: Boolean = false
)

/**
 * 预定义控件ID
 */
object WidgetIds {
    const val JOYSTICK_MOVE = "joystick_move"
    const val JOYSTICK_TURN = "joystick_turn"
    const val SLIDER_GRIPPER_UPDOWN = "slider_gripper_updown"
    const val BUTTON_GRIPPER_OPEN = "button_gripper_open"
    const val BUTTON_GRIPPER_CLOSE = "button_gripper_close"
    const val BUTTON_QUERY = "button_query"
    const val BUTTON_AUTO_PLOT = "button_auto_plot"
    const val BUTTON_STOP = "button_stop"
    const val STATUS_DISPLAY = "status_display"

    val ALL_IDS = listOf(
        JOYSTICK_MOVE,
        JOYSTICK_TURN,
        SLIDER_GRIPPER_UPDOWN,
        BUTTON_GRIPPER_OPEN,
        BUTTON_GRIPPER_CLOSE,
        BUTTON_QUERY,
        BUTTON_AUTO_PLOT,
        BUTTON_STOP,
        STATUS_DISPLAY
    )
}

/**
 * 布局配置持久化管理器
 */
class LayoutPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("fangzhou_layout", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_LAYOUT = "layout_config"
    }

    fun load(): LayoutConfig {
        val json = prefs.getString(KEY_LAYOUT, null) ?: return defaultLayout()
        return try {
            val type = object : TypeToken<LayoutConfig>() {}.type
            val config: LayoutConfig = gson.fromJson(json, type) ?: defaultLayout()
            // isEditing 每次启动都重置为 false
            config.copy(isEditing = false)
        } catch (_: Exception) {
            defaultLayout()
        }
    }

    fun save(config: LayoutConfig) {
        prefs.edit().putString(KEY_LAYOUT, gson.toJson(config)).apply()
    }

    fun reset() {
        save(defaultLayout())
    }

    private fun defaultLayout(): LayoutConfig {
        val defaults = mapOf(
            WidgetIds.JOYSTICK_MOVE to (0.10f to 0.50f),
            WidgetIds.JOYSTICK_TURN to (0.10f to 0.12f),
            WidgetIds.SLIDER_GRIPPER_UPDOWN to (0.35f to 0.30f),
            WidgetIds.BUTTON_GRIPPER_OPEN to (0.33f to 0.68f),
            WidgetIds.BUTTON_GRIPPER_CLOSE to (0.42f to 0.68f),
            WidgetIds.BUTTON_QUERY to (0.33f to 0.80f),
            WidgetIds.BUTTON_AUTO_PLOT to (0.42f to 0.80f),
            WidgetIds.BUTTON_STOP to (0.51f to 0.80f),
            WidgetIds.STATUS_DISPLAY to (0.72f to 0.08f),
        )
        return LayoutConfig(
            widgets = WidgetIds.ALL_IDS.map { id ->
                val (ox, oy) = defaults[id] ?: (0f to 0f)
                WidgetLayout(id = id, offsetX = ox, offsetY = oy)
            }
        )
    }
}
