package eu.siacs.conversations.entities;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.utils.StringUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.InvalidJid;
import eu.siacs.conversations.xmpp.Jid;

public class Bookmark extends Element implements ListItem {

    private final Account account;
    private WeakReference<Conversation> conversation;
    private Jid jid;
    protected Element extensions = new Element("extensions", Namespace.BOOKMARKS2);


    public Bookmark(final Account account, final Jid jid) {
        super("conference");
        this.jid = jid;
        this.setAttribute("jid", jid);
        this.account = account;
    }

    private Bookmark(Account account) {
        super("conference");
        this.account = account;
    }

    public static Map<Jid, Bookmark> parseFromStorage(Element storage, Account account) {
        if (storage == null) {
            return Collections.emptyMap();
        }
        final HashMap<Jid, Bookmark> bookmarks = new HashMap<>();
        for (final Element item : storage.getChildren()) {
            if (item.getName().equals("conference")) {
                final Bookmark bookmark = Bookmark.parse(item, account);
                if (bookmark != null) {
                    final Bookmark old = bookmarks.put(bookmark.jid, bookmark);
                    if (old != null && old.getBookmarkName() != null && bookmark.getBookmarkName() == null) {
                        bookmark.setBookmarkName(old.getBookmarkName());
                    }
                }
            }
        }
        return bookmarks;
    }

    public static Map<Jid, Bookmark> parseFromPubsub(Element pubsub, Account account) {
        if (pubsub == null) {
            return Collections.emptyMap();
        }
        final Element items = pubsub.findChild("items");
        if (items != null && Namespace.BOOKMARKS2.equals(items.getAttribute("node"))) {
            final Map<Jid, Bookmark> bookmarks = new HashMap<>();
            for (Element item : items.getChildren()) {
                if (item.getName().equals("item")) {
                    final Bookmark bookmark = Bookmark.parseFromItem(item, account);
                    if (bookmark != null) {
                        bookmarks.put(bookmark.jid, bookmark);
                    }
                }
            }
            return bookmarks;
        }
        return Collections.emptyMap();
    }

    public static Bookmark parse(Element element, Account account) {
        Bookmark bookmark = new Bookmark(account);
        bookmark.setAttributes(element.getAttributes());
        bookmark.setChildren(element.getChildren());
        bookmark.jid = InvalidJid.getNullForInvalid(bookmark.getAttributeAsJid("jid"));
        if (bookmark.jid == null) {
            return null;
        }
        return bookmark;
    }

    public static Bookmark parseFromItem(Element item, Account account) {
        final Element conference = item.findChild("conference", Namespace.BOOKMARKS2);
        if (conference == null) {
            return null;
        }
        final Bookmark bookmark = new Bookmark(account);
        bookmark.jid = InvalidJid.getNullForInvalid(item.getAttributeAsJid("id"));
        if (bookmark.jid == null) {
            return null;
        }
        bookmark.setBookmarkName(conference.getAttribute("name"));
        bookmark.setAutojoin(conference.getAttributeAsBoolean("autojoin"));
        bookmark.setNick(conference.findChildContent("nick"));
        bookmark.setPassword(conference.findChildContent("password"));
        final Element extensions = conference.findChild("extensions", Namespace.BOOKMARKS2);
        if (extensions != null) {
            for (final Element ext : extensions.getChildren()) {
                if (ext.getName().equals("group") && ext.getNamespace().equals("jabber:iq:roster")) {
                    bookmark.addGroup(ext.getContent());
                }
            }
            bookmark.extensions = extensions;
        }
        return bookmark;
    }

    public Element getExtensions() {
        return extensions;
    }

    public void addGroup(final String group) {
        addChild("group", "jabber:iq:roster").setContent(group);
        extensions.addChild("group", "jabber:iq:roster").setContent(group);
    }

    public void setGroups(List<String> groups) {
        final List<Element> children = new ArrayList<>(getChildren());
        for (final Element el : children) {
            if (el.getName().equals("group")) {
                removeChild(el);
            }
        }

        final List<Element> extChildren = new ArrayList<>(extensions.getChildren());
        for (final Element el : extChildren) {
            if (el.getName().equals("group")) {
                extensions.removeChild(el);
            }
        }

        for (final String group : groups) {
            addGroup(group);
        }
    }

    public void setAutojoin(boolean autojoin) {
        if (autojoin) {
            this.setAttribute("autojoin", "true");
        } else {
            this.setAttribute("autojoin", "false");
        }
    }

    @Override
    public int compareTo(final @NonNull ListItem another) {
        if (getJid().isDomainJid() && !another.getJid().isDomainJid()) {
            return -1;
        } else if (!getJid().isDomainJid() && another.getJid().isDomainJid()) {
            return 1;
        }

        return this.getDisplayName().compareToIgnoreCase(
                another.getDisplayName());
    }

