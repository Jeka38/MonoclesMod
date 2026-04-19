package eu.siacs.conversations.ui.util;

import android.text.TextUtils;
import android.util.Pair;

import java.util.Locale;
import java.util.Map;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.xmpp.Jid;

public final class ClientIconUtils {

    private ClientIconUtils() {
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
}
