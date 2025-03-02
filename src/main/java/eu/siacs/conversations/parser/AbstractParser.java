package eu.siacs.conversations.parser;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import java.util.Date;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.InvalidJid;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;

public abstract class AbstractParser {

    protected XmppConnectionService mXmppConnectionService;

    protected AbstractParser(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public static Long parseTimestamp(Element element, Long d) {
        return parseTimestamp(element, d, false);
    }

    public static Long parseTimestamp(Element element, Long d, boolean ignoreCsiAndSm) {
        long min = Long.MAX_VALUE;
        boolean returnDefault = true;
        final Jid to;
        if (ignoreCsiAndSm && element instanceof AbstractStanza) {
            to = ((AbstractStanza) element).getTo();
        } else {
            to = null;
        }
        for (Element child : element.getChildren()) {
            if ("delay".equals(child.getName()) && "urn:xmpp:delay".equals(child.getNamespace())) {
                final Jid f = to == null ? null : InvalidJid.getNullForInvalid(child.getAttributeAsJid("from"));
                if (f != null && (to.asBareJid().equals(f) || to.getDomain().equals(f))) {
                    continue;
                }
                final String stamp = child.getAttribute("stamp");
                if (stamp != null) {
                    try {
                        min = Math.min(min, AbstractParser.parseTimestamp(stamp));
                        returnDefault = false;
                    } catch (Throwable t) {
                        //ignore
                    }
                }
            }
        }
        if (returnDefault) {
            return d;
        } else {
            return min;
        }
    }

    public static long parseTimestamp(Element element) {
        return parseTimestamp(element, System.currentTimeMillis());
    }

    public static long parseTimestamp(String timestamp) throws ParseException {
        timestamp = timestamp.replace("Z", "+0000");
        SimpleDateFormat dateFormat;
        long ms;
        if (timestamp.length() >= 25 && timestamp.charAt(19) == '.') {
            String millis = timestamp.substring(19, timestamp.length() - 5);
            try {
                double fractions = Double.parseDouble("0" + millis);
                ms = Math.round(1000 * fractions);
            } catch (NumberFormatException e) {
                ms = 0;
            }
        } else {
            ms = 0;
        }
        timestamp = timestamp.substring(0, 19) + timestamp.substring(timestamp.length() - 5, timestamp.length());
        dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
        return Math.min(dateFormat.parse(timestamp).getTime() + ms, System.currentTimeMillis());
    }
    public static long getTimestamp(final String input) throws ParseException {
        if (input == null) {
            throw new IllegalArgumentException("timestamp should not be null");
        }
        final String timestamp = input.replace("Z", "+0000");
        final SimpleDateFormat simpleDateFormat =
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
        final long milliseconds = getMilliseconds(timestamp);
        final String formatted =
                timestamp.substring(0, 19) + timestamp.substring(timestamp.length() - 5);
        final Date date = simpleDateFormat.parse(formatted);
        if (date == null) {
            throw new IllegalArgumentException("Date was null");
        }
        return date.getTime() + milliseconds;
    }

    private static long getMilliseconds(final String timestamp) {
        if (timestamp.length() >= 25 && timestamp.charAt(19) == '.') {
            final String millis = timestamp.substring(19, timestamp.length() - 5);
            try {
                double fractions = Double.parseDouble("0" + millis);
                return Math.round(1000 * fractions);
            } catch (NumberFormatException e) {
                return 0;
            }
        } else {
            return 0;
        }
    }

    protected void updateLastseen(final Account account, final Jid from) {
        final Contact contact = account.getRoster().getContact(from);
        contact.setLastResource(from.isBareJid() ? "" : from.getResource());
    }

    protected String avatarData(Element items) {
        Element item = items.findChild("item");
        if (item == null) {
            return null;
        }
        return item.findChildContent("data", Namespace.AVATAR_DATA);
    }

    public static MucOptions.User parseItem(Conversation conference, Element item) {
        return parseItem(conference,item,null,null,null,new Element("hats", "urn:xmpp:hats:0"));
    }

    public static MucOptions.User parseItem(Conversation conference, Element item, Jid fullJid, final Element occupantId, final String nicknameIn, final Element hatsEl) {
        final String local = conference.getJid().getLocal();
        final String domain = conference.getJid().getDomain().toEscapedString();
        String affiliation = item.getAttribute("affiliation");
        String role = item.getAttribute("role");
        String nick = item.getAttribute("nick");
        if (nick != null && fullJid == null) {
            try {
                fullJid = Jid.of(local, domain, nick);
            } catch (IllegalArgumentException e) {
                fullJid = null;
            }
        }
        Jid realJid = item.getAttributeAsJid("jid");
        if (fullJid != null) nick = fullJid.getResource();
        String nickname = null;
        if (nick != null && nicknameIn != null) nickname = nick.equals(nicknameIn) ? nick : null;
        try {
            if (nickname == null && nicknameIn != null && nick != null && gnu.inet.encoding.Punycode.decode(nick).equals(nicknameIn)) {
                nickname = nicknameIn;
            }
        } catch (final Exception e) { }
        Set<MucOptions.Hat> hats = new TreeSet<>();
        for (Element hat : hatsEl.getChildren()) {
            if ("hat".equals(hat.getName()) && ("urn:xmpp:hats:0".equals(hat.getNamespace()) || "xmpp:prosody.im/protocol/hats:1".equals(hat.getNamespace()))) {
                hats.add(new MucOptions.Hat(hat));
            }
        }
        MucOptions.User user = new MucOptions.User(conference.getMucOptions(), fullJid, occupantId == null ? null : occupantId.getAttribute("id"), nickname, hatsEl == null ? null : hats);
        if (InvalidJid.isValid(realJid)) {
            user.setRealJid(realJid);
        }
        user.setAffiliation(affiliation);
        user.setRole(role);
        return user;
    }

    public static String extractErrorMessage(Element packet) {
        final Element error = packet.findChild("error");
        if (error != null && error.getChildren().size() > 0) {
            final List<String> errorNames = orderedElementNames(error.getChildren());
            final String text = error.findChildContent("text");
            if (text != null && !text.trim().isEmpty()) {
                return prefixError(errorNames) + text;
            } else if (errorNames.size() > 0) {
                return prefixError(errorNames) + errorNames.get(0).replace("-", " ");
            }
        }
        return null;
    }

    public static String errorMessage(Element packet) {
        final Element error = packet.findChild("error");
        if (error != null && error.getChildren().size() > 0) {
            final List<String> errorNames = orderedElementNames(error.getChildren());
            final String text = error.findChildContent("text");
            if (text != null && !text.trim().isEmpty()) {
                return text;
            } else if (errorNames.size() > 0){
                return errorNames.get(0).replace("-"," ");
            }
        }
        return null;
    }

    private static String prefixError(List<String> errorNames) {
        if (errorNames.size() > 0) {
            return errorNames.get(0) + '\u001f';
        }
        return "";
    }

    private static List<String> orderedElementNames(List<Element> children) {
        List<String> names = new ArrayList<>();
        for (Element child : children) {
            final String name = child.getName();
            if (name != null && !name.equals("text")) {
                if ("urn:ietf:params:xml:ns:xmpp-stanzas".equals(child.getNamespace())) {
                    names.add(name);
                } else {
                    names.add(0, name);
                }
            }
        }
        return names;
    }
}
