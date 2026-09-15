package eu.siacs.conversations.ui;

import android.Manifest;
import android.animation.ValueAnimator;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.common.base.Optional;

import org.webrtc.EglBase;
import org.webrtc.RendererCommon;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.widget.MujiParticipantGridView;
import eu.siacs.conversations.ui.widget.SurfaceViewRenderer;
import eu.siacs.conversations.ui.util.StyledAttributes;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import eu.siacs.conversations.xmpp.jingle.Media;
import eu.siacs.conversations.xmpp.jingle.MujiConference;
import eu.siacs.conversations.xmpp.jingle.MujiLog;
import eu.siacs.conversations.xmpp.jingle.RtpEndUserState;
import eu.siacs.conversations.xmpp.jingle.stanzas.Muji;
import eu.siacs.conversations.xmpp.jingle.stanzas.MujiContent;

/**
 * Participant overview for an XEP-0272 Multiparty Jingle (Muji) conference. Shows one tile per mesh
 * participant with its remote video (if any) and connection state, plus a button to leave.
 */
public class MujiConferenceActivity extends XmppActivity {

    public static final String EXTRA_ACCOUNT = "account";
    public static final String EXTRA_ROOM = "room";

    private static final long REFRESH_INTERVAL = 1000L;
    private static final long AUDIO_LEVEL_INTERVAL = 10L;
    private static final long SPEAKING_HOLD_MS = 500L;
    private static final double AUDIO_LEVEL_THRESHOLD = 0.04d;
    private static final int SPEAKING_COLOR = 0xFF4CAF50;
    private static final String SELF_KEY = "self";
    private static final int MAX_SCO_RETRIES = 30;
    private static final long SCO_RETRY_DELAY = 1000L;
    private static final long BLUETOOTH_ROUTING_INTERVAL = 2000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, ParticipantView> views = new HashMap<>();
    private final Runnable refreshRunnable =
            new Runnable() {
                @Override
                public void run() {
                    refresh();
                    handler.postDelayed(this, REFRESH_INTERVAL);
                }
            };
    private final Runnable audioLevelRunnable =
            new Runnable() {
                @Override
                public void run() {
                    updateAudioLevels();
                    handler.postDelayed(this, AUDIO_LEVEL_INTERVAL);
                }
            };

    /**
     * Polls for a Bluetooth headset while the call is active and the speaker is off. The
     * {@code ACTION_ACL_CONNECTED} broadcast is not reliably delivered on all devices (Android
     * 13+/EMUI huawei), so this is the robust fallback that picks up a mid-call headset connection
     * (and re-routes if the headset is disconnected and reconnected).
     */
    private final Runnable bluetoothRoutingRunnable =
            new Runnable() {
                @Override
                public void run() {
                    maybeRouteToBluetooth();
                    handler.postDelayed(bluetoothRoutingRunnable, BLUETOOTH_ROUTING_INTERVAL);
                }
            };

