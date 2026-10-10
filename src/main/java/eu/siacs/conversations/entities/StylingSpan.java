package eu.siacs.conversations.entities;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.TextPaint;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineBackgroundSpan;
import android.text.style.LineHeightSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import eu.siacs.conversations.xmpp.styling.MessageStyling;

/**
 * Spans that render XEP-0393 message styling.
 *
 * <p>Directives are styled together with the text they apply to, and every span produced here carries
 * {@link #STYLING_FLAG} so a re-render (message edits, recycling) can clear the previous pass instead
 * of stacking spans.
 */
public final class StylingSpan {

    /** Span-flag bit marking spans produced by XEP-0393 styling. */
    public static final int STYLING_FLAG = 1 << 27;

    private StylingSpan() {
    }

    /** The inline span for a styling directive. */
    @NonNull
    public static Object forDirective(@NonNull final MessageStyling.Style style) {
        switch (style) {
            case EMPHASIS:
                return new StyleSpan(Typeface.ITALIC);
            case STRIKE:
                return new StrikethroughSpan();
            case MONO:
                return new TypefaceSpan("monospace");
            default:
                throw new AssertionError("unknown directive: " + style);
        }
    }

    /** A {@code ```} preformatted block: monospaced, with a subtle panel background. */
    public static class CodeBlock extends TypefaceSpan implements LineBackgroundSpan {

        private static final int BACKGROUND_ALPHA = 36;
        private static final int PANEL = 0xFF000000;

        @Nullable
        private final String language;

        public CodeBlock(@Nullable final String language) {
            super("monospace");
            this.language = language;
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
            paint.setColor(PANEL);
            paint.setAlpha(BACKGROUND_ALPHA);
            canvas.drawRect(left, top, right, bottom, paint);
            paint.setColor(color);
            paint.setAlpha(alpha);
        }
    }

    /** A quotation: an indent plus a vertical bar in front of each quoted line. */
    public static class Quote implements LeadingMarginSpan {

        private final int depth;

        public Quote(final int depth) {
            this.depth = Math.max(1, depth);
        }

        @Override
        public int getLeadingMargin(final boolean first) {
            return Math.round(depth * 12 * density());
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
            final int bar = Math.round(3 * density());
            c.drawRect(x, top, x + bar, bottom, p);
            p.setStyle(style);
            p.setColor(color);
        }
    }

    private static float density() {
        return android.content.res.Resources.getSystem().getDisplayMetrics().density;
    }
}
