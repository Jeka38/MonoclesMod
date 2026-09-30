package eu.siacs.conversations.entities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

/**
 * A single note stored privately on the server and synchronised across the account's clients.
 *
 * <p>The wire format is the one used by Psi+'s <em>Storage Notes</em> plugin and Miranda IM
 * (XEP-0049 private XML storage):
 *
 * <pre>{@code
 * <iq type='set' id='strnotes_1'>
 *   <query xmlns='jabber:iq:private'>
 *     <storage xmlns='http://miranda-im.org/storage#notes'>
 *       <note tags='дом работа'><title>…</title><text>…</text></note>
 *       …
 *     </storage>
 *   </query>
 * </iq>
 * }</pre>
 *
 * <p>All notes are carried in a single storage element, tags are space separated and notes have
 * no id of their own; the identity used internally is derived from the XML so that the same note
 * keeps the same key across fetch/save round trips.
 */
public class Note {

    public enum Field {
        ALL,
        TITLE,
        TAG,
        TEXT
    }

    private final String key;
    private String title;
    private final List<String> tags = new ArrayList<>();
    private String body;

    public Note() {
        this.key = null;
    }

    public Note(final String title, final String body, final List<String> tags) {
        this.title = normalize(title);
        this.body = normalize(body);
        setTags(tags);
        this.key = computeKey(this.title, this.body, this.tags);
    }

    @Nullable
    public static List<Note> parseFromStorage(@Nullable final Element query) {
        final ArrayList<Note> notes = new ArrayList<>();
        if (query == null) {
            return notes;
        }
        final Element storage = query.findChild("storage", Namespace.NOTES_STORAGE);
        if (storage == null) {
            return notes;
        }
        for (final Element child : storage.getChildren()) {
            if ("note".equals(child.getName())) {
                notes.add(parse(child));
            }
        }
        return notes;
    }

    public static Note parse(final Element note) {
        final String title = childText(note, "title");
        final String body = childText(note, "text");
        final Note parsed = new Note();
        parsed.setTitle(title);
        parsed.setBody(body);
        parsed.setTagsFromAttribute(note.getAttribute("tags"));
        return parsed;
    }

    @Nullable
    private static String childText(final Element parent, final String name) {
        final Element child = parent.findChild(name);
        return child == null ? null : child.getContent();
    }

    /**
     * Builds the {@code <storage/>} element that wraps every note, matching the Psi+/Miranda
     * profile (tags space separated, {@code <title/>} then {@code <text/>}).
     */
    public static Element toStorageElement(final List<Note> notes) {
        final Element storage = new Element("storage", Namespace.NOTES_STORAGE);
        for (final Note note : notes) {
            final Element element = storage.addChild("note");
            final String joined = joinTags(note.tags);
            if (joined != null) {
                element.setAttribute("tags", joined);
            }
            if (note.title != null) {
                element.addChild("title").setContent(note.title);
            }
            if (note.body != null) {
                element.addChild("text").setContent(note.body);
            }
        }
        return storage;
    }

    /**
     * Stable identity derived from the note content. Used to address a note in the UI while the
     * actual list is stored and saved as one blob.
     */
    public String getKey() {
        return key != null ? key : computeKey(title, body, tags);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = normalize(title);
    }

    public String getBody() {
        return body;
    }

    public void setBody(final String body) {
        this.body = normalize(body);
    }

    public List<String> getTags() {
        return Collections.unmodifiableList(tags);
    }

    public void setTags(final List<String> tags) {
        this.tags.clear();
        if (tags == null) {
            return;
        }
        for (final String tag : tags) {
            final String normalized = normalize(tag);
            if (normalized != null && !this.tags.contains(normalized)) {
                this.tags.add(normalized);
            }
        }
    }

    public void setTagsFromAttribute(@Nullable final String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return;
        }
        for (final String part : raw.trim().split("\\s+")) {
            final String normalized = normalize(part);
            if (normalized != null && !this.tags.contains(normalized)) {
                this.tags.add(normalized);
            }
        }
    }

    public boolean isEmpty() {
        return (title == null || title.trim().isEmpty())
                && (body == null || body.trim().isEmpty())
                && tags.isEmpty();
    }

    public Note copy() {
        return new Note(title, body, new ArrayList<>(tags));
    }

    /**
     * Case-insensitive match of the given needle against the requested field(s). Multiple
     * space/comma separated needles must all match (AND), each of them against any field.
     */
    public boolean matches(@Nullable final String needle, @NonNull final Field field) {
        if (needle == null || needle.trim().isEmpty()) {
            return true;
        }
        return matchesLower(needle.trim().toLowerCase(Locale.US), field);
    }

    private boolean matchesLower(final String lowerNeedle, final Field field) {
        final String[] parts = lowerNeedle.split("[,\\s]+");
        for (final String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!matchesSingle(part, field)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesSingle(final String needle, final Field field) {
        return contains(titleSafe(), needle, field, Field.TITLE)
                || contains(bodySafe(), needle, field, Field.TEXT)
                || tagMatches(needle, field);
    }

    private boolean tagMatches(final String needle, final Field field) {
        if (field != Field.ALL && field != Field.TAG) {
            return false;
        }
        for (final String tag : tags) {
            if (tag.toLowerCase(Locale.US).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(
            final String haystack,
            final String needle,
            final Field field,
            final Field expected) {
        return (field == Field.ALL || field == expected) && haystack.contains(needle);
    }

    private String titleSafe() {
        return title == null ? "" : title.toLowerCase(Locale.US);
    }

    private String bodySafe() {
        return body == null ? "" : body.toLowerCase(Locale.US);
    }

    @Nullable
    private static String normalize(@Nullable final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Nullable
    private static String joinTags(final List<String> tags) {
        if (tags.isEmpty()) {
            return null;
        }
        final StringBuilder builder = new StringBuilder();
        for (final String tag : tags) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(tag);
        }
        return builder.toString();
    }

    private static String computeKey(
            @Nullable final String title,
            @Nullable final String body,
            final List<String> tags) {
        final StringBuilder builder = new StringBuilder();
        builder.append(title == null ? "" : title).append('\u0001');
        builder.append(body == null ? "" : body).append('\u0001');
        for (final String tag : tags) {
            builder.append(tag).append('\u0000');
        }
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-1");
            final byte[] hash = digest.digest(builder.toString().getBytes("UTF-8"));
            final StringBuilder hex = new StringBuilder();
            for (final byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (final NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            return builder.toString();
        }
    }
}
