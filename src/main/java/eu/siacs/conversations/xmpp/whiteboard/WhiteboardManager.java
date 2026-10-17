package eu.siacs.conversations.xmpp.whiteboard;

import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jingle.MujiLog;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.jingle.stanzas.Reason;
import eu.siacs.conversations.xmpp.sxe.SxeDocument;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

/**
 * Collaborative SVG whiteboard: a Jingle application ({@link Namespace#JINGLE_APPS_SVG_WHITEBOARD})
 * over the Shared XML Editing transport ({@link Namespace#SXE_TRANSPORT}).
 *
 * <p>The shared document is an SVG document synchronized with the XEP-0284 record model
 * ({@link SxeDocument}). The host relays the SXE payloads: for a 1:1 session the initiator is the
 * host, for a MUC session the room JID is the host and the payloads travel as {@code groupchat}
 * messages.
 *
 * <p>Local drawing operations become {@code <new>}/{@code <set>}/{@code <remove>} edits; incoming
 * edits are applied to the document and listeners are told to rebuild.
 */
public final class WhiteboardManager {

    public static final String NS_WHITEBOARD = Namespace.JINGLE_APPS_SVG_WHITEBOARD;
    public static final String NS_SXE = Namespace.SXE;
    public static final String NS_SXE_TRANSPORT = Namespace.SXE_TRANSPORT;

    /**
     * U+2063 (INVISIBLE SEPARATOR), used only to recognise messages sent by an older build that
     * padded a room payload with an invisible body. Current builds send SXE payloads with **no**
     * body at all (like the XEP examples and like our own MUC chat-state messages), so nothing
     * renders as an empty chat bubble; keeping the check means an older peer's payload is still
     * swallowed instead of showing up as an empty message.
     */
    private static final String LEGACY_SXE_FALLBACK_BODY = "\u2063";

    private static final int VIEW_W = (int) WhiteboardView.VIEW_W;
    private static final int VIEW_H = (int) WhiteboardView.VIEW_H;

    public interface OnWhiteboardUpdate {
        /** A shared document changed (a session became ready or remote edits arrived). */
        void onWhiteboardUpdated(String sid);
    }

    public static final class Session {
        public final String sid;
        public final Jid peer;
        public final boolean muc;
        public final Account account;
        public boolean host;
        public String fullPeer = "";
        public final SxeDocument doc = new SxeDocument();
        public boolean ready = false;
        public boolean offered = false;
        public boolean accepted = false;
        public boolean joined = false;
        /** The uuid of the in-chat "Open board" notice posted for this session, or null. */
        public String noticeUuid = null;
        /** True only when this device itself called {@link #startSession} (wording of the notice). */
        public boolean locallyStarted = false;
        /** For a MUC notice, the occupant (full JID) that started the board, to show their nick. */
        public Jid noticeCounterpart = null;

        Session(final String sid, final Jid peer, final boolean muc, final boolean host, final Account account) {
            this.sid = sid;
            this.peer = peer;
            this.muc = muc;
            this.host = host;
            this.account = account;
        }
    }

    private final XmppConnectionService service;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> byPeer = new ConcurrentHashMap<>();
    private final List<OnWhiteboardUpdate> listeners = new ArrayList<>();

    public WhiteboardManager(final XmppConnectionService service) {
        this.service = service;
    }

    public void addListener(final OnWhiteboardUpdate listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
    }

