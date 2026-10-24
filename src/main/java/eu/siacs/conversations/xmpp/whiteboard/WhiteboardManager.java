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

    private static final int VIEW_W = (int) WhiteboardView.DEFAULT_VIEW_W;
    private static final int VIEW_H = (int) WhiteboardView.DEFAULT_VIEW_H;

    public interface OnWhiteboardUpdate {
        /** A shared document changed (a session became ready or remote edits arrived). */
        void onWhiteboardUpdated(String sid);

        /**
         * A board was ended (locally by its creator, or by the peer/room). The activity should
         * detach and, if it is still showing that board, close itself.
         */
        void onWhiteboardEnded(String sid);
    }

    public static final class Session {
        public final String sid;
        public final Jid peer;
        public final boolean muc;
        public final Account account;
        /**
         * True when this side is the SXE **host**: the Jingle initiator for a 1:1 board (which owns
         * the snapshot duty and answers `<connect/>` with a state offer), or the room relay for a
         * MUC. Per XEP-0284 §4.1 the 1:1 host MUST be the initiator — this is *not* a JID comparison.
         */
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
        /**
         * The occupant (full JID) that created the room board. A room board has no central authority,
         * so the creator is whoever first opened it; that occupant owns the board's lifetime — when
         * they leave, the board is closed for everyone. Carried on the wire as {@code creator="…"} on
         * the {@code <sxe/>} so every occupant learns it.
         */
        public Jid creator = null;
        /**
         * True when this side sent the Jingle {@code session-initiate} (so we are the Jingle
         * *initiator*); false when the peer invited us. Needed to interpret the {@code senders}
         * attribute of the whiteboard content, which names roles, not JIDs.
         */
        public boolean initiator = false;
        /**
         * Negotiated {@code senders} of the whiteboard content: {@code "both"}, {@code "none"},
         * {@code "initiator"} or {@code "responder"}. Per the whiteboard profile this lists who may
         * edit; when it does not include our role the peer **ignores every edit we send** (Iris
         * gates {@code document->applyEdits} on {@code mayEdit(peerRole)}), so a session frozen with
         * {@code senders="none"} silently swallows all strokes. Psi starts a call that merely carries
         * a whiteboard this way. {@link #ensureEditable} negotiates it back to {@code both}.
         */
        public String senders = "both";
        /** True once we asked the peer to let us edit, so we do not re-ask on every stroke. */
        public boolean sendersRequested = false;
        /**
         * Name of the whiteboard Jingle {@code <content/>}. Iris looks a content up by
         * (name, creator), so a {@code content-modify} must echo the exact values of the offer; we
         * default to our own name but adopt whatever the peer offered.
         */
        public String contentName = "whiteboard";
        /** Creator of the whiteboard content: "initiator" (the normal case). */
        public String contentCreator = "initiator";

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

    private void notifyEnded(final String sid) {
        final List<OnWhiteboardUpdate> copy;
        synchronized (listeners) {
            copy = new ArrayList<>(listeners);
        }
        for (final OnWhiteboardUpdate listener : copy) {
            listener.onWhiteboardEnded(sid);
        }
    }

    /** The full JID of our own occupant in the room, or null when not joined yet. */
    @Nullable
    private Jid selfOccupant(final Account account, final Jid room) {
        final Conversation conversation = service.find(account, room.asBareJid());
        if (conversation == null || conversation.getMode() != Conversation.MODE_MULTI) {
            return null;
        }
        final MucOptions.User self = conversation.getMucOptions().getSelf();
        return self == null ? null : self.getFullJid();
    }

    /**
     * True when this device is the creator of the room board and therefore owns its lifetime (closing
     * the board when the creator leaves). A 1:1 board is always ours.
     */
    public boolean isOwner(final Session session) {
        if (session == null) {
            return false;
        }
        if (!session.muc) {
            return true;
        }
        return isSelfOccupant(session, session.creator);
    }

    /**
     * Records a candidate creator of a room board and keeps the **smallest** full occupant JID. A
     * room board has no central authority and the session id is derived from the room alone, so
     * several occupants can open the board and each would otherwise stamp itself as creator; taking
     * the minimum makes every device converge on the same creator, which is what the "creator leaves
     * closes the board" rule needs to be consistent across clients.
     */
    private void observeMucCreator(final Session session, @Nullable final Jid candidate) {
        if (!session.muc || candidate == null) {
            return;
        }
        if (session.creator != null && session.creator.compareTo(candidate) <= 0) {
            return;
        }
        session.creator = candidate;
    }

    /** True when {@code occupant} is our own occupant in the room of this session. */
    private boolean isSelfOccupant(final Session session, @Nullable final Jid occupant) {
        if (occupant == null) {
            return false;
        }
        final Jid self = selfOccupant(session.account, session.peer);
        return self != null && self.asBareJid().equals(occupant.asBareJid())
                && self.getResource() != null && self.getResource().equals(occupant.getResource());
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
     * A MUC occupant advertised a shared whiteboard in their presence ({@code <sxe session=…/>}).
     * Discover the room board from that (which also covers a member who joins the room after the
     * board started, since the MUC service replays the other occupants' presence to them), post the
     * <em>Open board</em> notice and ask for the current document once.
     */
    public void onMucPresenceSxe(final Account account, final Jid room, final Jid occupant, final String sid,
                                 @Nullable final Jid creator) {
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
            // The creator owns the board's lifetime: prefer the advertised creator, else the occupant
            // whose presence announced it (a brand-new board is announced by its creator). The
            // smallest occupant JID wins so all devices agree who that is.
            observeMucCreator(session, creator != null ? creator : occupant);
            session.noticeCounterpart = session.creator;
            postWhiteboardNotice(account, session);
        } else {
            observeMucCreator(session, creator != null ? creator : occupant);
        }
        if (!session.joined) {
            session.joined = true;
            send(account, session, sxe(session, "connect"), null);
        }
    }

    /**
     * Called when a MUC occupant leaves the room. If the occupant was the creator of the room board,
     * the board is closed for everyone (the creator owns its lifetime). Runs on every room presence
     * change so a creator who leaves ends the board even when we are not looking at it.
     */
    public void onMucOccupantUnavailable(final Account account, final Jid room, final Jid occupant) {
        if (account == null || room == null || occupant == null) {
            return;
        }
        for (final Session session : new ArrayList<>(sessions.values())) {
            if (!session.muc || !session.peer.asBareJid().equals(room.asBareJid())) {
                continue;
            }
            if (session.creator == null || !sameOccupant(session.creator, occupant)) {
                continue;
            }
            // The creator left: end the board for us (and broadcast a close so other occupants that
            // did not see the leave also drop it).
            endSession(session, true);
        }
    }

    private static boolean sameOccupant(final Jid a, final Jid b) {
        return a != null && b != null
                && a.asBareJid().equals(b.asBareJid())
                && a.getResource() != null && a.getResource().equals(b.getResource());
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
        // For a 1:1 board the INITIATOR is the host (XEP-0284 §4.1: "The host can be the initiator
        // of the session (and MUST be for a one-to-one session)"). It must NOT be decided by a JID
        // comparison: opening a board whose sid ranks us "smaller/larger" has nothing to do with who
        // sends session-initiate, and conflating the two made the initiator send an unsolicited
        // session-accept + <connect/> (so the peer showed no invitation at all). A room board has no
        // host (the room relays), so keep host=false there.
        final Session session = new Session(sid, barePeer, muc, !muc, account);
        session.locallyStarted = true;
        // We send the session-initiate, so we are the Jingle initiator (and the host of a 1:1 board).
        session.initiator = true;
        seedDocument(session.doc);
        sessions.put(sid, session);
        byPeer.put(key(account, barePeer), sid);
        if (muc) {
            // Room board: the room JID is the host and payloads travel as groupchat. This device
            // offers itself as a creator candidate (it is opening the board); a convergence step
            // (smallest occupant JID wins) makes every device agree who owns the board's lifetime.
            observeMucCreator(session, selfOccupant(account, barePeer));
            // Announce in presence + connect, then post the "Open board" notice into the room so
            // every member (including this device) sees the button and gets a notification per the
            // room settings.
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

    /**
     * Accepts an incoming invite and joins the shared document.
     *
     * <p>Crucially the two Jingle actions are gated on <em>different</em> roles:
     * <ul>
     *   <li>{@code session-accept} only ever comes from the party that RECEIVED a
     *       {@code session-initiate} ({@code !Session.initiator}). Sending it for a session we
     *       started (which happens when our JID is the larger one, i.e. {@code host == false})
     *       reaches the peer as an unsolicited accept with no invite, and Psi then shows no
     *       invitation at all.</li>
     *   <li>{@code <connect/>} is sent by the joiner — the party that is <em>not</em> the SXE host
     *       (for a 1:1 board: not the initiator, {@code Session.host}); the host waits for the
     *       joiner's connect and answers with the state offer.</li>
     * </ul>
     */
    public void joinSession(final Session session) {
        if (session == null || session.joined) {
            return;
        }
        session.joined = true;
        if (!session.muc && !session.initiator) {
            sendJingle(session.account, session, "session-accept");
            // Psi offers the whiteboard of a call with senders="none"; ask for edit permission right
            // away so the board is not silently read-only on our side.
            ensureEditable(session);
        }
        if (!session.host || session.muc) {
            send(session.account, session, sxe(session, "connect"), null);
        }
    }

    public void endSession(final Session session) {
        endSession(session, true);
    }

    /**
     * Ends a session.
     *
     * @param broadcast true when we are the party ending the board and should tell the other side
     *     (1:1: {@code session-terminate}; MUC: an explicit {@code <close/>}, since a room board has no
     *     Jingle session and the other occupants would otherwise keep it alive). Pass false when the
     *     {@code <close/>}/{@code session-terminate} already arrived from the peer, so we do not echo it.
     */
    public void endSession(final Session session, final boolean broadcast) {
        if (session == null) {
            return;
        }
        sessions.remove(session.sid);
        byPeer.remove(key(session.account, session.peer));
        if (broadcast) {
            if (!session.muc) {
                try {
                    sendJingle(session.account, session, "session-terminate");
                } catch (final RuntimeException e) {
                    Log.d(Config.LOGTAG, "whiteboard: could not terminate session", e);
                }
            } else {
                // A room board has no Jingle session: broadcast an explicit <close/> so every
                // occupant learns the board ended (a client extension of XEP-0284, and what the
                // reference implementation does).
                try {
                    send(session.account, session, sxe(session, "close"), null);
                } catch (final RuntimeException e) {
                    Log.d(Config.LOGTAG, "whiteboard: could not close room board", e);
                }
            }
        }
        if (session.muc) {
            // Clear the room presence advertisement. The MUC service stores the occupants' last
            // presence and replays it to anyone who joins later, so a stale <sxe/> would make the
            // "Open board" button reappear for every re-entry of the room (and for a late joiner)
            // even though the board is gone.
            clearAdvertisement(session);
        }
        // The board is gone: drop the in-chat "Open board" notice so it does not linger.
        removeWhiteboardNotice(session);
        notifyUpdate(session.sid);
        notifyEnded(session.sid);
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
            if (record == null || !"element".equals(record.type)) {
                continue;
            }
            final Map<String, String> attrs = new LinkedHashMap<>();
            for (final String attrId : doc.children(child)) {
                final SxeDocument.Record attr = doc.getRecord(attrId);
                if (attr != null && "attr".equals(attr.type)) {
                    attrs.put(attr.name, attr.chdata);
                }
            }
            // Only drawable SVG elements. The peer may use any of the SVG basic shapes, not just
            // <path/>: Psi's shape tools emit <polygon>/<polyline>/<rect>/<circle>/<ellipse>/<line>,
            // and an earlier build discarded those records entirely (it matched the element name
            // against "path"), so Psi's drawing never appeared on our board. svgPathData() converts
            // each of them to the "d" the canvas already parses.
            final String d = svgPathData(record.name, attrs);
            if (d == null) {
                continue;
            }
            out.add(WhiteboardShape.fromRemote(
                    child, attrs.get("id"), d,
                    attrs.get("stroke"), attrs.get("stroke-width"), attrs.get("fill"),
                    attrs.get("transform"), record.name));
        }
        return out;
    }

    /**
     * Converts a drawable SVG element into path data ({@code d}). Returns null for metadata and for
     * elements we cannot draw. {@code <path/>} keeps its own {@code d}; the basic shapes are mapped
     * with the standard SVG equivalent (as the whiteboard profile's schema allows them), so a shape
     * produced by any client renders here.
     */
    @Nullable
    private static String svgPathData(final String name, final Map<String, String> attrs) {
        if (name == null) {
            return null;
        }
        switch (name) {
            case "path":
                return attrs.get("d");
            case "polygon":
            case "polyline": {
                final String points = attrs.get("points");
                if (points == null || points.trim().isEmpty()) {
                    return null;
                }
                // The points list is "x,y x,y …"; build explicit L commands (the same form our own
                // shapes produce) instead of relying on SVG's implicit-lineto-after-moveto parsing.
                final String[] pairs = points.trim().split("\\s+");
                final StringBuilder sb = new StringBuilder();
                for (int i = 0; i < pairs.length; i++) {
                    final String pair = pairs[i].trim();
                    if (pair.isEmpty()) {
                        continue;
                    }
                    sb.append(sb.length() == 0 ? "M " : " L ").append(pair.replace(',', ' '));
                }
                if (sb.length() == 0) {
                    return null;
                }
                if ("polygon".equals(name)) {
                    sb.append(" Z");
                }
                return sb.toString();
            }
            case "line": {
                final String x1 = attrs.get("x1"), y1 = attrs.get("y1");
                final String x2 = attrs.get("x2"), y2 = attrs.get("y2");
                if (x1 == null || y1 == null || x2 == null || y2 == null) {
                    return null;
                }
                return "M " + x1 + " " + y1 + " L " + x2 + " " + y2;
            }
            case "rect": {
                final float x = parseFloat(attrs.get("x"), 0f);
                final float y = parseFloat(attrs.get("y"), 0f);
                final float w = parseFloat(attrs.get("width"), 0f);
                final float h = parseFloat(attrs.get("height"), 0f);
                if (w == 0f && h == 0f) {
                    return null;
                }
                return "M " + x + " " + y + " L " + (x + w) + " " + y + " L " + (x + w) + " "
                        + (y + h) + " L " + x + " " + (y + h) + " Z";
            }
            case "circle": {
                final float cx = parseFloat(attrs.get("cx"), 0f);
                final float cy = parseFloat(attrs.get("cy"), 0f);
                final float r = parseFloat(attrs.get("r"), 0f);
                if (r == 0f) {
                    return null;
                }
                return "M " + (cx - r) + " " + cy + " A " + r + " " + r + " 0 1 0 " + (cx + r) + " "
                        + cy + " A " + r + " " + r + " 0 1 0 " + (cx - r) + " " + cy + " Z";
            }
            case "ellipse": {
                final float cx = parseFloat(attrs.get("cx"), 0f);
                final float cy = parseFloat(attrs.get("cy"), 0f);
                final float rx = Math.abs(parseFloat(attrs.get("rx"), 0f));
                final float ry = Math.abs(parseFloat(attrs.get("ry"), 0f));
                if (rx == 0f && ry == 0f) {
                    return null;
                }
                return "M " + (cx - rx) + " " + cy + " A " + rx + " " + ry + " 0 1 0 " + (cx + rx) + " "
                        + cy + " A " + rx + " " + ry + " 0 1 0 " + (cx - rx) + " " + cy + " Z";
            }
            default:
                // <g>, <image>, <title>, <desc>, <defs>, … are not standalone drawable paths here.
                return null;
        }
    }

    private static float parseFloat(@Nullable final String value, final float fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }

    public boolean isReady(final String sid) {
        final Session session = sessions.get(sid);
        return session != null && session.ready;
    }

    /**
     * The negotiated document size (the shared SVG {@code viewBox}, e.g. "0 0 600 400"), so the
     * canvas can draw into the same coordinate space as the peer. Falls back to the profile default
     * when the session is unknown or carries no valid {@code viewBox}.
     */
    public float[] documentSize(final String sid) {
        final float[] fallback = {
            WhiteboardView.DEFAULT_VIEW_W, WhiteboardView.DEFAULT_VIEW_H
        };
        final Session session = sid == null ? null : sessions.get(sid);
        if (session == null) {
            return fallback;
        }
        final String viewBox = session.doc.getAttr(session.doc.getRootRid(), "viewBox");
        if (viewBox == null) {
            return fallback;
        }
        // SVG viewBox: "min-x min-y width height" — separated by whitespace and/or a comma.
        final String[] parts = viewBox.trim().split("[\\s,]+");
        if (parts.length != 4) {
            return fallback;
        }
        try {
            final float width = Float.parseFloat(parts[2]);
            final float height = Float.parseFloat(parts[3]);
            if (width > 0f && height > 0f) {
                return new float[] {width, height};
            }
        } catch (final NumberFormatException ignored) {
        }
        return fallback;
    }

    // ── XEP-0353 Jingle Message Initiation proposals ──────────────────────

    /**
     * Handles an incoming XEP-0353 Jingle Message Initiation element that concerns the whiteboard —
     * the optional "Full profile" way to invite from inside an existing chat (WHITEBOARD_PROFILE
     * §9.3 / extended spec §JMI). The peer sends a bodyless
     * {@code <propose><description xmlns='urn:xmpp:jingle:apps:svg-whiteboard:0'/></propose>} and
     * only starts the real Jingle session once it receives a {@code <proceed/>}. A later
     * {@code <retract/>}/{@code <reject/>} withdraws that proposal.
     *
     * <p>Such a message carries no body, so it never reaches the Jingle-message routing in
     * {@code MessageParser} (which lives inside the "has body" branch). Without handling it here the
     * "Open board" button is never posted for such an invite.
     *
     * @return true when the element concerned the whiteboard and was consumed.
     */
    public boolean handleJingleMessageInitiation(
            final Account account, final Jid from, final Element element) {
        if (account == null || from == null || element == null
                || !Namespace.JINGLE_MESSAGE.equals(element.getNamespace())) {
            return false;
        }
        final String name = element.getName();
        if (name == null) {
            return false;
        }
        switch (name) {
            case "propose":
                return handleProposal(account, from, element);
            case "retract":
            case "reject":
                return handleProposalWithdrawal(account, from, element);
            default:
                return false;
        }
    }

    private boolean handleProposal(final Account account, final Jid from, final Element propose) {
        final Element description = propose.findChild("description");
        if (description == null || !NS_WHITEBOARD.equals(description.getNamespace())) {
            return false;
        }
        final String sid = propose.getAttribute("id");
        if (sid == null) {
            return false;
        }
        MujiLog.log(service.getFilesDir(), "WB proposal sid=" + sid + " from=" + from);
        final Jid peer = from.asBareJid();
        Session session = sessions.get(sid);
        if (session == null) {
            // The proposal id becomes the Jingle `sid` of the ensuing session-initiate (XEP-0353),
            // so adopting it here means the real invite lands in this very session. The proposer is
            // the initiator and therefore the host of a 1:1 board.
            session = new Session(sid, peer, false, false, account);
            session.fullPeer = from.toString();
            seedDocument(session.doc);
            sessions.put(sid, session);
            byPeer.put(key(account, peer), sid);
        }
        // Invitations surface as a normal in-chat message with an "Open board" button (the screen is
        // never force-opened), then the proposer is told to proceed so it sends the session-initiate.
        if (session.noticeUuid == null) {
            session.noticeCounterpart = from;
            postWhiteboardNotice(account, session);
        }
        proceedProposal(account, from, sid);
        return true;
    }

    /**
     * A JMI {@code <retract/>}/{@code <reject/>} for a board we only know from a proposal: drop the
     * placeholder session and its "Open board" notice, since the board will never be started.
     */
    private boolean handleProposalWithdrawal(
            final Account account, final Jid from, final Element element) {
        final Element description = element.findChild("description");
        if (description == null || !NS_WHITEBOARD.equals(description.getNamespace())) {
            return false;
        }
        final String sid = element.getAttribute("id");
        if (sid == null) {
            return false;
        }
        final Session session = sessions.remove(sid);
        if (session == null) {
            return false;
        }
        byPeer.remove(key(account, session.peer));
        removeWhiteboardNotice(session);
        notifyUpdate(sid);
        notifyEnded(sid);
        return true;
    }

    /** Answers a whiteboard JMI {@code <propose/>} so the proposer continues with session-initiate. */
    private void proceedProposal(final Account account, final Jid target, final String sid) {
        final MessagePacket packet = new MessagePacket();
        packet.setType(MessagePacket.TYPE_CHAT);
        packet.setTo(target);
        packet.setId(SxeDocument.newRid());
        packet.addChild("proceed", Namespace.JINGLE_MESSAGE).setAttribute("id", sid);
        packet.addChild("store", "urn:xmpp:hints");
        service.sendMessagePacket(account, packet);
    }

    // ── Jingle dispatch ───────────────────────────────────────────────────

    /** Reads the {@code senders} attribute of the whiteboard {@code <content/>} ("both" default). */
    private static String offeredSenders(final JinglePacket packet) {
        final Element content = packet.getJingleChild("content");
        final String senders = content == null ? null : content.getAttribute("senders");
        return senders == null || senders.isEmpty() ? "both" : senders;
    }

    /** Adopts the name/creator of the offered content so a later content-modify matches it. */
    private static void adoptContentIdentity(final Session session, final JinglePacket packet) {
        final Element content = packet.getJingleChild("content");
        if (content == null) {
            return;
        }
        final String name = content.getAttribute("name");
        if (name != null && !name.isEmpty()) {
            session.contentName = name;
        }
        final String creator = content.getAttribute("creator");
        if (creator != null && !creator.isEmpty()) {
            session.contentCreator = creator;
        }
    }

    /**
     * Whether our role is allowed to edit under the negotiated {@code senders}. The attribute names
     * roles, not JIDs: for a session we initiated our role is "initiator", otherwise "responder".
     * A value that does not include our role (notably {@code none}, which Psi uses for the
     * whiteboard of a call that has not switched it on) means the peer ignores all our edits.
     */
    private static boolean mayEdit(final Session session) {
        final String senders = session.senders == null ? "both" : session.senders;
        if ("both".equals(senders)) {
            return true;
        }
        if ("none".equals(senders)) {
            return false;
        }
        return session.initiator ? "initiator".equals(senders) : "responder".equals(senders);
    }

    /**
     * Per the whiteboard profile a document is *frozen* with {@code senders="none"} (the peer both
     * stops editing and ignores edits) — that is how Psi starts the whiteboard of a call. The peer
     * switches it on by widening {@code senders}, so we ask once with {@code content-modify};
     * without it the board looks connected yet silently swallows every stroke. Only {@code none} is
     * requested: a role-specific value ({@code initiator}/{@code responder}) is a deliberate
     * "presenter" restriction and must be respected.
     */
    private void ensureEditable(final Session session) {
        if (session == null || session.muc || session.sendersRequested) {
            return;
        }
        if (!"none".equals(session.senders)) {
            return;
        }
        session.sendersRequested = true;
        MujiLog.log(service.getFilesDir(), "WB request edit permission sid=" + session.sid);
        // XEP-0284/content-modify: a childless <content/> that only carries the new senders value.
        // creator/name MUST match the offered content (Iris looks the content up by those two).
        final JinglePacket packet =
                new JinglePacket(JinglePacket.Action.CONTENT_MODIFY, session.sid);
        packet.setTo(jingleTarget(session.account, session));
        final Element content = new Element("content", Namespace.JINGLE);
        content.setAttribute("creator", session.contentCreator);
        content.setAttribute("name", session.contentName);
        content.setAttribute("senders", "both");
        packet.addJingleChild(content);
        service.sendIqPacket(session.account, packet, null);
        // Optimistically allow local editing so the user is not blocked while the peer processes it
        // (a refusal is simply dropped by the peer's own gate). Do NOT notify listeners here: this
        // runs from within sendEdits (i.e. while a stroke is being added) and a listener would
        // reload the canvas mid-stroke.
        session.senders = "both";
    }

    public void dispatchJingle(final Account account, final JinglePacket packet) {
        final String sid = packet.getSessionId();
        final String action = packet.getAction() == null ? null : packet.getAction().toString();
        final Jid from = packet.getFrom();
        MujiLog.log(service.getFilesDir(), "WB dispatch action=" + action + " sid=" + sid + " from=" + from);
        if (sid == null || action == null || from == null) {
            return;
        }
        final Jid peer = from.asBareJid();
        switch (action) {
            case "session-initiate": {
                final Session existing = sessions.get(sid);
                if (existing != null) {
                    // A duplicate invite for a session we already have: either both sides opened the
                    // board and computed the same deterministic sid, or — the common case — a XEP-0353
                    // <propose/> pre-created this session under the very sid the ensuing
                    // session-initiate reuses. In the latter case this is the FIRST time we see the
                    // offer's content, so the `senders` (and content identity) MUST still be read: a
                    // board frozen with senders="none" (Psi's whiteboard-of-a-call) would otherwise
                    // keep the default "both" and silently swallow every stroke.
                    existing.initiator = false;
                    existing.senders = offeredSenders(packet);
                    adoptContentIdentity(existing, packet);
                    // Just accept it again so the peer's `session-accept` expectation is met, but do
                    // not create a second session.
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
                    // A <propose/> already joined the session before the offer arrived, so asks for
                    // edit permission now that the offered `senders` is finally known. Idempotent.
                    ensureEditable(existing);
                    break;
                }
                // Same deterministic id as the initiator computed; adopt it so both ends share
                // one session and the live edits land in the session the UI is attached to.
                final Session session = new Session(sid, peer, false, false, account);
                session.fullPeer = from.toString();
                // We are the responder here: the peer sent the session-initiate, so the peer is the
                // initiator and the host. Record the offered `senders` so ensureEditable() can tell
                // whether we may actually edit (Psi offers the whiteboard of a call with
                // senders="none", which would otherwise swallow every stroke).
                session.initiator = false;
                session.senders = offeredSenders(packet);
                adoptContentIdentity(session, packet);
                seedDocument(session.doc);
                // A JMI proposal (XEP-0353) may have registered a placeholder under its own propose
                // id; if the real invite arrives with a different sid, drop the orphan so the peer
                // never ends up with two sessions (and two "Open board" notices).
                final String placeholderSid = byPeer.get(key(account, peer));
                if (placeholderSid != null && !placeholderSid.equals(sid)) {
                    final Session placeholder = sessions.remove(placeholderSid);
                    if (placeholder != null) {
                        removeWhiteboardNotice(placeholder);
                    }
                }
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
                    // The answer must repeat the offered host; also refresh `senders` in case the
                    // responder narrowed or widened the editing permission.
                    session.senders = offeredSenders(packet);
                    ensureEditable(session);
                }
                break;
            }
            case "content-modify": {
                final Session session = sessions.get(sid);
                if (session != null) {
                    session.senders = offeredSenders(packet);
                    // Allow a later re-request: if the peer freezes the board again (senders="none")
                    // and we draw, ensureEditable() must be able to ask once more.
                    session.sendersRequested = false;
                    notifyUpdate(sid);
                }
                break;
            }
            case "session-terminate": {
                sessions.remove(sid);
                byPeer.remove(key(account, peer));
                // The peer ended the board: drop our "Open board" notice (even when we no longer hold
                // the session) and surface the end to the UI. Do not echo a terminate back.
                removeWhiteboardNotices(account, peer, false);
                notifyUpdate(sid);
                notifyEnded(sid);
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
        if (session == null) {
            return;
        }
        session.noticeUuid = null;
        removeWhiteboardNotices(session.account, session.peer.asBareJid(), session.muc);
    }

    /**
     * Deletes every {@code TYPE_WHITEBOARD} notice in the conversation for {@code peer}. The notice
     * is a purely local message (each device posts its own), so it must be cleaned up on the device
     * that shows it — even when there is no in-memory session any more (app restart, or the board
     * was ended while we were not looking). All notices are removed, not just a remembered uuid, so a
     * stale one from an earlier board cannot survive.
     */
    private void removeWhiteboardNotices(final Account account, final Jid peer, final boolean muc) {
        final Conversation conversation = service.find(account, peer.asBareJid());
        if (conversation == null) {
            return;
        }
        final java.util.List<Message> notices = new java.util.ArrayList<>();
        conversation.findMessages(m -> m.getType() == Message.TYPE_WHITEBOARD, notices::add);
        if (notices.isEmpty()) {
            return;
        }
        for (final Message message : notices) {
            conversation.deleteMessage(message);
            service.databaseBackend.deleteMessageInConversation(message);
        }
        service.databaseBackend.updateConversation(conversation);
        service.updateConversationUi();
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
        final Jid bare = session.peer.asBareJid();
        if (!session.fullPeer.isEmpty()) {
            return Jid.of(session.fullPeer);
        }
        final Contact contact = account.getRoster().getContact(bare);
        if (contact != null) {
            final Jid resource = contact.resourceWhichSupport(NS_WHITEBOARD);
            if (resource != null && resource.isFullJid()) {
                return resource;
            }
            final String mostAvailable = mostAvailableResource(contact);
            if (mostAvailable != null) {
                return bare.withResource(mostAvailable);
            }
        }
        // No known resource: fall back to the first resource of the presence set (a Jingle IQ to a
        // *bare* JID is rejected by the server with service-unavailable, so the invite would vanish).
        final String[] resources = contact == null ? new String[0] : contact.getPresences().toResourceArray();
        if (resources.length > 0 && !resources[0].isEmpty()) {
            return bare.withResource(resources[0]);
        }
        MujiLog.log(service.getFilesDir(), "WB no full JID for " + bare + " (presence missing)");
        return session.peer;
    }

    /**
     * The peer's highest-priority resource name, or null. Do NOT use
     * {@link Contact#getMostAvailableResource()}: it wraps the resource in parentheses
     * ({@code "(" + resource + ")"}) for display, so passing it to {@link Jid#withResource} built an
     * invalid JID (`` user@host/(pc) ``) and the Jingle IQ was never routable — which is why an
     * invite sent from Monocles never reached Psi. The presence map's keys are the plain resources.
     */
    @Nullable
    private static String mostAvailableResource(final Contact contact) {
        final Map<String, Presence> presences = contact.getPresences().getPresencesMap();
        String bestResource = null;
        Presence best = null;
        for (final Map.Entry<String, Presence> entry : presences.entrySet()) {
            final String resource = entry.getKey();
            if (resource == null || resource.isEmpty()) {
                continue;
            }
            // Same ranking upstream uses for its display helper: the "smallest" Presence wins.
            if (best == null || entry.getValue().compareTo(best) < 0) {
                best = entry.getValue();
                bestResource = resource;
            }
        }
        return bestResource;
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
            // XEP-0284 §4.1/§4.2: the <host/> names the entity that relays the SXE payloads. For a
            // 1:1 session that is the *initiator*, and the answer MUST repeat the offered host.
            // Sending our own JID in the session-accept (as an earlier build did) is wrong.
            if (session.muc) {
                host.setContent(session.peer.toEscapedString());
            } else if ("session-accept".equals(action)) {
                // we are the responder: the initiator (our peer) is the host
                host.setContent(session.peer.toEscapedString());
            } else {
                // we are the initiator
                host.setContent(account.getJid().asBareJid().toEscapedString());
            }
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
        final Element sxeChild = firstChild(sxe);
        if (sxeChild != null && "close".equals(sxeChild.getName())) {
            // The board was ended (by the creator/owner or, for 1:1, the peer). Drop any local copy,
            // its notice and tell the UI the board is gone — even when we no longer hold the session
            // (app restart, re-entry). A room board has no Jingle session-terminate, so this <close/>
            // is the client extension that carries it.
            final Session local = sessions.remove(sid);
            final Jid room = local != null ? local.peer : from.asBareJid();
            final Account noticeAccount = local != null ? local.account : account;
            byPeer.remove(key(noticeAccount, room));
            removeWhiteboardNotices(noticeAccount, room, isGroupChat);
            notifyUpdate(sid);
            notifyEnded(sid);
            return true;
        }
        Session session = sessions.get(sid);
        if (session == null) {
            // We discovered the board from the peer's <sxe/> traffic, so the PEER is the initiator
            // and the host of a 1:1 board; a room board has no Jingle session (the room is the
            // relay). Do not derive this from a JID comparison.
            session = new Session(sid, from.asBareJid(), isGroupChat, false, account);
            seedDocument(session.doc);
            sessions.put(sid, session);
            byPeer.put(key(account, from.asBareJid()), sid);
            // A room board has no Jingle invite, so as soon as another participant starts one we
            // post the same in-chat notice (with the button) and notify per the room settings. A 1:1
            // board is normally announced by its Jingle invite; if that was missed we still surface
            // the button when the peer's SXE traffic reveals the board.
            if (isGroupChat) {
                // The creator owns the board's lifetime; it is advertised on the <sxe/> so a late
                // joiner learns who to watch for leaving (smallest occupant JID wins).
                observeMucCreator(session, sxe.getAttributeAsJid("creator"));
                session.noticeCounterpart = session.creator != null ? session.creator : from;
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
        } else if (isGroupChat) {
            observeMucCreator(session, sxe.getAttributeAsJid("creator"));
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
        // Carry the creator so every occupant can (a) show the invitation from the right person and
        // (b) close the board when that creator leaves the room.
        if (session.muc && session.creator != null) {
            el.setAttribute("creator", session.creator.toEscapedString());
        }
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
        // The peer ignores edits from a role the negotiated `senders` excludes (Iris gates the
        // application of incoming edits on mayEdit(peerRole)); Psi starts the whiteboard of a call
        // frozen with senders="none". Ask once for permission (content-modify) — it is an IQ, so the
        // peer applies it before this message on the same stream — and keep the stroke when the
        // request flips us to "both". A role-specific value (presenter mode) is respected and the
        // edit is dropped.
        if (!session.muc && !mayEdit(session)) {
            ensureEditable(session);
            if (!mayEdit(session)) {
                return;
            }
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
