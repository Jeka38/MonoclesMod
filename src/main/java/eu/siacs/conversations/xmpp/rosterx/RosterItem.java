package eu.siacs.conversations.xmpp.rosterx;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.Jid;

/**
 * A single roster item suggestion carried inside a XEP-0144 (Roster Item Exchange) payload.
 *
 * <p>Wire format (modern namespace {@code http://jabber.org/protocol/rosterx}):
 *
 * <pre>{@code
 * <item action='add' jid='rosencrantz@denmark.lit' name='Rosencrantz'>
 *   <group>Visitors</group>
 * </item>
 * }</pre>
 *
 * <p>The legacy XEP-0093 namespace {@code jabber:x:roster} carries the same {@code <item/>}
 * children but without an {@code action} attribute (always treated as "add").
 */
public class RosterItem {

    public enum Action {
        ADD,
        DELETE,
        MODIFY
    }

    private final Jid jid;
    private final String name;
    private final List<String> groups;
    private final Action action;

    public RosterItem(final Jid jid, final String name, final List<String> groups, final Action action) {
        this.jid = jid;
        this.name = name;
        this.groups = groups == null ? Collections.emptyList() : groups;
        this.action = action == null ? Action.ADD : action;
    }

    public static RosterItem of(final Contact contact) {
        return new RosterItem(contact.getJid(), contact.getDisplayName(), contact.getGroupNames(), Action.ADD);
    }

    public static RosterItem fromElement(final Element item) {
        if (item == null || !"item".equals(item.getName())) {
            return null;
        }
        final Jid jid;
        try {
            jid = Jid.of(item.getAttribute("jid"));
        } catch (final IllegalArgumentException e) {
            return null;
        }
        final List<String> groups = new ArrayList<>();
        for (final Element child : item.getChildren()) {
            if ("group".equals(child.getName())) {
                groups.add(child.getContent());
            }
        }
        return new RosterItem(jid, item.getAttribute("name"), groups, parseAction(item.getAttribute("action")));
    }

    private static Action parseAction(final String action) {
        if (action == null) {
            return Action.ADD;
        }
        switch (action) {
            case "delete":
                return Action.DELETE;
            case "modify":
                return Action.MODIFY;
            case "add":
            default:
                return Action.ADD;
        }
    }

    private static String actionToString(final Action action) {
        switch (action) {
            case DELETE:
                return "delete";
            case MODIFY:
                return "modify";
            case ADD:
            default:
                return "add";
        }
    }

    public Jid getJid() {
        return jid;
    }

    public String getName() {
        return name;
    }

    public List<String> getGroups() {
        return groups;
    }

    public Action getAction() {
        return action;
    }

    public Element asElement() {
        final Element item = new Element("item");
        item.setAttribute("action", actionToString(action));
        item.setAttribute("jid", jid);
        if (name != null) {
            item.setAttribute("name", name);
        }
        for (final String group : groups) {
            item.addChild("group").setContent(group);
        }
        return item;
    }

    /**
     * Whether the local roster already contains the suggested item in (one of) the suggested
     * group(s). Used to implement the XEP-0144 business rules (do not prompt / do not add when
     * nothing would change).
     */
    public boolean alreadyInRoster(final Account account) {
        final Contact contact = account.getRoster().getContact(jid);
        if (!contact.showInRoster()) {
            return false;
        }
        if (groups.isEmpty()) {
            return true;
        }
        final Set<String> existing = new HashSet<>(contact.getGroupNames());
        for (final String group : groups) {
            if (existing.contains(group)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    @Override
    public String toString() {
        return "RosterItem{" + action + " " + jid + " " + groups + '}';
    }
}
