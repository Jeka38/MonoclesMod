package eu.siacs.conversations.utils;

import android.content.Context;

import com.google.common.io.ByteStreams;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
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
    }

    public static boolean hasStacktrace(Context context) {
        return context.getFileStreamPath(FILENAME).exists();
    }

    public static String getStacktrace(Context context) {
        try (InputStream is = new FileInputStream(context.getFileStreamPath(FILENAME))) {
            return new String(ByteStreams.toByteArray(is));
        } catch (IOException e) {
            return null;
        }
    }

    public static void deleteStacktrace(Context context) {
        context.deleteFile(FILENAME);
    }
}
