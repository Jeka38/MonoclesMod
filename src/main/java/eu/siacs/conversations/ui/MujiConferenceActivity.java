package eu.siacs.conversations.ui;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.common.base.Optional;

import org.webrtc.EglBase;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoTrack;

import java.nio.ByteBuffer;
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
import eu.siacs.conversations.ui.widget.SpeakingBorderView;
import eu.siacs.conversations.ui.widget.TextureViewRenderer;
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
    /** A remote video track whose frames stopped for longer than this is treated as dead - the
     * sink then falls back to the avatar instead of rendering a black window. Covers the case of a
     * participant disabling their camera when their <muji> presence update never reaches us. */
    private static final long FRAME_TIMEOUT_MS = 3000L;
    /** Grace period after (re)binding a track during which the absence of frames is tolerated
     * (connection setup / first keyframe). Afterwards a track that never delivered a single frame
     * falls back to the avatar instead of staying a permanent black window. */
    private static final long FIRST_FRAME_GRACE_MS = 5000L;
    /** WebRTC keeps sending *black* frames when a remote disables its camera (it does not simply
     * stop the stream), so a frame-liveness check alone cannot detect it. A track whose frames are
     * continuously black for this long is treated as camera-off and falls back to the avatar. */
    private static final long BLACK_FRAME_TIMEOUT_MS = 2000L;
    /** How often (at most) the incoming frames are sampled for the black-frame check. */
    private static final long BLACK_FRAME_CHECK_INTERVAL_MS = 1000L;
    /** Average luma (0..255) below which a frame is considered black. */
    private static final int BLACK_FRAME_LUMA_THRESHOLD = 24;
    private static final long SPEAKING_HOLD_MS = 500L;
    private static final double AUDIO_LEVEL_THRESHOLD = 0.04d;
    private static final int SPEAKING_COLOR = 0xFF4CAF50;
    private static final String SELF_KEY = "self";

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

    private Toolbar toolbar;
    private MujiParticipantGridView container;
    private TextView status;
    private MaterialButton micButton;
    private MaterialButton videoButton;
    private MaterialButton switchCameraButton;
    private MaterialButton speakerButton;

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
        micButton.setOnClickListener(v -> toggleMicrophone());
        videoButton.setOnClickListener(v -> toggleVideo());
        switchCameraButton.setOnClickListener(v -> switchCamera());
        speakerButton.setOnClickListener(v -> toggleSpeaker());
        updateSpeakerButton();
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.post(refreshRunnable);
        handler.post(audioLevelRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(refreshRunnable);
        handler.removeCallbacks(audioLevelRunnable);
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
            bindVideo(view, conference, participant, remoteProvidesVideo(conference, participant));
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
        applyExpandedStyling();
        updateControls(conference);
    }

    /**
     * Applies the thumbnail/"floating avatar" look to every tile that is not the currently expanded
     * one (and restores the full tile look when nothing is expanded).
     *
     * <p>In expanded mode all thumbnails overlap the expanded tile. Android draws later-index
     * children on top of earlier ones, and since every thumbnailed tile (self is always index 0)
     * sits at a lower child index than most expanded tiles, they would be hidden behind the
     * expanded tile. Raising the thumbnails' Z above the expanded tile puts them back on top in
     * both the software and the SurfaceView (video) render paths.
     */
    private void applyExpandedStyling() {
        final View expanded = container.getExpandedChild();
        final float thumbnailZ = 2f * getResources().getDisplayMetrics().density;
        for (final ParticipantView view : views.values()) {
            final boolean thumbnail = expanded != null && view.root != expanded;
            view.setThumbnailStyle(thumbnail);
            view.root.setTranslationZ(thumbnail ? thumbnailZ : 0f);
        }
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

    private void toggleSpeaker() {
        final MujiConference conference = getActiveConference();
        if (conference == null) {
            return;
        }
        conference.toggleSpeaker();
        updateSpeakerButton();
    }

    private void updateSpeakerButton() {
        if (speakerButton == null) {
            return;
        }
        final MujiConference conference = getActiveConference();
        final boolean speakerOn = conference != null && conference.isSpeakerOn();
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
        final MucOptions.User user = participantUser(participant);
        if (user != null) {
            return user;
        }
        // Fall back to the roster contact for the real JID so the tile still shows an avatar
        // instead of a blank circle when the occupant could not be matched to a MucOptions.User.
        if (conversation != null && participant.jid != null) {
            return conversation.getAccount().getRoster().getContact(participant.jid.asBareJid());
        }
        return null;
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
    private boolean remoteProvidesVideo(
            final MujiConference conference, final MujiConference.Participant participant) {
        final MucOptions.User user = participantUser(participant);
        if (user == null) {
            return true;
        }
        final Muji muji = user.getMuji();
        if (muji == null) {
            // Distinguish between two causes of a missing <muji>:
            //  * hasEverSeenMuji(): the occupant previously advertised the conference and stopped
            //    (they left the call). The stale renderer must fall back to the avatar instead of
            //    staying bound to an orphaned session, which would render a black window.
            //  * !hasEverSeenMuji(): we never received any muji presence from them at all - the
            //    active voice/video presence of a participant who joined after us is often not
            //    delivered, so the resolved Jingle session is authoritative: a live remote video
            //    track means they are really in the call and the tile renders video.
            if (user.hasEverSeenMuji()) {
                return false;
            }
            final JingleRtpConnection connection = conference.getConnection(participant);
            return connection != null && connection.getRemoteVideoTrack().isPresent();
        }
        if (muji.isPreparing()) {
            // Only a <preparing/> placeholder: the active voice/video presence was not delivered to
            // us (the participant joined while we were already in the room and the roster never
            // refreshed). The capability element is not authoritative then - the resolved session
            // is: a received remote video track means the session really negotiated video.
            final JingleRtpConnection connection = conference.getConnection(participant);
            return connection != null && connection.getRemoteVideoTrack().isPresent();
        }
        for (final MujiContent content : muji.getContents()) {
            if (content.getMedia() == Media.VIDEO) {
                return true;
            }
        }
        // the participant advertises an active <muji> without video (e.g. camera turned off)
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
        final long now = System.currentTimeMillis();
        // A fresh (or changed) track restarts the first-frame grace period.
        if (remoteVideo != null && remoteVideo != view.boundTrack) {
            view.boundAt = now;
        }
        final boolean framesLively = framesLively(view, now);
        if (!videoExpected || remoteVideo == null || eglContext == null || !framesLively) {
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
                            + (eglContext != null)
                            + " lively="
                            + framesLively);
            return;
        }
        showRenderer(view);
        if (remoteVideo == view.boundTrack) {
            return;
        }
        bindTrack(view, remoteVideo, false, eglContext);
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
        final long now = System.currentTimeMillis();
        if (track != null && track != view.boundTrack) {
            view.boundAt = now;
        }
        final boolean framesLively = framesLively(view, now);
        if (!videoEnabled || track == null || eglContext == null || !framesLively) {
            showAvatar(view);
            return;
        }
        showRenderer(view);
        if (track == view.boundTrack) {
            return;
        }
        bindTrack(view, track, conference.isFrontCamera(), eglContext);
        MujiLog.log(
                xmppConnectionService.getFilesDir(),
                "UI bound local self video mirror=" + conference.isFrontCamera());
    }

    /** A track counts as live if frames arrived within {@link #FRAME_TIMEOUT_MS} - or, for a track
     *  whose first frame has not arrived yet, within {@link #FIRST_FRAME_GRACE_MS} of binding. A
     *  track that keeps delivering black frames (remote camera disabled) is not live either. */
    private static boolean framesLively(final ParticipantView view, final long now) {
        if (view.blackFrameSince > 0L && now - view.blackFrameSince >= BLACK_FRAME_TIMEOUT_MS) {
            return false;
        }
        if (view.lastFrameAt > 0L) {
            return now - view.lastFrameAt <= FRAME_TIMEOUT_MS;
        }
        return now - view.boundAt <= FIRST_FRAME_GRACE_MS;
    }

    private void bindTrack(
            final ParticipantView view,
            final VideoTrack track,
            final boolean mirror,
            final EglBase.Context eglContext) {
        view.ensureRendererInitialized(eglContext);
        if (view.boundTrack != null) {
            removeSink(view.boundTrack, view.renderer);
            if (view.frameLogger != null) {
                removeSink(view.boundTrack, view.frameLogger);
            }
        }
        if (view.frameLogger == null) {
            view.frameLogger = createFrameLogger(view);
        }
        track.addSink(view.frameLogger);
        track.addSink(view.renderer);
        view.renderer.setMirror(mirror);
        view.boundTrack = track;
        view.lastFrameAt = 0L;
        view.blackFrameSince = 0L;
        view.boundAt = System.currentTimeMillis();
    }

    private static void removeSink(final VideoTrack track, final VideoSink sink) {
        try {
            track.removeSink(sink);
        } catch (final IllegalStateException e) {
            // track already disposed
        }
    }

    private VideoSink createFrameLogger(final ParticipantView view) {
        return new VideoSink() {
            private long last = 0L;
            private long lastBlackCheck = 0L;

            @Override
            public void onFrame(final VideoFrame frame) {
                final long now = System.currentTimeMillis();
                view.lastFrameAt = now;
                if (now - lastBlackCheck >= BLACK_FRAME_CHECK_INTERVAL_MS) {
                    lastBlackCheck = now;
                    updateBlackFrameState(view, frame, now);
                }
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
                                    + frame.getRotation()
                                    + " black="
                                    + (view.blackFrameSince > 0L));
                }
            }
        };
    }

    /**
     * WebRTC keeps producing (black) frames after a remote disables its camera. Sample the luma of
     * the incoming frames and remember since when they have been continuously black so that the tile
     * can fall back to the avatar even when the {@code <muji>} presence update never arrives.
     */
    private static void updateBlackFrameState(
            final ParticipantView view, final VideoFrame frame, final long now) {
        boolean black = false;
        try {
            final VideoFrame.I420Buffer i420 = frame.getBuffer().toI420();
            try {
                black = isBlack(i420);
            } finally {
                i420.release();
            }
        } catch (final RuntimeException e) {
            // Some buffers cannot be read back; never let that keep the tile black forever.
            black = false;
        }
        if (black) {
            if (view.blackFrameSince == 0L) {
                view.blackFrameSince = now;
            }
        } else {
            view.blackFrameSince = 0L;
        }
    }

    private static boolean isBlack(final VideoFrame.I420Buffer i420) {
        final ByteBuffer y = i420.getDataY();
        final int stride = i420.getStrideY();
        final int width = i420.getWidth();
        final int height = i420.getHeight();
        if (width <= 0 || height <= 0 || stride <= 0) {
            return false;
        }
        long sum = 0L;
        int count = 0;
        for (int row = 0; row < height; row += 8) {
            final int rowOffset = row * stride;
            for (int col = 0; col < width; col += 8) {
                sum += y.get(rowOffset + col) & 0xFF;
                count++;
            }
        }
        return count > 0 && (sum / count) < BLACK_FRAME_LUMA_THRESHOLD;
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
                        root.findViewById(R.id.participant_speaking),
                        root.findViewById(R.id.participant_label));
        root.setOnClickListener(
                v -> {
                    final View expanded = container.getExpandedChild();
                    container.setExpandedChild(expanded == root ? null : root);
                    applyExpandedStyling();
                });
        container.addView(root);
        views.put(key, view);
        return view;
    }

    private void bindAvatar(
            final ParticipantView view,
            @Nullable final AvatarService.Avatarable avatarable) {
        if (avatarable == null) {
            if (view.boundAvatarable != null) {
                view.boundAvatarable = null;
                view.avatar.setImageDrawable(null);
                view.avatar.setBackgroundColor(0x00000000);
                MujiLog.log(xmppConnectionService.getFilesDir(), "UI avatar null");
            }
            return;
        }
        // Re-request the avatar if the same source is bound but nothing was ever loaded (e.g. the
        // first attempt ran before the view was attached), otherwise skip the redundant work.
        if (view.boundAvatarable == avatarable && view.avatar.getDrawable() != null) {
            return;
        }
        final boolean changed = view.boundAvatarable != avatarable;
        view.boundAvatarable = avatarable;
        if (changed) {
            MujiLog.log(
                    xmppConnectionService.getFilesDir(),
                    "UI avatar " + avatarable.getAvatarName());
        }
        AvatarWorkerTask.loadAvatar(avatarable, view.avatar, R.dimen.avatar_big);
        if (changed) {
            MujiLog.log(
                    xmppConnectionService.getFilesDir(),
                    "UI avatar loaded drawable=" + (view.avatar.getDrawable() != null));
        }
    }

    private void showAvatar(final ParticipantView view) {
        final boolean wasVisible = view.avatar.getVisibility() == View.VISIBLE;
        view.avatar.setVisibility(View.VISIBLE);
        view.renderer.setVisibility(View.GONE);
        view.updateSpeakingIndicator();
        if (!wasVisible) {
            MujiLog.log(
                    xmppConnectionService.getFilesDir(),
                    "UI show avatar drawable="
                            + (view.avatar.getDrawable() != null)
                            + " size="
                            + view.avatar.getWidth()
                            + "x"
                            + view.avatar.getHeight());
        }
    }

    private void showRenderer(final ParticipantView view) {
        view.avatar.setVisibility(View.GONE);
        view.renderer.setVisibility(View.VISIBLE);
        view.updateSpeakingIndicator();
    }

    private void removeView(final String key) {
        final ParticipantView view = views.remove(key);
        if (view == null) {
            return;
        }
        if (container.getExpandedChild() == view.root) {
            container.setExpandedChild(null);
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
        final TextureViewRenderer renderer;
        final ShapeableImageView avatar;
        final SpeakingBorderView speakingBorder;
        final TextView label;
        @Nullable VideoTrack boundTrack;
        @Nullable VideoSink frameLogger;
        @Nullable Object boundAvatarable;
        @Nullable ValueAnimator pulse;
        @Nullable ValueAnimator borderPulse;
        long lastSpeaking = 0L;
        boolean speaking = false;
        /** System.currentTimeMillis of the last WebRTC frame received on the bound track. 0 = no
         *  frame received yet (don't treat as stale during initial connection setup). */
        long lastFrameAt;
        /** System.currentTimeMillis when the current track was (re)bound; starts the first-frame
         *  grace period so a track that never renders falls back to the avatar. */
        long boundAt;
        /** System.currentTimeMillis since when the incoming frames have been continuously black;
         *  0 = the last sampled frame was not black. Detects a remote turning its camera off. */
        volatile long blackFrameSince = 0L;
        private boolean rendererInitialized = false;
        private boolean thumbnailStyle = false;

        ParticipantView(
                final View root,
                final TextureViewRenderer renderer,
                final ShapeableImageView avatar,
                final SpeakingBorderView speakingBorder,
                final TextView label) {
            this.root = root;
            this.renderer = renderer;
            this.avatar = avatar;
            this.speakingBorder = speakingBorder;
            this.label = label;
            this.speakingBorder.setBorderColor(SPEAKING_COLOR);
        }

        /**
         * Switches this tile between the small "thumbnail" look used in the left-hand strip of an
         * expanded grid (no tile background, no label, compact floating round avatar) and the
         * regular full tile look (solid background, label, big avatar).
         */
        void setThumbnailStyle(final boolean thumbnail) {
            if (this.thumbnailStyle == thumbnail) {
                return;
            }
            this.thumbnailStyle = thumbnail;
            final float density = root.getResources().getDisplayMetrics().density;
            if (thumbnail) {
                root.setBackgroundColor(Color.TRANSPARENT);
                label.setVisibility(View.GONE);
                final int size = Math.round(48f * density);
                setAvatarSize(size);
                renderer.setCircleDiameterPx(size);
                speakingBorder.setCircleDiameterPx(size);
            } else {
                root.setBackgroundColor(
                        StyledAttributes.getColor(
                                root.getContext(), R.attr.color_background_secondary));
                label.setVisibility(View.VISIBLE);
                setAvatarSize(Math.round(96f * density));
                renderer.setCircleDiameterPx(0);
                speakingBorder.setCircleDiameterPx(0);
            }
        }

        private void setAvatarSize(final int size) {
            final ViewGroup.LayoutParams params = avatar.getLayoutParams();
            if (params.width == size && params.height == size) {
                return;
            }
            params.width = size;
            params.height = size;
            avatar.requestLayout();
        }

        void setSpeaking(final boolean speaking) {
            if (this.speaking == speaking) {
                return;
            }
            this.speaking = speaking;
            updateSpeakingIndicator();
        }

        /**
         * Shows the speaking indicator on whichever surface is currently visible: the pulsing green
         * ring around the avatar, or a pulsing green border around the video renderer. Idempotent -
         * a running animation is not restarted.
         */
        void updateSpeakingIndicator() {
            final boolean videoShown = renderer.getVisibility() == View.VISIBLE;
            if (speaking && videoShown) {
                if (pulse != null) {
                    stopPulse();
                }
                avatar.setStrokeWidth(0f);
                speakingBorder.setVisibility(View.VISIBLE);
                if (borderPulse == null) {
                    startBorderPulse();
                }
            } else if (speaking) {
                if (borderPulse != null) {
                    stopBorderPulse();
                }
                speakingBorder.setVisibility(View.GONE);
                if (pulse == null) {
                    startAvatarPulse();
                }
            } else {
                if (pulse != null) {
                    stopPulse();
                }
                if (borderPulse != null) {
                    stopBorderPulse();
                }
                avatar.setStrokeWidth(0f);
                speakingBorder.setVisibility(View.GONE);
            }
        }

        private void startAvatarPulse() {
            final float density = root.getResources().getDisplayMetrics().density;
            float size = Math.min(avatar.getWidth(), avatar.getHeight());
            if (size <= 0f) {
                size = 96f * density;
            }
            // ring thickness = 5% of the avatar size
            final float maxStroke = 0.05f * size;
            avatar.setStrokeColor(ColorStateList.valueOf(SPEAKING_COLOR));
            avatar.setStrokeWidth(0.6f * maxStroke);
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

        private void startBorderPulse() {
            final ValueAnimator animator = ValueAnimator.ofFloat(0.35f, 1f);
            animator.setDuration(700L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
            animator.addUpdateListener(a -> speakingBorder.setAlpha((float) a.getAnimatedValue()));
            animator.start();
            this.borderPulse = animator;
        }

        private void stopBorderPulse() {
            if (borderPulse != null) {
                borderPulse.cancel();
                borderPulse = null;
            }
            speakingBorder.setAlpha(1f);
        }

        void ensureRendererInitialized(final EglBase.Context eglContext) {
            if (rendererInitialized) {
                return;
            }
            rendererInitialized = true;
            renderer.setVisibility(View.VISIBLE);
            try {
                renderer.init(eglContext);
            } catch (final RuntimeException e) {
                Log.w(Config.LOGTAG, "could not set up participant video renderer", e);
            }
            updateSpeakingIndicator();
        }

        void releaseRenderer() {
            stopPulse();
            stopBorderPulse();
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
