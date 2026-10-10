package eu.siacs.conversations.xmpp.markup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

/**
 * XEP-0394 (Message Markup).
 *
 * <p>Markup travels in a {@code <markup xmlns='urn:xmpp:markup:0'/>} element next to the plain
 * {@code <body/>}. The body stays the only copy of the text; markup refers to it by offsets counted
 * in <em>Unicode code points</em> (not UTF-16 units), so emoji and other non-BMP characters keep
 * their ranges.
 *
 * <p>The whole model is one flat list of {@link Mark}s. A list contributes one mark per item rather
 * than a nested structure, which keeps both the parser and the renderer small; the ordered flag is
 * carried by the mark type.
 */
public final class MessageMarkup {

    public enum Type {
        EMPHASIS,
        STRONG,
        CODE,
        DELETED,
        CODE_BLOCK,
        LIST_ITEM,
        LIST_ITEM_ORDERED,
        QUOTE
    }

    /** One markup element reduced to its type, a code point range and an optional argument. */
    public static final class Mark {
        public final Type type;
        public final int start;
        public final int end;
        /** Code block language, list item ordinal, or {@code null}. */
        @Nullable
        public final String argument;

        public Mark(final Type type, final int start, final int end, @Nullable final String argument) {
            this.type = type;
            this.start = start;
            this.end = end;
            this.argument = argument;
        }

        @NonNull
        public Mark withRange(final int start, final int end) {
            return new Mark(type, start, end, argument);
        }
    }

    private MessageMarkup() {
    }

    // ------------------------------------------------------------------ reading

    /** Parses a {@code <markup/>}; returns an empty list for {@code null} or nothing recognised. */
    @NonNull
    public static List<Mark> parse(@Nullable final Element markup) {
        final List<Mark> marks = new ArrayList<>();
        if (markup == null) {
            return marks;
        }
        for (final Element child : markup.getChildren()) {
            switch (child.getName()) {
                case "span":
                    parseSpan(child, marks);
                    break;
                case "bcode": {
                    final int[] range = range(child);
                    if (range != null) {
                        marks.add(new Mark(Type.CODE_BLOCK, range[0], range[1],
                                trimmed(child.getAttribute("language"))));
                    }
                    break;
                }
                case "list":
                    parseList(child, marks);
                    break;
                case "bquote": {
                    final int[] range = range(child);
                    if (range != null) {
                        marks.add(new Mark(Type.QUOTE, range[0], range[1], null));
                    }
                    break;
                }
                default:
                    // unknown elements are ignored on purpose, so future markup cannot break us
                    break;
            }
        }
        return marks;
    }

    private static void parseSpan(final Element span, final List<Mark> marks) {
        final int[] range = range(span);
        if (range == null) {
            return;
        }
        for (final Element child : span.getChildren()) {
            final Type type = spanType(child.getName());
            if (type != null) {
                marks.add(new Mark(type, range[0], range[1], null));
            }
        }
    }

    private static void parseList(final Element list, final List<Mark> marks) {
        final int[] range = range(list);
        if (range == null) {
            return;
        }
        final boolean ordered = "true".equals(list.getAttribute("ordered"));
        final Type type = ordered ? Type.LIST_ITEM_ORDERED : Type.LIST_ITEM;
        final List<Integer> starts = new ArrayList<>();
        for (final Element child : list.getChildren()) {
            if (!"li".equals(child.getName())) {
                continue;
            }
            final int start = intAttribute(child, "start", -1);
            if (start >= 0) {
                starts.add(start);
            }
        }
        for (int i = 0; i < starts.size(); i++) {
            final int start = starts.get(i);
            final int end = i + 1 < starts.size() ? starts.get(i + 1) : range[1];
            marks.add(new Mark(type, start, end, Integer.toString(i + 1)));
        }
    }

    @Nullable
    private static Type spanType(final String name) {
        switch (name) {
            case "emphasis":
                return Type.EMPHASIS;
            case "strong":
                return Type.STRONG;
            case "code":
                return Type.CODE;
            case "deleted":
                return Type.DELETED;
            default:
                return null;
        }
    }

    @Nullable
    private static int[] range(final Element element) {
        final int start = intAttribute(element, "start", -1);
        final int end = intAttribute(element, "end", -1);
        if (start < 0 || end < start) {
            return null;
        }
        return new int[] {start, end};
    }

    private static int intAttribute(final Element element, final String name, final int fallback) {
        final String value = element.getAttribute(name);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }

    @Nullable
    private static String trimmed(@Nullable final String value) {
        if (value == null) {
            return null;
        }
        final String result = value.trim();
        return result.isEmpty() ? null : result;
    }

    // ------------------------------------------------------------------ writing

    /**
     * Builds a {@code <markup/>} from marks whose offsets are already in code points. Only the
     * elements this client authors are written; a code block is folded into a single {@code <bcode/>}.
     */
    @NonNull
    public static Element build(@NonNull final List<Mark> marks) {
        final Element markup = new Element("markup", Namespace.MARKUP);
        for (final Mark mark : marks) {
            switch (mark.type) {
                case CODE_BLOCK: {
                    final Element element = markup.addChild("bcode");
                    element.setAttribute("start", mark.start);
                    element.setAttribute("end", mark.end);
                    if (mark.argument != null) {
                        element.setAttribute("language", mark.argument);
                    }
                    break;
                }
                case QUOTE: {
                    final Element element = markup.addChild("bquote");
                    element.setAttribute("start", mark.start);
                    element.setAttribute("end", mark.end);
                    break;
                }
                case LIST_ITEM:
                case LIST_ITEM_ORDERED: {
                    final Element element = markup.addChild("list");
                    element.setAttribute("start", mark.start);
                    element.setAttribute("end", mark.end);
                    element.setAttribute("ordered", mark.type == Type.LIST_ITEM_ORDERED ? "true" : "false");
                    element.addChild("li").setAttribute("start", mark.start);
                    break;
                }
                default: {
                    final Element element = markup.addChild("span");
                    element.setAttribute("start", mark.start);
                    element.setAttribute("end", mark.end);
                    element.addChild(elementName(mark.type));
                    break;
                }
            }
        }
        return markup;
    }

