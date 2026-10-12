package eu.siacs.conversations.xmpp.whiteboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.PathParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Drawing surface for the collaborative whiteboard.
 *
 * <p>Every shape is stored as an SVG {@code <path d="…"/>} (local shapes derive the path data, the
 * canvas parses the received {@code d} back via {@link PathParser}), so what is drawn locally is
 * exactly what is sent to the peer. Two-finger gestures pan and zoom the whole board; one finger
 * draws with the active brush.
 */
public class WhiteboardView extends View {

    /** Logical document size (the SVG {@code viewBox}). */
    public static final float VIEW_W = 1280f;
    public static final float VIEW_H = 720f;

    private static final int MAX_POINTS = 4000;

    public interface OnShapeListener {
        void onShapeAdded(WhiteboardShape shape);

        void onShapeUpdated(WhiteboardShape shape);

        void onShapeRemoved(WhiteboardShape shape);
    }

    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<WhiteboardShape> shapes = new ArrayList<>();
    private WhiteboardShape active = null;

    private final Matrix viewMatrix = new Matrix();
    private final float[] tmpPoint = new float[2];

    /** Resting transform (how the whole document is laid out inside the view). */
    private final Matrix baseMatrix = new Matrix();
    /** User pan/zoom on top of {@link #baseMatrix}. */
    private final Matrix userMatrix = new Matrix();

    private final ScaleGestureDetector scaleDetector;

    private float[] activePoints = new float[0];
    private float dragStartX = 0f;
    private float dragStartY = 0f;
    private float lastX = 0f;
    private float lastY = 0f;
    private boolean gestureInProgress = false;

    private String tool = "pencil";
    private String strokeColor = WhiteboardShape.DEFAULT_STROKE;
    private String fillColor = "#e53935";
    private float strokeWidth = 2f;

    @Nullable private OnShapeListener listener;

    public WhiteboardView(final Context context) {
        this(context, null);
    }

    public WhiteboardView(final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        pagePaint.setStyle(Paint.Style.FILL);
        pagePaint.setColor(Color.WHITE);
        gridPaint.setColor(0xFFEEF1F4);
        gridPaint.setStrokeWidth(1f);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        viewMatrix.set(baseMatrix);
    }

    /** The paper colour of the board (follows the app theme). */
    public void setPageColor(final int color) {
        pagePaint.setColor(color);
        invalidate();
    }

    /** The colour of the light helper grid. */
    public void setGridColor(final int color) {
        gridPaint.setColor(color);
        invalidate();
    }

    // ── Options ───────────────────────────────────────────────────────────

    public void setTool(final String tool) {
        this.tool = tool;
    }

    public String getTool() {
        return tool;
    }

    public void setStrokeColor(final String color) {
        this.strokeColor = color;
    }

    public String getStrokeColor() {
        return strokeColor;
    }

    public void setFillColor(final String color) {
        this.fillColor = color;
    }

    public String getFillColor() {
        return fillColor;
    }

    public void setStrokeWidth(final float width) {
        this.strokeWidth = Math.max(1f, width);
    }

    public float getStrokeWidth() {
        return strokeWidth;
    }

    public void setListener(@Nullable final OnShapeListener listener) {
        this.listener = listener;
    }

    public void finishDrawing() {
        commitActive();
        setTool("pan");
        invalidate();
    }

    // ── Data ──────────────────────────────────────────────────────────────

    public List<WhiteboardShape> getShapes() {
        return shapes;
    }

    /** Rebuild the board from remote state (one {@link WhiteboardShape} per remote path). */
    public void loadShapes(final List<WhiteboardShape> remote) {
        shapes.clear();
        if (remote != null) {
            shapes.addAll(remote);
        }
        active = null;
        invalidate();
    }

    public void clearBoard() {
        final List<WhiteboardShape> copy = new ArrayList<>(shapes);
        shapes.clear();
        active = null;
        for (final WhiteboardShape shape : copy) {
            if (listener != null) {
                listener.onShapeRemoved(shape);
            }
        }
        invalidate();
    }

    public void deleteSelected() {
        // Selection is intentionally simple: remove the most recently added shape.
        if (shapes.isEmpty()) {
            return;
        }
        final WhiteboardShape shape = shapes.remove(shapes.size() - 1);
        invalidate();
        if (listener != null) {
            listener.onShapeRemoved(shape);
        }
    }

    public void undo() {
        deleteSelected();
    }

