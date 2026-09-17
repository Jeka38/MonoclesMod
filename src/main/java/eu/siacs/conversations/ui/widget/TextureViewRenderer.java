package eu.siacs.conversations.ui.widget;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.Nullable;

import org.webrtc.EglBase;
import org.webrtc.EglRenderer;
import org.webrtc.GlRectDrawer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import eu.siacs.conversations.Config;

/**
 * Minimal {@link TextureView} based WebRTC video renderer.
 *
 * <p>Unlike {@link org.webrtc.SurfaceViewRenderer} (a {@link android.view.SurfaceView}) this renders
 * through the regular view hierarchy, so the output can be clipped to arbitrary shapes
 * ({@link #setCircleDiameterPx(int)}), ordered with the other views via elevation/translationZ and
 * animated like any other view. The video is always drawn center-cropped to fill the view
 * (SCALE_ASPECT_FILL behaviour), which is what the Muji participant tiles expect.
 */
public class TextureViewRenderer extends TextureView
        implements TextureView.SurfaceTextureListener, VideoSink {

    private final EglRenderer eglRenderer = new EglRenderer("MujiTextureViewRenderer");
    private final ViewOutlineProvider outlineProvider =
            new ViewOutlineProvider() {
                @Override
                public void getOutline(final View view, final Outline outline) {
                    final int width = view.getWidth();
                    final int height = view.getHeight();
                    if (circleDiameterPx > 0) {
                        final int radius =
                                Math.min(circleDiameterPx / 2, Math.min(width, height) / 2);
                        outline.setOval(
                                width / 2 - radius,
                                height / 2 - radius,
                                width / 2 + radius,
                                height / 2 + radius);
                    } else {
                        outline.setRect(0, 0, width, height);
                    }
                }
            };

    private boolean initialized = false;
    private int circleDiameterPx = 0;
    private int bufferWidth = 0;
    private int bufferHeight = 0;
    @Nullable private SurfaceTexture pendingSurface;

    public TextureViewRenderer(final Context context) {
        this(context, null);
    }

    public TextureViewRenderer(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setSurfaceTextureListener(this);
        setOpaque(false);
        setOutlineProvider(outlineProvider);
    }

    /** Initialises the underlying EGL renderer. Safe to call more than once. */
    public void init(final EglBase.Context eglContext) {
        if (initialized) {
            return;
        }
        initialized = true;
        eglRenderer.init(eglContext, EglBase.CONFIG_PLAIN, new GlRectDrawer());
        final SurfaceTexture surface = pendingSurface;
        pendingSurface = null;
        if (surface != null) {
            createEglSurface(surface);
        }
    }

    /**
     * Clips the rendered video to a {@code diameter}px circle centred in the view (e.g. to render a
     * circular thumbnail). A value {@code <= 0} restores the regular rectangular tile.
     */
    public void setCircleDiameterPx(final int diameter) {
        final int safe = Math.max(0, diameter);
        if (this.circleDiameterPx == safe) {
            return;
        }
        this.circleDiameterPx = safe;
        setClipToOutline(safe > 0);
        invalidateOutline();
    }

    public boolean isCircular() {
        return circleDiameterPx > 0;
    }

    public void setMirror(final boolean mirror) {
        eglRenderer.setMirror(mirror);
    }

    @Override
    public void onFrame(final VideoFrame frame) {
        if (!initialized) {
            frame.release();
            return;
        }
        eglRenderer.onFrame(frame);
    }

    public void release() {
        if (!initialized) {
            return;
        }
        initialized = false;
        pendingSurface = null;
        eglRenderer.release();
    }

    @Override
    protected void onLayout(final boolean changed, final int left, final int top, final int right, final int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        final int width = right - left;
        final int height = bottom - top;
        if (width <= 0 || height <= 0) {
            return;
        }
        // Fill (center-crop) the whole view regardless of the frame's aspect ratio.
        eglRenderer.setLayoutAspectRatio((float) width / height);
        setBufferSize(width, height);
    }

    private void setBufferSize(final int width, final int height) {
        final SurfaceTexture surface = getSurfaceTexture();
        if (surface == null || (width == bufferWidth && height == bufferHeight)) {
            return;
        }
        bufferWidth = width;
        bufferHeight = height;
        surface.setDefaultBufferSize(width, height);
    }

    private void createEglSurface(final SurfaceTexture surface) {
        setBufferSize(Math.max(1, getWidth()), Math.max(1, getHeight()));
        eglRenderer.createEglSurface(surface);
    }

    @Override
    public void onSurfaceTextureAvailable(final SurfaceTexture surface, final int width, final int height) {
        if (!initialized) {
            pendingSurface = surface;
            return;
        }
        createEglSurface(surface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(final SurfaceTexture surface, final int width, final int height) {
        // The layout always drives the buffer size; nothing to do here.
    }

    @Override
    public boolean onSurfaceTextureDestroyed(final SurfaceTexture surface) {
        pendingSurface = null;
        bufferWidth = 0;
        bufferHeight = 0;
        if (!initialized) {
            return true;
        }
        final CountDownLatch latch = new CountDownLatch(1);
        eglRenderer.releaseEglSurface(latch::countDown);
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                Log.w(Config.LOGTAG, "timed out releasing Muji video surface");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(final SurfaceTexture surface) {
        // no-op
    }
}
