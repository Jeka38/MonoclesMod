package eu.siacs.conversations.xml;

import androidx.annotation.NonNull;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Collectors;

import eu.siacs.conversations.utils.XmlHelper;
import eu.siacs.conversations.xmpp.InvalidJid;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class Element implements Node {
	private final String name;
	private Hashtable<String, String> attributes = new Hashtable<>();
	private List<Element> children = new ArrayList<>();
	private List<Node> childNodes = new ArrayList<>();

	public Element(String name) {
		this.name = name;
	}

	public Element(String name, String xmlns) {
		this.name = name;
		this.setAttribute("xmlns", xmlns);
	}

	public Node prependChild(Node child) {
		childNodes.add(0, child);
		if (child instanceof Element) children.add(0, (Element) child);
		return child;
	}

	public Node addChild(Node child) {
		childNodes.add(child);
		if (child instanceof Element) children.add((Element) child);
		return child;
	}

	public Element addChild(String name) {
		Element child = new Element(name);
		childNodes.add(child);
		children.add(child);
		return child;
	}

	public Element addChild(String name, String xmlns) {
		Element child = new Element(name);
		child.setAttribute("xmlns", xmlns);
		childNodes.add(child);
		children.add(child);
		return child;
	}

	public void addChildren(final Collection<? extends Node> children) {
		if (children == null) return;

		this.childNodes.addAll(children);
		for (Node node : children) {
			if (node instanceof Element) {
				this.children.add((Element) node);
			}
		}
	}

	public void removeChild(Node child) {
		if (child == null) return;

		this.childNodes.remove(child);
		if (child instanceof Element) this.children.remove(child);
	}

	public Element setContent(String content) {
		clearChildren();
		if (content != null) this.childNodes.add(new TextNode(content));
		return this;
	}

	public Element findChild(String name) {
		for (Element child : this.children) {
			if (child.getName().equals(name)) {
				return child;
			}
		}
		return null;
	}

	public String findChildContent(String name) {
		Element element = findChild(name);
		return element == null ? null : element.getContent();
	}

	public LocalizedContent findInternationalizedChildContentInDefaultNamespace(String name) {
		return LocalizedContent.get(this, name);
	}

	public Element findChild(String name, String xmlns) {
		for (Element child : getChildren()) {
			if (name.equals(child.getName()) && xmlns.equals(child.getAttribute("xmlns"))) {
				return child;
			}
		}
		return null;
	}

	public Element findChildEnsureSingle(String name, String xmlns) {
		final List<Element> results = new ArrayList<>();
		for (Element child : getChildren()) {
			if (name.equals(child.getName()) && xmlns.equals(child.getAttribute("xmlns"))) {
				results.add(child);
			}
		}
		if (results.size() == 1) {
			return results.get(0);
		}
		return null;
	}

	public String findChildContent(String name, String xmlns) {
		Element element = findChild(name,xmlns);
		return element == null ? null : element.getContent();
	}

	public boolean hasChild(final String name) {
		return findChild(name) != null;
	}

	public boolean hasChild(final String name, final String xmlns) {
		return findChild(name, xmlns) != null;
	}

	public final List<Element> getChildren() {
		return ImmutableList.copyOf(this.children);
	}

	// Deprecated: you probably want bindTo or replaceChildren
	public Element setChildren(List<Element> children) {
		this.childNodes = new ArrayList(children);
		this.children = new ArrayList(children);
		return this;
	}

	public void replaceChildren(List<Element> children) {
		this.childNodes.clear();
		this.childNodes.addAll(children);
		this.children.clear();
		this.children.addAll(children);
	}

	public void bindTo(Element original) {
		this.attributes = original.attributes;
		this.childNodes = original.childNodes;
		this.children = original.children;
	}

	public final String getContent() {
		return this.childNodes.stream().map(Node::getContent).filter(c -> c != null).collect(Collectors.joining());
	}

	public Element setAttribute(String name, String value) {
		if (name != null && value != null) {
			this.attributes.put(name, value);
		}
		return this;
	}

	public Element setAttribute(String name, Jid value) {
		if (name != null && value != null) {
			this.attributes.put(name, value.toEscapedString());
		}
		return this;
	}

	public String toString() {
		return toString(ImmutableMap.of());
	}

	public String toString(final ImmutableMap<String, String> parentNS) {
		final var mutns = new Hashtable<>(parentNS);
		final var attr = getSerializableAttributes(mutns);
		final StringBuilder elementOutput = new StringBuilder();
		if (childNodes.size() == 0) {
			Tag emptyTag = Tag.empty(name);
			emptyTag.setAttributes(attr);
			elementOutput.append(emptyTag.toString());
		} else {
			final var ns = ImmutableMap.copyOf(mutns);
			Tag startTag = Tag.start(name);
			startTag.setAttributes(attr);
			elementOutput.append(startTag);
			for (Node child : ImmutableList.copyOf(childNodes)) {
				elementOutput.append(child.toString(ns));
			}
			Tag endTag = Tag.end(name);
			elementOutput.append(endTag);
		}
		return elementOutput.toString();
	}

	protected Hashtable<String, String> getSerializableAttributes(Hashtable<String, String> ns) {
		final var result = new Hashtable<String, String>();
		for (final var attr : attributes.entrySet()) {
			if (attr.getKey().charAt(0) == '{') {
				final var uriIdx = attr.getKey().indexOf('}');
				final var uri = attr.getKey().substring(1, uriIdx - 1);
				if (!ns.containsKey(uri)) {
					result.put("ns" + ns.size() + ":xmlns", uri);
					ns.put(uri, "ns" + ns.size());
				}
				result.put(ns.get(uri) + ":" + attr.getKey().substring(uriIdx + 1), attr.getValue());
			} else {
				result.put(attr.getKey(), attr.getValue());
			}
		}

		return result;
	}

	public Element removeAttribute(String name) {
		this.attributes.remove(name);
		return this;
	}

	public Element setAttributes(Hashtable<String, String> attributes) {
		this.attributes = attributes;
		return this;
	}

	public String getAttribute(String name) {
		if (this.attributes.containsKey(name)) {
			return this.attributes.get(name);
		} else {
			return null;
		}
	}

	public Optional<Integer> getOptionalIntAttribute(final String name) {
		final String value = getAttribute(name);
		if (value == null) {
			return Optional.absent();
		}
		return Optional.fromNullable(Ints.tryParse(value));
	}

	public Jid getAttributeAsJid(String name) {
		final String jid = this.getAttribute(name);
		if (jid != null && !jid.isEmpty()) {
			try {
				return Jid.ofEscaped(jid);
			} catch (final IllegalArgumentException e) {
				return InvalidJid.of(jid, this instanceof MessagePacket);
			}
		}
		return null;
	}

	public Hashtable<String, String> getAttributes() {
		return this.attributes;
	}

	public final String getName() {
		return name;
	}

	public void clearChildren() {
		this.children.clear();
		this.childNodes.clear();
	}

	public void setAttribute(String name, long value) {
		this.setAttribute(name, Long.toString(value));
	}

	public void setAttribute(String name, int value) {
		this.setAttribute(name, Integer.toString(value));
	}

	public boolean getAttributeAsBoolean(String name) {
		String attr = getAttribute(name);
		return (attr != null && (attr.equalsIgnoreCase("true") || attr.equalsIgnoreCase("1")));
	}

	public String getNamespace() {
		return getAttribute("xmlns");
	}
}
