package eu.siacs.conversations.ui;

import static eu.siacs.conversations.ui.util.IntroHelper.showIntro;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.Patterns;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.databinding.DataBindingUtil;

import com.bumptech.glide.Glide;
import com.google.common.base.Optional;

import org.openintents.openpgp.util.OpenPgpUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.monocles.chat.Util;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.databinding.ActivityContactDetailsBinding;
import eu.siacs.conversations.databinding.CommandRowBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.services.NotificationService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnRosterUpdate;
import eu.siacs.conversations.ui.adapter.MediaAdapter;
import eu.siacs.conversations.ui.interfaces.OnMediaLoaded;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.util.CallManager;
import eu.siacs.conversations.ui.util.GridManager;
import eu.siacs.conversations.ui.util.JidDialog;
import eu.siacs.conversations.ui.util.ShareUtil;
import eu.siacs.conversations.ui.util.SoftKeyboardUtils;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.Emoticons;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.IrregularUnicodeDetector;
import eu.siacs.conversations.utils.MenuDoubleTabUtil;
import eu.siacs.conversations.utils.TimeFrameUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.OnKeyStatusUpdated;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jingle.OngoingRtpSession;
import eu.siacs.conversations.xmpp.jingle.RtpCapability;
import me.drakeet.support.toast.ToastCompat;

