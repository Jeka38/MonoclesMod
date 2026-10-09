package de.monocles.mod;

import android.text.Spanned;
import android.text.style.BulletSpan;
import android.text.style.CharacterStyle;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.ui.text.QuoteSpan;
import eu.siacs.conversations.utils.StylingHelper;
import eu.siacs.conversations.xmpp.markup.MessageMarkup;

/**
 * Builds the XEP-0394 {@code <markup/>} model from the editor's styled text.
 *
 * <p>Offsets are emitted in Unicode <em>code points</em>, while {@link Spanned} works in UTF-16
 * units, so every start/end is converted. The inline styles the editor produces ({@code _emphasis_},
 * {@code ~deleted~}, {@code ```code```}, bold) map 1:1 onto the XEP's {@code <span/>} children; a
 * {@link QuoteSpan} becomes a {@code <bquote/>} and a {@link BulletSpan} a {@code <list/>} item.
 */
public final class SpannedToMarkup {

    /** How far the existing editor styles spread onto the semantic markup. */
    private SpannedToMarkup() {
    }

    public static boolean isEmpty(final Spanned text) {
        return text == null || !hasMarkup(text);
    }

    private static boolean hasMarkup(@NonNull final Spanned text) {
        return text.getSpans(0, text.length(), StyleSpan.class).length > 0
                || text.getSpans(0, text.length(), TypefaceSpan.class).length > 0
                || text.getSpans(0, text.length(), StrikethroughSpan.class).length > 0
                || text.getSpans(0, text.length(), QuoteSpan.class).length > 0
                || text.getSpans(0, text.length(), BulletSpan.class).length > 0;
    }

    @NonNull
    public static MessageMarkup.Model fromSpanned(@NonNull final Spanned text) {
        final MessageMarkup.Model model = new MessageMarkup.Model();
        // inline spans: one <span> per styled run, grouped by identical range + kind
        addStyledSpans(text, model, StyleSpan.class);
        addStyledSpans(text, model, TypefaceSpan.class);
        addStyledSpans(text, model, StrikethroughSpan.class);

        for (final QuoteSpan quote : text.getSpans(0, text.length(), QuoteSpan.class)) {
            final int start = toCodePoints(text, text.getSpanStart(quote));
            final int end = toCodePoints(text, text.getSpanEnd(quote));
            if (end > start) {
                model.quotes.add(new MessageMarkup.Range(start, end));
            }
        }

        final BulletSpan[] bullets = text.getSpans(0, text.length(), BulletSpan.class);
        if (bullets.length > 0) {
            final List<Integer> starts = new ArrayList<>();
            int listStart = Integer.MAX_VALUE;
            int listEnd = 0;
            for (final BulletSpan bullet : bullets) {
                final int start = text.getSpanStart(bullet);
                final int end = text.getSpanEnd(bullet);
                starts.add(toCodePoints(text, start));
                listStart = Math.min(listStart, toCodePoints(text, start));
                listEnd = Math.max(listEnd, toCodePoints(text, end));
            }
            starts.sort(Integer::compareTo);
            final int[] itemStarts = new int[starts.size()];
            for (int i = 0; i < itemStarts.length; i++) {
                itemStarts[i] = starts.get(i);
            }
            if (!starts.isEmpty()) {
                model.lists.add(new MessageMarkup.ListBlock(
                        new MessageMarkup.Range(listStart, listEnd), false, itemStarts));
            }
        }
        return model;
    }

    private static <T extends CharacterStyle> void addStyledSpans(
            final Spanned text, final MessageMarkup.Model model, final Class<T> type) {
        for (final T span : text.getSpans(0, text.length(), type)) {
            final int userFlags =
                    (text.getSpanFlags(span) & Spanned.SPAN_USER) >> Spanned.SPAN_USER_SHIFT;
            if (userFlags == StylingHelper.XHTML_REMOVE || userFlags == StylingHelper.XHTML_IGNORE) {
                continue;
            }
            final MessageMarkup.Kind kind = kindOf(span);
            if (kind == null) {
                continue;
            }
            final int start = toCodePoints(text, text.getSpanStart(span));
            final int end = toCodePoints(text, text.getSpanEnd(span));
            if (end <= start) {
                continue;
            }
            final MessageMarkup.Range range = new MessageMarkup.Range(start, end);
            MessageMarkup.Span existing = null;
            for (final MessageMarkup.Span candidate : model.spans) {
                if (candidate.range.start == start && candidate.range.end == end) {
                    existing = candidate;
                    break;
                }
            }
            if (existing == null) {
                final List<MessageMarkup.Kind> kinds = new ArrayList<>();
                kinds.add(kind);
                model.spans.add(new MessageMarkup.Span(range, kinds));
            } else if (!existing.kinds.contains(kind)) {
                existing.kinds.add(kind);
            }
        }
    }

    private static MessageMarkup.Kind kindOf(final CharacterStyle span) {
        if (span instanceof StyleSpan) {
            final int style = ((StyleSpan) span).getStyle();
            if ((style & android.graphics.Typeface.BOLD) != 0) {
                return MessageMarkup.Kind.STRONG;
            }
            if ((style & android.graphics.Typeface.ITALIC) != 0) {
                return MessageMarkup.Kind.EMPHASIS;
            }
            return null;
        }
        if (span instanceof TypefaceSpan && "monospace".equals(((TypefaceSpan) span).getFamily())) {
            return MessageMarkup.Kind.CODE;
        }
        if (span instanceof StrikethroughSpan) {
            return MessageMarkup.Kind.DELETED;
        }
        return null;
    }

    /** Number of Unicode code points before {@code charIndex}. */
    private static int toCodePoints(@NonNull final CharSequence text, final int charIndex) {
        if (charIndex <= 0) {
            return 0;
        }
        if (charIndex >= text.length()) {
            return Character.codePointCount(text, 0, text.length());
        }
        return Character.codePointCount(text, 0, charIndex);
    }
}
