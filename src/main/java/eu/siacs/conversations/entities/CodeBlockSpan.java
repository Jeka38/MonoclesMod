package eu.siacs.conversations.entities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineBackgroundSpan;
import android.text.style.LineHeightSpan;
import android.text.style.TypefaceSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

import eu.siacs.conversations.R;
import me.drakeet.support.toast.ToastCompat;

/**
 * A fenced code block, rendered as a rounded card instead of a bare tinted rectangle.
 *
 * <p>The card is drawn by the span itself (the message body is one {@link android.widget.TextView},
 * so there is no view to inflate):
 *
 * <pre>
 * +------------------------------+
 * | code c++                  [] |   header band: small muted label + copy glyph
 * |------------------------------|   hairline divider
 * | #include &lt;iostream&gt;          |   monospaced body on a darker panel
 * | int main() { ... }           |
 * +------------------------------+
 * </pre>
 *
 * <p>Geometry is derived from the paint's text size, so the card scales with the message font. The
 * header row is reserved through {@link LineHeightSpan} on the first line and the bottom padding
 * through it on the last one; {@link LeadingMarginSpan} indents the code from the card's left edge.
 * Tapping the card copies the code ({@link #attach} installs the click span — the span itself is
 * created while the body is rendered, long before there is a {@code Context}).
 *
 * <p>The card always ends at the right edge of the layout, and the layout of a {@code wrap_content}
 * TextView is exactly as wide as its widest line, so a full-width card would leave the longest code
 * line glued to its right border. {@link #attach} therefore appends one invisible non-breaking space
 * to the longest line: the layout grows by one character cell and the card gets the same air on the
 * right as the {@link LeadingMarginSpan} gives on the left.
 */
