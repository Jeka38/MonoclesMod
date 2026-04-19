package eu.siacs.conversations.ui.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Pair;
import android.widget.ImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.entities.ServiceDiscoveryResult;
import eu.siacs.conversations.xmpp.Jid;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

public final class ClientIconUtils {
    public static final String CLIENT_ICONS_DIRECTORY = "client_icons";

    private ClientIconUtils() {
    }

    public static boolean applyRosterClientIcon(final ImageView imageView, final Contact contact) {
        if (contact == null) {
            return false;
        }
        final Pair<Map<String, String>, Map<String, String>> typeAndName = contact.getPresences().toTypeAndNameMap();
        if (applyCustomIcon(imageView, contact, contact.getLastResource())) {
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
        if (applyCustomIcon(imageView, contact, resource)) {
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

    private static boolean applyCustomIcon(final ImageView imageView, final Contact contact, final String resource) {
        final File iconsDir = new File(imageView.getContext().getFilesDir(), CLIENT_ICONS_DIRECTORY);
        if (!iconsDir.isDirectory()) {
            return false;
        }
        final File iconDefFile = findIconDefFile(iconsDir);
        final Set<String> candidates = buildXep0115Candidates(contact, resource);
        File match = null;
        if (iconDefFile != null && !candidates.isEmpty()) {
            match = findByIconDef(iconsDir, iconDefFile, candidates);
        }
        if (match == null) {
            final String clientName = inferClientName(contact, resource);
            if (!TextUtils.isEmpty(clientName)) {
                match = findBestIconFile(iconsDir, normalize(clientName));
            }
        }
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

    private static String inferClientName(final Contact contact, final String resource) {
        final Pair<Map<String, String>, Map<String, String>> typeAndName = contact.getPresences().toTypeAndNameMap();
        final Map<String, String> names = typeAndName.second;
        if (names.isEmpty()) {
            return null;
        }
        if (!TextUtils.isEmpty(resource) && names.containsKey(resource)) {
            return names.get(resource);
        }
        return names.values().iterator().next();
    }

    private static Set<String> buildXep0115Candidates(final Contact contact, final String resource) {
        final Set<String> candidates = new HashSet<>();
        final Presence primary = getPresence(contact, resource);
        if (primary != null) {
            addCandidate(candidates, primary.getNode());
            addCandidate(candidates, primary.getVer());
            addCandidate(candidates, primary.getHash());
            final ServiceDiscoveryResult disco = primary.getServiceDiscoveryResult();
            if (disco != null && !disco.getIdentities().isEmpty()) {
                final ServiceDiscoveryResult.Identity identity = disco.getIdentities().get(0);
                addCandidate(candidates, identity.getName());
                addCandidate(candidates, identity.getType());
                addCandidate(candidates, identity.getCategory());
            }
        }
        addCandidate(candidates, resource);
        final String fallbackName = inferClientName(contact, resource);
        addCandidate(candidates, fallbackName);
        return candidates;
    }

    private static Presence getPresence(final Contact contact, final String resource) {
        if (!TextUtils.isEmpty(resource)) {
            final Presence direct = contact.getPresences().get(resource);
            if (direct != null) {
                return direct;
            }
        }
        for (Map.Entry<String, Presence> entry : contact.getPresences().getPresencesMap().entrySet()) {
            if (entry.getValue() != null) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static void addCandidate(final Set<String> candidates, final String value) {
        if (!TextUtils.isEmpty(value)) {
            candidates.add(normalize(value));
        }
    }

    private static File findByIconDef(final File iconsDir, final File iconDefFile, final Set<String> candidates) {
        final List<IconDefEntry> entries = parseIconDef(iconDefFile);
        for (IconDefEntry entry : entries) {
            for (String matcher : entry.matchers) {
                for (String candidate : candidates) {
                    if (candidate.contains(matcher) || matcher.contains(candidate)) {
                        final File resolved = resolveIconFile(iconsDir, entry.fileName);
                        if (resolved != null) {
                            return resolved;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static File resolveIconFile(final File iconsDir, final String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return null;
        }
        final File direct = new File(iconsDir, fileName);
        if (direct.isFile()) {
            return direct;
        }
        final String justName = fileName.contains("/") ? fileName.substring(fileName.lastIndexOf('/') + 1) : fileName;
        final File fallback = new File(iconsDir, justName);
        return fallback.isFile() ? fallback : null;
    }

    private static File findIconDefFile(final File iconsDir) {
        final File direct = new File(iconsDir, "icondef.xml");
        if (direct.isFile()) {
            return direct;
        }
        final File[] files = iconsDir.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                return file;
            }
        }
        return null;
    }

    private static List<IconDefEntry> parseIconDef(final File iconDefFile) {
        final ArrayList<IconDefEntry> entries = new ArrayList<>();
        try (InputStream inputStream = new FileInputStream(iconDefFile)) {
            final XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            final XmlPullParser parser = factory.newPullParser();
            parser.setInput(inputStream, "UTF-8");
            IconDefEntry current = null;
            String currentTag = null;
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    final String tag = parser.getName();
                    if ("icon".equalsIgnoreCase(tag)) {
                        current = new IconDefEntry();
                    } else if (current != null) {
                        currentTag = tag.toLowerCase(Locale.ROOT);
                    }
                } else if (event == XmlPullParser.TEXT) {
                    if (current != null && currentTag != null) {
                        final String text = parser.getText();
                        if ("object".equals(currentTag)) {
                            current.fileName = text == null ? null : text.trim();
                        } else {
                            final String normalized = normalize(text);
                            if (!TextUtils.isEmpty(normalized)) {
                                current.matchers.add(normalized);
                            }
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    final String tag = parser.getName();
                    if ("icon".equalsIgnoreCase(tag) && current != null) {
                        if (!TextUtils.isEmpty(current.fileName) && !current.matchers.isEmpty()) {
                            entries.add(current);
                        }
                        current = null;
                    }
                    currentTag = null;
                }
                event = parser.next();
            }
        } catch (Exception ignore) {
            // fallback to generic name matching
        }
        return entries;
    }

    private static class IconDefEntry {
        String fileName;
        final Set<String> matchers = new HashSet<>();
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
