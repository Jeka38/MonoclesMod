package eu.siacs.conversations.xmpp.styling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * XEP-0393 (Message Styling).
 *
 * <p>Unlike message markup, styling is <em>in-band</em>: the directives live in the body itself
 * ({@code _emphasis_}, {@code ~strike~}, {@code `mono`}) plus two block kinds — a
 * preformatted block fenced by {@code ```} and quotations introduced by {@code >}. The XEP
 * recommends rendering the directives in the same style as the text they apply to (so {@code _x_}
 * shows both underscores italic), which is what this parser assumes: it never edits the body, it
 * only reports ranges the renderer styles.
 *
 * <p>Only plain and quoted lines carry inline spans; preformatted blocks are literal and may not
 * contain child blocks or spans. Parsing is one left-to-right pass per block, lazily matching each
 * directive.
 */
public final class MessageStyling {

    public static final String NAMESPACE = "urn:xmpp:styling:0";

    public enum Style {
        EMPHASIS,
        STRIKE,
        MONO
    }

    /** An inline directive pair; {@code start}..{@code end} includes both marker characters. */
    public static final class Span {
        public final Style style;
        public final int start;
        public final int end;

        public Span(final Style style, final int start, final int end) {
            this.style = style;
            this.start = start;
            this.end = end;
        }
    }

    /** A preformatted block or a run of quoted lines, as offsets into the body. */
    public static final class Block {
        public final boolean preformatted;
        /** The whole block, fence lines included. */
        public final int start;
        public final int end;
        /** For a preformatted block, the code itself, without the {@code ```} fence lines. */
        public final int contentStart;
        public final int contentEnd;
        /** Quote nesting depth (1 for a single {@code >}), 0 for a preformatted block. */
        public final int depth;
        /** Text after the opening fence of a preformatted block, if any. */
        @Nullable
        public final String language;

        Block(final boolean preformatted, final int start, final int end, final int contentStart,
              final int contentEnd, final int depth, @Nullable final String language) {
            this.preformatted = preformatted;
            this.start = start;
            this.end = end;
            this.contentStart = contentStart;
            this.contentEnd = contentEnd;
            this.depth = depth;
            this.language = language;
        }
    }

    private MessageStyling() {
    }

    /**
     * Parses a body into the inline spans and the block ranges to style. Offsets are plain
     * {@code char} indices into {@code body} (styling is applied to the string as-is, directives
     * included, so no remapping is needed).
     */
    @NonNull
    public static List<Object> parse(@NonNull final CharSequence body) {
        final List<Object> result = new ArrayList<>();
        final int length = body.length();
        int lineStart = 0;
        boolean inPre = false;
        int preStart = 0;
        int preContentStart = 0;
        String language = null;
        int quoteStart = -1;
        int quoteDepth = 0;

        while (lineStart <= length) {
            final int lineEnd = nextLineEnd(body, lineStart);
            final String line = body.subSequence(lineStart, lineEnd).toString();

            final String fenceLanguage = inPre ? null : fenceLanguage(line);
            if (inPre) {
                if (isClosingFence(line)) {
                    result.add(new Block(true, preStart, lineEnd, preContentStart,
                            contentEndBefore(body, lineStart), 0, language));
                    inPre = false;
                } else if (lineEnd >= length) {
                    result.add(new Block(true, preStart, lineEnd, preContentStart, lineEnd,
                            0, language));
                    inPre = false;
                }
            } else if (fenceLanguage != null) {
                flushQuote(result, quoteStart, lineStart, quoteDepth);
                quoteStart = -1;
                quoteDepth = 0;
                inPre = true;
                preStart = lineStart;
                preContentStart = lineEnd < length ? lineEnd + 1 : length;
                language = fenceLanguage.isEmpty() ? null : fenceLanguage;
            } else {
                final int depth = quoteDepth(line);
                if (depth > 0) {
                    if (quoteStart < 0) {
                        quoteStart = lineStart;
                        quoteDepth = depth;
                    }
                    parseInline(body, lineEnd, lineStart + quoteMarkerEnd(line), result);
                } else {
                    flushQuote(result, quoteStart, lineStart, quoteDepth);
                    quoteStart = -1;
                    quoteDepth = 0;
                    parseInline(body, lineEnd, lineStart, result);
                }
            }

            if (lineEnd >= length) {
                break;
            }
            lineStart = lineEnd + 1;
        }

        if (inPre) {
            result.add(new Block(true, preStart, length, preContentStart, length, 0, language));
        }
        flushQuote(result, quoteStart, length, quoteDepth);
        return result;

    }

    /** End of a preformatted block's content: just before the newline that starts the closing fence. */
    private static int contentEndBefore(@NonNull final CharSequence body, final int fenceStart) {
        if (fenceStart > 0 && body.charAt(fenceStart - 1) == '\n') {
            return fenceStart - 1;
        }
        return fenceStart;
    }

    private static void flushQuote(
            final List<Object> out, final int start, final int end, final int depth) {
        if (start >= 0 && depth > 0) {
            out.add(new Block(false, start, end, start, end, depth, null));
        }
    }

    private static int nextLineEnd(@NonNull final CharSequence body, final int from) {
        final int length = body.length();
        for (int i = from; i < length; i++) {
            if (body.charAt(i) == '\n') {
                return i;
            }
        }
        return length;
    }

    /** The language on a {@code ```} fence line, {@code ""} for a bare fence, or null. */
    @Nullable
    private static String fenceLanguage(@NonNull final String line) {
        final String trimmed = line.trim();
        if (!trimmed.startsWith("```")) {
            return null;
        }
        return trimmed.substring(3).trim();
    }

    private static boolean isClosingFence(@NonNull final String line) {
        return line.trim().equals("```");
    }

    /** Nesting depth of a quotation line, 0 when the line is not quoted. */
    private static int quoteDepth(@NonNull final String line) {
        int depth = 0;
        int i = 0;
        while (i < line.length()) {
            while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
                i++;
            }
            if (i < line.length() && line.charAt(i) == '>') {
                depth++;
                i++;
                if (i < line.length() && line.charAt(i) == ' ') {
                    i++;
                }
            } else {
                break;
            }
        }
        return depth;
    }

    /** First character index after the quotation markers, where the child block starts. */
    private static int quoteMarkerEnd(@NonNull final String line) {
        int i = 0;
        while (i < line.length()) {
            while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
                i++;
            }
            if (i < line.length() && line.charAt(i) == '>') {
                i++;
                if (i < line.length() && line.charAt(i) == ' ') {
                    i++;
                }
            } else {
                break;
            }
        }
        return i;
    }

    /**
     * Finds the inline directives of one line. {@code from} is where the content starts (past any
     * quote markers) and {@code to} where it ends; offsets are absolute. Directives are matched
     * lazily, left to right, and may nest, so the content of a span is scanned again.
     */
    private static void parseInline(
            @NonNull final CharSequence body,
            final int lineEnd,
            final int from,
            @NonNull final List<Object> out) {
        parseRange(body, from, lineEnd, from, out);
    }

    private static void parseRange(
            @NonNull final CharSequence body,
            final int start,
            final int end,
            final int blockStart,
            @NonNull final List<Object> out) {
        int i = start;
        while (i < end) {
            final Style style = styleOf(body.charAt(i));
            if (style == null || !isOpening(body, i, blockStart, end)) {
                i++;
                continue;
            }
            final int close = findClosingDirective(body, i, end, body.charAt(i));
            // both directives must contain some text between them, otherwise neither is valid
            if (close <= i + 1) {
                i++;
                continue;
            }
            out.add(new Span(style, i, close + 1));
            if (style != Style.MONO) {
                // a preformatted span holds a single plain span, everything else may nest
                parseRange(body, i + 1, close, blockStart, out);
            }
            i = close + 1;
        }
    }

    @Nullable
    private static Style styleOf(final char c) {
        switch (c) {
            case '_':
                return Style.EMPHASIS;
            case '~':
                return Style.STRIKE;
            case '`':
                return Style.MONO;
            default:
                return null;
        }
    }

    /**
     * A directive opens when it is at the start of the block or after whitespace or another
     * directive, and is not followed by whitespace.
     */
    private static boolean isOpening(
            @NonNull final CharSequence body, final int index, final int blockStart, final int end) {
        if (index + 1 >= end || isWhitespace(body.charAt(index + 1))) {
            return false;
        }
        if (index == blockStart) {
            return true;
        }
        final char previous = body.charAt(index - 1);
        return isWhitespace(previous) || styleOf(previous) != null;
    }

    /** Lazily finds the next directive that closes the one at {@code open}. */
    private static int findClosingDirective(
            @NonNull final CharSequence body, final int open, final int end, final char directive) {
        for (int i = open + 1; i < end; i++) {
            if (body.charAt(i) == directive && !isWhitespace(body.charAt(i - 1))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isWhitespace(final char c) {
        return Character.isWhitespace(c);
    }
}
