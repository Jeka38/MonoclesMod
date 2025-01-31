package eu.siacs.conversations.ui;

import static eu.siacs.conversations.entities.Bookmark.printableValue;
import static eu.siacs.conversations.ui.util.IntroHelper.showIntro;
import static eu.siacs.conversations.utils.StringUtils.changed;
import de.monocles.mod.Util;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.ListItem;
import android.net.Uri;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityMucDetailsBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.User;
import eu.siacs.conversations.services.NotificationService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.OnConversationUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnMucRosterUpdate;
import eu.siacs.conversations.ui.adapter.MediaAdapter;
import eu.siacs.conversations.ui.adapter.UserPreviewAdapter;
import eu.siacs.conversations.ui.interfaces.OnMediaLoaded;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.util.GridManager;
import eu.siacs.conversations.ui.util.JidDialog;
import eu.siacs.conversations.ui.util.MucConfiguration;
import eu.siacs.conversations.ui.util.MucDetailsContextMenuHelper;
import eu.siacs.conversations.ui.util.MyLinkify;
import eu.siacs.conversations.ui.util.SoftKeyboardUtils;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.MenuDoubleTabUtil;
import eu.siacs.conversations.utils.StringUtils;
import eu.siacs.conversations.utils.StylingHelper;
import eu.siacs.conversations.utils.TimeFrameUtils;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import me.drakeet.support.toast.ToastCompat;

