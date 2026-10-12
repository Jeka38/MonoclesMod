package eu.siacs.conversations.xmpp.whiteboard;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A single shape on the whiteboard.
 *
 * <p>Every shape is serialised to the shared SVG document as a single {@code <path d="…"/>}; local
 * shapes keep their geometric definition and derive the {@code d} string, remote shapes keep the
 * received {@code d} verbatim (and are rendered by parsing it).
 */
public final class WhiteboardShape {

    public static final String DEFAULT_STROKE = "#1a1a1a";

    public String id;
    /** The SXE record id once the shape has been added to a shared session (may be empty). */
    public String rid = "";
    /** pencil | line | rect | ellipse | remote */
    public String kind = "pencil";
    public float[] points = new float[0]; // x0,y0,x1,y1,… for pencil/line
    public float[] rect = new float[4];   // x,y,w,h
    public float[] ellipse = new float[4]; // cx,cy,rx,ry
    public String d = "";
    public String stroke = DEFAULT_STROKE;
    public float strokeWidth = 2f;
    public String fill = "";

    public static WhiteboardShape create(final String kind) {
        final WhiteboardShape shape = new WhiteboardShape();
        shape.id = UUID.randomUUID().toString().replace("-", "");
        shape.kind = kind;
        return shape;
    }

    public boolean isRemote() {
        return "remote".equals(kind);
    }

    /** The SVG {@code d} attribute for this shape. */
    @NonNull
    public String pathData() {
        switch (kind) {
            case "remote":
                return d == null ? "" : d;
            case "pencil":
            case "line":
                return polyline(points, false);
            case "rect":
                return rectPath(rect);
            case "ellipse":
                return ellipsePath(ellipse);
            default:
                return "";
        }
    }

    public boolean isFilled() {
        if (fill == null || fill.isEmpty() || "none".equals(fill)) {
            return false;
        }
        // Remote shapes carry their fill verbatim, so a non-empty fill means "filled".
        return "rect".equals(kind) || "ellipse".equals(kind) || isRemote();
    }

    private static String polyline(final float[] points, final boolean close) {
        if (points == null || points.length < 2) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(fmt("M %s %s", points[0], points[1]));
        for (int i = 2; i + 1 < points.length; i += 2) {
            sb.append(fmt(" L %s %s", points[i], points[i + 1]));
        }
        if (close) {
            sb.append(" Z");
        }
        return sb.toString();
    }

    private static String rectPath(final float[] r) {
        final float x = r[0], y = r[1], w = r[2], h = r[3];
        return fmt("M %s %s L %s %s L %s %s L %s %s Z", x, y, x + w, y, x + w, y + h, x, y + h);
    }

    private static String ellipsePath(final float[] e) {
        final float cx = e[0], cy = e[1], rx = Math.abs(e[2]), ry = Math.abs(e[3]);
        return fmt(
                "M %s %s A %s %s 0 1 0 %s %s A %s %s 0 1 0 %s %s Z",
                cx - rx, cy, rx, ry, cx + rx, cy, rx, ry, cx - rx, cy);
    }

    private static String fmt(final String pattern, final Object... args) {
        return String.format(Locale.US, pattern, args);
    }

    /** Parse a remote {@code <path>} descriptor (from {@code WhiteboardManager} state). */
    @Nullable
    public static WhiteboardShape fromRemote(
            final String rid, final String id, final String d,
            final String stroke, final String strokeWidth, final String fill) {
        final WhiteboardShape shape = new WhiteboardShape();
        shape.kind = "remote";
        shape.rid = rid == null ? "" : rid;
        shape.id = id == null || id.isEmpty() ? WhiteboardShape.create("remote").id : id;
        shape.d = d == null ? "" : d;
        shape.stroke = stroke == null || stroke.isEmpty() ? DEFAULT_STROKE : stroke;
        try {
            shape.strokeWidth = Float.parseFloat(strokeWidth == null ? "2" : strokeWidth);
        } catch (final NumberFormatException e) {
            shape.strokeWidth = 2f;
        }
        shape.fill = fill == null || "none".equals(fill) ? "" : fill;
        return shape;
    }

    /** The SVG attributes of this shape for a collaborative edit. */
    public List<String[]> attrs() {
        final List<String[]> out = new ArrayList<>();
        out.add(new String[]{"id", id});
        out.add(new String[]{"d", pathData()});
        out.add(new String[]{"stroke", stroke});
        out.add(new String[]{"stroke-width", trimFloat(strokeWidth)});
        out.add(new String[]{"fill", isFilled() ? fill : "none"});
        return out;
    }

    public static String trimFloat(final float value) {
        if (value == Math.floor(value)) {
            return Integer.toString((int) value);
        }
        return Float.toString(value);
    }
}
