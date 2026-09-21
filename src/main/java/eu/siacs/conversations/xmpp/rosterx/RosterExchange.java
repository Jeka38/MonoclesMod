package eu.siacs.conversations.xmpp.rosterx;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

/**
 * XEP-0144 Roster Item Exchange payload. Wraps an {@code <x/>} element qualified by either the
 * modern {@code http://jabber.org/protocol/rosterx} namespace (XEP-0144) or the legacy
 * {@code jabber:x:roster} namespace (XEP-0093) and exposes the contained {@link RosterItem}s.
 */
public class RosterExchange {

    private final String from;
    private final List<RosterItem> items;

    private RosterExchange(final String from, final List<RosterItem> items) {
        this.from = from;
        this.items = items;
    }

    /**
     * Extracts a roster exchange from a stanza payload, or returns {@code null} when the stanza
     * does not carry one.
     */
    public static RosterExchange fromElement(final Element message) {
        if (message == null) {
            return null;
        }
        final Element x = findRosterExchangeElement(message);
        if (x == null) {
            return null;
        }
        final List<RosterItem> items = new ArrayList<>();
        for (final Element child : x.getChildren()) {
            final RosterItem item = RosterItem.fromElement(child);
            if (item != null) {
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            return null;
        }
        return new RosterExchange(message.getAttribute("from"), items);
    }

    private static Element findRosterExchangeElement(final Element message) {
        final Element modern = message.findChild("x", Namespace.ROSTERX);
        if (modern != null) {
            return modern;
        }
        return message.findChild("x", Namespace.ROSTER_LEGACY);
    }

    public String getFrom() {
        return from;
    }

    public List<RosterItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    @NonNull
    @Override
    public String toString() {
        return "RosterExchange{from=" + from + ", items=" + items + '}';
    }
}
