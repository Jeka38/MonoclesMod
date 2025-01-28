package eu.siacs.conversations.medialib.extensions

import android.app.Activity
import android.view.View
import androidx.appcompat.app.AlertDialog
import eu.siacs.conversations.R
import eu.siacs.conversations.medialib.models.FileDirItem
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream

fun Activity.setupDialogStuff(
    view: View,
    dialog: AlertDialog.Builder,
    titleId: Int = 0,
    titleText: String = "",
    cancelOnTouchOutside: Boolean = true,
    callback: ((alertDialog: AlertDialog) -> Unit)? = null
) {
    if (isDestroyed || isFinishing) {
        return
    }

    dialog.create().apply {
        if (titleId != 0) {
            setTitle(titleId)
        } else if (titleText.isNotEmpty()) {
            setTitle(titleText)
        }

        setView(view)
        setCancelable(cancelOnTouchOutside)
        if (!isFinishing) {
            show()
        }

        callback?.invoke(this)
    }
}
