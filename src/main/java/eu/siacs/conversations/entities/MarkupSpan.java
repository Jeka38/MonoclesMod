package eu.siacs.conversations.entities;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineBackgroundSpan;
import android.text.style.LineHeightSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import eu.siacs.conversations.xmpp.markup.MessageMarkup;

/**
 * Spans that render an XEP-0394 {@link MessageMarkup.Mark} on a message bubble.
 *
 * <p>Every span carries {@link #MARKUP_FLAG} in its flags. The flag makes markup spans identifiable,
 * which the renderer uses to clear old ones before re-rendering instead of accumulating duplicates.
 */
public final class MarkupSpan {

    /** Span-flag bit marking spans that were produced from message markup. */
    public static final int MARKUP_FLAG = 1 << 28;

    private MarkupSpan() {
    }

    /** Turns a markup mark into the span that renders it, or {@code null} for an unknown type. */
    @Nullable
    public static Object forMark(@NonNull final MessageMarkup.Mark mark) {
        switch (mark.type) {
            case EMPHASIS:
                return new StyleSpan(Typeface.ITALIC);
            case CODE:
                return new TypefaceSpan("monospace");
            case DELETED:
                return new StrikethroughSpan();
            case CODE_BLOCK:
                return new CodeBlockSpan(mark.argument);
            case LIST_ITEM:
            case LIST_ITEM_ORDERED:
                return new ListItemSpan(mark.argument);
            case QUOTE:
                return new QuoteBarSpan();
            default:
                return null;
        }
    }

    /**
     * A code block, Telegram style: monospaced text, a lighter block background and the language
     * printed above it when the sender provided one.
     */
    public static class CodeBlockSpan extends TypefaceSpan implements LineBackgroundSpan, LineHeightSpan {

        private static final float LABEL_SP = 12f;
        private static final int BACKGROUND_ALPHA = 40;
        private static final int PANEL_BACKGROUND = 0xFF000000;

        @Nullable
        private final String language;
        private int labelHeight = 0;

        public CodeBlockSpan(@Nullable final String language) {
            super("monospace");
            this.language = language;
        }

        @Override
        public void chooseHeight(
                final CharSequence text,
                final int start,
                final int end,
                final int spanstartv,
                final int lineHeight,
                final Paint.FontMetricsInt fm) {
            if (language != null && fm != null) {
                final int label = Math.round(LABEL_SP * density());
                fm.ascent -= label;
                labelHeight = label;
            }
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
            // whole block : soft tint, slightly stronger than the bubble
            paint.setColor(PANEL_BACKGROUND);
            paint.setAlpha(BACKGROUND_ALPHA);
            canvas.drawRect(left, top, right, bottom, paint);
            // language label : a small chip in the top-left of the first line
            if (language != null && lineNumber == 0) {
                paint.setTextSize(LABEL_SP * density());
                paint.setColor(Color.WHITE);
                paint.setAlpha(200);
                canvas.drawText(language, left + 8 * density(), top + labelHeight - 3 * density(), paint);
            }
            paint.setColor(color);
            paint.setAlpha(alpha);
        }
    }

    /** One list item: a hanging indent and a bullet or its ordinal. */
    public static class ListItemSpan implements LeadingMarginSpan {

        private final String marker;

        public ListItemSpan(@Nullable final String marker) {
            this.marker = marker == null || marker.isEmpty() ? "•" : marker + ".";
        }

        @Override
        public int getLeadingMargin(final boolean first) {
            return Math.round(24 * density());
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
            c.drawText(marker, x, baseline, p);
            p.setStyle(style);
        }
    }

    /** A vertical bar in front of a quoted passage. */
    public static class QuoteBarSpan implements LeadingMarginSpan {

        @Override
        public int getLeadingMargin(final boolean first) {
            return first ? Math.round(18 * density()) : 0;
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