public class CodeBlockSpan extends TypefaceSpan
        implements LineBackgroundSpan, LineHeightSpan.WithDensity, LeadingMarginSpan {

    /** Scrim over the bubble: the code body is darker than the header, as in the reference card. */
    private static final int BODY_ALPHA = 0x33;
    private static final int HEADER_ALPHA = 0x1A;
    private static final int DIVIDER_ALPHA = 0x33;
    private static final int LABEL_ALPHA = 180;

    private static final float LABEL_RATIO = 0.78f;

    /** One character cell of air on the right of the card (see the class comment). */
    private static final String RIGHT_AIR = "\u00A0";

    @Nullable
    private final String language;
    private final Path path = new Path();
    private final Path iconBack = new Path();
    private final Path iconFront = new Path();
    private final RectF rect = new RectF();

    private boolean rightAirAdded = false;
    @Nullable
    private WeakReference<Context> context;
    @Nullable
    private String code;

    public CodeBlockSpan(@Nullable final String language) {
        super("monospace");
        final String trimmed = language == null ? null : language.trim();
        this.language = trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Installs the tap-to-copy handler on every code block of a rendered body. Called by the adapter
     * once the body is final (before it reaches the {@link android.widget.TextView}), because the
     * spans are created by the entity layer, which has no {@code Context}.
     */
    public static void attach(@Nullable final Spannable body, @Nullable final Context context) {
        if (body == null || context == null) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        for (final CodeBlockSpan span : body.getSpans(0, body.length(), CodeBlockSpan.class)) {
            final int start = body.getSpanStart(span);
            final int end = body.getSpanEnd(span);
            if (end <= start) {
                continue;
            }
            span.context = new WeakReference<>(appContext);
            // Remember the clean code first: the right air is added below.
            span.code = body.subSequence(start, end)
                    .toString()
                    .replaceAll("(?m)[\\s\\u00A0]+$", "");
            if (body.getSpans(start, end, CopyClickSpan.class).length == 0) {
                body.setSpan(
                        new CopyClickSpan(span), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            span.addRightAir(body, start, end);
        }
    }

    /**
     * Makes the layout one character cell wider than the longest code line, so the card drawn to the
     * layout's right edge has the same air on the right as the leading margin gives on the left.
     */
    private void addRightAir(final Spannable body, final int start, final int end) {
        if (rightAirAdded || !(body instanceof SpannableStringBuilder)) {
            return;
        }
        rightAirAdded = true;
        int lineStart = start;
        int longest = -1;
        int longestEnd = -1;
        for (int i = start; i <= end; i++) {
            if (i == end || body.charAt(i) == '\n') {
                final int length = i - lineStart;
                if (length > longest) {
                    longest = length;
                    longestEnd = i;
                }
                lineStart = i + 1;
            }
        }
        if (longest > 0) {
            // Inserted after the span's end: the card covers the layout width, not the span range.
            ((SpannableStringBuilder) body).insert(longestEnd, RIGHT_AIR);
        }
    }

    // ── metrics (all derived from the text size, so the card follows the font scale) ──

    private static float defaultTextSize() {
        return dp(16);
    }

    private static float labelSize(final float textSize) {
        return textSize * LABEL_RATIO;
    }

    private static float headerHeight(final float textSize) {
        return labelSize(textSize) * 1.75f + dp(4);
    }

    private static float bottomPadding(final float textSize) {
        return textSize * 0.42f;
    }

    private static float sidePadding(final float textSize) {
        return textSize * 0.6f;
    }

    private static float radius(final float textSize) {
        return textSize * 0.5f;
    }

    private static float dp(final float value) {
        return value * android.content.res.Resources.getSystem().getDisplayMetrics().density;
    }

    @Override
    public int getLeadingMargin(final boolean first) {
        // The paint is not available here, so use the density; this is the one metric that does not
        // follow the font scale (a 10 dp gutter looks right at every text size anyway).
        return Math.round(dp(10));
    }

    @Override
    public void drawLeadingMargin(
            final Canvas c,
            final Paint p,
            final int x,
            final int dir,
            final int top,
            final int baseline,
            final int bottom,
            final CharSequence text,
            final int start,
            final int end,
            final boolean first,
            final Layout layout) {
        // The margin only makes room for the card's left padding; nothing is drawn in it.
    }

    /** The card's title: {@code code} or {@code code <language>}. */
    @NonNull
    private String title() {
        return language == null ? "code" : "code " + language;
    }

    // ── line height: the header row and the bottom padding live inside the block ──

    @Override
    public void chooseHeight(
            final CharSequence text,
            final int start,
            final int end,
            final int spanstartv,
            final int lineHeight,
            final Paint.FontMetricsInt fm) {
        chooseHeight(text, start, end, spanstartv, lineHeight, fm, null);
    }

    @Override
    public void chooseHeight(
            final CharSequence text,
            final int start,
            final int end,
            final int spanstartv,
            final int lineHeight,
            final Paint.FontMetricsInt fm,
            @Nullable final TextPaint paint) {
        if (fm == null || !(text instanceof Spanned)) {
            return;
        }
        final float textSize = paint == null ? defaultTextSize() : paint.getTextSize();
        final Spanned spanned = (Spanned) text;
        final int spanStart = spanned.getSpanStart(this);
        final int spanEnd = spanned.getSpanEnd(this);
        if (spanStart < 0 || spanEnd <= spanStart) {
            return;
        }
        if (isFirstLine(start, end, spanStart)) {
            // Reserve the header band above the first code line.
            fm.ascent -= Math.round(headerHeight(textSize));
            fm.top = Math.min(fm.top, fm.ascent);
        }
        if (isLastLine(start, end, spanStart, spanEnd)) {
            // Reserve the padding below the last code line so the card is not flush with the text.
            fm.descent += Math.round(bottomPadding(textSize));
            fm.bottom = Math.max(fm.bottom, fm.descent);
        }
    }

    // ── drawing ──

    @Override
    public void drawBackground(
            final Canvas canvas,
            final Paint paint,
            final int left,
            final int right,
            final int top,
            final int baseline,
            final int bottom,
            final CharSequence text,
            final int start,
            final int end,
            final int lineNumber) {
        if (paint == null || !(text instanceof Spanned)) {
            return;
        }
        final Spanned spanned = (Spanned) text;
        final int spanStart = spanned.getSpanStart(this);
        final int spanEnd = spanned.getSpanEnd(this);
        if (spanStart < 0 || spanEnd <= spanStart) {
            return;
        }
        final boolean first = isFirstLine(start, end, spanStart);
        final boolean last = isLastLine(start, end, spanStart, spanEnd);

        // Preserve everything borrowed from the layout's paint: the layout keeps using it after the
        // background pass (the label is drawn at a different size and typeface).
        final int textColor = paint.getColor();
        final int oldAlpha = paint.getAlpha();
        final float oldSize = paint.getTextSize();
        final Typeface oldTypeface = paint.getTypeface();
        final Paint.Style oldStyle = paint.getStyle();
        final float oldStrokeWidth = paint.getStrokeWidth();
        final Paint.Join oldJoin = paint.getStrokeJoin();

        final float textSize = paint.getTextSize();
        final float radius = radius(textSize);
        // Overlap consecutive lines by a pixel so no hairline seam appears between them.
        final float panelTop = first ? top : top - 1f;
        final float panelBottom = last ? bottom : bottom + 1f;
        drawPanel(canvas, paint, left, panelTop, right, panelBottom, first, last, BODY_ALPHA, radius);
        if (first) {
            drawHeader(canvas, paint, left, right, top, radius, textColor);
        }

        paint.setColor(textColor);
        paint.setAlpha(oldAlpha);
        paint.setTextSize(oldSize);
        paint.setTypeface(oldTypeface);
        paint.setStyle(oldStyle);
        paint.setStrokeWidth(oldStrokeWidth);
        paint.setStrokeJoin(oldJoin);
    }

    private void drawPanel(
            final Canvas canvas,
            final Paint paint,
            final float left,
            final float top,
            final float right,
            final float bottom,
            final boolean roundTop,
            final boolean roundBottom,
            final int alpha,
            final float radius) {
        paint.setColor(Color.BLACK);
        paint.setAlpha(alpha);
        paint.setStyle(Paint.Style.FILL);
        if (!roundTop && !roundBottom) {
            canvas.drawRect(left, top, right, bottom, paint);
            return;
        }
        final float r = Math.min(radius, Math.min((right - left) / 2f, (bottom - top) / 2f));
        final float[] radii = new float[]{
                roundTop ? r : 0f, roundTop ? r : 0f,
                roundTop ? r : 0f, roundTop ? r : 0f,
                roundBottom ? r : 0f, roundBottom ? r : 0f,
                roundBottom ? r : 0f, roundBottom ? r : 0f};
        rect.set(left, top, right, bottom);
        path.reset();
        path.addRoundRect(rect, radii, Path.Direction.CW);
        canvas.drawPath(path, paint);
    }

    /** The header band: a lighter scrim, a hairline divider, the muted title and the copy glyph. */
    private void drawHeader(
            final Canvas canvas,
            final Paint paint,
            final float left,
            final float right,
            final float top,
            final float radius,
            final int textColor) {
        final float textSize = paint.getTextSize();
        final float headerHeight = headerHeight(textSize);
        final float labelSize = labelSize(textSize);
        final float padding = sidePadding(textSize);
        final float headerBottom = top + headerHeight;
        drawPanel(canvas, paint, left, top, right, headerBottom, true, false, HEADER_ALPHA, radius);
        // A hairline at the bottom of the band separates the title row from the code.
        final float divider = Math.max(1f, dp(1) * 0.5f);
        drawPanel(
                canvas,
                paint,
                left,
                headerBottom - divider,
                right,
                headerBottom,
                false,
                false,
                DIVIDER_ALPHA,
                radius);

        final float iconSize = labelSize * 1.15f;
        final float iconLeft = right - padding - iconSize;
        // Show the copy glyph only when it does not eat the whole title.
        final boolean showIcon = iconLeft - padding > left + padding + labelSize * 3;
        final float available = (showIcon ? iconLeft - padding : right - padding) - (left + padding);

        paint.setTextSize(labelSize);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(textColor);
        paint.setAlpha(LABEL_ALPHA);
        final CharSequence label = TextUtils.ellipsize(
                title(), new TextPaint(paint), Math.max(0f, available), TextUtils.TruncateAt.END);
        final float baseline = top + headerHeight / 2f + labelSize * 0.36f;
        canvas.drawText(label.toString(), left + padding, baseline, paint);

        if (showIcon) {
            drawCopyIcon(canvas, paint, iconLeft, top + (headerHeight - iconSize) / 2f, iconSize);
        }
        paint.setColor(textColor);
    }

    /** The classic "two overlapping sheets" copy glyph, stroked in the muted label colour. */
    private void drawCopyIcon(
            final Canvas canvas,
            final Paint paint,
            final float x,
            final float y,
            final float size) {
        final float stroke = Math.max(1f, size * 0.09f);
        final float corner = size * 0.16f;
        final float offset = size * 0.26f;
        rect.set(x, y + offset, x + size - offset, y + size);
        iconBack.reset();
        iconBack.addRoundRect(rect, corner, corner, Path.Direction.CW);
        rect.set(x + offset, y, x + size, y + size - offset);
        iconFront.reset();
        iconFront.addRoundRect(rect, corner, corner, Path.Direction.CW);
        // Cut the front sheet out of the back one so the glyph reads as two sheets, then outline both.
        iconBack.op(iconFront, Path.Op.DIFFERENCE);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setAlpha(LABEL_ALPHA);
        canvas.drawPath(iconBack, paint);
        canvas.drawPath(iconFront, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    // ── line/span ranges ──

    /** True for the line that contains the first character of the block. */
    private static boolean isFirstLine(final int start, final int end, final int spanStart) {
        if (start == end) {
            // an empty line: it is ours only if the block starts there
            return start == spanStart;
        }
        return spanStart >= start && spanStart < end;
    }

    /** True for the line that contains the last character of the block. */
    private static boolean isLastLine(
            final int start, final int end, final int spanStart, final int spanEnd) {
        return spanEnd > start && spanEnd <= Math.max(end, start + 1);
    }

    // ── tap to copy ──

    private void copy() {
        final Context context = this.context == null ? null : this.context.get();
        if (context == null || code == null || code.isEmpty()) {
            return;
        }
        final ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("code", code));
        ToastCompat.makeText(context, R.string.code_copied_to_clipboard, ToastCompat.LENGTH_SHORT)
                .show();
    }

    /** Copies the block its span belongs to; draws nothing, so the code keeps its appearance. */
    private static final class CopyClickSpan extends ClickableSpan {

        private final WeakReference<CodeBlockSpan> block;

        CopyClickSpan(final CodeBlockSpan block) {
            this.block = new WeakReference<>(block);
        }

        @Override
        public void updateDrawState(@NonNull final TextPaint ds) {
            // Deliberately nothing: a code block must not turn into a coloured, underlined link.
        }

        @Override
        public void onClick(@NonNull final View widget) {
            final CodeBlockSpan span = block.get();
            if (span != null) {
                span.copy();
            }
        }
    }
}
