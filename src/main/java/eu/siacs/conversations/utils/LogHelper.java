package eu.siacs.conversations.utils;

import android.content.Context;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

public class LogHelper {
    private static Context context;

    public static void init(Context context) {
        LogHelper.context = context.getApplicationContext();
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
        writeToFile("ERROR", tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable tr) {
        Log.e(tag, msg, tr);
        writeToFile("ERROR", tag, msg, tr);
    }

    public static void w(String tag, String msg) {
        Log.w(tag, msg);
        writeToFile("WARN", tag, msg, null);
    }

    public static void w(String tag, String msg, Throwable tr) {
        Log.w(tag, msg, tr);
        writeToFile("WARN", tag, msg, tr);
    }

    public static void i(String tag, String msg) {
        Log.i(tag, msg);
    }

    public static void i(String tag, String msg, Throwable tr) {
        Log.i(tag, msg, tr);
    }

    public static void d(String tag, String msg) {
        Log.d(tag, msg);
    }

    public static void d(String tag, String msg, Throwable tr) {
        Log.d(tag, msg, tr);
    }

    public static void v(String tag, String msg) {
        Log.v(tag, msg);
    }

    public static void v(String tag, String msg, Throwable tr) {
        Log.v(tag, msg, tr);
    }

    private static void writeToFile(String level, String tag, String msg, Throwable tr) {
        if (context == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(level).append("] ").append(tag).append(": ").append(msg);
        if (tr != null) {
            sb.append("\n");
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            tr.printStackTrace(pw);
            sb.append(sw.toString());
        }
        ExceptionHelper.writeToLogFile(context, sb.toString());
    }
}
