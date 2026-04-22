package eu.siacs.conversations.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ExceptionHelper {
    private static final String FILENAME = "stacktrace.txt";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

    public static void init(Context context) {
        if (Thread.getDefaultUncaughtExceptionHandler() instanceof ExceptionHandler) {
            return;
        }
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(context));
    }

    static void writeToStacktraceFile(Context context, String msg) {
        try {
            OutputStream os = context.openFileOutput(FILENAME, Context.MODE_PRIVATE);
            os.write(msg.getBytes());
            os.flush();
            os.close();
        } catch (IOException ignored) {
        }
        writeToLogFile(context, msg);
    }

    public static void writeToLogFile(Context context, String msg) {
        try {
            String path = StorageHelper.getGlobalDocumentsPath();
            if (path == null) return;
            File documentsDir = new File(path);
            File dir = documentsDir.getParentFile(); // Back up to 'monocles mod'
            if (dir == null) dir = documentsDir;
            dir.mkdirs();
            File logFile = new File(dir, "crash_log.txt");
            boolean isNew = !logFile.exists();
            FileOutputStream fos = new FileOutputStream(logFile, true);
            if (isNew) {
                String header = "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n" +
                                "Android: " + Build.VERSION.RELEASE + "\n";
                try {
                    PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                    header += "App version: " + pInfo.versionName + "\n";
                } catch (PackageManager.NameNotFoundException ignored) {}
                header += "----------------------------------------\n";
                fos.write(header.getBytes());
            }
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(new Date());
            fos.write((timestamp + " " + msg + "\n").getBytes());
            fos.flush();
            fos.close();
        } catch (IOException ignored) {}
    }
}
