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

    /** Matches an SVG {@code matrix(a b c d e f)} transform (the form used for move/rotate/scale). */
    private static final java.util.regex.Pattern MATRIX_RE = java.util.regex.Pattern.compile(
            "matrix\\(\\s*([-\\d.eE+]+)[\\s,]+([-\\d.eE+]+)[\\s,]+([-\\d.eE+]+)[\\s,]+"
                    + "([-\\d.eE+]+)[\\s,]+([-\\d.eE+]+)[\\s,]+([-\\d.eE+]+)\\s*\\)");

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

    /** The selected shape (select/move/rotate/scale tools) and its handles. */
    @Nullable private WhiteboardShape selected = null;
    private boolean transforming = false;

    @Nullable private OnShapeListener listener;

    /** Called by the activity when the "text" tool is tapped at a point (to open an input dialog). */
    public interface OnTextRequested {
        void onTextRequested(float worldX, float worldY);
    }

    @Nullable private OnTextRequested textRequested;

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

    public void setTextRequestedListener(@Nullable final OnTextRequested listener) {
        this.textRequested = listener;
    }

    /** The currently selected shape, or null. */
    @Nullable
    public WhiteboardShape getSelected() {
        return selected;
    }

    public void clearSelection() {
        selected = null;
        invalidate();
    }

    /**
     * Adds text at a world position as a filled glyph-outline path (font size in document units), so
     * it serialises as a plain {@code <path d="…"/>} and syncs/renders like any other shape.
     */
    public WhiteboardShape addText(final float worldX, final float worldY, final String text, final float fontSize) {
        final String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(Math.max(4f, fontSize));
        // Convert each line to outlines so the glyphs travel as path data (vector, scalable, and
        // identical on every client regardless of fonts).
        final android.graphics.Path path = new android.graphics.Path();
        final Paint.FontMetrics fm = paint.getFontMetrics();
        final float lineSpacing = fm.descent - fm.ascent + fm.leading;
        final String[] lines = trimmed.split("\n", -1);
        float y = 0f;
        for (final String line : lines) {
            final android.graphics.Path linePath = new android.graphics.Path();
            paint.getTextPath(line, 0, line.length(), 0f, -fm.ascent, linePath);
            linePath.transform(matrixTranslate(0f, y));
            path.addPath(linePath);
            y += lineSpacing;
        }
        final String d = pathToData(path);
        if (d.isEmpty()) {
            return null;
        }
        final WhiteboardShape shape = WhiteboardShape.create("text");
        shape.d = d;
        shape.stroke = strokeColor;
        shape.strokeWidth = strokeWidth;
        shape.fill = strokeColor; // text colour follows the line colour
        // Position via a translate transform so it moves/scales like any other object.
        shape.transform = translateMatrix(worldX, worldY);
        shapes.add(shape);
        selected = shape;
        invalidate();
        if (listener != null) {
            listener.onShapeAdded(shape);
        }
        return shape;
    }

    private static Matrix matrixTranslate(final float dx, final float dy) {
        final Matrix m = new Matrix();
        m.setTranslate(dx, dy);
        return m;
    }

    /**
     * Serialise a {@link Path} to SVG path data (one {@code M…L…Z} per contour). Android's Path has
     * no public element iterator, so each contour is flattened into short line segments via
     * {@link android.graphics.PathMeasure}; iterating {@code nextContour()} preserves the separate
     * outlines (and their winding) so filled glyphs keep their holes.
     */
    private static String pathToData(final android.graphics.Path path) {
        final android.graphics.PathMeasure measure = new android.graphics.PathMeasure(path, false);
        final float[] pos = new float[2];
        final float step = 1.5f;
        final StringBuilder sb = new StringBuilder();
        boolean any = false;
        do {
            final float length = measure.getLength();
            if (length <= 0f) {
                continue;
            }
            any = true;
            measure.getPosTan(0f, pos, null);
            sb.append(fmtPoint("M", pos[0], pos[1]));
            for (float t = step; t < length; t += step) {
                measure.getPosTan(t, pos, null);
                sb.append(fmtPoint("L", pos[0], pos[1]));
            }
            measure.getPosTan(length, pos, null);
            sb.append(fmtPoint("L", pos[0], pos[1]));
            sb.append(" Z");
        } while (measure.nextContour());
        return any ? sb.toString().trim() : "";
    }

    private static String fmtPoint(final String cmd, final float x, final float y) {
        return String.format(java.util.Locale.US, "%s %.1f %.1f ", cmd, x, y);
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
        // The old shape objects are gone; a stale selection would mutate an orphan and render nothing.
        selected = null;
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
        // Remove the selected shape if any, otherwise the most recently added shape.
        final WhiteboardShape shape;
        if (selected != null && shapes.remove(selected)) {
            shape = selected;
            selected = null;
        } else if (!shapes.isEmpty()) {
            shape = shapes.remove(shapes.size() - 1);
        } else {
            return;
        }
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
        // move/scale operate on an object with one finger; a second finger still pans/zooms.
        final boolean objectTool = "move".equals(tool) || "scale".equals(tool);
        if (objectTool) {
            return onObjectTouch(event);
        }
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
                if ("text".equals(tool)) {
                    toWorld(event.getX(), event.getY());
                    if (textRequested != null) {
                        textRequested.onTextRequested(tmpPoint[0], tmpPoint[1]);
                    }
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

    // ── Object manipulation (select / move / scale) ───────────────────────

    private boolean onObjectTouch(final MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (event.getPointerCount() > 1 || scaleDetector.isInProgress()) {
            // A second finger pans/zooms the view.
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                final float cx = centroidX(event);
                final float cy = centroidY(event);
                userMatrix.postTranslate(cx - lastX, cy - lastY);
                lastX = cx;
                lastY = cy;
                recomputeViewMatrix();
            } else if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                lastX = event.getX();
                lastY = event.getY();
            }
            gestureInProgress = true;
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                toWorld(event.getX(), event.getY());
                final float x = tmpPoint[0];
                final float y = tmpPoint[1];
                lastX = event.getX();
                lastY = event.getY();
                // scale: grabbing the bottom-right handle of the selected shape starts a scale.
                if ("scale".equals(tool) && selected != null && insideScaleHandle(selected, x, y)) {
                    transforming = true;
                    setAnchor(x, y);
                    return true;
                }
                final WhiteboardShape hit = shapeAt(x, y);
                if (hit != null) {
                    selected = hit;
                } else if (selected == null) {
                    // Nothing under the finger: grab the nearest shape so a slightly imprecise tap
                    // still works on a phone.
                    selected = nearestShape(x, y);
                }
                lastWorldX = x;
                lastWorldY = y;
                // Once something is selected, dragging anywhere moves/scales it (precise grabbing is
                // unrealistic on a phone); a plain tap just selects.
                transforming = selected != null;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (selected == null) {
                    return true;
                }
                toWorld(event.getX(), event.getY());
                final float x = tmpPoint[0];
                final float y = tmpPoint[1];
                if (transforming) {
                    if ("scale".equals(tool)) {
                        applyScale(selected, x, y);
                    } else {
                        applyTranslate(selected, x - lastWorldX, y - lastWorldY);
                    }
                }
                lastWorldX = x;
                lastWorldY = y;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (transforming && selected != null) {
                    transforming = false;
                    if (listener != null) {
                        listener.onShapeUpdated(selected);
                    }
                }
                gestureInProgress = false;
                return true;
            }
            default:
                return true;
        }
    }

    private float lastWorldX = 0f;
    private float lastWorldY = 0f;

    /** The topmost shape whose (transformed) outline is near the point, or null. */
    @Nullable
    private WhiteboardShape shapeAt(final float x, final float y) {
        for (int i = shapes.size() - 1; i >= 0; i--) {
            final WhiteboardShape shape = shapes.get(i);
            if (hitTest(shape, x, y)) {
                return shape;
            }
        }
        return null;
    }

    /** The shape whose bounding box is closest to the point (within a generous radius), or null. */
    @Nullable
    private WhiteboardShape nearestShape(final float x, final float y) {
        WhiteboardShape best = null;
        float bestDistance = NEAREST_WORLD;
        for (int i = shapes.size() - 1; i >= 0; i--) {
            final WhiteboardShape shape = shapes.get(i);
            final RectF b = boundsOf(shape);
            if (b == null) {
                continue;
            }
            final float dx = Math.max(Math.max(b.left - x, 0f), x - b.right);
            final float dy = Math.max(Math.max(b.top - y, 0f), y - b.bottom);
            final float distance = (float) Math.hypot(dx, dy);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = shape;
            }
        }
        return best;
    }

    private static final float NEAREST_WORLD = 60f;

    private boolean hitTest(final WhiteboardShape shape, final float x, final float y) {
        final Path path = worldPath(shape);
        if (path == null) {
            return false;
        }
        // Stroke hit-test: widen the path so thin lines remain grabbable.
        final RectF bounds = new RectF();
        path.computeBounds(bounds, true);
        bounds.inset(-HIT_SLOP_WORLD, -HIT_SLOP_WORLD);
        if (!bounds.contains(x, y)) {
            return false;
        }
        // Filled shapes (rect/ellipse/text) can be grabbed anywhere inside their box, not just on
        // the outline — much easier to tap on a phone.
        if (shape.isFilled()) {
            final RectF box = new RectF();
            path.computeBounds(box, true);
            if (box.contains(x, y)) {
                return true;
            }
        }
        final android.graphics.PathMeasure m = new android.graphics.PathMeasure(path, false);
        final float[] pos = new float[2];
        final float len = m.getLength();
        for (float t = 0; t <= len; t += 3f) {
            m.getPosTan(t, pos, null);
            if (Math.hypot(pos[0] - x, pos[1] - y) <= HIT_SLOP_WORLD) {
                return true;
            }
        }
        return false;
    }

    private static final float HIT_SLOP_WORLD = 12f;
    private static final float SCALE_HANDLE_WORLD = 22f;

    private boolean insideScaleHandle(final WhiteboardShape shape, final float x, final float y) {
        final RectF b = boundsOf(shape);
        return b != null && Math.hypot(x - b.right, y - b.bottom) <= SCALE_HANDLE_WORLD;
    }

    private RectF anchorBounds = new RectF();
    private float anchorX = 0f;
    private float anchorY = 0f;
    private float scaleStartDistance = 0f;

    private void setAnchor(final float x, final float y) {
        final RectF b = boundsOf(selected);
        if (b != null) {
            anchorBounds.set(b);
        }
        anchorX = x;
        anchorY = y;
        scaleStartDistance = (float) Math.hypot(x - anchorBounds.left, y - anchorBounds.top);
        if (scaleStartDistance < 1f) {
            scaleStartDistance = 1f;
        }
    }

    private void applyTranslate(final WhiteboardShape shape, final float dx, final float dy) {
        shape.transform = concatMatrix(shape.transform, translateMatrix(dx, dy));
    }

    private void applyScale(final WhiteboardShape shape, final float x, final float y) {
        final float distance = (float) Math.hypot(x - anchorBounds.left, y - anchorBounds.top);
        final float factor = Math.max(0.05f, distance / scaleStartDistance);
        // Scale about the shape's top-left so dragging the bottom-right handle grows it.
        final Matrix m = new Matrix();
        m.setScale(factor, factor, anchorBounds.left, anchorBounds.top);
        shape.transform = concatMatrix(shape.transform, matrixToString(m));
        // Reset the reference so the next move scales incrementally from here.
        anchorBounds.set(boundsOf(shape));
        scaleStartDistance = Math.max(1f,
                (float) Math.hypot(x - anchorBounds.left, y - anchorBounds.top));
    }

    private static String translateMatrix(final float dx, final float dy) {
        return String.format(java.util.Locale.US, "matrix(1 0 0 1 %.2f %.2f)", dx, dy);
    }

    /** Multiplies two SVG matrix strings (a then b), falling back to b when a is empty. */
    private static String concatMatrix(final String a, final String b) {
        if (a == null || a.isEmpty()) {
            return b;
        }
        final Matrix ma = parseTransform(a);
        final Matrix mb = parseTransform(b);
        if (ma == null) {
            return b;
        }
        if (mb == null) {
            return a;
        }
        ma.postConcat(mb);
        return matrixToString(ma);
    }

    private static String matrixToString(final Matrix m) {
        final float[] v = new float[9];
        m.getValues(v);
        if (m.isIdentity()) {
            return "";
        }
        return String.format(java.util.Locale.US,
                "matrix(%s %s %s %s %s %s)",
                trimFloat(v[Matrix.MSCALE_X]), trimFloat(v[Matrix.MSKEW_Y]),
                trimFloat(v[Matrix.MSKEW_X]), trimFloat(v[Matrix.MSCALE_Y]),
                trimFloat(v[Matrix.MTRANS_X]), trimFloat(v[Matrix.MTRANS_Y]));
    }

    private static String trimFloat(final float value) {
        return WhiteboardShape.trimFloat(value);
    }

    /** The shape path with its local transform applied (world coordinates). */
    @Nullable
    private Path worldPath(final WhiteboardShape shape) {
        final Path path = shapePath(shape);
        if (path == null) {
            return null;
        }
        final Matrix t = parseTransform(shape.transform);
        if (t != null) {
            path.transform(t);
        }
        return path;
    }

    @Nullable
    private RectF boundsOf(final WhiteboardShape shape) {
        final Path path = worldPath(shape);
        if (path == null) {
            return null;
        }
        final RectF r = new RectF();
        path.computeBounds(r, true);
        return r;
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
            case "arrow":
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
            case "arrow":
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
        if ("pencil".equals(active.kind) || "line".equals(active.kind) || "arrow".equals(active.kind)) {
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
        if (selected != null) {
            drawSelection(canvas, selected);
        }
        canvas.restore();
    }

    /** Dashed bounding box plus a bottom-right scale handle for the selected shape. */
    private void drawSelection(final Canvas canvas, final WhiteboardShape shape) {
        final RectF b = boundsOf(shape);
        if (b == null) {
            return;
        }
        final Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
        box.setStyle(Paint.Style.STROKE);
        box.setColor(0xFF2196F3);
        box.setStrokeWidth(2f);
        canvas.drawRect(b, box);
        final Paint handle = new Paint(Paint.ANTI_ALIAS_FLAG);
        handle.setStyle(Paint.Style.FILL);
        handle.setColor(0xFF2196F3);
        canvas.drawCircle(b.right, b.bottom, SCALE_HANDLE_WORLD / 2f, handle);
    }

    private void drawShape(final Canvas canvas, final WhiteboardShape shape) {
        final Path path = shapePath(shape);
        if (path == null) {
            return;
        }
        final Matrix transform = parseTransform(shape.transform);
        final int save = transform == null ? -1 : canvas.save();
        if (transform != null) {
            canvas.concat(transform);
        }
        if (shape.isFilled()) {
            fillPaint.setColor(parseColor(shape.fill, Color.TRANSPARENT));
            canvas.drawPath(path, fillPaint);
        }
        strokePaint.setColor(parseColor(shape.stroke, Color.BLACK));
        strokePaint.setStrokeWidth(shape.strokeWidth);
        canvas.drawPath(path, strokePaint);
        if (transform != null) {
            canvas.restoreToCount(save);
        }
    }

    /**
     * Parses an SVG {@code transform="matrix(a b c d e f)"} attribute (the form another client sends
     * for a move/rotate/scale) into a {@link Matrix}, or null for an empty/identity/unsupported value.
     */
    @Nullable
    private static Matrix parseTransform(final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        final java.util.regex.Matcher m = MATRIX_RE.matcher(value);
        if (!m.find()) {
            return null;
        }
        try {
            final Matrix matrix = new Matrix();
            matrix.setValues(new float[]{
                    Float.parseFloat(m.group(1)), Float.parseFloat(m.group(3)), Float.parseFloat(m.group(5)),
                    Float.parseFloat(m.group(2)), Float.parseFloat(m.group(4)), Float.parseFloat(m.group(6)),
                    0f, 0f, 1f});
            return matrix.isIdentity() ? null : matrix;
        } catch (final RuntimeException e) {
            return null;
        }
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
