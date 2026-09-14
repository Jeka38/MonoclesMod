package eu.siacs.conversations.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
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
import eu.siacs.conversations.ui.widget.MujiParticipantGridView;
import eu.siacs.conversations.ui.widget.SurfaceViewRenderer;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import eu.siacs.conversations.xmpp.jingle.MujiConference;
import eu.siacs.conversations.xmpp.jingle.MujiLog;
import eu.siacs.conversations.xmpp.jingle.RtpEndUserState;

/**
 * Participant overview for an XEP-0272 Multiparty Jingle (Muji) conference. Shows one tile per mesh
 * participant with its remote video (if any) and connection state, plus a button to leave.
 */
public class MujiConferenceActivity extends XmppActivity {

    public static final String EXTRA_ACCOUNT = "account";
    public static final String EXTRA_ROOM = "room";

    private static final long REFRESH_INTERVAL = 1000L;

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

    private Toolbar toolbar;
    private MujiParticipantGridView container;
    private TextView status;
    private MaterialButton micButton;
    private MaterialButton videoButton;
    private MaterialButton switchCameraButton;

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
        micButton.setOnClickListener(v -> toggleMicrophone());
        videoButton.setOnClickListener(v -> toggleVideo());
        switchCameraButton.setOnClickListener(v -> switchCamera());
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.post(refreshRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(refreshRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        clearParticipants();
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
        final List<MujiConference.Participant> participants = conference.getParticipants();
        final Set<String> present = new HashSet<>();
        for (final MujiConference.Participant participant : participants) {
            final String key = participant.jid.asBareJid().toString();
            present.add(key);
            final ParticipantView view = getOrCreateView(key);
            view.label.setText(
                    participant.nick + " - " + stateLabel(participant.state));
            bindVideo(view, conference, participant);
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
        boolean microphone = false;
        boolean video = false;
        boolean hasVideo = false;
        boolean cameraSwitchable = false;
        for (final JingleRtpConnection connection : connections) {
            if (connection.isMicrophoneEnabled()) {
                microphone = true;
            }
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
        final int neutral = 0xFF525252;
        final int red = 0xFFD32F2F;
        micButton.setIconResource(
                microphone ? R.drawable.ic_mic_black_24dp : R.drawable.ic_mic_off_black_24dp);
        micButton.setBackgroundTintList(ColorStateList.valueOf(microphone ? neutral : red));
        micButton.setVisibility(connections.isEmpty() ? View.GONE : View.VISIBLE);
        videoButton.setIconResource(
                video
                        ? R.drawable.ic_videocam_black_24dp
                        : R.drawable.ic_videocam_off_black_24dp);
        videoButton.setBackgroundTintList(ColorStateList.valueOf(video ? neutral : red));
        videoButton.setVisibility(hasVideo ? View.VISIBLE : View.GONE);
        switchCameraButton.setVisibility(cameraSwitchable ? View.VISIBLE : View.GONE);
    }

    private void toggleMicrophone() {
        final List<JingleRtpConnection> connections = connections();
        if (connections.isEmpty()) {
            return;
        }
        boolean enable = true;
        for (final JingleRtpConnection connection : connections) {
            if (connection.isMicrophoneEnabled()) {
                enable = false;
                break;
            }
        }
        for (final JingleRtpConnection connection : connections) {
            connection.setMicrophoneEnabled(enable);
        }
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
        refresh();
    }

    private void switchCamera() {
        final List<JingleRtpConnection> connections = connections();
        if (connections.isEmpty()) {
            return;
        }
        connections.get(0).switchCamera();
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

    private void bindVideo(
            final ParticipantView view,
            final MujiConference conference,
            final MujiConference.Participant participant) {
        final JingleRtpConnection connection = conference.getConnection(participant);
        final VideoTrack remoteVideo =
                connection == null ? null : connection.getRemoteVideoTrack().orNull();
        if (remoteVideo == view.boundTrack) {
            return;
        }
        final EglBase.Context eglContext = conference.getEglBaseContext();
        if (remoteVideo == null || eglContext == null) {
            MujiLog.log(
                    xmppConnectionService.getFilesDir(),
                    "UI no video for "
                            + participant.nick
                            + " remote="
                            + (remoteVideo != null)
                            + " egl="
                            + (eglContext != null));
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
                        root.findViewById(R.id.participant_label));
        container.addView(root);
        views.put(key, view);
        return view;
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
        final TextView label;
        @Nullable VideoTrack boundTrack;
        @Nullable VideoSink frameLogger;
        private boolean rendererInitialized = false;

        ParticipantView(
                final View root, final SurfaceViewRenderer renderer, final TextView label) {
            this.root = root;
            this.renderer = renderer;
            this.label = label;
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
