package eu.siacs.conversations.ui.util;

import android.text.TextUtils;

import java.util.Map;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.xmpp.Jid;

public final class ClientIconUtils {

    private ClientIconUtils() {
    }

    public static Integer getRosterClientIconRes(final Contact contact) {
        if (contact == null) {
            return null;
        }
        return getIconRes(getTypeForContact(contact));
    }

    public static Integer getMucUserClientIconRes(final MucOptions.User user) {
        if (user == null) {
            return null;
        }
        final Contact contact = user.getContact();
        if (contact == null) {
            return null;
        }
        final Presences presences = contact.getPresences();
        final Map<String, String> types = presences.toTypeAndNameMap().first;
        if (types.isEmpty()) {
            return null;
        }

        final Jid fullJid = user.getFullJid();
        if (fullJid != null && !TextUtils.isEmpty(fullJid.getResource())) {
            final Integer icon = getIconRes(types.get(fullJid.getResource()));
            if (icon != null) {
                return icon;
            }
        }

        return getRosterClientIconRes(contact);
    }

    private static String getTypeForContact(final Contact contact) {
        final Presences presences = contact.getPresences();
        final Map<String, String> types = presences.toTypeAndNameMap().first;
        if (types.isEmpty()) {
            return null;
        }
        final String lastResource = contact.getLastResource();
        if (!TextUtils.isEmpty(lastResource) && types.containsKey(lastResource)) {
            return types.get(lastResource);
        }
        return types.values().iterator().next();
    }

    private static Integer getIconRes(final String rawType) {
        if (TextUtils.isEmpty(rawType)) {
            return null;
        }
        switch (rawType.toLowerCase()) {
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
}
