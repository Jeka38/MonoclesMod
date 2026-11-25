package eu.siacs.conversations.ui.text;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineBackgroundSpan;
import android.text.style.LineHeightSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ReplacementSpan;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.graphics.Typeface;

import androidx.annotation.Nullable;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.util.StyledAttributes;

/**
 * Renders a XEP-0393 quotation as soft tinted cards with rounded accent bars. Each nesting level
 * adds an indented card, so a quote-in-quote is visible as a card inside the outer card.
 *
 * <p>The body keeps its literal {@code > } markers; only the rendering removes them. The same span
 * is used for messages (created by {@link eu.siacs.conversations.entities.StylingSpan}) and for the
 * reply preview (created by the adapter), so both look identical.
 */
public class QuoteSpan implements LeadingMarginSpan, LineBackgroundSpan, LineHeightSpan.WithDensity {

    /** Flag marking the spans that style the sender/date header line. */
    private static final int HEADER_FLAG = 1 << 28;

    /** Flag marking the zero-width spans that hide the literal {@code > } markers. */
    private static final int MARKER_FLAG = 1 << 29;

    private static final int BACKGROUND_ALPHA = 0x16;

    private final float density;
    private final int depth;
    private final int barWidth;
    private final int paddingLeft;
    private final int paddingRight;
    private final int levelIndent;
    private final int levelRightInset;
    private final float radius;
    private final float verticalPadding;

