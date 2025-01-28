package eu.siacs.conversations.utils;

import static eu.siacs.conversations.persistance.FileBackend.APP_DIRECTORY;
import static eu.siacs.conversations.persistance.FileBackend.STORAGE_INDEX;

import android.content.Context;
import android.os.Environment;

import androidx.annotation.Nullable;

import java.io.File;

public class StorageHelper {

    public static File getConversationsDirectory(final Context context, final String type) {
        if (type.equalsIgnoreCase("null")) {
            return new File(getStorage(context, STORAGE_INDEX.get(), APP_DIRECTORY, type));
        } else {
            return new File(getAppMediaDirectory(context, type) + APP_DIRECTORY + " " + type);
        }
    }

    public static String getAppMediaDirectory(final Context context, final String type) {
        return getStorage(context, STORAGE_INDEX.get(), APP_DIRECTORY, type);
    }

    public static String getStorage(Context c, int index, String APP_DIRECTORY, String type) {
        if (index == 0) {
            return getData(c, APP_DIRECTORY, type);
        } else {
            return c.getFilesDir().getAbsolutePath() + File.separator;
        }
    }

    public static String getBackupDirectory(@Nullable String app) {
        if (app != null && (app.equalsIgnoreCase("conversations") || app.equalsIgnoreCase("Quicksy"))) {
            return Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + app + File.separator + "Backup" + File.separator;
        } else if (app != null && (app.equalsIgnoreCase("Monocles Messenger"))) {
            return Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + app + File.separator + "Database" + File.separator;
        } else {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + APP_DIRECTORY + File.separator + "Database" + File.separator;
        }
    }

    private static String getData(Context context, String APP_DIRECTORY, String type) {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + APP_DIRECTORY + File.separator + "Media" + File.separator;
    }

    public static String getGlobalPicturesPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + APP_DIRECTORY + File.separator + "pictures" + File.separator;
    }

    public static String getGlobalVideosPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + APP_DIRECTORY + File.separator + "videos" + File.separator;
    }

    public static String getGlobalDocumentsPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + APP_DIRECTORY + File.separator + "documents" + File.separator;
    }

    public static String getGlobalDownloadsPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + APP_DIRECTORY + File.separator + "downloads" + File.separator;
    }

    public static String getGlobalAudiosPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + APP_DIRECTORY + File.separator + "audios" + File.separator;
    }

    public static String getAppUpdateDirectory() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + APP_DIRECTORY + File.separator + "Update" + File.separator;
    }

    public static String getAppLogsDirectory() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + APP_DIRECTORY + File.separator + "Chats" + File.separator;
    }

    public static void migrateStorage(Context c) {
        //ignore
    }
}