    public void removeListener(final OnWhiteboardUpdate listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    private void notifyUpdate(final String sid) {
        final List<OnWhiteboardUpdate> copy;
        synchronized (listeners) {
            copy = new ArrayList<>(listeners);
        }
        for (final OnWhiteboardUpdate listener : copy) {
            listener.onWhiteboardUpdated(sid);
        }
    }

    // ── Lookups ───────────────────────────────────────────────────────────

    @Nullable
    public Session getSession(final String sid) {
        return sid == null ? null : sessions.get(sid);
    }

    @Nullable
    public Session getSessionForPeer(final Account account, final Jid peer) {
        if (account == null || peer == null) {
            return null;
        }
        final String sid = byPeer.get(key(account, peer));
        return sid == null ? null : sessions.get(sid);
    }

    public boolean isActive(final Account account, final Jid peer) {
        return getSessionForPeer(account, peer) != null;
    }

    private static String key(final Account account, final Jid peer) {
        return account.getUuid() + '\u0000' + peer.asBareJid().toString();
    }

    /**
     * A session id that is identical on both ends for the same account/peer pair. This is essential:
     * if the two sides each opened the board and derived their own random id, the UI would attach to
     * one session while edits were broadcast under the other, so only the initial snapshot synced.
     *
     * <p>For a MUC the id is derived from the room alone so every occupant who presses "open"
     * converges on the same room-wide board.
     */
    private static String sessionIdFor(final Account account, final Jid peer, final boolean muc) {
        if (muc) {
            return sha1("muc|" + peer.asBareJid().toString());
        }
        final String a = account.getUuid();
        final String b = peer.asBareJid().toString();
        final String ordered = a.compareTo(b) <= 0 ? a + '|' + b : b + '|' + a;
        return sha1("one|" + ordered);
    }

    private static String sha1(final String value) {
        try {
            final java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            final byte[] digest = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            final StringBuilder sb = new StringBuilder(digest.length * 2);
            for (final byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (final java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    /**
     * For a 1:1 session the host is chosen deterministically (the account with the smaller bare JID)
     * so both ends agree even when they both pressed "open" at the same time — otherwise both would
     * believe they are the host and answer each other's state offers, replacing their own drawing.
     */
    private static boolean hostFor(final Account account, final Jid peer) {
        return account.getJid().asBareJid().toString().compareTo(peer.asBareJid().toString()) < 0;
    }

    /**
     * A MUC occupant advertised a shared whiteboard in their presence ({@code <sxe session=…/>}).
     * Discover the room board from that (which also covers a member who joins the room after the
     * board started, since the MUC service replays the other occupants' presence to them), post the
     * <em>Open board</em> notice and ask for the current document once.
     */
    public void onMucPresenceSxe(final Account account, final Jid room, final Jid occupant, final String sid) {
        if (account == null || room == null || occupant == null || sid == null || sid.isEmpty()) {
            return;
        }
        if (isSelfReflection(account, occupant)) {
            // Our own presence (or another device on this account) — the board is ours, and
            // startSession already posted the notice.
            return;
        }
        Session session = sessions.get(sid);
        if (session == null) {
            session = new Session(sid, room.asBareJid(), true, false, account);
            seedDocument(session.doc);
            sessions.put(sid, session);
            byPeer.put(key(account, room.asBareJid()), sid);
            session.noticeCounterpart = occupant;
            postWhiteboardNotice(account, session);
        }
        if (!session.joined) {
            session.joined = true;
            send(account, session, sxe(session, "connect"), null);
        }
    }

    public boolean isWhiteboardJingle(final JinglePacket packet) {
        // <content> is a child of <jingle>, not of the IQ — a plain findChild on the packet never
        // matches, which would make the whiteboard branch dead and the invite get rejected.
        final Element content = packet.getJingleChild("content");
        if (content == null) {
            return false;
        }
        final Element description = content.findChild("description");
        return description != null && NS_WHITEBOARD.equals(description.getNamespace());
    }

    // ── Session lifecycle ─────────────────────────────────────────────────

    /**
     * Start (or return the already running) shared session for this peer. The session id is derived
     * deterministically so both ends converge on the same session no matter who opened the board
     * first, which is what keeps the live edits flowing to the same session the UI is attached to.
     */
    public String startSession(final Account account, final Jid peer, final boolean muc) {
        final Jid barePeer = peer.asBareJid();
        final Session existing = getSessionForPeer(account, barePeer);
        if (existing != null) {
            return existing.sid;
        }
        final String sid = sessionIdFor(account, barePeer, muc);
        final Session session = new Session(sid, barePeer, muc, muc || hostFor(account, barePeer), account);
        session.locallyStarted = true;
        seedDocument(session.doc);
        sessions.put(sid, session);
        byPeer.put(key(account, barePeer), sid);
        if (muc) {
            // Room board: the room JID is the host and payloads travel as groupchat. Announce in
            // presence + connect, then post the "Open board" notice into the room so every member
            // (including this device) sees the button and gets a notification per the room settings.
            advertise(session);
            session.ready = true;
            send(account, session, sxe(session, "connect"), null);
            // Mark joined so attachSession does not send a second <connect> (which would make every
            // participant with content answer with another snapshot).
            session.joined = true;
            notifyUpdate(sid);
            postWhiteboardNotice(account, session);
        } else {
            // Always invite: whoever opens the board must let the peer know (otherwise a board
            // opened by the non-deterministic-host side would silently do nothing). If both sides
            // invite the same deterministic sid, the receiver already has it and ignores the second
            // invite, so there is no double document. `host` is still used for snapshot duty.
            sendJingle(account, session, "session-initiate");
        }
        return sid;
    }

    /** Accept an incoming invite and connect to the host. */
    public void joinSession(final Session session) {
        if (session == null || session.joined) {
            return;
        }
        session.joined = true;
        if (!session.muc) {
            sendJingle(session.account, session, "session-accept");
        }
        send(session.account, session, sxe(session, "connect"), null);
    }

    public void endSession(final Session session) {
        if (session == null) {
            return;
        }
        sessions.remove(session.sid);
        byPeer.remove(key(session.account, session.peer));
        if (!session.muc) {
            try {
                sendJingle(session.account, session, "session-terminate");
            } catch (final RuntimeException e) {
                Log.d(Config.LOGTAG, "whiteboard: could not terminate session", e);
            }
        } else {
            // Clear the room presence advertisement. The MUC service stores the occupants' last
            // presence and replays it to anyone who joins later, so a stale <sxe/> would make the
            // "Open board" button reappear for every re-entry of the room (and for a late joiner)
            // even though the board is gone.
            clearAdvertisement(session);
        }
        // The board is gone: drop the in-chat "Open board" notice so it does not linger.
        removeWhiteboardNotice(session);
        notifyUpdate(session.sid);
    }

    /** Re-sends our room presence **without** the {@code <sxe/>} board advertisement. */
    private void clearAdvertisement(final Session session) {
        sendAdvertisement(session, false);
    }

    private void advertise(final Session session) {
        sendAdvertisement(session, true);
    }

    private void sendAdvertisement(final Session session, final boolean withSxe) {
        final Conversation conversation = service.find(session.account, session.peer);
        if (conversation == null || conversation.getMode() != Conversation.MODE_MULTI) {
            return;
        }
        final MucOptions mucOptions = conversation.getMucOptions();
        final MucOptions.User self = mucOptions.getSelf();
        if (self == null || self.getFullJid() == null) {
            return;
        }
        final PresencePacket packet = service.getPresenceGenerator()
                .selfPresence(session.account, Presence.Status.ONLINE, false, self.getNick());
        packet.setTo(self.getFullJid());
        packet.addChild("x", Namespace.MUC);
        if (withSxe) {
            packet.addChild(sxeElement(session));
        }
        service.sendPresencePacket(session.account, packet);
    }

    private static void seedDocument(final SxeDocument doc) {
        doc.setAttr(doc.getRootRid(), "version", "1.2");
        doc.setAttr(doc.getRootRid(), "baseProfile", "tiny");
        doc.setAttr(doc.getRootRid(), "viewBox", "0 0 " + VIEW_W + " " + VIEW_H);
    }

    // ── Local edits ───────────────────────────────────────────────────────

    public String addShape(final String sid, final WhiteboardShape shape) {
        final Session session = sessions.get(sid);
        if (session == null) {
            return "";
        }
        final Map<String, String> attrs = new LinkedHashMap<>();
        for (final String[] pair : shape.attrs()) {
            // Skip empty attributes (e.g. an identity transform), like the reference implementation.
            if (pair[1] != null && !pair[1].isEmpty()) {
                attrs.put(pair[0], pair[1]);
            }
        }
        final String rid = session.doc.addElement(session.doc.getRootRid(), "path", "", attrs);
        sendEdits(session, session.doc.newElementsFor(rid), null, null);
        return rid;
    }

    public void updateShape(final String sid, final String rid, final WhiteboardShape shape) {
        final Session session = sessions.get(sid);
        if (session == null || rid == null || rid.isEmpty() || session.doc.getRecord(rid) == null) {
            return;
        }
        final List<Element> news = new ArrayList<>();
        final List<Element> sets = new ArrayList<>();
        for (final String[] pair : shape.attrs()) {
            // An attribute that does not exist yet (e.g. the first `transform` of a moved shape) is a
            // NEW record: it must be announced with <new parent="…"/>, not <set>. Sending a <set> for
            // a record id the peer has never seen is silently dropped (applySet finds no target), so
            // the peer would never learn about the attribute — that is why a first move did not sync.
            final boolean existed = session.doc.findAttr(rid, pair[0]) != null;
            final String attrRid = session.doc.setAttr(rid, pair[0], pair[1]);
            if (existed) {
                sets.add(setElement(session.doc, attrRid));
            } else {
                news.addAll(session.doc.newElementsFor(attrRid));
            }
        }
        sendEdits(session, news, sets, null);
    }

    public void removeShape(final String sid, final String rid) {
        final Session session = sessions.get(sid);
        if (session == null || rid == null || rid.isEmpty()) {
            return;
        }
        final List<String> removed = session.doc.remove(rid);
        final List<Element> removes = new ArrayList<>();
        for (final String recordId : removed) {
            removes.add(removeElement(recordId));
        }
        sendEdits(session, null, null, removes);
    }

    /** The current shapes of the session document, in document order. */
    public List<WhiteboardShape> shapes(final String sid) {
        final List<WhiteboardShape> out = new ArrayList<>();
        final Session session = sessions.get(sid);
        if (session == null) {
            return out;
        }
        final SxeDocument doc = session.doc;
        for (final String child : doc.children(doc.getRootRid())) {
            final SxeDocument.Record record = doc.getRecord(child);
            if (record == null || !"element".equals(record.type) || !"path".equals(record.name)) {
                continue;
            }
            final Map<String, String> attrs = new LinkedHashMap<>();
            for (final String attrId : doc.children(child)) {
                final SxeDocument.Record attr = doc.getRecord(attrId);
                if (attr != null && "attr".equals(attr.type)) {
                    attrs.put(attr.name, attr.chdata);
                }
            }
            out.add(WhiteboardShape.fromRemote(
                    child, attrs.get("id"), attrs.get("d"),
                    attrs.get("stroke"), attrs.get("stroke-width"), attrs.get("fill"),
                    attrs.get("transform")));
        }
        return out;
    }

    public boolean isReady(final String sid) {
        final Session session = sessions.get(sid);
        return session != null && session.ready;
    }

    // ── Jingle dispatch ───────────────────────────────────────────────────

    public void dispatchJingle(final Account account, final JinglePacket packet) {
        final String sid = packet.getSessionId();
        final String action = packet.getAction() == null ? null : packet.getAction().toString();
        final Jid from = packet.getFrom();
        MujiLog.log(service.getFilesDir(), "WB dispatch action=" + action + " sid=" + sid + " from=" + from);
        if (sid == null || action == null || from == null) {
            return;
        }
        final Jid peer = from.asBareJid();
        final boolean isInitiate = "session-initiate".equals(action);
        final boolean known = sessions.containsKey(sid) || byPeer.containsKey(key(account, peer));
        switch (action) {
            case "session-initiate": {
                final Session existing = sessions.get(sid);
                if (existing != null) {
                    // A duplicate invite for a session we already have (e.g. both sides opened the
                    // board and the same deterministic sid was computed). Just accept it again so the
                    // peer's `session-accept` expectation is met, but do not create a second session.
                    if (!existing.joined) {
                        joinSession(existing);
                    }
                    // Surface the "Open board" button when the peer's invite arrives for a session
                    // that has no notice yet. Without this the peer's board is invisible if we
                    // happened to hold the session already (e.g. we opened it once and left the
                    // screen: startSession had put the session in the map, so the peer's initiate
                    // looked like a duplicate and no notice was ever posted because startSession for
                    // a 1:1 does not post one). `noticeUuid == null` is the dedup guard.
                    if (existing.noticeUuid == null) {
                        existing.noticeCounterpart = from;
                        postWhiteboardNotice(account, existing);
                    }
                    break;
                }
                // Same deterministic id as the initiator computed; adopt it so both ends share
                // one session and the live edits land in the session the UI is attached to.
                final Session session = new Session(sid, peer, false, hostFor(account, peer), account);
                session.fullPeer = from.toString();
                seedDocument(session.doc);
                sessions.put(sid, session);
                byPeer.put(key(account, peer), sid);
                // Synchronize by default: accept the invite so the shared document is live on this
                // end too. Do NOT auto-open the screen or post a push — surface it as a normal
                // in-chat message with an "Open board" button instead.
                joinSession(session);
                notifyUpdate(sid);
                postWhiteboardNotice(account, session);
                break;
            }
            case "session-accept": {
                final Session session = sessions.get(sid);
                if (session != null) {
                    session.fullPeer = from.toString();
                }
                break;
            }
            case "session-terminate": {
                final Session terminated = sessions.remove(sid);
                if (terminated != null) {
                    byPeer.remove(key(account, peer));
                    // The peer ended the board: drop our "Open board" notice too.
                    removeWhiteboardNotice(terminated);
                    notifyUpdate(sid);
                }
                break;
            }
            default:
                Log.d(Config.LOGTAG, "whiteboard: unhandled Jingle action " + action);
        }
    }

    /**
     * Posts an in-chat notice that a board started (normal bubble + "Open board" button). The uuid is
     * remembered on the session so the message can be removed when the board is cancelled/closed.
     * Works for a 1:1 conversation and for a MUC room (the notice lands in the room and the sender's
     * own device sees it too, like a normal group message).
     */
    private void postWhiteboardNotice(final Account account, final Session session) {
        final Jid peer = session.peer;
        final Conversation conversation = session.muc
                ? service.findOrCreateConversation(account, peer.asBareJid(), true, false)
                : service.findOrCreateConversation(account, peer.asBareJid(), false, false);
        MujiLog.log(service.getFilesDir(), "WB notice peer=" + peer + " muc=" + session.muc
                + " conv=" + (conversation == null ? "null" : conversation.getUuid()));
        if (conversation == null) {
            return;
        }
        // A room board is re-discovered whenever the MUC replays an occupant's presence, so a
        // duplicate "Open board" button must never be added there. A 1:1 board start however is a
        // single event (one Jingle session-initiate), so a stale notice from an earlier board must
        // not suppress the new one: drop every leftover notice and post a fresh one.
        final java.util.List<Message> existing = new java.util.ArrayList<>();
        conversation.findMessages(m -> m.getType() == Message.TYPE_WHITEBOARD, existing::add);
        if (session.muc) {
            if (!existing.isEmpty()) {
                session.noticeUuid = existing.get(0).getUuid();
                return;
            }
        } else {
            boolean changed = false;
            for (final Message stale : existing) {
                conversation.deleteMessage(stale);
                service.databaseBackend.deleteMessageInConversation(stale);
                changed = true;
            }
            if (changed) {
                service.updateConversationUi();
            }
        }
        final Message message = Message.createWhiteboardNotice(conversation,
                session.noticeCounterpart != null ? session.noticeCounterpart : peer);
        message.setStatus(session.locallyStarted ? Message.STATUS_SEND : Message.STATUS_RECEIVED);
        // A body keeps the chat-list preview and any plain-text fallback meaningful.
        message.setBody(service.getString(session.locallyStarted
                ? eu.siacs.conversations.R.string.whiteboard_chat_notice_self
                : eu.siacs.conversations.R.string.whiteboard_chat_notice));
        conversation.add(message);
        service.databaseBackend.createMessage(message);
        session.noticeUuid = message.getUuid();
        service.updateConversationUi();
        // Only an incoming board raises the unread badge and a notification — announcing our own
        // board must not notify us. For an incoming one the room's notification settings apply: a
        // muted room, or one that only notifies on highlights, will not raise a notification
        // (NotificationService.notifyMessage honours exactly that).
        if (!session.locallyStarted) {
            message.markUnread();
            service.getNotificationService().push(message);
        }
    }

    /**
     * Removes the in-chat "Open board" notice of a session (when the board is cancelled or closed),
     * so the button does not linger for a board that no longer exists.
     */
    private void removeWhiteboardNotice(final Session session) {
        if (session == null || session.noticeUuid == null) {
            return;
        }
        final Conversation conversation =
                service.find(session.account, session.peer.asBareJid());
        final String uuid = session.noticeUuid;
        session.noticeUuid = null;
        if (conversation == null) {
            return;
        }
        final Message message = conversation.findMessageWithUuid(uuid);
        if (message != null) {
            conversation.deleteMessage(message);
            service.databaseBackend.deleteMessageInConversation(message);
            service.updateConversationUi();
        }
    }

    /**
     * The 1:1 Jingle target: the peer's **full JID** (a resource that advertises the whiteboard,
     * else the most available one). A Jingle IQ to a bare JID is rejected by the server with
     * `service-unavailable` ("No module is handling this query"), so the invite would never arrive.
     */
    private Jid jingleTarget(final Account account, final Session session) {
        if (session.muc) {
            return session.peer;
        }
        if (!session.fullPeer.isEmpty()) {
            return Jid.of(session.fullPeer);
        }
        final Contact contact = account.getRoster().getContact(session.peer);
        if (contact != null) {
            final Jid resource = contact.resourceWhichSupport(NS_WHITEBOARD);
            if (resource != null && resource.isFullJid()) {
                return resource;
            }
            final String mostAvailable = contact.getMostAvailableResource();
            if (mostAvailable != null && !mostAvailable.isEmpty()) {
                return contact.getJid().asBareJid().withResource(mostAvailable);
            }
        }
        return session.peer;
    }

    private void sendJingle(final Account account, final Session session, final String action) {
        if (account == null) {
            return;
        }
        final JinglePacket packet = new JinglePacket(JinglePacket.Action.of(action), session.sid);
        final Jid target = jingleTarget(account, session);
        packet.setTo(target);
        MujiLog.log(service.getFilesDir(), "WB tx jingle " + action + " sid=" + session.sid + " to=" + target
                + " (full=" + (target != null && target.isFullJid()) + ")");
        if ("session-initiate".equals(action)) {
            packet.setInitiator(account.getJid());
        }
        if (!"session-terminate".equals(action)) {
            final Element content = new Element("content", Namespace.JINGLE);
            content.setAttribute("creator", "initiator");
            content.setAttribute("name", "whiteboard");
            content.addChild("description", NS_WHITEBOARD);
            final Element transport = new Element("transport", NS_SXE_TRANSPORT);
            final Element host = transport.addChild("host", NS_SXE_TRANSPORT);
            host.setContent(session.muc
                    ? session.peer.toEscapedString()
                    : account.getJid().asBareJid().toEscapedString());
            content.addChild(transport);
            packet.addJingleChild(content);
        } else {
            packet.setReason(Reason.SUCCESS, null);
        }
        service.sendIqPacket(account, packet, null);
    }

    // ── SXE messages ──────────────────────────────────────────────────────

    /**
     * Handle a XEP-0284 {@code <sxe/>} message; returns true if it was consumed.
     *
     * @param live true for live traffic, false for a replayed MAM copy. A replayed copy is always
     *     swallowed (so an older build's invisible-body payload can never resurface as an empty
     *     chat row), but it does not create a session, post a join notice or send a {@code <connect>}.
     */
    public boolean handleSxeMessage(
            final Account account, final MessagePacket packet, final boolean live) {
        final Element sxe = packet.findChild("sxe", NS_SXE);
        if (sxe == null || !isWhiteboardBody(packet)) {
            return false;
        }
        if (!live) {
            return true;
        }
        if (packet.fromAccount(account)) {
            // our own carbon of our own edit
            return true;
        }
        final String sid = sxe.getAttribute("session");
        final Jid from = packet.getFrom();
        MujiLog.log(service.getFilesDir(), "WB sxe sid=" + sid + " from=" + from
                + " child=" + (firstChild(sxe) == null ? "null" : firstChild(sxe).getName()));
        if (sid == null || from == null) {
            return true;
        }
        final boolean isGroupChat = packet.getType() == MessagePacket.TYPE_GROUPCHAT;
        if (isGroupChat && isSelfReflection(account, from)) {
            // The MUC service reflects our own groupchat message back to us.
            return true;
        }
        Session session = sessions.get(sid);
        if (session == null) {
            // For a MUC there is no Jingle invite: the room is the relay and every occupant is a
            // plain participant. The deterministic host rule only applies to 1:1.
            final boolean host = !isGroupChat && hostFor(account, from.asBareJid());
            session = new Session(sid, from.asBareJid(), isGroupChat, host, account);
            seedDocument(session.doc);
            sessions.put(sid, session);
            byPeer.put(key(account, from.asBareJid()), sid);
            // A room board has no Jingle invite, so as soon as another participant starts one we
            // post the same in-chat notice (with the button) and notify per the room settings. A 1:1
            // board is normally announced by its Jingle invite; if that was missed we still surface
            // the button when the peer's SXE traffic reveals the board.
            if (isGroupChat) {
                session.noticeCounterpart = from;
                postWhiteboardNotice(account, session);
                // Ask whoever already has the document for the current snapshot, so a member who
                // discovers the board through an edit (rather than presence) still catches up.
                // Idempotent via Session.joined; every participant with content answers, and each
                // answer merges, so nothing is lost.
                session.joined = true;
                send(account, session, sxe(session, "connect"), null);
            } else if (session.noticeUuid == null) {
                session.noticeCounterpart = from;
                postWhiteboardNotice(account, session);
            }
        }
        final Element child = firstChild(sxe);
        if (child == null) {
            return true;
        }
        switch (child.getName()) {
            case "connect":
                onConnect(session, from.toString());
                break;
            case "state-offer":
                onStateOffer(session, from.toString());
                break;
            case "accept-state":
                onAcceptState(session, from.toString());
                break;
            case "refuse-state":
                session.offered = false;
                break;
            case "state":
                applyState(session, child);
                break;
            default:
                break;
        }
        return true;
    }

    /** True when the groupchat resource belongs to our own MUC occupant. */
    private boolean isSelfReflection(final Account account, final Jid from) {
        if (from == null || from.isBareJid()) {
            return false;
        }
        final Conversation conversation = service.find(account, from.asBareJid());
        if (conversation == null || conversation.getMode() != Conversation.MODE_MULTI) {
            return false;
        }
        final MucOptions.User self = conversation.getMucOptions().getSelf();
        return self != null
                && from.getResource() != null
                && from.getResource().equals(self.getNick());
    }

    private void onConnect(final Session session, final String from) {
        if (session.muc) {
            // A room board has no host: the room relays every groupchat payload and snapshots are
            // merged on arrival. Answer a join request with our current document (only if we drew
            // something) so a participant who opens the board late catches up; everyone with content
            // answers, and each answer merges, so no one's drawing is lost.
            if (!shapes(session.sid).isEmpty()) {
                sendState(session, null);
            }
            return;
        }
        if (!session.host) {
            // A participant connected (or we just discovered the host): announce ourselves so the
            // host sends us the current document. Idempotent via Session.joined.
            joinSession(session);
            return;
        }
        session.offered = true;
        final Element el = sxe(session, "state-offer");
        el.findChild("state-offer", NS_SXE).addChild("description", NS_WHITEBOARD);
        send(session.account, session, el, from);
    }

    private void onStateOffer(final Session session, final String from) {
        if (session.accepted) {
            send(session.account, session, sxe(session, "refuse-state"), from);
            return;
        }
        session.accepted = true;
        session.ready = true;
        send(session.account, session, sxe(session, "accept-state"), from);
        notifyUpdate(session.sid);
    }

    private void onAcceptState(final Session session, final String from) {
        if (!session.host) {
            return;
        }
        session.ready = true;
        sendState(session, from);
        notifyUpdate(session.sid);
    }

    private void applyState(final Session session, final Element state) {
        final SxeDocument doc = session.doc;
        // A snapshot is merged, not applied destructively: every document shares one root rid and
        // applyNew merges records, so a remote snapshot (e.g. a room board's current drawing) never
        // wipes what this client already drew. clearShapes() must not run here.
        for (final Element child : state.getChildren()) {
            switch (child.getName()) {
                case "new":
                    doc.applyNew(child);
                    break;
                case "set":
                    doc.applySet(child);
                    break;
                case "remove":
                    doc.applyRemove(child);
                    break;
                default:
                    break;
            }
        }
        if (state.findChild("document-end", NS_SXE) != null) {
            session.ready = true;
        }
        notifyUpdate(session.sid);
    }

    // ── Sending ───────────────────────────────────────────────────────────

    /** True when the message carries no body or only the legacy invisible MUC fallback body. */
    private static boolean isWhiteboardBody(final MessagePacket packet) {
        final eu.siacs.conversations.xml.LocalizedContent body = packet.getBody();
        if (body == null || body.content == null) {
            return true;
        }
        return LEGACY_SXE_FALLBACK_BODY.equals(body.content.trim());
    }

    private static Element firstChild(final Element parent) {
        for (final Element child : parent.getChildren()) {
            if (child.getName() != null) {
                return child;
            }
        }
        return null;
    }

    private Element sxeElement(final Session session) {
        final Element el = new Element("sxe", NS_SXE);
        el.setAttribute("session", session.sid);
        el.setAttribute("id", SxeDocument.newRid());
        return el;
    }

    private Element sxe(final Session session, final String kind) {
        final Element el = sxeElement(session);
        el.addChild(kind, NS_SXE);
        return el;
    }

    private void send(final Account account, final Session session, final Element sxe, @Nullable final String to) {
        send(account, session, sxe, to, true);
    }

    /**
     * @param requestAck whether the connection should ask for a stream-management ack right after
     *     this stanza. Board <em>edits</em> pass false: a stroke is sent continuously, and an ack
     *     request per stroke produced a constant {@code <r/>}/{@code <a/>} flood while drawing. They
     *     stay reliable through the periodic keep-alive ack (and the stanza queue).
     */
    private void send(
            final Account account,
            final Session session,
            final Element sxe,
            @Nullable final String to,
            final boolean requestAck) {
        if (account == null) {
            return;
        }
        final MessagePacket packet = new MessagePacket();
        packet.setType(session.muc ? MessagePacket.TYPE_GROUPCHAT : MessagePacket.TYPE_CHAT);
        if (session.muc) {
            packet.setTo(session.peer);
        } else {
            final String target = to != null
                    ? to
                    : (session.fullPeer.isEmpty() ? session.peer.toString() : session.fullPeer);
            packet.setTo(Jid.of(target));
        }
        packet.setId(SxeDocument.newRid());
        packet.addChild(sxe);
        packet.setRequestAck(requestAck);
        // Send no <body> at all (the XEP examples carry none, and our own chat-state messages are
        // bodyless groupchat too). A MUC relays a bodyless groupchat but — unlike one with a body —
        // does not write it to the room history, so a stroke never shows up as an empty message in
        // other clients or in our own history after a reload. An invisible body (U+2063), used by an
        // earlier build, was archived and rendered as exactly such an empty message.
        //
        // A board payload is signalling, not a chat line: mark it no-store so an archived MAM copy
        // does not replay every stroke either.
        packet.addChild("no-store", "urn:xmpp:hints");
        service.sendMessagePacket(account, packet);
    }

    private void sendState(final Session session, final String to) {
        final Element el = sxeElement(session);
        final Element state = el.addChild("state", NS_SXE);
        final Element begin = state.addChild("document-begin", NS_SXE);
        begin.setAttribute("prolog", "data:text/xml,%3C%3Fxml%20version%3D%271.0%27%3F%3E");
        for (final Element newEl : session.doc.allNewElements()) {
            state.addChild(newEl);
        }
        final Element end = state.addChild("document-end", NS_SXE);
        end.setAttribute("last-sender", "");
        end.setAttribute("last-id", "");
        send(session.account, session, el, to);
    }

    private void sendEdits(
            final Session session,
            @Nullable final List<Element> news,
            @Nullable final List<Element> sets,
            @Nullable final List<Element> removes) {
        if (session.account == null) {
            return;
        }
        if ((news == null || news.isEmpty())
                && (sets == null || sets.isEmpty())
                && (removes == null || removes.isEmpty())) {
            return;
        }
        final Element el = sxeElement(session);
        final Element state = el.addChild("state", NS_SXE);
        appendAll(state, news);
        appendAll(state, sets);
        appendAll(state, removes);
        send(session.account, session, el, null, false);
    }

    private static void appendAll(final Element state, @Nullable final List<Element> items) {
        if (items == null) {
            return;
        }
        for (final Element item : items) {
            state.addChild(item);
        }
    }

    private static Element setElement(final SxeDocument doc, final String rid) {
        final SxeDocument.Record record = doc.getRecord(rid);
        final Element el = new Element("set", NS_SXE);
        el.setAttribute("target", rid);
        if (record != null) {
            el.setAttribute("version", Integer.toString(record.version));
            el.setAttribute("chdata", record.chdata);
        }
        return el;
    }

    private static Element removeElement(final String rid) {
        final Element el = new Element("remove", NS_SXE);
        el.setAttribute("target", rid);
        return el;
    }
}