    /** Restores the original pan/zoom so the whole board is visible again. */
    public void resetView() {
        userMatrix.reset();
        recomputeViewMatrix();
    }

    // ── Layout / transform ────────────────────────────────────────────────

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Uniform scale so circles stay circles and the brush has the same thickness in every
        // direction (a non-uniform x/y scale stretched the drawing). "Cover" the viewport and
        // centre it, so the board fills the whole window; the rest is reachable by panning.
        final float scale = Math.max(w / VIEW_W, h / VIEW_H);
        final float dx = (w - VIEW_W * scale) / 2f;
        final float dy = (h - VIEW_H * scale) / 2f;
        baseMatrix.reset();
        baseMatrix.postScale(scale, scale);
        baseMatrix.postTranslate(dx, dy);
        recomputeViewMatrix();
    }

    private void recomputeViewMatrix() {
        viewMatrix.set(baseMatrix);
        viewMatrix.postConcat(userMatrix);
        invalidate();
    }

    private void toWorld(final float viewX, final float viewY) {
        final Matrix inverse = new Matrix();
        if (!viewMatrix.invert(inverse)) {
            tmpPoint[0] = viewX;
            tmpPoint[1] = viewY;
            return;
        }
        tmpPoint[0] = viewX;
        tmpPoint[1] = viewY;
        inverse.mapPoints(tmpPoint);
    }

    // ── Touch ─────────────────────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        final int pointerCount = event.getPointerCount();
        // Two fingers (or the dedicated pan tool) pan/zoom the board; one finger draws.
        final boolean panning = pointerCount > 1 || "pan".equals(tool);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gestureInProgress = false;
                lastX = event.getX();
                lastY = event.getY();
                if ("pan".equals(tool)) {
                    return true;
                }
                beginShape(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                // A second finger means the user wants to pan/zoom, not draw.
                gestureInProgress = true;
                cancelActiveShape();
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (scaleDetector.isInProgress()) {
                    gestureInProgress = true;
                }
                if (panning) {
                    // Pan by the centroid delta (works for one or two fingers).
                    final float cx = centroidX(event);
                    final float cy = centroidY(event);
                    userMatrix.postTranslate(cx - lastX, cy - lastY);
                    lastX = cx;
                    lastY = cy;
                    recomputeViewMatrix();
                    return true;
                }
                extendShape(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                // Dropping back to one finger: re-anchor so the board does not jump.
                lastX = centroidX(event);
                lastY = centroidY(event);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!gestureInProgress) {
                    commitActive();
                }
                gestureInProgress = false;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void cancelActiveShape() {
        if (active != null) {
            active = null;
            activePoints = new float[0];
            invalidate();
        }
    }

    private static float centroidX(final MotionEvent event) {
        float sum = 0f;
        for (int i = 0; i < event.getPointerCount(); i++) {
            sum += event.getX(i);
        }
        return sum / Math.max(1, event.getPointerCount());
    }

    private static float centroidY(final MotionEvent event) {
        float sum = 0f;
        for (int i = 0; i < event.getPointerCount(); i++) {
            sum += event.getY(i);
        }
        return sum / Math.max(1, event.getPointerCount());
    }

    private static float clampX(final float x) {
        return Math.max(0f, Math.min(VIEW_W, x));
    }

    private static float clampY(final float y) {
        return Math.max(0f, Math.min(VIEW_H, y));
    }

    /**
     * Rounds a float array of alternating x/y pairs to integers, so a path built from it can be
     * clipped to the document without hairline gaps from fractional coordinates.
     */
    private static float[] roundPoints(final float[] points) {
        final float[] out = new float[points.length];
        for (int i = 0; i < points.length; i++) {
            out[i] = Math.round(points[i]);
        }
        return out;
    }

    private void beginShape(final float vx, final float vy) {
        toWorld(vx, vy);
        final float x = clampX(tmpPoint[0]);
        final float y = clampY(tmpPoint[1]);
        active = WhiteboardShape.create(tool);
        active.stroke = strokeColor;
        active.strokeWidth = strokeWidth;
        active.fill = fillColor;
        dragStartX = x;
        dragStartY = y;
        lastX = vx;
        lastY = vy;
        switch (tool) {
            case "rect":
                active.rect = new float[]{x, y, 0f, 0f};
                break;
            case "ellipse":
                active.ellipse = new float[]{x, y, 0f, 0f};
                break;
            case "line":
                activePoints = new float[]{x, y, x, y};
                break;
            case "pencil":
            default:
                activePoints = new float[]{x, y};
                break;
        }
        invalidate();
    }

    private void extendShape(final float vx, final float vy) {
        if (active == null) {
            return;
        }
        toWorld(vx, vy);
        final float x = clampX(tmpPoint[0]);
        final float y = clampY(tmpPoint[1]);
        switch (active.kind) {
            case "rect":
                active.rect = new float[]{
                        Math.min(dragStartX, x), Math.min(dragStartY, y),
                        Math.abs(x - dragStartX), Math.abs(y - dragStartY)};
                break;
            case "ellipse":
                active.ellipse = new float[]{
                        (dragStartX + x) / 2f, (dragStartY + y) / 2f,
                        Math.abs(x - dragStartX) / 2f, Math.abs(y - dragStartY) / 2f};
                break;
            case "line":
                activePoints[2] = x;
                activePoints[3] = y;
                break;
            case "pencil":
            default:
                if (activePoints.length / 2 < MAX_POINTS) {
                    final float[] grown = new float[activePoints.length + 2];
                    System.arraycopy(activePoints, 0, grown, 0, activePoints.length);
                    grown[activePoints.length] = x;
                    grown[activePoints.length + 1] = y;
                    activePoints = grown;
                }
                break;
        }
        invalidate();
    }

    private void commitActive() {
        if (active == null) {
            return;
        }
        if ("pencil".equals(active.kind) || "line".equals(active.kind)) {
            if (activePoints.length < 4) {
                active = null;
                activePoints = new float[0];
                return;
            }
            active.points = roundPoints(activePoints);
        }
        if (active.isRemote()) {
            active = null;
            return;
        }
        shapes.add(active);
        final WhiteboardShape committed = active;
        active = null;
        activePoints = new float[0];
        invalidate();
        if (listener != null) {
            listener.onShapeAdded(committed);
        }
    }

    /** Called after a shape was added locally and the session assigned it an SXE record id. */
    public void setShapeRid(final WhiteboardShape shape, final String rid) {
        if (shape != null) {
            shape.rid = rid == null ? "" : rid;
        }
    }

    private final class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(final ScaleGestureDetector detector) {
            final float factor = detector.getScaleFactor();
            userMatrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
            recomputeViewMatrix();
            return true;
        }
    }

    // ── Drawing ───────────────────────────────────────────────────────────

    @Override
    protected void onDraw(@NonNull final Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.concat(viewMatrix);
        canvas.clipRect(0, 0, VIEW_W, VIEW_H);
        canvas.drawRect(0, 0, VIEW_W, VIEW_H, pagePaint);
        canvas.drawRect(0, 0, VIEW_W, VIEW_H, gridPaint);
        for (final WhiteboardShape shape : shapes) {
            drawShape(canvas, shape);
        }
        if (active != null) {
            drawShape(canvas, active);
        }
        canvas.restore();
    }

    private void drawShape(final Canvas canvas, final WhiteboardShape shape) {
        Path path = shapePath(shape);
        if (path == null) {
            return;
        }
        if (shape.isFilled()) {
            fillPaint.setColor(parseColor(shape.fill, Color.TRANSPARENT));
            canvas.drawPath(path, fillPaint);
        }
        strokePaint.setColor(parseColor(shape.stroke, Color.BLACK));
        strokePaint.setStrokeWidth(shape.strokeWidth);
        canvas.drawPath(path, strokePaint);
    }

    @Nullable
    private static Path pathForData(final String d) {
        if (d == null || d.isEmpty()) {
            return null;
        }
        try {
            return PathParser.createPathFromPathData(d);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static Path shapePath(final WhiteboardShape shape) {
        if (shape.kind == null) {
            return null;
        }
        if (shape.isRemote()) {
            return pathForData(shape.d);
        }
        return pathForData(shape.pathData());
    }

    private static int parseColor(final String value, final int fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Color.parseColor(value);
        } catch (final IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Compute the bounding rect of every shape (used to export/save if needed). */
    public RectF contentBounds() {
        final RectF bounds = new RectF();
        for (final WhiteboardShape shape : shapes) {
            final Path path = shapePath(shape);
            if (path == null) {
                continue;
            }
            final RectF rect = new RectF();
            path.computeBounds(rect, true);
            bounds.union(rect);
        }
        return bounds;
    }
}
