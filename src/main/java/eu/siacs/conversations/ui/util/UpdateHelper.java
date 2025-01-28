package eu.siacs.conversations.ui.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.WelcomeActivity;
import eu.siacs.conversations.utils.ThemeHelper;
import me.drakeet.support.toast.ToastCompat;

public class UpdateHelper {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
    private static final String INSTALL_DATE = "2020-11-01";
    private static final String monocles_message = "MONOCLES.IM_UPDATE_MESSAGE";
    private static boolean moveData = true;
    private static boolean dataMoved = false;

    private static final File PAM_MainDirectory = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Monocles Messenger/");
    private static final File monocles_MainDirectory = new File(Environment.getExternalStorageDirectory() + "/monocles chat/");
    private static final File PAM_PicturesDirectory = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Monocles Messenger/Media/Monocles Messenger Images/");
    private static final File PAM_FilesDirectory = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Monocles Messenger/Media/Monocles Messenger Files/");
    private static final File PAM_AudiosDirectory = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Monocles Messenger/Media/Monocles Messenger Audios/");
    private static final File PAM_VideosDirectory = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Monocles Messenger/Media/Monocles Messenger Videos/");

    public static void showPopup(Activity activity) {
        new Thread(new showPopupFinisher(activity)).start();
    }

    private static class showPopupFinisher implements Runnable {

        private final WeakReference<Activity> activityReference;

        private showPopupFinisher(Activity activity) {

            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        public void run() {
            final Activity activity = activityReference.get();
            if (activity == null) {
                return;
            }
            updateInstalled(activity);
            final SharedPreferences getPrefs = PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
            final String Message = "message_shown_" + monocles_message;
            final boolean SHOW_MESSAGE = getPrefs.getBoolean(Message, true);
            if (activity instanceof ConversationsActivity && (SHOW_MESSAGE && updateInstalled(activity) && Config.SHOW_MIGRATION_INFO)) {
                Log.d(Config.LOGTAG, "UpdateHelper: installed update from Monocles Messenger to monocles chat");
                activity.runOnUiThread(() -> {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setTitle(activity.getString(R.string.title_activity_updater));
                    builder.setMessage(activity.getString(R.string.updated_to_monocles));
                    builder.setCancelable(false);
                    builder.setPositiveButton(R.string.ok, (dialog, which) -> SaveMessageShown(activity, monocles_message)
                    );
                    builder.create().show();
                });
            } else if (activity instanceof WelcomeActivity && (SHOW_MESSAGE && newInstalled(activity) && !Config.SHOW_MIGRATION_INFO && PAMInstalled(activity))) {
                Log.d(Config.LOGTAG, "UpdateHelper: new installed monocles chat");
                showNewInstalledDialog(activity);
            }
        }
    }

    private static void showNewInstalledDialog(Activity activity) {
        checkOldData();
        activity.runOnUiThread(() -> {
            if (dataMoved) {
                ToastCompat.makeText(activity, R.string.data_successfully_moved, ToastCompat.LENGTH_LONG).show();
            }
            final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(activity.getString(R.string.title_activity_updater));
            builder.setMessage(activity.getString(R.string.updated_to_monocles_google));
            builder.setCancelable(false);
            builder.setPositiveButton(R.string.link, (dialog, which) -> {
                        SaveMessageShown(activity, monocles_message);
                        try {
                            final Uri uri = Uri.parse(Config.migrationURL);
                            try {
                                CustomTab.openTab(activity, uri, ThemeHelper.isDark(ThemeHelper.find(activity)));
                            } catch (Exception e) {
                                ToastCompat.makeText(activity, R.string.no_application_found_to_open_link, ToastCompat.LENGTH_SHORT).show();
                            }
                            showNewInstalledDialog(activity);
                        } catch (Exception e) {
                            ToastCompat.makeText(activity, R.string.no_application_found_to_open_link, ToastCompat.LENGTH_LONG).show();
                            showNewInstalledDialog(activity);
                        }
                    }
            );
            builder.setNegativeButton(R.string.move_data, (dialog, which) -> {
                        SaveMessageShown(activity, monocles_message);
                        try {
                            if (!moveData) {
                                ToastCompat.makeText(activity, R.string.error_moving_data, ToastCompat.LENGTH_LONG).show();
                            } else {
                                moveData_PAM_monocles();
                            }
                            showNewInstalledDialog(activity);
                        } catch (Exception e) {
                            ToastCompat.makeText(activity, R.string.error_moving_data, ToastCompat.LENGTH_LONG).show();
                            showNewInstalledDialog(activity);
                        }
                    }
            );
            builder.setNeutralButton(R.string.done, (dialog, which) -> SaveMessageShown(activity, monocles_message)
            );
            AlertDialog dialog = builder.create();
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!dataMoved);
        });
    }

