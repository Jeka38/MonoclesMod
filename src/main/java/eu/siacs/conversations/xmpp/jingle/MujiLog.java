package eu.siacs.conversations.xmpp.jingle;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.Config;

/**
 * Debug-only file log for Muji/Jingle signalling. Huawei/EMUI strips {@code Log.d} from logcat, so
 * this writes the relevant stanzas to {@code files/muji.log} where they can be read with
 * {@code adb shell run-as de.monocles.mod cat files/muji.log}. Disabled in release builds.
 */
public final class MujiLog {

    private static final long MAX_SIZE = 256 * 1024;

    private MujiLog() {}

    public static void log(final File filesDir, final String message) {
        if (!BuildConfig.DEBUG || filesDir == null) {
            return;
        }
        final File file = new File(filesDir, "muji.log");
        try {
            if (file.length() > MAX_SIZE) {
                try (final FileWriter truncate = new FileWriter(file, false)) {
                    truncate.write("");
                }
            }
            try (final FileWriter writer = new FileWriter(file, true)) {
                writer.write(
                        new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date()));
                writer.write(' ');
                writer.write(message);
                writer.write('\n');
            }
        } catch (final IOException e) {
            Log.w(Config.LOGTAG, "unable to write Muji log", e);
        }
    }
}
