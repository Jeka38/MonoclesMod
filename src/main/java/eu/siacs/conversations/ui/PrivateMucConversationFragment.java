package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.xmpp.Jid;

public class PrivateMucConversationFragment extends ConversationFragment {

    private Jid counterpart;

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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
    protected void refresh(boolean notifyConversationRead) {
        synchronized (this.messageList) {
            if (this.conversation != null) {
                conversation.populateWithMessages(this.messageList, activity == null ? null : activity.xmppConnectionService);
                // Filter messages to only show private messages with the specific counterpart
                this.messageList.removeIf(m -> {
                    if (m.getType() == Message.TYPE_STATUS) {
                        return false;
                    }
                    if (m.getType() != Message.TYPE_PRIVATE && m.getType() != Message.TYPE_PRIVATE_FILE) {
                        return true;
                    }
                    return counterpart != null && !counterpart.equals(m.getCounterpart());
                });
                updateStatusMessages();
                if (conversation.unreadCount() > 0) {
                    binding.unreadCountCustomView.setVisibility(View.VISIBLE);
                    binding.unreadCountCustomView.setUnreadCount(conversation.unreadCount());
                }
                this.messageListAdapter.notifyDataSetChanged();
                updateChatMsgHint();
                if (notifyConversationRead && activity != null) {
                    binding.messagesView.post(this::fireReadEvent);
                }
                updateSendButton();
                updateEditablity();
                conversation.refreshSessions();
            }
        }
    }

    @Override
    public void updateChatMsgHint() {
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
            conversation.setNextCounterpart(counterpart);
        }
        super.sendMessage(sendAt);
    }
}