public class ConferenceDetailsActivity extends XmppActivity implements OnConversationUpdate, OnMucRosterUpdate, XmppConnectionService.OnAffiliationChanged, XmppConnectionService.OnConfigurationPushed, TextWatcher, OnMediaLoaded {
    public static final String ACTION_VIEW_MUC = "view_muc";
    private Conversation mConversation;
    private OnClickListener destroyListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final AlertDialog.Builder DestroyMucDialog = new AlertDialog.Builder(ConferenceDetailsActivity.this);
            DestroyMucDialog.setNegativeButton(getString(R.string.cancel), null);
            final boolean groupChat = mConversation != null && mConversation.isPrivateAndNonAnonymous();
            DestroyMucDialog.setTitle(groupChat ? R.string.destroy_room : R.string.destroy_channel);
            DestroyMucDialog.setMessage(getString(groupChat ? R.string.destroy_room_dialog : R.string.destroy_channel_dialog, mConversation.getName()));
            DestroyMucDialog.setPositiveButton(getString(R.string.delete), (dialogInterface, i) -> {
                Intent intent = new Intent(xmppConnectionService, ConversationsActivity.class);
                intent.setAction(ConversationsActivity.ACTION_DESTROY_MUC);
                intent.putExtra("MUC_UUID", mConversation.getUuid());
                Log.d(Config.LOGTAG, "Sending DESTROY intent for " + mConversation.getName());
                startActivity(intent);
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                deleteBookmark();
                finish();
            });
            DestroyMucDialog.create().show();
        }
    };
    private ActivityMucDetailsBinding binding;
    private MediaAdapter mMediaAdapter;
    private UserPreviewAdapter mUserPreviewAdapter;
    private String uuid = null;

    private boolean mAdvancedMode = false;
    private boolean showDynamicTags = true;
    private boolean mIndividualNotifications = false;

    private UiCallback<Conversation> renameCallback = new UiCallback<Conversation>() {
        @Override
        public void success(Conversation object) {
            displayToast(getString(R.string.your_nick_has_been_changed));
            runOnUiThread(() -> {
                updateView();
            });

        }

        @Override
        public void error(final int errorCode, Conversation object) {
            displayToast(getString(errorCode));
        }

        @Override
        public void userInputRequired(PendingIntent pi, Conversation object) {

        }

        @Override
        public void progress(int progress) {

        }

        @Override
        public void showToast() {
        }
    };

    public static void open(final Activity activity, final Conversation conversation) {
        Intent intent = new Intent(activity, ConferenceDetailsActivity.class);
        intent.setAction(ConferenceDetailsActivity.ACTION_VIEW_MUC);
        intent.putExtra("uuid", conversation.getUuid());
        activity.startActivity(intent);
        activity.overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
    }

    private OnClickListener mNotifyStatusClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(ConferenceDetailsActivity.this);
            builder.setTitle(R.string.pref_notification_settings);
            String[] choices = {
                    getString(R.string.notify_on_all_messages),
                    getString(R.string.notify_only_when_highlighted),
                    getString(R.string.notify_only_when_highlighted_or_replied),
                    getString(R.string.notify_never)
            };
            final AtomicInteger choice;
            if (mConversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL, 0) == Long.MAX_VALUE) {
                choice = new AtomicInteger(3);
            } else {
                choice = new AtomicInteger(mConversation.alwaysNotify() ? 0 : (mConversation.notifyReplies() ? 2 : 1));
            }
            builder.setSingleChoiceItems(choices, choice.get(), (dialog, which) -> choice.set(which));
            builder.setNegativeButton(R.string.cancel, null);
            builder.setPositiveButton(R.string.ok, (dialog, which) -> {
                if (choice.get() == 3) {
                    final AlertDialog.Builder builder1 = new AlertDialog.Builder(ConferenceDetailsActivity.this);
                    builder1.setTitle(R.string.disable_notifications);
                    final int[] durations = getResources().getIntArray(R.array.mute_options_durations);
                    final CharSequence[] labels = new CharSequence[durations.length];
                    for (int i = 0; i < durations.length; ++i) {
                        if (durations[i] == -1) {
                            labels[i] = getString(R.string.until_further_notice);
                        } else {
                            labels[i] = TimeFrameUtils.resolve(ConferenceDetailsActivity.this, 1000L * durations[i]);
                        }
                    }
                    builder1.setItems(labels, (dialog1, which1) -> {
                        final long till;
                        if (durations[which1] == -1) {
                            till = Long.MAX_VALUE;
                        } else {
                            till = System.currentTimeMillis() + (durations[which1] * 1000);
                        }
                        mConversation.setMutedTill(till);
                        xmppConnectionService.updateConversation(mConversation);
                        updateView();
                    });
                    builder1.create().show();
                } else {
                    mConversation.setMutedTill(0);
                    mConversation.setAttribute(Conversation.ATTRIBUTE_ALWAYS_NOTIFY, String.valueOf(choice.get() == 0));
                    mConversation.setAttribute(Conversation.ATTRIBUTE_NOTIFY_REPLIES, String.valueOf(choice.get() == 2));
                }
                xmppConnectionService.updateConversation(mConversation);
                updateView();
            });
            builder.create().show();
        }
    };

    private final OnClickListener mChangeConferenceSettings =
            new OnClickListener() {
                @Override
                public void onClick(View v) {
                    final MucOptions mucOptions = mConversation.getMucOptions();
                    final MaterialAlertDialogBuilder builder =
                            new MaterialAlertDialogBuilder(ConferenceDetailsActivity.this);
                    MucConfiguration configuration =
                            MucConfiguration.get(
                                    ConferenceDetailsActivity.this, mAdvancedMode, mucOptions);
                    builder.setTitle(configuration.title);
                    final boolean[] values = configuration.values;
                    builder.setMultiChoiceItems(
                            configuration.names,
                            values,
                            (dialog, which, isChecked) -> values[which] = isChecked);
                    builder.setNegativeButton(R.string.cancel, null);
                    builder.setPositiveButton(
                            R.string.confirm,
                            (dialog, which) -> {
                                final Bundle options = configuration.toBundle(values);
                                options.putString("muc#roomconfig_persistentroom", "1");
                                if (options.containsKey("muc#roomconfig_allowinvites")) {
                                    options.putString(
                                            "{http://prosody.im/protocol/muc}roomconfig_allowmemberinvites",
                                            options.getString("muc#roomconfig_allowinvites"));
                                }
                                xmppConnectionService.pushConferenceConfiguration(
                                        mConversation, options, ConferenceDetailsActivity.this);
                            });
                    builder.create().show();
                }
            };

    @Override
    public void onConversationUpdate() {
        refreshUi();
    }

    @Override
    public void onMucRosterUpdate() {
        refreshUi();
    }

    @Override
    protected void refreshUiReal() {
        updateView();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_muc_details);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        showDynamicTags = preferences.getBoolean(SettingsActivity.SHOW_DYNAMIC_TAGS, getResources().getBoolean(R.bool.show_dynamic_tags));
        this.binding.changeConferenceButton.setOnClickListener(this.mChangeConferenceSettings);
        this.binding.destroy.setVisibility(View.GONE);
        this.binding.destroy.setOnClickListener(destroyListener);
        this.binding.leaveMuc.setVisibility(View.GONE);
        this.binding.addContactButton.setVisibility(View.GONE);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar());
        this.binding.editNickButton.setOnClickListener(v -> {
            try {
                quickEdit(mConversation.getMucOptions().getActualNick(),
                        R.string.nickname,
                        value -> {
                            if (xmppConnectionService.renameInMuc(mConversation, value, renameCallback)) {
                                return null;
                            } else {
                                return getString(R.string.invalid_muc_nick);
                            }
                        });
            } catch (Exception e) {
                ToastCompat.makeText(this, R.string.unable_to_perform_this_action, ToastCompat.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        });
        this.binding.detailsMucAvatar.setOnClickListener(v -> {
            try {
                final MucOptions mucOptions = mConversation.getMucOptions();
                if (!mucOptions.hasVCards()) {
                    ToastCompat.makeText(this, R.string.host_does_not_support_group_chat_avatars, ToastCompat.LENGTH_SHORT).show();
                    return;
                }
                if (!mucOptions.getSelf().getAffiliation().ranks(MucOptions.Affiliation.OWNER)) {
                    ToastCompat.makeText(this, R.string.only_the_owner_can_change_group_chat_avatar, ToastCompat.LENGTH_SHORT).show();
                    return;
                }
                final Intent intent = new Intent(this, PublishGroupChatProfilePictureActivity.class);
                intent.putExtra("uuid", mConversation.getUuid());
                startActivity(intent);
            } catch (Exception e) {
                ToastCompat.makeText(this, R.string.unable_to_perform_this_action, ToastCompat.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        });
        this.binding.detailsMucAvatar.setOnLongClickListener(v -> {
            ShowAvatarPopup(ConferenceDetailsActivity.this, mConversation);
            return true;
        });
        this.mAdvancedMode = getPreferences().getBoolean("advanced_muc_mode", false);
        this.binding.mucInfoMore.setVisibility(this.mAdvancedMode ? View.VISIBLE : View.GONE);
        this.binding.notificationStatusButton.setOnClickListener(this.mNotifyStatusClickListener);

        this.binding.editMucNameButton.setOnClickListener(this::onMucEditButtonClicked);
        this.binding.mucEditTitle.addTextChangedListener(this);
        this.binding.mucEditSubject.addTextChangedListener(this);
        // this.binding.mucEditSubject.addTextChangedListener(new StylingHelper.MessageEditorStyler(this.binding.mucEditSubject));
        this.binding.editTags.addTextChangedListener(this);
        this.mMediaAdapter = new MediaAdapter(this, R.dimen.media_size);
        this.mUserPreviewAdapter = new UserPreviewAdapter();
        this.binding.media.setAdapter(mMediaAdapter);
        this.binding.users.setAdapter(mUserPreviewAdapter);
        //TODO: Implement recyclerview for users list and media list
        GridManager.setupLayoutManager(this, this.binding.media, R.dimen.media_size);
        GridManager.setupLayoutManager(this, this.binding.users, R.dimen.media_size);
        this.binding.recentThreads.setOnItemClickListener((a0, v, pos, a3) -> {
            final Conversation.Thread thread = (Conversation.Thread) binding.recentThreads.getAdapter().getItem(pos);
            switchToConversation(mConversation, null, false, null, false, true, null, thread.getThreadId());
        });
        this.binding.invite.setOnClickListener(v -> inviteToConversation(mConversation));
        this.binding.showUsers.setOnClickListener(v -> {
            Intent intent = new Intent(this, MucUsersActivity.class);
            intent.putExtra("uuid", mConversation.getUuid());
            startActivity(intent);
        });
        this.binding.relatedMucs.setOnClickListener(v -> {
            final Intent intent = new Intent(this, ChannelDiscoveryActivity.class);
            intent.putExtra("services", new String[]{ mConversation.getJid().getDomain().toEscapedString(), mConversation.getAccount().getJid().toEscapedString() });
            startActivity(intent);
        });
        showIntro(this, true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        }
        binding.mediaWrapper.setVisibility(Compatibility.hasStoragePermission(this) ? View.VISIBLE : View.GONE);
    }

    private boolean canChangeMUCAvatar() {
        final MucOptions mucOptions = mConversation.getMucOptions();
        if (!mucOptions.hasVCards()) {
            return false;
        } else if (!mucOptions.getSelf().getAffiliation().ranks(MucOptions.Affiliation.OWNER)) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.action_share_http:
                shareLink(true);
                break;
            case R.id.action_share_uri:
                shareLink(false);
                break;
            case R.id.action_advanced_mode:
                this.mAdvancedMode = !menuItem.isChecked();
                menuItem.setChecked(this.mAdvancedMode);
                getPreferences().edit().putBoolean("advanced_muc_mode", mAdvancedMode).apply();
                invalidateOptionsMenu();
                updateView();
                break;
            case R.id.action_activate_individual_notifications:
                if (!menuItem.isChecked()) {
                    this.mIndividualNotifications = true;
                } else {
                    if (Compatibility.runsTwentySix()) {
                        final AlertDialog.Builder removeIndividualNotificationDialog = new AlertDialog.Builder(ConferenceDetailsActivity.this);
                        removeIndividualNotificationDialog.setTitle(getString(R.string.remove_individual_notifications));
                        removeIndividualNotificationDialog.setMessage(JidDialog.style(this, R.string.remove_individual_notifications_message, mConversation.getJid().asBareJid().toString()));
                        removeIndividualNotificationDialog.setPositiveButton(R.string.yes, (dialog, which) -> {
                            this.mIndividualNotifications = false;
                            try {
                                xmppConnectionService.getNotificationService().cleanNotificationChannels(this, mConversation.getUuid());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            menuItem.setChecked(this.mIndividualNotifications);
                            xmppConnectionService.setIndividualNotificationPreference(mConversation, !mIndividualNotifications);
                            xmppConnectionService.updateNotificationChannels();
                            invalidateOptionsMenu();
                            refreshUi();
                        });
                        removeIndividualNotificationDialog.setNegativeButton(R.string.no, (dialog, which) -> {
                            this.mIndividualNotifications = true;
                        });
                        removeIndividualNotificationDialog.create().show();
                    }
                }
                menuItem.setChecked(this.mIndividualNotifications);
                xmppConnectionService.setIndividualNotificationPreference(mConversation, !mIndividualNotifications);
                xmppConnectionService.updateNotificationChannels();
                invalidateOptionsMenu();
                refreshUi();
                break;
            case R.id.action_message_notifications:
                Intent messageNotificationIntent = null;
                if (Compatibility.runsTwentySix()) {
                    final String time = String.valueOf(xmppConnectionService.getIndividualNotificationPreference(mConversation));
                    messageNotificationIntent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, this.getPackageName())
                            .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationService.INDIVIDUAL_NOTIFICATION_PREFIX + NotificationService.MESSAGES_CHANNEL_ID + "_" + mConversation.getUuid() + "_" + time);
                }
                startActivity(messageNotificationIntent);
                break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final User user = mUserPreviewAdapter.getSelectedUser();
        if (user == null) {
            ToastCompat.makeText(this, R.string.unable_to_perform_this_action, ToastCompat.LENGTH_SHORT).show();
            return true;
        }
        if (!MucDetailsContextMenuHelper.onContextItemSelected(item, mUserPreviewAdapter.getSelectedUser(), this)) {
            return super.onContextItemSelected(item);
        }
        return true;
    }

    public void onMucEditButtonClicked(View v) {
        if (this.binding.mucEditor.getVisibility() == View.GONE) {
            final MucOptions mucOptions = mConversation.getMucOptions();
            this.binding.mucEditor.setVisibility(View.VISIBLE);
            this.binding.mucDisplay.setVisibility(View.GONE);
            this.binding.editMucNameButton.setImageResource(getThemeResource(R.attr.icon_cancel, R.drawable.ic_cancel_black_24dp));
            final String name = mucOptions.getName();
            this.binding.mucEditTitle.setText("");
            final boolean owner = mucOptions.getSelf().getAffiliation().ranks(MucOptions.Affiliation.OWNER);
            if (owner || printableValue(name)) {
                this.binding.mucEditTitle.setVisibility(View.VISIBLE);
                if (name != null) {
                    this.binding.mucEditTitle.append(name);
                }
            } else {
                this.binding.mucEditTitle.setVisibility(View.GONE);
            }
            this.binding.mucEditTitle.setEnabled(owner);
            final String subject = mucOptions.getSubject();
            this.binding.mucEditSubject.setText("");
            if (subject != null) {
                this.binding.mucEditSubject.append(subject);
            }
            this.binding.mucEditSubject.setEnabled(mucOptions.canChangeSubject());
            if (!owner) {
                this.binding.mucEditSubject.requestFocus();
            }

            final Bookmark bookmark = mConversation.getBookmark();
            if (bookmark != null && mConversation.getAccount().getXmppConnection().getFeatures().bookmarks2() && showDynamicTags) {
                for (final ListItem.Tag group : bookmark.getGroupTags()) {
                    binding.editTags.addObjectSync(group);
                }
                ArrayList<ListItem.Tag> tags = new ArrayList<>();
                for (final Account account : xmppConnectionService.getAccounts()) {
                    for (Contact contact : account.getRoster().getContacts()) {
                        tags.addAll(contact.getTags(this));
                    }
                    for (Bookmark bmark : account.getBookmarks()) {
                        tags.addAll(bmark.getTags(this));
                    }
                }
                Comparator<Map.Entry<ListItem.Tag,Integer>> sortTagsBy = Map.Entry.comparingByValue(Comparator.reverseOrder());
                sortTagsBy = sortTagsBy.thenComparing(entry -> entry.getKey().getName());

                ArrayAdapter<ListItem.Tag> adapter = new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        tags.stream()
                                .collect(Collectors.toMap((x) -> x, (t) -> 1, (c1, c2) -> c1 + c2))
                                .entrySet().stream()
                                .sorted(sortTagsBy)
                                .map(e -> e.getKey()).collect(Collectors.toList())
                );
                binding.editTags.setAdapter(adapter);
                this.binding.editTags.setVisibility(View.VISIBLE);
            } else {
                this.binding.editTags.setVisibility(View.GONE);
            }
        } else {
            String subject = this.binding.mucEditSubject.isEnabled() ? this.binding.mucEditSubject.getEditableText().toString().trim() : null;
            String name = this.binding.mucEditTitle.isEnabled() ? this.binding.mucEditTitle.getEditableText().toString().trim() : null;
            onMucInfoUpdated(subject, name);

            final Bookmark bookmark = mConversation.getBookmark();
            if (bookmark != null && mConversation.getAccount().getXmppConnection().getFeatures().bookmarks2()) {
                bookmark.setGroups(binding.editTags.getObjects().stream().map(tag -> tag.getName()).collect(Collectors.toList()));
                xmppConnectionService.createBookmark(bookmark.getAccount(), bookmark);
            }

            SoftKeyboardUtils.hideSoftKeyboard(this);
            hideEditor();
            updateView();
        }
    }

    private void hideEditor() {
        this.binding.mucEditor.setVisibility(View.GONE);
        this.binding.mucDisplay.setVisibility(View.VISIBLE);
        this.binding.editMucNameButton.setImageResource(getThemeResource(R.attr.icon_edit_body, R.drawable.ic_edit_black_24dp));
    }

    private void onMucInfoUpdated(String subject, String name) {
        final MucOptions mucOptions = mConversation.getMucOptions();
        if (mucOptions.canChangeSubject() && changed(mucOptions.getSubject(), subject)) {
            xmppConnectionService.pushSubjectToConference(mConversation, subject);
        }
        if (mucOptions.getSelf().getAffiliation().ranks(MucOptions.Affiliation.OWNER) && changed(mucOptions.getName(), name)) {
            Bundle options = new Bundle();
            options.putString("muc#roomconfig_persistentroom", "1");
            options.putString("muc#roomconfig_roomname", StringUtils.nullOnEmpty(name));
            xmppConnectionService.pushConferenceConfiguration(mConversation, options, this);
        }
    }

    @Override
    protected String getShareableUri(boolean http) {
        if (mConversation != null) {
            if (http) {
                return "https://monocles.chat/chat/" + XmppUri.lameUrlEncode(mConversation.getJid().asBareJid().toEscapedString());
            } else {
                return "xmpp:" + Uri.encode(mConversation.getJid().asBareJid().toEscapedString(), "@/+") + "?join";
            }
        } else {
            return null;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem menuItemAdvancedMode = menu.findItem(R.id.action_advanced_mode);
        menuItemAdvancedMode.setChecked(mAdvancedMode);
        MenuItem menuItemIndividualNotifications = menu.findItem(R.id.action_activate_individual_notifications);
        menuItemIndividualNotifications.setChecked(mIndividualNotifications);
        menuItemIndividualNotifications.setVisible(Compatibility.runsTwentySix());
        if (mConversation == null) {
            return true;
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final boolean groupChat = mConversation != null && mConversation.isPrivateAndNonAnonymous();
        getMenuInflater().inflate(R.menu.muc_details, menu);
        final MenuItem share = menu.findItem(R.id.action_share);
        share.setVisible(!groupChat);
        final MenuItem menuMessageNotification = menu.findItem(R.id.action_message_notifications);
        if (Compatibility.runsTwentySix() && xmppConnectionServiceBound) {
            menuMessageNotification.setVisible(xmppConnectionService.hasIndividualNotification(mConversation));
        } else {
            menuMessageNotification.setVisible(false);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onMediaLoaded(List<Attachment> attachments) {
        runOnUiThread(() -> {
            int limit = GridManager.getCurrentColumnCount(binding.media);
            mMediaAdapter.setAttachments(attachments.subList(0, Math.min(limit, attachments.size())));
            binding.mediaWrapper.setVisibility(attachments.size() > 0 ? View.VISIBLE : View.GONE);
        });
    }

    protected void saveAsBookmark() {
        xmppConnectionService.saveConversationAsBookmark(mConversation, mConversation.getMucOptions().getName());
        updateView();
    }

    protected void deleteBookmark() {
        try {
            final Account account = mConversation.getAccount();
            final Bookmark bookmark = mConversation.getBookmark();
            bookmark.setConversation(null);
            xmppConnectionService.deleteBookmark(account, bookmark);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            updateView();
        }
    }

    @Override
    protected void onBackendConnected() {
        if (mPendingConferenceInvite != null) {
            mPendingConferenceInvite.execute(this);
            mPendingConferenceInvite = null;
        }
        if (getIntent().getAction().equals(ACTION_VIEW_MUC)) {
            this.uuid = getIntent().getExtras().getString("uuid");
        }
        if (uuid != null) {
            this.mConversation = xmppConnectionService.findConversationByUuid(uuid);
            if (this.mConversation != null) {
                if (Compatibility.hasStoragePermission(this)) {
                    final int limit = GridManager.getCurrentColumnCount(this.binding.media);
                    xmppConnectionService.getAttachments(this.mConversation, limit, this);
                    this.binding.showMedia.setOnClickListener((v) -> MediaBrowserActivity.launch(this, mConversation));
                    final boolean groupChat = mConversation != null && mConversation.isPrivateAndNonAnonymous();
                    this.binding.destroy.setText(groupChat ? R.string.destroy_room : R.string.destroy_channel);
                    this.binding.leaveMuc.setText(groupChat ? R.string.action_end_conversation_muc : R.string.action_end_conversation_channel);
                }
                this.mIndividualNotifications = xmppConnectionService.hasIndividualNotification(mConversation);
                updateView();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (this.binding.mucEditor.getVisibility() == View.VISIBLE) {
            hideEditor();
        } else {
            super.onBackPressed();
        }
    }

    private void updateView() {
        invalidateOptionsMenu();
        if (mConversation == null) {
            return;
        }
        final MucOptions mucOptions = mConversation.getMucOptions();
        final User self = mucOptions.getSelf();
        String account;
        if (Config.DOMAIN_LOCK != null) {
            account = mConversation.getAccount().getJid().getEscapedLocal();
        } else {
            account = mConversation.getAccount().getJid().asBareJid().toEscapedString();
        }
        setTitle(mucOptions.isPrivateAndNonAnonymous() ? R.string.conference_details : R.string.channel_details);
        final Bookmark bookmark = mConversation.getBookmark();
        final XmppConnection connection = mConversation.getAccount().getXmppConnection();
        this.binding.editMucNameButton.setVisibility((self.getAffiliation().ranks(MucOptions.Affiliation.OWNER) || mucOptions.canChangeSubject() || (bookmark != null && connection != null && connection.getFeatures().bookmarks2())) ? View.VISIBLE : View.GONE);
        this.binding.detailsAccount.setText(getString(R.string.using_account, account));
        this.binding.jid.setText(mConversation.getJid().asBareJid().toEscapedString());
        if (xmppConnectionService.multipleAccounts()) {
            this.binding.detailsAccount.setVisibility(View.VISIBLE);
        } else {
            this.binding.detailsAccount.setVisibility(View.GONE);
        }
        //todo add edit overlay to avatar and change layout
        AvatarWorkerTask.loadAvatar(mConversation, binding.detailsMucAvatar, R.dimen.avatar_on_details_screen_size, canChangeMUCAvatar());
        AvatarWorkerTask.loadAvatar(mConversation.getAccount(), binding.yourPhoto, R.dimen.avatar_on_details_screen_size);
        binding.detailsMucAvatar.setVisibility(View.VISIBLE);
        binding.yourPhoto.setVisibility(View.VISIBLE);
        String roomName = mucOptions.getName();
        String subject = mucOptions.getSubject();
        final boolean hasTitle;
        if (printableValue(roomName)) {
            this.binding.mucTitle.setText(roomName);
            this.binding.mucTitle.setVisibility(View.VISIBLE);
            hasTitle = true;
        } else if (!printableValue(subject)) {
            this.binding.mucTitle.setText(mConversation.getName());
            hasTitle = true;
            this.binding.mucTitle.setVisibility(View.VISIBLE);
        } else {
            hasTitle = false;
            this.binding.mucTitle.setVisibility(View.GONE);
        }
        if (printableValue(subject)) {
            SpannableStringBuilder spannable = new SpannableStringBuilder(subject);
            StylingHelper.format(spannable, this.binding.mucSubject.getCurrentTextColor(), true);
            MyLinkify.addLinks(spannable, false);
            this.binding.mucSubject.setText(spannable);
            this.binding.mucSubject.setTextAppearance(this, (hasTitle ? 128 : 196));
            this.binding.mucSubject.setAutoLinkMask(0);
            this.binding.mucSubject.setVisibility(View.VISIBLE);
            this.binding.mucSubject.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            this.binding.mucSubject.setVisibility(View.GONE);
        }
        this.binding.mucYourNick.setText(mucOptions.getActualNick());
        if (mucOptions.online()) {
            this.binding.usersWrapper.setVisibility(View.VISIBLE);
            this.binding.mucInfoMore.setVisibility(this.mAdvancedMode ? View.VISIBLE : View.GONE);
            this.binding.jid.setVisibility(this.mAdvancedMode ? View.VISIBLE : View.GONE);
            this.binding.mucRole.setVisibility(View.VISIBLE);
            this.binding.mucRole.setText(getStatus(self));
            if (mucOptions.getSelf().getAffiliation().ranks(MucOptions.Affiliation.OWNER)) {
                this.binding.mucSettings.setVisibility(View.VISIBLE);
                this.binding.mucConferenceType.setText(MucConfiguration.describe(this, mucOptions));
            } else if (!mucOptions.isPrivateAndNonAnonymous() && mucOptions.nonanonymous()) {
                this.binding.mucSettings.setVisibility(View.VISIBLE);
                this.binding.mucConferenceType.setText(R.string.group_chat_will_make_your_jabber_id_public);
            } else {
                this.binding.mucSettings.setVisibility(View.GONE);
            }
            if (mucOptions.mamSupport()) {
                this.binding.mucInfoMam.setText(R.string.server_info_available);
            } else {
                this.binding.mucInfoMam.setText(R.string.server_info_unavailable);
            }
            if (self.getAffiliation().ranks(MucOptions.Affiliation.OWNER)) {
                if (mAdvancedMode) {
                    this.binding.destroy.getBackground().setTint(getWarningButtonColor());
                    this.binding.destroy.setTextColor(getWarningTextColor());
                    this.binding.destroy.setVisibility(View.VISIBLE);
                } else {
                    this.binding.destroy.setVisibility(View.GONE);
                }
                this.binding.changeConferenceButton.setVisibility(View.VISIBLE);
            } else {
                this.binding.destroy.setVisibility(View.GONE);
                this.binding.changeConferenceButton.setVisibility(View.INVISIBLE);
            }
            this.binding.leaveMuc.setVisibility(View.VISIBLE);
            this.binding.leaveMuc.setOnClickListener(v1 -> {
                final AlertDialog.Builder LeaveMucDialog = new AlertDialog.Builder(ConferenceDetailsActivity.this);
                LeaveMucDialog.setTitle(getString(R.string.action_end_conversation_muc));
                LeaveMucDialog.setMessage(getString(R.string.leave_conference_warning));
                LeaveMucDialog.setNegativeButton(getString(R.string.cancel), null);
                LeaveMucDialog.setPositiveButton(getString(R.string.action_end_conversation_muc),
                        (dialog, which) -> {
                            startActivity(new Intent(xmppConnectionService, ConversationsActivity.class));
                            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                            this.xmppConnectionService.archiveConversation(mConversation);
                            finish();
                        });
                LeaveMucDialog.create().show();
            });
            this.binding.leaveMuc.getBackground().setTint(getWarningButtonColor());
            this.binding.leaveMuc.setTextColor(getWarningTextColor());
            this.binding.addContactButton.setVisibility(View.VISIBLE);
            if (mConversation.getBookmark() != null) {
                this.binding.addContactButton.setText(R.string.delete_bookmark);
                this.binding.addContactButton.getBackground().setTint(getWarningButtonColor());
                this.binding.addContactButton.setTextColor(getWarningTextColor());
                this.binding.addContactButton.setOnClickListener(v2 -> {
                    final AlertDialog.Builder deleteFromRosterDialog = new AlertDialog.Builder(ConferenceDetailsActivity.this);
                    deleteFromRosterDialog.setNegativeButton(getString(R.string.cancel), null);
                    deleteFromRosterDialog.setTitle(getString(R.string.action_delete_contact));
                    deleteFromRosterDialog.setMessage(getString(R.string.remove_bookmark_text, mConversation.getJid().toString()));
                    deleteFromRosterDialog.setPositiveButton(getString(R.string.delete),
                            (dialog, which) -> {
                                deleteBookmark();
                                recreate();
                            });
                    deleteFromRosterDialog.create().show();
                });
            } else {
                this.binding.addContactButton.setText(R.string.save_as_bookmark);
                this.binding.addContactButton.getBackground().clearColorFilter();
                this.binding.addContactButton.setTextColor(getDefaultButtonTextColor());
                this.binding.addContactButton.setOnClickListener(v2 -> {
                    saveAsBookmark();
                    recreate();
                });
            }
        } else {
            this.binding.usersWrapper.setVisibility(View.GONE);
            this.binding.mucInfoMore.setVisibility(View.GONE);
            this.binding.mucSettings.setVisibility(View.GONE);
        }

        int ic_notifications = getThemeResource(R.attr.icon_notifications, R.drawable.ic_notifications_black_24dp);
        int ic_notifications_off = getThemeResource(R.attr.icon_notifications_off, R.drawable.ic_notifications_off_black_24dp);
        int ic_notifications_paused = getThemeResource(R.attr.icon_notifications_paused, R.drawable.ic_notifications_paused_black_24dp);
        int ic_notifications_none = getThemeResource(R.attr.icon_notifications_none, R.drawable.ic_notifications_none_black_24dp);
        long mutedTill = mConversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL, 0);
        if (mutedTill == Long.MAX_VALUE) {
            this.binding.notificationStatusText.setText(R.string.notify_never);
            this.binding.notificationStatusButton.setImageResource(ic_notifications_off);
        } else if (System.currentTimeMillis() < mutedTill) {
            this.binding.notificationStatusText.setText(R.string.notify_paused);
            this.binding.notificationStatusButton.setImageResource(ic_notifications_paused);
        } else if (mConversation.alwaysNotify()) {
            this.binding.notificationStatusText.setText(R.string.notify_on_all_messages);
            this.binding.notificationStatusButton.setImageResource(ic_notifications);
        } else if (mConversation.notifyReplies()) {
            this.binding.notificationStatusText.setText(R.string.notify_only_when_highlighted_or_replied);
            this.binding.notificationStatusButton.setImageResource(ic_notifications_none);
        } else {
            this.binding.notificationStatusText.setText(R.string.notify_only_when_highlighted);
            this.binding.notificationStatusButton.setImageResource(ic_notifications_none);
        }

        final List<User> users = mucOptions.getUsers();
        Collections.sort(users, (a, b) -> {
            if (b.getAffiliation().outranks(a.getAffiliation())) {
                return 1;
            } else if (a.getAffiliation().outranks(b.getAffiliation())) {
                return -1;
            } else {
                if (a.getAvatar() != null && b.getAvatar() == null) {
                    return -1;
                } else if (a.getAvatar() == null && b.getAvatar() != null) {
                    return 1;
                } else {
                    return a.getComparableName().compareToIgnoreCase(b.getComparableName());
                }
            }
        });
        this.mUserPreviewAdapter.submitList(MucOptions.sub(users, GridManager.getCurrentColumnCount(binding.users)));
        this.binding.invite.setVisibility(mucOptions.canInvite() ? View.VISIBLE : View.GONE);
        this.binding.showUsers.setVisibility(mucOptions.getUsers(true, mucOptions.getSelf().getAffiliation().ranks(MucOptions.Affiliation.ADMIN)).size() > 0 ? View.VISIBLE : View.GONE);
        this.binding.showUsers.setText(getResources().getQuantityString(R.plurals.view_users, users.size(), users.size()));
        this.binding.usersWrapper.setVisibility(users.size() > 0 || mucOptions.canInvite() ? View.VISIBLE : View.GONE);
        if (users.size() == 0) {
            this.binding.noUsersHints.setText(mucOptions.isPrivateAndNonAnonymous() ? R.string.no_users_hint_group_chat : R.string.no_users_hint_channel);
            this.binding.noUsersHints.setVisibility(View.VISIBLE);
        } else {
            this.binding.noUsersHints.setVisibility(View.GONE);
        }
        if (bookmark == null) {
            binding.tags.setVisibility(View.GONE);
            return;
        }

        final List<Conversation.Thread> recentThreads = mConversation.recentThreads();
        if (recentThreads.isEmpty()) {
            this.binding.recentThreadsWrapper.setVisibility(View.GONE);
        } else {

            if (xmppConnectionService != null && xmppConnectionService.getBooleanPreference("show_thread_feature", R.bool.show_thread_feature)) {
                this.binding.recentThreadsWrapper.setVisibility(View.VISIBLE);
            } else {
                this.binding.recentThreadsWrapper.setVisibility(View.GONE);
            }
            Util.justifyListViewHeightBasedOnChildren(binding.recentThreads);
        }

        List<ListItem.Tag> tagList = bookmark.getTags(this);
        if (tagList.size() == 0 || !showDynamicTags) {
            binding.tags.setVisibility(View.GONE);
        } else {
            final LayoutInflater inflater = getLayoutInflater();
            binding.tags.setVisibility(View.VISIBLE);
            binding.tags.removeAllViewsInLayout();
            for (final ListItem.Tag tag : tagList) {
                final TextView tv = (TextView) inflater.inflate(R.layout.list_item_tag, binding.tags, false);
                String upperString = tag.getName().substring(0, 1).toUpperCase() + tag.getName().substring(1).toLowerCase();
                tv.setText(upperString);
                Drawable unwrappedDrawable = AppCompatResources.getDrawable(this, R.drawable.rounded_tag);
                Drawable wrappedDrawable = DrawableCompat.wrap(unwrappedDrawable);
                DrawableCompat.setTint(wrappedDrawable, tag.getColor());
                tv.setBackgroundResource(R.drawable.rounded_tag);
                binding.tags.addView(tv);
            }
        }
    }

    public static String getStatus(Context context, User user, final boolean advanced) {
        if (advanced) {
            return String.format("%s (%s)", context.getString(user.getAffiliation().getResId()), context.getString(user.getRole().getResId()));
        } else {
            return context.getString(user.getAffiliation().getResId());
        }
    }

    private String getStatus(User user) {
        return getStatus(this, user, mAdvancedMode);
    }

    @Override
    public void onAffiliationChangedSuccessful(Jid jid) {
        refreshUi();
    }

    @Override
    public void onAffiliationChangeFailed(Jid jid, int resId) {
        displayToast(getString(resId, jid.asBareJid().toEscapedString()));
    }

    @Override
    public void onPushSucceeded() {
        displayToast(getString(R.string.modified_conference_options));
    }

    @Override
    public void onPushFailed() {
        displayToast(getString(R.string.could_not_modify_conference_options));
    }

    private void displayToast(final String msg) {
        runOnUiThread(() -> {
            if (isFinishing()) {
                return;
            }
            ToastCompat.makeText(this, msg, ToastCompat.LENGTH_SHORT).show();
        });
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        if (mConversation == null) {
            return;
        }
        final MucOptions mucOptions = mConversation.getMucOptions();
        if (this.binding.mucEditor.getVisibility() == View.VISIBLE) {
            boolean subjectChanged = changed(binding.mucEditSubject.getEditableText().toString(), mucOptions.getSubject());
            boolean nameChanged = changed(binding.mucEditTitle.getEditableText().toString(), mucOptions.getName());
            final Bookmark bookmark = mConversation.getBookmark();
            if (subjectChanged || nameChanged || (bookmark != null && mConversation.getAccount().getXmppConnection().getFeatures().bookmarks2())) {
                this.binding.editMucNameButton.setImageResource(getThemeResource(R.attr.icon_save, R.drawable.ic_save_black_24dp));
            } else {
                this.binding.editMucNameButton.setImageResource(getThemeResource(R.attr.icon_cancel, R.drawable.ic_cancel_black_24dp));
            }
        }
    }
}
