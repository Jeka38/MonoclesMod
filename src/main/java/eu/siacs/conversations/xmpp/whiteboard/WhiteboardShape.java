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
    /** pencil | line | arrow | rect | ellipse | remote | text */
    public String kind = "pencil";
    public float[] points = new float[0]; // x0,y0,x1,y1,… for pencil/line
    public float[] rect = new float[4];   // x,y,w,h
    public float[] ellipse = new float[4]; // cx,cy,rx,ry
    public String d = "";
    public String stroke = DEFAULT_STROKE;
    public float strokeWidth = 2f;
    public String fill = "";
    /**
     * The SVG {@code fill-opacity} attribute of a remote shape (1 = opaque). Qt's SVG generator —
     * which Psi uses for its whiteboard — writes a <em>solid transparent</em> brush as
     * {@code fill="#000000" fill-opacity="0"}, so the colour alone says nothing: a pencil stroke has
     * a black fill colour with zero opacity. Without this the whole freehand line was painted as a
     * black blob ("рисунок заполняется цветом").
     */
    public float fillOpacity = 1f;
    /** The SVG {@code transform} attribute (e.g. {@code matrix(a b c d e f)}), or "" if identity. */
    public String transform = "";
    /**
     * For a remote shape, the original SVG element name ({@code path}, {@code polygon}, …), so a
     * later edit does not add a {@code d} attribute to a shape that has none (e.g. a {@code polygon}
     * carries {@code points}, and SVG has no {@code d} on it). Empty for locally created shapes.
     */
    public String remoteElement = "";

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
            case "text":
                return d == null ? "" : d;
            case "pencil":
            case "line":
                return polyline(points, false);
            case "arrow":
                return arrowPath(points, strokeWidth);
            case "rect":
                return rectPath(rect);
            case "ellipse":
                return ellipsePath(ellipse);
            default:
                return "";
        }
    }

    public boolean isFilled() {
        if (fill == null || fill.isEmpty() || isTransparentFill(fill)) {
            return false;
        }
        // A remote shape carries its fill verbatim, but the colour is only half the story: Qt/Psi send
        // a solid *transparent* brush as `fill="#000000" fill-opacity="0"`, i.e. a black fill colour
        // with zero opacity. Treating that as filled painted every pencil stroke as a black blob.
        if (fillOpacity <= 0f) {
            return false;
        }
        // Text is glyph outlines and is always filled.
        return "rect".equals(kind) || "ellipse".equals(kind) || "text".equals(kind) || isRemote();
    }

    /** {@code fill="none"}, {@code fill="transparent"} and paint-server references are not fills. */
    private static boolean isTransparentFill(final String value) {
        final String v = value.trim();
        return "none".equalsIgnoreCase(v)
                || "transparent".equalsIgnoreCase(v)
                || v.startsWith("url(");
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

    /**
     * An arrow: a line from p0 to p1 plus a two-stroke arrowhead at p1. The head size scales with the
     * stroke width so the arrow looks consistent at any thickness.
     */
    private static String arrowPath(final float[] points, final float strokeWidth) {
        if (points == null || points.length < 4) {
            return "";
        }
        final float x0 = points[0], y0 = points[1], x1 = points[2], y1 = points[3];
        final double angle = Math.atan2(y1 - y0, x1 - x0);
        final float head = Math.max(10f, strokeWidth * 4f);
        final double spread = Math.toRadians(28);
        final float hx1 = (float) (x1 - head * Math.cos(angle - spread));
        final float hy1 = (float) (y1 - head * Math.sin(angle - spread));
        final float hx2 = (float) (x1 - head * Math.cos(angle + spread));
        final float hy2 = (float) (y1 - head * Math.sin(angle + spread));
        return fmt("M %s %s L %s %s M %s %s L %s %s M %s %s L %s %s",
                x0, y0, x1, y1, hx1, hy1, x1, y1, hx2, hy2, x1, y1);
    }

    private static String rectPath(final float[] r) {        final float x = r[0], y = r[1], w = r[2], h = r[3];
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
            final String stroke, final String strokeWidth, final String fill,
            final String transform) {
        return fromRemote(rid, id, d, stroke, strokeWidth, fill, null, transform, "path");
    }

    /** As {@link #fromRemote} but remembers the original SVG element name (see {@link #remoteElement}). */
    public static WhiteboardShape fromRemote(
            final String rid, final String id, final String d,
            final String stroke, final String strokeWidth, final String fill,
            final String transform, @Nullable final String element) {
        return fromRemote(rid, id, d, stroke, strokeWidth, fill, null, transform, element);
    }

    /**
     * The full form, which also keeps the peer's {@code fill-opacity}: a client may send an opaque
     * fill colour with zero opacity (Qt's SVG generator does exactly that for a transparent brush,
     * which is how Psi's pencil draws), and only the two values together describe a fill.
     */
    public static WhiteboardShape fromRemote(
            final String rid, final String id, final String d,
            final String stroke, final String strokeWidth, final String fill,
            @Nullable final String fillOpacity, final String transform,
            @Nullable final String element) {
        final WhiteboardShape shape = new WhiteboardShape();
        shape.kind = "remote";
        shape.remoteElement = element == null ? "" : element;
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
        shape.fillOpacity = parseOpacity(fillOpacity);
        shape.transform = transform == null ? "" : transform;
        return shape;
    }

    private static float parseOpacity(@Nullable final String value) {
        if (value == null || value.isEmpty()) {
            return 1f;
        }
        try {
            return Math.max(0f, Math.min(1f, Float.parseFloat(value.trim())));
        } catch (final NumberFormatException e) {
            return 1f;
        }
    }


    /** The SVG attributes of this shape for a collaborative edit. */
    public List<String[]> attrs() {
        final List<String[]> out = new ArrayList<>();
        out.add(new String[]{"id", id});
        // A remote shape keeps the element the peer sent: SVG has no `d` on a <polygon>/<circle>/…,
        // so only a <path> gets one. Otherwise we would push an invalid attribute the peer's parser
        // has to reject.
        if (!isRemote() || remoteElement.isEmpty() || "path".equals(remoteElement)) {
            out.add(new String[]{"d", pathData()});
        }
        out.add(new String[]{"stroke", stroke});
        out.add(new String[]{"stroke-width", trimFloat(strokeWidth)});
        out.add(new String[]{"fill", isFilled() ? fill : "none"});
        // Only a translucent fill needs the attribute: an opaque fill (the normal case, including
        // everything this client draws) must not gain a new record on the peer.
        if (fillOpacity < 1f) {
            out.add(new String[]{"fill-opacity", trimFloat(fillOpacity)});
        }
        // A transform (move/rotate/scale from another client) must round-trip; empty = identity.
        out.add(new String[]{"transform", transform == null ? "" : transform});
        return out;
    }

    public static String trimFloat(final float value) {
        if (value == Math.floor(value)) {
            return Integer.toString((int) value);
        }
        return Float.toString(value);
    }
}
