package eu.siacs.conversations.entities;

import android.content.Context;

import java.util.Collections;
import java.util.List;

import eu.siacs.conversations.xmpp.Jid;

public class GroupHeader implements ListItem {

    private final String name;

    public GroupHeader(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    @Override
    public int getOffline() {
        return 0;
    }

    @Override
    public Jid getJid() {
        return null;
    }

    @Override
    public Account getAccount() {
        return null;
    }

    @Override
    public List<Tag> getTags(Context context) {
        return Collections.emptyList();
    }

    @Override
    public boolean getActive() {
        return false;
    }

    @Override
    public String getAvatarName() {
        return name;
    }

    @Override
    public int getAvatarBackgroundColor() {
        return 0;
    }

    @Override
    public boolean match(Context context, String needle) {
        return false;
    }

    @Override
    public int compareTo(ListItem another) {
        return 0;
    }
}