package eu.siacs.conversations.ui.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Pair;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.xmpp.Jid;

public final class ClientIconUtils {
    public static final String CLIENT_ICONS_DIRECTORY = "client_icons";

    private ClientIconUtils() {
    }

    public static boolean applyRosterClientIcon(final ImageView imageView, final Contact contact) {
        if (contact == null) {
            return false;
        }
        final Pair<Map<String, String>, Map<String, String>> typeAndName = contact.getPresences().toTypeAndNameMap();
        final String selectedName = getClientNameForResource(typeAndName, contact.getLastResource());
        if (applyCustomIcon(imageView, selectedName)) {
            return true;
        }
        final Integer iconRes = getIconForResource(typeAndName, contact.getLastResource());
        if (iconRes == null) {
            return false;
        }
        imageView.setImageResource(iconRes);
        return true;
    }

    public static boolean applyMucUserClientIcon(final ImageView imageView, final MucOptions.User user) {
        if (user == null) {
            return false;
        }
        final Contact contact = user.getContact();
        if (contact == null) {
            return false;
        }
        final Pair<Map<String, String>, Map<String, String>> typeAndName = contact.getPresences().toTypeAndNameMap();
        String resource = null;
        final Jid fullJid = user.getFullJid();
        if (fullJid != null && !TextUtils.isEmpty(fullJid.getResource())) {
            resource = fullJid.getResource();
        }
        final String selectedName = getClientNameForResource(typeAndName, resource);
        if (applyCustomIcon(imageView, selectedName)) {
            return true;
        }
        final Integer iconRes = getIconForResource(typeAndName, resource);
        if (iconRes == null) {
            return false;
        }
        imageView.setImageResource(iconRes);
        return true;
    }

    public static Integer getRosterClientIconRes(final Contact contact) {
        if (contact == null) {
            return null;
        }
        final Pair<Map<String, String>, Map<String, String>> typeAndName = contact.getPresences().toTypeAndNameMap();
        return getIconForResource(typeAndName, contact.getLastResource());
    }

    public static Integer getMucUserClientIconRes(final MucOptions.User user) {
        if (user == null) {
            return null;
        }
        final Contact contact = user.getContact();
        if (contact == null) {
            return null;
        }
        final Pair<Map<String, String>, Map<String, String>> typeAndName = contact.getPresences().toTypeAndNameMap();
        final Jid fullJid = user.getFullJid();
        if (fullJid != null && !TextUtils.isEmpty(fullJid.getResource())) {
            final Integer icon = getIconForResource(typeAndName, fullJid.getResource());
            if (icon != null) {
                return icon;
            }
        }

        return getIconForResource(typeAndName, contact.getLastResource());
    }

    private static Integer getIconForResource(final Pair<Map<String, String>, Map<String, String>> typeAndName, final String resource) {
        final Map<String, String> types = typeAndName.first;
        final Map<String, String> names = typeAndName.second;
        if (types.isEmpty() && names.isEmpty()) {
            return null;
        }
        if (!TextUtils.isEmpty(resource)) {
            final Integer icon = getIconRes(types.get(resource), names.get(resource));
            if (icon != null) {
                return icon;
            }
        }
        for (Map.Entry<String, String> typeEntry : types.entrySet()) {
            final Integer icon = getIconRes(typeEntry.getValue(), names.get(typeEntry.getKey()));
            if (icon != null) {
                return icon;
            }
        }
        for (String name : names.values()) {
            final Integer icon = inferIconByClientName(name);
            if (icon != null) {
                return icon;
            }
        }
        return null;
    }

    private static String getClientNameForResource(final Pair<Map<String, String>, Map<String, String>> typeAndName, final String resource) {
        final Map<String, String> names = typeAndName.second;
        if (names.isEmpty()) {
            return null;
        }
        if (!TextUtils.isEmpty(resource) && names.containsKey(resource)) {
            return names.get(resource);
        }
        return names.values().iterator().next();
    }

    private static Integer getIconRes(final String rawType, final String rawName) {
        if (TextUtils.isEmpty(rawType)) {
            return inferIconByClientName(rawName);
        }
        switch (rawType.toLowerCase(Locale.ROOT)) {
            case "phone":
                return R.drawable.ic_client_phone;
            case "tablet":
                return R.drawable.ic_client_tablet;
            case "web":
                return R.drawable.ic_client_web;
            case "console":
                return R.drawable.ic_client_console;
            case "pc":
            default:
                return R.drawable.ic_client_pc;
        }
    }

    private static Integer inferIconByClientName(final String rawName) {
        if (TextUtils.isEmpty(rawName)) {
            return null;
        }
        final String name = rawName.toLowerCase(Locale.ROOT);
        if (name.contains("android") || name.contains("quicksy") || name.contains("conversations")
                || name.contains("monocles") || name.contains("cheogram") || name.contains("yaxim")
                || name.contains("blabber")) {
            return R.drawable.ic_client_phone;
        } else if (name.contains("web") || name.contains("browser")) {
            return R.drawable.ic_client_web;
        } else if (name.contains("gajim") || name.contains("psi") || name.contains("pidgin")
                || name.contains("dino") || name.contains("kaidan") || name.contains("poezio")
                || name.contains("profanity") || name.contains("beagle")) {
            return R.drawable.ic_client_pc;
        }
        return null;
    }

    private static boolean applyCustomIcon(final ImageView imageView, final String clientName) {
        if (TextUtils.isEmpty(clientName)) {
            return false;
        }
        final File iconsDir = new File(imageView.getContext().getFilesDir(), CLIENT_ICONS_DIRECTORY);
        if (!iconsDir.isDirectory()) {
            return false;
        }
        final File match = findBestIconFile(iconsDir, normalize(clientName));
        if (match == null) {
            return false;
        }
        final Bitmap bitmap = BitmapFactory.decodeFile(match.getAbsolutePath());
        if (bitmap == null) {
            return false;
        }
        imageView.setImageBitmap(bitmap);
        return true;
    }

    private static File findBestIconFile(final File dir, final String normalizedClientName) {
        if (TextUtils.isEmpty(normalizedClientName)) {
            return null;
        }
        final File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return null;
        }
        final ArrayList<File> candidates = new ArrayList<>();
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            final String normalizedFileName = normalize(stripExtension(file.getName()));
            if (TextUtils.isEmpty(normalizedFileName)) {
                continue;
            }
            if (normalizedFileName.equals(normalizedClientName)) {
                return file;
            }
            if (normalizedClientName.contains(normalizedFileName) || normalizedFileName.contains(normalizedClientName)) {
                candidates.add(file);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static String stripExtension(final String fileName) {
        final int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName;
        }
        return fileName.substring(0, dot);
    }

    private static String normalize(final String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }
}
