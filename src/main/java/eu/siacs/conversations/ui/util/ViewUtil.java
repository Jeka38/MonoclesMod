package eu.siacs.conversations.ui.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import android.preference.PreferenceManager;

import java.io.File;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.MediaViewerActivity;
import eu.siacs.conversations.utils.MimeUtils;
import me.drakeet.support.toast.ToastCompat;

public class ViewUtil {

    public static void view(Context context, Uri uri) {
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            String mime = MimeUtils.guessMimeTypeFromUri(context, uri);
            if (mime == null) {
                mime = "*/*";
            }
            if (mime.startsWith("image/") && PreferenceManager.getDefaultSharedPreferences(context).getBoolean("internal_media_viewer", context.getResources().getBoolean(R.bool.internal_media_viewer))) {
                final Intent intent = new Intent(context, MediaViewerActivity.class);
                intent.putExtra("image", uri);
                try {
                    context.startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    //ignored
                }
            } else if (mime.startsWith("video/") && PreferenceManager.getDefaultSharedPreferences(context).getBoolean("internal_media_viewer", context.getResources().getBoolean(R.bool.internal_media_viewer))) {
                final Intent intent = new Intent(context, MediaViewerActivity.class);
                intent.putExtra("video", uri);
                try {
                    context.startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    //ignored
                }
            } else {
                final Intent openIntent = new Intent(Intent.ACTION_VIEW);
                openIntent.setDataAndType(uri, mime);
                try {
                    context.startActivity(openIntent);
                } catch (final ActivityNotFoundException e) {
                    ToastCompat.makeText(context, R.string.no_application_found_to_open_file, ToastCompat.LENGTH_SHORT).show();
                }
            }
        } else {
            final Intent openIntent = new Intent(Intent.ACTION_VIEW);
            String mime = MimeUtils.guessMimeTypeFromUri(context, uri);
            if (mime != null) {
                openIntent.setDataAndType(uri, mime);
            } else {
                openIntent.setData(uri);
            }
            openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                context.startActivity(openIntent);
            } catch (final ActivityNotFoundException e) {
                ToastCompat.makeText(context, R.string.no_application_found_to_open_file, ToastCompat.LENGTH_SHORT).show();
            }
        }
    }

    public static void view(Context context, Attachment attachment) {
        File file = new File(attachment.getUri().getPath());
        final String mime = attachment.getMime() == null ? "*/*" : attachment.getMime();
        view(context, file, mime);
    }

    public static void view(Context context, DownloadableFile file) {
        if (!file.exists()) {
            ToastCompat.makeText(context, R.string.file_deleted, ToastCompat.LENGTH_SHORT).show();
            return;
        }
        String mime = file.getMimeType();
        if (mime == null) {
            mime = "*/*";
        }
        view(context, file, mime);
    }

    public static void view(Context context, File file, String mime) {
        Log.d(Config.LOGTAG, "viewing " + file.getAbsolutePath() + " " + mime);
        final Uri uri;
        try {
            uri = FileBackend.getUriForFile(context, file);
        } catch (SecurityException e) {
            Log.d(Config.LOGTAG, "No permission to access " + file.getAbsolutePath(), e);
            ToastCompat.makeText(context, context.getString(R.string.no_permission_to_access_x, file.getAbsolutePath()), ToastCompat.LENGTH_SHORT).show();
            return;
        }
        // use internal viewer for images and videos
        if (mime.startsWith("image/") && PreferenceManager.getDefaultSharedPreferences(context).getBoolean("internal_media_viewer", context.getResources().getBoolean(R.bool.internal_media_viewer))) {
            final Intent intent = new Intent(context, MediaViewerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra("image", Uri.fromFile(file));
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                //ignored
            }
        } else if (mime.startsWith("video/") && PreferenceManager.getDefaultSharedPreferences(context).getBoolean("internal_media_viewer", context.getResources().getBoolean(R.bool.internal_media_viewer))) {
            final Intent intent = new Intent(context, MediaViewerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra("video", Uri.fromFile(file));
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                //ignored
            }
        } else {
            final Intent openIntent = new Intent(Intent.ACTION_VIEW);
            openIntent.setDataAndType(uri, mime);
            openIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                context.startActivity(openIntent);
            } catch (final ActivityNotFoundException e) {
                if (mime.equals("application/webxdc+zip")) {
                    Toast.makeText(context, R.string.webxdc_hint, Toast.LENGTH_LONG).show();
                } else {
                    ToastCompat.makeText(context, R.string.no_application_found_to_open_file, ToastCompat.LENGTH_SHORT).show();
                }
            }
        }
    }
}