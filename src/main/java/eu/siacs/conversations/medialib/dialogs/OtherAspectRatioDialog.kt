package eu.siacs.conversations.medialib.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.DialogOtherAspectRatioBinding
import eu.siacs.conversations.medialib.extensions.setupDialogStuff

class OtherAspectRatioDialog(
    val activity: Activity,
    val lastOtherAspectRatio: Pair<Float, Float>?,
    val callback: (aspectRatio: Pair<Float, Float>) -> Unit
) {
    private var dialog: AlertDialog? = null

    init {
        val binding = DialogOtherAspectRatioBinding.inflate(activity.layoutInflater)
        val view = binding.root.apply {
            binding.otherAspectRatio21.setOnClickListener { ratioPicked(Pair(2f, 1f)) }
            binding.otherAspectRatio32.setOnClickListener { ratioPicked(Pair(3f, 2f)) }
            binding.otherAspectRatio43.setOnClickListener { ratioPicked(Pair(4f, 3f)) }
            binding.otherAspectRatio53.setOnClickListener { ratioPicked(Pair(5f, 3f)) }
            binding.otherAspectRatio169.setOnClickListener { ratioPicked(Pair(16f, 9f)) }
            binding.otherAspectRatio199.setOnClickListener { ratioPicked(Pair(19f, 9f)) }
            binding.otherAspectRatioCustom.setOnClickListener { customRatioPicked() }

            binding.otherAspectRatio12.setOnClickListener { ratioPicked(Pair(1f, 2f)) }
            binding.otherAspectRatio23.setOnClickListener { ratioPicked(Pair(2f, 3f)) }
            binding.otherAspectRatio34.setOnClickListener { ratioPicked(Pair(3f, 4f)) }
            binding.otherAspectRatio35.setOnClickListener { ratioPicked(Pair(3f, 5f)) }
            binding.otherAspectRatio916.setOnClickListener { ratioPicked(Pair(9f, 16f)) }
            binding.otherAspectRatio919.setOnClickListener { ratioPicked(Pair(9f, 19f)) }

            val radio1SelectedItemId = when (lastOtherAspectRatio) {
                Pair(2f, 1f) -> binding.otherAspectRatio21.id
                Pair(3f, 2f) -> binding.otherAspectRatio32.id
                Pair(4f, 3f) -> binding.otherAspectRatio43.id
                Pair(5f, 3f) -> binding.otherAspectRatio53.id
                Pair(16f, 9f) -> binding.otherAspectRatio169.id
                Pair(19f, 9f) -> binding.otherAspectRatio199.id
                else -> 0
            }
            binding.otherAspectRatioDialogRadio1.check(radio1SelectedItemId)

            val radio2SelectedItemId = when (lastOtherAspectRatio) {
                Pair(1f, 2f) -> binding.otherAspectRatio12.id
                Pair(2f, 3f) -> binding.otherAspectRatio23.id
                Pair(3f, 4f) -> binding.otherAspectRatio34.id
                Pair(3f, 5f) -> binding.otherAspectRatio35.id
                Pair(9f, 16f) -> binding.otherAspectRatio916.id
                Pair(9f, 19f) -> binding.otherAspectRatio919.id
                else -> 0
            }
            binding.otherAspectRatioDialogRadio2.check(radio2SelectedItemId)

            if (radio1SelectedItemId == 0 && radio2SelectedItemId == 0) {
                binding.otherAspectRatioDialogRadio1.check(binding.otherAspectRatioCustom.id)
            }
        }

        MaterialAlertDialogBuilder(activity)
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun customRatioPicked() {
        CustomAspectRatioDialog(activity, lastOtherAspectRatio) {
            callback(it)
            dialog?.dismiss()
        }
    }

    private fun ratioPicked(pair: Pair<Float, Float>) {
        callback(pair)
        dialog?.dismiss()
    }
}
