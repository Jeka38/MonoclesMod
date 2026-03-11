package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.xmpp.Jid;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class PrivateMucConversationFragment extends ConversationFragment {

    private Jid counterpart;

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(Config.LOGTAG, "PrivateMucConversationFragment.onCreateView()");
        View view = super.onCreateView(inflater, container, savedInstanceState);
        if (getArguments() != null) {
            String jid = getArguments().getString("counterpart");
            if (jid != null) {
                try {
                    this.counterpart = Jid.of(jid);
                } catch (IllegalArgumentException e) {
                    this.counterpart = null;
                }
            }
        }
        return view;
    }

    @Override
    protected void populateMessageList() {
        if (this.conversation != null) {
            Log.d(Config.LOGTAG, "PrivateMucConversationFragment.populateMessageList()");
            conversation.populateWithMessages(this.messageList, activity == null ? null : activity.xmppConnectionService);
            int before = this.messageList.size();
            // Filter messages to only show private messages with the specific counterpart
            for (java.util.Iterator<Message> i = this.messageList.iterator(); i.hasNext(); ) {
                Message m = i.next();
                if (m.getType() == Message.TYPE_STATUS) {
                    continue;
                }
                boolean remove;
                if (m.getType() != Message.TYPE_PRIVATE && m.getType() != Message.TYPE_PRIVATE_FILE) {
                    remove = true;
                } else {
                    remove = counterpart != null && !counterpart.equals(m.getCounterpart());
                }
                if (remove) {
                    i.remove();
                }
            }
            Log.d(Config.LOGTAG, "Filtered messages: " + before + " -> " + this.messageList.size());
            updateStatusMessages();
        }
    }

    @Override
    public void updateChatMsgHint() {
        if (this.binding == null) {
            return;
        }
        if (conversation != null && counterpart != null) {
            this.binding.textInputHint.setVisibility(View.GONE);
            this.binding.textinput.setHint(counterpart.getResource());
        } else {
            super.updateChatMsgHint();
        }
    }

    @Override
    protected void sendMessage(Long sendAt) {
        if (conversation != null && counterpart != null) {
            Log.d(Config.LOGTAG, "PrivateMucConversationFragment.sendMessage() to " + counterpart);
            conversation.setNextCounterpart(counterpart);
        }
        super.sendMessage(sendAt);
    }
}