    private int accentColor;
    private int backgroundColor;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];

    public QuoteSpan(int color, DisplayMetrics metrics) {
        this(color, metrics, 1);
    }

    public QuoteSpan(int color, DisplayMetrics metrics, int depth) {
        this.density = metrics == null ? 1f : metrics.density;
        this.depth = Math.min(Math.max(depth, 1), Config.QUOTE_MAX_DEPTH);
        this.barWidth = dp(3);
        this.paddingLeft = dp(3);
        this.paddingRight = dp(9);
        this.levelIndent = paddingLeft + barWidth + paddingRight;
        this.levelRightInset = dp(5);
        this.radius = dp(8);
        this.verticalPadding = dp(3);
        setColors(color);
    }

    @Override
    public int getLeadingMargin(boolean first) {
        return levelIndent * depth;
    }

    @Override
    public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom,
                                  CharSequence text, int start, int end, boolean first, Layout layout) {
        // The card and its bar are drawn in drawBackground() so the rounded corners line up.
    }

    @Override
    public void drawBackground(
            Canvas canvas,
            Paint paint,
            int left,
            int right,
            int top,
            int baseline,
            int bottom,
            CharSequence text,
            int start,
            int end,
            int lineNumber) {
        final int[] bounds = spanBounds(text);
        if (bounds == null || right <= left || bottom <= top) {
            return;
        }
        final boolean first = isFirstLine(start, end, bounds[0]);
        final boolean last = isLastLine(start, end, bounds[0], bounds[1]);

        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(backgroundColor);
        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setColor(accentColor);
        for (int level = 1; level <= depth; level++) {
            final float cardLeft = left + levelIndent * (level - 1);
            final float cardRight = right - levelRightInset * (depth - level);
            final float cardTop = top + verticalPadding * (level - 1) - (first ? 0f : 1f);
            final float cardBottom = bottom - verticalPadding * (level - 1) + (last ? 0f : 1f);
            if (cardRight <= cardLeft + barWidth || cardBottom <= cardTop) {
                continue;
            }
            final float r = Math.min(radius,
                    Math.min((cardRight - cardLeft) / 2f, (cardBottom - cardTop) / 2f));
            radii[0] = first ? r : 0f;
            radii[1] = first ? r : 0f;
            radii[2] = first ? r : 0f;
            radii[3] = first ? r : 0f;
            radii[4] = last ? r : 0f;
            radii[5] = last ? r : 0f;
            radii[6] = last ? r : 0f;
            radii[7] = last ? r : 0f;
            rect.set(cardLeft, cardTop, cardRight, cardBottom);
            path.reset();
            path.addRoundRect(rect, radii, Path.Direction.CW);
            canvas.drawPath(path, backgroundPaint);

            final float barLeft = cardLeft + paddingLeft;
            final float barRight = barLeft + barWidth;
            final float barTop = cardTop + (first ? verticalPadding : 0f);
            final float barBottom = cardBottom - (last ? verticalPadding : 0f);
            if (barBottom > barTop) {
                rect.set(barLeft, barTop, barRight, barBottom);
                canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, barPaint);
            }
        }
    }

    @Override
    public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight,
                             Paint.FontMetricsInt fm) {
        chooseHeight(text, start, end, spanstartv, lineHeight, fm, null);
    }

    @Override
    public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight,
                             Paint.FontMetricsInt fm, @Nullable TextPaint paint) {
        if (fm == null) {
            return;
        }
        final int[] bounds = spanBounds(text);
        if (bounds == null) {
            return;
        }
        final float pad = verticalPadding;
        if (isFirstLine(start, end, bounds[0])) {
            fm.ascent -= Math.round(pad);
            fm.top = Math.min(fm.top, fm.ascent);
        }
        if (isLastLine(start, end, bounds[0], bounds[1])) {
            fm.descent += Math.round(pad);
            fm.bottom = Math.max(fm.bottom, fm.descent);
        }
    }

    public int getColor() {
        return accentColor;
    }

    private int[] spanBounds(CharSequence text) {
        if (!(text instanceof Spanned)) {
            return null;
        }
        final Spanned spanned = (Spanned) text;
        final int start = spanned.getSpanStart(this);
        final int end = spanned.getSpanEnd(this);
        return end > start ? new int[]{start, end} : null;
    }

    private static boolean isFirstLine(int start, int end, int spanStart) {
        if (start == end) {
            return start == spanStart;
        }
        return spanStart >= start && spanStart < end;
    }

    private static boolean isLastLine(int start, int end, int spanStart, int spanEnd) {
        return spanEnd > start && spanEnd <= Math.max(end, start + 1);
    }

    private int dp(float value) {
        return Math.round(value * density);
    }

    private void setColors(int accent) {
        this.accentColor = accent;
        this.backgroundColor = Color.argb(BACKGROUND_ALPHA,
                Color.red(accent), Color.green(accent), Color.blue(accent));
    }

    /**
     * Applies the active theme's accent to every quote span, hides the literal {@code > } markers,
     * and makes the header line (sender and date/time) slightly smaller and bold. Safe to call again
     * after a view is recycled.
     */
    public static void attach(@Nullable Spannable body, @Nullable Context context) {
        if (body == null || context == null) {
            return;
        }
        final Object[] all = body.getSpans(0, body.length(), Object.class);
        for (Object span : all) {
            final int flags = body.getSpanFlags(span);
            if ((flags & HEADER_FLAG) != 0 || (flags & MARKER_FLAG) != 0) {
                body.removeSpan(span);
            }
        }
        final int accent = StyledAttributes.getColor(context, R.attr.colorAccent);
        for (QuoteSpan span : body.getSpans(0, body.length(), QuoteSpan.class)) {
            span.setColors(accent);
            final int start = body.getSpanStart(span);
            final int end = body.getSpanEnd(span);
            if (end <= start) {
                continue;
            }
            int lineEnd = start;
            while (lineEnd < end && body.charAt(lineEnd) != '\n') {
                lineEnd++;
            }
            if (lineEnd > start) {
                body.setSpan(new StyleSpan(Typeface.BOLD), start, lineEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | HEADER_FLAG);
                body.setSpan(new RelativeSizeSpan(0.92f), start, lineEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | HEADER_FLAG);
            }
            hideMarkers(body, start, end);
        }
    }

    /** Replaces the leading {@code > } run of every quoted line with a zero-width, invisible span. */
    private static void hideMarkers(final Spannable body, final int start, final int end) {
        int lineStart = start;
        while (lineStart < end) {
            int lineEnd = lineStart;
            while (lineEnd < end && body.charAt(lineEnd) != '\n') {
                lineEnd++;
            }
            int i = lineStart;
            while (i < lineEnd && (body.charAt(i) == ' ' || body.charAt(i) == '\t')) {
                i++;
            }
            if (i < lineEnd && body.charAt(i) == '>') {
                while (i < lineEnd && (body.charAt(i) == '>'
                        || body.charAt(i) == ' '
                        || body.charAt(i) == '\t')) {
                    i++;
                }
                if (i > lineStart) {
                    body.setSpan(new MarkerSpan(), lineStart, i,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | MARKER_FLAG);
                }
            }
            lineStart = lineEnd + 1;
        }
    }

    /** Draws nothing and takes no space; the card's leading margin provides the indentation. */
    private static final class MarkerSpan extends ReplacementSpan {

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return 0;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                         int top, int y, int bottom, Paint paint) {
            // invisible
        }
    }
}
