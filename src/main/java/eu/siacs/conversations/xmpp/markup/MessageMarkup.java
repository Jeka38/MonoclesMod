package eu.siacs.conversations.xmpp.markup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

/**
 * XEP-0394 (Message Markup) parser and serialiser.
 *
 * <p>The markup lives in a {@code <markup xmlns='urn:xmpp:markup:0'/>} element next to the plain
 * {@code <body/>}, never inside it: the body stays the single source of truth for the text and the
 * markup only refers to it by offsets. All offsets are in <em>Unicode code points</em> (not Java
 * {@code char}s, which are UTF-16 units), so a message with emoji or non-BMP characters still
 * addresses the right range.
 *
 * <p>Supported elements: {@code <span>} with {@code <emphasis/>}, {@code <strong/>}, {@code <code/>}
 * and {@code <deleted/>}; {@code <bcode>} (code blocks, optional {@code language}); {@code <list>}
 * with {@code <li>} (optional {@code ordered}); {@code <bquote>} (block quotes). Unknown elements are
 * ignored while parsing, per the XEP, and carried through untouched on the way out because we build
 * the element from scratch.
 */
public final class MessageMarkup {

    public enum Kind {
        EMPHASIS,
        STRONG,
        CODE,
        DELETED
    }

    /** A start/end range expressed in Unicode code points of the message body. */
    public static final class Range {
        public final int start;
        public final int end;

        public Range(final int start, final int end) {
            this.start = start;
            this.end = end;
        }
    }

    /** An inline {@code <span/>}: a code point range with one or more semantic children. */
    public static final class Span {
        public final Range range;
        public final List<Kind> kinds;

        public Span(final Range range, final List<Kind> kinds) {
            this.range = range;
            this.kinds = kinds;
        }
    }

    /** A {@code <list/>} with its items' start offsets and whether it is ordered. */
    public static final class ListBlock {
        public final Range range;
        public final boolean ordered;
        public final int[] itemStarts;

        public ListBlock(final Range range, final boolean ordered, final int[] itemStarts) {
            this.range = range;
            this.ordered = ordered;
            this.itemStarts = itemStarts;
        }
    }

    /** A parsed {@code <markup/>}. */
    public static final class Model {
        public final List<Span> spans = new ArrayList<>();
        public final List<CodeBlock> codeBlocks = new ArrayList<>();
        public final List<ListBlock> lists = new ArrayList<>();
        public final List<Range> quotes = new ArrayList<>();

        public boolean isEmpty() {
            return spans.isEmpty() && codeBlocks.isEmpty() && lists.isEmpty() && quotes.isEmpty();
        }
    }

    /** A {@code <bcode/>} block. */
    public static final class CodeBlock {
        public final Range range;
        @Nullable
        public final String language;

        public CodeBlock(final Range range, @Nullable final String language) {
            this.range = range;
            this.language = language;
        }
    }

    private MessageMarkup() {
    }

    // ---------------------------------------------------------------- parsing

    @Nullable
    public static Model parse(@Nullable final Element markup) {
        if (markup == null) {
            return null;
        }
        final Model model = new Model();
        for (final Element child : markup.getChildren()) {
            final String name = child.getName();
            if ("span".equals(name)) {
                parseSpan(child, model);
            } else if ("bcode".equals(name)) {
                final Range range = range(child);
                if (range != null) {
                    model.codeBlocks.add(new CodeBlock(range, blankToNull(child.getAttribute("language"))));
                }
            } else if ("list".equals(name)) {
                parseList(child, model);
            } else if ("bquote".equals(name)) {
                final Range range = range(child);
                if (range != null) {
                    model.quotes.add(range);
                }
            }
            // anything else is ignored on purpose (forward compatibility)
        }
        return model.isEmpty() ? null : model;
    }

    private static void parseSpan(final Element span, final Model model) {
        final Range range = range(span);
        if (range == null) {
            return;
        }
        final List<Kind> kinds = new ArrayList<>();
        for (final Element child : span.getChildren()) {
            switch (child.getName()) {
                case "emphasis":
                    kinds.add(Kind.EMPHASIS);
                    break;
                case "strong":
                    kinds.add(Kind.STRONG);
                    break;
                case "code":
                    kinds.add(Kind.CODE);
                    break;
                case "deleted":
                    kinds.add(Kind.DELETED);
                    break;
                default:
                    break;
            }
        }
        if (!kinds.isEmpty()) {
            model.spans.add(new Span(range, kinds));
        }
    }

    private static void parseList(final Element list, final Model model) {
        final Range range = range(list);
        if (range == null) {
            return;
        }
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
        if (starts.isEmpty()) {
            return;
        }
        final int[] itemStarts = new int[starts.size()];
        for (int i = 0; i < itemStarts.length; i++) {
            itemStarts[i] = starts.get(i);
        }
        model.lists.add(new ListBlock(range, "true".equals(list.getAttribute("ordered")), itemStarts));
    }

    @Nullable
    private static Range range(final Element element) {
        final int start = intAttribute(element, "start", -1);
        final int end = intAttribute(element, "end", -1);
        if (start < 0 || end < start) {
            return null;
        }
        return new Range(start, end);
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
    private static String blankToNull(@Nullable final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ---------------------------------------------------------------- writing

    /** Builds a {@code <markup/>} element from already code-point based offsets. */
    @NonNull
    public static Element toElement(@NonNull final Model model) {
        final Element markup = new Element("markup", Namespace.MARKUP);
        for (final Span span : model.spans) {
            final Element element = markup.addChild("span");
            element.setAttribute("start", span.range.start);
            element.setAttribute("end", span.range.end);
            for (final Kind kind : span.kinds) {
                element.addChild(kindName(kind));
            }
        }
        for (final CodeBlock block : model.codeBlocks) {
            final Element element = markup.addChild("bcode");
            element.setAttribute("start", block.range.start);
            element.setAttribute("end", block.range.end);
            if (block.language != null) {
                element.setAttribute("language", block.language);
            }
        }
        for (final ListBlock list : model.lists) {
            final Element element = markup.addChild("list");
            element.setAttribute("start", list.range.start);
            element.setAttribute("end", list.range.end);
            element.setAttribute("ordered", list.ordered ? "true" : "false");
            for (final int start : list.itemStarts) {
                element.addChild("li").setAttribute("start", start);
            }
        }
        for (final Range quote : model.quotes) {
            final Element element = markup.addChild("bquote");
            element.setAttribute("start", quote.start);
            element.setAttribute("end", quote.end);
        }
        return markup;
    }

    private static String kindName(final Kind kind) {
        switch (kind) {
            case EMPHASIS:
                return "emphasis";
            case STRONG:
                return "strong";
            case CODE:
                return "code";
            case DELETED:
                return "deleted";
            default:
                throw new AssertionError("unknown kind");
        }
    }
}
