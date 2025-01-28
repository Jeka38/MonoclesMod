package eu.siacs.conversations.ui.adapter;

import static de.monocles.chat.Util.getReadmakerType;
import static eu.siacs.conversations.ui.util.MyLinkify.replaceYoutube;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.base.Optional;
import com.google.common.base.Strings;

import java.util.List;

import de.monocles.chat.Util;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ConversationListRowBinding;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.AttachFileToConversationRunnable;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.util.StyledAttributes;
import eu.siacs.conversations.utils.IrregularUnicodeDetector;
import eu.siacs.conversations.utils.MimeUtils;
import eu.siacs.conversations.utils.StylingHelper;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.jingle.OngoingRtpSession;

public class ConversationAdapter
        extends RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder> {

    private static final float INACTIVE_ALPHA = 0.4684f;
    private static final float ACTIVE_ALPHA = 1.0f;
    private XmppActivity activity;
    private List<Conversation> conversations;
    private OnConversationClickListener listener;
    private boolean hasInternetConnection = false;
    private String readmarkervalue;

    public ConversationAdapter(XmppActivity activity, List<Conversation> conversations) {
        this.activity = activity;
        this.conversations = conversations;
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
        this.readmarkervalue = sharedPref.getString("readmarker_style", "blue_readmarkers");
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ConversationViewHolder(
                DataBindingUtil.inflate(
                        LayoutInflater.from(parent.getContext()),
                        R.layout.conversation_list_row,
                        parent,
                        false));
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder viewHolder, int position) {
        Conversation conversation = conversations.get(position);
        if (conversation == null) {
            return;
        }
        String UUID = conversation.getUuid();
        CharSequence name = conversation.getName();
        hasInternetConnection = activity.xmppConnectionService.hasInternetConnection();
        if (name instanceof Jid) {
            viewHolder.binding.conversationName.setText(
                    IrregularUnicodeDetector.style(activity, (Jid) name));
        } else {
            viewHolder.binding.conversationName.setText(name);
        }

        if (activity.xmppConnectionService.multipleAccounts() && activity.xmppConnectionService.showOwnAccounts()) {
            viewHolder.binding.account.setVisibility(View.VISIBLE);
            viewHolder.binding.account.setText(conversation.getAccount().getJid().asBareJid());
        } else {
            viewHolder.binding.account.setVisibility(View.GONE);
        }

        if (activity.xmppConnectionService != null && activity.xmppConnectionService.getAccounts().size() > 1) {
            viewHolder.binding.frame.setBackgroundColor(conversation.getAccount().getColor(activity.isDarkTheme()));
        } else {
            viewHolder.binding.frame.setBackgroundColor(StyledAttributes.getColor(this.activity, R.attr.color_background_secondary));
        }

        final Message message = conversation.getLatestMessage();
        final int failedCount = conversation.failedCount();
        final int unreadCount = conversation.unreadCount();
        final boolean isRead = conversation.isRead();
        final Conversation.Draft draft = isRead ? conversation.getDraft() : null;

        viewHolder.binding.indicatorReceived.setVisibility(View.GONE);
        viewHolder.binding.unreadCount.setVisibility(View.GONE);
        viewHolder.binding.failedCount.setVisibility(View.GONE);

        if (isRead) {
            viewHolder.binding.conversationName.setTypeface(null, Typeface.NORMAL);
        } else {
            viewHolder.binding.conversationName.setTypeface(null, Typeface.BOLD);
        }

        if (unreadCount > 0) {
            viewHolder.binding.unreadCount.setVisibility(View.VISIBLE);
            viewHolder.binding.unreadCount.setUnreadCount(unreadCount);
        } else {
            viewHolder.binding.unreadCount.setVisibility(View.GONE);
        }
        if (failedCount > 0) {
            viewHolder.binding.failedCount.setVisibility(View.VISIBLE);
            viewHolder.binding.failedCount.setFailedCount(failedCount);
        } else {
            viewHolder.binding.failedCount.setVisibility(View.GONE);
        }

        if (draft != null) {
            viewHolder.binding.conversationLastmsgImg.setVisibility(View.GONE);
            viewHolder.binding.conversationLastmsg.setText(draft.getMessage());
            viewHolder.binding.senderName.setText(R.string.draft);
            viewHolder.binding.senderName.setVisibility(View.VISIBLE);
            viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.NORMAL);
            viewHolder.binding.senderName.setTypeface(null, Typeface.ITALIC);
        } else if (conversation.getMode() == Conversation.MODE_SINGLE && conversation.getIncomingChatState().equals(ChatState.COMPOSING)) {
            viewHolder.binding.conversationLastmsg.setText(R.string.is_typing);
            viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.BOLD_ITALIC);
            viewHolder.binding.conversationLastmsgImg.setVisibility(View.GONE);
            viewHolder.binding.senderName.setVisibility(View.GONE);
        } else if (conversation.getMode() == Conversation.MODE_MULTI && conversation.getMucOptions().getUsersWithChatState(ChatState.COMPOSING, 5).size() != 0) {
            ChatState state = ChatState.COMPOSING;
            List<MucOptions.User> userWithChatStates = conversation.getMucOptions().getUsersWithChatState(state, 5);
            if (userWithChatStates.size() == 0) {
                state = ChatState.PAUSED;
                userWithChatStates = conversation.getMucOptions().getUsersWithChatState(state, 5);
            }
            if (state == ChatState.COMPOSING) {
                viewHolder.binding.senderName.setVisibility(View.GONE);
                viewHolder.binding.conversationLastmsgImg.setVisibility(View.GONE);
                if (userWithChatStates.size() > 0) {
                    if (userWithChatStates.size() == 1) {
                        MucOptions.User user = userWithChatStates.get(0);
                        viewHolder.binding.conversationLastmsg.setText(activity.getString(R.string.contact_is_typing, UIHelper.getDisplayName(user)));
                        viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.BOLD_ITALIC);
                    } else {
                        StringBuilder builder = new StringBuilder();
                        for (MucOptions.User user : userWithChatStates) {
                            if (builder.length() != 0) {
                                builder.append(", ");
                            }
                            builder.append(UIHelper.getDisplayName(user));
                        }
                        viewHolder.binding.conversationLastmsg.setText(activity.getString(R.string.contacts_are_typing, builder.toString()));
                        viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.BOLD_ITALIC);
                    }
                }
            }
        } else if (UUID.equalsIgnoreCase(AttachFileToConversationRunnable.isCompressingVideo[0])) {
            viewHolder.binding.conversationLastmsgImg.setVisibility(View.GONE);
            viewHolder.binding.conversationLastmsg.setText(activity.getString(R.string.transcoding_video_x, AttachFileToConversationRunnable.isCompressingVideo[1]));
            viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.ITALIC);
            viewHolder.binding.senderName.setVisibility(View.GONE);
        } else {
            final boolean fileAvailable = !message.isFileDeleted();
            final boolean showPreviewText;
            if (fileAvailable
                    && (message.isFileOrImage()
                    || message.treatAsDownloadable()
                    || message.isGeoUri())) {
                final int imageResource;
                if (message.isGeoUri()) {
                    imageResource =
                            activity.getThemeResource(R.attr.share_location, R.drawable.rounded_location_black_24);
                    showPreviewText = false;
                } else {
                    // TODO move this into static MediaPreview method and use same icons as in
                    // MediaAdapter
                    final String mime = message.getMimeType();
                    if (MimeUtils.AMBIGUOUS_CONTAINER_FORMATS.contains(mime)) {
                        final Message.FileParams fileParams = message.getFileParams();
                        if (fileParams.width > 0 && fileParams.height > 0) {
                            imageResource =
                                    activity.getThemeResource(R.attr.take_video, R.drawable.outline_videocam_black_24);
                            showPreviewText = false;
                        } else if (fileParams.runtime > 0) {
                            imageResource =
                                    activity.getThemeResource(
                                            R.attr.ic_send_voice_offline, R.drawable.ic_send_voice_offline);
                            showPreviewText = false;
                        } else {
                            imageResource =
                                    activity.getThemeResource(
                                            R.attr.document_file, R.drawable.document_black_24);
                            showPreviewText = true;
                        }
                    } else {
                        switch (Strings.nullToEmpty(mime).split("/")[0]) {
                            case "image":
                                imageResource =
                                        activity.getThemeResource(
                                                R.attr.outline_photo, R.drawable.ic_attach_photo);
                                showPreviewText = false;
                                break;
                            case "video":
                                imageResource =
                                        activity.getThemeResource(
                                                R.attr.attach_video_file, R.drawable.outline_video_file_black_24);
                                showPreviewText = false;
                                break;
                            case "audio":
                                imageResource =
                                        activity.getThemeResource(
                                                R.attr.ic_send_voice_offline, R.drawable.ic_send_voice_offline);
                                showPreviewText = false;
                                break;
                            default:
                                imageResource =
                                        activity.getThemeResource(R.attr.choose_file, R.drawable.choose_file_black_24dp);
                                showPreviewText = true;
                                break;
                        }
                    }
                }
                viewHolder.binding.conversationLastmsgImg.setImageResource(imageResource);
                viewHolder.binding.conversationLastmsgImg.setVisibility(View.VISIBLE);
            } else {
                viewHolder.binding.conversationLastmsgImg.setVisibility(View.GONE);
                showPreviewText = true;
            }
            final Pair<CharSequence, Boolean> preview =
                    UIHelper.getMessagePreview(
                            activity.xmppConnectionService,
                            message,
                            viewHolder.binding.conversationLastmsg.getCurrentTextColor());
            if (showPreviewText) {
                if (message.hasDeletedBody()) {
                    viewHolder.binding.conversationLastmsg.setText(UIHelper.shorten(activity.getString(R.string.message_deleted)));
                } else {
                    SpannableStringBuilder body = new SpannableStringBuilder(replaceYoutube(activity.getApplicationContext(), preview.first.toString()));
                    StylingHelper.format(body, viewHolder.binding.conversationLastmsg.getCurrentTextColor(), true);
                    viewHolder.binding.conversationLastmsg.setText(UIHelper.shorten(body));
                }
            } else {
                viewHolder.binding.conversationLastmsgImg.setContentDescription(preview.first);
            }
            viewHolder.binding.conversationLastmsg.setVisibility(
                    showPreviewText ? View.VISIBLE : View.GONE);
            if (preview.second) {
                if (isRead) {
                    viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.ITALIC);
                    viewHolder.binding.senderName.setTypeface(null, Typeface.NORMAL);
                } else {
                    viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.BOLD_ITALIC);
                    viewHolder.binding.senderName.setTypeface(null, Typeface.BOLD);
                }
            } else {
                if (isRead) {
                    viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.NORMAL);
                    viewHolder.binding.senderName.setTypeface(null, Typeface.NORMAL);
                } else {
                    viewHolder.binding.conversationLastmsg.setTypeface(null, Typeface.BOLD);
                    viewHolder.binding.senderName.setTypeface(null, Typeface.BOLD);
                }
            }
            if (message.getStatus() == Message.STATUS_RECEIVED) {
                if (conversation.getMode() == Conversation.MODE_MULTI) {
                    viewHolder.binding.senderName.setVisibility(View.VISIBLE);
                    viewHolder.binding.senderName.setText(
                            UIHelper.getColoredUsername(activity.xmppConnectionService, message));
                    viewHolder.binding.senderName.append(":");
                } else {
                    viewHolder.binding.senderName.setVisibility(View.GONE);
                }
            } else if (message.getType() != Message.TYPE_STATUS) {
                viewHolder.binding.senderName.setVisibility(View.VISIBLE);
                final SpannableString me;
                me = SpannableString.valueOf(activity.getString(R.string.me));
                me.setSpan(new StyleSpan(Typeface.BOLD), 0, me.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                viewHolder.binding.senderName.setText(me);
                viewHolder.binding.senderName.append(":");
            } else {
                viewHolder.binding.senderName.setVisibility(View.GONE);
            }
        }

        final Optional<OngoingRtpSession> ongoingCall;
        if (conversation.getMode() == Conversational.MODE_MULTI) {
            ongoingCall = Optional.absent();
        } else {
            ongoingCall =
                    activity.xmppConnectionService
                            .getJingleConnectionManager()
                            .getOngoingRtpConnection(conversation.getContact());
        }

        if (ongoingCall.isPresent()) {
            viewHolder.binding.notificationStatus.setVisibility(View.VISIBLE);
            final int ic_ongoing_call =
                    activity.getThemeResource(
                            R.attr.ic_ongoing_call_hint, R.drawable.ic_phone_in_talk_black_18dp);
            viewHolder.binding.notificationStatus.setImageResource(ic_ongoing_call);
        } else {
            final long muted_till =
                    conversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL, 0);
            if (muted_till == Long.MAX_VALUE) {
                viewHolder.binding.notificationStatus.setVisibility(View.VISIBLE);
                int ic_notifications_off =
                        activity.getThemeResource(
                                R.attr.icon_notifications_off,
                                R.drawable.ic_notifications_off_black_24dp);
                viewHolder.binding.notificationStatus.setImageResource(ic_notifications_off);
            } else if (muted_till >= System.currentTimeMillis()) {
                viewHolder.binding.notificationStatus.setVisibility(View.VISIBLE);
                int ic_notifications_paused =
                        activity.getThemeResource(
                                R.attr.icon_notifications_paused,
                                R.drawable.ic_notifications_paused_black_24dp);
                viewHolder.binding.notificationStatus.setImageResource(ic_notifications_paused);
            } else if (conversation.alwaysNotify()) {
                viewHolder.binding.notificationStatus.setVisibility(View.GONE);
            } else {
                viewHolder.binding.notificationStatus.setVisibility(View.VISIBLE);
                int ic_notifications_none =
                        activity.getThemeResource(
                                R.attr.icon_notifications_none,
                                R.drawable.ic_notifications_none_black_24dp);
                viewHolder.binding.notificationStatus.setImageResource(ic_notifications_none);
            }
        }

        long timestamp;
        if (draft != null) {
            timestamp = draft.getTimestamp();
        } else {
            timestamp = message.getTimeSent();
        }
        final boolean isAccountDisabled = !conversation.getAccount().isEnabled();
        final boolean isPinned = conversation.getBooleanAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP,false);
        if (isPinned) {
            viewHolder.binding.chat.setBackgroundColor(StyledAttributes.getColor(this.activity, R.attr.colorAccentLight));
            viewHolder.binding.chat.setAlpha(ACTIVE_ALPHA);
            if (isAccountDisabled) {
                viewHolder.binding.chat.setBackgroundColor(StyledAttributes.getColor(this.activity, R.attr.colorAccentLightDisabled));
                viewHolder.binding.chat.setAlpha(INACTIVE_ALPHA);
            }
        } else {
            viewHolder.binding.chat.setBackgroundColor(0);
            viewHolder.binding.chat.setAlpha(ACTIVE_ALPHA);
            if (isAccountDisabled) {
                viewHolder.binding.chat.setBackgroundColor(StyledAttributes.getColor(this.activity, R.attr.colorAccentLightDisabled));
                viewHolder.binding.chat.setAlpha(INACTIVE_ALPHA);
            }
        }
        viewHolder.binding.pinnedOnTop.setVisibility(isPinned ? View.VISIBLE
                : View.GONE);
        viewHolder.binding.conversationLastupdate.setText(
                UIHelper.readableTimeDifference(activity, timestamp));
            AvatarWorkerTask.loadAvatar(
                conversation,
                viewHolder.binding.conversationImage,
                R.dimen.avatar_on_conversation_overview);
        if (conversation.getMode() == Conversational.MODE_SINGLE && conversation.getContact().isActive()) {
            viewHolder.binding.userActiveIndicator.setVisibility(View.VISIBLE);
        } else {
            viewHolder.binding.userActiveIndicator.setVisibility(View.GONE);
        }
        viewHolder.itemView.setOnClickListener(v -> listener.onConversationClick(v, conversation));

        if (conversation.getMode() == Conversation.MODE_SINGLE && ShowPresenceColoredNames()) {
            if (hasInternetConnection) {
                switch (conversation.getContact().getPresences().getShownStatus()) {
                    case CHAT:
                    case ONLINE:
                        viewHolder.binding.conversationName.setTextColor(ContextCompat.getColor(activity, R.color.online));
                        break;
                    case AWAY:
                        viewHolder.binding.conversationName.setTextColor(ContextCompat.getColor(activity, R.color.away));
                        break;
                    case XA:
                    case DND:
                        viewHolder.binding.conversationName.setTextColor(ContextCompat.getColor(activity, R.color.notavailable));
                        break;
                    case OFFLINE:
                    default:
                        viewHolder.binding.conversationName.setTextColor(StyledAttributes.getColor(activity, R.attr.text_Color_Main));
                        break;
                }
            } else {
                viewHolder.binding.conversationName.setTextColor(StyledAttributes.getColor(activity, R.attr.text_Color_Main));
            }
        } else {
            viewHolder.binding.conversationName.setTextColor(StyledAttributes.getColor(activity, R.attr.text_Color_Main));
        }
        if (activity.xmppConnectionService.indicateReceived()) {
            switch (message.getMergedStatus()) {
                case Message.STATUS_SEND_RECEIVED:
                    if (viewHolder.binding.indicatorReceived != null) {
                        viewHolder.binding.indicatorReceived.setVisibility(View.VISIBLE);
                        viewHolder.binding.indicatorReceived.setImageResource(getReadmakerType(activity.isDarkTheme(), readmarkervalue, Util.ReadmarkerType.RECEIVED));
                        viewHolder.binding.indicatorReceived.setAlpha(activity.isDarkTheme() ? 0.7f : 0.57f);
                    }
                    break;
                case Message.STATUS_SEND_DISPLAYED:
                    if (viewHolder.binding.indicatorReceived != null) {
                        viewHolder.binding.indicatorReceived.setVisibility(View.VISIBLE);
                        viewHolder.binding.indicatorReceived.setImageResource(getReadmakerType(activity.isDarkTheme(), readmarkervalue, Util.ReadmarkerType.DISPLAYED));
                        viewHolder.binding.indicatorReceived.setAlpha(activity.isDarkTheme() ? 0.7f : 0.57f);
                    }
                    break;
                default:
                    viewHolder.binding.indicatorReceived.setVisibility(View.GONE);
            }
        }
    }


    @Override
    public int getItemCount() {
        return conversations.size();
    }

    public void setConversationClickListener(OnConversationClickListener listener) {
        this.listener = listener;
    }

    public void insert(Conversation c, int position) {
        conversations.add(position, c);
        notifyDataSetChanged();
    }

    public void remove(Conversation conversation, int position) {
        conversations.remove(conversation);
        notifyItemRemoved(position);
    }

    public interface OnConversationClickListener {
        void onConversationClick(View view, Conversation conversation);
    }

    static class ConversationViewHolder extends RecyclerView.ViewHolder {
        private final ConversationListRowBinding binding;

        private ConversationViewHolder(ConversationListRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setLongClickable(true);
        }
    }

    private boolean ShowPresenceColoredNames() {
        return getPreferences().getBoolean("presence_colored_names", activity.getResources().getBoolean(R.bool.presence_colored_names));
    }

    protected SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
    }
}