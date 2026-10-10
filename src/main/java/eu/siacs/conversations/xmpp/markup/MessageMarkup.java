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
            case CODE:
                return "code";
            case DELETED:
                return "deleted";
            default:
                throw new AssertionError("not an inline mark: " + type);
        }
    }

}
