package de.monocles.mod.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;

public class StickerUtils {

    public static File getStickersDir(Context context) {
        return new File(context.getFilesDir(), "stickers");
    }

    public static String getUniqueFileName(File dir, String filename) {
        if (!new File(dir, filename).exists()) {
            return filename;
        }
        String name;
        String extension;
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            name = filename.substring(0, dotIndex);
            extension = filename.substring(dotIndex);
        } else {
            name = filename;
            extension = "";
        }
        int i = 1;
        while (new File(dir, name + "_" + i + extension).exists()) {
            i++;
        }
        return name + "_" + i + extension;
    }

    public static String getFileName(ContentResolver contentResolver, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            Cursor cursor = contentResolver.query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    public static void compressImageToSticker(Context context, File f, Uri image, int sampleSize) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        int IMAGE_QUALITY = 65;
        int ImageSize = (int) (0.5 * 1024 * 1024); // 512KB for stickers is reasonable
        try {
            File tempFile = File.createTempFile("sticker_temp", ".webp", context.getCacheDir());
            is = context.getContentResolver().openInputStream(image);
            if (is == null) {
                throw new IOException("Not an image file");
            }
            final BitmapFactory.Options options = new BitmapFactory.Options();
            final int inSampleSize = (int) Math.pow(2, sampleSize);
            options.inSampleSize = inSampleSize;
            Bitmap originalBitmap = BitmapFactory.decodeStream(is, null, options);
            is.close();
            if (originalBitmap == null) {
                throw new IOException("Source file was not an image");
            }

            int resolution = 512;
            int size;
            int height = originalBitmap.getHeight();
            int width = originalBitmap.getWidth();
            size = Math.max(height, width);

            Bitmap scaledBitmap = resize(originalBitmap, Math.min(size, resolution));
            final int rotation = getRotation(context, image);
            scaledBitmap = rotate(scaledBitmap, rotation);

            boolean targetSizeReached = false;
            int quality = IMAGE_QUALITY;
            while (!targetSizeReached) {
                os = new FileOutputStream(tempFile);
                // Use WEBP to support alpha channel for stickers
                boolean success = scaledBitmap.compress(Bitmap.CompressFormat.WEBP, quality, os);
                if (!success) {
                    throw new IOException("Error compressing image");
                }
                os.flush();
                os.close();
                os = null;
                targetSizeReached = (tempFile.length() <= ImageSize) || quality <= 30;
                quality -= 10;
            }
            scaledBitmap.recycle();
            if (!tempFile.renameTo(f)) {
                if (!f.exists() && !f.createNewFile()) {
                    throw new IOException("Unable to create file");
                }
                try (InputStream in = new java.io.FileInputStream(tempFile);
                     OutputStream out = new FileOutputStream(f)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
                tempFile.delete();
            }
        } catch (final FileNotFoundException e) {
            cleanup(f);
            throw new IOException("File not found");
        } catch (final IOException e) {
            cleanup(f);
            throw new IOException("IO Exception");
        } catch (SecurityException e) {
            cleanup(f);
            throw new IOException("Security exception");
        } catch (final OutOfMemoryError e) {
            if (sampleSize <= 3) {
                compressImageToSticker(context, f, image, sampleSize + 1);
            } else {
                throw new IOException("Out of memory");
            }
        } finally {
            close(os);
            close(is);
        }
    }

    private static Bitmap resize(final Bitmap originalBitmap, int size) throws IOException {
        int w = originalBitmap.getWidth();
        int h = originalBitmap.getHeight();
        if (w <= 0 || h <= 0) {
            throw new IOException("Decoded bitmap reported bounds smaller 0");
        } else if (Math.max(w, h) > size) {
            int scaledW;
            int scaledH;
            if (w <= h) {
                scaledW = Math.max((int) (w / ((double) h / size)), 1);
                scaledH = size;
            } else {
                scaledW = size;
                scaledH = Math.max((int) (h / ((double) w / size)), 1);
            }
            final Bitmap result = Bitmap.createScaledBitmap(originalBitmap, scaledW, scaledH, true);
            if (!originalBitmap.isRecycled()) {
                originalBitmap.recycle();
            }
            return result;
        } else {
            return originalBitmap;
        }
    }

    private static int getRotation(Context context, final Uri image) {
        try (final InputStream is = context.getContentResolver().openInputStream(image)) {
            return is == null ? 0 : getRotation(is);
        } catch (final Exception e) {
            return 0;
        }
    }

    public static int getRotation(final InputStream inputStream) throws IOException {
        final ExifInterface exif = new ExifInterface(inputStream);
        final int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    private static Bitmap rotate(final Bitmap bitmap, final int degree) {
        if (degree == 0) {
            return bitmap;
        }
        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();
        final Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        final Bitmap result = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);
        if (!bitmap.isRecycled()) {
            bitmap.recycle();
        }
        return result;
    }

    public static void close(final Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "unable to close stream", e);
            }
        }
    }

    private static void cleanup(final File file) {
        try {
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            // ignore
        }
    }
}
