package eu.siacs.conversations.ui.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.Jid;

/**
 * Shows who reacted to a message (XEP-0444) as a scrollable list: avatar, nickname and the emojis
 * that person used. Opened on a long press on the reaction chips under a message.
 */
public final class ReactionDetailsDialog {

    private ReactionDetailsDialog() {
    }

    private static final class Reactor {
        final String name;
        final String emojis;
        final AvatarService.Avatarable avatarable;
        final String seed;

        Reactor(String name, String emojis, AvatarService.Avatarable avatarable, String seed) {
            this.name = name;
            this.emojis = emojis;
            this.avatarable = avatarable;
            this.seed = seed;
        }
    }

    public static void show(final XmppActivity activity, final Message message) {
        if (activity == null || message == null || !(message.getConversation() instanceof Conversation)) {
            return;
        }
        final Conversation conversation = (Conversation) message.getConversation();
        final List<Message> carriers = conversation.getReactionCarriers(message);
        if (carriers.isEmpty()) {
            return;
        }
        final List<Reactor> reactors = new ArrayList<>();
        for (final Message carrier : carriers) {
            reactors.add(resolve(activity, conversation, carrier));
        }
        final ReactorAdapter adapter = new ReactorAdapter(activity, reactors);
        final String title = activity.getResources().getQuantityString(
                R.plurals.reaction_count, reactors.size(), reactors.size());
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setAdapter(adapter, null)
                .setPositiveButton(R.string.cancel, null)
                .show();
    }

    private static Reactor resolve(final Context context, final Conversation conversation, final Message carrier) {
        final boolean isSelf = carrier.getStatus() > Message.STATUS_RECEIVED;
        final Jid counterpart = carrier.getCounterpart();
        final Set<String> emojis = carrier.getReactionEmojis();
        final String emojiText = String.join(" ", emojis);
        if (isSelf) {
            // In a MUC use our own room nick instead of a generic "Me"; elsewhere fall back to it.
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                final MucOptions mucOptions = conversation.getMucOptions();
                final MucOptions.User self = mucOptions.getSelf();
                final String nick = mucOptions.getActualNick();
                if (nick != null && !nick.isEmpty()) {
                    return new Reactor(nick, emojiText, self,
                            self.getRealJid() != null ? self.getRealJid().asBareJid().toString() : null);
                }
            }
            return new Reactor(context.getString(R.string.me), emojiText, conversation.getAccount(), null);
        }
        // In a MUC (including MUC private messages) the reactor is an occupant; resolve the nick
        // and avatar from the participant list when possible.
        if (conversation.getMode() == Conversation.MODE_MULTI && counterpart != null) {
            final MucOptions.User user = conversation.getMucOptions().findUserByFullJid(counterpart);
            if (user != null) {
                return new Reactor(user.getNick(), emojiText, user,
                        user.getRealJid() != null ? user.getRealJid().asBareJid().toString() : null);
            }
        }
        final Contact contact = conversation.getContact();
        if (contact != null && conversation.getMode() == Conversation.MODE_SINGLE) {
            return new Reactor(contact.getDisplayName(), emojiText, contact, null);
        }
        final String fallback = counterpart == null
                ? conversation.getName().toString()
                : UIHelper.getDisplayedMucCounterpart(counterpart);
        return new Reactor(fallback, emojiText, null,
                counterpart == null ? null : counterpart.asBareJid().toString());
    }

    private static final class ReactorAdapter extends BaseAdapter {
        private final XmppActivity activity;
        private final List<Reactor> reactors;

        ReactorAdapter(final XmppActivity activity, final List<Reactor> reactors) {
            this.activity = activity;
            this.reactors = reactors;
        }

        @Override
        public int getCount() {
            return reactors.size();
        }

        @Override
        public Object getItem(int position) {
            return reactors.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            final View view = convertView != null
                    ? convertView
                    : LayoutInflater.from(activity).inflate(R.layout.reaction_reactor_item, parent, false);
            final Reactor reactor = reactors.get(position);
            final ImageView photo = view.findViewById(R.id.reactor_photo);
            final TextView name = view.findViewById(R.id.reactor_name);
            final TextView reaction = view.findViewById(R.id.reactor_reaction);
            name.setText(reactor.name);
            reaction.setText(reactor.emojis);
            loadAvatar(photo, reactor);
            return view;
        }

        private void loadAvatar(final ImageView imageView, final Reactor reactor) {
            if (reactor.avatarable != null) {
                AvatarWorkerTask.loadAvatar(reactor.avatarable, imageView, R.dimen.avatar);
                return;
            }
            final AvatarService service = activity.avatarService();
            if (service == null) {
                return;
            }
            final Drawable drawable = service.get(reactor.name, reactor.seed,
                    (int) activity.getResources().getDimension(R.dimen.avatar), false);
            imageView.setImageDrawable(drawable);
            imageView.setBackgroundColor(0x00000000);
        }
    }
}