public class ContactDetailsActivity extends OmemoActivity implements OnAccountUpdate, OnRosterUpdate, OnUpdateBlocklist, OnKeyStatusUpdated, OnMediaLoaded {
    public static final String ACTION_VIEW_CONTACT = "view_contact";
    private final int REQUEST_SYNC_CONTACTS = 0x28cf;
    private Contact contact;
    private Conversation mConversation;
    private ConversationFragment mConversationFragment;
    ActivityContactDetailsBinding binding;
    private MediaAdapter mMediaAdapter;
    protected MenuItem edit = null;
    protected MenuItem save = null;
    private boolean mIndividualNotifications = false;
    private DialogInterface.OnClickListener removeFromRoster = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            xmppConnectionService.deleteContactOnServer(contact);
            recreate();
        }
    };
    private OnCheckedChangeListener mOnSendCheckedChange = new OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                if (contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
                    xmppConnectionService.stopPresenceUpdatesTo(contact);
                } else {
                    contact.setOption(Contact.Options.PREEMPTIVE_GRANT);
                }
            } else {
                contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
                xmppConnectionService.sendPresencePacket(contact.getAccount(), xmppConnectionService.getPresenceGenerator().stopPresenceUpdatesTo(contact));
            }
        }
    };
    private OnCheckedChangeListener mOnReceiveCheckedChange = new OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                xmppConnectionService.sendPresencePacket(contact.getAccount(),
                        xmppConnectionService.getPresenceGenerator()
                                .requestPresenceUpdatesFrom(contact));
            } else {
                xmppConnectionService.sendPresencePacket(contact.getAccount(),
                        xmppConnectionService.getPresenceGenerator()
                                .stopPresenceUpdatesFrom(contact));
            }
        }
    };
    private Jid accountJid;
    private Jid contactJid;
    private boolean showDynamicTags = false;
    private boolean showLastSeen = false;
    private boolean showInactiveOmemo = false;
    private String messageFingerprint;
    private TextView mNotifyStatusText;
    private ImageButton mNotifyStatusButton;

    private void checkContactPermissionAndShowAddDialog() {
        if (hasContactsPermission()) {
            showAddToPhoneBookDialog();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_SYNC_CONTACTS);
        }
    }

    private boolean hasContactsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void showAddToPhoneBookDialog() {
        //TODO check if isQuicksy and contact is on quicksy.im domain
        // store in final boolean. show different message. use phone number for add
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.action_add_phone_book));
        builder.setMessage(getString(R.string.add_phone_book_text, contact.getJid().toEscapedString()));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.add), (dialog, which) -> {
            final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            intent.setType(Contacts.CONTENT_ITEM_TYPE);
            intent.putExtra(Intents.Insert.IM_HANDLE, contact.getJid().toEscapedString());
            intent.putExtra(Intents.Insert.IM_PROTOCOL, CommonDataKinds.Im.PROTOCOL_JABBER);
            //TODO for modern use we want PROTOCOL_CUSTOM and an extra field with a value of 'XMPP'
            // however we don’t have such a field and thus have to use the legacy PROTOCOL_JABBER
            intent.putExtra("finishActivityOnSaveCompleted", true);
            try {
                startActivityForResult(intent, 0);
            } catch (ActivityNotFoundException e) {
                ToastCompat.makeText(ContactDetailsActivity.this, R.string.no_application_found_to_view_contact, ToastCompat.LENGTH_SHORT).show();
            }
        });
        builder.create().show();
    }

    private OnClickListener mNotifyStatusClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(ContactDetailsActivity.this);
            builder.setTitle(R.string.pref_notification_settings);
            String[] choices = {
                    getString(R.string.notify_on_all_messages),
                    getString(R.string.notify_never)
            };
            final AtomicInteger choice;
            if (mConversation.alwaysNotify()) {
                choice = new AtomicInteger(0);
            } else {
                choice = new AtomicInteger(1);
            }
            builder.setSingleChoiceItems(choices, choice.get(), (dialog, which) -> choice.set(which));
            builder.setNegativeButton(R.string.cancel, null);
            builder.setPositiveButton(R.string.ok, (DialogInterface.OnClickListener) (dialog, which) -> {
                if (choice.get() == 1) {
                    final AlertDialog.Builder builder1 = new AlertDialog.Builder(ContactDetailsActivity.this);
                    builder1.setTitle(R.string.disable_notifications);
                    final int[] durations = getResources().getIntArray(R.array.mute_options_durations);
                    final CharSequence[] labels = new CharSequence[durations.length];
                    for (int i = 0; i < durations.length; ++i) {
                        if (durations[i] == -1) {
                            labels[i] = getString(R.string.until_further_notice);
                        } else {
                            labels[i] = TimeFrameUtils.resolve(ContactDetailsActivity.this, 1000L * durations[i]);
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
                        populateView();
                    });
                    builder1.create().show();
                } else {
                    mConversation.setMutedTill(0);
                    mConversation.setAttribute(Conversation.ATTRIBUTE_ALWAYS_NOTIFY, String.valueOf(choice.get() == 0));
                }
                xmppConnectionService.updateConversation(mConversation);
                populateView();
            });
            builder.create().show();
        }
    };

    @Override
    public void onRosterUpdate(final XmppConnectionService.UpdateRosterReason reason, final Contact contact) {
        refreshUi();
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }

    @Override
    public void OnUpdateBlocklist(final Status status) {
        refreshUi();
    }

    @Override
    protected void refreshUiReal() {
        populateView();
    }

    @Override
    protected String getShareableUri(boolean http) {
        if (http) {
            return "https://monocles.chat/chat/" + XmppUri.lameUrlEncode(contact.getJid().asBareJid().toEscapedString());
        } else {
            return "xmpp:" + Uri.encode(contact.getJid().asBareJid().toEscapedString(), "@/+");
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showInactiveOmemo = savedInstanceState != null && savedInstanceState.getBoolean("show_inactive_omemo", false);
        if (getIntent().getAction().equals(ACTION_VIEW_CONTACT)) {
            try {
                this.accountJid = Jid.ofEscaped(getIntent().getExtras().getString(EXTRA_ACCOUNT));
            } catch (final IllegalArgumentException ignored) {
            }
            try {
                this.contactJid = Jid.ofEscaped(getIntent().getExtras().getString("contact"));
            } catch (final IllegalArgumentException ignored) {
            }
        }
        this.messageFingerprint = getIntent().getStringExtra("fingerprint");
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_contact_details);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar());
        binding.showInactiveDevices.setOnClickListener(v -> {
            showInactiveOmemo = !showInactiveOmemo;
            populateView();
        });
        binding.addContactButton.setOnClickListener(v -> showAddToRosterDialog(contact));
        this.mNotifyStatusButton = findViewById(R.id.notification_status_button);
        this.mNotifyStatusButton.setOnClickListener(this.mNotifyStatusClickListener);
        this.mNotifyStatusText = findViewById(R.id.notification_status_text);
        mMediaAdapter = new MediaAdapter(this, R.dimen.media_size);
        this.binding.media.setAdapter(mMediaAdapter);
        GridManager.setupLayoutManager(this, this.binding.media, R.dimen.media_size);
        this.binding.recentThreads.setOnItemClickListener((a0, v, pos, a3) -> {
            final Conversation.Thread thread = (Conversation.Thread) binding.recentThreads.getAdapter().getItem(pos);
            switchToConversation(mConversation, null, false, null, false, true, null, thread.getThreadId());
        });
        showIntro(this, false);
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        savedInstanceState.putBoolean("show_inactive_omemo", showInactiveOmemo);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        } else {
            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            this.showDynamicTags = preferences.getBoolean(SettingsActivity.SHOW_DYNAMIC_TAGS, getResources().getBoolean(R.bool.show_dynamic_tags));
            this.showLastSeen = preferences.getBoolean("last_activity", getResources().getBoolean(R.bool.last_activity));
        }
        binding.mediaWrapper.setVisibility(Compatibility.hasStoragePermission(this) ? View.VISIBLE : View.GONE);
        mMediaAdapter.setAttachments(Collections.emptyList());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0)
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (requestCode == REQUEST_SYNC_CONTACTS && xmppConnectionServiceBound) {
                    showAddToPhoneBookDialog();
                    xmppConnectionService.loadPhoneContacts();
                    xmppConnectionService.startContactObserver();
                }
            }
    }

    protected void saveEdits() {
        binding.editTags.setVisibility(View.GONE);
        if (edit != null) {
            EditText text = edit.getActionView().findViewById(R.id.search_field);
            contact.setServerName(text.getText().toString());
            contact.setGroups(binding.editTags.getObjects().stream().map(tag -> tag.getName()).collect(Collectors.toList()));
            ContactDetailsActivity.this.xmppConnectionService.pushContactToServer(contact);
            populateView();
            edit.collapseActionView();
        }
        if (save != null) save.setVisible(false);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem menuItem) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setNegativeButton(getString(R.string.cancel), null);
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.action_audio_call:
                CallManager.checkPermissionAndTriggerAudioCall(this, mConversation);
                break;
            case R.id.action_video_call:
                CallManager.checkPermissionAndTriggerVideoCall(this, mConversation);
                break;
            case R.id.action_ongoing_call:
                CallManager.returnToOngoingCall(this, mConversation);
                break;
            case R.id.action_share_http:
                shareLink(true);
                break;
            case R.id.action_share_uri:
                shareLink(false);
                break;
            case R.id.action_save:
                saveEdits();
                break;
            case R.id.action_edit_contact:
                Uri systemAccount = contact.getSystemAccount();
                if (systemAccount == null) {
                    menuItem.expandActionView();
                    EditText text = menuItem.getActionView().findViewById(R.id.search_field);
                    text.setOnEditorActionListener((v, actionId, event) -> {
                        saveEdits();
                        return true;
                    });
                    text.setText(contact.getServerName());
                    text.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(text, InputMethodManager.SHOW_IMPLICIT);
                    }
                    binding.tags.setVisibility(View.GONE);
                    binding.editTags.clearSync();
                    for (final ListItem.Tag group : contact.getGroupTags()) {
                        binding.editTags.addObjectSync(group);
                    }
                    ArrayList<ListItem.Tag> tags = new ArrayList<>();
                    for (final Account account : xmppConnectionService.getAccounts()) {
                        for (Contact contact : account.getRoster().getContacts()) {
                            tags.addAll(contact.getTags(this));
                        }
                        for (Bookmark bookmark : account.getBookmarks()) {
                            tags.addAll(bookmark.getTags(this));
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
                    if (showDynamicTags) binding.editTags.setVisibility(View.VISIBLE);
                    if (save != null) save.setVisible(true);
                } else {
                    final AlertDialog.Builder EditConactbuilder = new AlertDialog.Builder(ContactDetailsActivity.this);
                    EditConactbuilder.setTitle(R.string.action_edit_contact);
                    EditConactbuilder.setMessage(R.string.edit_sytemaccount_or_xmppaccount);
                    //EditConactbuilder.setIcon(R.drawable.ic_mode_edit_black_18dp);
                    EditConactbuilder.setPositiveButton(R.string.edit_sytemaccount, new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface EditContactdialog, int whichButton) {

                            menuItem.collapseActionView();
                            if (save != null) save.setVisible(false);
                            Intent intent = new Intent(Intent.ACTION_EDIT);
                            intent.setDataAndType(systemAccount, Contacts.CONTENT_ITEM_TYPE);
                            intent.putExtra("finishActivityOnSaveCompleted", true);
                            try {
                                startActivity(intent);
                            } catch (ActivityNotFoundException e) {
                                Toast.makeText(ContactDetailsActivity.this, R.string.no_application_found_to_view_contact, Toast.LENGTH_SHORT).show();
                            }
                        }});
                    EditConactbuilder.setNegativeButton(R.string.edit_chat_contact, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface EditContactdialog, int whichButton) {
                            menuItem.expandActionView();
                            EditText text = menuItem.getActionView().findViewById(R.id.search_field);
                            text.setOnEditorActionListener((v, actionId, event) -> {
                                saveEdits();
                                return true;
                            });
                            text.setText(contact.getServerName());
                            text.requestFocus();
                            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (imm != null) {
                                imm.showSoftInput(text, InputMethodManager.SHOW_IMPLICIT);
                            }
                            binding.tags.setVisibility(View.GONE);
                            binding.editTags.clearSync();
                            for (final ListItem.Tag group : contact.getGroupTags()) {
                                binding.editTags.addObjectSync(group);
                            }
                            ArrayList<ListItem.Tag> tags = new ArrayList<>();
                            contact.getAccount();
                            for (final Account account : xmppConnectionService.getAccounts()) {
                                for (Contact contact : account.getRoster().getContacts()) {
                                    tags.addAll(contact.getTags(ContactDetailsActivity.this));
                                }
                                for (Bookmark bookmark : account.getBookmarks()) {
                                    tags.addAll(bookmark.getTags(ContactDetailsActivity.this));
                                }
                            }
                            Comparator<Map.Entry<ListItem.Tag,Integer>> sortTagsBy = Map.Entry.comparingByValue(Comparator.reverseOrder());
                            sortTagsBy = sortTagsBy.thenComparing(entry -> entry.getKey().getName());

                            ArrayAdapter<ListItem.Tag> adapter = new ArrayAdapter<>(
                                    ContactDetailsActivity.this,
                                    android.R.layout.simple_list_item_1,
                                    tags.stream()
                                            .collect(Collectors.toMap((x) -> x, (t) -> 1, (c1, c2) -> c1 + c2))
                                            .entrySet().stream()
                                            .sorted(sortTagsBy)
                                            .map(e -> e.getKey()).collect(Collectors.toList())
                            );
                            binding.editTags.setAdapter(adapter);
                            if (showDynamicTags) binding.editTags.setVisibility(View.VISIBLE);
                            if (save != null) save.setVisible(true);
                        }
                    });
                    AlertDialog EditContactdialog = EditConactbuilder.create();
                    EditContactdialog.show();
                }
                break;
            case R.id.action_block:
                BlockContactDialog.show(this, contact);
                break;
            case R.id.action_unblock:
                BlockContactDialog.show(this, contact);
                break;
            case R.id.action_activate_individual_notifications:
                if (!menuItem.isChecked()) {
                    this.mIndividualNotifications = true;
                } else {
                    if (Compatibility.runsTwentySix()) {
                        final AlertDialog.Builder removeIndividualNotificationDialog = new AlertDialog.Builder(ContactDetailsActivity.this);
                        removeIndividualNotificationDialog.setTitle(getString(R.string.remove_individual_notifications));
                        removeIndividualNotificationDialog.setMessage(JidDialog.style(this, R.string.remove_individual_notifications_message, contact.getJid().toEscapedString()));
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    final String time = String.valueOf(xmppConnectionService.getIndividualNotificationPreference(mConversation));
                    messageNotificationIntent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, this.getPackageName())
                            .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationService.INDIVIDUAL_NOTIFICATION_PREFIX + NotificationService.MESSAGES_CHANNEL_ID + "_" + mConversation.getUuid() + "_" + time);
                }
                startActivity(messageNotificationIntent);
                break;
            case R.id.action_call_notifications:
                Intent callNotificationIntent = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    final String time = String.valueOf(xmppConnectionService.getIndividualNotificationPreference(mConversation));
                    callNotificationIntent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, this.getPackageName())
                            .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationService.INDIVIDUAL_NOTIFICATION_PREFIX + NotificationService.INCOMING_CALLS_CHANNEL_ID + "_" + mConversation.getUuid() + "_" + time);
                }
                startActivity(callNotificationIntent);
                break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void editContact() {
        Uri systemAccount = contact.getSystemAccount();
        if (systemAccount == null) {
            quickEdit(contact.getServerName(), R.string.contact_name, value -> {
                contact.setServerName(value);
                ContactDetailsActivity.this.xmppConnectionService.pushContactToServer(contact);
                populateView();
                return null;
            }, true);
        } else {
            Intent intent = new Intent(Intent.ACTION_EDIT);
            intent.setDataAndType(systemAccount, Contacts.CONTENT_ITEM_TYPE);
            intent.putExtra("finishActivityOnSaveCompleted", true);
            try {
                startActivity(intent);
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
            } catch (ActivityNotFoundException e) {
                ToastCompat.makeText(ContactDetailsActivity.this, R.string.no_application_found_to_view_contact, ToastCompat.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem menuItemIndividualNotifications = menu.findItem(R.id.action_activate_individual_notifications);
        menuItemIndividualNotifications.setChecked(mIndividualNotifications);
        menuItemIndividualNotifications.setVisible(Compatibility.runsTwentySix());
        if (mConversation == null) {
            return true;
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.contact_details, menu);
        final MenuItem block = menu.findItem(R.id.action_block);
        final MenuItem unblock = menu.findItem(R.id.action_unblock);
        final MenuItem menuCall = menu.findItem(R.id.action_call);
        final MenuItem menuOngoingCall = menu.findItem(R.id.action_ongoing_call);
        final MenuItem menuVideoCall = menu.findItem(R.id.action_video_call);
        final MenuItem menuMessageNotification = menu.findItem(R.id.action_message_notifications);
        final MenuItem menuCallNotification = menu.findItem(R.id.action_call_notifications);
        edit = menu.findItem(R.id.action_edit_contact);
        save = menu.findItem(R.id.action_save);
        if (contact == null) {
            return true;
        }
        if (this.mConversation != null) {
            if (xmppConnectionService.hasInternetConnection()) {
                menuCall.setVisible(false);
                menuOngoingCall.setVisible(false);
            } else {
                final Optional<OngoingRtpSession> ongoingRtpSession = xmppConnectionService.getJingleConnectionManager().getOngoingRtpConnection(this.mConversation.getContact());
                if (ongoingRtpSession.isPresent()) {
                    menuOngoingCall.setVisible(true);
                    menuCall.setVisible(false);
                } else {
                    menuOngoingCall.setVisible(false);
                    final RtpCapability.Capability rtpCapability = RtpCapability.check(this.mConversation.getContact());
                    menuCall.setVisible(rtpCapability != RtpCapability.Capability.NONE);
                    menuVideoCall.setVisible(rtpCapability == RtpCapability.Capability.VIDEO);
                }
            }
            if (Compatibility.runsTwentySix()) {
                menuCallNotification.setVisible(xmppConnectionService.hasIndividualNotification(mConversation));
                menuMessageNotification.setVisible(xmppConnectionService.hasIndividualNotification(mConversation));
            } else {
                menuCallNotification.setVisible(false);
                menuMessageNotification.setVisible(false);
            }
        }
        final XmppConnection connection = contact.getAccount().getXmppConnection();
        if (connection != null && connection.getFeatures().blocking()) {
            if (this.contact.isBlocked()) {
                block.setVisible(false);
            } else {
                unblock.setVisible(false);
            }
        } else {
            unblock.setVisible(false);
            block.setVisible(false);
        }
        if (!contact.showInRoster()) {
            edit.setVisible(false);
        }
        edit.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                SoftKeyboardUtils.hideSoftKeyboard(ContactDetailsActivity.this);
                binding.editTags.setVisibility(View.GONE);
                if (save != null) save.setVisible(false);
                populateView();
                return true;
            }

            @Override
            public boolean onMenuItemActionExpand(MenuItem item) { return true; }
        });
        return super.onCreateOptionsMenu(menu);
    }

    private void populateView() {
        if (contact == null) {
            return;
        }
        if (binding.editTags.getVisibility() != View.GONE) return;
        int ic_notifications = getThemeResource(R.attr.icon_notifications, R.drawable.ic_notifications_black_24dp);
        int ic_notifications_off = getThemeResource(R.attr.icon_notifications_off, R.drawable.ic_notifications_off_black_24dp);
        int ic_notifications_paused = getThemeResource(R.attr.icon_notifications_paused, R.drawable.ic_notifications_paused_black_24dp);
        long mutedTill = mConversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL, 0);
        if (mutedTill == Long.MAX_VALUE) {
            mNotifyStatusText.setText(R.string.notify_never);
            mNotifyStatusButton.setImageResource(ic_notifications_off);
        } else if (System.currentTimeMillis() < mutedTill) {
            mNotifyStatusText.setText(R.string.notify_paused);
            mNotifyStatusButton.setImageResource(ic_notifications_paused);
        } else {
            mNotifyStatusButton.setImageResource(ic_notifications);
            mNotifyStatusText.setText(R.string.notify_on_all_messages);
        }

        TextView abtitle = binding.detailsContactName;
        abtitle.setText(contact.getDisplayName());
        abtitle.setSelected(true);
        abtitle.setClickable(false);

        invalidateOptionsMenu();
        if (contact.showInRoster()) {
            binding.detailsSendPresence.setVisibility(View.VISIBLE);
            binding.detailsSendPresence.setOnCheckedChangeListener(null);
            binding.detailsReceivePresence.setVisibility(View.VISIBLE);
            binding.detailsReceivePresence.setOnCheckedChangeListener(null);
            binding.addContactButton.setVisibility(View.VISIBLE);
            binding.addContactButton.setText(getString(R.string.action_delete_contact));
            binding.addContactButton.getBackground().setTint(getWarningButtonColor());
            binding.addContactButton.setTextColor(getWarningTextColor());
            binding.addContactButton.setOnClickListener(view -> {
                final AlertDialog.Builder deleteFromRosterDialog = new AlertDialog.Builder(ContactDetailsActivity.this);
                deleteFromRosterDialog.setNegativeButton(getString(R.string.cancel), null)
                        .setTitle(getString(R.string.action_delete_contact))
                        .setMessage(JidDialog.style(this, R.string.remove_contact_text, contact.getJid().toEscapedString()))
                        .setPositiveButton(getString(R.string.delete), removeFromRoster).create().show();
            });
            List<String> statusMessages = contact.getPresences().getStatusMessages();
            if (getPreferences().getBoolean("send_link_previews", true)) {
                if (statusMessages.isEmpty()) {
                    binding.statusMessage.setVisibility(View.GONE);
                    binding.statusBox.setVisibility(View.GONE);
                } else if (statusMessages.size() == 1) {
                    final String message = statusMessages.get(0);
                    binding.statusBox.setVisibility(View.VISIBLE);
                    binding.statusMessage.setVisibility(View.VISIBLE);
                    binding.statusImage.setVisibility(View.VISIBLE);
                    final Spannable span = new SpannableString(message);
                    if (Emoticons.isOnlyEmoji(message)) {
                        span.setSpan(new RelativeSizeSpan(3.0f), 0, message.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    binding.statusMessage.setText(span);

                    //find and show images from links
                    int size = statusMessages.size();
                    for (int i = 0; i < size; ++i) {
                        if (containsLink(statusMessages.get(i))) {
                            List<String> url = extractUrls(statusMessages.get(i));
                            for (int j = 0; j < url.size(); j++) {
                                if (this != null && xmppConnectionService.getBooleanPreference("play_gif_inside", R.bool.play_gif_inside)) {
                                    Glide.with(this).load(url.get(j)).into(binding.statusImage);
                                } else {
                                    Glide.with(this).asBitmap().load(url.get(j)).into(binding.statusImage);
                                }
                                binding.statusImage.setOnClickListener(new View.OnClickListener() {
                                    public void onClick(View v) {
                                        Intent intent = new Intent();
                                        intent.setAction(Intent.ACTION_VIEW);
                                        intent.addCategory(Intent.CATEGORY_BROWSABLE);
                                        intent.setData(Uri.parse(url.get(0)));
                                        startActivity(intent);
                                    }
                                });
                                binding.statusImage.setOnLongClickListener(v -> {
                                    ShowStatusImagePopup(ContactDetailsActivity.this, url.get(0));
                                    return true;
                                });
                            }
                        }
                    }
                } else {
                    StringBuilder builder = new StringBuilder();
                    binding.statusBox.setVisibility(View.VISIBLE);
                    binding.statusMessage.setVisibility(View.VISIBLE);
                    binding.statusImage.setVisibility(View.VISIBLE);
                    int s = statusMessages.size();
                    for (int i = 0; i < s; ++i) {
                        builder.append(statusMessages.get(i));
                        if (i < s - 1) {
                            if (containsLink(statusMessages.get(i))) {
                                List<String> url = extractUrls(statusMessages.get(i));
                                for (int j = 0; j < url.size(); j++) {
                                    if (this != null && xmppConnectionService.getBooleanPreference("play_gif_inside", R.bool.play_gif_inside)) {
                                        Glide.with(this).load(url.get(j)).into(binding.statusImage);
                                    } else {
                                        Glide.with(this).asBitmap().load(url.get(j)).into(binding.statusImage);
                                    }
                                    binding.statusImage.setOnClickListener(new View.OnClickListener() {
                                        public void onClick(View v) {
                                            Intent intent = new Intent();
                                            intent.setAction(Intent.ACTION_VIEW);
                                            intent.addCategory(Intent.CATEGORY_BROWSABLE);
                                            intent.setData(Uri.parse(url.get(0)));
                                            startActivity(intent);
                                        }
                                    });
                                    binding.statusImage.setOnLongClickListener(v -> {
                                        ShowStatusImagePopup(ContactDetailsActivity.this, url.get(0));
                                        return true;
                                    });
                                }
                            }
                            builder.append("\n");
                        }
                    }
                    binding.statusMessage.setText(builder);


                }
            } else {
                if (statusMessages.isEmpty()) {
                    binding.statusBox.setVisibility(View.GONE);
                    binding.statusMessage.setVisibility(View.GONE);
                } else if (statusMessages.size() == 1) {
                    final String message = statusMessages.get(0);
                    binding.statusBox.setVisibility(View.VISIBLE);
                    binding.statusMessage.setVisibility(View.VISIBLE);
                    final Spannable span = new SpannableString(message);
                    if (Emoticons.isOnlyEmoji(message)) {
                        span.setSpan(new RelativeSizeSpan(3.0f), 0, message.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    binding.statusMessage.setText(span);
                } else {
                    StringBuilder builder = new StringBuilder();
                    binding.statusBox.setVisibility(View.VISIBLE);
                    binding.statusMessage.setVisibility(View.VISIBLE);
                    int s = statusMessages.size();
                    for (int i = 0; i < s; ++i) {
                        builder.append(statusMessages.get(i));
                        if (i < s - 1) {
                            builder.append("\n");
                        }
                    }
                    binding.statusMessage.setText(builder);
                }
            }
            String resources = contact.getPresences().getMostAvailableResource();
            if (resources.isEmpty()) {
                binding.resource.setVisibility(View.GONE);
            } else {
                binding.resource.setVisibility(View.VISIBLE);
                binding.resource.setText(resources);
            }
            if (contact.getOption(Contact.Options.FROM)) {
                binding.detailsSendPresence.setText(R.string.send_presence_updates);
                binding.detailsSendPresence.setChecked(true);
            } else if (contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
                binding.detailsSendPresence.setChecked(false);
                binding.detailsSendPresence.setText(R.string.send_presence_updates);
            } else {
                binding.detailsSendPresence.setText(R.string.preemptively_grant);
                if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
                    binding.detailsSendPresence.setChecked(true);
                } else {
                    binding.detailsSendPresence.setChecked(false);
                }
            }
            if (contact.getOption(Contact.Options.TO)) {
                binding.detailsReceivePresence.setText(R.string.receive_presence_updates);
                binding.detailsReceivePresence.setChecked(true);
            } else {
                binding.detailsReceivePresence.setText(R.string.ask_for_presence_updates);
                if (contact.getOption(Contact.Options.ASKING)) {
                    binding.detailsReceivePresence.setChecked(true);
                } else {
                    binding.detailsReceivePresence.setChecked(false);
                }
            }
            if (contact.getAccount().isOnlineAndConnected()) {
                binding.detailsReceivePresence.setEnabled(true);
                binding.detailsSendPresence.setEnabled(true);
            } else {
                binding.detailsReceivePresence.setEnabled(false);
                binding.detailsSendPresence.setEnabled(false);
            }
            binding.detailsSendPresence.setOnCheckedChangeListener(this.mOnSendCheckedChange);
            binding.detailsReceivePresence.setOnCheckedChangeListener(this.mOnReceiveCheckedChange);
        } else {
            binding.addContactButton.setVisibility(View.VISIBLE);
            binding.addContactButton.setText(getString(R.string.add_contact));
            binding.addContactButton.getBackground().clearColorFilter();
            binding.addContactButton.setTextColor(getDefaultButtonTextColor());
            binding.addContactButton.setOnClickListener(view -> showAddToRosterDialog(contact));
            binding.detailsSendPresence.setVisibility(View.GONE);
            binding.detailsReceivePresence.setVisibility(View.GONE);
            binding.statusMessage.setVisibility(View.GONE);
        }

        if (contact.isBlocked() && !this.showDynamicTags) {
            binding.detailsLastseen.setVisibility(View.VISIBLE);
            binding.detailsLastseen.setText(R.string.contact_blocked);
        } else {
            if (showLastSeen && contact.getLastseen() > 0 && contact.getPresences().allOrNonSupport(Namespace.IDLE)) {
                binding.detailsLastseen.setVisibility(View.VISIBLE);
                binding.detailsLastseen.setText(UIHelper.lastseen(getApplicationContext(), contact.isActive(), contact.getLastseen()));
            } else {
                binding.detailsLastseen.setVisibility(View.GONE);
            }
        }

        binding.jid.setText(IrregularUnicodeDetector.style(this, contact.getJid()));
        String account;
        if (Config.DOMAIN_LOCK != null) {
            account = contact.getAccount().getJid().getEscapedLocal();
        } else {
            account = contact.getAccount().getJid().asBareJid().toEscapedString();
        }
        binding.detailsAccount.setText(getString(R.string.using_account, account));
        AvatarWorkerTask.loadAvatar(contact, binding.detailsContactBadge, R.dimen.avatar_on_details_screen_size);
        binding.detailsContactBadge.setVisibility(View.VISIBLE);
        binding.detailsContactBadge.setOnClickListener(this::onBadgeClick);
        binding.detailsContactBadge.setOnLongClickListener(v -> {
            ShowAvatarPopup(ContactDetailsActivity.this, contact);
            return true;
        });
        if (xmppConnectionService.multipleAccounts()) {
            binding.detailsAccount.setVisibility(View.VISIBLE);
        } else {
            binding.detailsAccount.setVisibility(View.GONE);
        }

        binding.detailsContactKeys.removeAllViews();
        boolean hasKeys = false;
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final AxolotlService axolotlService = contact.getAccount().getAxolotlService();
        if (Config.supportOmemo() && axolotlService != null) {
            final Collection<XmppAxolotlSession> sessions = axolotlService.findSessionsForContact(contact);
            boolean anyActive = false;
            for (XmppAxolotlSession session : sessions) {
                anyActive = session.getTrust().isActive();
                if (anyActive) {
                    break;
                }
            }
            boolean skippedInactive = false;
            boolean showsInactive = false;
            boolean showUnverifiedWarning = false;
            for (final XmppAxolotlSession session : sessions) {
                final FingerprintStatus trust = session.getTrust();
                hasKeys |= !trust.isCompromised();
                if (!trust.isActive() && anyActive) {
                    if (showInactiveOmemo) {
                        showsInactive = true;
                    } else {
                        skippedInactive = true;
                        continue;
                    }
                }
                if (!trust.isCompromised()) {
                    boolean highlight = session.getFingerprint().equals(messageFingerprint);
                    addFingerprintRow(binding.detailsContactKeys, session, highlight);
                }
                if (trust.isUnverified()) {
                    showUnverifiedWarning = true;
                }
            }
            binding.unverifiedWarning.setVisibility(showUnverifiedWarning ? View.VISIBLE : View.GONE);
            if (showsInactive || skippedInactive) {
                binding.showInactiveDevices.setText(showsInactive ? R.string.hide_inactive_devices : R.string.show_inactive_devices);
                binding.showInactiveDevices.setVisibility(View.VISIBLE);
            } else {
                binding.showInactiveDevices.setVisibility(View.GONE);
            }
        } else {
            binding.showInactiveDevices.setVisibility(View.GONE);
        }
        final boolean isCameraFeatureAvailable = isCameraFeatureAvailable();
        binding.scanButton.setVisibility(hasKeys && isCameraFeatureAvailable ? View.VISIBLE : View.GONE);
        if (hasKeys) {
            binding.scanButton.setOnClickListener((v) -> ScanActivity.scan(this));
        }
        if (Config.supportOpenPgp() && contact.getPgpKeyId() != 0) {
            hasKeys = true;
            View view = inflater.inflate(R.layout.contact_key, binding.detailsContactKeys, false);
            TextView key = view.findViewById(R.id.key);
            TextView keyType = view.findViewById(R.id.key_type);
            keyType.setText(R.string.openpgp_key_id);
            if ("pgp".equals(messageFingerprint)) {
                keyType.setTextColor(ContextCompat.getColor(this, R.color.accent));
            }
            key.setText(OpenPgpUtils.convertKeyIdToHex(contact.getPgpKeyId()));
            final OnClickListener openKey = v -> launchOpenKeyChain(contact.getPgpKeyId());
            view.setOnClickListener(openKey);
            key.setOnClickListener(openKey);
            keyType.setOnClickListener(openKey);
            binding.detailsContactKeys.addView(view);
        }
        binding.keysWrapper.setVisibility(hasKeys ? View.VISIBLE : View.GONE);

        List<ListItem.Tag> tagList = contact.getTags(this);
        if (tagList.isEmpty() || !this.showDynamicTags) {
            binding.tags.setVisibility(View.GONE);
        } else {
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

    }

    private void onBadgeClick(View view) {
        final Uri systemAccount = contact.getSystemAccount();
        if (systemAccount == null) {
            checkContactPermissionAndShowAddDialog();
        } else {
            final Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(systemAccount);
            try {
                startActivity(intent);
            } catch (final ActivityNotFoundException e) {
                ToastCompat.makeText(this, R.string.no_application_found_to_view_contact, ToastCompat.LENGTH_SHORT).show();
            }
        }
    }

    public void onBackendConnected() {
        if (accountJid != null && contactJid != null && this != null) {
            Account account = xmppConnectionService.findAccountByJid(accountJid);
            if (account == null) {
                return;
            }
            this.mConversation = xmppConnectionService.findConversation(account, contactJid, false);
            this.contact = account.getRoster().getContact(contactJid);
            if (mPendingFingerprintVerificationUri != null) {
                processFingerprintVerification(mPendingFingerprintVerificationUri);
                mPendingFingerprintVerificationUri = null;
            }
            if (Compatibility.hasStoragePermission(this)) {
                final int limit = GridManager.getCurrentColumnCount(this.binding.media);
                xmppConnectionService.getAttachments(account, contact.getJid().asBareJid(), limit, this);
                this.binding.showMedia.setOnClickListener((v) -> MediaBrowserActivity.launch(this, contact));
            }
            this.mIndividualNotifications = xmppConnectionService.hasIndividualNotification(mConversation);

            final VcardAdapter items = new VcardAdapter();
            binding.profileItems.setAdapter(items);
            binding.profileItems.setOnItemClickListener((a0, v, pos, a3) -> {
                final Uri uri = items.getUri(pos);
                if (uri == null) return;

                if ("xmpp".equals(uri.getScheme())) {
                    switchToConversation(xmppConnectionService.findOrCreateConversation(account, Jid.of(uri.getSchemeSpecificPart()), false, true));
                } else {
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(this, R.string.no_application_found_to_open_link, Toast.LENGTH_SHORT).show();
                    }
                }
            });
            binding.profileItems.setOnItemLongClickListener((a0, v, pos, a3) -> {
                String toCopy = null;
                final Uri uri = items.getUri(pos);
                if (uri != null) toCopy = uri.toString();
                if (toCopy == null) {
                    toCopy = items.getItem(pos).findChildContent("text", Namespace.VCARD4);
                }

                if (toCopy == null) return false;
                if (ShareUtil.copyTextToClipboard(ContactDetailsActivity.this, toCopy, R.string.message)) {
                    Toast.makeText(ContactDetailsActivity.this, R.string.message_copied_to_clipboard, Toast.LENGTH_SHORT).show();
                }
                return true;
            });
            xmppConnectionService.fetchVcard4(account, contact, (vcard4) -> {
                if (vcard4 == null) return;
                runOnUiThread(() -> {
                    binding.profile.setVisibility(View.VISIBLE);
                    for (Element el : vcard4.getChildren()) {
                        if (el.findChildEnsureSingle("uri", Namespace.VCARD4) != null || el.findChildEnsureSingle("text", Namespace.VCARD4) != null) {
                            items.add(el);
                        }
                    }
                    Util.justifyListViewHeightBasedOnChildren(binding.profileItems);
                });
            });
            populateView();
        }
    }

    @Override
    public void onKeyStatusUpdated(AxolotlService.FetchStatus report) {
        refreshUi();
    }

    @Override
    protected void processFingerprintVerification(XmppUri uri) {
        if (contact != null && contact.getJid().asBareJid().equals(uri.getJid()) && uri.hasFingerprints()) {
            if (xmppConnectionService.verifyFingerprints(contact, uri.getFingerprints())) {
                ToastCompat.makeText(this, R.string.verified_fingerprints, ToastCompat.LENGTH_SHORT).show();
            }
        } else {
            ToastCompat.makeText(this, R.string.invalid_barcode, ToastCompat.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMediaLoaded(List<Attachment> attachments) {
        runOnUiThread(() -> {
            int limit = GridManager.getCurrentColumnCount(binding.media);
            mMediaAdapter.setAttachments(attachments.subList(0, Math.min(limit, attachments.size())));
            binding.mediaWrapper.setVisibility(attachments.size() > 0 ? View.VISIBLE : View.GONE);
        });
    }

    class VcardAdapter extends ArrayAdapter<Element> {
        VcardAdapter() { super(ContactDetailsActivity.this, 0); }

        private Drawable getDrawable(int attr) {
            final TypedValue typedvalueattr = new TypedValue();
            getTheme().resolveAttribute(attr, typedvalueattr, true);
            return getResources().getDrawable(typedvalueattr.resourceId);
        }

        @Override
        public View getView(int position, View view, @NonNull ViewGroup parent) {
            final CommandRowBinding binding = DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), R.layout.command_row, parent, false);
            final Element item = getItem(position);

            if (item.getName().equals("org")) {
                binding.command.setCompoundDrawablesRelativeWithIntrinsicBounds(getDrawable(R.attr.icon_org), null, null, null);
                binding.command.setCompoundDrawablePadding(20);
            } else if (item.getName().equals("impp")) {
                binding.command.setCompoundDrawablesRelativeWithIntrinsicBounds(getDrawable(R.attr.icon_chat), null, null, null);
                binding.command.setCompoundDrawablePadding(20);
            } else if (item.getName().equals("url")) {
                binding.command.setCompoundDrawablesRelativeWithIntrinsicBounds(getDrawable(R.attr.icon_link), null, null, null);
                binding.command.setCompoundDrawablePadding(20);
            }

            final Uri uri = getUri(position);
            if (uri != null && uri.getScheme() != null) {
                if (uri.getScheme().equals("xmpp")) {
                    binding.command.setText(uri.getSchemeSpecificPart());
                    binding.command.setCompoundDrawablesRelativeWithIntrinsicBounds(getResources().getDrawable(R.drawable.xmpp_logo), null, null, null);
                    binding.command.setCompoundDrawablePadding(20);
                } else if (uri.getScheme().equals("tel")) {
                    binding.command.setText(uri.getSchemeSpecificPart());
                    binding.command.setCompoundDrawablesRelativeWithIntrinsicBounds(getDrawable(R.attr.icon_call), null, null, null);
                    binding.command.setCompoundDrawablePadding(20);
                } else if (uri.getScheme().equals("mailto")) {
                    binding.command.setText(uri.getSchemeSpecificPart());
                    binding.command.setCompoundDrawablesRelativeWithIntrinsicBounds(getDrawable(R.attr.icon_email), null, null, null);
                    binding.command.setCompoundDrawablePadding(20);
                } else if (uri.getScheme().equals("http") || uri.getScheme().equals("https")) {
                    binding.command.setText(uri.toString());
                    binding.command.setCompoundDrawablesRelativeWithIntrinsicBounds(getDrawable(R.attr.icon_link), null, null, null);
                    binding.command.setCompoundDrawablePadding(20);
                } else {
                    binding.command.setText(uri.toString());
                    binding.command.setPadding(0,0 ,0, 20);
                }
            } else {
                final String text = item.findChildContent("text", Namespace.VCARD4);
                binding.command.setText(text);
            }

            return binding.getRoot();
        }

        public Uri getUri(int pos) {
            final Element item = getItem(pos);
            final String uriS = item.findChildContent("uri", Namespace.VCARD4);
            if (uriS != null) return Uri.parse(uriS).normalizeScheme();
            if (item.getName().equals("email")) return Uri.parse("mailto:" + item.findChildContent("text", Namespace.VCARD4));
            return null;
        }
    }

    public static boolean containsLink(String input) {
        boolean result = false;

        String[] parts = input.split("\\s+");

        for (String item : parts) {
            if (android.util.Patterns.WEB_URL.matcher(item).matches()) {
                result = true;
                break;
            }
        }

        return result;
    }

    public static List<String> extractUrls(String input)
    {
        List<String> result = new ArrayList<String>();

        String[] words = input.split("\\s+");


        Pattern pattern = Patterns.WEB_URL;
        for(String word : words)
        {
            if(pattern.matcher(word).find())
            {
                if(!word.toLowerCase().contains("http://") && !word.toLowerCase().contains("https://"))
                {
                    word = "http://" + word;
                }
                result.add(word);
            }
        }

        return result;
    }
}