    private static String elementName(final Type type) {
        switch (type) {
            case EMPHASIS:
                return "emphasis";
            case STRONG:
                return "strong";
            case CODE:
                return "code";
            case DELETED:
                return "deleted";
            default:
                throw new AssertionError("not an inline mark: " + type);
        }
    }

    // ------------------------------------------------------------------ source parsing

    /**
     * Splits a message the user typed into its plain text and the code blocks it contains.
     *
     * <p>Code blocks are fenced with three backticks, Telegram style: the fence may carry a language
     * on the same line ({@code ```python}). The text of a block is kept in the result (the fence
     * lines themselves are not). Text outside a fence may carry inline Markdown markers, which are
     * stripped here as well — this client does not author inline markup, but the plain body a
     * receiving client sees must not contain the markers either way.
     */
    @NonNull
    public static ParsedBody parseSource(@NonNull final String source) {
        final StringBuilder body = new StringBuilder();
        final List<Mark> marks = new ArrayList<>();
        String language = null;
        int blockStart = -1;
        boolean inBlock = false;

        final String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        final String[] lines = normalized.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            final boolean last = index == lines.length - 1;
            final String line = lines[index];
            final String fence = fenceLanguage(line);
            if (fence != null) {
                if (inBlock) {
                    final int end = blockEnd(body, blockStart);
                    if (end > blockStart) {
                        marks.add(new Mark(Type.CODE_BLOCK, blockStart, end, language));
                    }
                    inBlock = false;
                    blockStart = -1;
                    language = null;
                    if (!last && !endsWithNewline(body)) {
                        body.append('\n');
                    }
                } else {
                    if (body.length() > 0 && !endsWithNewline(body)) {
                        body.append('\n');
                    }
                    blockStart = body.length();
                    language = fence.isEmpty() ? null : fence;
                    inBlock = true;
                }
                continue;
            }
            if (inBlock) {
                // inside a code block every character is literal
                body.append(line);
            } else {
                appendWithoutInlineMarkers(body, line);
            }
            if (!last) {
                body.append('\n');
            }
        }
        if (inBlock) {
            final int end = blockEnd(body, blockStart);
            if (end > blockStart) {
                marks.add(new Mark(Type.CODE_BLOCK, blockStart, end, language));
            }
        }
        trimTrailingNewline(body);
        return new ParsedBody(body.toString(), marks);
    }

    private static boolean endsWithNewline(@NonNull final StringBuilder body) {
        return body.length() > 0 && body.charAt(body.length() - 1) == '\n';
    }

    private static void trimTrailingNewline(@NonNull final StringBuilder body) {
        if (endsWithNewline(body)) {
            body.setLength(body.length() - 1);
        }
    }

    /** End of a code block: the position before the newline that closes its last line. */
    private static int blockEnd(@NonNull final StringBuilder body, final int blockStart) {
        int end = body.length();
        if (end > blockStart && body.charAt(end - 1) == '\n') {
            end--;
        }
        return end;
    }

    /** The language of a ``` fence line, or {@code null} when the line is not a fence. */
    @Nullable
    private static String fenceLanguage(@NonNull final String line) {
        final String trimmed = line.trim();
        if (!trimmed.startsWith("```")) {
            return null;
        }
        if (trimmed.length() == 3) {
            return "";
        }
        // the language must look like a token, otherwise the line is literal text
        final String rest = trimmed.substring(3);
        for (int i = 0; i < rest.length(); i++) {
            final char c = rest.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '+' && c != '#' && c != '-' && c != '.') {
                return null;
            }
        }
        return rest;
    }

    /**
     * Appends {@code line}, dropping the inline Markdown markers {@code _x_}, {@code ~x~} and
     * {@code `x`} while keeping their content. This client does not render those markers, so the
     * plain body a receiving client sees must not contain them.
     */
    private static void appendWithoutInlineMarkers(
            @NonNull final StringBuilder out, @NonNull final String line) {
        int i = 0;
        while (i < line.length()) {
            final char c = line.charAt(i);
            if ((c == '_' || c == '~' || c == '`') && isMarkerStart(line, i)) {
                final int close = findMarkerEnd(line, i, c);
                if (close > i) {
                    out.append(line, i + 1, close);
                    i = close + 1;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
    }

    private static boolean isMarkerStart(final String input, final int index) {
        return index == 0 || Character.isWhitespace(input.charAt(index - 1));
    }

    private static int findMarkerEnd(final String input, final int start, final char marker) {
        for (int i = start + 1; i < input.length(); i++) {
            if (input.charAt(i) == marker && !Character.isWhitespace(input.charAt(i - 1))) {
                return i;
            }
        }
        return -1;
    }

    /** The plain body plus the code blocks found in it. */
    public static final class ParsedBody {
        public final String body;
        public final List<Mark> marks;

        ParsedBody(final String body, final List<Mark> marks) {
            this.body = body;
            this.marks = marks;
        }

        public boolean hasMarks() {
            return !marks.isEmpty();
        }
    }
}
