package eu.siacs.conversations.xmpp.jingle;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.webrtc.VideoTrack;

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

    private static final int MAX_SCO_RETRIES = 30;
    private static final long SCO_RETRY_DELAY = 1000L;
    private static final long BLUETOOTH_ROUTING_INTERVAL = 2000L;

    private final XmppConnectionService xmppConnectionService;
    private final Account account;
    private final Conversation conversation;
    private final Jid room;
    private final Set<Media> media = new HashSet<>();
    private final Map<String, AbstractJingleConnection.Id> sessions = new HashMap<>();
    private final Set<String> announced = new HashSet<>();
    private final AtomicBoolean joinChimePlayed = new AtomicBoolean(false);
    private final int existingMujiParticipantsAtCreation;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable bluetoothRoutingRunnable =
            new Runnable() {
                @Override
                public void run() {
                    maybeRouteToBluetooth();
                    handler.postDelayed(bluetoothRoutingRunnable, BLUETOOTH_ROUTING_INTERVAL);
                }
            };

    @Nullable private BroadcastReceiver bluetoothScoReceiver;
    @Nullable private AudioManager audioManager;
    @Nullable private PowerManager.WakeLock wakeLock;
    private boolean speakerOn = false;
    private boolean scoRequested = false;
    private int scoRetries = 0;

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
        this.existingMujiParticipantsAtCreation = countExistingMujiParticipants(conversation);
        this.conversation.setMujiCallTimestamp(System.currentTimeMillis());
        this.xmppConnectionService.updateConversationUi();
    }

    private static int countExistingMujiParticipants(final Conversation conversation) {
        int count = 0;
        for (final MucOptions.User user : conversation.getMucOptions().getUsers(false)) {
            if (!user.realJidMatchesAccount() && user.getMuji() != null) {
                count++;
            }
        }
        return count;
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
        initAudioRouting();
        sendPresence();
        handler.postDelayed(this::finishPreparation, PREPARATION_DELAY);
    }

    /** Owns the audio route for as long as the conference lives (survives the UI window closing). */
    private void initAudioRouting() {
        final Context context = xmppConnectionService.getApplicationContext();
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        final BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(final Context context, final Intent intent) {
                        final String action = intent.getAction();
                        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                            if (active && !speakerOn && bluetoothPermissionGranted()) {
                                logBt("Bluetooth device connected, re-routing audio");
                                scoRequested = false;
                                scoRetries = 0;
                                requestBluetoothRouteRetry();
                            }
                            return;
                        }
                        if (!AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED.equals(action)) {
                            return;
                        }
                        final int state =
                                intent.getIntExtra(
                                        AudioManager.EXTRA_SCO_AUDIO_STATE,
                                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                        if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                            logBt("Bluetooth SCO connected");
                            scoRetries = 0;
                        } else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                            logBt("Bluetooth SCO disconnected");
                            scoRequested = false;
                            if (active && !speakerOn && bluetoothPermissionGranted()) {
                                requestBluetoothRouteRetry();
                            }
                        }
                    }
                };
        this.bluetoothScoReceiver = receiver;
        final IntentFilter filter =
                new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        try {
            ContextCompat.registerReceiver(
                    context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        } catch (final IllegalArgumentException e) {
            Log.w(Config.LOGTAG, "unable to register Muji Bluetooth receiver", e);
        }
        acquireWakeLock();
        handler.post(bluetoothRoutingRunnable);
        applySpeaker();
    }

    @SuppressWarnings("deprecation")
    private void acquireWakeLock() {
        final PowerManager powerManager =
                (PowerManager) xmppConnectionService.getApplicationContext().getSystemService(Context.POWER_SERVICE);
        if (powerManager == null || wakeLock != null) {
            return;
        }
        final PowerManager.WakeLock lock =
                powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK, "monocles:MujiConference:" + room);
        lock.acquire();
        this.wakeLock = lock;
        logBt("ML wake lock acquired");
    }

    private void releaseWakeLock() {
        final PowerManager.WakeLock lock = this.wakeLock;
        this.wakeLock = null;
        if (lock != null && lock.isHeld()) {
            lock.release();
        }
    }

    /**
     * Polls for a Bluetooth headset while the conference is active and the speaker is off. The
     * {@code ACTION_ACL_CONNECTED} broadcast is not reliably delivered on all devices, so this is
     * the robust fallback that picks up a mid-call headset connection (and re-routes if the headset
     * is disconnected and reconnected).
     */
    private void maybeRouteToBluetooth() {
        if (audioManager == null
                || !active
                || speakerOn
                || !bluetoothPermissionGranted()) {
            return;
        }
        // On API 31+, setCommunicationDevice() is used instead of startBluetoothSco(). The legacy
        // ACTION_SCO_AUDIO_STATE_CHANGED broadcast does not fire when the communication device
        // disappears, so scoRequested can go stale after a headset disconnects — the poll corrects
        // this by checking whether the BT device is still in the output device list.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scoRequested) {
            if (findBluetoothAudioDevice() == null) {
                scoRequested = false;
            }
        }
        requestBluetoothSco();
    }

    private void requestBluetoothRouteRetry() {
        if (scoRequested) {
            return;
        }
        if (audioManager == null || !active || speakerOn || !bluetoothPermissionGranted()) {
            return;
        }
        if (scoRetries >= MAX_SCO_RETRIES) {
            logBt("giving up on Bluetooth routing after " + scoRetries + " retries");
            return;
        }
        scoRetries++;
        requestBluetoothSco();
        if (!scoRequested && scoRetries < MAX_SCO_RETRIES) {
            logBt("no Bluetooth route yet, retry " + scoRetries + "/" + MAX_SCO_RETRIES);
            handler.postDelayed(
                    MujiConference.this::requestBluetoothRouteRetry, SCO_RETRY_DELAY);
        }
    }

    /** Routes the conference audio to a connected Bluetooth headset when available. */
    @SuppressWarnings("deprecation")
    private void requestBluetoothSco() {
        if (audioManager == null || scoRequested || !bluetoothPermissionGranted()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            final AudioDeviceInfo bluetoothDevice = findBluetoothAudioDevice();
            if (bluetoothDevice == null) {
                return;
            }
            try {
                audioManager.setCommunicationDevice(bluetoothDevice);
                scoRequested = true;
                logBt(
                        "routed audio to Bluetooth device via setCommunicationDevice (type="
                                + bluetoothDevice.getType()
                                + ", product="
                                + bluetoothDevice.getProductName()
                                + ")");
            } catch (final RuntimeException e) {
                logBt("unable to set Bluetooth communication device: " + e.getMessage());
            }
        } else {
            try {
                audioManager.startBluetoothSco();
                audioManager.setBluetoothScoOn(true);
                scoRequested = true;
                logBt("Bluetooth SCO requested (legacy)");
            } catch (final RuntimeException e) {
                Log.w(Config.LOGTAG, "unable to start Bluetooth SCO", e);
            }
        }
    }

    /** The preferred Bluetooth output device for a voice call, or null if none is available. */
    @Nullable
    private AudioDeviceInfo findBluetoothAudioDevice() {
        if (audioManager == null) {
            return null;
        }
        for (final AudioDeviceInfo device :
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            final int type = device.getType();
            if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    || type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    || type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                    || type == AudioDeviceInfo.TYPE_HEARING_AID) {
                return device;
            }
        }
        return null;
    }

    /** Releases the Bluetooth routing; the call falls back to the default output device. */
    @SuppressWarnings("deprecation")
    private void releaseBluetoothSco() {
        if (audioManager == null || !scoRequested) {
            return;
        }
        scoRequested = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.clearCommunicationDevice();
                logBt("cleared Bluetooth communication device");
            } catch (final RuntimeException e) {
                Log.w(Config.LOGTAG, "unable to clear Bluetooth communication device", e);
            }
        } else {
            try {
                audioManager.setBluetoothScoOn(false);
                audioManager.stopBluetoothSco();
            } catch (final RuntimeException e) {
                Log.w(Config.LOGTAG, "unable to stop Bluetooth SCO", e);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private boolean bluetoothPermissionGranted() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ContextCompat.checkSelfPermission(
                                xmppConnectionService.getApplicationContext(),
                                Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED;
    }

    /** Toggles the loudspeaker (off = route to the BT headset/earpiece, on = loudspeaker). */
    @SuppressWarnings("deprecation")
    public void toggleSpeaker() {
        speakerOn = !speakerOn;
        applySpeaker();
    }

    public boolean isSpeakerOn() {
        return speakerOn;
    }

    @SuppressWarnings("deprecation")
    private void applySpeaker() {
        if (audioManager == null || !active) {
            return;
        }
        try {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            if (speakerOn) {
                releaseBluetoothSco();
                audioManager.setSpeakerphoneOn(true);
            } else {
                scoRetries = 0;
                requestBluetoothSco();
                audioManager.setSpeakerphoneOn(false);
            }
        } catch (final RuntimeException e) {
            Log.w(Config.LOGTAG, "unable to change speakerphone state", e);
        }
    }

    private void logBt(final String message) {
        MujiLog.log(xmppConnectionService.getFilesDir(), message);
    }

    public synchronized void updateMedia(final Set<Media> media) {
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
        handler.removeCallbacks(bluetoothRoutingRunnable);
        final BroadcastReceiver receiver = this.bluetoothScoReceiver;
        this.bluetoothScoReceiver = null;
        if (receiver != null) {
            try {
                xmppConnectionService
                        .getApplicationContext()
                        .unregisterReceiver(receiver);
            } catch (final IllegalArgumentException ignored) {
                // receiver was not registered
            }
        }
        releaseBluetoothSco();
        releaseWakeLock();
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

    /**
     * Returns the local camera track shared by the conference, so the UI can render a self preview.
     * Only available when this conference was joined with video.
     */
    @Nullable
    public VideoTrack getLocalVideoTrack() {
        final WebRTCResources resources = this.webRTCResources;
        if (resources == null) {
            return null;
        }
        // Keep showing the (disabled, avatar-covered) self view after the user turns the camera
        // off, so only create/start the camera when video is actually contributed.
        final VideoTrack existing = resources.getVideoTrack();
        if (existing != null) {
            return existing;
        }
        if (!media.contains(Media.VIDEO)) {
            return null;
        }
        return resources.getOrCreateVideoTrack();
    }

    public boolean isFrontCamera() {
        final WebRTCResources resources = this.webRTCResources;
        return resources == null || resources.isFrontCamera();
    }

    /**
     * Whether the shared conference microphone track is enabled. The same {@code AudioTrack} is used
     * by every peer connection, so this is the single source of truth even before any participant
     * session exists.
     */
    public boolean isMicrophoneEnabled() {
        final WebRTCResources resources = this.webRTCResources;
        if (resources == null) {
            return true;
        }
        try {
            return resources.audioTrack.enabled();
        } catch (final IllegalStateException e) {
            Log.w(Config.LOGTAG, "unable to check conference microphone", e);
            return false;
        }
    }

    /**
     * Enables or disables the shared conference microphone track. Works both before and after other
     * participants join, since every session references the same track.
     */
    public boolean setMicrophoneEnabled(final boolean enabled) {
        final WebRTCResources resources = this.webRTCResources;
        if (resources == null) {
            return false;
        }
        try {
            resources.audioTrack.setEnabled(enabled);
            return true;
        } catch (final IllegalStateException e) {
            Log.w(Config.LOGTAG, "unable to toggle conference microphone", e);
            return false;
        }
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
        final Jid realJid = user.getRealJid();
        if (user.getMuji() != null) {
            if (realJid != null) {
                announced.remove(realJid.asBareJid().toString());
            }
            return;
        }
        if (realJid != null && announced.add(realJid.asBareJid().toString())) {
            // an occupant that is not (yet) in the conference; re-advertise so latecomers see the call
            sendPresence();
        }
        terminateSession(realJid);
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

    /**
     * Decides whether a just-connected session should play the local join chime. The chime is played
     * exactly once per conference lifetime and only on the devices of people actually in the call:
     *
     * <ul>
     *   <li>the conference starter (no other Muji participants when this conference was created)
     *       hears the very first participant joining on their first connected responder session;
     *   <li>the participant who joins a call that is already populated (2+ participants) hears a
     *       single chime when their first locally initiated session connects;
     *   <li>the first joiner of an empty-looking call and established participants stay silent.
     * </ul>
     */
    boolean shouldPlayJoinChime(final boolean locallyInitiated) {
        if (locallyInitiated) {
            if (existingMujiParticipantsAtCreation < 2) {
                return false;
            }
        } else if (existingMujiParticipantsAtCreation != 0) {
            return false;
        }
        return joinChimePlayed.compareAndSet(false, true);
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
                        .initializeMujiRtpSession(account, target, room, unionMedia(theirMuji));
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

    private Set<Media> unionMedia(final Muji theirMuji) {
        final Set<Media> result = new HashSet<>(media);
        for (final MujiContent content : theirMuji.getContents()) {
            result.add(content.getMedia());
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
