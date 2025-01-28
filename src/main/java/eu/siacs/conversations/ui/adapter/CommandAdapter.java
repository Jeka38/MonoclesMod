package eu.siacs.conversations.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;

import eu.siacs.conversations.R;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.databinding.CommandRowBinding;

import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.xml.Element;

public class CommandAdapter extends ArrayAdapter<CommandAdapter.Command> {
    public CommandAdapter(XmppActivity activity) {
        super(activity, 0);
    }

    @Override
    public View getView(int position, View view, @NonNull ViewGroup parent) {
        CommandRowBinding binding = DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), R.layout.command_row, parent, false);
        binding.command.setText(getItem(position).getName());
        return binding.getRoot();
    }

    public interface Command {
        public String getName();
        public void start(final ConversationsActivity activity, final Conversation conversation);
    }

    public static class Command0050 implements Command {
        public final Element el;
        public Command0050(Element el) { this.el = el; }

        public String getName() {
            return el.getAttribute("name");
        }

        public void start(final ConversationsActivity activity, final Conversation conversation) {
            activity.startCommand(conversation.getAccount(), el.getAttributeAsJid("jid"), el.getAttribute("node"));
        }
    }

    public static class MucConfig implements Command {
        public MucConfig() { }

        public String getName() {
            return "⚙️ Configure room";
        }

        public void start(final ConversationsActivity activity, final Conversation conversation) {
            conversation.startMucConfig(activity.xmppConnectionService);
        }
    }
}