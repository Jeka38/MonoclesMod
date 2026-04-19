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
        String resource = null;
        final Jid fullJid = user.getFullJid();
        if (fullJid != null && !TextUtils.isEmpty(fullJid.getResource())) {
            resource = fullJid.getResource();
        }
        Contact contact = user.getContact();
        if (contact == null && user.getRealJid() != null) {
            contact = user.getAccount().getRoster().getContact(user.getRealJid());
        }
        if (contact == null) {
            final Integer inferredFromResource = inferIconByClientName(resource);
            if (inferredFromResource == null) {
                return false;
            }
            imageView.setImageResource(inferredFromResource);
            return true;
        }
        final Pair<Map<String, String>, Map<String, String>> typeAndName = contact.getPresences().toTypeAndNameMap();
        if (applyCustomIcon(imageView, contact, resource)) {
            return true;
        }
        final Integer iconRes = getIconForResource(typeAndName, resource, contact.getSoftwareVersion());
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
        return getIconForResource(typeAndName, contact.getLastResource(), contact.getSoftwareVersion());
    }

    public static Integer getMucUserClientIconRes(final MucOptions.User user) {
        if (user == null) {
            return null;
        }
        final Jid fullJid = user.getFullJid();
        final String resource = (fullJid != null && !TextUtils.isEmpty(fullJid.getResource())) ? fullJid.getResource() : null;
        Contact contact = user.getContact();
        if (contact == null && user.getRealJid() != null) {
            contact = user.getAccount().getRoster().getContact(user.getRealJid());
        }
        if (contact == null) {
            return inferIconByClientName(resource);
        }
        final Pair<Map<String, String>, Map<String, String>> typeAndName = contact.getPresences().toTypeAndNameMap();
        if (!TextUtils.isEmpty(resource)) {
            final Integer icon = getIconForResource(typeAndName, resource, contact.getSoftwareVersion());
            if (icon != null) {
                return icon;
            }
        }

        return getIconForResource(typeAndName, contact.getLastResource(), contact.getSoftwareVersion());
    }

    private static Integer getIconForResource(final Pair<Map<String, String>, Map<String, String>> typeAndName, final String resource, final String softwareVersion) {
        final Map<String, String> types = typeAndName.first;
        final Map<String, String> names = typeAndName.second;
        if (!TextUtils.isEmpty(resource)) {
            final Integer icon = getIconRes(types.get(resource), names.get(resource));
            if (icon != null) {
                return icon;
            }
            final Integer resourceIcon = inferIconByClientName(resource);
            if (resourceIcon != null) {
                return resourceIcon;
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
        if (name.contains("adium")) {
            return R.drawable.client_adium;
        } else if (name.contains("github.com/snuk182/aceim")) {
            return R.drawable.client_aceim;
        } else if (name.contains("aqq.eu")) {
            return R.drawable.client_aqq;
        } else if (name.contains("barobin.com/caps")) {
            return R.drawable.client_bayan;
        } else if (name.contains("beem-project.com")) {
            return R.drawable.client_beem;
        } else if (name.contains("bitlbee.org/xmpp/caps")) {
            return R.drawable.client_bitlbee;
        } else if (name.contains("blabber")) {
            return R.drawable.client_blabber;
        } else if (name.contains("simpleapps.ru/caps") || name.contains("blacksmith-2.googlecode.com/svn/") || name.contains("matrix.bz/safety") || name.contains("matrix.bz")) {
            return R.drawable.client_blacksmith_bot;
        } else if (name.contains("bluejabb")) {
            return R.drawable.client_bluejabb;
        } else if (name.contains("bombusmod.net.ru") || name.contains("github.com/bombusmod/caps")) {
            return R.drawable.client_bombusmod;
        } else if (name.contains("bombusmod-qd.wen.ru")) {
            return R.drawable.client_bombusqd;
        } else if (name.contains("bombus-im.org/ng") || name.contains("bombus-ng")) {
            return R.drawable.client_bombusng;
        } else if (name.contains("bombus.pl")) {
            return R.drawable.client_bombuspl;
        } else if (name.contains("bombus+") || name.contains("voffk.org.ru")) {
            return R.drawable.client_bombusplus;
        } else if (name.contains("bombus-im.org/java")) {
            return R.drawable.client_bombus;
        } else if (name.contains("dino-im.org") || name.contains("dino.im") || name.contains("dino")) {
            return R.drawable.client_dino;
        } else if (name.contains("exodus.jabberstudio.org/caps")) {
            return R.drawable.client_exodus;
        } else if (name.contains("eyecu.ru")) {
            return R.drawable.client_eyecu;
        } else if (name.contains("urn:xmpp:rtt:0")) {
            return R.drawable.client_fasttext;
        } else if (name.contains("jabga.ru")) {
            return R.drawable.client_fj;
        } else if (name.contains("freomessenger.com/caps")) {
            return R.drawable.client_freomessenger;
        } else if (name.contains("freq-bot.net")) {
            return R.drawable.client_freq;
        } else if (name.contains("cheogram")) {
            return R.drawable.client_cheogram;
        } else if (name.contains("climm.org/xmpp/caps")) {
            return R.drawable.client_climm;
        } else if (name.contains("coccinella.sourceforge.net/protocol/caps")) {
            return R.drawable.client_coccinella;
        } else if (name.contains("c0nnect.de") || name.contains("c0nnecteasy")) {
            return R.drawable.client_con0pro;
        } else if (name.contains("sum7.eu") || name.contains("conv6ations")) {
            return R.drawable.client_conv6ations;
        } else if (name.contains("conversations.im") || name.contains("conversations")) {
            return R.drawable.client_conversations;
        } else if (name.contains("github.com/jeka38/conversations-classic-mod")) {
            return R.drawable.client_conversations_mod;
        } else if (name.contains("dev.narayana.im/narayana/conversations-classic") || name.contains("conversations-classic") || name.contains("conversations classic")) {
            return R.drawable.client_conversations_old;
        } else if (name.contains("gajim")) {
            return R.drawable.client_gajim;
        } else if (name.contains("gmail")) {
            return R.drawable.client_gmail;
        } else if (name.contains("isida-bot.com") || name.contains("isida")) {
            return R.drawable.client_isida;
        } else if (name.contains("jabbim")) {
            return R.drawable.client_jabbim;
        } else if (name.contains("jabbroid.akuz.de/caps")) {
            return R.drawable.client_jabbroid;
        } else if (name.contains("jajc.jrudevels.org/caps")) {
            return R.drawable.client_jajc;
        } else if (name.contains("jimm.net.ru/caps")) {
            return R.drawable.client_jimm;
        } else if (name.contains("jitsi.org")) {
            return R.drawable.client_jitsi;
        } else if (name.contains("kadu.im/caps")) {
            return R.drawable.client_kadu;
        } else if (name.contains("jtalk.ustyugov.net/caps")) {
            return R.drawable.client_jtalk;
        } else if (name.contains("juick") || name.contains("juick.com/caps") || name.contains("xmpp.rocks")) {
            return R.drawable.client_juick;
        } else if (name.contains("kopete.kde.org/jabber/caps")) {
            return R.drawable.client_kopete;
        } else if (name.contains("bluendo.com/protocol/caps")) {
            return R.drawable.client_lampiro;
        } else if (name.contains("leechcraft")) {
            return R.drawable.client_leechcraft;
        } else if (name.contains("loqui.im")) {
            return R.drawable.client_loqui;
        } else if (name.contains("mchat")) {
            return R.drawable.client_mchat;
        } else if (name.contains("miranda-im.org/caps") || name.contains("miranda.sourceforge.net")) {
            return R.drawable.client_miranda;
        } else if (name.contains("miranda-ng.org/caps")) {
            return R.drawable.client_miranda_ng;
        } else if (name.contains("mail.google.com")) {
            return R.drawable.client_gmail;
        } else if (name.contains("tomclaw.com/mandarin_im/caps")) {
            return R.drawable.client_mandarin;
        } else if (name.contains("mcabber")) {
            return R.drawable.client_mcabber;
        } else if (name.contains("monal.im/caps")) {
            return R.drawable.client_monal;
        } else if (name.contains("monocles")) {
            return R.drawable.client_monocles;
        } else if (name.contains("moxl.movim.eu") || name.contains("movim")) {
            return R.drawable.client_movim;
        } else if (name.contains("nimbuzz")) {
            return R.drawable.client_nimbuzz;
        } else if (name.contains("slixmpp.com/ver/")) {
            return R.drawable.client_poezio_new;
        } else if (name.contains("profanity")) {
            return R.drawable.client_profanity;
        } else if (name.contains("psi-im.org") || name.contains("psi")) {
            return R.drawable.client_psi;
        } else if (name.contains("psi+") || name.contains("psi-dev") || name.contains("psi-plus.com")) {
            return R.drawable.client_psiplus;
        } else if (name.contains("qip")) {
            return R.drawable.client_qip;
        } else if (name.contains("2010.qip.ru/caps")) {
            return R.drawable.client_qip2010;
        } else if (name.contains("pda.qip.ru")) {
            return R.drawable.client_qippda;
        } else if (name.contains("pako.googlecode.com")) {
            return R.drawable.client_pako;
        } else if (name.contains("pandion.im")) {
            return R.drawable.client_pandion;
        } else if (name.contains("pidgin")) {
            return R.drawable.client_pidgin;
        } else if (name.contains("jabber.pix-art.de") || name.contains("pix-art messenger")) {
            return R.drawable.client_pixart;
        } else if (name.contains("poez.io") || name.contains("poezio")) {
            return R.drawable.client_poezio;
        } else if (name.contains("oneteam.im/caps")) {
            return R.drawable.client_oneteam;
        } else if (name.contains("oneteam_iphone")) {
            return R.drawable.client_oneteamiphone;
        } else if (name.contains("code.google.com/p/qxmpp")) {
            return R.drawable.client_qt;
        } else if (name.contains("qutim.org")) {
            return R.drawable.client_qutim;
        } else if (name.contains("riddim")) {
            return R.drawable.client_riddim;
        } else if (name.contains("sawim.ru/caps")) {
            return R.drawable.client_sawim;
        } else if (name.contains("www.igniterealtime.org/projects/smack/") || name.contains("xabber")) {
            return R.drawable.client_xabber;
        } else if (name.contains("conversions.fjsdevelopment.weebly.com")) {
            return R.drawable.client_secugab;
        } else if (name.contains("safetyjabber.com/caps")) {
            return R.drawable.client_sj;
        } else if (name.contains("www.lonelycatgames.com/slick/caps")) {
            return R.drawable.client_slick;
        } else if (name.contains("smuxi.im")) {
            return R.drawable.client_smuxi;
        } else if (name.contains("swift.im")) {
            return R.drawable.client_swift;
        } else if (name.contains("google.com/xmpp/client/caps") || name.contains("talkonaut")) {
            return R.drawable.client_talkonaut;
        } else if (name.contains("talk.google.com") || name.contains("gtalk")) {
            return R.drawable.client_gtalk;
        } else if (name.contains("tigase.org/messenger")) {
            return R.drawable.client_tigase;
        } else if (name.contains("tkabber.jabber.ru/")) {
            return R.drawable.client_tkabber;
        } else if (name.contains("trillian.im/caps")) {
            return R.drawable.client_trillian;
        } else if (name.contains("palringo.com/caps")) {
            return R.drawable.client_utalk;
        } else if (name.contains("vacuum")) {
            return R.drawable.client_vacuum;
        } else if (name.contains("wime")) {
            return R.drawable.client_wime;
        } else if (name.contains("wtw.k2t.eu/")) {
            return R.drawable.client_wtw;
        } else if (name.contains("telepathy.freedesktop.org")) {
            return R.drawable.client_telepathy;
        } else if (name.contains("online.yandex.ru")) {
            return R.drawable.client_yaonline;
        } else if (name.contains("yaxim") || name.contains("smack")) {
            return R.drawable.client_yaxim;
        } else if (name.contains("pjc.googlecode.com")) {
            return R.drawable.client_pjc;
        } else if (name.contains("android.com/gtalk/client") || name.contains("android") || name.contains("quicksy")) {
            return R.drawable.client_android;
        } else if (name.contains("habahaba.im/")) {
            return R.drawable.client_habahaba;
        } else if (name.contains("apple.com/ichat/caps")) {
            return R.drawable.client_ichat;
        } else if (name.contains("imov")) {
            return R.drawable.client_imov;
        } else if (name.contains("chat.jabbercity.ru/caps")) {
            return R.drawable.client_jabbercity;
        } else if (name.contains("emacs-jabber.sourceforge.net")) {
            return R.drawable.client_jabber_el;
        } else if (name.contains("jabify.com/caps")) {
            return R.drawable.client_jabify;
        } else if (name.contains("jabiru.mzet.net/caps")) {
            return R.drawable.client_jabiru;
        } else if (name.contains("jappix")) {
            return R.drawable.client_jappix;
        } else if (name.contains("pjc")) {
            return R.drawable.client_pjc;
        } else if (name.contains("mobileagent")) {
            return R.drawable.client_mobileagent;
        } else if (name.contains("meebo")) {
            return R.drawable.client_meebo;
        } else if (name.contains("jasmineicq.ru/caps")) {
            return R.drawable.client_jasmine;
        }

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