    @Override
    public String getDisplayName() {
        final Conversation c = getConversation();
        final String name = getBookmarkName();
        if (c != null) {
            return c.getName().toString();
        } else if (printableValue(name, false)) {
            return name.trim();
        } else {
            Jid jid = this.getJid();
            return jid != null && jid.getLocal() != null ? jid.getLocal() : "";
        }
    }

    @Override
    public int getOffline() {
        return 0;
    }

    public static boolean printableValue(@Nullable String value, boolean permitNone) {
        return value != null && !value.trim().isEmpty() && (permitNone || !"None".equals(value));
    }

    public static boolean printableValue(@Nullable String value) {
        return printableValue(value, true);
    }

    @Override
    public Jid getJid() {
        return this.jid;
    }

    public Jid getFullJid() {
        return getFullJid(getNick(), true);
    }

    private Jid getFullJid(final String nick, boolean tryFix) {
        try {
            return jid == null || nick == null || nick.trim().isEmpty() ? jid : jid.withResource(nick);
        } catch (final IllegalArgumentException e) {
            try {
                return tryFix ? getFullJid(gnu.inet.encoding.Punycode.encode(nick), false) : null;
            } catch (final Exception e2) {
                return null;
            }
        }
    }

    public List<Tag> getGroupTags() {
        ArrayList<Tag> tags = new ArrayList<>();

        for (Element element : getChildren()) {
            if (element.getName().equals("group") && element.getContent() != null) {
                String group = element.getContent();
                tags.add(new Tag(group, UIHelper.getColorForName(group, true), 0, account, true));
            }
        }

        return tags;
    }

    @Override
    public List<Tag> getTags(Context context) {
        ArrayList<Tag> tags = new ArrayList<>();
        tags.add(new Tag("group", UIHelper.getColorForName("Channel",true), 0, account, true));
        tags.addAll(getGroupTags());
        return tags;
    }

    @Override
    public boolean getActive() {
        return false;
    }

    public String getNick() {
        return this.findChildContent("nick");
    }

    public void setNick(String nick) {
        Element element = this.findChild("nick");
        if (element == null) {
            element = this.addChild("nick");
        }
        element.setContent(nick);
    }

    public boolean autojoin() {
        return this.getAttributeAsBoolean("autojoin");
    }

    public String getPassword() {
        return this.findChildContent("password");
    }

    public void setPassword(String password) {
        Element element = this.findChild("password");
        if (element != null) {
            element.setContent(password);
        }
    }

    @Override
    public boolean match(Context context, String needle) {
        if (needle == null) {
            return true;
        }
        needle = needle.toLowerCase(Locale.US);
        String[] parts = needle.split("[,\\s]+");
        if (parts.length > 1) {
            for (String part : parts) {
                if (!match(context, part)) {
                    return false;
                }
            }
            return true;
        } else if (parts.length > 0) {
            final Jid jid = getJid();
            return (jid != null && jid.toString().contains(parts[0])) ||
                    getDisplayName().toLowerCase(Locale.US).contains(parts[0]) ||
                    matchInTag(context, parts[0]);
        } else {
            final Jid jid = getJid();
            return (jid != null && jid.toString().contains(needle)) ||
                    getDisplayName().toLowerCase(Locale.US).contains(needle);
        }
    }

    private boolean matchInTag(Context context, String needle) {
        needle = needle.toLowerCase(Locale.US);
        for (Tag tag : getTags(context)) {
            if (tag.getName().toLowerCase(Locale.US).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    public Account getAccount() {
        return this.account;
    }

    public synchronized Conversation getConversation() {
        return this.conversation != null ? this.conversation.get() : null;
    }

    public synchronized void setConversation(Conversation conversation) {
        if (this.conversation != null) {
            this.conversation.clear();
        }
        if (conversation == null) {
            this.conversation = null;
        } else {
            this.conversation = new WeakReference<>(conversation);
            conversation.getMucOptions().notifyOfBookmarkNick(getNick());
        }
    }

    public String getBookmarkName() {
        return this.getAttribute("name");
    }

    public boolean setBookmarkName(String name) {
        String before = getBookmarkName();
        if (name != null) {
            this.setAttribute("name", name);
        } else {
            this.removeAttribute("name");
        }
        return StringUtils.changed(before, name);
    }

    @Override
    public int getAvatarBackgroundColor() {
        return UIHelper.getColorForName(jid != null ? jid.asBareJid().toString() : getDisplayName());
    }

	@Override
	public String getAvatarName() {
		return getDisplayName();
	}
}