package eu.siacs.conversations.xmpp.jingle;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jingle.stanzas.Muji;
import eu.siacs.conversations.xmpp.jingle.stanzas.MujiContent;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

/**
 * State for a single XEP-0272 Multiparty Jingle (Muji) conference inside a MUC room.
 *
 * <p>A conference tracks the local media the user wants to provide and one Jingle RTP session per
 * other participant (full mesh). In this first stage the conference only implements the signalling
 * side of the protocol: advertising contents in MUC presence, initiating sessions with participants
 * that already advertise contents and accepting sessions initiated by later joiners. There is no
 * dedicated multi party user interface yet.
 */
public class MujiConference {

    /** Time we let other preparing participants finish before we advertise our own contents. */
    private static final long PREPARATION_DELAY = 1500L;

    private final XmppConnectionService xmppConnectionService;
    private final Account account;
    private final Conversation conversation;
    private final Jid room;
    private final Set<Media> media = new HashSet<>();
    private final Map<String, AbstractJingleConnection.Id> sessions = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable private WebRTCResources webRTCResources;
    private boolean preparing = false;
    private boolean active = false;

    MujiConference(
            final MujiConferenceManager manager,
            final Conversation conversation,
            final Set<Media> media) {
        this.xmppConnectionService = manager.getXmppConnectionService();
        this.account = conversation.getAccount();
        this.conversation = conversation;
        this.room = conversation.getJid().asBareJid();
        this.media.addAll(media);
    }

    public Jid getRoom() {
        return room;
    }

    public Account getAccount() {
        return account;
    }

    public Set<Media> getMedia() {
        return media;
    }

    public boolean isActive() {
        return active;
    }

