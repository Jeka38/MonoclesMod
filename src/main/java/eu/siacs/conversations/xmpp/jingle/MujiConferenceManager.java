package eu.siacs.conversations.xmpp.jingle;

import androidx.annotation.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;

/**
 * Registry for active XEP-0272 Multiparty Jingle (Muji) conferences, keyed by account and MUC room.
 */
public class MujiConferenceManager {

    private final XmppConnectionService xmppConnectionService;
    private final Map<String, MujiConference> conferences = new ConcurrentHashMap<>();

    public MujiConferenceManager(final XmppConnectionService service) {
        this.xmppConnectionService = service;
    }

    private static String key(final Account account, final Jid room) {
        return account.getUuid() + '\u0000' + room.asBareJid().toString();
    }

    public XmppConnectionService getXmppConnectionService() {
        return xmppConnectionService;
    }

    @Nullable
    public MujiConference get(final Account account, final Jid room) {
        if (account == null || room == null) {
            return null;
        }
        return conferences.get(key(account, room.asBareJid()));
    }

    @Nullable
    public MujiConference get(final Conversation conversation) {
        return get(conversation.getAccount(), conversation.getJid());
    }

    public MujiConference join(final Conversation conversation, final Set<Media> media) {
        final Account account = conversation.getAccount();
        final Jid room = conversation.getJid().asBareJid();
        final MujiConference existing = conferences.get(key(account, room));
        if (existing != null) {
            existing.updateMedia(media);
            return existing;
        }
        final MujiConference conference = new MujiConference(this, conversation, media);
        conferences.put(key(account, room), conference);
        conference.join();
        if (!conference.isActive()) {
            conferences.remove(key(account, room));
        }
        return conference;
    }

    public void leave(final Account account, final Jid room) {
        if (account == null || room == null) {
            return;
        }
        final MujiConference conference = conferences.remove(key(account, room.asBareJid()));
        if (conference != null) {
            conference.leave();
        }
    }

    public void leave(final Conversation conversation) {
        leave(conversation.getAccount(), conversation.getJid());
    }

    public boolean isActive(final Account account, final Jid room) {
        return get(account, room) != null;
    }

    public void onOccupantPresence(final Conversation conversation, final MucOptions.User user) {
        final MujiConference conference = get(conversation);
        if (conference != null) {
            conference.onOccupantPresence(user);
        }
    }
}
