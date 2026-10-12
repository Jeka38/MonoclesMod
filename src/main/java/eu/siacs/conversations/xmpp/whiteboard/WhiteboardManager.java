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
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
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

    public boolean isWhiteboardJingle(final JinglePacket packet) {
        final Element content = packet.findChild("content", Namespace.JINGLE);
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
        final boolean host = muc || hostFor(account, barePeer);
        final Session session = new Session(sid, barePeer, muc, host, account);
        seedDocument(session.doc);
        sessions.put(sid, session);
        byPeer.put(key(account, barePeer), sid);
        if (muc) {
            advertise(session);
            session.ready = true;
            send(account, session, sxe(session, "connect"), null);
            notifyUpdate(sid);
        } else if (host) {
            // Only the deterministic host initiates; the other end waits for the invite so the two
            // sides cannot both act as host and overwrite each other's document.
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
        }
        notifyUpdate(session.sid);
    }

    private void advertise(final Session session) {
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
        packet.addChild(sxeElement(session));
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
            attrs.put(pair[0], pair[1]);
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
        final List<Element> sets = new ArrayList<>();
        for (final String[] pair : shape.attrs()) {
            final String attrRid = session.doc.setAttr(rid, pair[0], pair[1]);
            sets.add(setElement(session.doc, attrRid));
        }
        sendEdits(session, null, sets, null);
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
                    attrs.get("stroke"), attrs.get("stroke-width"), attrs.get("fill")));
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
        if (sid == null || action == null || from == null) {
            return;
        }
        final Jid peer = from.asBareJid();
        final boolean isInitiate = "session-initiate".equals(action);
        final boolean known = sessions.containsKey(sid) || byPeer.containsKey(key(account, peer));
        switch (action) {
            case "session-initiate": {
                if (sessions.containsKey(sid)) {
                    return;
                }
                // Same deterministic id as the initiator computed; adopt it so both ends share
                // one session and the live edits land in the session the UI is attached to.
                final Session session = new Session(sid, peer, false, hostFor(account, peer), account);
                session.fullPeer = from.toString();
                seedDocument(session.doc);
                sessions.put(sid, session);
                byPeer.put(key(account, peer), sid);
                // Synchronize by default: accept the invite right away and open the board on
                // this end too, so both sides show the same document without a manual "Share".
                joinSession(session);
                notifyUpdate(sid);
                if (service.checkListeners()) {
                    launchWhiteboard(account, peer);
                } else {
                    service.getNotificationService().notifyWhiteboardInvite(account, peer);
                }
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
                if (sessions.remove(sid) != null) {
                    byPeer.remove(key(account, peer));
                    notifyUpdate(sid);
                }
                break;
            }
            default:
                Log.d(Config.LOGTAG, "whiteboard: unhandled Jingle action " + action);
        }
    }

    private void sendJingle(final Account account, final Session session, final String action) {
        if (account == null) {
            return;
        }
        final JinglePacket packet = new JinglePacket(JinglePacket.Action.of(action), session.sid);
        packet.setTo(session.fullPeer.isEmpty() ? session.peer : Jid.of(session.fullPeer));
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

    /** Handle a bodyless XEP-0284 {@code <sxe/>} message; returns true if it was consumed. */
    public boolean handleSxeMessage(final Account account, final MessagePacket packet) {
        final Element sxe = packet.findChild("sxe", NS_SXE);
        if (sxe == null || packet.getBody() != null) {
            return false;
        }
        if (packet.fromAccount(account)) {
            // our own carbon of our own edit
            return true;
        }
        final String sid = sxe.getAttribute("session");
        final Jid from = packet.getFrom();
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
            // The deterministic host serves the snapshot, so derive the flag here too — otherwise a
            // peer that opened the board first and only sent <connect> would never receive state.
            final boolean host = isGroupChat || hostFor(account, from.asBareJid());
            session = new Session(sid, from.asBareJid(), isGroupChat, host, account);
            seedDocument(session.doc);
            sessions.put(sid, session);
            byPeer.put(key(account, from.asBareJid()), sid);
            // A MUC session has no Jingle invite (the room is the host and payloads are
            // groupchat), so surface it here as soon as it appears.
            if (isGroupChat) {
                if (service.checkListeners()) {
                    launchWhiteboard(account, from.asBareJid());
                } else {
                    service.getNotificationService()
                            .notifyWhiteboardInvite(account, from.asBareJid());
                }
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
        if (state.findChild("document-begin", NS_SXE) != null) {
            doc.clearShapes();
        }
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

    /** Opens the whiteboard UI for {@code peer} (from the service, so NEW_TASK is required). */
    private void launchWhiteboard(final Account account, final Jid peer) {
        final android.content.Intent intent =
                eu.siacs.conversations.ui.WhiteboardActivity.create(
                        service,
                        account.getJid().asBareJid().toEscapedString(),
                        peer.asBareJid().toEscapedString());
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        service.startActivity(intent);
    }

    // ── Sending ───────────────────────────────────────────────────────────
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
        send(session.account, session, el, null);
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
