package eu.siacs.conversations.medialib.dialogs

import android.app.Activity
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.DialogCustomAspectRatioBinding
import eu.siacs.conversations.medialib.extensions.setupDialogStuff
import eu.siacs.conversations.medialib.extensions.showKeyboard
import eu.siacs.conversations.medialib.extensions.value

class CustomAspectRatioDialog(
    val activity: Activity, val defaultCustomAspectRatio: Pair<Float, Float>?, val callback: (aspectRatio: Pair<Float, Float>) -> Unit
) {
    init {
        val binding = DialogCustomAspectRatioBinding.inflate(activity.layoutInflater)
        val view = binding.root.apply {
            binding.aspectRatioWidth.setText(defaultCustomAspectRatio?.first?.toInt()?.toString() ?: "")
            binding.aspectRatioHeight.setText(defaultCustomAspectRatio?.second?.toInt()?.toString() ?: "")
        }

        MaterialAlertDialogBuilder(activity)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this) { alertDialog ->
                    alertDialog.showKeyboard(binding.aspectRatioWidth)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val width = getViewValue(binding.aspectRatioWidth)
                        val height = getViewValue(binding.aspectRatioHeight)
                        callback(Pair(width, height))
                        alertDialog.dismiss()
                    }
                }
            }
    }

    private fun getViewValue(view: EditText): Float {
        val textValue = view.value
        return if (textValue.isEmpty()) 0f else textValue.toFloat()
    }
}
