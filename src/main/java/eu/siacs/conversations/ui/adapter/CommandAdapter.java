package eu.siacs.conversations.ui.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;

import java.util.Objects;

import eu.siacs.conversations.R;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.databinding.CommandRowBinding;
import eu.siacs.conversations.entities.Conversation;

public class CommandAdapter extends ArrayAdapter<CommandAdapter.Command> {
    public CommandAdapter(XmppActivity activity) {
        super(activity, 0);
    }

    @Override
    public View getView(int position, View view, @NonNull ViewGroup parent) {
        @SuppressLint("ViewHolder") CommandRowBinding binding = DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), R.layout.command_row, parent, false);
        binding.command.setText(Objects.requireNonNull(getItem(position)).getName(parent.getContext())); // Pass context to get localized name
        return binding.getRoot();
    }

    public interface Command {
        String getName(@NonNull android.content.Context context); // Modify to accept context for localization
        void start(final ConversationsActivity activity, final Conversation conversation);
    }

    public static class Command0050 implements Command {
        public final Element el;
        public Command0050(Element el) { this.el = el; }

        public String getName(@NonNull android.content.Context context) {
            return el.getAttribute("name");
        }

        public void start(final ConversationsActivity activity, final Conversation conversation) {
            activity.startCommand(conversation.getAccount(), el.getAttributeAsJid("jid"), el.getAttribute("node"));
        }
    }

    public static class MucConfig implements Command {
        public MucConfig() { }

        public String getName(@NonNull android.content.Context context) {
            // Use context to fetch localized string
            return context.getString(R.string.configure_room); // Localized string from resources
        }

        public void start(final ConversationsActivity activity, final Conversation conversation) {
            conversation.startMucConfig(activity.xmppConnectionService);
        }
    }
}