    private static void checkOldData() {
        if (PAM_MainDirectory.exists() && PAM_MainDirectory.isDirectory()) {
            if (monocles_MainDirectory.exists() && monocles_MainDirectory.isDirectory()) {
                moveData = false;
            } else {
                moveData = true;
            }
        } else {
            moveData = false;
        }
        Log.d(Config.LOGTAG, "UpdateHelper: old data available: " + moveData);
    }

    public static void moveData_PAM_monocles() {
        if (PAM_PicturesDirectory.exists() && PAM_PicturesDirectory.isDirectory()) {
            final File newPicturesDirectory = new File(Environment.getExternalStorageDirectory() + "/Monocles Messenger/Media/monocles chat Images/");
            newPicturesDirectory.getParentFile().mkdirs();
            final File[] files = PAM_PicturesDirectory.listFiles();
            if (files == null) {
                return;
            }
            if (PAM_PicturesDirectory.renameTo(newPicturesDirectory)) {
                Log.d(Config.LOGTAG, "moved " + PAM_PicturesDirectory.getAbsolutePath() + " to " + newPicturesDirectory.getAbsolutePath());
            } else {
                Log.d(Config.LOGTAG, "could not move " + PAM_PicturesDirectory.getAbsolutePath() + " to " + newPicturesDirectory.getAbsolutePath());
            }
        }
        if (PAM_FilesDirectory.exists() && PAM_FilesDirectory.isDirectory()) {
            final File newFilesDirectory = new File(Environment.getExternalStorageDirectory() + "/Monocles Messenger/Media/monocles chat Files/");
            newFilesDirectory.mkdirs();
            final File[] files = PAM_FilesDirectory.listFiles();
            if (files == null) {
                return;
            }
            if (PAM_FilesDirectory.renameTo(newFilesDirectory)) {
                Log.d(Config.LOGTAG, "moved " + PAM_FilesDirectory.getAbsolutePath() + " to " + newFilesDirectory.getAbsolutePath());
            } else {
                Log.d(Config.LOGTAG, "could not move " + PAM_FilesDirectory.getAbsolutePath() + " to " + newFilesDirectory.getAbsolutePath());
            }
        }
        if (PAM_AudiosDirectory.exists() && PAM_AudiosDirectory.isDirectory()) {
            final File newAudiosDirectory = new File(Environment.getExternalStorageDirectory() + "/Monocles Messenger/Media/monocles chat Audios/");
            newAudiosDirectory.mkdirs();
            final File[] files = PAM_AudiosDirectory.listFiles();
            if (files == null) {
                return;
            }
            if (PAM_AudiosDirectory.renameTo(newAudiosDirectory)) {
                Log.d(Config.LOGTAG, "moved " + PAM_AudiosDirectory.getAbsolutePath() + " to " + newAudiosDirectory.getAbsolutePath());
            } else {
                Log.d(Config.LOGTAG, "could not move " + PAM_AudiosDirectory.getAbsolutePath() + " to " + newAudiosDirectory.getAbsolutePath());
            }
        }
        if (PAM_VideosDirectory.exists() && PAM_VideosDirectory.isDirectory()) {
            final File newVideosDirectory = new File(Environment.getExternalStorageDirectory() + "/Monocles Messenger/Media/monocles chat Videos/");
            newVideosDirectory.mkdirs();
            final File[] files = PAM_VideosDirectory.listFiles();
            if (files == null) {
                return;
            }
            if (PAM_VideosDirectory.renameTo(newVideosDirectory)) {
                Log.d(Config.LOGTAG, "moved " + PAM_VideosDirectory.getAbsolutePath() + " to " + newVideosDirectory.getAbsolutePath());
            } else {
                Log.d(Config.LOGTAG, "could not move " + PAM_VideosDirectory.getAbsolutePath() + " to " + newVideosDirectory.getAbsolutePath());
            }
        }
        if (PAM_MainDirectory.exists() && PAM_MainDirectory.isDirectory()) {
            monocles_MainDirectory.mkdirs();
            final File[] files = PAM_MainDirectory.listFiles();
            if (files == null) {
                return;
            }
            if (PAM_MainDirectory.renameTo(monocles_MainDirectory)) {
                dataMoved = true;
                Log.d(Config.LOGTAG, "moved " + PAM_MainDirectory.getAbsolutePath() + " to " + monocles_MainDirectory.getAbsolutePath());
            } else {
                Log.d(Config.LOGTAG, "could not move " + PAM_MainDirectory.getAbsolutePath() + " to " + monocles_MainDirectory.getAbsolutePath());
            }
        }
    }

