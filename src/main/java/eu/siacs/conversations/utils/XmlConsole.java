package eu.siacs.conversations.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public final class XmlConsole {

    public static final String PREF_ENABLED = "enable_xml_console";
    private static final int MAX_ENTRIES = 2000;
    private static final SimpleDateFormat FORMAT =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    public interface Listener {
        void onEntry(String entry);
    }

    private static final List<String> entries = new ArrayList<>();
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static volatile boolean enabled = false;
    private static volatile boolean initialized = false;

    private XmlConsole() {}

    public static synchronized void init(final Context context) {
        if (context == null) {
            return;
        }
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        enabled = prefs.getBoolean(PREF_ENABLED, false);
        initialized = true;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static synchronized void setEnabled(final Context context, final boolean value) {
        enabled = value;
        initialized = true;
        if (context != null) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
            prefs.edit().putBoolean(PREF_ENABLED, value).apply();
        }
        logInternal(value ? "XML console enabled" : "XML console disabled");
    }

    public static void logOutgoing(final String xml) {
        log("OUT", xml);
    }

    public static void logIncoming(final String xml) {
        log("IN", xml);
    }

    public static void log(final String direction, final String xml) {
        if (!enabled || xml == null) {
            return;
        }
        final String ts;
        synchronized (FORMAT) {
            ts = FORMAT.format(new Date());
        }
        final String entry = ts + " " + direction + "\n" + xml;
        System.out.println("[XML-CONSOLE] " + direction + " " + xml);
        synchronized (entries) {
            entries.add(entry);
            while (entries.size() > MAX_ENTRIES) {
                entries.remove(0);
            }
        }
        for (final Listener listener : listeners) {
            listener.onEntry(entry);
        }
    }

    private static void logInternal(final String message) {
        final String ts;
        synchronized (FORMAT) {
            ts = FORMAT.format(new Date());
        }
        final String entry = ts + " SYS\n" + message;
        System.out.println("[XML-CONSOLE] SYS " + message);
        synchronized (entries) {
            entries.add(entry);
            while (entries.size() > MAX_ENTRIES) {
                entries.remove(0);
            }
        }
        for (final Listener listener : listeners) {
            listener.onEntry(entry);
        }
    }

    public static void addListener(final Listener listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    public static void removeListener(final Listener listener) {
        listeners.remove(listener);
    }

    public static synchronized List<String> getEntries() {
        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }

    public static synchronized void clear() {
        synchronized (entries) {
            entries.clear();
        }
        logInternal("XML console cleared");
    }

    public static synchronized File export(final File dir) throws IOException {
        if (dir == null) {
            throw new IOException("no directory");
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("could not create directory");
        }
        final String name;
        synchronized (FORMAT) {
            name = "xml-console-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".log";
        }
        final File file = new File(dir, name);
        try (final FileWriter writer = new FileWriter(file, false)) {
            synchronized (entries) {
                for (final String entry : entries) {
                    writer.write(entry);
                    writer.write("\n\n");
                }
            }
        }
        return file;
    }
}
