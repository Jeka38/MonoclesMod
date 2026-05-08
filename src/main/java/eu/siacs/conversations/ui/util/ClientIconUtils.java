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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Collections;
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

    private static final List<ClientRule> CLIENT_ICON_RULES = createClientRules();
    private static final List<ClientRule> GENERIC_ICON_RULES = createGenericRules();

    public static boolean applyRosterClientIcon(final ImageView imageView, final Contact contact) {
        if (contact == null) {
            return false;
        }
        final Integer capsIcon = inferIconFromPresence(contact, contact.getLastResource());
        if (capsIcon != null) {
            imageView.setImageResource(capsIcon);
            return true;
        }
        final Pair<Map<String, String>, Map<String, String>> typeAndName = contact.getPresences().toTypeAndNameMap();
        if (applyCustomIcon(imageView, contact, contact.getLastResource())) {
            return true;
        }
        final Integer iconRes = getIconForResource(typeAndName, contact.getLastResource(), contact.getSoftwareVersion());
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
        final Integer occupantIcon = inferIconFromMucPresence(user);
        if (occupantIcon != null) {
            imageView.setImageResource(occupantIcon);
            return true;
        }
        final String softwareVersion = user.getSoftwareVersion();
        if (!TextUtils.isEmpty(softwareVersion)) {
            final Integer icon = inferIconByClientName(softwareVersion);
            if (icon != null) {
                imageView.setImageResource(icon);
                return true;
            }
        }
        Contact contact = user.getContact();
        if (contact == null && user.getRealJid() != null) {
            contact = user.getAccount().getRoster().getContact(user.getRealJid());
        }
        final boolean applied = applyRosterClientIcon(imageView, contact);
        if (applied) {
            return true;
        }
        imageView.setImageResource(R.drawable.ic_client_pc);
        return true;
    }

    public static Integer getRosterClientIconRes(final Contact contact) {
        if (contact == null) {
            return null;
        }
        final Integer capsIcon = inferIconFromPresence(contact, contact.getLastResource());
        if (capsIcon != null) {
            return capsIcon;
        }
        final Pair<Map<String, String>, Map<String, String>> typeAndName = contact.getPresences().toTypeAndNameMap();
        return getIconForResource(typeAndName, contact.getLastResource(), contact.getSoftwareVersion());
    }

    public static Integer getMucUserClientIconRes(final MucOptions.User user) {
        if (user == null) {
            return null;
        }
        final Integer occupantIcon = inferIconFromMucPresence(user);
        if (occupantIcon != null) {
            return occupantIcon;
        }
        Contact contact = user.getContact();
        if (contact == null && user.getRealJid() != null) {
            contact = user.getAccount().getRoster().getContact(user.getRealJid());
        }
        final Integer icon = getRosterClientIconRes(contact);
        return icon != null ? icon : R.drawable.ic_client_pc;
    }

    private static Integer getIconForResource(final Pair<Map<String, String>, Map<String, String>> typeAndName, final String resource, final String softwareVersion) {
        final Map<String, String> types = typeAndName.first;
        final Map<String, String> names = typeAndName.second;
        if (!TextUtils.isEmpty(resource)) {
            final Integer icon = getIconRes(types.get(resource), names.get(resource));
            if (icon != null) {
                return icon;
            }
        }
        if (types.isEmpty() && names.isEmpty() && TextUtils.isEmpty(softwareVersion)) {
            return null;
        }
        final Integer versionIcon = inferIconByClientName(softwareVersion);
        if (versionIcon != null) {
            return versionIcon;
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
        final Integer clientSpecific = inferIconByClientName(rawName);
        if (clientSpecific != null) {
            return clientSpecific;
        }
        if (TextUtils.isEmpty(rawType)) {
            return null;
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
        final Integer mappedIcon = matchClientRule(name);
        if (mappedIcon != null) {
            return mappedIcon;
        }
        return matchGenericClientRule(name);
    }

    private static Integer matchClientRule(final String name) {
        for (ClientRule rule : CLIENT_ICON_RULES) {
            if (rule.matches(name)) {
                return rule.iconRes;
            }
        }
        return null;
    }

    private static Integer matchGenericClientRule(final String name) {
        for (ClientRule rule : GENERIC_ICON_RULES) {
            if (rule.matches(name)) {
                return rule.iconRes;
            }
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

    public static String getSoftwareVersion(final Contact contact) {
        if (contact == null) {
            return null;
        }
        final String version = contact.getSoftwareVersion();
        if (!TextUtils.isEmpty(version)) {
            return version;
        }
        return inferClientName(contact, contact.getLastResource());
    }

    public static String getSoftwareVersion(final MucOptions.User user) {
        if (user == null) {
            return null;
        }
        final String softwareVersion = user.getSoftwareVersion();
        if (!TextUtils.isEmpty(softwareVersion)) {
            return softwareVersion;
        }
        final Contact contact = user.getContact();
        if (contact != null) {
            final String version = contact.getSoftwareVersion();
            if (!TextUtils.isEmpty(version)) {
                return version;
            }
        }
        final Presence presence = user.getPresence();
        if (presence != null) {
            final ServiceDiscoveryResult disco = presence.getServiceDiscoveryResult();
            if (disco != null) {
                for (ServiceDiscoveryResult.Identity identity : disco.getIdentities()) {
                    if (!TextUtils.isEmpty(identity.getName())) {
                        return identity.getName();
                    }
                }
            }
        }
        return null;
    }

    public static String inferClientName(final Contact contact, final String resource) {
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
        final Map<String, Presence> presencesSnapshot = new HashMap<>(contact.getPresences().getPresencesMap());
        for (Presence value : presencesSnapshot.values()) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Integer inferIconFromPresence(final Contact contact, final String resource) {
        final Presence presence = getPresence(contact, resource);
        if (presence == null) {
            return null;
        }
        final Integer nodeIcon = inferIconByClientName(presence.getNode());
        if (nodeIcon != null) {
            return nodeIcon;
        }
        return inferIconByClientName(presence.getVer());
    }

    private static Integer inferIconFromMucPresence(final MucOptions.User user) {
        final Presence presence = user.getPresence();
        if (presence == null) {
            return null;
        }
        final Integer nodeIcon = inferIconByClientName(presence.getNode());
        if (nodeIcon != null) {
            return nodeIcon;
        }
        return inferIconByClientName(presence.getVer());
    }

    private static List<ClientRule> createClientRules() {
        final ArrayList<ClientRule> rules = new ArrayList<>();
        rules.add(rule(R.drawable.client_adium, "adium"));
        rules.add(rule(R.drawable.client_aceim, "github.com/snuk182/aceim"));
        rules.add(rule(R.drawable.client_another, "dev.narayana.im/narayana/conversations-classic", "conversations-classic", "conversations classic"));
        rules.add(rule(R.drawable.client_aqq, "aqq.eu"));
        rules.add(rule(R.drawable.client_bayan, "barobin.com/caps"));
        rules.add(rule(R.drawable.client_beem, "beem-project.com"));
        rules.add(rule(R.drawable.client_bitlbee, "bitlbee.org/xmpp/caps"));
        rules.add(rule(R.drawable.client_blabber, "blabber"));
        rules.add(rule(R.drawable.client_blacksmith_bot, "simpleapps.ru/caps", "blacksmith-2.googlecode.com/svn/", "matrix.bz/safety", "matrix.bz"));
        rules.add(rule(R.drawable.client_bluejabb, "bluejabb"));
        rules.add(rule(R.drawable.client_bombusmod, "bombusmod.net.ru", "github.com/bombusmod/caps"));
        rules.add(rule(R.drawable.client_bombusqd, "bombusmod-qd.wen.ru"));
        rules.add(rule(R.drawable.client_bombusng, "bombus-im.org/ng", "bombus-ng"));
        rules.add(rule(R.drawable.client_bombuspl, "bombus.pl"));
        rules.add(rule(R.drawable.client_bombusplus, "bombus+", "voffk.org.ru"));
        rules.add(rule(R.drawable.client_bombus, "bombus-im.org/java"));
        rules.add(rule(R.drawable.client_dino, "dino-im.org", "dino.im", "dino"));
        rules.add(rule(R.drawable.client_exodus, "exodus.jabberstudio.org/caps"));
        rules.add(rule(R.drawable.client_eyecu, "eyecu.ru"));
        rules.add(rule(R.drawable.client_fasttext, "urn:xmpp:rtt:0"));
        rules.add(rule(R.drawable.client_fj, "jabga.ru"));
        rules.add(rule(R.drawable.client_freomessenger, "freomessenger.com/caps"));
        rules.add(rule(R.drawable.client_freq, "freq-bot.net"));
        rules.add(rule(R.drawable.client_cheogram, "cheogram"));
        rules.add(rule(R.drawable.client_climm, "climm.org/xmpp/caps"));
        rules.add(rule(R.drawable.client_coccinella, "coccinella.sourceforge.net/protocol/caps"));
        rules.add(rule(R.drawable.client_con0pro, "c0nnect.de", "c0nnecteasy"));
        rules.add(rule(R.drawable.client_conv6ations, "sum7.eu", "conv6ations"));
        rules.add(rule(R.drawable.client_conversations, "conversations.im", "conversations"));
        rules.add(rule(R.drawable.client_conversations_mod, "github.com/jeka38/conversations-classic-mod"));
        rules.add(rule(R.drawable.client_conversations_old, "dev.narayana.im/narayana/conversations-classic", "conversations-classic", "conversations classic"));
        rules.add(rule(R.drawable.client_gajim, "gajim"));
        rules.add(rule(R.drawable.client_gmail, "gmail", "mail.google.com"));
        rules.add(rule(R.drawable.client_isida, "isida-bot.com", "isida"));
        rules.add(rule(R.drawable.client_jabbim, "jabbim"));
        rules.add(rule(R.drawable.client_jabbroid, "jabbroid.akuz.de/caps"));
        rules.add(rule(R.drawable.client_jajc, "jajc.jrudevels.org/caps"));
        rules.add(rule(R.drawable.client_jimm, "jimm.net.ru/caps"));
        rules.add(rule(R.drawable.client_jitsi, "jitsi.org"));
        rules.add(rule(R.drawable.client_kadu, "kadu.im/caps"));
        rules.add(rule(R.drawable.client_jtalk, "jtalk.ustyugov.net/caps"));
        rules.add(rule(R.drawable.client_juick, "juick", "juick.com/caps", "xmpp.rocks"));
        rules.add(rule(R.drawable.client_kopete, "kopete.kde.org/jabber/caps"));
        rules.add(rule(R.drawable.client_lampiro, "bluendo.com/protocol/caps"));
        rules.add(rule(R.drawable.client_leechcraft, "leechcraft"));
        rules.add(rule(R.drawable.client_loqui, "loqui.im"));
        rules.add(rule(R.drawable.client_mchat, "mchat"));
        rules.add(rule(R.drawable.client_miranda, "miranda-im.org/caps", "miranda.sourceforge.net"));
        rules.add(rule(R.drawable.client_miranda_ng, "miranda-ng.org/caps"));
        rules.add(rule(R.drawable.client_mandarin, "tomclaw.com/mandarin_im/caps"));
        rules.add(rule(R.drawable.client_mcabber, "mcabber"));
        rules.add(rule(R.drawable.client_monal, "monal.im/caps"));
        rules.add(rule(R.drawable.client_monocles, "monocles"));
        rules.add(rule(R.drawable.client_movim, "moxl.movim.eu", "movim"));
        rules.add(rule(R.drawable.client_nimbuzz, "nimbuzz"));
        rules.add(rule(R.drawable.client_poezio_new, "slixmpp.com/ver/"));
        rules.add(rule(R.drawable.client_profanity, "profanity"));
        rules.add(rule(R.drawable.client_psi, "psi-im.org", "psi"));
        rules.add(rule(R.drawable.client_psiplus, "psi+", "psi-dev", "psi-plus.com"));
        rules.add(rule(R.drawable.client_qip, "qip"));
        rules.add(rule(R.drawable.client_qip2010, "2010.qip.ru/caps"));
        rules.add(rule(R.drawable.client_qippda, "pda.qip.ru"));
        rules.add(rule(R.drawable.client_pako, "pako.googlecode.com"));
        rules.add(rule(R.drawable.client_pandion, "pandion.im"));
        rules.add(rule(R.drawable.client_pidgin, "pidgin"));
        rules.add(rule(R.drawable.client_pixart, "jabber.pix-art.de", "pix-art messenger"));
        rules.add(rule(R.drawable.client_poezio, "poez.io", "poezio"));
        rules.add(rule(R.drawable.client_oneteam, "oneteam.im/caps"));
        rules.add(rule(R.drawable.client_oneteamiphone, "oneteam_iphone"));
        rules.add(rule(R.drawable.client_qt, "code.google.com/p/qxmpp"));
        rules.add(rule(R.drawable.client_qutim, "qutim.org"));
        rules.add(rule(R.drawable.client_riddim, "riddim"));
        rules.add(rule(R.drawable.client_sawim, "sawim.ru/caps"));
        rules.add(rule(R.drawable.client_xabber, "www.igniterealtime.org/projects/smack/", "xabber"));
        rules.add(rule(R.drawable.client_secugab, "conversions.fjsdevelopment.weebly.com"));
        rules.add(rule(R.drawable.client_sj, "safetyjabber.com/caps"));
        rules.add(rule(R.drawable.client_slick, "www.lonelycatgames.com/slick/caps"));
        rules.add(rule(R.drawable.client_smuxi, "smuxi.im"));
        rules.add(rule(R.drawable.client_swift, "swift.im"));
        rules.add(rule(R.drawable.client_talkonaut, "google.com/xmpp/client/caps", "talkonaut"));
        rules.add(rule(R.drawable.client_gtalk, "talk.google.com", "gtalk"));
        rules.add(rule(R.drawable.client_tigase, "tigase.org/messenger"));
        rules.add(rule(R.drawable.client_tkabber, "tkabber.jabber.ru/"));
        rules.add(rule(R.drawable.client_trillian, "trillian.im/caps"));
        rules.add(rule(R.drawable.client_utalk, "palringo.com/caps"));
        rules.add(rule(R.drawable.client_vacuum, "vacuum"));
        rules.add(rule(R.drawable.client_wime, "wime"));
        rules.add(rule(R.drawable.client_wtw, "wtw.k2t.eu/"));
        rules.add(rule(R.drawable.client_telepathy, "telepathy.freedesktop.org"));
        rules.add(rule(R.drawable.client_yaonline, "online.yandex.ru"));
        rules.add(rule(R.drawable.client_yaxim, "yaxim", "smack"));
        rules.add(rule(R.drawable.client_pjc, "pjc.googlecode.com", "pjc"));
        rules.add(rule(R.drawable.client_android, "android.com/gtalk/client", "android", "quicksy"));
        rules.add(rule(R.drawable.client_habahaba, "habahaba.im/"));
        rules.add(rule(R.drawable.client_ichat, "apple.com/ichat/caps"));
        rules.add(rule(R.drawable.client_imov, "imov"));
        rules.add(rule(R.drawable.client_jabbercity, "chat.jabbercity.ru/caps"));
        rules.add(rule(R.drawable.client_jabber_el, "emacs-jabber.sourceforge.net"));
        rules.add(rule(R.drawable.client_jabify, "jabify.com/caps"));
        rules.add(rule(R.drawable.client_jabiru, "jabiru.mzet.net/caps"));
        rules.add(rule(R.drawable.client_jappix, "jappix"));
        rules.add(rule(R.drawable.client_mobileagent, "mobileagent"));
        rules.add(rule(R.drawable.client_meebo, "meebo"));
        rules.add(rule(R.drawable.client_jasmine, "jasmineicq.ru/caps"));
        return Collections.unmodifiableList(rules);
    }

    private static List<ClientRule> createGenericRules() {
        final ArrayList<ClientRule> rules = new ArrayList<>();
        rules.add(rule(R.drawable.ic_client_phone, "android", "quicksy", "conversations", "monocles", "cheogram", "yaxim", "blabber"));
        rules.add(rule(R.drawable.ic_client_web, "web", "browser"));
        rules.add(rule(R.drawable.ic_client_pc, "gajim", "psi", "pidgin", "dino", "kaidan", "poezio", "profanity", "beagle"));
        return Collections.unmodifiableList(rules);
    }

    private static ClientRule rule(final int iconRes, final String... tokens) {
        return new ClientRule(iconRes, tokens);
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

    private static final class ClientRule {
        private final int iconRes;
        private final String[] tokens;

        private ClientRule(final int iconRes, final String... tokens) {
            this.iconRes = iconRes;
            this.tokens = tokens;
        }

        private boolean matches(final String haystack) {
            for (String token : tokens) {
                if (haystack.contains(token)) {
                    return true;
                }
            }
            return false;
        }
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
