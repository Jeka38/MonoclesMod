package eu.siacs.conversations.medialib.extensions

import android.graphics.Bitmap

fun String.getFilenameFromPath() = substring(lastIndexOf("/") + 1)

fun String.getFilenameExtension() = substring(lastIndexOf(".") + 1)

fun String.getCompressionFormat() = when (getFilenameExtension().lowercase()) {
    "png" -> Bitmap.CompressFormat.PNG
    "webp" -> Bitmap.CompressFormat.WEBP
    else -> Bitmap.CompressFormat.JPEG
}

fun String.areDigitsOnly() = matches(Regex("[0-9]+"))

fun String.getParentPath() = removeSuffix("/${getFilenameFromPath()}")
