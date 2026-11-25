package eu.siacs.conversations.entities;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import androidx.annotation.NonNull;

import eu.siacs.conversations.ui.text.QuoteSpan;
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

    /** A quotation; every nesting level is rendered as its own indented card. */
    public static class Quote extends QuoteSpan {

        public Quote(final int depth) {
            super(Color.GRAY, android.content.res.Resources.getSystem().getDisplayMetrics(), depth);
        }
    }
}
