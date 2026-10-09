package eu.siacs.conversations.entities;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineBackgroundSpan;

import androidx.annotation.NonNull;

/**
 * Spans applied while rendering XEP-0394 markup.
 *
 * <p>They carry {@link #MARKUP_FLAG} in the span flags so that everything produced from markup can be
 * told apart from the user's own editor formatting (XHTML-IM export must not re-export them) and can
 * be cleared before re-rendering.
 */
final class MarkupSpan {

    /** Extra span-flag bit used to tag spans that came from XEP-0394 markup. */
    static final int MARKUP_FLAG = 1 << 29;

    private MarkupSpan() {
    }

    static boolean isMarkupSpan(@NonNull final Object span) {
        return (span instanceof MarkupSpan.Marked);
    }

    interface Marked {
    }

    static float density() {
        return Resources.getSystem().getDisplayMetrics().density;
    }
}

/** Block-level code block: a monospaced, indented block with a subtle background. */
class MarkupCodeBlockSpan implements LeadingMarginSpan, LineBackgroundSpan, MarkupSpan.Marked {

    private static final int INDENT_DP = 12;
    private static final int BACKGROUND_ALPHA = 32;

    @Override
    public int getLeadingMargin(final boolean first) {
        return (int) (INDENT_DP * 3 * MarkupSpan.density());
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
        // nothing: the background is drawn by onDrawBackground
    }

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
        final int color = paint.getColor();
        final int alpha = paint.getAlpha();
        paint.setColor(0xFF808080);
        paint.setAlpha(BACKGROUND_ALPHA);
        canvas.drawRect(left, top, right, bottom, paint);
        paint.setColor(color);
        paint.setAlpha(alpha);
    }
}

/** One list item: a leading indent plus a bullet or an ordinal. */
class MarkupListItemSpan implements LeadingMarginSpan, MarkupSpan.Marked {

    private final boolean ordered;
    private final int index;

    MarkupListItemSpan(final boolean ordered, final int index) {
        this.ordered = ordered;
        this.index = index;
    }

    @Override
    public int getLeadingMargin(final boolean first) {
        return (int) (24 * MarkupSpan.density());
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
        if (!first) {
            return;
        }
        final Paint.Style style = p.getStyle();
        p.setStyle(Paint.Style.FILL);
        final String marker = ordered ? (index + 1) + "." : "•";
        c.drawText(marker, x, baseline, p);
        p.setStyle(style);
    }
}

/** Vertical quotation bar drawn next to a quoted block. */
class MarkupQuoteSpan implements LeadingMarginSpan, MarkupSpan.Marked {

    private static final int INDENT_DP = 6;
    private static final int BAR_WIDTH_DP = 3;

    @Override
    public int getLeadingMargin(final boolean first) {
        return (int) (INDENT_DP * 3 * MarkupSpan.density());
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
        final Paint.Style style = p.getStyle();
        final int color = p.getColor();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF808080);
        final int left = x + dir * INDENT_DP;
        c.drawRect(left, top, left + dir * BAR_WIDTH_DP, bottom, p);
        p.setStyle(style);
        p.setColor(color);
    }
}
