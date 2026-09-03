package de.monocles.mod;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

import de.monocles.mod.ui.CrashActivity;
import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.Config;

public class XmppApplication extends Application {

    public static final String EXTRA_CRASH_REPORT = "crash_report";

    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            final String report = buildReport(thread, throwable);
            Log.e(Config.LOGTAG, "uncaught exception", throwable);
            final Intent intent = new Intent(XmppApplication.this, CrashActivity.class);
            intent.putExtra(EXTRA_CRASH_REPORT, report);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            try {
                startActivity(intent);
            } catch (final Exception e) {
                Log.e(Config.LOGTAG, "unable to start crash activity", e);
            }
            try {
                Thread.sleep(800);
            } catch (final InterruptedException ignored) {
            }
            Process.killProcess(Process.myPid());
        });
    }

    private static String buildReport(final Thread thread, final Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        pw.println("Version: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        pw.println("Thread : " + thread.getName());
        pw.println("Device : " + Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")");
        pw.println();
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}
