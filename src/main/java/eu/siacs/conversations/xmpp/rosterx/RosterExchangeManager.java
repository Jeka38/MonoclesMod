package eu.siacs.conversations.xmpp.rosterx;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

/**
 * XEP-0144 Roster Item Exchange.
 *
 * <p>Sender side: wraps one or more {@link RosterItem}s into a {@code <message/>} stanza and sends
 * them to a peer. Receiver side: detects incoming payloads and hands them to the service, which
 * dispatches to the foreground UI (required to prompt the user before touching the roster) or shows
 * a notification when the app is in the background.
 */
public class RosterExchangeManager {

    private final XmppConnectionService mXmppConnectionService;

    public RosterExchangeManager(final XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    /**
     * Sends the given roster items to {@code to} on behalf of {@code account}.
     *
     * @return {@code true} if the stanza was handed to the connection.
     */
    public boolean send(final Account account, final Jid to, final List<RosterItem> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        final Element x = new Element("x", Namespace.ROSTERX);
        for (final RosterItem item : items) {
            x.addChild(item.asElement());
        }
        final MessagePacket packet = new MessagePacket();
        packet.setType(MessagePacket.TYPE_NORMAL);
        packet.setTo(to.asBareJid());
        packet.setFrom(account.getJid());
        packet.setId(java.util.UUID.randomUUID().toString());
        packet.addChild(x);
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": sending roster exchange with "
                + items.size() + " item(s) to " + to);
        mXmppConnectionService.sendMessagePacket(account, packet);
        return true;
    }

    public boolean send(final Account account, final Jid to, final RosterItem item) {
        return send(account, to, Collections.singletonList(item));
    }

    /**
     * Handles an incoming stanza that may carry a roster exchange. Returns {@code true} when a
     * roster exchange was present and consumed, so the caller can stop further message processing.
     */
    public boolean onStanzaReceived(final Account account, final Jid from, final Element packet) {
        final RosterExchange exchange = RosterExchange.fromElement(packet);
        if (exchange == null) {
            return false;
        }
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received roster exchange with "
                + exchange.getItems().size() + " item(s) from " + from);
        if (!mXmppConnectionService.displayRosterExchangeRequest(account, from, exchange)) {
            mXmppConnectionService.getNotificationService()
                    .notifyRosterExchange(from, exchange.getItems().size());
        }
        return true;
    }

    /** Applies an accepted suggestion to the local roster. */
    public void apply(final Account account, final RosterItem item) {
        switch (item.getAction()) {
            case ADD:
            case MODIFY:
                addToRoster(account, item);
                break;
            case DELETE:
                mXmppConnectionService.deleteContactOnServer(
                        account.getRoster().getContact(item.getJid()));
                break;
        }
    }

    private void addToRoster(final Account account, final RosterItem item) {
        final var contact = account.getRoster().getContact(item.getJid());
        if (item.getName() != null && !item.getName().isEmpty()) {
            contact.setServerName(item.getName());
        }
        final LinkedHashSet<String> groups = new LinkedHashSet<>(contact.getGroupNames());
        groups.addAll(item.getGroups());
        contact.setGroups(new ArrayList<>(groups));
        mXmppConnectionService.createContact(contact, true);
        mXmppConnectionService.updateRosterUi(XmppConnectionService.UpdateRosterReason.PUSH, contact);
    }
}
