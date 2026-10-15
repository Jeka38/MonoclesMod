package eu.siacs.conversations.xmpp.sxe;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

/**
 * XEP-0284 (Shared XML Editing) record model and shared document.
 *
 * <p>A document keeps an unordered set of {@link Record}s (one per DOM node). Records are edited
 * with the {@code <new>}/{@code <set>}/{@code <remove>} vocabulary and can be serialised either as
 * an XEP-0284 state snapshot (a flat list of {@code <new/>} elements) or rebuilt back into an
 * {@link Element} tree.
 *
 * <p>Only the subset the collaborative whiteboard needs is implemented (element/attr/text records,
 * last-writer-wins {@code <set>}); the ordering rule follows the spec ({@code primary-weight} then
 * RID).
 */
public final class SxeDocument {

    public static final String NS = Namespace.SXE;

    public static final class Record {
        public String rid;
        public String type = "element";
        public int version = 0;
        public String parent = "";
        public double primaryWeight = 0.0d;
        public String ns = "";
        public String name = "";
        public String chdata = "";
        public String pitarget = "";
        public String pidata = "";
        public final List<String> children = new ArrayList<>();
    }

    /**
     * A shared root record id. Every whiteboard document uses it so that a {@code <new>} edit (or a
     * state snapshot) from any peer attaches to the same root node — otherwise a client that
     * discovered the board through someone else's edit would hold its own private root and the two
     * drawings would never merge (which is what broke the group board).
     */
    public static final String SHARED_ROOT_RID = "whiteboard-root";

    private final Map<String, Record> records = new LinkedHashMap<>();
    private final Map<String, Double> nextWeight = new HashMap<>();
    private String rootRid;

    public SxeDocument() {
        this(SHARED_ROOT_RID, "svg", "http://www.w3.org/2000/svg");
    }

    public SxeDocument(final String rootRid, final String rootName, final String rootNs) {
        this.rootRid = rootRid;
        final Record root = new Record();
        root.rid = rootRid;
        root.type = "element";
        root.name = rootName;
        root.ns = rootNs;
        records.put(rootRid, root);
    }

    public static String newRid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public String getRootRid() {
        return rootRid;
    }

    public Record getRecord(final String rid) {
        return records.get(rid);
    }

    // ── Building ──────────────────────────────────────────────────────────

    private double weight(final String parent) {
        final double value = nextWeight.getOrDefault(parent, 0.0d) + 1.0d;
        nextWeight.put(parent, value);
        return value;
    }

    private void attach(final Record record) {
        records.put(record.rid, record);
        final Record parent = records.get(record.parent);
        if (parent != null && !parent.children.contains(record.rid)) {
            parent.children.add(record.rid);
        }
    }

    public String addElement(
            final String parent, final String name, final String ns,
            @Nullable final Map<String, String> attrs) {
        final String rid = newRid();
        final Record record = new Record();
        record.rid = rid;
        record.type = "element";
        record.parent = parent;
        record.name = name;
        record.ns = ns == null ? "" : ns;
        record.primaryWeight = weight(parent);
        attach(record);
        if (attrs != null) {
            for (final Map.Entry<String, String> entry : attrs.entrySet()) {
                if (entry.getValue() != null) {
                    addAttr(rid, entry.getKey(), entry.getValue());
                }
            }
        }
        return rid;
    }

    public String addAttr(final String parent, final String name, final String value) {
        final String rid = newRid();
        final Record record = new Record();
        record.rid = rid;
        record.type = "attr";
        record.parent = parent;
        record.name = name;
        record.chdata = value == null ? "" : value;
        record.primaryWeight = weight(parent);
        attach(record);
        return rid;
    }

    @Nullable
    public String findAttr(final String element, final String name) {
        for (final String child : children(element)) {
            final Record record = records.get(child);
            if (record != null && "attr".equals(record.type) && name.equals(record.name)) {
                return child;
            }
        }
        return null;
    }

    public boolean setChdata(final String rid, final String value) {
        final Record record = records.get(rid);
        if (record == null) {
            return false;
        }
        record.chdata = value == null ? "" : value;
        record.version += 1;
        return true;
    }

    public String setAttr(final String element, final String name, final String value) {
        final String rid = findAttr(element, name);
        if (rid == null) {
            return addAttr(element, name, value);
        }
        setChdata(rid, value);
        return rid;
    }

