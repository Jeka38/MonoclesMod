package eu.siacs.conversations.xml;

import com.google.common.collect.ImmutableMap;

public interface Node {
	public String getContent();
	public String toString(final ImmutableMap<String, String> ns);
}
