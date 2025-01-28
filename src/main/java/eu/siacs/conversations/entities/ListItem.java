package eu.siacs.conversations.entities;

import android.content.Context;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.entities.Account;

public interface ListItem extends Comparable<ListItem>, AvatarService.Avatarable  {
    String getDisplayName();

    int getOffline();

    Jid getJid();

    Account getAccount();

    List<Tag> getTags(Context context);

    boolean getActive();

    final class Tag implements Serializable {
        private final String name;
        private final int color;
        private final int offline;
        private final Account account;
        private final boolean active;

        public Tag(final String name, final int color, final int offline, final Account account, final boolean active) {
            this.name = name;
            this.color = color;
            this.offline = offline;
            this.account = account;
            this.active = active;
        }

        public int getColor() {
            return this.color;
        }

        public String getName() {
            return this.name;
        }

        public int getOffline() {
            return this.offline;
        }

        public Account getAccount() {
            return this.account;
        }

        public boolean getActive() {
            return this.active;
        }

        public String toString() {
            return getName();
        }

        public boolean equals(Object o) {
            if (!(o instanceof Tag)) return false;
            Tag ot = (Tag) o;
            return name.toLowerCase(Locale.US).equals(ot.getName().toLowerCase(Locale.US)) && color == ot.getColor();
        }

        public int hashCode() {
            return name.toLowerCase(Locale.US).hashCode();
        }
    }

    boolean match(Context context, final String needle);
}