    /** Remove {@code rid} and its descendants; returns the removed record ids (children first). */
    public List<String> remove(final String rid) {
        final Record record = records.get(rid);
        if (record == null) {
            return new ArrayList<>();
        }
        final List<String> removed = new ArrayList<>();
        for (final String child : new ArrayList<>(record.children)) {
            removed.addAll(remove(child));
        }
        final Record parent = records.get(record.parent);
        if (parent != null) {
            parent.children.remove(rid);
        }
        records.remove(rid);
        removed.add(rid);
        return removed;
    }

    public List<String> children(final String rid) {
        final Record record = records.get(rid);
        if (record == null) {
            return new ArrayList<>();
        }
        final List<String> sorted = new ArrayList<>(record.children);
        sorted.sort((a, b) -> {
            final Record ra = records.get(a);
            final Record rb = records.get(b);
            final double wa = ra == null ? 0.0d : ra.primaryWeight;
            final double wb = rb == null ? 0.0d : rb.primaryWeight;
            int cmp = Double.compare(wa, wb);
            if (cmp == 0) {
                cmp = a.compareTo(b);
            }
            return cmp;
        });
        return sorted;
    }

    // ── Local edits → wire ────────────────────────────────────────────────

    /** {@code <new/>} elements for {@code rid} and its subtree (parents first). */
    public List<Element> newElementsFor(final String rid) {
        final List<Element> out = new ArrayList<>();
        final Record record = records.get(rid);
        if (record == null) {
            return out;
        }
        out.add(toNewElement(record));
        for (final String child : children(rid)) {
            out.addAll(newElementsFor(child));
        }
        return out;
    }

    /** The whole document as {@code <new/>} elements (a state snapshot). */
    public List<Element> allNewElements() {
        return newElementsFor(rootRid);
    }

    private static Element toNewElement(final Record record) {
        final Element el = new Element("new", NS);
        el.setAttribute("rid", record.rid);
        el.setAttribute("type", record.type);
        if (record.version != 0) {
            el.setAttribute("version", Integer.toString(record.version));
        }
        if (record.parent != null && !record.parent.isEmpty()) {
            el.setAttribute("parent", record.parent);
        }
        el.setAttribute("primary-weight", formatWeight(record.primaryWeight));
        if ("element".equals(record.type) || "attr".equals(record.type)) {
            if (record.ns != null && !record.ns.isEmpty()) {
                el.setAttribute("ns", record.ns);
            }
            el.setAttribute("name", record.name);
        }
        if ("attr".equals(record.type) || "text".equals(record.type) || "comment".equals(record.type)) {
            el.setAttribute("chdata", record.chdata);
        }
        if ("processinginstruction".equals(record.type)) {
            el.setAttribute("pitarget", record.pitarget);
            el.setAttribute("pidata", record.pidata);
        }
        return el;
    }

