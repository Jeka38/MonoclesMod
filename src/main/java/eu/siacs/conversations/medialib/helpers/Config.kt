package eu.siacs.conversations.medialib.helpers

import android.content.Context
import android.graphics.Color
import eu.siacs.conversations.R
import java.util.*

class Config(private val context: Context) {
    protected val prefs = context.getSharedPreferences("media_config_prefs", Context.MODE_PRIVATE)


    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    // color picker last used colors
    var colorPickerRecentColors: LinkedList<Int>
        get(): LinkedList<Int> {
            val defaultList = arrayListOf(
                Color.RED,
                Color.BLUE,
                Color.GREEN,
                Color.YELLOW,
                Color.BLACK
            )
            return LinkedList(prefs.getString(COLOR_PICKER_RECENT_COLORS, null)?.lines()?.map { it.toInt() } ?: defaultList)
        }
        set(recentColors) = prefs.edit().putString(COLOR_PICKER_RECENT_COLORS, recentColors.joinToString(separator = "\n")).apply()


    var lastEditorCropAspectRatio: Int
        get() = prefs.getInt(LAST_EDITOR_CROP_ASPECT_RATIO, ASPECT_RATIO_FREE)
        set(lastEditorCropAspectRatio) = prefs.edit().putInt(LAST_EDITOR_CROP_ASPECT_RATIO, lastEditorCropAspectRatio).apply()

    var lastEditorCropOtherAspectRatioX: Float
        get() = prefs.getFloat(LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_X, 2f)
        set(lastEditorCropOtherAspectRatioX) = prefs.edit().putFloat(LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_X, lastEditorCropOtherAspectRatioX).apply()

    var lastEditorCropOtherAspectRatioY: Float
        get() = prefs.getFloat(LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_Y, 1f)
        set(lastEditorCropOtherAspectRatioY) = prefs.edit().putFloat(LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_Y, lastEditorCropOtherAspectRatioY).apply()

    var lastEditorDrawColor: Int
        get() = prefs.getInt(LAST_EDITOR_DRAW_COLOR, context.getColor(R.color.editor_draw_default_color))
        set(lastEditorDrawColor) = prefs.edit().putInt(LAST_EDITOR_DRAW_COLOR, lastEditorDrawColor).apply()

    var lastEditorBrushSize: Int
        get() = prefs.getInt(LAST_EDITOR_BRUSH_SIZE, 50)
        set(lastEditorBrushSize) = prefs.edit().putInt(LAST_EDITOR_BRUSH_SIZE, lastEditorBrushSize).apply()
}