    private static boolean updateInstalled(Activity activity) {
        PackageManager pm = activity.getPackageManager();
        PackageInfo packageInfo;
        String firstInstalled = null;
        String lastUpdate = null;
        Date updateDate = null;
        Date lastUpdateDate = null;
        try {
            packageInfo = pm.getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNATURES);
            firstInstalled = DATE_FORMAT.format(new Date(packageInfo.firstInstallTime));
            lastUpdate = DATE_FORMAT.format(new Date(packageInfo.lastUpdateTime));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        try {
            updateDate = DATE_FORMAT.parse(INSTALL_DATE);
            if (lastUpdate != null) {
                lastUpdateDate = DATE_FORMAT.parse(lastUpdate);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        if (updateDate != null) {
            if (lastUpdateDate != null) {
                if (firstInstalled.equals(lastUpdate)) {
                    SaveMessageShown(activity, monocles_message);
                    return false;
                } else {
                    if (lastUpdateDate.getTime() <= updateDate.getTime()) {
                        return true;
                    } else {
                        SaveMessageShown(activity, monocles_message);
                        return false;
                    }
                }
            } else {
                SaveMessageShown(activity, monocles_message);
                return false;
            }
        } else {
            SaveMessageShown(activity, monocles_message);
            return false;
        }
    }

    private static boolean newInstalled(Activity activity) {
        PackageManager pm = activity.getPackageManager();
        PackageInfo packageInfo;
        String firstInstalled = null;
        Date installDate = null;
        Date firstInstalledDate = null;
        try {
            packageInfo = pm.getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNATURES);
            firstInstalled = DATE_FORMAT.format(new Date(packageInfo.firstInstallTime));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        try {
            installDate = DATE_FORMAT.parse(INSTALL_DATE);
            if (firstInstalled != null) {
                firstInstalledDate = DATE_FORMAT.parse(firstInstalled);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return installDate != null && firstInstalledDate != null && firstInstalledDate.getTime() >= installDate.getTime();
    }

    private static boolean PAMInstalled(Activity activity) {
        PackageManager pm = activity.getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo("de.pixart.messenger", 0)).equals("Monocles Messenger");
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static void SaveMessageShown(Context context, String message) {
        SharedPreferences getPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String Message = "message_shown_" + message;
        SharedPreferences.Editor e = getPrefs.edit();
        e.putBoolean(Message, false);
        e.apply();
    }
}