    private static String formatWeight(final double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    // ── Wire → local edits ────────────────────────────────────────────────

    public void applyNew(final Element el) {
        final Record record = fromNewElement(el);
        if (record.rid == null || record.rid.isEmpty()) {
            return;
        }
        if ("element".equals(record.type) && (record.parent == null || record.parent.isEmpty())) {
            // A state snapshot carries its own root element. Because every document shares the same
            // root rid, keep our existing root record (and therefore our children) so a received
            // snapshot merges into ours instead of replacing it.
            if (record.rid.equals(rootRid)) {
                final Record existing = records.get(rootRid);
                if (existing != null) {
                    existing.name = record.name;
                    existing.ns = record.ns;
                    return;
                }
            }
            // A foreign/legacy root: adopt it so the received document is rendered from that root.
            final String oldRoot = rootRid;
            rootRid = record.rid;
            records.put(record.rid, record);
            if (!oldRoot.equals(record.rid)) {
                records.remove(oldRoot);
            }
            return;
        }
        if ("attr".equals(record.type) && rootRid.equals(record.parent)) {
            // A merged snapshot re-announces the root attributes that every client seeds (viewBox/
            // version/baseProfile). Fold a same-named root attribute into the existing one instead
            // of piling up duplicates. Only the root is deduped: shape attributes are referenced by
            // rid on the wire and must keep their identity.
            final String existing = findAttr(rootRid, record.name);
            if (existing != null && !existing.equals(record.rid)) {
                setChdata(existing, record.chdata);
                return;
            }
        }
        records.put(record.rid, record);
        final Record parent = records.get(record.parent);
        if (parent != null && !parent.children.contains(record.rid)) {
            parent.children.add(record.rid);
        }
        final double current = nextWeight.getOrDefault(record.parent, 0.0d);
        nextWeight.put(record.parent, Math.max(current, record.primaryWeight));
    }

    public void applySet(final Element el) {
        final String target = el.getAttribute("target");
        final Record record = target == null ? null : records.get(target);
        if (record == null) {
            return;
        }
        if (el.getAttribute("parent") != null) {
            record.parent = el.getAttribute("parent");
        }
        if (el.getAttribute("ns") != null) {
            record.ns = el.getAttribute("ns");
        }
        if (el.getAttribute("name") != null) {
            record.name = el.getAttribute("name");
        }
        if (el.getAttribute("chdata") != null) {
            record.chdata = el.getAttribute("chdata");
        }
        if (el.getAttribute("pitarget") != null) {
            record.pitarget = el.getAttribute("pitarget");
        }
        if (el.getAttribute("pidata") != null) {
            record.pidata = el.getAttribute("pidata");
        }
        if (el.getAttribute("primary-weight") != null) {
            try {
                record.primaryWeight = Double.parseDouble(el.getAttribute("primary-weight"));
            } catch (final NumberFormatException ignored) {
            }
        }
        if (el.getAttribute("parent") != null) {
            for (final Record candidate : records.values()) {
                if (!candidate.rid.equals(record.parent) && candidate.children.remove(record.rid)) {
                    // detached from the old parent
                }
            }
            final Record parent = records.get(record.parent);
            if (parent != null && !parent.children.contains(record.rid)) {
                parent.children.add(record.rid);
            }
        }
        record.version += 1;
    }

    public void applyRemove(final Element el) {
        final String target = el.getAttribute("target");
        if (target != null) {
            remove(target);
        }
    }

    public void clearShapes() {
        final Record root = records.get(rootRid);
        if (root == null) {
            return;
        }
        for (final String child : new ArrayList<>(root.children)) {
            remove(child);
        }
        root.children.clear();
    }

    private static Record fromNewElement(final Element el) {
        final Record record = new Record();
        record.rid = el.getAttribute("rid");
        record.type = el.getAttribute("type") == null ? "element" : el.getAttribute("type");
        try {
            record.version = Integer.parseInt(el.getAttribute("version") == null ? "0" : el.getAttribute("version"));
        } catch (final NumberFormatException ignored) {
        }
        record.parent = el.getAttribute("parent") == null ? "" : el.getAttribute("parent");
        try {
            record.primaryWeight = Double.parseDouble(el.getAttribute("primary-weight") == null ? "0" : el.getAttribute("primary-weight"));
        } catch (final NumberFormatException ignored) {
        }
        record.ns = el.getAttribute("ns") == null ? "" : el.getAttribute("ns");
        record.name = el.getAttribute("name") == null ? "" : el.getAttribute("name");
        record.chdata = el.getAttribute("chdata") == null ? "" : el.getAttribute("chdata");
        record.pitarget = el.getAttribute("pitarget") == null ? "" : el.getAttribute("pitarget");
        record.pidata = el.getAttribute("pidata") == null ? "" : el.getAttribute("pidata");
        return record;
    }

    // ── Rendering ─────────────────────────────────────────────────────────

    @Nullable
    public Element toElement() {
        return elementFor(rootRid);
    }

    @Nullable
    private Element elementFor(final String rid) {
        final Record record = records.get(rid);
        if (record == null || !"element".equals(record.type)) {
            return null;
        }
        final Element el = new Element(record.name);
        if (record.ns != null && !record.ns.isEmpty()) {
            el.setAttribute("xmlns", record.ns);
        }
        for (final String child : children(rid)) {
            final Record childRecord = records.get(child);
            if (childRecord != null && "attr".equals(childRecord.type)) {
                el.setAttribute(childRecord.name, childRecord.chdata);
            }
        }
        final StringBuilder text = new StringBuilder();
        for (final String child : children(rid)) {
            final Record childRecord = records.get(child);
            if (childRecord == null) {
                continue;
            }
            if ("element".equals(childRecord.type)) {
                final Element sub = elementFor(child);
                if (sub != null) {
                    el.addChild(sub);
                }
            } else if ("text".equals(childRecord.type)) {
                text.append(childRecord.chdata);
            }
        }
        if (text.length() > 0) {
            el.setContent(text.toString());
        }
        return el;
    }

    public String toXml() {
        final Element el = toElement();
        return el == null ? "" : el.toString();
    }
}
