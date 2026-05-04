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
        if (name.contains("conversations-classic-mod")) {
            return R.drawable.client_conversations_mod;
        } else if (name.contains("talkgadget.google.com")) {
            return R.drawable.client_talkgadget_google_com;
        } else if (name.contains("bombus-avalon-old")) {
            return R.drawable.client_bombus_avalon_old;
        } else if (name.contains("sip-communicator")) {
            return R.drawable.client_sip_communicator;
        } else if (name.contains("sonic-revolution")) {
            return R.drawable.client_sonic_revolution;
        } else if (name.contains("fluux-messenger")) {
            return R.drawable.client_fluux;
        } else if (name.contains("mail.google.com")) {
            return R.drawable.client_mail_google_com;
        } else if (name.contains("talk.google.com")) {
            return R.drawable.client_talk_google_com;
        } else if (name.contains("telegram-bridge")) {
            return R.drawable.client_telegram;
        } else if (name.contains("agilemessenger")) {
            return R.drawable.client_agilemessenger;
        } else if (name.contains("oneteam-iphone")) {
            return R.drawable.client_oneteam_iphone;
        } else if (name.contains("snapi-snup-bot")) {
            return R.drawable.client_snapi_snup_bot;
        } else if (name.contains("bombus-avalon")) {
            return R.drawable.client_bombus_avalon;
        } else if (name.contains("bombusmod-old")) {
            return R.drawable.client_bombusmod_old;
        } else if (name.contains("conversations")) {
            return R.drawable.client_conversations;
        } else if (name.contains("gtalk-android")) {
            return R.drawable.client_gtalk_android;
        } else if (name.contains("historian-bot")) {
            return R.drawable.client_historian_bot;
        } else if (name.contains("libpurple-old")) {
            return R.drawable.client_libpurple_old;
        } else if (name.contains("movamessenger")) {
            return R.drawable.client_movamessenger;
        } else if (name.contains("talisman-bot2")) {
            return R.drawable.client_talisman_bot2;
        } else if (name.contains("trillianbasic")) {
            return R.drawable.client_trillianbasic;
        } else if (name.contains("barracuda-im")) {
            return R.drawable.client_barracuda_im;
        } else if (name.contains("imformer-bot")) {
            return R.drawable.client_imformer_bot;
        } else if (name.contains("jabber-popov")) {
            return R.drawable.client_jabber_popov;
        } else if (name.contains("jimm-android")) {
            return R.drawable.client_jimm_android;
        } else if (name.contains("omnipresence")) {
            return R.drawable.client_omnipresence;
        } else if (name.contains("talisman-bot")) {
            return R.drawable.client_talisman_bot;
        } else if (name.contains("ultimate-bot")) {
            return R.drawable.client_ultimate_bot;
        } else if (name.contains("arabic-fake")) {
            return R.drawable.client_arabic_fake;
        } else if (name.contains("bombus-klub")) {
            return R.drawable.client_bombus_klub;
        } else if (name.contains("bombusng-md")) {
            return R.drawable.client_bombusng_md;
        } else if (name.contains("bombusqd-ng")) {
            return R.drawable.client_bombusqd_ng;
        } else if (name.contains("capsula-bot")) {
            return R.drawable.client_capsula_bot;
        } else if (name.contains("conv6ations")) {
            return R.drawable.client_conv6ations;
        } else if (name.contains("freqbot-old")) {
            return R.drawable.client_freqbot_old;
        } else if (name.contains("magnet2-bot")) {
            return R.drawable.client_magnet2_bot;
        } else if (name.contains("rankoid-bot")) {
            return R.drawable.client_rankoid_bot;
        } else if (name.contains("another-im")) {
            return R.drawable.client_another;
        } else if (name.contains("blacksmith")) {
            return R.drawable.client_blacksmith_bot;
        } else if (name.contains("bombus-old")) {
            return R.drawable.client_bombus_old;
        } else if (name.contains("bombusklub")) {
            return R.drawable.client_bombusklub;
        } else if (name.contains("bombuslime")) {
            return R.drawable.client_bombuslime;
        } else if (name.contains("bombusplus")) {
            return R.drawable.client_bombusplus;
        } else if (name.contains("buddydroid")) {
            return R.drawable.client_buddydroid;
        } else if (name.contains("c0nnectpro")) {
            return R.drawable.client_con_pro;
        } else if (name.contains("chatsecure")) {
            return R.drawable.client_chatsecure;
        } else if (name.contains("coccinella")) {
            return R.drawable.client_coccinella;
        } else if (name.contains("conversejs")) {
            return R.drawable.client_conversejs;
        } else if (name.contains("freeswitch")) {
            return R.drawable.client_freeswitch;
        } else if (name.contains("google.com")) {
            return R.drawable.client_google_com;
        } else if (name.contains("jimm-aspro")) {
            return R.drawable.client_jimm_aspro;
        } else if (name.contains("leechcraft")) {
            return R.drawable.client_leechcraft;
        } else if (name.contains("miranda-ng")) {
            return R.drawable.client_miranda_ng;
        } else if (name.contains("osiris-bot")) {
            return R.drawable.client_osiris_bot;
        } else if (name.contains("quizer-bot")) {
            return R.drawable.client_quizer_bot;
        } else if (name.contains("shield-bot")) {
            return R.drawable.client_shield_bot;
        } else if (name.contains("bombusmod")) {
            return R.drawable.client_bombusmod;
        } else if (name.contains("emess-old")) {
            return R.drawable.client_emess_old;
        } else if (name.contains("fatal-bot")) {
            return R.drawable.client_fatal_bot;
        } else if (name.contains("imadering")) {
            return R.drawable.client_imadering;
        } else if (name.contains("isida-bot")) {
            return R.drawable.client_isida_bot;
        } else if (name.contains("jabber.el")) {
            return R.drawable.client_jabber_el;
        } else if (name.contains("libpurple")) {
            return R.drawable.client_libpurple;
        } else if (name.contains("profanity")) {
            return R.drawable.client_profanity;
        } else if (name.contains("qipmobile")) {
            return R.drawable.client_qipmobile;
        } else if (name.contains("qutim-old")) {
            return R.drawable.client_qutim_old;
        } else if (name.contains("sawim-ios")) {
            return R.drawable.client_sawim_iphone;
        } else if (name.contains("stanza.io")) {
            return R.drawable.client_stanza;
        } else if (name.contains("talkonaut")) {
            return R.drawable.client_talkonaut;
        } else if (name.contains("telepathy")) {
            return R.drawable.client_telepathy;
        } else if (name.contains("webclient")) {
            return R.drawable.client_webclient;
        } else if (name.contains("asterisk")) {
            return R.drawable.client_asterisk;
        } else if (name.contains("bayanicq")) {
            return R.drawable.client_bayanicq;
        } else if (name.contains("bluejabb")) {
            return R.drawable.client_bluejabb;
        } else if (name.contains("bombusng")) {
            return R.drawable.client_bombusng;
        } else if (name.contains("bombuspl")) {
            return R.drawable.client_bombuspl;
        } else if (name.contains("bombusqd")) {
            return R.drawable.client_bombusqd;
        } else if (name.contains("centerim")) {
            return R.drawable.client_centerim;
        } else if (name.contains("chatopus")) {
            return R.drawable.client_chatopus;
        } else if (name.contains("cheogram")) {
            return R.drawable.client_cheogram;
        } else if (name.contains("emclient")) {
            return R.drawable.client_emclient;
        } else if (name.contains("freq-bot")) {
            return R.drawable.client_freq_bot;
        } else if (name.contains("gadugadu")) {
            return R.drawable.client_gadugadu;
        } else if (name.contains("gamebot2")) {
            return R.drawable.client_gamebot2;
        } else if (name.contains("gismeteo")) {
            return R.drawable.client_gismeteo;
        } else if (name.contains("gluxibot")) {
            return R.drawable.client_gluxibot;
        } else if (name.contains("habahaba")) {
            return R.drawable.client_habahaba;
        } else if (name.contains("jabbroid")) {
            return R.drawable.client_jabbroid;
        } else if (name.contains("jtalkmod")) {
            return R.drawable.client_jtalkmod;
        } else if (name.contains("mandarin")) {
            return R.drawable.client_mandarin;
        } else if (name.contains("monocles")) {
            return R.drawable.client_monocles;
        } else if (name.contains("ovi-chat")) {
            return R.drawable.client_ovi_chat;
        } else if (name.contains("palringo")) {
            return R.drawable.client_palringo;
        } else if (name.contains("trillian")) {
            return R.drawable.client_trillian;
        } else if (name.contains("utah-bot")) {
            return R.drawable.client_utah_bot;
        } else if (name.contains("weonlydo")) {
            return R.drawable.client_weonlydo;
        } else if (name.contains("wod-xmpp")) {
            return R.drawable.client_wod_xmpp;
        } else if (name.contains("yaonline")) {
            return R.drawable.client_yaonline;
        } else if (name.contains("zeus-bot")) {
            return R.drawable.client_zeus_bot;
        } else if (name.contains("android")) {
            return R.drawable.client_android;
        } else if (name.contains("beejive")) {
            return R.drawable.client_beejive;
        } else if (name.contains("bitlbee")) {
            return R.drawable.client_bitlbee;
        } else if (name.contains("blabber")) {
            return R.drawable.client_blabber;
        } else if (name.contains("cudumar")) {
            return R.drawable.client_cudumar;
        } else if (name.contains("freelab")) {
            return R.drawable.client_flm;
        } else if (name.contains("freqbot")) {
            return R.drawable.client_freqbot;
        } else if (name.contains("gamebot")) {
            return R.drawable.client_gamebot;
        } else if (name.contains("hipchat")) {
            return R.drawable.client_hipchat;
        } else if (name.contains("jabber2")) {
            return R.drawable.client_jabber2;
        } else if (name.contains("jamebot")) {
            return R.drawable.client_jamebot;
        } else if (name.contains("jasmine")) {
            return R.drawable.client_jasmine;
        } else if (name.contains("jbother")) {
            return R.drawable.client_jbother;
        } else if (name.contains("lampiro")) {
            return R.drawable.client_lampiro;
        } else if (name.contains("m-agent")) {
            return R.drawable.client_m_agent;
        } else if (name.contains("mcabber")) {
            return R.drawable.client_mcabber;
        } else if (name.contains("megafon")) {
            return R.drawable.client_megafon;
        } else if (name.contains("miranda")) {
            return R.drawable.client_miranda;
        } else if (name.contains("nimbuzz")) {
            return R.drawable.client_nimbuzz;
        } else if (name.contains("oneteam")) {
            return R.drawable.client_oneteam;
        } else if (name.contains("pandion")) {
            return R.drawable.client_pandion;
        } else if (name.contains("pix-art")) {
            return R.drawable.client_pix_art;
        } else if (name.contains("psiplus")) {
            return R.drawable.client_psiplus;
        } else if (name.contains("qip2010")) {
            return R.drawable.client_qip2010;
        } else if (name.contains("radio-t")) {
            return R.drawable.client_radio_t;
        } else if (name.contains("secugab")) {
            return R.drawable.client_secugab_messenger;
        } else if (name.contains("tipicim")) {
            return R.drawable.client_tipicim;
        } else if (name.contains("tkabber")) {
            return R.drawable.client_tkabber;
        } else if (name.contains("unknown")) {
            return R.drawable.client_unknown;
        } else if (name.contains("vk4xmpp")) {
            return R.drawable.client_vk4xmpp;
        } else if (name.contains("webchat")) {
            return R.drawable.client_webchat;
        } else if (name.contains("xu6-bot")) {
            return R.drawable.client_xu6_bot;
        } else if (name.contains("bombus")) {
            return R.drawable.client_bombus;
        } else if (name.contains("breeze")) {
            return R.drawable.client_shtorm;
        } else if (name.contains("ebuddy")) {
            return R.drawable.client_ebuddy;
        } else if (name.contains("exodus")) {
            return R.drawable.client_exodus;
        } else if (name.contains("freize")) {
            return R.drawable.client_freize;
        } else if (name.contains("implus")) {
            return R.drawable.client_implus;
        } else if (name.contains("jabber")) {
            return R.drawable.client_jabber;
        } else if (name.contains("jabbim")) {
            return R.drawable.client_jabbim;
        } else if (name.contains("jabbin")) {
            return R.drawable.client_jabbin;
        } else if (name.contains("jabiru")) {
            return R.drawable.client_jabiru;
        } else if (name.contains("jappix")) {
            return R.drawable.client_jappix;
        } else if (name.contains("kandru")) {
            return R.drawable.client_kandu_im;
        } else if (name.contains("kopete")) {
            return R.drawable.client_kopete;
        } else if (name.contains("mabber")) {
            return R.drawable.client_mabber;
        } else if (name.contains("meegim")) {
            return R.drawable.client_meegim;
        } else if (name.contains("meetro")) {
            return R.drawable.client_meetro;
        } else if (name.contains("nekbot")) {
            return R.drawable.client_nekbot;
        } else if (name.contains("osiris")) {
            return R.drawable.client_osiris;
        } else if (name.contains("pidgin")) {
            return R.drawable.client_pidgin;
        } else if (name.contains("poezio")) {
            return R.drawable.client_poezio;
        } else if (name.contains("qippda")) {
            return R.drawable.client_qippda;
        } else if (name.contains("riddim")) {
            return R.drawable.client_riddim;
        } else if (name.contains("safety")) {
            return R.drawable.client_safety_bot;
        } else if (name.contains("tigase")) {
            return R.drawable.client_tigase;
        } else if (name.contains("vacuum")) {
            return R.drawable.client_vacuum;
        } else if (name.contains("xabber")) {
            return R.drawable.client_xabber;
        } else if (name.contains("adium")) {
            return R.drawable.client_adium;
        } else if (name.contains("akari")) {
            return R.drawable.client_akiri_bot;
        } else if (name.contains("akeni")) {
            return R.drawable.client_akeni;
        } else if (name.contains("apple")) {
            return R.drawable.client_apple;
        } else if (name.contains("atalk")) {
            return R.drawable.client_atalk;
        } else if (name.contains("ayttm")) {
            return R.drawable.client_ayttm;
        } else if (name.contains("candy")) {
            return R.drawable.client_candy;
        } else if (name.contains("emess")) {
            return R.drawable.client_emess;
        } else if (name.contains("erlim")) {
            return R.drawable.client_erlim;
        } else if (name.contains("eyecu")) {
            return R.drawable.client_eyecu;
        } else if (name.contains("fatal")) {
            return R.drawable.client_fatal_bot;
        } else if (name.contains("gajim")) {
            return R.drawable.client_gajim;
        } else if (name.contains("gloox")) {
            return R.drawable.client_gloox;
        } else if (name.contains("gmail")) {
            return R.drawable.client_gmail;
        } else if (name.contains("gtalk")) {
            return R.drawable.client_gtalk;
        } else if (name.contains("ichat")) {
            return R.drawable.client_ichat;
        } else if (name.contains("japyt")) {
            return R.drawable.client_japyt;
        } else if (name.contains("jdisk")) {
            return R.drawable.client_jdisk;
        } else if (name.contains("jitsi")) {
            return R.drawable.client_jitsi;
        } else if (name.contains("jtalk")) {
            return R.drawable.client_jtalk;
        } else if (name.contains("juick")) {
            return R.drawable.client_juick_bot;
        } else if (name.contains("mchat")) {
            return R.drawable.client_mchat;
        } else if (name.contains("monal")) {
            return R.drawable.client_monal;
        } else if (name.contains("movim")) {
            return R.drawable.client_movim;
        } else if (name.contains("prose")) {
            return R.drawable.client_prose;
        } else if (name.contains("qutim")) {
            return R.drawable.client_qutim;
        } else if (name.contains("robot")) {
            return R.drawable.client_robot;
        } else if (name.contains("sawim")) {
            return R.drawable.client_sawim;
        } else if (name.contains("siejc")) {
            return R.drawable.client_siejc;
        } else if (name.contains("slick")) {
            return R.drawable.client_slick;
        } else if (name.contains("smack")) {
            return R.drawable.client_smack;
        } else if (name.contains("smuxi")) {
            return R.drawable.client_smuxi;
        } else if (name.contains("snapi")) {
            return R.drawable.client_snapi_snup_bot;
        } else if (name.contains("spark")) {
            return R.drawable.client_spark;
        } else if (name.contains("swift")) {
            return R.drawable.client_swift;
        } else if (name.contains("utalk")) {
            return R.drawable.client_utalk;
        } else if (name.contains("yaxim")) {
            return R.drawable.client_yaxim;
        } else if (name.contains("beem")) {
            return R.drawable.client_beem;
        } else if (name.contains("dino")) {
            return R.drawable.client_dino;
        } else if (name.contains("freo")) {
            return R.drawable.client_freo;
        } else if (name.contains("gaim")) {
            return R.drawable.client_gaim;
        } else if (name.contains("jajc")) {
            return R.drawable.client_jajc;
        } else if (name.contains("jubo")) {
            return R.drawable.client_jubo;
        } else if (name.contains("kadu")) {
            return R.drawable.client_kadu;
        } else if (name.contains("wime")) {
            return R.drawable.client_wime;
        } else if (name.contains("aim")) {
            return R.drawable.client_aim;
        } else if (name.contains("aqq")) {
            return R.drawable.client_aqq;
        } else if (name.contains("bot")) {
            return R.drawable.client_bot;
        } else if (name.contains("glu")) {
            return R.drawable.client_glu;
        } else if (name.contains("psi")) {
            return R.drawable.client_psi;
        } else if (name.contains("qip")) {
            return R.drawable.client_qip;
        } else if (name.contains("rnq")) {
            return R.drawable.client_rnq;
        } else if (name.contains("rss")) {
            return R.drawable.client_rss;
        } else if (name.contains("sim")) {
            return R.drawable.client_sim;
        } else if (name.contains("wtw")) {
            return R.drawable.client_wtw;
        } else if (name.contains("fj")) {
            return R.drawable.client_fj;
        } else if (name.contains("rq")) {
            return R.drawable.client_rq;
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
