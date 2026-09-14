package eu.siacs.conversations.xmpp.jingle;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import eu.siacs.conversations.Config;

/**
 * Shared WebRTC resources for a group of simultaneous {@link org.webrtc.PeerConnection}s.
 *
 * <p>Every {@link WebRTCWrapper} normally creates its own {@link PeerConnectionFactory}, EGL context
 * and audio device. In a multiparty (XEP-0272 Muji) session that would mean one microphone/playout
 * device per participant. This holder creates a single factory, EGL context and microphone track
 * that all peer connections of a conference share. It is reference counted: the conference holds one
 * reference and every wrapper retains/releases one for the lifetime of its peer connection.
 */
public final class WebRTCResources {

    final EglBase eglBase;
    final PeerConnectionFactory peerConnectionFactory;
    final AudioSource audioSource;
    final AudioTrack audioTrack;

    private final Context context;
    private final AtomicInteger refCount = new AtomicInteger(1);
    private boolean disposed = false;
    @Nullable private VideoSourceWrapper videoSourceWrapper;
    @Nullable private VideoTrack videoTrack;

    private WebRTCResources(
            final Context context,
            final EglBase eglBase,
            final PeerConnectionFactory peerConnectionFactory,
            final AudioSource audioSource,
            final AudioTrack audioTrack) {
        this.context = context;
        this.eglBase = eglBase;
        this.peerConnectionFactory = peerConnectionFactory;
        this.audioSource = audioSource;
        this.audioTrack = audioTrack;
    }

    public static WebRTCResources create(final Context context)
            throws WebRTCWrapper.InitializationException {
        try {
            PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                            .setFieldTrials("WebRTC-BindUsingInterfaceName/Enabled/")
                            .createInitializationOptions());
        } catch (final UnsatisfiedLinkError e) {
            throw new WebRTCWrapper.InitializationException(
                    "Unable to initialize PeerConnectionFactory", e);
        }
        final EglBase eglBase;
        try {
            eglBase = EglBase.create();
        } catch (final RuntimeException e) {
            throw new WebRTCWrapper.InitializationException("Unable to create EGL base", e);
        }
        final boolean setUseHardwareAcousticEchoCanceler =
                !WebRTCWrapper.HARDWARE_AEC_BLACKLIST.contains(Build.MODEL);
        final PeerConnectionFactory peerConnectionFactory =
                PeerConnectionFactory.builder()
                        .setVideoDecoderFactory(
                                new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                        .setVideoEncoderFactory(
                                new DefaultVideoEncoderFactory(
                                        eglBase.getEglBaseContext(), true, true))
                        .setAudioDeviceModule(
                                JavaAudioDeviceModule.builder(context)
                                        .setUseHardwareAcousticEchoCanceler(
                                                setUseHardwareAcousticEchoCanceler)
                                        .createAudioDeviceModule())
                        .createPeerConnectionFactory();
        final AudioSource audioSource =
                peerConnectionFactory.createAudioSource(new MediaConstraints());
        final AudioTrack audioTrack =
                peerConnectionFactory.createAudioTrack(
                        TrackWrapper.id(AudioTrack.class), audioSource);
        return new WebRTCResources(
                context, eglBase, peerConnectionFactory, audioSource, audioTrack);
    }

    /**
     * Returns a local camera track shared by every peer connection of the conference, creating it on
     * first use. Returns null if the device has no usable camera.
     */
    @Nullable
    synchronized VideoTrack getOrCreateVideoTrack() {
        if (videoTrack != null) {
            return videoTrack;
        }
        if (videoSourceWrapper == null) {
            try {
                final VideoSourceWrapper wrapper =
                        new VideoSourceWrapper.Factory(context).create();
                if (wrapper == null) {
                    MujiLog.log(context.getFilesDir(), "camera: no capturer available");
                    return null;
                }
                wrapper.initialize(peerConnectionFactory, context, eglBase.getEglBaseContext());
                wrapper.startCapture();
                this.videoSourceWrapper = wrapper;
                MujiLog.log(context.getFilesDir(), "camera: capture started");
            } catch (final RuntimeException e) {
                MujiLog.log(context.getFilesDir(), "camera: error " + e);
                return null;
            }
        }
        this.videoTrack =
                peerConnectionFactory.createVideoTrack(
                        TrackWrapper.id(VideoTrack.class), videoSourceWrapper.getVideoSource());
        return videoTrack;
    }

    boolean isCameraSwitchable() {
        final VideoSourceWrapper wrapper = this.videoSourceWrapper;
        return wrapper != null && wrapper.isCameraSwitchable();
    }

    boolean isFrontCamera() {
        final VideoSourceWrapper wrapper = this.videoSourceWrapper;
        return wrapper == null || wrapper.isFrontCamera();
    }

    ListenableFuture<Boolean> switchCamera() {
        final VideoSourceWrapper wrapper = this.videoSourceWrapper;
        if (wrapper == null) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException("VideoSourceWrapper has not been initialized"));
        }
        return wrapper.switchCamera();
    }

    void retain() {
        refCount.incrementAndGet();
    }

    void release() {
        if (refCount.decrementAndGet() <= 0) {
            dispose();
        }
    }

    private synchronized void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        final VideoSourceWrapper videoSourceWrapper = this.videoSourceWrapper;
        this.videoSourceWrapper = null;
        if (videoSourceWrapper != null) {
            try {
                videoSourceWrapper.stopCapture();
            } catch (final InterruptedException e) {
                Log.w(Config.LOGTAG, "unable to stop shared video capture", e);
            }
            videoSourceWrapper.dispose();
        }
        final VideoTrack videoTrack = this.videoTrack;
        this.videoTrack = null;
        if (videoTrack != null) {
            try {
                videoTrack.dispose();
            } catch (final IllegalStateException e) {
                Log.w(Config.LOGTAG, "unable to dispose shared video track", e);
            }
        }
        try {
            audioTrack.dispose();
        } catch (final IllegalStateException e) {
            Log.w(Config.LOGTAG, "unable to dispose shared audio track", e);
        }
        try {
            audioSource.dispose();
        } catch (final IllegalStateException e) {
            Log.w(Config.LOGTAG, "unable to dispose shared audio source", e);
        }
        peerConnectionFactory.dispose();
        eglBase.release();
    }
}