    public synchronized void join() {
        preparing = true;
        active = true;
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": joining Muji conference " + room);
        try {
            this.webRTCResources =
                    WebRTCResources.create(xmppConnectionService.getApplicationContext());
        } catch (final WebRTCWrapper.InitializationException e) {
            Log.e(Config.LOGTAG, "unable to create shared WebRTC resources for Muji conference", e);
            this.active = false;
            this.preparing = false;
            return;
        }
        sendPresence();
        handler.postDelayed(this::finishPreparation, PREPARATION_DELAY);
    }

    synchronized void updateMedia(final Set<Media> media) {
        this.media.clear();
        this.media.addAll(media);
        if (active) {
            sendPresence();
        }
    }

    private synchronized void finishPreparation() {
        if (!active) {
            return;
        }
        preparing = false;
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": finished Muji preparation for " + room);
        sendPresence();
        initiateSessions();
    }

    public synchronized void leave() {
        active = false;
        preparing = false;
        handler.removeCallbacksAndMessages(null);
        terminateSessions();
        sendPresence();
        final WebRTCResources resources = this.webRTCResources;
        this.webRTCResources = null;
        if (resources != null) {
            // the conference reference; per-peer references are released as their connections close
            resources.release();
        }
    }

    @Nullable
    WebRTCResources getWebRTCResources() {
        return webRTCResources;
    }

    /** Snapshot of the participants we currently have a mesh session with, for the conference UI. */
    public synchronized List<Participant> getParticipants() {
        final List<Participant> participants = new ArrayList<>();
        for (final AbstractJingleConnection.Id id : sessions.values()) {
            final MucOptions.User user =
                    conversation.getMucOptions().findUserByRealJid(id.with.asBareJid());
            final String nick = user == null ? id.with.getResource() : user.getNick();
            participants.add(new Participant(id, id.with, nick, sessionState(id)));
        }
        return participants;
    }

    @Nullable
    public JingleRtpConnection getConnection(final Participant participant) {
        final WeakReference<JingleRtpConnection> reference =
                xmppConnectionService
                        .getJingleConnectionManager()
                        .findJingleRtpConnection(
                                participant.id.account,
                                participant.id.with,
                                participant.id.sessionId);
        return reference == null ? null : reference.get();
    }

    @Nullable
    public org.webrtc.EglBase.Context getEglBaseContext() {
        final WebRTCResources resources = this.webRTCResources;
        return resources == null ? null : resources.eglBase.getEglBaseContext();
    }

    @Nullable
    private RtpEndUserState sessionState(final AbstractJingleConnection.Id id) {
        final WeakReference<JingleRtpConnection> reference =
                xmppConnectionService
                        .getJingleConnectionManager()
                        .findJingleRtpConnection(id.account, id.with, id.sessionId);
        final JingleRtpConnection connection = reference == null ? null : reference.get();
        if (connection == null) {
            return null;
        }
        try {
            return connection.getEndUserState();
        } catch (final IllegalStateException e) {
            return null;
        }
    }

    public static class Participant {
        public final AbstractJingleConnection.Id id;
        public final Jid jid;
        public final String nick;
        @Nullable public final RtpEndUserState state;

        Participant(
                final AbstractJingleConnection.Id id,
                final Jid jid,
                final String nick,
                @Nullable final RtpEndUserState state) {
            this.id = id;
            this.jid = jid;
            this.nick = nick;
            this.state = state;
        }
    }

    /**
     * Called for every available MUC presence of an occupant. The joining participant is responsible
     * for initiating sessions, so we only react to our own presence and to participants that
     * disappeared from the conference.
     */
    synchronized void onOccupantPresence(final MucOptions.User user) {
        if (user == null || !active) {
            return;
        }
        if (user.realJidMatchesAccount()) {
            return;
        }
        if (user.getMuji() == null) {
            terminateSession(user.getRealJid());
        }
    }

    synchronized void attach(final AbstractJingleConnection.Id id) {
        sessions.put(key(id.with), id);
    }

    synchronized void onSessionTerminated(final AbstractJingleConnection.Id id) {
        sessions.remove(key(id.with));
    }

    synchronized boolean isParticipant(final Jid realJid) {
        if (realJid == null) {
            return false;
        }
        return conversation.getMucOptions().findUserByRealJid(realJid.asBareJid()) != null;
    }

    private void initiateSessions() {
        for (final MucOptions.User user : conversation.getMucOptions().getUsers(false)) {
            if (user.realJidMatchesAccount()) {
                continue;
            }
            initiateSession(user);
        }
    }

    private void initiateSession(final MucOptions.User user) {
        final Jid realJid = user.getRealJid();
        if (realJid == null) {
            return;
        }
        final Muji theirMuji = user.getMuji();
        if (theirMuji == null || theirMuji.isPreparing() || !sharesContent(theirMuji)) {
            return;
        }
        if (sessions.containsKey(key(realJid))) {
            return;
        }
        // XEP-0272 0.2.0: send Jingle IQs to the real JID; prefer the full resource exposed by the MUC
        final Jid realFullJid = user.getRealFullJid();
        final Jid target = realFullJid != null ? realFullJid : realJid;
        final JingleRtpConnection connection =
                xmppConnectionService
                        .getJingleConnectionManager()
                        .initializeMujiRtpSession(account, target, room, intersectMedia(theirMuji));
        if (connection != null) {
            sessions.put(key(realJid), connection.getId());
        }
    }

    private void terminateSession(final Jid realJid) {
        if (realJid == null) {
            return;
        }
        final AbstractJingleConnection.Id id = sessions.remove(key(realJid.asBareJid()));
        endSession(id);
    }

    private void terminateSessions() {
        for (final AbstractJingleConnection.Id id : new HashSet<>(sessions.values())) {
            endSession(id);
        }
        sessions.clear();
    }

    private void endSession(final AbstractJingleConnection.Id id) {
        if (id == null) {
            return;
        }
        final WeakReference<JingleRtpConnection> reference =
                xmppConnectionService
                        .getJingleConnectionManager()
                        .findJingleRtpConnection(account, id.with, id.sessionId);
        final JingleRtpConnection connection = reference == null ? null : reference.get();
        if (connection != null) {
            connection.endCall();
        }
    }

    private Set<Media> intersectMedia(final Muji theirMuji) {
        final Set<Media> theirs = new HashSet<>();
        for (final MujiContent content : theirMuji.getContents()) {
            theirs.add(content.getMedia());
        }
        final Set<Media> result = new HashSet<>(media);
        result.retainAll(theirs);
        if (result.isEmpty()) {
            result.addAll(media);
        }
        return result;
    }

    private boolean sharesContent(final Muji theirMuji) {
        if (theirMuji.getContents().isEmpty()) {
            return false;
        }
        for (final MujiContent content : theirMuji.getContents()) {
            if (media.contains(content.getMedia())) {
                return true;
            }
        }
        return false;
    }

    private Muji toMuji() {
        if (!active) {
            return null;
        }
        if (preparing) {
            return Muji.preparing();
        }
        final Muji muji = Muji.empty();
        if (media.contains(Media.AUDIO)) {
            muji.addContent(MujiContent.of("voice", Media.AUDIO));
        }
        if (media.contains(Media.VIDEO)) {
            muji.addContent(MujiContent.of("video", Media.VIDEO));
        }
        return muji;
    }

    private void sendPresence() {
        final MucOptions mucOptions = conversation.getMucOptions();
        final MucOptions.User self = mucOptions.getSelf();
        if (self == null || self.getFullJid() == null) {
            Log.d(Config.LOGTAG, "unable to send Muji presence: not joined yet");
            return;
        }
        final PresencePacket packet =
                xmppConnectionService
                        .getPresenceGenerator()
                        .selfPresence(
                                account,
                                Presence.Status.ONLINE,
                                false,
                                self.getNick());
        packet.setTo(self.getFullJid());
        packet.addChild("x", Namespace.MUC);
        final Muji muji = toMuji();
        if (muji != null) {
            packet.addChild(muji);
        }
        MujiLog.log(xmppConnectionService.getFilesDir(), "TX MUC " + packet);
        xmppConnectionService.sendPresencePacket(account, packet);
    }

    private static String key(final Jid jid) {
        return jid == null ? "" : jid.asBareJid().toString();
    }
}
