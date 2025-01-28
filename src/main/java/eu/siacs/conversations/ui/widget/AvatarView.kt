package eu.siacs.conversations.ui.widget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Outline
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import androidx.appcompat.widget.AppCompatImageView
import eu.siacs.conversations.R

class AvatarView : AppCompatImageView {
    private var currentShape: String = ""

    private val preferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> invalidateShape() }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        invalidateShape()
        PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(preferencesListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(preferencesListener)
    }

    private fun invalidateShape() {
        val shape = PreferenceManager.getDefaultSharedPreferences(context).getString("avatar_shape", context.getString(R.string.avatar_shape))

        if (shape == currentShape) {
            return
        }

        when {
            shape == "oval" -> {
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
            }
            shape == "rounded_square" -> {
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        val maxRadius = minOf(view.width, view.height) / 2f
                        val radius = minOf(context.resources.getDimension(R.dimen.avatar_corners_radius), maxRadius)
                        outline.setRoundRect(0, 0, view.width, view.height, radius)
                    }
                }
            }
            shape == "square" -> {
                clipToOutline = false
                outlineProvider = ViewOutlineProvider.BACKGROUND
            }
        }

        currentShape = shape!!
    }
}