    private void maybeRouteToBluetooth() {
        if (audioManager == null
                || speakerOn
                || !bluetoothPermissionGranted()
                || getActiveConference() == null) {
            return;
        }
        // On API 31+, setCommunicationDevice() is used instead of startBluetoothSco(). The
        // legacy ACTION_SCO_AUDIO_STATE_CHANGED broadcast does not fire when the communication
        // device disappears, so scoRequested can go stale after a headset disconnects — the poll
        // corrects this by checking whether the BT device is still in the output device list.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scoRequested) {
            if (findBluetoothAudioDevice() == null) {
                scoRequested = false;
            }
        }
        requestBluetoothSco();
    }

    private final BroadcastReceiver bluetoothScoReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(final Context context, final Intent intent) {
                    final String action = intent.getAction();
                    if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                        // A Bluetooth headset was (re)connected mid-call — the call was started
                        // without one, so re-route audio to the headset.
                        if (getActiveConference() != null
                                && !speakerOn
                                && bluetoothPermissionGranted()) {
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
                        if (getActiveConference() != null
                                && !speakerOn
                                && bluetoothPermissionGranted()) {
                            requestBluetoothRouteRetry();
                        }
                    }
                }
            };

    private Toolbar toolbar;
    private MujiParticipantGridView container;
    private TextView status;
    private MaterialButton micButton;
    private MaterialButton videoButton;
    private MaterialButton switchCameraButton;
    private MaterialButton speakerButton;

    private AudioManager audioManager;
    private PowerManager.WakeLock wakeLock;
    private boolean speakerOn = false;
    private boolean scoRequested = false;
    private int scoRetries = 0;

    private Account account;
    private Jid room;
    private Conversation conversation;

    @Override
    protected void refreshUiReal() {
        refresh();
    }

    @Override
    protected void onBackendConnected() {
        final Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        final String accountJid = intent.getStringExtra(EXTRA_ACCOUNT);
        final String roomJid = intent.getStringExtra(EXTRA_ROOM);
        if (accountJid == null || roomJid == null) {
            return;
        }
        this.account = xmppConnectionService.findAccountByJid(Jid.of(accountJid));
        if (this.account == null) {
            return;
        }
        this.room = Jid.of(roomJid).asBareJid();
        this.conversation = xmppConnectionService.find(account, room);
        refresh();
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_muji_conference);
        this.toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        configureActionBar(getSupportActionBar(), true);
        setTitle(R.string.muji_conference);
        this.status = findViewById(R.id.muji_status);
        this.container = findViewById(R.id.muji_participants);
        final MaterialButton leave = findViewById(R.id.muji_leave);
        leave.setOnClickListener(v -> leaveConference());
        this.micButton = findViewById(R.id.muji_mic);
        this.videoButton = findViewById(R.id.muji_video);
        this.switchCameraButton = findViewById(R.id.muji_switch_camera);
        this.speakerButton = findViewById(R.id.muji_speaker);
        this.audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        final IntentFilter bluetoothFilter =
                new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED);
        bluetoothFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        ContextCompat.registerReceiver(
                getApplicationContext(),
                bluetoothScoReceiver,
                bluetoothFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        acquireWakelock();
        micButton.setOnClickListener(v -> toggleMicrophone());
        videoButton.setOnClickListener(v -> toggleVideo());
        switchCameraButton.setOnClickListener(v -> switchCamera());
        speakerButton.setOnClickListener(v -> toggleSpeaker());
        applySpeaker();
        updateSpeakerButton();
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.post(refreshRunnable);
        handler.post(audioLevelRunnable);
        handler.post(bluetoothRoutingRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(refreshRunnable);
        handler.removeCallbacks(audioLevelRunnable);
        handler.removeCallbacks(bluetoothRoutingRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseWakeLock();
        releaseBluetoothSco();
        try {
            getApplicationContext().unregisterReceiver(bluetoothScoReceiver);
        } catch (final IllegalArgumentException ignored) {
            // receiver was not registered
        }
        handler.removeCallbacksAndMessages(null);
        clearParticipants();
    }

    private void acquireWakelock() {
        if (wakeLock != null) {
            return;
        }
        final PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            return;
        }
        wakeLock =
                powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK, "monocles:MujiConferenceActivity");
        wakeLock.acquire();
        Log.d(Config.LOGTAG, "Muji: partial wakelock acquired");
    }

    private void releaseWakeLock() {
        if (wakeLock != null) {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
            wakeLock = null;
            Log.d(Config.LOGTAG, "Muji: partial wakelock released");
        }
    }

    private void leaveConference() {
        if (account != null && room != null) {
            xmppConnectionService.getMujiConferenceManager().leave(account, room);
        }
        finish();
    }

    private void refresh() {
        final MujiConference conference = getActiveConference();
        if (conference == null) {
            showInactive();
            return;
        }
        status.setText(R.string.muji_conference_active);
        toolbar.setVisibility(View.GONE);
        final Set<String> present = new HashSet<>();
        present.add(SELF_KEY);
        final ParticipantView selfView = getOrCreateView(SELF_KEY);
        selfView.label.setText(R.string.muji_self);
        bindAvatar(selfView, selfAvatarable());
        final VideoTrack selfVideo = conference.getLocalVideoTrack();
        final boolean selfVideoEnabled = conference.getMedia().contains(Media.VIDEO);
        bindSelfVideo(selfView, conference, selfVideo, selfVideoEnabled);
        final List<MujiConference.Participant> participants = conference.getParticipants();
        for (final MujiConference.Participant participant : participants) {
            final String key = participant.jid.asBareJid().toString();
            present.add(key);
            final ParticipantView view = getOrCreateView(key);
            view.label.setText(
                    participant.nick + " - " + stateLabel(participant.state));
            bindAvatar(view, participantAvatarable(participant));
            bindVideo(view, conference, participant, remoteProvidesVideo(participant));
        }
        final List<String> stale = new ArrayList<>();
        for (final String key : views.keySet()) {
            if (!present.contains(key)) {
                stale.add(key);
            }
        }
        for (final String key : stale) {
            removeView(key);
        }
        updateControls(conference);
    }

    private List<JingleRtpConnection> connections() {
        final List<JingleRtpConnection> connections = new ArrayList<>();
        final MujiConference conference = getActiveConference();
        if (conference == null) {
            return connections;
        }
        for (final MujiConference.Participant participant : conference.getParticipants()) {
            final JingleRtpConnection connection = conference.getConnection(participant);
            if (connection != null) {
                connections.add(connection);
            }
        }
        return connections;
    }

    private void updateControls(final MujiConference conference) {
        final List<JingleRtpConnection> connections = new ArrayList<>();
        for (final MujiConference.Participant participant : conference.getParticipants()) {
            final JingleRtpConnection connection = conference.getConnection(participant);
            if (connection != null) {
                connections.add(connection);
            }
        }
boolean microphone = conference.isMicrophoneEnabled();
        boolean video = false;
        boolean hasVideo = false;
        boolean cameraSwitchable = false;
        if (connections.isEmpty()) {
            // Nobody has joined yet — reflect our own call state so the controls
            // look exactly like they do once two or more participants are talking.
            final VideoTrack selfVideo = conference.getLocalVideoTrack();
            hasVideo = selfVideo != null;
            video = hasVideo && conference.getMedia().contains(Media.VIDEO);
        } else {
            for (final JingleRtpConnection connection : connections) {
                if (connection.getLocalVideoTrack().isPresent()) {
                    hasVideo = true;
                    if (connection.isVideoEnabled()) {
                        video = true;
                    }
                    if (connection.isCameraSwitchable()) {
                        cameraSwitchable = true;
                    }
                }
            }
        }
        final int neutral = 0xFF525252;
        final int red = 0xFFD32F2F;
        micButton.setIconResource(
                microphone ? R.drawable.ic_mic_black_24dp : R.drawable.ic_mic_off_black_24dp);
        micButton.setBackgroundTintList(ColorStateList.valueOf(microphone ? neutral : red));
        videoButton.setIconResource(
                video
                        ? R.drawable.ic_videocam_black_24dp
                        : R.drawable.ic_videocam_off_black_24dp);
        videoButton.setBackgroundTintList(ColorStateList.valueOf(video ? neutral : red));
        videoButton.setVisibility(hasVideo ? View.VISIBLE : View.GONE);
        switchCameraButton.setVisibility(cameraSwitchable ? View.VISIBLE : View.GONE);
        micButton.setVisibility(View.VISIBLE);
        speakerButton.setVisibility(View.VISIBLE);
        updateSpeakerButton();
    }

    private void updateAudioLevels() {
        final boolean animationsEnabled =
                getBooleanPreference("play_gif_inside", R.bool.play_gif_inside);
        final MujiConference conference = getActiveConference();
        if (!animationsEnabled || conference == null) {
            for (final ParticipantView view : views.values()) {
                view.setSpeaking(false);
            }
            return;
        }
        for (final MujiConference.Participant participant : conference.getParticipants()) {
            final JingleRtpConnection connection = conference.getConnection(participant);
            if (connection == null) {
                continue;
            }
            final String key = participant.jid.asBareJid().toString();
            connection.getRemoteAudioLevel(
                    level -> handler.post(() -> applyAudioLevel(key, level)));
        }
    }

    private void applyAudioLevel(final String key, final double level) {
        final ParticipantView view = views.get(key);
        if (view == null) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (level > AUDIO_LEVEL_THRESHOLD) {
            view.lastSpeaking = now;
            view.setSpeaking(true);
        } else if (now - view.lastSpeaking > SPEAKING_HOLD_MS) {
            view.setSpeaking(false);
        }
    }

    private void toggleMicrophone() {
        final MujiConference conference = getActiveConference();
        if (conference == null) {
            return;
        }
        conference.setMicrophoneEnabled(!conference.isMicrophoneEnabled());
        refresh();
    }

    private void toggleVideo() {
        final List<JingleRtpConnection> connections = connections();
        if (connections.isEmpty()) {
            return;
        }
        boolean hasTrack = false;
        boolean enable = true;
        for (final JingleRtpConnection connection : connections) {
            if (connection.getLocalVideoTrack().isPresent()) {
                hasTrack = true;
                if (connection.isVideoEnabled()) {
                    enable = false;
                }
            }
        }
        if (!hasTrack) {
            return;
        }
        for (final JingleRtpConnection connection : connections) {
            if (connection.getLocalVideoTrack().isPresent()) {
                connection.setVideoEnabled(enable);
            }
        }
        updateConferenceVideoMedia(enable);
        refresh();
    }

    private void updateConferenceVideoMedia(final boolean videoEnabled) {
        final MujiConference conference = getActiveConference();
        if (conference == null) {
            return;
        }
        final Set<Media> media = new HashSet<>(conference.getMedia());
        final boolean changed;
        if (videoEnabled) {
            changed = media.add(Media.VIDEO);
        } else {
            changed = media.remove(Media.VIDEO);
        }
        if (changed) {
            conference.updateMedia(media);
        }
    }

    private void switchCamera() {
        final List<JingleRtpConnection> connections = connections();
        if (connections.isEmpty()) {
            return;
        }
        connections.get(0).switchCamera();
    }

    @SuppressWarnings("deprecation")
    private void toggleSpeaker() {
        speakerOn = !speakerOn;
        applySpeaker();
        updateSpeakerButton();
    }

    @SuppressWarnings("deprecation")
    private void applySpeaker() {
        if (audioManager == null) {
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

    @SuppressWarnings("deprecation")
    private boolean bluetoothPermissionGranted() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private void logBt(final String message) {
        Log.d(Config.LOGTAG, message);
        if (xmppConnectionService != null) {
            MujiLog.log(xmppConnectionService.getFilesDir(), message);
        }
    }

    /**
     * Attempts to route audio to the Bluetooth headset, and keeps retrying on a delay until either a
     * route is established, the call ends, the speaker is turned on, or the retry budget runs out.
     * Needed because the audio profile may become available a moment after the link is reported.
     */
    private void requestBluetoothRouteRetry() {
        if (scoRequested) {
            return;
        }
        if (audioManager == null
                || getActiveConference() == null
                || speakerOn
                || !bluetoothPermissionGranted()) {
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
                    MujiConferenceActivity.this::requestBluetoothRouteRetry, SCO_RETRY_DELAY);
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
    @RequiresApi(api = Build.VERSION_CODES.S)
    @Nullable
    private AudioDeviceInfo findBluetoothAudioDevice() {
        if (audioManager == null) {
            return null;
        }
        for (final AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
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

    private void updateSpeakerButton() {
        if (speakerButton == null) {
            return;
        }
        speakerButton.setIconResource(
                speakerOn
                        ? R.drawable.ic_volume_up_black_24dp
                        : R.drawable.ic_volume_off_black_24dp);
        speakerButton.setContentDescription(
                getString(speakerOn ? R.string.muji_speaker_on : R.string.muji_speaker_off));
        speakerButton.setBackgroundTintList(
                ColorStateList.valueOf(
                        speakerOn
                                ? StyledAttributes.getColor(this, R.attr.colorAccent)
                                : 0xFF525252));
    }

    @Nullable
    private MujiConference getActiveConference() {
        if (conversation == null || account == null || room == null) {
            return null;
        }
        final MujiConference conference =
                xmppConnectionService.getMujiConferenceManager().get(account, room);
        if (conference == null || !conference.isActive()) {
            return null;
        }
        return conference;
    }

    @Nullable
    private AvatarService.Avatarable selfAvatarable() {
        if (conversation == null) {
            return null;
        }
        return conversation.getMucOptions().getSelf();
    }

    @Nullable
    private AvatarService.Avatarable participantAvatarable(
            final MujiConference.Participant participant) {
        return participantUser(participant);
    }

    @Nullable
    private MucOptions.User participantUser(final MujiConference.Participant participant) {
        if (conversation == null || participant.jid == null) {
            return null;
        }
        return conversation.getMucOptions().findUserByRealJid(participant.jid.asBareJid());
    }

    /**
     * Whether the remote participant currently advertises a video content in the MUC. When a
     * participant turns the camera off, the {@code <muji>} element stops carrying video and the tile
     * falls back to the avatar.
     */
    private boolean remoteProvidesVideo(final MujiConference.Participant participant) {
        final MucOptions.User user = participantUser(participant);
        if (user == null) {
            return true;
        }
        final Muji muji = user.getMuji();
        if (muji == null) {
            return false;
        }
        for (final MujiContent content : muji.getContents()) {
            if (content.getMedia() == Media.VIDEO) {
                return true;
            }
        }
        return false;
    }

    private void bindVideo(
            final ParticipantView view,
            final MujiConference conference,
            final MujiConference.Participant participant,
            final boolean videoExpected) {
        final JingleRtpConnection connection = conference.getConnection(participant);
        final VideoTrack remoteVideo =
                connection == null ? null : connection.getRemoteVideoTrack().orNull();
        final EglBase.Context eglContext = conference.getEglBaseContext();
        if (!videoExpected || remoteVideo == null || eglContext == null) {
            showAvatar(view);
            MujiLog.log(
                    xmppConnectionService.getFilesDir(),
                    "UI no video for "
                            + participant.nick
                            + " expected="
                            + videoExpected
                            + " remote="
                            + (remoteVideo != null)
                            + " egl="
                            + (eglContext != null));
            return;
        }
        showRenderer(view);
        if (remoteVideo == view.boundTrack) {
            return;
        }
        view.ensureRendererInitialized(eglContext);
        if (view.boundTrack != null) {
            try {
                view.boundTrack.removeSink(view.renderer);
            } catch (final IllegalStateException e) {
                // track already disposed
            }
        }
        if (view.frameLogger == null) {
            view.frameLogger =
                    new VideoSink() {
                        private long last = 0L;

                        @Override
                        public void onFrame(final VideoFrame frame) {
                            final long now = System.currentTimeMillis();
                            if (now - last > 1000L) {
                                last = now;
                                final VideoFrame.Buffer buffer = frame.getBuffer();
                                MujiLog.log(
                                        xmppConnectionService.getFilesDir(),
                                        "UI frame "
                                                + buffer.getWidth()
                                                + "x"
                                                + buffer.getHeight()
                                                + " rot="
                                                + frame.getRotation());
                            }
                        }
                    };
        }
        remoteVideo.addSink(view.frameLogger);
        remoteVideo.addSink(view.renderer);
        view.boundTrack = remoteVideo;
        MujiLog.log(
                xmppConnectionService.getFilesDir(),
                "UI bound video for "
                        + participant.nick
                        + " size="
                        + view.renderer.getWidth()
                        + "x"
                        + view.renderer.getHeight());
    }

    private void bindSelfVideo(
            final ParticipantView view,
            final MujiConference conference,
            final VideoTrack track,
            final boolean videoEnabled) {
        final EglBase.Context eglContext = conference.getEglBaseContext();
        if (!videoEnabled || track == null || eglContext == null) {
            showAvatar(view);
            return;
        }
        showRenderer(view);
        if (track == view.boundTrack) {
            return;
        }
        view.ensureRendererInitialized(eglContext);
        if (view.boundTrack != null) {
            try {
                view.boundTrack.removeSink(view.renderer);
            } catch (final IllegalStateException e) {
                // track already disposed
            }
        }
        track.addSink(view.renderer);
        view.renderer.setMirror(conference.isFrontCamera());
        view.boundTrack = track;
        MujiLog.log(
                xmppConnectionService.getFilesDir(),
                "UI bound local self video mirror=" + conference.isFrontCamera());
    }

    private ParticipantView getOrCreateView(final String key) {
        final ParticipantView existing = views.get(key);
        if (existing != null) {
            return existing;
        }
        final View root =
                LayoutInflater.from(this)
                        .inflate(R.layout.item_muji_participant, container, false);
        final ParticipantView view =
                new ParticipantView(
                        root,
                        root.findViewById(R.id.participant_video),
                        root.findViewById(R.id.participant_avatar),
                        root.findViewById(R.id.participant_label));
        container.addView(root);
        views.put(key, view);
        return view;
    }

    private void bindAvatar(
            final ParticipantView view,
            @Nullable final AvatarService.Avatarable avatarable) {
        if (view.boundAvatarable == avatarable) {
            return;
        }
        view.boundAvatarable = avatarable;
        if (avatarable == null) {
            return;
        }
        AvatarWorkerTask.loadAvatar(avatarable, view.avatar, R.dimen.avatar_big);
    }

    private void showAvatar(final ParticipantView view) {
        view.avatar.setVisibility(View.VISIBLE);
        view.renderer.setVisibility(View.GONE);
    }

    private void showRenderer(final ParticipantView view) {
        view.avatar.setVisibility(View.GONE);
        view.renderer.setVisibility(View.VISIBLE);
    }

    private void removeView(final String key) {
        final ParticipantView view = views.remove(key);
        if (view == null) {
            return;
        }
        container.removeView(view.root);
        view.releaseRenderer();
    }

    private void clearParticipants() {
        for (final ParticipantView view : views.values()) {
            container.removeView(view.root);
            view.releaseRenderer();
        }
        views.clear();
    }

    private void showInactive() {
        toolbar.setVisibility(View.VISIBLE);
        status.setText(R.string.muji_conference_not_joined);
        clearParticipants();
        micButton.setVisibility(View.GONE);
        videoButton.setVisibility(View.GONE);
        switchCameraButton.setVisibility(View.GONE);
        speakerButton.setVisibility(View.GONE);
    }

    private String stateLabel(@Nullable final RtpEndUserState state) {
        if (state == null) {
            return getString(R.string.rtp_state_connecting);
        }
        return switch (state) {
            case CONNECTED -> getString(R.string.rtp_state_connected);
            case RECONNECTING, CONNECTIVITY_LOST_ERROR -> getString(R.string.rtp_state_reconnecting);
            case CONNECTIVITY_ERROR -> getString(R.string.rtp_state_connectivity_error);
            case RINGING -> getString(R.string.rtp_state_ringing);
            case ENDING_CALL -> getString(R.string.rtp_state_ending_call);
            case DECLINED_OR_BUSY -> getString(R.string.rtp_state_declined_or_busy);
            case CONTACT_OFFLINE -> getString(R.string.rtp_state_contact_offline);
            case RETRACTED -> getString(R.string.rtp_state_retracted);
            case APPLICATION_ERROR -> getString(R.string.rtp_state_application_failure);
            case SECURITY_ERROR -> getString(R.string.rtp_state_security_error);
            case ENDED -> getString(R.string.muji_state_ended);
            case CONNECTING, ACCEPTING_CALL, FINDING_DEVICE, INCOMING_CALL, INCOMING_CONTENT_ADD ->
                    getString(R.string.rtp_state_connecting);
        };
    }

    private static final class ParticipantView {
        final View root;
        final SurfaceViewRenderer renderer;
        final ShapeableImageView avatar;
        final TextView label;
        @Nullable VideoTrack boundTrack;
        @Nullable VideoSink frameLogger;
        @Nullable Object boundAvatarable;
        @Nullable ValueAnimator pulse;
        long lastSpeaking = 0L;
        boolean speaking = false;
        private boolean rendererInitialized = false;

        ParticipantView(
                final View root,
                final SurfaceViewRenderer renderer,
                final ShapeableImageView avatar,
                final TextView label) {
            this.root = root;
            this.renderer = renderer;
            this.avatar = avatar;
            this.label = label;
        }

        void setSpeaking(final boolean speaking) {
            if (this.speaking == speaking) {
                return;
            }
            this.speaking = speaking;
            if (speaking) {
                final float density = root.getResources().getDisplayMetrics().density;
                float size = Math.min(avatar.getWidth(), avatar.getHeight());
                if (size <= 0f) {
                    size = 96f * density;
                }
                // ring thickness = 5% of the avatar size
                final float maxStroke = 0.05f * size;
                avatar.setStrokeColor(ColorStateList.valueOf(SPEAKING_COLOR));
                avatar.setStrokeWidth(0.6f * maxStroke);
                startPulse(maxStroke);
            } else {
                stopPulse();
                avatar.setStrokeWidth(0f);
            }
        }

        private void startPulse(final float maxStroke) {
            stopPulse();
            final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(700L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
            animator.addUpdateListener(
                    a -> {
                        final float f = (float) a.getAnimatedValue();
                        final float scale = 1.0f + 0.05f * f;
                        avatar.setScaleX(scale);
                        avatar.setScaleY(scale);
                        avatar.setStrokeWidth(maxStroke * (0.6f + 0.4f * f));
                    });
            animator.start();
            this.pulse = animator;
        }

        private void stopPulse() {
            if (pulse != null) {
                pulse.cancel();
                pulse = null;
            }
            avatar.setScaleX(1f);
            avatar.setScaleY(1f);
        }

        void ensureRendererInitialized(final EglBase.Context eglContext) {
            if (rendererInitialized) {
                return;
            }
            rendererInitialized = true;
            renderer.setVisibility(View.VISIBLE);
            try {
                renderer.init(eglContext, null);
            } catch (final IllegalStateException ignored) {
                // SurfaceViewRenderer was already initialized
            } catch (final RuntimeException e) {
                Log.w(Config.LOGTAG, "could not set up participant video renderer", e);
            }
            renderer.setEnableHardwareScaler(false);
            // Keep the tile size fixed: using SCALE_ASPECT_FIT as the layout aspect ratio makes the
            // renderer resize itself and fight the grid's fixed 16:9 layout.
            renderer.setScalingType(
                    RendererCommon.ScalingType.SCALE_ASPECT_FILL,
                    RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        }

        void releaseRenderer() {
            stopPulse();
            if (boundTrack != null) {
                try {
                    boundTrack.removeSink(renderer);
                    if (frameLogger != null) {
                        boundTrack.removeSink(frameLogger);
                    }
                } catch (final IllegalStateException e) {
                    // track already disposed
                }
                boundTrack = null;
                frameLogger = null;
            }
            if (rendererInitialized) {
                renderer.release();
                rendererInitialized = false;
            }
        }
    }
}
