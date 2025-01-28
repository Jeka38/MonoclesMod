package eu.siacs.conversations.utils;

import static eu.siacs.conversations.persistance.FileBackend.APP_DIRECTORY;
import static eu.siacs.conversations.persistance.FileBackend.AUDIOS;
import static eu.siacs.conversations.persistance.FileBackend.FILES;
import static eu.siacs.conversations.persistance.FileBackend.IMAGES;
import static eu.siacs.conversations.persistance.FileBackend.STORAGE_INDEX;
import static eu.siacs.conversations.persistance.FileBackend.VIDEOS;
import static eu.siacs.conversations.persistance.FileBackend.moveDirectory;
import static eu.siacs.conversations.persistance.FileBackend.moveFile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.services.XmppConnectionService;

public class StorageHelper {

    public static File getConversationsDirectory(final Context context, final String type) {
        if (type.equalsIgnoreCase("null")) {
            return new File(getStorage(context, STORAGE_INDEX.get(), APP_DIRECTORY, type));
        } else {
            return new File(getAppMediaDirectory(context, type) + APP_DIRECTORY + " " + type + File.separator);
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
        } else if (app != null && (app.equalsIgnoreCase("Pix-Art Messenger"))) {
            return Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + app + File.separator + "Database" + File.separator;
        } else {
            if (Compatibility.runsThirty()) {
                return getGlobalDownloadsPath() + "Database" + File.separator;
            } else {
                return Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + APP_DIRECTORY + File.separator + "Database" + File.separator;
            }
        }
    }

    public static String getAppLogsDirectory() {
        if (Compatibility.runsThirty()) {
            return getGlobalDownloadsPath() + "Chats" + File.separator;
        } else {
            return Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + APP_DIRECTORY + File.separator + "Chats" + File.separator;
        }
    }

    private static String getData(Context context, String APP_DIRECTORY, String type) {
        if (Compatibility.runsThirty()) {
            return context.getExternalMediaDirs()[0].getAbsolutePath() + File.separator;
        } else {
            return Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + APP_DIRECTORY + File.separator + "Media" + File.separator;
        }
    }

    public static String getGlobalPicturesPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + File.separator + "monocles chat" + File.separator;
    }

    public static String getGlobalVideosPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) + File.separator + "monocles chat"+ File.separator;
    }

    public static String getGlobalDocumentsPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + "monocles chat"+ File.separator;
    }

    public static String getGlobalDownloadsPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + File.separator + "monocles chat"+ File.separator;
    }

    public static String getGlobalAudiosPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) + File.separator + "monocles chat"+ File.separator;
    }

    public static String getAppUpdateDirectory() {
        if (Compatibility.runsThirty()) {
            return getGlobalDownloadsPath() + "Update" + File.separator;
        } else {
            return Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + APP_DIRECTORY + File.separator + "Update" + File.separator;
        }
    }

    public static void migrateStorage(XmppConnectionService xmppConnectionService) {
        new Thread(() -> {
            SharedPreferences getPrefs = PreferenceManager.getDefaultSharedPreferences(xmppConnectionService);
            String migrated = "MIGRATED_ANDROID_Q";
            final boolean isMigrated = getPrefs.getBoolean(migrated, false);
            if (!isMigrated) {
                try {
                    Log.d(Config.LOGTAG, "Migrating Media for Android 11");
                    final File oldAudiosDirectory = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "monocles chat" + File.separator + "Media" + File.separator + "monocles chat Audios" + File.separator);
                    final File newAudiosDirectory = new File(xmppConnectionService.getExternalMediaDirs()[0].getAbsolutePath() + File.separator + APP_DIRECTORY + " " + AUDIOS + File.separator);
                    final File oldFilesDirectory = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "monocles chat" + File.separator + "Media" + File.separator + "monocles chat Files" + File.separator);
                    final File newFilesDirectory = new File(xmppConnectionService.getExternalMediaDirs()[0].getAbsolutePath() + File.separator + APP_DIRECTORY + " " + FILES + File.separator);
                    final File oldImagesDirectory = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "monocles chat" + File.separator + "Media" + File.separator + "monocles chat Images" + File.separator);
                    final File newImagesDirectory = new File(xmppConnectionService.getExternalMediaDirs()[0].getAbsolutePath() + File.separator + APP_DIRECTORY + " " + IMAGES + File.separator);
                    final File oldVideosDirectory = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "monocles chat" + File.separator + "Media" + File.separator + "monocles chat Videos" + File.separator);
                    final File newVideosDirectory = new File(xmppConnectionService.getExternalMediaDirs()[0].getAbsolutePath() + File.separator + APP_DIRECTORY + " " + VIDEOS + File.separator);

                    final File oldDatabaseDirectory = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "monocles chat" + File.separator + "Database" + File.separator);
                    final File newDatabaseDirectory = new File(getBackupDirectory(null));

                    final File oldChatsDirectory = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "monocles chat" + File.separator + "Chats" + File.separator);
                    final File newChatsDirectory = new File(getAppLogsDirectory());

                    try {
                        moveDirectory(xmppConnectionService, oldAudiosDirectory, newAudiosDirectory);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    try {
                        moveDirectory(xmppConnectionService, oldFilesDirectory, newFilesDirectory);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    try {
                        moveDirectory(xmppConnectionService, oldImagesDirectory, newImagesDirectory);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    try {
                        moveDirectory(xmppConnectionService, oldVideosDirectory, newVideosDirectory);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (oldDatabaseDirectory.exists() && oldDatabaseDirectory.isDirectory()) {
                        newDatabaseDirectory.getParentFile().mkdirs();
                        final File[] files = oldDatabaseDirectory.listFiles();
                        if (files == null) {
                            Log.d(Config.LOGTAG, "No backups found in " + oldDatabaseDirectory);
                        }
                        if (oldDatabaseDirectory.renameTo(newDatabaseDirectory)) {
                            Log.d(Config.LOGTAG, "moved " + oldDatabaseDirectory.getAbsolutePath() + " to " + newDatabaseDirectory.getAbsolutePath());
                        }
                        if (newDatabaseDirectory.exists() && files != null) {
                            for (File file : files) {
                                moveFile(oldDatabaseDirectory.getAbsolutePath(), file.getName(), newDatabaseDirectory.getAbsolutePath());
                            }
                        }
                    }
                    if (oldChatsDirectory.exists() && oldChatsDirectory.isDirectory()) {
                        newChatsDirectory.getParentFile().mkdirs();
                        final File[] files = oldChatsDirectory.listFiles();
                        if (files == null) {
                            Log.d(Config.LOGTAG, "No chat logs found in " + oldChatsDirectory);
                        }
                        if (oldChatsDirectory.renameTo(newChatsDirectory)) {
                            Log.d(Config.LOGTAG, "moved " + oldChatsDirectory.getAbsolutePath() + " to " + newChatsDirectory.getAbsolutePath());
                        }
                        if (newChatsDirectory.exists() && files != null) {
                            for (File file : files) {
                                moveFile(oldChatsDirectory.getAbsolutePath(), file.getName(), newChatsDirectory.getAbsolutePath());
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    SharedPreferences.Editor e = getPrefs.edit();
                    e.putBoolean(migrated, true);
                    e.apply();
                }
            }
        }).start();
    }
}
