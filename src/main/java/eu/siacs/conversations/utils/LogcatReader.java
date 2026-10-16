package eu.siacs.conversations.utils;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import eu.siacs.conversations.Config;

/**
 * Reads the device logcat from inside the app, so a log can be inspected and shared without ADB
 * (needed on devices such as Huawei/EMUI where ADB is often unavailable). {@code logcat -d} dumps the
 * buffer non-destructively; reading the log needs no special permission for the app's own entries.
 */
public final class LogcatReader {

    private LogcatReader() {}

    /** A snapshot of the logcat buffer, oldest entries first. */
    public static final class Result {
        public final String text;
        public final boolean ok;
        @Nullable public final String error;

        private Result(final String text, final boolean ok, @Nullable final String error) {
            this.text = text;
            this.ok = ok;
            this.error = error;
        }

        static Result ok(final String text) {
            return new Result(text, true, null);
        }

        static Result failure(final String error) {
            return new Result("", false, error);
        }
    }

    /**
     * Dumps the logcat buffer (up to {@code maxLines} newest lines).
     *
     * @param maxLines hard cap so a huge buffer does not blow up the TextView; the newest lines win.
     * @param appOnly restrict to this app's own log entries; {@code false} dumps the whole buffer.
     */
    @NonNull
    public static Result read(final int maxLines, final boolean appOnly) {
        final List<String> command = new ArrayList<>();
        command.add("logcat");
        command.add("-d");
        command.add("-v");
        command.add("threadtime");
        boolean tagFilter = false;
        if (appOnly && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // --pid was added in Android 7; the tag can contain a space so it makes a poor filter.
            command.add("--pid=" + android.os.Process.myPid());
        } else if (appOnly) {
            // Older devices without --pid: filter in-process by the log tag instead.
            tagFilter = true;
        }
        if (maxLines > 0) {
            command.add("-t");
            command.add(Integer.toString(maxLines));
        }
        final Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (final Exception e) {
            return Result.failure(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        final StringBuilder builder = new StringBuilder();
        try (final BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (tagFilter && !line.contains(Config.LOGTAG)) {
                    continue;
                }
                builder.append(line).append('\n');
            }
        } catch (final Exception e) {
            return Result.failure(e.getMessage() == null ? e.toString() : e.getMessage());
        } finally {
            process.destroy();
            try {
                process.waitFor(1, TimeUnit.SECONDS);
            } catch (final InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        return Result.ok(builder.toString());
    }
}
