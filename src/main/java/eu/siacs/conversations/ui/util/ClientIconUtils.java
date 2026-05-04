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
import java.util.LinkedHashMap;
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

    private static final Map<String, Integer> CLIENT_SPECIFIC_ICONS = new LinkedHashMap<>();

    static {
        CLIENT_SPECIFIC_ICONS.put("conversations-classic", R.drawable.client_another);
        CLIENT_SPECIFIC_ICONS.put("talkgadget.google.com", R.drawable.client_talkgadget_google_com);
        CLIENT_SPECIFIC_ICONS.put("bombus-avalon-old", R.drawable.client_bombus_avalon_old);
        CLIENT_SPECIFIC_ICONS.put("sip-communicator", R.drawable.client_sip_communicator);
        CLIENT_SPECIFIC_ICONS.put("sonic-revolution", R.drawable.client_sonic_revolution);
        CLIENT_SPECIFIC_ICONS.put("fluux-messenger", R.drawable.client_fluux);
        CLIENT_SPECIFIC_ICONS.put("mail.google.com", R.drawable.client_mail_google_com);
        CLIENT_SPECIFIC_ICONS.put("talk.google.com", R.drawable.client_talk_google_com);
        CLIENT_SPECIFIC_ICONS.put("telegram-bridge", R.drawable.client_telegram);
        CLIENT_SPECIFIC_ICONS.put("agilemessenger", R.drawable.client_agilemessenger);
        CLIENT_SPECIFIC_ICONS.put("oneteam-iphone", R.drawable.client_oneteam_iphone);
        CLIENT_SPECIFIC_ICONS.put("snapi-snup-bot", R.drawable.client_snapi_snup_bot);
        CLIENT_SPECIFIC_ICONS.put("bombus-avalon", R.drawable.client_bombus_avalon);
        CLIENT_SPECIFIC_ICONS.put("bombusmod-old", R.drawable.client_bombusmod_old);
        CLIENT_SPECIFIC_ICONS.put("conversations", R.drawable.client_conversations);
        CLIENT_SPECIFIC_ICONS.put("gtalk-android", R.drawable.client_gtalk_android);
        CLIENT_SPECIFIC_ICONS.put("historian-bot", R.drawable.client_historian_bot);
        CLIENT_SPECIFIC_ICONS.put("libpurple-old", R.drawable.client_libpurple_old);
        CLIENT_SPECIFIC_ICONS.put("movamessenger", R.drawable.client_movamessenger);
        CLIENT_SPECIFIC_ICONS.put("talisman-bot2", R.drawable.client_talisman_bot2);
        CLIENT_SPECIFIC_ICONS.put("trillianbasic", R.drawable.client_trillianbasic);
        CLIENT_SPECIFIC_ICONS.put("barracuda-im", R.drawable.client_barracuda_im);
        CLIENT_SPECIFIC_ICONS.put("imformer-bot", R.drawable.client_imformer_bot);
        CLIENT_SPECIFIC_ICONS.put("jabber-popov", R.drawable.client_jabber_popov);
        CLIENT_SPECIFIC_ICONS.put("jimm-android", R.drawable.client_jimm_android);
        CLIENT_SPECIFIC_ICONS.put("omnipresence", R.drawable.client_omnipresence);
        CLIENT_SPECIFIC_ICONS.put("talisman-bot", R.drawable.client_talisman_bot);
        CLIENT_SPECIFIC_ICONS.put("ultimate-bot", R.drawable.client_ultimate_bot);
        CLIENT_SPECIFIC_ICONS.put("arabic-fake", R.drawable.client_arabic_fake);
        CLIENT_SPECIFIC_ICONS.put("bombus-klub", R.drawable.client_bombus_klub);
        CLIENT_SPECIFIC_ICONS.put("bombusng-md", R.drawable.client_bombusng_md);
        CLIENT_SPECIFIC_ICONS.put("bombusqd-ng", R.drawable.client_bombusqd_ng);
        CLIENT_SPECIFIC_ICONS.put("capsula-bot", R.drawable.client_capsula_bot);
        CLIENT_SPECIFIC_ICONS.put("conv6ations", R.drawable.client_conv6ations);
        CLIENT_SPECIFIC_ICONS.put("freqbot-old", R.drawable.client_freqbot_old);
        CLIENT_SPECIFIC_ICONS.put("magnet2-bot", R.drawable.client_magnet2_bot);
        CLIENT_SPECIFIC_ICONS.put("rankoid-bot", R.drawable.client_rankoid_bot);
        CLIENT_SPECIFIC_ICONS.put("blacksmith", R.drawable.client_blacksmith_bot);
        CLIENT_SPECIFIC_ICONS.put("bombus-old", R.drawable.client_bombus_old);
        CLIENT_SPECIFIC_ICONS.put("bombusklub", R.drawable.client_bombusklub);
        CLIENT_SPECIFIC_ICONS.put("bombuslime", R.drawable.client_bombuslime);
        CLIENT_SPECIFIC_ICONS.put("bombusplus", R.drawable.client_bombusplus);
        CLIENT_SPECIFIC_ICONS.put("buddydroid", R.drawable.client_buddydroid);
        CLIENT_SPECIFIC_ICONS.put("c0nnectpro", R.drawable.client_con_pro);
        CLIENT_SPECIFIC_ICONS.put("chatsecure", R.drawable.client_chatsecure);
        CLIENT_SPECIFIC_ICONS.put("coccinella", R.drawable.client_coccinella);
        CLIENT_SPECIFIC_ICONS.put("conversejs", R.drawable.client_conversejs);
        CLIENT_SPECIFIC_ICONS.put("freeswitch", R.drawable.client_freeswitch);
        CLIENT_SPECIFIC_ICONS.put("google.com", R.drawable.client_google_com);
        CLIENT_SPECIFIC_ICONS.put("jimm-aspro", R.drawable.client_jimm_aspro);
        CLIENT_SPECIFIC_ICONS.put("leechcraft", R.drawable.client_leechcraft);
        CLIENT_SPECIFIC_ICONS.put("miranda-ng", R.drawable.client_miranda_ng);
        CLIENT_SPECIFIC_ICONS.put("osiris-bot", R.drawable.client_osiris_bot);
        CLIENT_SPECIFIC_ICONS.put("quizer-bot", R.drawable.client_quizer_bot);
        CLIENT_SPECIFIC_ICONS.put("shield-bot", R.drawable.client_shield_bot);
        CLIENT_SPECIFIC_ICONS.put("bombusmod", R.drawable.client_bombusmod);
        CLIENT_SPECIFIC_ICONS.put("emess-old", R.drawable.client_emess_old);
        CLIENT_SPECIFIC_ICONS.put("fatal-bot", R.drawable.client_fatal_bot);
        CLIENT_SPECIFIC_ICONS.put("imadering", R.drawable.client_imadering);
        CLIENT_SPECIFIC_ICONS.put("isida-bot", R.drawable.client_isida_bot);
        CLIENT_SPECIFIC_ICONS.put("jabber.el", R.drawable.client_jabber_el);
        CLIENT_SPECIFIC_ICONS.put("libpurple", R.drawable.client_libpurple);
        CLIENT_SPECIFIC_ICONS.put("profanity", R.drawable.client_profanity);
        CLIENT_SPECIFIC_ICONS.put("qipmobile", R.drawable.client_qipmobile);
        CLIENT_SPECIFIC_ICONS.put("qutim-old", R.drawable.client_qutim_old);
        CLIENT_SPECIFIC_ICONS.put("sawim-ios", R.drawable.client_sawim_iphone);
        CLIENT_SPECIFIC_ICONS.put("stanza.io", R.drawable.client_stanza);
        CLIENT_SPECIFIC_ICONS.put("talkonaut", R.drawable.client_talkonaut);
        CLIENT_SPECIFIC_ICONS.put("telepathy", R.drawable.client_telepathy);
        CLIENT_SPECIFIC_ICONS.put("webclient", R.drawable.client_webclient);
        CLIENT_SPECIFIC_ICONS.put("asterisk", R.drawable.client_asterisk);
        CLIENT_SPECIFIC_ICONS.put("bayanicq", R.drawable.client_bayanicq);
        CLIENT_SPECIFIC_ICONS.put("bluejabb", R.drawable.client_bluejabb);
        CLIENT_SPECIFIC_ICONS.put("bombusng", R.drawable.client_bombusng);
        CLIENT_SPECIFIC_ICONS.put("bombuspl", R.drawable.client_bombuspl);
        CLIENT_SPECIFIC_ICONS.put("bombusqd", R.drawable.client_bombusqd);
        CLIENT_SPECIFIC_ICONS.put("centerim", R.drawable.client_centerim);
        CLIENT_SPECIFIC_ICONS.put("chatopus", R.drawable.client_chatopus);
        CLIENT_SPECIFIC_ICONS.put("cheogram", R.drawable.client_cheogram);
        CLIENT_SPECIFIC_ICONS.put("emclient", R.drawable.client_emclient);
        CLIENT_SPECIFIC_ICONS.put("freq-bot", R.drawable.client_freq_bot);
        CLIENT_SPECIFIC_ICONS.put("gadugadu", R.drawable.client_gadugadu);
        CLIENT_SPECIFIC_ICONS.put("gamebot2", R.drawable.client_gamebot2);
        CLIENT_SPECIFIC_ICONS.put("gismeteo", R.drawable.client_gismeteo);
        CLIENT_SPECIFIC_ICONS.put("gluxibot", R.drawable.client_gluxibot);
        CLIENT_SPECIFIC_ICONS.put("habahaba", R.drawable.client_habahaba);
        CLIENT_SPECIFIC_ICONS.put("jabbroid", R.drawable.client_jabbroid);
        CLIENT_SPECIFIC_ICONS.put("jtalkmod", R.drawable.client_jtalkmod);
        CLIENT_SPECIFIC_ICONS.put("mandarin", R.drawable.client_mandarin);
        CLIENT_SPECIFIC_ICONS.put("monocles", R.drawable.client_monocles);
        CLIENT_SPECIFIC_ICONS.put("ovi-chat", R.drawable.client_ovi_chat);
        CLIENT_SPECIFIC_ICONS.put("palringo", R.drawable.client_palringo);
        CLIENT_SPECIFIC_ICONS.put("trillian", R.drawable.client_trillian);
        CLIENT_SPECIFIC_ICONS.put("utah-bot", R.drawable.client_utah_bot);
        CLIENT_SPECIFIC_ICONS.put("weonlydo", R.drawable.client_weonlydo);
        CLIENT_SPECIFIC_ICONS.put("wod-xmpp", R.drawable.client_wod_xmpp);
        CLIENT_SPECIFIC_ICONS.put("yaonline", R.drawable.client_yaonline);
        CLIENT_SPECIFIC_ICONS.put("zeus-bot", R.drawable.client_zeus_bot);
        CLIENT_SPECIFIC_ICONS.put("android", R.drawable.client_android);
        CLIENT_SPECIFIC_ICONS.put("beejive", R.drawable.client_beejive);
        CLIENT_SPECIFIC_ICONS.put("bitlbee", R.drawable.client_bitlbee);
        CLIENT_SPECIFIC_ICONS.put("blabber", R.drawable.client_blabber);
        CLIENT_SPECIFIC_ICONS.put("cudumar", R.drawable.client_cudumar);
        CLIENT_SPECIFIC_ICONS.put("freelab", R.drawable.client_flm);
        CLIENT_SPECIFIC_ICONS.put("freqbot", R.drawable.client_freqbot);
        CLIENT_SPECIFIC_ICONS.put("gamebot", R.drawable.client_gamebot);
        CLIENT_SPECIFIC_ICONS.put("hipchat", R.drawable.client_hipchat);
        CLIENT_SPECIFIC_ICONS.put("jabber2", R.drawable.client_jabber2);
        CLIENT_SPECIFIC_ICONS.put("jamebot", R.drawable.client_jamebot);
        CLIENT_SPECIFIC_ICONS.put("jasmine", R.drawable.client_jasmine);
        CLIENT_SPECIFIC_ICONS.put("jbother", R.drawable.client_jbother);
        CLIENT_SPECIFIC_ICONS.put("lampiro", R.drawable.client_lampiro);
        CLIENT_SPECIFIC_ICONS.put("m-agent", R.drawable.client_m_agent);
        CLIENT_SPECIFIC_ICONS.put("mcabber", R.drawable.client_mcabber);
        CLIENT_SPECIFIC_ICONS.put("megafon", R.drawable.client_megafon);
        CLIENT_SPECIFIC_ICONS.put("miranda", R.drawable.client_miranda);
        CLIENT_SPECIFIC_ICONS.put("nimbuzz", R.drawable.client_nimbuzz);
        CLIENT_SPECIFIC_ICONS.put("oneteam", R.drawable.client_oneteam);
        CLIENT_SPECIFIC_ICONS.put("pandion", R.drawable.client_pandion);
        CLIENT_SPECIFIC_ICONS.put("pix-art", R.drawable.client_pix_art);
        CLIENT_SPECIFIC_ICONS.put("psiplus", R.drawable.client_psiplus);
        CLIENT_SPECIFIC_ICONS.put("qip2010", R.drawable.client_qip2010);
        CLIENT_SPECIFIC_ICONS.put("radio-t", R.drawable.client_radio_t);
        CLIENT_SPECIFIC_ICONS.put("secugab", R.drawable.client_secugab_messenger);
        CLIENT_SPECIFIC_ICONS.put("tipicim", R.drawable.client_tipicim);
        CLIENT_SPECIFIC_ICONS.put("tkabber", R.drawable.client_tkabber);
        CLIENT_SPECIFIC_ICONS.put("unknown", R.drawable.client_unknown);
        CLIENT_SPECIFIC_ICONS.put("vk4xmpp", R.drawable.client_vk4xmpp);
        CLIENT_SPECIFIC_ICONS.put("webchat", R.drawable.client_webchat);
        CLIENT_SPECIFIC_ICONS.put("xu6-bot", R.drawable.client_xu6_bot);
        CLIENT_SPECIFIC_ICONS.put("bombus", R.drawable.client_bombus);
        CLIENT_SPECIFIC_ICONS.put("breeze", R.drawable.client_shtorm);
        CLIENT_SPECIFIC_ICONS.put("ebuddy", R.drawable.client_ebuddy);
        CLIENT_SPECIFIC_ICONS.put("exodus", R.drawable.client_exodus);
        CLIENT_SPECIFIC_ICONS.put("freize", R.drawable.client_freize);
        CLIENT_SPECIFIC_ICONS.put("implus", R.drawable.client_implus);
        CLIENT_SPECIFIC_ICONS.put("jabber", R.drawable.client_jabber);
        CLIENT_SPECIFIC_ICONS.put("jabbim", R.drawable.client_jabbim);
        CLIENT_SPECIFIC_ICONS.put("jabbin", R.drawable.client_jabbin);
        CLIENT_SPECIFIC_ICONS.put("jabiru", R.drawable.client_jabiru);
        CLIENT_SPECIFIC_ICONS.put("jappix", R.drawable.client_jappix);
        CLIENT_SPECIFIC_ICONS.put("kandru", R.drawable.client_kandu_im);
        CLIENT_SPECIFIC_ICONS.put("kopete", R.drawable.client_kopete);
        CLIENT_SPECIFIC_ICONS.put("mabber", R.drawable.client_mabber);
        CLIENT_SPECIFIC_ICONS.put("meegim", R.drawable.client_meegim);
        CLIENT_SPECIFIC_ICONS.put("meetro", R.drawable.client_meetro);
        CLIENT_SPECIFIC_ICONS.put("nekbot", R.drawable.client_nekbot);
        CLIENT_SPECIFIC_ICONS.put("osiris", R.drawable.client_osiris);
        CLIENT_SPECIFIC_ICONS.put("pidgin", R.drawable.client_pidgin);
        CLIENT_SPECIFIC_ICONS.put("poezio", R.drawable.client_poezio);
        CLIENT_SPECIFIC_ICONS.put("qippda", R.drawable.client_qippda);
        CLIENT_SPECIFIC_ICONS.put("riddim", R.drawable.client_riddim);
        CLIENT_SPECIFIC_ICONS.put("safety", R.drawable.client_safety_bot);
        CLIENT_SPECIFIC_ICONS.put("tigase", R.drawable.client_tigase);
        CLIENT_SPECIFIC_ICONS.put("vacuum", R.drawable.client_vacuum);
        CLIENT_SPECIFIC_ICONS.put("xabber", R.drawable.client_xabber);
        CLIENT_SPECIFIC_ICONS.put("adium", R.drawable.client_adium);
        CLIENT_SPECIFIC_ICONS.put("akari", R.drawable.client_akiri_bot);
        CLIENT_SPECIFIC_ICONS.put("akeni", R.drawable.client_akeni);
        CLIENT_SPECIFIC_ICONS.put("apple", R.drawable.client_apple);
        CLIENT_SPECIFIC_ICONS.put("atalk", R.drawable.client_atalk);
        CLIENT_SPECIFIC_ICONS.put("ayttm", R.drawable.client_ayttm);
        CLIENT_SPECIFIC_ICONS.put("candy", R.drawable.client_candy);
        CLIENT_SPECIFIC_ICONS.put("emess", R.drawable.client_emess);
        CLIENT_SPECIFIC_ICONS.put("erlim", R.drawable.client_erlim);
        CLIENT_SPECIFIC_ICONS.put("eyecu", R.drawable.client_eyecu);
        CLIENT_SPECIFIC_ICONS.put("fatal", R.drawable.client_fatal_bot);
        CLIENT_SPECIFIC_ICONS.put("gajim", R.drawable.client_gajim);
        CLIENT_SPECIFIC_ICONS.put("gloox", R.drawable.client_gloox);
        CLIENT_SPECIFIC_ICONS.put("gmail", R.drawable.client_gmail);
        CLIENT_SPECIFIC_ICONS.put("gtalk", R.drawable.client_gtalk);
        CLIENT_SPECIFIC_ICONS.put("ichat", R.drawable.client_ichat);
        CLIENT_SPECIFIC_ICONS.put("japyt", R.drawable.client_japyt);
        CLIENT_SPECIFIC_ICONS.put("jdisk", R.drawable.client_jdisk);
        CLIENT_SPECIFIC_ICONS.put("jitsi", R.drawable.client_jitsi);
        CLIENT_SPECIFIC_ICONS.put("jtalk", R.drawable.client_jtalk);
        CLIENT_SPECIFIC_ICONS.put("juick", R.drawable.client_juick_bot);
        CLIENT_SPECIFIC_ICONS.put("mchat", R.drawable.client_mchat);
        CLIENT_SPECIFIC_ICONS.put("monal", R.drawable.client_monal);
        CLIENT_SPECIFIC_ICONS.put("movim", R.drawable.client_movim);
        CLIENT_SPECIFIC_ICONS.put("prose", R.drawable.client_prose);
        CLIENT_SPECIFIC_ICONS.put("qutim", R.drawable.client_qutim);
        CLIENT_SPECIFIC_ICONS.put("robot", R.drawable.client_robot);
        CLIENT_SPECIFIC_ICONS.put("sawim", R.drawable.client_sawim);
        CLIENT_SPECIFIC_ICONS.put("siejc", R.drawable.client_siejc);
        CLIENT_SPECIFIC_ICONS.put("slick", R.drawable.client_slick);
        CLIENT_SPECIFIC_ICONS.put("smack", R.drawable.client_smack);
        CLIENT_SPECIFIC_ICONS.put("smuxi", R.drawable.client_smuxi);
        CLIENT_SPECIFIC_ICONS.put("snapi", R.drawable.client_snapi_snup_bot);
        CLIENT_SPECIFIC_ICONS.put("spark", R.drawable.client_spark);
        CLIENT_SPECIFIC_ICONS.put("swift", R.drawable.client_swift);
        CLIENT_SPECIFIC_ICONS.put("utalk", R.drawable.client_utalk);
        CLIENT_SPECIFIC_ICONS.put("yaxim", R.drawable.client_yaxim);
        CLIENT_SPECIFIC_ICONS.put("beem", R.drawable.client_beem);
        CLIENT_SPECIFIC_ICONS.put("dino", R.drawable.client_dino);
        CLIENT_SPECIFIC_ICONS.put("freo", R.drawable.client_freo);
        CLIENT_SPECIFIC_ICONS.put("gaim", R.drawable.client_gaim);
        CLIENT_SPECIFIC_ICONS.put("jajc", R.drawable.client_jajc);
        CLIENT_SPECIFIC_ICONS.put("jubo", R.drawable.client_jubo);
        CLIENT_SPECIFIC_ICONS.put("kadu", R.drawable.client_kadu);
        CLIENT_SPECIFIC_ICONS.put("wime", R.drawable.client_wime);
        CLIENT_SPECIFIC_ICONS.put("aim", R.drawable.client_aim);
        CLIENT_SPECIFIC_ICONS.put("aqq", R.drawable.client_aqq);
        CLIENT_SPECIFIC_ICONS.put("bot", R.drawable.client_bot);
        CLIENT_SPECIFIC_ICONS.put("glu", R.drawable.client_glu);
        CLIENT_SPECIFIC_ICONS.put("psi", R.drawable.client_psi);
        CLIENT_SPECIFIC_ICONS.put("qip", R.drawable.client_qip);
        CLIENT_SPECIFIC_ICONS.put("rnq", R.drawable.client_rnq);
        CLIENT_SPECIFIC_ICONS.put("rss", R.drawable.client_rss);
        CLIENT_SPECIFIC_ICONS.put("sim", R.drawable.client_sim);
        CLIENT_SPECIFIC_ICONS.put("wtw", R.drawable.client_wtw);
        CLIENT_SPECIFIC_ICONS.put("fj", R.drawable.client_fj);
        CLIENT_SPECIFIC_ICONS.put("rq", R.drawable.client_rq);
    }



    private ClientIconUtils() {
    }

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
        for (Map.Entry<String, Integer> entry : CLIENT_SPECIFIC_ICONS.entrySet()) {
            if (name.contains(entry.getKey())) {
                return entry.getValue();
            }
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
        for (Map.Entry<String, Presence> entry : contact.getPresences().getPresencesMap().entrySet()) {
            if (entry.getValue() != null) {
                return entry.getValue();
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
