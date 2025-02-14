package eu.siacs.conversations.ui;

import static android.app.Activity.RESULT_CANCELED;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static eu.siacs.conversations.persistance.FileBackend.APP_DIRECTORY;
import static eu.siacs.conversations.persistance.FileBackend.SENT_AUDIOS;
import static eu.siacs.conversations.ui.SettingsActivity.HIDE_WEBXDC_STORE_HINT;
import static eu.siacs.conversations.ui.SettingsActivity.HIDE_YOU_ARE_NOT_PARTICIPATING;
import static eu.siacs.conversations.ui.SettingsActivity.WARN_UNENCRYPTED_CHAT;
import static eu.siacs.conversations.ui.XmppActivity.EXTRA_ACCOUNT;
import static eu.siacs.conversations.ui.XmppActivity.REQUEST_INVITE_TO_CONVERSATION;
import static eu.siacs.conversations.ui.util.SoftKeyboardUtils.hideSoftKeyboard;
import static eu.siacs.conversations.utils.CameraUtils.getCameraApp;
import static eu.siacs.conversations.utils.CameraUtils.showCameraChooser;
import static eu.siacs.conversations.utils.Compatibility.hasStoragePermission;
import static eu.siacs.conversations.utils.PermissionUtils.allGranted;
import static eu.siacs.conversations.utils.PermissionUtils.audioGranted;
import static eu.siacs.conversations.utils.PermissionUtils.cameraGranted;
import static eu.siacs.conversations.utils.PermissionUtils.getFirstDenied;
import static eu.siacs.conversations.utils.PermissionUtils.readGranted;
import static eu.siacs.conversations.utils.StorageHelper.getConversationsDirectory;
import static eu.siacs.conversations.xmpp.Patches.ENCRYPTION_EXCEPTIONS;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.icu.util.Calendar;
import android.icu.util.TimeZone;
import android.media.MediaRecorder;
import android.media.MicrophoneDirection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;
import androidx.databinding.DataBindingUtil;
import androidx.documentfile.provider.DocumentFile;
import androidx.emoji2.emojipicker.EmojiPickerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;

import com.bumptech.glide.Glide;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.common.base.Optional;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.otaliastudios.autocomplete.Autocomplete;
import com.otaliastudios.autocomplete.AutocompleteCallback;
import com.otaliastudios.autocomplete.AutocompletePresenter;
import com.otaliastudios.autocomplete.CharPolicy;
import com.otaliastudios.autocomplete.RecyclerViewPresenter;

import net.java.otr4j.session.SessionStatus;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.monocles.mod.BobTransfer;
import de.monocles.mod.EmojiSearch;
import de.monocles.mod.GifsAdapter;
import de.monocles.mod.KeyboardHeightProvider;
import de.monocles.mod.WebxdcPage;
import de.monocles.mod.WebxdcStore;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.databinding.FragmentConversationBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Edit;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.User;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.entities.ReadByMarker;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.entities.TransferablePlaceholder;
import eu.siacs.conversations.http.HttpDownloadConnection;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.AttachFileToConversationRunnable;
import eu.siacs.conversations.services.CallIntegrationConnectionService;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.CommandAdapter;
import eu.siacs.conversations.ui.adapter.MediaPreviewAdapter;
import eu.siacs.conversations.ui.adapter.MessageAdapter;
import eu.siacs.conversations.ui.adapter.MessageLogAdapter;
import eu.siacs.conversations.ui.adapter.UserAdapter;
import eu.siacs.conversations.ui.adapter.model.MessageLogModel;
import eu.siacs.conversations.ui.util.ActivityResult;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.ui.util.CallManager;
import eu.siacs.conversations.ui.util.ConversationMenuConfigurator;
import eu.siacs.conversations.ui.util.DateSeparator;
import eu.siacs.conversations.ui.util.EditMessageActionModeCallback;
import eu.siacs.conversations.ui.util.ListViewUtils;
import eu.siacs.conversations.ui.util.MucDetailsContextMenuHelper;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.ui.util.PresenceSelector;
import eu.siacs.conversations.ui.util.QuoteHelper;
import eu.siacs.conversations.ui.util.ScrollState;
import eu.siacs.conversations.ui.util.SendButtonAction;
import eu.siacs.conversations.ui.util.SendButtonTool;
import eu.siacs.conversations.ui.util.ShareUtil;
import eu.siacs.conversations.ui.util.StyledAttributes;
import eu.siacs.conversations.ui.util.ViewUtil;
import eu.siacs.conversations.ui.widget.EditMessage;
import eu.siacs.conversations.utils.CameraUtils;
import eu.siacs.conversations.utils.ChatBackgroundHelper;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.Emoticons;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.MenuDoubleTabUtil;
import eu.siacs.conversations.utils.MessageUtils;
import eu.siacs.conversations.utils.MimeUtils;
import eu.siacs.conversations.utils.NickValidityChecker;
import eu.siacs.conversations.utils.PermissionUtils;
import eu.siacs.conversations.utils.QuickLoader;
import eu.siacs.conversations.utils.StylingHelper;
import eu.siacs.conversations.utils.TimeFrameUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.jingle.AbstractJingleConnection;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.jingle.JingleFileTransferConnection;
import eu.siacs.conversations.xmpp.jingle.Media;
import eu.siacs.conversations.xmpp.jingle.OngoingRtpSession;
import eu.siacs.conversations.xmpp.jingle.RtpCapability;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import io.ipfs.cid.Cid;
import me.drakeet.support.toast.ToastCompat;

public class ConversationFragment extends XmppFragment
        implements EditMessage.KeyboardListener,
        MessageAdapter.OnContactPictureLongClicked,
        MessageAdapter.OnContactPictureClicked,
        MessageAdapter.OnInlineImageLongClicked {

    //Voice recoder
    private MediaRecorder mRecorder;
    private Integer oldOrientation;
    private int mStartTime = 0;
    private boolean recording = false;

    private CountDownLatch outputFileWrittenLatch = new CountDownLatch(1);

    private final Handler mHandler = new Handler();
    private final Runnable mTickExecutor = new Runnable() {
        @Override
        public void run() {
            tick();
            mHandler.postDelayed(mTickExecutor, 1000);
        }
    };

    private File mOutputFile;

    private FileObserver mFileObserver;

    public static final int REQUEST_SEND_MESSAGE = 0x0201;
    public static final int REQUEST_DECRYPT_PGP = 0x0202;
    public static final int REQUEST_ENCRYPT_MESSAGE = 0x0207;
    public static final int REQUEST_TRUST_KEYS_TEXT = 0x0208;
    public static final int REQUEST_TRUST_KEYS_ATTACHMENTS = 0x0209;
    public static final int REQUEST_START_DOWNLOAD = 0x0210;
    public static final int REQUEST_ADD_EDITOR_CONTENT = 0x0211;
    public static final int REQUEST_COMMIT_ATTACHMENTS = 0x0212;
    public static final int ATTACHMENT_CHOICE = 0x0300;
    public static final int REQUEST_START_AUDIO_CALL = 0x213;
    public static final int REQUEST_START_VIDEO_CALL = 0x214;
    public static final int REQUEST_SAVE_STICKER = 0x215;
    public static final int REQUEST_SAVE_GIF = 0x216;
    public static final int REQUEST_WEBXDC_STORE = 0x217;
    public static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0x0301;
    public static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 0x0302;
    public static final int ATTACHMENT_CHOICE_CHOOSE_FILE = 0x0303;
    public static final int ATTACHMENT_CHOICE_RECORD_VOICE = 0x0304;
    public static final int ATTACHMENT_CHOICE_LOCATION = 0x0305;
    public static final int ATTACHMENT_CHOICE_CHOOSE_VIDEO = 0x0306;
    public static final int ATTACHMENT_CHOICE_RECORD_VIDEO = 0x0307;
    public static final int ATTACHMENT_CHOICE_INVALID = 0x0399;

    public static final String RECENTLY_USED_QUICK_ACTION = "recently_used_quick_action";
    public static final String STATE_CONVERSATION_UUID = ConversationFragment.class.getName() + ".uuid";
    public static final String STATE_SCROLL_POSITION = ConversationFragment.class.getName() + ".scroll_position";
    public static final String STATE_PHOTO_URI = ConversationFragment.class.getName() + ".media_previews";
    public static final String STATE_MEDIA_PREVIEWS = ConversationFragment.class.getName() + ".take_photo_uri";

    private static final String STATE_LAST_MESSAGE_UUID = "state_last_message_uuid";

    private final List<Message> messageList = new ArrayList<>();
    private final PendingItem<ActivityResult> postponedActivityResult = new PendingItem<>();
    private final PendingItem<String> pendingConversationsUuid = new PendingItem<>();
    private final PendingItem<ArrayList<Attachment>> pendingMediaPreviews = new PendingItem<>();
    private final PendingItem<Bundle> pendingExtras = new PendingItem<>();
    private final PendingItem<Uri> pendingTakePhotoUri = new PendingItem<>();
    private final PendingItem<Uri> pendingTakeVideoUri = new PendingItem<>();
    private final PendingItem<ScrollState> pendingScrollState = new PendingItem<>();
    private final PendingItem<String> pendingLastMessageUuid = new PendingItem<>();
    private final PendingItem<Message> pendingMessage = new PendingItem<>();
    public Uri mPendingEditorContent = null;
    public FragmentConversationBinding binding;
    protected MessageAdapter messageListAdapter;
    protected CommandAdapter commandAdapter;
    private String lastMessageUuid = null;
    private Conversation conversation;
    private Toast messageLoaderToast;
    private static ConversationsActivity activity;
    private Menu mOptionsMenu;
    //Stickerspaths
    private File[] filesStickers;
    private String[] filesPathsStickers;
    private String[] filesNamesStickers;
    File dirStickers = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + APP_DIRECTORY + File.separator + "Stickers");
    //Gifspaths
    private File[] files;
    private String[] filesPaths;
    private String[] filesNames;
    File dirGifs = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + APP_DIRECTORY + File.separator + "GIFs");
    private EmojiSearch emojiSearch = null;


    protected OnClickListener clickToVerify = new OnClickListener() {
        @Override
        public void onClick(View v) {
            activity.verifyOtrSessionDialog(conversation, v);
        }
    };

    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd  hh:mm (z)", Locale.US);

    private boolean reInitRequiredOnStart = true;
    private int identiconWidth = -1;
    private File savingAsSticker = null;
    private File savingAsGif = null;
    private MediaPreviewAdapter mediaPreviewAdapter;

    private KeyboardHeightProvider.KeyboardHeightListener keyboardHeightListener = null;
    private KeyboardHeightProvider keyboardHeightProvider = null;
    private final OnClickListener clickToMuc = new OnClickListener() {

        @Override
        public void onClick(View v) {
            ConferenceDetailsActivity.open(activity, conversation);
        }
    };
    private final OnClickListener leaveMuc = new OnClickListener() {

        @Override
        public void onClick(View v) {
            activity.xmppConnectionService.archiveConversation(conversation);
        }
    };
    private final OnClickListener joinMuc = new OnClickListener() {

        @Override
        public void onClick(View v) {
            activity.xmppConnectionService.joinMuc(conversation);
        }
    };

    private final OnClickListener acceptJoin = new OnClickListener() {
        @Override
        public void onClick(View v) {
            conversation.setAttribute("accept_non_anonymous", true);
            activity.xmppConnectionService.updateConversation(conversation);
            activity.xmppConnectionService.joinMuc(conversation);
        }
    };

    private final OnClickListener enterPassword = new OnClickListener() {

        @Override
        public void onClick(View v) {
            MucOptions muc = conversation.getMucOptions();
            String password = muc.getPassword();
            if (password == null) {
                password = "";
            }
            activity.quickPasswordEdit(password, value -> {
                activity.xmppConnectionService.providePasswordForMuc(conversation, value);
                return null;
            });
        }
    };

    private final OnClickListener meCommand = v -> Objects.requireNonNull(binding.textinput.getText()).insert(0, Message.ME_COMMAND + " ");
    private final OnClickListener quote = v -> insertQuote();
    private final OnClickListener boldText = v -> insertFormatting("bold");
    private final OnClickListener italicText = v -> insertFormatting("italic");
    private final OnClickListener monospaceText = v -> insertFormatting("monospace");
    private final OnClickListener strikethroughText = v -> insertFormatting("strikethrough");
    private final OnClickListener close = v -> closeFormatting();

    private void closeFormatting() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.close);
        builder.setMessage(R.string.close_format_text);
        builder.setPositiveButton(getString(R.string.close),
                (dialog, which) -> {
                    final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
                    preferences.edit().putBoolean("showtextformatting", false).apply();
                    binding.textformat.setVisibility(GONE);
                    updateSendButton();
                });
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.create().show();
    }

    private void insertFormatting(String format) {
        final String BOLD = "*";
        final String ITALIC = "_";
        final String MONOSPACE = "`";
        final String STRIKETHROUGH = "~";

        int selStart = this.binding.textinput.getSelectionStart();
        int selEnd = this.binding.textinput.getSelectionEnd();
        int min = 0;
        int max = this.binding.textinput.getText().length();
        if (this.binding.textinput.isFocused()) {
            selStart = this.binding.textinput.getSelectionStart();
            selEnd = this.binding.textinput.getSelectionEnd();
            min = Math.max(0, Math.min(selStart, selEnd));
            max = Math.max(0, Math.max(selStart, selEnd));
        }
        final CharSequence selectedText = this.binding.textinput.getText().subSequence(min, max);

        if (format.equals("bold")) {
            if (selectedText.length() != 0) {
                this.binding.textinput.getText().replace(Math.min(selStart, selEnd), Math.max(selStart, selEnd),
                        BOLD + selectedText + BOLD, 0, selectedText.length() + 2);
            } else {
                this.binding.textinput.getText().insert(this.binding.textinput.getSelectionStart(), (BOLD));
            }
            return;
        } else if (format.equals("italic")) {
            if (selectedText.length() != 0) {
                this.binding.textinput.getText().replace(Math.min(selStart, selEnd), Math.max(selStart, selEnd),
                        ITALIC + selectedText + ITALIC, 0, selectedText.length() + 2);
            } else {
                this.binding.textinput.getText().insert(this.binding.textinput.getSelectionStart(), (ITALIC));
            }
            return;
        } else if (format.equals("monospace")) {
            if (selectedText.length() != 0) {
                this.binding.textinput.getText().replace(Math.min(selStart, selEnd), Math.max(selStart, selEnd),
                        MONOSPACE + selectedText + MONOSPACE, 0, selectedText.length() + 2);
            } else {
                this.binding.textinput.getText().insert(this.binding.textinput.getSelectionStart(), (MONOSPACE));
            }
            return;
        } else if (format.equals("strikethrough")) {
            if (selectedText.length() != 0) {
                this.binding.textinput.getText().replace(Math.min(selStart, selEnd), Math.max(selStart, selEnd),
                        STRIKETHROUGH + selectedText + STRIKETHROUGH, 0, selectedText.length() + 2);
            } else {
                this.binding.textinput.getText().insert(this.binding.textinput.getSelectionStart(), (STRIKETHROUGH));
            }
            return;
        }
    }

    private void insertQuote() {
        int pos = 0;
        if (this.binding.textinput.getSelectionStart() == this.binding.textinput.getSelectionEnd()) {
            pos = this.binding.textinput.getSelectionStart();
        }
        if (pos == 0) {
            Objects.requireNonNull(binding.textinput.getText()).insert(0, QuoteHelper.QUOTE_CHAR + " ");
        } else {
            Objects.requireNonNull(binding.textinput.getText()).insert(pos, System.getProperty("line.separator") + QuoteHelper.QUOTE_CHAR + " ");
        }
    }

    private final OnScrollListener mOnScrollListener = new OnScrollListener() {

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            if (AbsListView.OnScrollListener.SCROLL_STATE_IDLE == scrollState) {
                updateThreadFromLastMessage();
                fireReadEvent();
            }
        }

        @Override
        public void onScroll(final AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            toggleScrollDownButton(view);
            synchronized (ConversationFragment.this.messageList) {
                if (firstVisibleItem < 5 && conversation != null && conversation.messagesLoaded.compareAndSet(true, false) && messageList.size() > 0) {
                    long timestamp = conversation.loadMoreTimestamp();
                    activity.xmppConnectionService.loadMoreMessages(conversation, timestamp, new XmppConnectionService.OnMoreMessagesLoaded() {
                        @Override
                        public void onMoreMessagesLoaded(final int c, final Conversation conversation) {
                            if (ConversationFragment.this.conversation != conversation) {
                                conversation.messagesLoaded.set(true);
                                return;
                            }
                            runOnUiThread(() -> {
                                synchronized (messageList) {
                                    final int oldPosition = binding.messagesView.getFirstVisiblePosition();
                                    Message message = null;
                                    int childPos;
                                    for (childPos = 0; childPos + oldPosition < messageList.size(); ++childPos) {
                                        message = messageList.get(oldPosition + childPos);
                                        if (message.getType() != Message.TYPE_STATUS) {
                                            break;
                                        }
                                    }
                                    final String uuid = message != null ? message.getUuid() : null;
                                    View v = binding.messagesView.getChildAt(childPos);
                                    final int pxOffset = (v == null) ? 0 : v.getTop();
                                    ConversationFragment.this.conversation.populateWithMessages(ConversationFragment.this.messageList,  activity == null ? null : activity.xmppConnectionService);
                                    try {
                                        updateStatusMessages();
                                    } catch (IllegalStateException e) {
                                        Log.d(Config.LOGTAG, "caught illegal state exception while updating status messages");
                                    }
                                    messageListAdapter.notifyDataSetChanged();
                                    int pos = Math.max(getIndexOf(uuid, messageList), 0);
                                    binding.messagesView.setSelectionFromTop(pos, pxOffset);
                                    if (messageLoaderToast != null) {
                                        messageLoaderToast.cancel();
                                    }
                                    conversation.messagesLoaded.set(true);
                                }
                            });
                        }

                        @Override
                        public void informUser(final int resId) {

                            runOnUiThread(() -> {
                                if (messageLoaderToast != null) {
                                    messageLoaderToast.cancel();
                                }
                                if (ConversationFragment.this.conversation != conversation) {
                                    return;
                                }
                                messageLoaderToast = ToastCompat.makeText(view.getContext(), resId, ToastCompat.LENGTH_LONG);
                                messageLoaderToast.show();
                            });
                        }
                    });
                }
            }
        }
    };
    private final EditMessage.OnCommitContentListener mEditorContentListener = new EditMessage.OnCommitContentListener() {
        @Override
        public boolean onCommitContent(InputContentInfoCompat inputContentInfo, int flags, Bundle opts, String[] contentMimeTypes) {
            // try to get permission to read the image, if applicable
            if ((flags & InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
                try {
                    inputContentInfo.requestPermission();
                } catch (Exception e) {
                    Log.e(Config.LOGTAG, "InputContentInfoCompat#requestPermission() failed.", e);
                    ToastCompat.makeText(getActivity(), activity.getString(R.string.no_permission_to_access_x, inputContentInfo.getDescription()), ToastCompat.LENGTH_LONG
                    ).show();
                    return false;
                }
            }
            if (!Compatibility.runsThirtyThree() && hasPermissions(REQUEST_ADD_EDITOR_CONTENT, Manifest.permission.WRITE_EXTERNAL_STORAGE) && hasPermissions(REQUEST_ADD_EDITOR_CONTENT, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                attachEditorContentToConversation(inputContentInfo.getContentUri());
            } else if (Compatibility.runsThirtyThree() && hasPermissions(REQUEST_ADD_EDITOR_CONTENT, Manifest.permission.READ_MEDIA_IMAGES) && hasPermissions(REQUEST_ADD_EDITOR_CONTENT, Manifest.permission.READ_MEDIA_AUDIO) && hasPermissions(REQUEST_ADD_EDITOR_CONTENT, Manifest.permission.READ_MEDIA_VIDEO)) {
                attachEditorContentToConversation(inputContentInfo.getContentUri());
            } else {
                mPendingEditorContent = inputContentInfo.getContentUri();
            }
            return true;
        }
    };
    private Message selectedMessage;
    private final OnClickListener mEnableAccountListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final Account account = conversation == null ? null : conversation.getAccount();
            if (account != null) {
                account.setOption(Account.OPTION_SOFT_DISABLED, false);
                account.setOption(Account.OPTION_DISABLED, false);
                activity.xmppConnectionService.updateAccount(account);
            }
        }
    };
    private final OnClickListener mUnblockClickListener = new OnClickListener() {
        @Override
        public void onClick(final View v) {
            v.post(() -> v.setVisibility(View.INVISIBLE));
            if (conversation.isDomainBlocked()) {
                BlockContactDialog.show(activity, conversation);
            } else {
                unblockConversation(conversation);
            }
        }
    };
    private final OnClickListener mBlockClickListener = this::showBlockSubmenu;

    private final OnClickListener mAddBackClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            final Contact contact = conversation == null ? null : conversation.getContact();
            if (contact != null) {
                activity.xmppConnectionService.createContact(contact, true);
                activity.switchToContactDetails(contact);
            }
        }
    };

    private final View.OnLongClickListener mLongPressBlockListener = this::showBlockSubmenu;

    private final OnClickListener mHideUnencryptionHint = v -> enableMessageEncryption();

    private void enableMessageEncryption() {
        if (Config.supportOmemo() && Conversation.suitableForOmemoByDefault(conversation) && conversation.isSingleOrPrivateAndNonAnonymous()) {
            conversation.setNextEncryption(Message.ENCRYPTION_AXOLOTL);
            activity.xmppConnectionService.updateConversation(conversation);
            activity.refreshUi();
        }
        hideSnackbar();
    }
    private void disableMessageEncryption() {
        if (conversation.isSingleOrPrivateAndNonAnonymous()) {
            conversation.setNextEncryption(Message.ENCRYPTION_NONE);
            activity.xmppConnectionService.updateConversation(conversation);
            activity.refreshUi();
        }
        hideSnackbar();
    }



    private final OnClickListener mAllowPresenceSubscription = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final Contact contact = conversation == null ? null : conversation.getContact();
            if (contact != null) {
                activity.xmppConnectionService.sendPresencePacket(contact.getAccount(),
                        activity.xmppConnectionService.getPresenceGenerator()
                                .sendPresenceUpdatesTo(contact));
                hideSnackbar();
            }
        }
    };
    private OnClickListener mAnswerSmpClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            Intent intent = new Intent(activity, VerifyOTRActivity.class);
            intent.setAction(VerifyOTRActivity.ACTION_VERIFY_CONTACT);
            intent.putExtra(EXTRA_ACCOUNT, conversation.getAccount().getJid().asBareJid().toString());
            intent.putExtra(VerifyOTRActivity.EXTRA_ACCOUNT, conversation.getAccount().getJid().asBareJid().toString());
            intent.putExtra("mode", VerifyOTRActivity.MODE_ANSWER_QUESTION);
            startActivity(intent);
            activity.overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
        }
    };

    protected OnClickListener clickToDecryptListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            PendingIntent pendingIntent = conversation.getAccount().getPgpDecryptionService().getPendingIntent();
            if (pendingIntent != null) {
                try {
                    getActivity()
                            .startIntentSenderForResult(
                                    pendingIntent.getIntentSender(),
                                    REQUEST_DECRYPT_PGP,
                                    null,
                                    0,
                                    0,
                                    0,
                                    Compatibility.pgpStartIntentSenderOptions());
                } catch (SendIntentException e) {
                    Toast.makeText(
                                    getActivity(),
                                    R.string.unable_to_connect_to_keychain,
                                    Toast.LENGTH_SHORT)
                            .show();
                    conversation
                            .getAccount()
                            .getPgpDecryptionService()
                            .continueDecryption(true);
                }
            }
            updateSnackBar(conversation);
        }
    };
    private final AtomicBoolean mSendingPgpMessage = new AtomicBoolean(false);
    private final OnEditorActionListener mEditorActionListener = (v, actionId, event) -> {
        if (actionId == EditorInfo.IME_ACTION_SEND) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && imm.isFullscreenMode()) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
            sendMessage();
            return true;
        } else {
            return false;
        }
    };
    private final OnClickListener mScrollButtonListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            stopScrolling();
            setSelection(binding.messagesView.getCount() - 1, true);
        }
    };

    private final OnClickListener memojiButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (binding.emojiButton.getVisibility() == VISIBLE && binding.emojisStickerLayout.getHeight() > 100) {
                binding.emojiButton.setVisibility(GONE);
                binding.keyboardButton.setVisibility(VISIBLE);
                hideSoftKeyboard(activity);
                EmojiPickerView emojiPickerView = binding.emojiPicker;
                backPressedLeaveEmojiPicker.setEnabled(true);
                binding.textinput.requestFocus();
                emojiPickerView.setOnEmojiPickedListener(emojiViewItem -> {
                    int start = binding.textinput.getSelectionStart(); //this is to get the the cursor position
                    binding.textinput.getText().insert(start, emojiViewItem.getEmoji()); //this will get the text and insert the emoji into   the current position
                });

                if (binding.emojiPicker.getVisibility() == VISIBLE) {
                    binding.emojisButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                    binding.emojisButton.setTypeface(null, Typeface.BOLD);
                } else {
                    binding.emojisButton.setBackgroundColor(0);
                    binding.emojisButton.setTypeface(null, Typeface.NORMAL);
                }
                if (binding.stickersview.getVisibility() == VISIBLE) {
                    binding.stickersButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                    binding.stickersButton.setTypeface(null, Typeface.BOLD);
                } else {
                    binding.stickersButton.setBackgroundColor(0);
                    binding.stickersButton.setTypeface(null, Typeface.NORMAL);
                }
                if (binding.gifsview.getVisibility() == VISIBLE) {
                    binding.gifsButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                    binding.gifsButton.setTypeface(null, Typeface.BOLD);
                } else {
                    binding.gifsButton.setBackgroundColor(0);
                    binding.gifsButton.setTypeface(null, Typeface.NORMAL);
                }
            } else if (binding.emojiButton.getVisibility() == VISIBLE && binding.emojisStickerLayout.getHeight() < 100) {
                LinearLayout emojipickerview = binding.emojisStickerLayout;
                ViewGroup.LayoutParams params = emojipickerview.getLayoutParams();
                params.height = 800;
                emojipickerview.setLayoutParams(params);
                binding.emojiButton.setVisibility(GONE);
                binding.keyboardButton.setVisibility(VISIBLE);
                hideSoftKeyboard(activity);
                EmojiPickerView emojiPickerView = binding.emojiPicker;
                backPressedLeaveEmojiPicker.setEnabled(true);
                binding.textinput.requestFocus();
                emojiPickerView.setOnEmojiPickedListener(emojiViewItem -> {
                    int start = binding.textinput.getSelectionStart(); //this is to get the the cursor position
                    binding.textinput.getText().insert(start, emojiViewItem.getEmoji()); //this will get the text and insert the emoji into   the current position
                });

                if (binding.emojiPicker.getVisibility() == VISIBLE) {
                    binding.emojisButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                    binding.emojisButton.setTypeface(null, Typeface.BOLD);
                } else {
                    binding.emojisButton.setBackgroundColor(0);
                    binding.emojisButton.setTypeface(null, Typeface.NORMAL);
                }
                if (binding.stickersview.getVisibility() == VISIBLE) {
                    binding.stickersButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                    binding.stickersButton.setTypeface(null, Typeface.BOLD);
                } else {
                    binding.stickersButton.setBackgroundColor(0);
                    binding.stickersButton.setTypeface(null, Typeface.NORMAL);
                }
                if (binding.gifsview.getVisibility() == VISIBLE) {
                    binding.gifsButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                    binding.gifsButton.setTypeface(null, Typeface.BOLD);
                } else {
                    binding.gifsButton.setBackgroundColor(0);
                    binding.gifsButton.setTypeface(null, Typeface.NORMAL);
                }
            }
        }
    };

    private final OnClickListener memojisButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            binding.emojiPicker.setVisibility(VISIBLE);
            binding.stickersview.setVisibility(GONE);
            binding.gifsview.setVisibility(GONE);
            EmojiPickerView emojiPickerView = binding.emojiPicker;
            backPressedLeaveEmojiPicker.setEnabled(true);
            binding.textinput.requestFocus();
            emojiPickerView.setOnEmojiPickedListener(emojiViewItem -> {
                int start = binding.textinput.getSelectionStart(); //this is to get the the cursor position
                binding.textinput.getText().insert(start, emojiViewItem.getEmoji()); //this will get the text and insert the emoji into   the current position
            });

            if (binding.emojiPicker.getVisibility() == VISIBLE) {
                binding.emojisButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                binding.emojisButton.setTypeface(null, Typeface.BOLD);
            } else {
                binding.emojisButton.setBackgroundColor(0);
                binding.emojisButton.setTypeface(null, Typeface.NORMAL);
            }
            if (binding.stickersview.getVisibility() == VISIBLE) {
                binding.stickersButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                binding.stickersButton.setTypeface(null, Typeface.BOLD);
            } else {
                binding.stickersButton.setBackgroundColor(0);
                binding.stickersButton.setTypeface(null, Typeface.NORMAL);
            }
            if (binding.gifsview.getVisibility() == VISIBLE) {
                binding.gifsButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                binding.gifsButton.setTypeface(null, Typeface.BOLD);
            } else {
                binding.gifsButton.setBackgroundColor(0);
                binding.gifsButton.setTypeface(null, Typeface.NORMAL);
            }
        }
    };

    private final OnClickListener mstickersButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            binding.emojiPicker.setVisibility(GONE);
            binding.stickersview.setVisibility(VISIBLE);
            binding.gifsview.setVisibility(GONE);
            backPressedLeaveEmojiPicker.setEnabled(true);
            binding.textinput.requestFocus();
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isEmpty(dirStickers.toPath())) {
                    Toast.makeText(activity, R.string.update_default_stickers, Toast.LENGTH_LONG).show();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (binding.emojiPicker.getVisibility() == VISIBLE) {
                binding.emojisButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                binding.emojisButton.setTypeface(null, Typeface.BOLD);
            } else {
                binding.emojisButton.setBackgroundColor(0);
                binding.emojisButton.setTypeface(null, Typeface.NORMAL);
            }
            if (binding.stickersview.getVisibility() == VISIBLE) {
                binding.stickersButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                binding.stickersButton.setTypeface(null, Typeface.BOLD);
            } else {
                binding.stickersButton.setBackgroundColor(0);
                binding.stickersButton.setTypeface(null, Typeface.NORMAL);
            }
            if (binding.gifsview.getVisibility() == VISIBLE) {
                binding.gifsButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                binding.gifsButton.setTypeface(null, Typeface.BOLD);
            } else {
                binding.gifsButton.setBackgroundColor(0);
                binding.gifsButton.setTypeface(null, Typeface.NORMAL);
            }
        }
    };

    public boolean isEmpty(Path path) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (Files.isDirectory(path)) {
                try (Stream<Path> entries = Files.list(path)) {
                    return !entries.findFirst().isPresent();
                }
            }
        }
        return false;
    }

    private final OnClickListener mgifsButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            binding.emojiPicker.setVisibility(GONE);
            binding.stickersview.setVisibility(GONE);
            binding.gifsview.setVisibility(VISIBLE);
            backPressedLeaveEmojiPicker.setEnabled(true);
            binding.textinput.requestFocus();
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isEmpty(dirGifs.toPath())) {
                    Toast.makeText(activity, R.string.copy_GIFs_to_GIFs_folder, Toast.LENGTH_LONG).show();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (binding.emojiPicker.getVisibility() == VISIBLE) {
                binding.emojisButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                binding.emojisButton.setTypeface(null, Typeface.BOLD);
            } else {
                binding.emojisButton.setBackgroundColor(0);
                binding.emojisButton.setTypeface(null, Typeface.NORMAL);
            }
            if (binding.stickersview.getVisibility() == VISIBLE) {
                binding.stickersButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                binding.stickersButton.setTypeface(null, Typeface.BOLD);
            } else {
                binding.stickersButton.setBackgroundColor(0);
                binding.stickersButton.setTypeface(null, Typeface.NORMAL);
            }
            if (binding.gifsview.getVisibility() == VISIBLE) {
                binding.gifsButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                binding.gifsButton.setTypeface(null, Typeface.BOLD);
            } else {
                binding.gifsButton.setBackgroundColor(0);
                binding.gifsButton.setTypeface(null, Typeface.NORMAL);
            }
        }
    };

    private final OnClickListener mkeyboardButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (binding.keyboardButton.getVisibility() == VISIBLE) {
                binding.keyboardButton.setVisibility(GONE);
                binding.emojiButton.setVisibility(VISIBLE);
                InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (inputMethodManager != null) {
                    binding.textinput.requestFocus();
                    inputMethodManager.showSoftInput(binding.textinput, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }
    };

    private final OnClickListener mTimerClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (recording && binding.recordingVoiceActivity.getVisibility() == VISIBLE) {
                pauseRecording();
            } else if (!recording && binding.recordingVoiceActivity.getVisibility() == VISIBLE) {
                resumeRecording();
            }
        }
    };


    private final OnClickListener mRecordVoiceButtonListener = v -> attachFile(ATTACHMENT_CHOICE_RECORD_VOICE);

    private final OnClickListener mtakePictureButtonListener = v -> attachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);

    private final OnClickListener mSendButtonListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            ConversationMenuConfigurator.reloadFeatures(conversation, activity);
            Object tag = v.getTag();
            if (tag instanceof SendButtonAction) {
                SendButtonAction action = (SendButtonAction) tag;
                if (action == SendButtonAction.CHOOSE_ATTACHMENT) {
                    choose_attachment(v);


                    attachFile(action.toChoice());
                } else if (action == SendButtonAction.TAKE_PHOTO || action == SendButtonAction.RECORD_VIDEO || action == SendButtonAction.SEND_LOCATION || action == SendButtonAction.RECORD_VOICE || action == SendButtonAction.CHOOSE_PICTURE) {
                    attachFile(action.toChoice());
                } else if (action == SendButtonAction.CANCEL) {
                    if (conversation != null) {
                        conversation.setUserSelectedThread(false);
                        if (conversation.setCorrectingMessage(null)) {
                            binding.textinput.setText("");
                            binding.textinput.append(conversation.getDraftMessage());
                            conversation.setDraftMessage(null);
                        } else if (conversation.getMode() == Conversation.MODE_MULTI) {
                            conversation.setNextCounterpart(null);
                            binding.textinput.setText("");
                        } else {
                            binding.textinput.setText("");
                        }
                        binding.textinputSubject.setText("");
                        binding.textinputSubject.setVisibility(GONE);
                        updateChatMsgHint();
                        updateSendButton();
                        updateEditablity();
                    }
                } else {
                    sendMessage();
                }
            } else {
                sendMessage();
            }
        }
    };

    private final OnClickListener mCancelVoiceRecord = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mHandler.removeCallbacks(mTickExecutor);
            stopRecording(false);
            activity.setResult(RESULT_CANCELED);
            //activity.finish();
            binding.recordingVoiceActivity.setVisibility(View.GONE);
        }
    };

    private final OnClickListener mShareVoiceRecord = new OnClickListener() {
        @Override
        public void onClick(View v) {
            binding.shareButton.setEnabled(false);        // TODO: Activate again
            //binding.shareButton.setText(R.string.please_wait);
            mHandler.removeCallbacks(mTickExecutor);
            mHandler.postDelayed(() -> stopRecording(true), 100);
        }
    };

    private View.OnLongClickListener mSendButtonLongListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            final String body = binding.textinput.getText().toString();
            if (body.isEmpty()) {
                binding.textinput.getText().insert(0, Message.ME_COMMAND + " ");
            } else if (!mediaPreviewAdapter.hasAttachments() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                scheduleMessage();
            }
            return true;
        }
    };
    private final OnBackPressedCallback backPressedLeaveSingleThread = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            conversation.setLockThread(false);
            this.setEnabled(false);
            conversation.setUserSelectedThread(false);
            refresh();
            updateThreadFromLastMessage();
        }
    };


    private final OnBackPressedCallback backPressedLeaveEmojiPicker = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            if (binding.emojisStickerLayout.getHeight() > 100) {
                LinearLayout emojipickerview = binding.emojisStickerLayout;
                ViewGroup.LayoutParams params = emojipickerview.getLayoutParams();
                params.height = 0;
                emojipickerview.setLayoutParams(params);
                binding.keyboardButton.setVisibility(GONE);
                binding.emojiButton.setVisibility(VISIBLE);
            }
            this.setEnabled(false);
            refresh();
        }
    };

    private final OnBackPressedCallback backPressedLeaveVoiceRecorder = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            if (binding.recordingVoiceActivity.getVisibility()==VISIBLE){
                mHandler.removeCallbacks(mTickExecutor);
                stopRecording(false);
                activity.setResult(RESULT_CANCELED);
                //activity.finish();
                binding.recordingVoiceActivity.setVisibility(View.GONE);
            }
            this.setEnabled(false);
            refresh();
        }
    };


    private int completionIndex = 0;
    private int lastCompletionLength = 0;
    private String incomplete;
    private int lastCompletionCursor;
    private boolean firstWord = false;
    private Message mPendingDownloadableMessage;

    private static ConversationFragment findConversationFragment(Activity activity) {
        Fragment fragment = activity.getFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment instanceof ConversationFragment) {
            return (ConversationFragment) fragment;
        }
        fragment = activity.getFragmentManager().findFragmentById(R.id.secondary_fragment);
        if (fragment instanceof ConversationFragment) {
            return (ConversationFragment) fragment;
        }
        return null;
    }

    public static void startStopPending(Activity activity) {
        ConversationFragment fragment = findConversationFragment(activity);
        if (fragment != null) {
            fragment.messageListAdapter.startStopPending();
        }
    }

    public static void downloadFile(Activity activity, Message message) {
        ConversationFragment fragment = findConversationFragment(activity);
        if (fragment != null) {
            fragment.startDownloadable(message);
        }
    }

    public static void registerPendingMessage(Activity activity, Message message) {
        ConversationFragment fragment = findConversationFragment(activity);
        if (fragment != null) {
            fragment.pendingMessage.push(message);
        }
    }

    public static void openPendingMessage(Activity activity) {
        ConversationFragment fragment = findConversationFragment(activity);
        if (fragment != null) {
            Message message = fragment.pendingMessage.pop();
            if (message != null) {
                fragment.messageListAdapter.openDownloadable(message);
            }
        }
    }

    public static Conversation getConversation(Activity activity) {
        return getConversation(activity, R.id.secondary_fragment);
    }

    private static Conversation getConversation(Activity activity, @IdRes int res) {
        final Fragment fragment = activity.getFragmentManager().findFragmentById(res);
        if (fragment instanceof ConversationFragment) {
            return ((ConversationFragment) fragment).getConversation();
        } else {
            return null;
        }
    }

    public static ConversationFragment get(Activity activity) {
        FragmentManager fragmentManager = activity.getFragmentManager();
        Fragment fragment = fragmentManager.findFragmentById(R.id.main_fragment);
        if (fragment instanceof ConversationFragment) {
            return (ConversationFragment) fragment;
        } else {
            fragment = fragmentManager.findFragmentById(R.id.secondary_fragment);
            return fragment instanceof ConversationFragment ? (ConversationFragment) fragment : null;
        }
    }

    public static Conversation getConversationReliable(Activity activity) {
        final Conversation conversation = getConversation(activity, R.id.secondary_fragment);
        if (conversation != null) {
            return conversation;
        }
        return getConversation(activity, R.id.main_fragment);
    }

    private static boolean scrolledToBottom(AbsListView listView) {
        final int count = listView.getCount();
        if (count == 0) {
            return true;
        } else if (listView.getLastVisiblePosition() == count - 1) {
            final View lastChild = listView.getChildAt(listView.getChildCount() - 1);
            return lastChild != null && lastChild.getBottom() <= listView.getHeight();
        } else {
            return false;
        }
    }

    @SuppressLint("RestrictedApi")
    private void choose_attachment(View v) {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
        final boolean hideVoiceAndTakePicture = p.getBoolean("show_record_voice_btn", activity.getResources().getBoolean(R.bool.show_record_voice_btn));
        PopupMenu popup = new PopupMenu(activity, v);
        popup.inflate(R.menu.choose_attachment);
        final Menu menu = popup.getMenu();
        ConversationMenuConfigurator.configureQuickShareAttachmentMenu(conversation, menu, hideVoiceAndTakePicture);
        popup.setOnMenuItemClickListener(attachmentItem -> {
            int itemId = attachmentItem.getItemId();
            if (itemId == R.id.attach_choose_picture || itemId == R.id.attach_choose_video || itemId == R.id.attach_take_picture || itemId == R.id.attach_record_video || itemId == R.id.attach_choose_file || itemId == R.id.attach_record_voice || itemId == R.id.attach_subject || itemId == R.id.attach_webxdc || itemId == R.id.attach_location) {
                handleAttachmentSelection(attachmentItem);
            }
            return false;
        });
        MenuPopupHelper menuHelper = new MenuPopupHelper(getActivity(), (MenuBuilder) menu, v);
        menuHelper.setForceShowIcon(true);
        menuHelper.show();
    }

    private void toggleScrollDownButton() {
        toggleScrollDownButton(binding.messagesView);
    }

    private void toggleScrollDownButton(AbsListView listView) {
        if (conversation == null) {
            return;
        }
        if (scrolledToBottom(listView)) {
            lastMessageUuid = null;
            hideUnreadMessagesCount();
        } else {
            binding.scrollToBottomButton.setEnabled(true);
            binding.scrollToBottomButton.show();
            if (lastMessageUuid == null) {
                lastMessageUuid = conversation.getLatestMessage().getUuid();
            }
            if (conversation.getReceivedMessagesCountSinceUuid(lastMessageUuid) > 0) {
                binding.unreadCountCustomView.setVisibility(View.VISIBLE);
            }
        }
    }

    private int getIndexOf(String uuid, List<Message> messages) {
        if (uuid == null) {
            return messages.size() - 1;
        }
        for (int i = 0; i < messages.size(); ++i) {
            if (uuid.equals(messages.get(i).getUuid())) {
                return i;
            } else {
                Message next = messages.get(i);
                while (next != null && next.wasMergedIntoPrevious(activity.xmppConnectionService)) {
                    if (uuid.equals(next.getUuid())) {
                        return i;
                    }
                    next = next.next();
                }

            }
        }
        return -1;
    }

    private ScrollState getScrollPosition() {
        final ListView listView = this.binding == null ? null : this.binding.messagesView;
        if (listView == null || listView.getCount() == 0 || listView.getLastVisiblePosition() == listView.getCount() - 1) {
            return null;
        } else {
            final int pos = listView.getFirstVisiblePosition();
            final View view = listView.getChildAt(0);
            if (view == null) {
                return null;
            } else {
                return new ScrollState(pos, view.getTop());
            }
        }
    }

    private void setScrollPosition(ScrollState scrollPosition, String lastMessageUuid) {
        if (scrollPosition != null) {
            this.lastMessageUuid = lastMessageUuid;
            if (lastMessageUuid != null) {
                binding.unreadCountCustomView.setUnreadCount(conversation.getReceivedMessagesCountSinceUuid(lastMessageUuid));
            }
            //TODO maybe this needs a 'post'
            this.binding.messagesView.setSelectionFromTop(scrollPosition.position, scrollPosition.offset);
            toggleScrollDownButton();
        }
    }

    private void attachLocationToConversation(Conversation conversation, Uri uri) {
        if (conversation == null) {
            return;
        }
        final String subject = binding.textinputSubject.getText().toString();
        activity.xmppConnectionService.attachLocationToConversation(conversation, uri, subject, new UiCallback<Message>() {

            @Override
            public void success(Message message) {
                messageSent();
            }

            @Override
            public void error(int errorCode, Message object) {
                //TODO show possible pgp error
            }

            @Override
            public void userInputRequired(PendingIntent pi, Message object) {

            }

            @Override
            public void progress(int progress) {

            }

            @Override
            public void showToast() {

            }
        });
    }

    private void attachFileToConversation(Conversation conversation, Uri uri, String type) {
        if (conversation == null) {
            return;
        }
        if (Objects.equals(type, "application/webxdc+zip")) newSubThread();
        final String subject = binding.textinputSubject.getText().toString();
        final Toast prepareFileToast = ToastCompat.makeText(getActivity(), getText(R.string.preparing_file), ToastCompat.LENGTH_SHORT);
        activity.delegateUriPermissionsToService(uri);
        activity.xmppConnectionService.attachFileToConversation(conversation, uri, type, subject, new UiInformableCallback<Message>() {
            @Override
            public void inform(final String text) {
                hidePrepareFileToast(prepareFileToast);
                runOnUiThread(() -> activity.replaceToast(text));
            }

            @Override
            public void success(Message message) {
                runOnUiThread(() -> {
                    activity.hideToast();
                    messageSent();
                });
                hidePrepareFileToast(prepareFileToast);
            }

            @Override
            public void error(final int errorCode, Message message) {
                hidePrepareFileToast(prepareFileToast);
                runOnUiThread(() -> activity.replaceToast(getString(errorCode)));

            }

            @Override
            public void userInputRequired(PendingIntent pi, Message message) {
                hidePrepareFileToast(prepareFileToast);
            }

            @Override
            public void progress(int progress) {
                hidePrepareFileToast(prepareFileToast);
                updateSnackBar(conversation);
            }

            @Override
            public void showToast() {
                prepareFileToast.show();
            }
        });
    }

    public void attachEditorContentToConversation(Uri uri) {
        mediaPreviewAdapter.addMediaPreviews(Attachment.of(getActivity(), uri, Attachment.Type.FILE));
        toggleInputMethod();
    }

    private void attachImageToConversation(Conversation conversation, Uri uri, String type) {
        if (conversation == null) {
            return;
        }
        final String subject = binding.textinputSubject.getText().toString();
        final Toast prepareFileToast = ToastCompat.makeText(getActivity(), getText(R.string.preparing_image), ToastCompat.LENGTH_SHORT);

        activity.delegateUriPermissionsToService(uri);
        activity.xmppConnectionService.attachImageToConversation(conversation, uri, type, subject,
                new UiCallback<Message>() {
                    @Override
                    public void userInputRequired(PendingIntent pi, Message object) {
                        hidePrepareFileToast(prepareFileToast);
                    }

                    @Override
                    public void progress(int progress) {
                    }

                    @Override
                    public void showToast() {
                        prepareFileToast.show();
                    }

                    @Override
                    public void success(Message message) {
                        //hidePrepareFileToast(prepareFileToast);
                        prepareFileToast.cancel();
                        runOnUiThread(() -> messageSent());
                    }

                    @Override
                    public void error(final int error, final Message message) {
                        hidePrepareFileToast(prepareFileToast);
                        final ConversationsActivity activity = ConversationFragment.this.activity;
                        if (activity == null) {
                            return;
                        }
                        activity.runOnUiThread(() -> activity.replaceToast(getString(error)));
                    }
                });
    }

    private void hidePrepareFileToast(final Toast prepareFileToast) {
        if (prepareFileToast != null && activity != null) {
            activity.runOnUiThread(prepareFileToast::cancel);
        }
    }

    private void sendMessage() {
        sendMessage((Long) null);
    }

    private void sendMessage(Long sendAt) {
        if (sendAt != null && sendAt < System.currentTimeMillis()) sendAt = null; // No sending in past plz
        if (mediaPreviewAdapter.hasAttachments()) {
            commitAttachments();
            return;
        }
        Editable body = this.binding.textinput.getText();
        if (body == null) body = new SpannableStringBuilder("");
        final Conversation conversation = this.conversation;
        final boolean hasSubject = binding.textinputSubject.getText().length() > 0;
        if (conversation == null || (body.length() == 0 && (conversation.getThread() == null || !hasSubject))) {
            return;
        }
        if (trustKeysIfNeeded(conversation, REQUEST_TRUST_KEYS_TEXT)) {
            return;
        }
        final Message message;
        if (conversation.getCorrectingMessage() == null) {
            boolean attention = false;
            if (Pattern.compile("\\A@here\\s.*").matcher(body).find()) {
                attention = true;
                body.delete(0, 6);
                while (body.length() > 0 && Character.isWhitespace(body.charAt(0))) body.delete(0, 1);
            }
            if (conversation.getReplyTo() != null) {
                if (Emoticons.isEmoji(body.toString().replaceAll("\\s", ""))) {
                    message = conversation.getReplyTo().react(body.toString().replaceAll("\\s", ""));
                } else {
                    message = conversation.getReplyTo().reply();
                    message.appendBody(body);
                }
                message.setEncryption(conversation.getNextEncryption());
            } else {
                message = new Message(conversation, body.toString(), conversation.getNextEncryption());
                message.setBody(hasSubject && body.length() == 0 ? null : body);
                if (message.bodyIsOnlyEmojis()) {
                    SpannableStringBuilder spannable = message.getSpannableBody(null, null);
                    ImageSpan[] imageSpans = spannable.getSpans(0, spannable.length(), ImageSpan.class);
                    if (imageSpans.length == 1) {
                        // Only one inline image, so it's a sticker
                        String source = imageSpans[0].getSource();
                        if (source != null && source.length() > 0 && source.substring(0, 4).equals("cid:")) {
                            try {
                                final Cid cid = BobTransfer.cid(Uri.parse(source));
                                final String url = activity.xmppConnectionService.getUrlForCid(cid);
                                final File f = activity.xmppConnectionService.getFileForCid(cid);
                                if (url != null) {
                                    message.setBody("");
                                    message.setRelativeFilePath(f.getAbsolutePath());
                                    activity.xmppConnectionService.getFileBackend().updateFileParams(message);
                                }
                            } catch (final Exception e) { }
                        }
                    }
                }
            }
            if (hasSubject) message.setSubject(binding.textinputSubject.getText().toString());
            message.setThread(conversation.getThread());
            if (attention) {
                message.addPayload(new Element("attention", "urn:xmpp:attention:0"));
            }
            Message.configurePrivateMessage(message);
        } else {
            message = conversation.getCorrectingMessage();
            if (hasSubject) message.setSubject(binding.textinputSubject.getText().toString());
            message.setThread(conversation.getThread());
            if (conversation.getReplyTo() != null) {
                if (Emoticons.isEmoji(body.toString().replaceAll("\\s", ""))) {
                    message.updateReaction(conversation.getReplyTo(), body.toString().replaceAll("\\s", ""));
                } else {
                    message.updateReplyTo(conversation.getReplyTo(), body);
                }
            } else {
                message.clearReplyReact();
                message.setBody(hasSubject && body.length() == 0 ? null : body);
            }
            message.putEdited(message.getUuid(), message.getServerMsgId(), message.getBody(), message.getTimeSent());
            message.setServerMsgId(null);
            message.setUuid(UUID.randomUUID().toString());
        }
        if (sendAt != null) message.setTime(sendAt);
        int nextEncryption = conversation.getNextEncryption();
        if (nextEncryption == Message.ENCRYPTION_OTR) {
            sendOtrMessage(message);
        } else if (nextEncryption == Message.ENCRYPTION_PGP) {
            sendPgpMessage(message);
        } else {
            sendMessage(message);
        }
        setupReply(null);
    }

    private boolean trustKeysIfNeeded(final Conversation conversation, final int requestCode) {
        return conversation.getNextEncryption() == Message.ENCRYPTION_AXOLOTL && trustKeysIfNeeded(requestCode);
    }

    protected boolean trustKeysIfNeeded(int requestCode) {
        AxolotlService axolotlService = conversation.getAccount().getAxolotlService();
        final List<Jid> targets = axolotlService.getCryptoTargets(conversation);
        boolean hasUnaccepted = !conversation.getAcceptedCryptoTargets().containsAll(targets);
        boolean hasUndecidedOwn = !axolotlService.getKeysWithTrust(FingerprintStatus.createActiveUndecided()).isEmpty();
        boolean hasUndecidedContacts = !axolotlService.getKeysWithTrust(FingerprintStatus.createActiveUndecided(), targets).isEmpty();
        boolean hasPendingKeys = !axolotlService.findDevicesWithoutSession(conversation).isEmpty();
        boolean hasNoTrustedKeys = axolotlService.anyTargetHasNoTrustedKeys(targets);
        boolean downloadInProgress = axolotlService.hasPendingKeyFetches(targets);
        if (hasUndecidedOwn || hasUndecidedContacts || hasPendingKeys || hasNoTrustedKeys || hasUnaccepted || downloadInProgress) {
            axolotlService.createSessionsIfNeeded(conversation);
            Intent intent = new Intent(getActivity(), TrustKeysActivity.class);
            String[] contacts = new String[targets.size()];
            for (int i = 0; i < contacts.length; ++i) {
                contacts[i] = targets.get(i).toString();
            }
            intent.putExtra("contacts", contacts);
            intent.putExtra(EXTRA_ACCOUNT, conversation.getAccount().getJid().asBareJid().toEscapedString());
            intent.putExtra("conversation", conversation.getUuid());
            startActivityForResult(intent, requestCode);
            return true;
        } else {
            return false;
        }
    }

    public void updateChatMsgHint() {
        final boolean multi = conversation.getMode() == Conversation.MODE_MULTI;
        if (conversation.getCorrectingMessage() != null) {
            this.binding.textInputHint.setVisibility(View.VISIBLE);
            this.binding.textInputHint.setText(R.string.send_corrected_message);
            this.binding.textinput.setHint(R.string.send_corrected_message);
            binding.conversationViewPager.setCurrentItem(0);
        } else if (isPrivateMessage()) {
            this.binding.textinput.setHint(R.string.send_unencrypted_message);
            this.binding.textInputHint.setVisibility(View.VISIBLE);
            final MucOptions.User user = conversation.getMucOptions().findUserByName(conversation.getNextCounterpart().getResource());
            String nick = user == null ? null : user.getNick();
            if (nick == null) nick = conversation.getNextCounterpart().getResource();
            SpannableStringBuilder hint = new SpannableStringBuilder(getString(R.string.send_private_message_to, nick));
            hint.setSpan(new StyleSpan(Typeface.BOLD), 0, hint.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            this.binding.textInputHint.setText(hint);
            binding.conversationViewPager.setCurrentItem(0);
        } else if (multi && !conversation.getMucOptions().participating()) {
            this.binding.textInputHint.setVisibility(View.VISIBLE);
            this.binding.textInputHint.setText(R.string.ask_for_writeaccess);
            this.binding.textinput.setHint(R.string.you_are_not_participating);
        } else {
            this.binding.textInputHint.setVisibility(GONE);
            if (getActivity() != null) {
                this.binding.textinput.setHint(UIHelper.getMessageHint(getActivity(), conversation));
                getActivity().invalidateOptionsMenu();
            }
        }
        binding.messagesView.post(this::updateThreadFromLastMessage);
    }

    private boolean isPrivateMessage() {
        return conversation != null && conversation.getMode() == Conversation.MODE_MULTI && conversation.getNextCounterpart() != null;
    }

    public void setupIme() {
        this.binding.textinput.refreshIme();
    }

    private void handleActivityResult(ActivityResult activityResult) {
        if (activityResult.resultCode == Activity.RESULT_OK) {
            handlePositiveActivityResult(activityResult.requestCode, activityResult.data);
        } else {
            handleNegativeActivityResult(activityResult.requestCode);
        }
    }

    private void handlePositiveActivityResult(int requestCode, final Intent data) {
        if (requestCode == REQUEST_WEBXDC_STORE) {
            mediaPreviewAdapter.addMediaPreviews(Attachment.of(activity, data.getData(), Attachment.Type.FILE));
            toggleInputMethod();
        } else if (requestCode == REQUEST_SAVE_STICKER) {
            final DocumentFile df = DocumentFile.fromSingleUri(activity, data.getData());
            final File f = savingAsSticker;
            savingAsSticker = null;
            try {
                activity.xmppConnectionService.getFileBackend().copyFileToDocumentFile(activity, f, df);
                Toast.makeText(activity, R.string.sticker_saved, Toast.LENGTH_SHORT).show();
            } catch (final FileBackend.FileCopyException e) {
                Toast.makeText(activity, e.getResId(), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_SAVE_GIF) {
            final DocumentFile df = DocumentFile.fromSingleUri(activity, data.getData());
            final File f = savingAsGif;
            savingAsGif = null;
            try {
                activity.xmppConnectionService.getFileBackend().copyFileToDocumentFile(activity, f, df);
                Toast.makeText(activity, R.string.gif_saved, Toast.LENGTH_SHORT).show();
            } catch (final FileBackend.FileCopyException e) {
                Toast.makeText(activity, e.getResId(), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_TRUST_KEYS_TEXT) {
            sendMessage();
        } else if (requestCode == REQUEST_TRUST_KEYS_ATTACHMENTS) {
            commitAttachments();
        } else if (requestCode == REQUEST_START_AUDIO_CALL) {
            triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VOICE_CALL);
        } else if (requestCode == REQUEST_START_VIDEO_CALL) {
            triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VIDEO_CALL);
        } else if (requestCode == ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
            final List<Attachment> imageUris = Attachment.extractAttachments(getActivity(), data, Attachment.Type.IMAGE);

            mediaPreviewAdapter.addMediaPreviews(imageUris);
            toggleInputMethod();
        } else if (requestCode == ATTACHMENT_CHOICE_TAKE_PHOTO) {
            final Uri takePhotoUri = pendingTakePhotoUri.pop();
            if (takePhotoUri != null) {
                mediaPreviewAdapter.addMediaPreviews(Attachment.of(getActivity(), takePhotoUri, Attachment.Type.IMAGE));
                activity.xmppConnectionService.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, takePhotoUri));
                toggleInputMethod();
            } else {
                Log.d(Config.LOGTAG, "lost take photo uri. unable to to attach");
            }
        } else if (requestCode == ATTACHMENT_CHOICE_CHOOSE_FILE || requestCode == ATTACHMENT_CHOICE_RECORD_VIDEO || requestCode == ATTACHMENT_CHOICE_CHOOSE_VIDEO || requestCode == ATTACHMENT_CHOICE_RECORD_VOICE) {
            final Attachment.Type type = requestCode == ATTACHMENT_CHOICE_RECORD_VOICE ? Attachment.Type.RECORDING : Attachment.Type.FILE;
            final List<Attachment> fileUris = Attachment.extractAttachments(getActivity(), data, type);
            mediaPreviewAdapter.addMediaPreviews(fileUris);
            toggleInputMethod();
        } else if (requestCode == ATTACHMENT_CHOICE_LOCATION) {
            final double latitude = data.getDoubleExtra("latitude", 0);
            final double longitude = data.getDoubleExtra("longitude", 0);
            final int accuracy = data.getIntExtra("accuracy", 0);
            final Uri geo;
            if (accuracy > 0) {
                geo = Uri.parse(String.format("geo:%s,%s;u=%s", latitude, longitude, accuracy));
            } else {
                geo = Uri.parse(String.format("geo:%s,%s", latitude, longitude));
            }
            mediaPreviewAdapter.addMediaPreviews(Attachment.of(getActivity(), geo, Attachment.Type.LOCATION));
            toggleInputMethod();
        } else if (requestCode == REQUEST_INVITE_TO_CONVERSATION) {
            XmppActivity.ConferenceInvite invite = XmppActivity.ConferenceInvite.parse(data);
            if (invite != null && activity != null) {
                if (invite.execute(activity)) {
                    activity.mToast = ToastCompat.makeText(activity, R.string.creating_conference, ToastCompat.LENGTH_LONG);
                    activity.mToast.show();
                }
            }
        }
    }

    private void commitAttachments() {
        final List<Attachment> attachments = mediaPreviewAdapter.getAttachments();
        if (!Compatibility.runsThirtyThree()){
            if (anyNeedsExternalStoragePermission(attachments) && !hasPermissions(REQUEST_COMMIT_ATTACHMENTS, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                return;
            }
        } else if (!hasPermissions(REQUEST_COMMIT_ATTACHMENTS, Manifest.permission.READ_MEDIA_IMAGES)
                && !hasPermissions(REQUEST_COMMIT_ATTACHMENTS, Manifest.permission.READ_MEDIA_VIDEO)
                && !hasPermissions(REQUEST_COMMIT_ATTACHMENTS, Manifest.permission.READ_MEDIA_AUDIO)) {
            return;
        }
        if (trustKeysIfNeeded(conversation, REQUEST_TRUST_KEYS_ATTACHMENTS)) {
            return;
        }
        final PresenceSelector.OnPresenceSelected callback = () -> {
            for (Iterator<Attachment> i = attachments.iterator(); i.hasNext(); i.remove()) {
                final Attachment attachment = i.next();
                if (attachment.getType() == Attachment.Type.LOCATION) {
                    attachLocationToConversation(conversation, attachment.getUri());
                } else if (attachment.getType() == Attachment.Type.IMAGE) {
                    Log.d(Config.LOGTAG, "ConversationsActivity.commitAttachments() - attaching image to conversations. CHOOSE_IMAGE");
                    attachImageToConversation(conversation, attachment.getUri(), attachment.getMime());
                } else if (attachment.getMime() != null && attachment.getMime().equals("application/webxdc+zip")) {
                    Log.d(Config.LOGTAG, "ConversationsActivity.commitAttachments() - attaching WebXDC to conversations. CHOOSE_FILE");
                    newSubThread();
                    if (conversation.getNextEncryption() == Message.ENCRYPTION_AXOLOTL || conversation.getNextEncryption() == Message.ENCRYPTION_PGP || conversation.getNextEncryption() == Message.ENCRYPTION_OTR) {
                        // Show warning to use WebXDC unencrypted
                        if (activity.xmppConnectionService != null && !activity.xmppConnectionService.getBooleanPreference("hide_webxdc_store_hint", R.bool.hide_webxdc_store_hint)) {
                            final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                            builder.setTitle(R.string.webxdc_store_hint_title);
                            builder.setMessage(Html.fromHtml(getString(R.string.webxdc_store_hint_summary)));
                            builder.setNegativeButton(R.string.send_unencrypted, (dialog, which) -> disableMessageEncryption());
                            builder.setPositiveButton(R.string.send_encrypted, (dialog, which) -> enableMessageEncryption());
                            builder.setNeutralButton(R.string.hide_warning, (dialog, which) -> HideWarning());
                            builder.setOnDismissListener(dialog -> attachFileToConversation(conversation, attachment.getUri(), attachment.getMime()));
                            final AlertDialog dialog = builder.create();
                            dialog.setOnShowListener(d -> {
                                final TextView textView = dialog.findViewById(android.R.id.message);
                                if (textView == null) {
                                    return;
                                }
                                textView.setMovementMethod(LinkMovementMethod.getInstance());
                            });
                            dialog.setCanceledOnTouchOutside(false);
                            dialog.show();
                        }
                    } else {
                        attachFileToConversation(conversation, attachment.getUri(), attachment.getMime());
                    }
                } else {
                    Log.d(Config.LOGTAG, "ConversationsActivity.commitAttachments() - attaching file to conversations. CHOOSE_FILE/RECORD_VOICE/RECORD_VIDEO");
                    attachFileToConversation(conversation, attachment.getUri(), attachment.getMime());
                }
            }
            mediaPreviewAdapter.notifyDataSetChanged();
            toggleInputMethod();
        };
        if (conversation == null
                || conversation.getMode() == Conversation.MODE_MULTI
                || Attachment.canBeSendInband(attachments)
                || (conversation.getAccount().httpUploadAvailable() && FileBackend.allFilesUnderSize(getActivity(), attachments, getMaxHttpUploadSize(conversation)))) {
            callback.onPresenceSelected();
        } else {
            activity.selectPresence(conversation, callback);
        }
        setupReply(null);
    }

    private void HideWarning() {
        SharedPreferences preferences = activity.xmppConnectionService.getPreferences();
        preferences.edit().putBoolean(HIDE_WEBXDC_STORE_HINT, true).apply();
    }

    private static boolean anyNeedsExternalStoragePermission(final Collection<Attachment> attachments) {
        for (final Attachment attachment : attachments) {
            if (attachment.getType() != Attachment.Type.LOCATION) {
                return true;
            }
        }
        return false;
    }

    public void toggleInputMethod() {
        boolean hasAttachments = mediaPreviewAdapter.hasAttachments();
        binding.textinput.setVisibility(hasAttachments ? GONE : View.VISIBLE);
        binding.mediaPreview.setVisibility(hasAttachments ? View.VISIBLE : GONE);
        if (mOptionsMenu != null) {
            ConversationMenuConfigurator.configureAttachmentMenu(conversation, mOptionsMenu, activity.getAttachmentChoicePreference(), hasAttachments);
        }
        updateSendButton();
    }

    private boolean canSendMeCommand() {
        if (conversation != null) {
            final String body = binding.textinput.getText().toString();
            return body.length() == 0;
        }
        return false;
    }

    private void handleNegativeActivityResult(int requestCode) {
        if (requestCode == ATTACHMENT_CHOICE_TAKE_PHOTO) {
            if (pendingTakePhotoUri.clear()) {
                Log.d(Config.LOGTAG, "cleared pending photo uri after negative activity result");
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ActivityResult activityResult = ActivityResult.of(requestCode, resultCode, data);
        if (activity != null && activity.xmppConnectionService != null) {
            handleActivityResult(activityResult);
        } else {
            this.postponedActivityResult.push(activityResult);
        }

        ChatBackgroundHelper.onActivityResult(activity, requestCode, resultCode, data, conversation.getUuid());

        if (requestCode == ChatBackgroundHelper.REQUEST_IMPORT_BACKGROUND) {
            refresh();
        }
    }

    public void unblockConversation(final Blockable conversation) {
        activity.xmppConnectionService.sendUnblockRequest(conversation);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Log.d(Config.LOGTAG, "ConversationFragment.onAttach()");
        if (activity instanceof ConversationsActivity) {
            this.activity = (ConversationsActivity) activity;
        } else {
            throw new IllegalStateException("Trying to attach fragment to activity that is not the ConversationsActivity");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.activity = null; //TODO maybe not a good idea since some callbacks really need it
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        oldOrientation = activity.getRequestedOrientation();
        activity.getOnBackPressedDispatcher().addCallback(this, backPressedLeaveSingleThread);
        activity.getOnBackPressedDispatcher().addCallback(this, backPressedLeaveEmojiPicker);
        activity.getOnBackPressedDispatcher().addCallback(this, backPressedLeaveVoiceRecorder);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        if (activity != null) {
            mOptionsMenu = menu;
            boolean hasAttachments = mediaPreviewAdapter != null && mediaPreviewAdapter.hasAttachments();
            menuInflater.inflate(R.menu.fragment_conversation, menu);
            final MenuItem menuInviteContact = menu.findItem(R.id.action_invite);
            final MenuItem menuArchiveChat = menu.findItem(R.id.action_archive_chat);
            final MenuItem menuLeaveGroup = menu.findItem(R.id.action_leave_group);
            final MenuItem menuGroupDetails = menu.findItem(R.id.action_group_details);
            final MenuItem menuParticipants = menu.findItem(R.id.action_participants);
            final MenuItem menuContactDetails = menu.findItem(R.id.action_contact_details);
            final MenuItem menuMucParticipants = menu.findItem(R.id.action_muc_participants);
            final MenuItem menuCall = menu.findItem(R.id.action_call);
            final MenuItem menuOngoingCall = menu.findItem(R.id.action_ongoing_call);
            final MenuItem menuVideoCall = menu.findItem(R.id.action_video_call);
            final MenuItem menuTogglePinned = menu.findItem(R.id.action_toggle_pinned);
            final MenuItem menuSettings = menu.findItem(R.id.action_settings);
            final MenuItem menuInviteToChat = menu.findItem(R.id.action_invite_user);
            final MenuItem deleteCustomBg = menu.findItem(R.id.action_delete_custom_bg);

            if (conversation != null) {
                if (conversation.getMode() == Conversation.MODE_MULTI || (activity.xmppConnectionService != null && !activity.xmppConnectionService.hasInternetConnection())) {
                    menuInviteContact.setVisible(conversation.getMucOptions().canInvite());
                    menuArchiveChat.setVisible(false);
                    menuLeaveGroup.setVisible(true);
                    menuCall.setVisible(false);
                    menuOngoingCall.setVisible(false);
                    menuParticipants.setVisible(true);
                    menuSettings.setVisible(false);
                    menuInviteToChat.setVisible(false);
                } else {
                    menuMucParticipants.setVisible(false);
                    final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
                    final Optional<OngoingRtpSession> ongoingRtpSession = service == null ? Optional.absent() : service.getJingleConnectionManager().getOngoingRtpConnection(conversation.getContact());
                    if (ongoingRtpSession.isPresent()) {
                        menuOngoingCall.setVisible(true);
                        menuCall.setVisible(false);
                    } else {
                        menuOngoingCall.setVisible(false);
                        final RtpCapability.Capability rtpCapability = RtpCapability.check(conversation.getContact());
                        final boolean cameraAvailable = activity != null && activity.isCameraFeatureAvailable();
                        menuCall.setVisible(rtpCapability != RtpCapability.Capability.NONE);
                        menuVideoCall.setVisible(rtpCapability == RtpCapability.Capability.VIDEO && cameraAvailable);
                    }
                    menuParticipants.setVisible(false);
                    menuInviteContact.setVisible(false);
                    menuArchiveChat.setVisible(true);
                    menuLeaveGroup.setVisible(false);
                    menuSettings.setVisible(false);
                    menuInviteToChat.setVisible(false);
                }
                try {
                    Fragment secondaryFragment = activity.getFragmentManager().findFragmentById(R.id.secondary_fragment);
                    if (secondaryFragment instanceof ConversationFragment) {
                        if (conversation.getMode() == Conversation.MODE_MULTI) {
                            menuGroupDetails.setTitle(conversation.getMucOptions().isPrivateAndNonAnonymous() ? R.string.action_group_details : R.string.channel_details);
                            menuGroupDetails.setVisible(true);
                            menuContactDetails.setVisible(false);
                        } else {
                            menuGroupDetails.setVisible(false);
                            menuContactDetails.setVisible(!this.conversation.withSelf());
                        }
                    } else {
                        menuGroupDetails.setVisible(false);
                        menuContactDetails.setVisible(false);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    menuGroupDetails.setVisible(false);
                    menuContactDetails.setVisible(false);
                }
                ConversationMenuConfigurator.configureAttachmentMenu(conversation, menu, activity.getAttachmentChoicePreference(), hasAttachments);
                ConversationMenuConfigurator.configureEncryptionMenu(conversation, menu, activity);
                if (conversation.getBooleanAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP, false)) {
                    menuTogglePinned.setTitle(R.string.remove_from_favorites);
                } else {
                    menuTogglePinned.setTitle(R.string.add_to_favorites);
                }
            } else {
                menuInviteContact.setVisible(false);
                menuGroupDetails.setVisible(false);
                menuContactDetails.setVisible(false);
            }
            deleteCustomBg.setVisible(ChatBackgroundHelper.getBgFile(activity, conversation.getUuid()).exists());
            super.onCreateOptionsMenu(menu, menuInflater);
        }
        Fragment secondaryFragment = activity.getFragmentManager().findFragmentById(R.id.secondary_fragment);
        if (secondaryFragment instanceof ConversationFragment) {
            activity.showNavigationBar();
        } else {
            activity.hideNavigationBar();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.binding = DataBindingUtil.inflate(inflater, R.layout.fragment_conversation, container, false);
        binding.getRoot().setOnClickListener(null); //TODO why did we do this?

        LoadStickers();
        LoadGifs();

        if (binding.emojisStickerLayout.getHeight() > 100) {
            backPressedLeaveEmojiPicker.setEnabled(true);
        } else {
            backPressedLeaveEmojiPicker.setEnabled(false);
        }

        binding.textinput.setOnEditorActionListener(mEditorActionListener);
        binding.textinput.setRichContentListener(new String[] {"image/*"}, mEditorContentListener);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        if (displayMetrics.heightPixels > 0) binding.textinput.setMaxHeight(displayMetrics.heightPixels / 4);
        binding.textSendButton.setOnClickListener(this.mSendButtonListener);
        binding.cancelButton.setOnClickListener(this.mCancelVoiceRecord);
        binding.shareButton.setOnClickListener(this.mShareVoiceRecord);
        binding.contextPreviewCancel.setOnClickListener((v) -> {
            conversation.setUserSelectedThread(false);
            setupReply(null);
        });
        binding.textSendButton.setOnLongClickListener(this.mSendButtonLongListener);
        binding.scrollToBottomButton.setOnClickListener(this.mScrollButtonListener);
        binding.recordVoiceButton.setOnClickListener(this.mRecordVoiceButtonListener);
        binding.emojiButton.setOnClickListener(this.memojiButtonListener);
        binding.emojisButton.setOnClickListener(this.memojisButtonListener);
        binding.stickersButton.setOnClickListener(this.mstickersButtonListener);
        binding.gifsButton.setOnClickListener(this.mgifsButtonListener);
        binding.keyboardButton.setOnClickListener(this.mkeyboardButtonListener);
        binding.timer.setOnClickListener(this.mTimerClickListener);
        binding.takePictureButton.setOnClickListener(this.mtakePictureButtonListener);
        binding.messagesView.setOnScrollListener(mOnScrollListener);
        binding.messagesView.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        mediaPreviewAdapter = new MediaPreviewAdapter(this);
        binding.mediaPreview.setAdapter(mediaPreviewAdapter);
        messageListAdapter = new MessageAdapter((XmppActivity) getActivity(), this.messageList);
        messageListAdapter.setOnContactPictureClicked(this);
        messageListAdapter.setOnContactPictureLongClicked(this);
        messageListAdapter.setOnInlineImageLongClicked(this);
        messageListAdapter.setConversationFragment(this);
        binding.messagesView.setAdapter(messageListAdapter);

        binding.textinput.addTextChangedListener(
                new StylingHelper.MessageEditorStyler(binding.textinput, messageListAdapter));

        registerForContextMenu(binding.messagesView);
        registerForContextMenu(binding.textSendButton);

        this.binding.textinput.setCustomInsertionActionModeCallback(new EditMessageActionModeCallback(this.binding.textinput));

        messageListAdapter.setOnMessageBoxSwiped(message -> {
            quoteMessage(message, null);
        });
        messageListAdapter.setOnMessageBoxClicked(message -> {
            conversation.setUserSelectedThread(true);
        });


        if (activity.xmppConnectionService.getBooleanPreference("message_autocomplete", R.bool.message_autocomplete)) {
            Autocomplete.<MucOptions.User>on(binding.textinput)
                    .with(activity.getDrawable(R.drawable.input_bubble_light))
                    .with(new CharPolicy('@'))
                    .with(new RecyclerViewPresenter<User>(activity) {
                        protected UserAdapter adapter;

                        @Override
                        protected RecyclerView.Adapter instantiateAdapter() {
                            adapter = new UserAdapter(false) {
                                @Override
                                public void onBindViewHolder(UserAdapter.ViewHolder viewHolder, int position) {
                                    super.onBindViewHolder(viewHolder, position);
                                    final var item = getItem(position);
                                    viewHolder.binding.getRoot().setOnClickListener(v -> {
                                        dispatchClick(item);
                                    });
                                }
                            };
                            return adapter;
                        }

                        @Override
                        protected void onQuery(@Nullable CharSequence query) {
                            getRecyclerView().getItemAnimator().endAnimations();
                            final var allUsers = conversation.getMucOptions().getUsers();
                            if (!conversation.getMucOptions().getUsersByRole(MucOptions.Role.MODERATOR).isEmpty()) {
                                final var u = new MucOptions.User(conversation.getMucOptions(), null, "\0role:moderator", "Notify active moderators", new HashSet<>());
                                u.setRole("participant");
                                allUsers.add(u);
                            }
                            if (!allUsers.isEmpty() && conversation.getMucOptions().getSelf() != null && conversation.getMucOptions().getSelf().getAffiliation().ranks(MucOptions.Affiliation.MEMBER)) {
                                final var u = new MucOptions.User(conversation.getMucOptions(), null, "\0attention", "Notify active participants", new HashSet<>());
                                u.setRole("participant");
                                allUsers.add(u);
                            }
                            final String needle = query.toString().toLowerCase(Locale.getDefault());
                            adapter.submitList(
                                    Ordering.natural().immutableSortedCopy(Collections2.filter(
                                            allUsers,
                                            user -> {
                                                if ("mods".contains(needle) && "\0role:moderator".equals(user.getOccupantId()))
                                                    return true;
                                                if ("here".contains(needle) && "\0attention".equals(user.getOccupantId()))
                                                    return true;
                                                final String name = user.getNick();
                                                if (name == null) return false;
                                                for (final var hat : user.getHats()) {
                                                    if (hat.toString().toLowerCase(Locale.getDefault()).contains(needle))
                                                        return true;
                                                }
                                                for (final var hat : user.getPseudoHats(activity)) {
                                                    if (hat.toString().toLowerCase(Locale.getDefault()).contains(needle))
                                                        return true;
                                                }
                                                final Contact contact = user.getContact();
                                                return name.toLowerCase(Locale.getDefault()).contains(needle)
                                                        || contact != null
                                                        && contact.getDisplayName().toLowerCase(Locale.getDefault()).contains(needle);
                                            })));
                        }

                        @Override
                        protected AutocompletePresenter.PopupDimensions getPopupDimensions() {
                            final var dim = new AutocompletePresenter.PopupDimensions();
                            dim.width = displayMetrics.widthPixels * 4 / 5;
                            return dim;
                        }
                    })
                    .with(new AutocompleteCallback<User>() {
                        @Override
                        public boolean onPopupItemClicked(Editable editable, MucOptions.User user) {
                            int[] range = com.otaliastudios.autocomplete.CharPolicy.getQueryRange(editable);
                            if (range == null) return false;
                            range[0] -= 1;
                            if ("\0attention".equals(user.getOccupantId())) {
                                editable.delete(Math.max(0, range[0]), Math.min(editable.length(), range[1]));
                                editable.insert(0, "@here ");
                                return true;
                            }
                            int colon = editable.toString().indexOf(':');
                            final var beforeColon = range[0] < colon;
                            String prefix = "";
                            String suffix = " ";
                            if (beforeColon) suffix = ", ";
                            if (colon < 0 && range[0] == 0) suffix = ": ";
                            if (colon > 0 && colon == range[0] - 2) {
                                prefix = ", ";
                                suffix = ": ";
                                range[0] -= 2;
                            }
                            var insert = user.getNick();
                            if ("\0role:moderator".equals(user.getOccupantId())) {
                                insert = conversation.getMucOptions().getUsersByRole(MucOptions.Role.MODERATOR).stream().map(MucOptions.User::getNick).collect(Collectors.joining(", "));
                            }
                            editable.replace(Math.max(0, range[0]), Math.min(editable.length(), range[1]), prefix + insert + suffix);
                            return true;
                        }

                        @Override
                        public void onPopupVisibilityChanged(boolean shown) {
                        }
                    }).build();
        }

        hasWriteAccessInMUC();
        return binding.getRoot();
    }


    public void LoadStickers() {
        final Pattern lastColonPattern = Pattern.compile("");
        binding.stickersview.setOnItemClickListener((parent, view, position, id) -> {
            EmojiSearch.EmojiSearchAdapter adapter = ((EmojiSearch.EmojiSearchAdapter) binding.stickersview.getAdapter());
            Editable toInsert = adapter.getItem(position).toInsert();
            toInsert.append(" ");
            Editable s = binding.textinput.getText();

            Matcher lastColonMatcher = lastColonPattern.matcher(s);
            int lastColon = 0;
            while(lastColonMatcher.find()) lastColon = lastColonMatcher.end();
            if (lastColon >= 0) {
                int start = binding.textinput.getSelectionStart(); //this is to get the the cursor position
                binding.textinput.getText().insert(start, toInsert); //this will get the text and insert the emoji into   the current position
            }
        });

        setupEmojiSearch();
    }

    public void LoadGifs() {
            if (!hasStoragePermission(activity)) return;
            // Load and show GIFs
            if (!dirGifs.exists()) {
                dirGifs.mkdir();
            }
            if (dirGifs.listFiles() != null) {
                if (dirGifs.isDirectory() && dirGifs.listFiles() != null) {
                    files = dirGifs.listFiles();
                    filesPaths = new String[files.length];
                    filesNames = new String[files.length];
                    for (int i = 0; i < files.length; i++) {
                        filesPaths[i] = files[i].getAbsolutePath();
                        filesNames[i] = files[i].getName();
                    }
                }
            }
            de.monocles.mod.GridView GifsGrid = binding.gifsview; // init GridView
            // Create an object of CustomAdapter and set Adapter to GirdView
            GifsGrid.setAdapter(new GifsAdapter(activity, filesNames, filesPaths));
            // implement setOnItemClickListener event on GridView
            GifsGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (activity == null) return;
                    String filePath = filesPaths[position];
                    mediaPreviewAdapter.addMediaPreviews(Attachment.of(activity, Uri.fromFile(new File(filePath)), Attachment.Type.IMAGE));
                    toggleInputMethod();
                }
            });

            GifsGrid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    if (activity != null && filesPaths[position] != null) {
                        File file = new File(filesPaths[position]);
                        if (file.delete()) {
                            Toast.makeText(activity, R.string.gif_deleted, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(activity, R.string.failed_to_delete_gif, Toast.LENGTH_LONG).show();
                        }
                    }
                    return true;
                }
            });
    }

    protected void setupEmojiSearch() {
        if (emojiSearch == null && activity != null && activity.xmppConnectionService != null) {
            emojiSearch = activity.xmppConnectionService.emojiSearch();
        }
        if (emojiSearch == null || binding.stickersview == null) return;

        binding.stickersview.setAdapter(emojiSearch.makeAdapter(activity));

        final Pattern lastColonPattern = Pattern.compile("");
        Editable s = binding.textinput.getText();
        Handler emojiDebounce = new Handler(Looper.getMainLooper());
        emojiDebounce.removeCallbacksAndMessages(null);
        emojiDebounce.postDelayed(() -> {
            Matcher lastColonMatcher = lastColonPattern.matcher(s);
            int lastColon = 0;
            while(lastColonMatcher.find()) lastColon = lastColonMatcher.end();

            final String q = s.toString().substring(lastColon);
            EmojiSearch.EmojiSearchAdapter adapter = ((EmojiSearch.EmojiSearchAdapter) binding.stickersview.getAdapter());
            if (adapter != null) {
                adapter.search(q);
            }
        }, 400L);
    }

    protected void newThreadTutorialToast(String s) {
        if (activity == null) return;
        final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
        final int tutorialCount = p.getInt("thread_tutorial", 0);
        if (tutorialCount < 5) {
            Toast.makeText(activity, s, Toast.LENGTH_SHORT).show();
            p.edit().putInt("thread_tutorial", tutorialCount + 1).apply();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(Config.LOGTAG, "ConversationFragment.onDestroyView()");
        if (activity != null &&
                activity.getWindow() != null &&
                activity.getWindow().getDecorView() != null) {
            ViewCompat.setOnApplyWindowInsetsListener(activity.getWindow().getDecorView(), null);
        }
        if (keyboardHeightProvider != null) {
            keyboardHeightProvider.dismiss();
        }
        if (messageListAdapter == null) {
            return;
        }
        messageListAdapter.setOnContactPictureClicked(null);
        messageListAdapter.setOnContactPictureLongClicked(null);
        messageListAdapter.setOnInlineImageLongClicked(null);
        messageListAdapter.setConversationFragment(null);
        binding.conversationViewPager.setAdapter(null);
        if (conversation != null) conversation.setupViewPager(null, null, false, null);
    }

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        int animator = enter ? R.animator.fade_right_in : R.animator.fade_right_out;
        return AnimatorInflater.loadAnimator(this.activity, animator);

    }

    public void quoteText(String text, String user) {
        if (binding.textinput.isEnabled()) {
            String username = "";
            if (user != null && user.length() > 0) {
                if (user.equals(getString(R.string.me))) {
                    username = getString(R.string.me_quote) + System.getProperty("line.separator");
                } else {
                    username = getString(R.string.x_user_quote, user) + System.getProperty("line.separator");
                }
            }
            binding.textinput.insertAsQuote(username + text);
            binding.textinput.requestFocus();
            InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null) {
                inputMethodManager.showSoftInput(binding.textinput, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    public void showRecordVoiceButton() {
        if (activity != null && binding != null) {
            SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
            final boolean hideVoiceAndTakePicture = p.getBoolean("show_record_voice_btn", activity.getResources().getBoolean(R.bool.show_record_voice_btn));
            Log.d(Config.LOGTAG, "Recorder " + hideVoiceAndTakePicture);
            if (!hideVoiceAndTakePicture || binding.textinput.getText().length() > 0) {
                binding.recordVoiceButton.setVisibility(GONE);
                binding.takePictureButton.setVisibility(GONE);
            } else if (hideVoiceAndTakePicture && binding.textinput.getText().length() < 1) {
                binding.recordVoiceButton.setVisibility(View.VISIBLE);
                binding.takePictureButton.setVisibility(View.VISIBLE);
            }
        }
    }

    private void quoteMedia(Message message, @Nullable String user) {
        Message.FileParams params = message.getFileParams();
        String filesize = params.size != null ? UIHelper.filesizeToString(params.size) : null;
        final StringBuilder stringBuilder = new StringBuilder();
        if (activity.showDateInQuotes()) {
            stringBuilder.append(df.format(message.getTimeSent())).append(System.getProperty("line.separator"));
        }
        stringBuilder.append(MimeUtils.getMimeTypeEmoji(getActivity(), message.getMimeType())).append(" ");
        stringBuilder.append(" \u00B7 ");
        stringBuilder.append(filesize);
        quoteText(stringBuilder.toString(), user);
    }

    private void quoteGeoUri(Message message, @Nullable String user) {
        final StringBuilder stringBuilder = new StringBuilder();
        if (activity.showDateInQuotes()) {
            stringBuilder.append(df.format(message.getTimeSent())).append(System.getProperty("line.separator"));
        }
        stringBuilder.append("\uD83D\uDDFA"); // map
        quoteText(stringBuilder.toString(), user);
    }

    private void quoteMessage(Message message, @Nullable String user) {
        conversation.setUserSelectedThread(true);
        if (message.isGeoUri()) {
            quoteGeoUri(message, user);
        }
        if (!forkNullThread(message)) newThread();
        setupReply(message);
    }

    private boolean forkNullThread(Message message) {
        if (message.getThread() != null || conversation.getMode() != Conversation.MODE_MULTI) return true;
        for (final Message m : conversation.findReplies(message.getServerMsgId())) {
            final Element thread = m.getThread();
            if (thread != null) {
                return true;
            }
        }

        return false;
    }

    private void setupReply(Message message) {
        conversation.setReplyTo(message);
        if (message == null) {
            binding.contextPreview.setVisibility(GONE);
            return;
        }

        SpannableStringBuilder body = message.getSpannableBody(null, null);
        if ((message.isFileOrImage() || message.isOOb()) && binding.imageReplyPreview != null) {
            binding.imageReplyPreview.setVisibility(VISIBLE);
            if (activity.getBooleanPreference("play_gif_inside", R.bool.play_gif_inside)) {
                Glide.with(activity).load(message.getRelativeFilePath()).placeholder(R.drawable.ic_file_grey600_48dp).thumbnail(0.2f).into(binding.imageReplyPreview);
            } else {
                Glide.with(activity).asBitmap().load(message.getRelativeFilePath()).placeholder(R.drawable.ic_file_grey600_48dp).thumbnail(0.2f).into(binding.imageReplyPreview);
            }
        } else if (binding.imageReplyPreview != null) {
            Glide.with(activity).clear(binding.imageReplyPreview);
            binding.imageReplyPreview.setVisibility(GONE);
        }
        messageListAdapter.handleTextQuotes(binding.contextPreviewText, body, activity.isDarkTheme());
        binding.contextPreviewText.setText(body);
        binding.contextPreview.setVisibility(View.VISIBLE);
    }
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        //This should cancel any remaining click events that would otherwise trigger links
        v.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0));

        if (v == binding.textSendButton) {
            super.onCreateContextMenu(menu, v, menuInfo);
            try {
                java.lang.reflect.Method m = menu.getClass().getSuperclass().getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
                m.setAccessible(true);
                m.invoke(menu, true);
            } catch (Exception e) {
                Log.w("WUT", "" + e);
                e.printStackTrace();
            }
            Menu tmpMenu = new android.widget.PopupMenu(activity, null).getMenu();
            activity.getMenuInflater().inflate(R.menu.fragment_conversation, tmpMenu);
            MenuItem attachMenu = tmpMenu.findItem(R.id.action_attach_file);
            for (int i = 0; i < attachMenu.getSubMenu().size(); i++) {
                MenuItem item = attachMenu.getSubMenu().getItem(i);
                MenuItem newItem = menu.add(item.getGroupId(), item.getItemId(), item.getOrder(), item.getTitle());
                newItem.setIcon(item.getIcon());
            }
            return;
        }

        synchronized (this.messageList) {
            super.onCreateContextMenu(menu, v, menuInfo);
            AdapterView.AdapterContextMenuInfo acmi = (AdapterContextMenuInfo) menuInfo;
            this.selectedMessage = this.messageList.get(acmi.position);
            populateContextMenu(menu);
        }
    }

    private void populateContextMenu(ContextMenu menu) {
        final Message m = this.selectedMessage;
        final Transferable t = m.getTransferable();
        Message relevantForCorrection = m;
        while (relevantForCorrection.mergeable(relevantForCorrection.next())) {
            relevantForCorrection = relevantForCorrection.next();
        }
        if (m.getType() != Message.TYPE_STATUS && m.getType() != Message.TYPE_RTP_SESSION) {

            if (m.getEncryption() == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE || m.getEncryption() == Message.ENCRYPTION_AXOLOTL_FAILED) {
                return;
            }
            if (m.getStatus() == Message.STATUS_RECEIVED && t != null && (t.getStatus() == Transferable.STATUS_CANCELLED || t.getStatus() == Transferable.STATUS_FAILED)) {
                return;
            }
            final boolean fileDeleted = m.isFileDeleted();
            final boolean encrypted = m.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED
                    || m.getEncryption() == Message.ENCRYPTION_PGP;
            final boolean receiving = m.getStatus() == Message.STATUS_RECEIVED && (t instanceof JingleFileTransferConnection || t instanceof HttpDownloadConnection);
            activity.getMenuInflater().inflate(R.menu.message_context, menu);
            final MenuItem reportAndBlock = menu.findItem(R.id.action_report_and_block);
            MenuItem openWith = menu.findItem(R.id.open_with);
            MenuItem copyMessage = menu.findItem(R.id.copy_message);
            MenuItem quoteMessage = menu.findItem(R.id.quote_message);
            MenuItem retryDecryption = menu.findItem(R.id.retry_decryption);
            MenuItem correctMessage = menu.findItem(R.id.correct_message);
            MenuItem retractMessage = menu.findItem(R.id.retract_message);
            MenuItem moderateMessage = menu.findItem(R.id.moderate_message);
            MenuItem onlyThisThread = menu.findItem(R.id.only_this_thread);
            MenuItem deleteMessage = menu.findItem(R.id.delete_message);
            MenuItem messageReaction = menu.findItem(R.id.message_reaction);  //add the most used emoticons
            MenuItem shareWith = menu.findItem(R.id.share_with);
            MenuItem sendAgain = menu.findItem(R.id.send_again);
            MenuItem copyUrl = menu.findItem(R.id.copy_url);
            MenuItem saveAsSticker = menu.findItem(R.id.save_as_sticker);
            MenuItem saveAsGif = menu.findItem(R.id.save_as_gif);
            MenuItem cancelTransmission = menu.findItem(R.id.cancel_transmission);
            MenuItem downloadFile = menu.findItem(R.id.download_file);
            MenuItem blockMedia = menu.findItem(R.id.block_media);
            MenuItem deleteFile = menu.findItem(R.id.delete_file);
            MenuItem showLog = menu.findItem(R.id.show_edit_log);
            MenuItem showErrorMessage = menu.findItem(R.id.show_error_message);
            MenuItem saveFile = menu.findItem(R.id.save_file);
            onlyThisThread.setVisible(!conversation.getLockThread() && m.getThread() != null);
            final boolean unInitiatedButKnownSize = MessageUtils.unInitiatedButKnownSize(m);
            final boolean showError = m.getStatus() == Message.STATUS_SEND_FAILED && m.getErrorMessage() != null && !Message.ERROR_MESSAGE_CANCELLED.equals(m.getErrorMessage());
            final Conversational conversational = m.getConversation();
            if (m.getStatus() == Message.STATUS_RECEIVED && conversational instanceof Conversation c) {
                final XmppConnection connection = c.getAccount().getXmppConnection();
                if (c.isWithStranger()
                        && m.getServerMsgId() != null
                        && !c.isBlocked()
                        && connection != null
                        && connection.getFeatures().spamReporting()) {
                    reportAndBlock.setVisible(true);
                }
            }
            final boolean messageDeleted = m.isMessageDeleted();
            deleteMessage.setVisible(true);
            if (!encrypted && !m.getBody().equals("")) {
                copyMessage.setVisible(true);
                quoteMessage.setVisible(!showError && MessageUtils.prepareQuote(m).length() > 0);
            }
            quoteMessage.setVisible(!encrypted && !showError);
            if (m.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED && !fileDeleted) {
                retryDecryption.setVisible(true);
            }
            if (!encrypted && !unInitiatedButKnownSize && t == null) {
                quoteMessage.setVisible(!showError && QuoteHelper.isMessageQuoteable(m));
            }
            if (!showError
                    && relevantForCorrection.getType() == Message.TYPE_TEXT
                    && !m.isGeoUri()
                    && relevantForCorrection.isEditable()
                    && m.getConversation() instanceof Conversation) {
                correctMessage.setVisible(true);
                if (!relevantForCorrection.getBody().equals("") && !relevantForCorrection.getBody().equals(" ")) retractMessage.setVisible(false);
            }
            if (relevantForCorrection.getReactions() != null) {
                correctMessage.setVisible(false);
                retractMessage.setVisible(false);
            }
            if (conversation.getMode() == Conversation.MODE_MULTI && m.getServerMsgId() != null && m.getModerated() == null && conversation.getMucOptions().getSelf().getRole().ranks(MucOptions.Role.MODERATOR) && conversation.getMucOptions().hasFeature("urn:xmpp:message-moderate:0")) {
                moderateMessage.setVisible(true);
            }
            if ((m.isFileOrImage() && !fileDeleted && !receiving) || (m.getType() == Message.TYPE_TEXT && !m.treatAsDownloadable()) && !unInitiatedButKnownSize && t == null && !messageDeleted) {
                shareWith.setVisible(true);

            }
            if (m.getStatus() == Message.STATUS_SEND_FAILED) {
                sendAgain.setVisible(true);
            }
            if (m.hasFileOnRemoteHost()
                    || m.isGeoUri()
                    || m.isXmppUri()
                    || m.treatAsDownloadable()
                    || unInitiatedButKnownSize
                    || t instanceof HttpDownloadConnection) {
                copyUrl.setVisible(true);
            }
            if (m.isFileOrImage() && fileDeleted && m.hasFileOnRemoteHost()) {
                downloadFile.setVisible(true);
                downloadFile.setTitle(activity.getString(R.string.download_x_file, UIHelper.getFileDescriptionString(activity, m)));
            }
            final boolean waitingOfferedSending = m.getStatus() == Message.STATUS_WAITING
                    || m.getStatus() == Message.STATUS_UNSEND
                    || m.getStatus() == Message.STATUS_OFFERED;
            final boolean cancelable = (t != null && !fileDeleted) || waitingOfferedSending && m.needsUploading();
            if (cancelable) {
                cancelTransmission.setVisible(true);
            }
            if (m.isFileOrImage() && !fileDeleted && !cancelable) {
                final String path = m.getRelativeFilePath();
                Log.d(Config.LOGTAG, "Path = " + path);
                if ((path == null || !path.startsWith("/") || path.contains(getConversationsDirectory(this.activity, "null").getAbsolutePath())) && Objects.equals(MimeUtils.guessMimeTypeFromUri(activity, Uri.parse(path)), "image/gif")) {
                    saveAsGif.setVisible(true);
                    blockMedia.setVisible(true);
                    deleteFile.setVisible(true);
                    deleteFile.setTitle(activity.getString(R.string.delete_x_file, UIHelper.getFileDescriptionString(activity, m)));
                } else if (path == null || !path.startsWith("/") || path.contains(getConversationsDirectory(this.activity, "null").getAbsolutePath())) {
                    saveAsSticker.setVisible(true);
                    blockMedia.setVisible(true);
                    deleteFile.setVisible(true);
                    deleteFile.setTitle(activity.getString(R.string.delete_x_file, UIHelper.getFileDescriptionString(activity, m)));
                }
                saveFile.setVisible(true);
                saveFile.setTitle(activity.getString(R.string.save_x_file, UIHelper.getFileDescriptionString(activity, m)));
            }
            if (m.getFileParams() != null && !m.getFileParams().getThumbnails().isEmpty()) {
                // We might be showing a thumbnail worth blocking
                blockMedia.setVisible(true);
            }
            if (showError) {
                showErrorMessage.setVisible(true);
            }
            final String mime = m.isFileOrImage() ? m.getMimeType() : null;
            if ((m.isGeoUri() && GeoHelper.openInOsmAnd(getActivity(), m)) || (mime != null && mime.startsWith("audio/"))) {
                openWith.setVisible(true);
            }
            if (m.edited() && m.getRetractId() == null) {
                showLog.setVisible(false);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        String user;
        try {
            final Contact contact = selectedMessage.getContact();
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                user = UIHelper.getDisplayedMucCounterpart(selectedMessage.getCounterpart());
            } else {
                user = contact != null ? contact.getDisplayName() : null;
            }
            if (selectedMessage.getStatus() == Message.STATUS_SEND
                    || selectedMessage.getStatus() == Message.STATUS_SEND_FAILED
                    || selectedMessage.getStatus() == Message.STATUS_SEND_RECEIVED
                    || selectedMessage.getStatus() == Message.STATUS_SEND_DISPLAYED) {
                user = getString(R.string.me);
            }
        } catch (Exception e) {
            e.printStackTrace();
            user = null;
        }
        int itemId = item.getItemId();
        if (itemId == R.id.share_with) {
            ShareUtil.share(activity, selectedMessage, user);
            return true;
        } else if (itemId == R.id.correct_message) {
            correctMessage(selectedMessage);
            return true;
        } else if (itemId == R.id.retract_message) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.retract_message)
                    .setMessage(R.string.retract_message_dialog_msg)
                    .setPositiveButton(R.string.yes, (dialog, whichButton) -> {
                        Message message = selectedMessage;
                        while (message.mergeable(message.next())) {
                            message = message.next();
                        }
                        Element reactions = message.getReactions();
                        if (reactions != null) {
                            final Message previousReaction = conversation.findMessageReactingTo(reactions.getAttribute("id"), null);
                            if (previousReaction != null)
                                reactions = previousReaction.getReactions();
                            for (Element el : reactions.getChildren()) {
                                if (message.getRawBody().endsWith(el.getContent())) {
                                    reactions.removeChild(el);
                                }
                            }
                            message.setReactions(reactions);
                            if (previousReaction != null) {
                                previousReaction.setReactions(reactions);
                                activity.xmppConnectionService.updateMessage(previousReaction);
                            }
                        } else {
                            message.setInReplyTo(null);
                            message.clearPayloads();
                        }
                        message.setBody(" ");
                        message.setSubject(null);
                        message.putEdited(message.getUuid(), message.getServerMsgId(), message.getBody(), message.getTimeSent());
                        message.setServerMsgId(null);
                        message.setUuid(UUID.randomUUID().toString());
                        sendMessage(message);
                    })
                    .setNegativeButton(R.string.no, null).show();
            return true;
        } else if (itemId == R.id.moderate_message) {
            activity.quickEdit("Spam", (reason) -> {
                activity.xmppConnectionService.moderateMessage(conversation.getAccount(), selectedMessage, reason);
                return null;
            }, R.string.moderate_reason, false, false, true, true);
            return true;
        } else if (itemId == R.id.copy_message) {
            ShareUtil.copyToClipboard(activity, selectedMessage);
            return true;
        } else if (itemId == R.id.quote_message) {
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                quoteMessage(selectedMessage, user);
            } else {
                quoteMessage(selectedMessage, null);
            }
            return true;
        } else if (itemId == R.id.send_again) {
            resendMessage(selectedMessage);
            return true;
        } else if (itemId == R.id.copy_url) {
            ShareUtil.copyUrlToClipboard(activity, selectedMessage);
            return true;
        } else if (itemId == R.id.save_as_sticker) {
            saveAsSticker(selectedMessage);
            return true;
        } else if (itemId == R.id.save_as_gif) {
            saveAsGif(selectedMessage);
            return true;
        } else if (itemId == R.id.download_file) {
            startDownloadable(selectedMessage);
            return true;
        } else if (itemId == R.id.cancel_transmission) {
            cancelTransmission(selectedMessage);
            return true;
        } else if (itemId == R.id.retry_decryption) {
            retryDecryption(selectedMessage);
            return true;
        } else if (itemId == R.id.block_media) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.block_media)
                    .setMessage("Do you really want to block this media in all messages?")
                    .setPositiveButton(R.string.yes, (dialog, whichButton) -> {
                        File f = activity.xmppConnectionService.getFileBackend().getFile(selectedMessage);
                        activity.xmppConnectionService.blockMedia(f);
                        if (activity.xmppConnectionService.getFileBackend().deleteFile(selectedMessage)) {
                            activity.xmppConnectionService.evictPreview(f);
                            activity.xmppConnectionService.updateMessage(selectedMessage, false);
                            activity.onConversationsListItemUpdated();
                            refresh();
                        }
                    })
                    .setNegativeButton(R.string.no, null).show();
            return true;
        } else if (itemId == R.id.delete_message) {
            deleteMessage(selectedMessage);
            return true;
        } else if (itemId == R.id.delete_file) {
            deleteFile(selectedMessage);
            return true;
        } else if (itemId == R.id.show_error_message) {
            showErrorMessage(selectedMessage);
            return true;
        } else if (itemId == R.id.action_report_and_block) {
            reportMessage(selectedMessage);
            return true;
        } else if (itemId == R.id.open_with) {
            openWith(selectedMessage);
            return true;
        } else if (itemId == R.id.save_file) {
            activity.xmppConnectionService.getFileBackend().saveFile(selectedMessage, activity);
            return true;
        } else if (itemId == R.id.show_edit_log) {
            openLog(selectedMessage);
            return true;
        } else if (itemId == R.id.only_this_thread) {
            conversation.setLockThread(true);
            backPressedLeaveSingleThread.setEnabled(true);
            refresh();
            return true;
        } else if (itemId == R.id.message_reaction) {
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                quoteMessage(selectedMessage, user);
            } else {
                quoteMessage(selectedMessage, null);
            }
            chooseReaction(selectedMessage);
            return true;
        }
        return onOptionsItemSelected(item);
    }

    private void openLog(Message logMsg) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.show_edit_log);
        ArrayList dataModels = new ArrayList<>();
        for (Edit itm : logMsg.getEditedList()) {
            dataModels.add(new MessageLogModel(itm.getBody(), itm.getTimeSent()));
        }
        dataModels.add(new MessageLogModel(logMsg.getBody(), logMsg.getTimeSent()));

        MessageLogAdapter adapter = new MessageLogAdapter(dataModels, getActivity());

        LinearLayout layout = new LinearLayout(getActivity());
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(layoutParams);

        ListView listView = new ListView(getActivity());
        listView.setLayoutParams(layoutParams);
        layout.addView(listView);

        builder.setView(layout);

        listView.setAdapter(adapter);

        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            dialog.dismiss();
        });
        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        Activity mXmppActivity = getActivity();
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        } else if (conversation == null) {
            return super.onOptionsItemSelected(item);
        }
        int itemId = item.getItemId();
        if (itemId == R.id.encryption_choice_axolotl || itemId == R.id.encryption_choice_otr || itemId == R.id.encryption_choice_pgp || itemId == R.id.encryption_choice_none) {
            handleEncryptionSelection(item);
        } else if (itemId == R.id.attach_choose_picture || itemId == R.id.attach_choose_video || itemId == R.id.attach_take_picture || itemId == R.id.attach_record_video || itemId == R.id.attach_choose_file || itemId == R.id.attach_record_voice || itemId == R.id.attach_location) {
            handleAttachmentSelection(item);
        } else if (itemId == R.id.attach_webxdc) {
            handleAttachmentSelection(item);
        } else if (itemId == R.id.attach_subject) {
            handleAttachmentSelection(item);
        } else if (itemId == R.id.action_search) {
            startSearch();
        } else if (itemId == R.id.action_muc_participants) {
            Intent intent_user = new Intent(activity, MucUsersActivity.class);
            intent_user.putExtra("uuid", conversation.getUuid());
            activity.startActivity(intent_user);
        }
        else if (itemId == R.id.action_archive_chat) {
            activity.xmppConnectionService.archiveConversation(conversation);
        } else if (itemId == R.id.action_leave_group) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(activity.getString(R.string.action_end_conversation_muc));
            builder.setMessage(activity.getString(R.string.leave_conference_warning));
            builder.setNegativeButton(activity.getString(R.string.cancel), null);
            builder.setPositiveButton(activity.getString(R.string.action_end_conversation_muc),
                    (dialog, which) -> {
                        activity.xmppConnectionService.archiveConversation(conversation);
                    });
            builder.create().show();
        } else if (itemId == R.id.action_invite) {
            startActivityForResult(ChooseContactActivity.create(activity, conversation), REQUEST_INVITE_TO_CONVERSATION);
        } else if (itemId == R.id.action_clear_history) {
            clearHistoryDialog(conversation);
        } else if (itemId == R.id.action_group_details) {
            activity.switchToMUCDetails(conversation);
        } else if (itemId == R.id.action_participants) {
            Intent intent1 = new Intent(activity, MucUsersActivity.class);
            intent1.putExtra("uuid", conversation.getUuid());
            startActivity(intent1);
            activity.overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
        } else if (itemId == R.id.action_contact_details) {
            activity.switchToContactDetails(conversation.getContact());
        } else if (itemId == R.id.action_block || itemId == R.id.action_unblock) {
            if (mXmppActivity instanceof XmppActivity) {
                BlockContactDialog.show((XmppActivity) mXmppActivity, conversation);
            }
        } else if (itemId == R.id.action_audio_call) {
            if (mXmppActivity instanceof XmppActivity) {
                CallManager.checkPermissionAndTriggerAudioCall((XmppActivity) mXmppActivity, conversation);
            }
        } else if (itemId == R.id.action_video_call) {
            if (mXmppActivity instanceof XmppActivity) {
                CallManager.checkPermissionAndTriggerVideoCall((XmppActivity) mXmppActivity, conversation);
            }
        } else if (itemId == R.id.action_ongoing_call) {
            if (mXmppActivity instanceof XmppActivity) {
                CallManager.returnToOngoingCall((XmppActivity) mXmppActivity, conversation);
            }
        } else if (itemId == R.id.action_toggle_pinned) {
            togglePinned();
        } else if (itemId == R.id.action_add_shortcut) {
            addShortcut();
        } else if (itemId == R.id.action_mute) {
            muteConversationDialog(conversation);
        } else if (itemId == R.id.action_unmute) {
            unmuteConversation(conversation);
//        } else if (itemId == R.id.action_refresh_feature_discovery) {
//            refreshFeatureDiscovery();
        } else if (itemId == R.id.action_set_custom_bg) {
            if (activity.hasStoragePermission(ChatBackgroundHelper.REQUEST_IMPORT_BACKGROUND)) {
                ChatBackgroundHelper.openBGPicker(this);
            }
        } else if (itemId == R.id.action_delete_custom_bg) {
            try {
                File bgfile = ChatBackgroundHelper.getBgFile(activity, conversation.getUuid());
                if (bgfile.exists()) {
                    bgfile.delete();
                    Toast.makeText(activity, R.string.delete_background_success, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(activity, R.string.no_background_set, Toast.LENGTH_LONG).show();
                }

                refresh();
            } catch (Exception e) {
                Toast.makeText(activity, R.string.delete_background_failed, Toast.LENGTH_LONG).show();
                throw new RuntimeException(e);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    public boolean onBackPressed() {
        boolean wasLocked = conversation.getLockThread();
        conversation.setLockThread(false);
        backPressedLeaveSingleThread.setEnabled(false);
        if (wasLocked) {
            conversation.setUserSelectedThread(false);
            refresh();
            updateThreadFromLastMessage();
            return true;
        }
        if (binding.emojisStickerLayout.getHeight() > 100){
            LinearLayout emojipickerview = binding.emojisStickerLayout;
            ViewGroup.LayoutParams params = emojipickerview.getLayoutParams();
            params.height = 0;
            emojipickerview.setLayoutParams(params);
            hideSoftKeyboard(activity);
            return false;
        }
        if (binding.recordingVoiceActivity.getVisibility()==VISIBLE){
            mHandler.removeCallbacks(mTickExecutor);
            stopRecording(false);
            activity.setResult(RESULT_CANCELED);
            //activity.finish();
            binding.recordingVoiceActivity.setVisibility(View.GONE);
            return false;
        }
        return false;
    }

    private void startSearch() {
        final Intent intent = new Intent(getActivity(), SearchActivity.class);
        intent.putExtra(SearchActivity.EXTRA_CONVERSATION_UUID, conversation.getUuid());
        startActivity(intent);
    }


    private void scheduleMessage() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            final var datePicker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
                    .setTitleText(R.string.schedule_message_title)
                    .setSelection(com.google.android.material.datepicker.MaterialDatePicker.todayInUtcMilliseconds())
                    .setCalendarConstraints(
                            new com.google.android.material.datepicker.CalendarConstraints.Builder()
                                    .setStart(com.google.android.material.datepicker.MaterialDatePicker.todayInUtcMilliseconds())
                                    .build()
                    )
                    .build();
            datePicker.addOnPositiveButtonClickListener((date) -> {
                final Calendar now = Calendar.getInstance();
                final var timePicker = new com.google.android.material.timepicker.MaterialTimePicker.Builder()
                        .setTitleText(R.string.schedule_message_title)
                        .setHour(now.get(Calendar.HOUR_OF_DAY))
                        .setMinute(now.get(Calendar.MINUTE))
                        .setTimeFormat(android.text.format.DateFormat.is24HourFormat(activity) ? com.google.android.material.timepicker.TimeFormat.CLOCK_24H : com.google.android.material.timepicker.TimeFormat.CLOCK_12H)
                        .build();
                timePicker.addOnPositiveButtonClickListener((v2) -> {
                    final var dateCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    dateCal.setTimeInMillis(date);
                    final var time = Calendar.getInstance();
                    time.set(dateCal.get(Calendar.YEAR), dateCal.get(Calendar.MONTH), dateCal.get(Calendar.DAY_OF_MONTH), timePicker.getHour(), timePicker.getMinute(), 0);
                    final long timestamp = time.getTimeInMillis();
                    sendMessage(timestamp);
                    Log.d(Config.LOGTAG, conversation.getAccount().getJid().asBareJid() + ": scheduled message for " + timestamp);
                });
                timePicker.show(activity.getSupportFragmentManager(), "schedulMessageTime");
            });
            datePicker.show(activity.getSupportFragmentManager(), "schedulMessageDate");
        }
    }


    private void returnToOngoingCall() {
        final Optional<OngoingRtpSession> ongoingRtpSession = activity.xmppConnectionService.getJingleConnectionManager().getOngoingRtpConnection(conversation.getContact());
        if (ongoingRtpSession.isPresent()) {
            final OngoingRtpSession id = ongoingRtpSession.get();
            final Intent intent = new Intent(activity, RtpSessionActivity.class);
            intent.putExtra(RtpSessionActivity.EXTRA_ACCOUNT, id.getAccount().getJid().asBareJid().toEscapedString());
            intent.putExtra(RtpSessionActivity.EXTRA_WITH, id.getWith().toEscapedString());
            if (id instanceof AbstractJingleConnection) {
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra(RtpSessionActivity.EXTRA_SESSION_ID, id.getSessionId());
                startActivity(intent);
            } else if (id instanceof JingleConnectionManager.RtpSessionProposal proposal) {
                if (proposal.media.contains(Media.VIDEO)) {
                    intent.setAction(RtpSessionActivity.ACTION_MAKE_VIDEO_CALL);
                } else {
                    intent.setAction(RtpSessionActivity.ACTION_MAKE_VOICE_CALL);
                }
                startActivity(intent);
            }
        }
    }

//    private void refreshFeatureDiscovery() {
//        Set<Map.Entry<String, Presence>> presences = conversation.getContact().getPresences().getPresencesMap().entrySet();
//        if (presences.isEmpty()) {
//            presences = new HashSet<>();
//            presences.add(new AbstractMap.SimpleEntry("", null));
//        }
//        for (Map.Entry<String, Presence> entry : presences) {
//            Jid jid = conversation.getContact().getJid();
//            if (!entry.getKey().equals("")) jid = jid.withResource(entry.getKey());
//            activity.xmppConnectionService.fetchCaps(conversation.getAccount(), jid, entry.getValue(), () -> {
//                if (activity == null) return;
//                activity.runOnUiThread(() -> {
//                    refresh();
//                    refreshCommands(true);
//                });
//            });
//        }
//    }

    private void addShortcut() {
        ShortcutInfoCompat info;
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            info = activity.xmppConnectionService.getShortcutService().getShortcutInfoCompat(conversation.getMucOptions());
        } else {
            info = activity.xmppConnectionService.getShortcutService().getShortcutInfoCompat(conversation.getContact());
        }
        ShortcutManagerCompat.requestPinShortcut(activity, info, null);
    }

    private void togglePinned() {
        final boolean pinned = conversation.getBooleanAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP, false);
        conversation.setAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP, !pinned);
        activity.xmppConnectionService.updateConversation(conversation);
        activity.invalidateOptionsMenu();
    }

    private void checkPermissionAndTriggerAudioCall() {
        if (activity.mUseTor || conversation.getAccount().isOnion()) {
            Toast.makeText(activity, R.string.disable_tor_to_make_call, Toast.LENGTH_SHORT).show();
            return;
        }
        if (activity.mUseI2P || conversation.getAccount().isI2P()) {
            Toast.makeText(activity, R.string.no_i2p_calls, Toast.LENGTH_SHORT).show();
            return;
        }
        final List<String> permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions =
                    Arrays.asList(
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissions = Collections.singletonList(Manifest.permission.RECORD_AUDIO);
        }
        if (hasPermissions(REQUEST_START_AUDIO_CALL, permissions)) {
            triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VOICE_CALL);
        }
    }

    private void checkPermissionAndTriggerVideoCall() {
        if (activity.mUseTor || conversation.getAccount().isOnion()) {
            Toast.makeText(activity, R.string.disable_tor_to_make_call, Toast.LENGTH_SHORT).show();
            return;
        }
        if (activity.mUseI2P || conversation.getAccount().isI2P()) {
            Toast.makeText(activity, R.string.no_i2p_calls, Toast.LENGTH_SHORT).show();
            return;
        }
        if (hasPermissions(REQUEST_START_VIDEO_CALL, Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)) {
            triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VIDEO_CALL);
        }
    }

    private void triggerRtpSession(final String action) {
        if (activity.xmppConnectionService.getJingleConnectionManager().isBusy()) {
            Toast.makeText(getActivity(), R.string.only_one_call_at_a_time, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        final Account account = conversation.getAccount();
        if (account.setOption(Account.OPTION_SOFT_DISABLED, false)) {
            activity.xmppConnectionService.updateAccount(account);
        }
        final Contact contact = conversation.getContact();
        if (Config.USE_JINGLE_MESSAGE_INIT && RtpCapability.jmiSupport(contact)) {
            triggerRtpSession(contact.getAccount(), contact.getJid().asBareJid(), action);
        } else {
            final RtpCapability.Capability capability;
            if (action.equals(RtpSessionActivity.ACTION_MAKE_VIDEO_CALL)) {
                capability = RtpCapability.Capability.VIDEO;
            } else {
                capability = RtpCapability.Capability.AUDIO;
            }
            PresenceSelector.selectFullJidForDirectRtpConnection(
                    activity,
                    contact,
                    capability,
                    fullJid -> {
                        triggerRtpSession(contact.getAccount(), fullJid, action);
                    });
        }
    }

    private void triggerRtpSession(final Account account, final Jid with, final String action) {
        CallIntegrationConnectionService.placeCall(activity.xmppConnectionService, account,with,RtpSessionActivity.actionToMedia(action));
    }

    private void handleAttachmentSelection(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.attach_choose_picture) {
            attachFile(ATTACHMENT_CHOICE_CHOOSE_IMAGE);
        } else if (itemId == R.id.attach_choose_video) {
            attachFile(ATTACHMENT_CHOICE_CHOOSE_VIDEO);
        } else if (itemId == R.id.attach_take_picture) {
            attachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);
        } else if (itemId == R.id.attach_record_video) {
            attachFile(ATTACHMENT_CHOICE_RECORD_VIDEO);
        } else if (itemId == R.id.attach_choose_file) {
            attachFile(ATTACHMENT_CHOICE_CHOOSE_FILE);
        } else if (itemId == R.id.attach_record_voice) {
            attachFile(ATTACHMENT_CHOICE_RECORD_VOICE);
        } else if (itemId == R.id.attach_webxdc) {
            final Intent intent2 = new Intent(getActivity(), WebxdcStore.class);
            startActivityForResult(intent2, REQUEST_WEBXDC_STORE);
            activity.overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
        } else if (itemId == R.id.attach_location) {
            attachFile(ATTACHMENT_CHOICE_LOCATION);
        } else if (itemId == R.id.attach_subject) {
            binding.textinputSubject.setVisibility(binding.textinputSubject.getVisibility() == GONE ? VISIBLE : GONE);
        }
    }

    private void handleEncryptionSelection(MenuItem item) {
        if (conversation == null) {
            return;
        }
        final boolean updated;
        int itemId = item.getItemId();
        if (itemId == R.id.encryption_choice_none) {
            updated = conversation.setNextEncryption(Message.ENCRYPTION_NONE);
            item.setChecked(true);
        } else if (itemId == R.id.encryption_choice_otr) {
            updated = conversation.setNextEncryption(Message.ENCRYPTION_OTR);
            item.setChecked(true);
        } else if (itemId == R.id.encryption_choice_pgp) {
            if (activity.hasPgp()) {
                if (conversation.getAccount().getPgpSignature() != null) {
                    updated = conversation.setNextEncryption(Message.ENCRYPTION_PGP);
                    item.setChecked(true);
                } else {
                    updated = false;
                    activity.announcePgp(conversation.getAccount(), conversation, null, activity.onOpenPGPKeyPublished);
                }
            } else {
                activity.showInstallPgpDialog();
                updated = false;
            }
        } else if (itemId == R.id.encryption_choice_axolotl) {
            Log.d(Config.LOGTAG, AxolotlService.getLogprefix(conversation.getAccount())
                    + "Enabled axolotl for Contact " + conversation.getContact().getJid());
            updated = conversation.setNextEncryption(Message.ENCRYPTION_AXOLOTL);
            item.setChecked(true);
        } else {
            updated = conversation.setNextEncryption(Message.ENCRYPTION_NONE);
        }
        if (updated) {
            activity.xmppConnectionService.updateConversation(conversation);
        }
        updateChatMsgHint();
        getActivity().invalidateOptionsMenu();
        activity.refreshUi();
    }

    public void attachFile(final int attachmentChoice) {
        attachFile(attachmentChoice, true);
    }

    public void attachFile(final int attachmentChoice, final boolean updateRecentlyUsed) {
        if (attachmentChoice == ATTACHMENT_CHOICE_RECORD_VOICE) {
            if (!hasPermissions(attachmentChoice, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO) && !Compatibility.runsThirtyThree()) {
                return;
            } else if (Compatibility.runsThirtyThree() && !hasPermissions(attachmentChoice,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_VIDEO)) {
                return;
            }
        } else if (attachmentChoice == ATTACHMENT_CHOICE_TAKE_PHOTO) {
            if (!hasPermissions(attachmentChoice, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA) && !Compatibility.runsThirtyThree()) {
                return;
            } else if (Compatibility.runsThirtyThree() && !hasPermissions(attachmentChoice,
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_VIDEO)) {
                return;
            }
        } else if (attachmentChoice == ATTACHMENT_CHOICE_RECORD_VIDEO) {
            if (!hasPermissions(attachmentChoice, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO) && !Compatibility.runsThirtyThree()) {
                return;
            } else if (Compatibility.runsThirtyThree() && !hasPermissions(attachmentChoice,
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_VIDEO)) {
                return;
            }
        } else if (attachmentChoice == ATTACHMENT_CHOICE_LOCATION) {
            if (!hasPermissions(attachmentChoice, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                return;
            }
        } else if ((attachmentChoice == ATTACHMENT_CHOICE_CHOOSE_FILE || attachmentChoice == ATTACHMENT_CHOICE_CHOOSE_IMAGE || attachmentChoice == ATTACHMENT_CHOICE_CHOOSE_VIDEO) && !Compatibility.runsThirtyThree()) {
            if (!hasPermissions(attachmentChoice, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE) && !Compatibility.runsThirtyThree()) {
                return;
            } else if (Compatibility.runsThirtyThree() && !hasPermissions(attachmentChoice,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_VIDEO)) {
                return;
            }
        }
        if (updateRecentlyUsed) {
            storeRecentlyUsedQuickAction(attachmentChoice);
        }
        final int encryption = conversation.getNextEncryption();
        final int mode = conversation.getMode();
        if (encryption == Message.ENCRYPTION_PGP) {
            if (activity.hasPgp()) {
                if (mode == Conversation.MODE_SINGLE && conversation.getContact().getPgpKeyId() != 0) {
                    activity.xmppConnectionService.getPgpEngine().hasKey(
                            conversation.getContact(),
                            new UiCallback<Contact>() {

                                @Override
                                public void userInputRequired(PendingIntent pi, Contact contact) {
                                    startPendingIntent(pi, attachmentChoice);
                                }

                                @Override
                                public void progress(int progress) {

                                }

                                @Override
                                public void success(Contact contact) {
                                    invokeAttachFileIntent(attachmentChoice);
                                }

                                @Override
                                public void showToast() {
                                }

                                @Override
                                public void error(int error, Contact contact) {
                                    activity.replaceToast(getString(error));
                                }
                            });
                } else if (mode == Conversation.MODE_MULTI && conversation.getMucOptions().pgpKeysInUse()) {
                    if (!conversation.getMucOptions().everybodyHasKeys()) {
                        getActivity().runOnUiThread(() -> {
                            Toast warning = ToastCompat.makeText(activity, R.string.missing_public_keys, ToastCompat.LENGTH_LONG);
                            warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                            warning.show();
                        });
                    }
                    invokeAttachFileIntent(attachmentChoice);
                } else {
                    showNoPGPKeyDialog(false, (dialog, which) -> {
                        conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                        activity.xmppConnectionService.updateConversation(conversation);
                        invokeAttachFileIntent(attachmentChoice);
                    });
                }
            } else {
                activity.showInstallPgpDialog();
            }
        } else {
            invokeAttachFileIntent(attachmentChoice);
        }
    }

    private void storeRecentlyUsedQuickAction(final int attachmentChoice) {
        try {
            activity.getPreferences().edit()
                    .putString(RECENTLY_USED_QUICK_ACTION, SendButtonAction.of(attachmentChoice).toString())
                    .apply();
        } catch (IllegalArgumentException e) {
            //just do not save
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        final PermissionUtils.PermissionResult permissionResult =
                PermissionUtils.removeBluetoothConnect(permissions, grantResults);
        if (grantResults.length > 0) {
            if (allGranted(permissionResult.grantResults)) {
                Activity mXmppActivity = getActivity();
                if (requestCode == REQUEST_START_DOWNLOAD) {
                    if (this.mPendingDownloadableMessage != null) {
                        startDownloadable(this.mPendingDownloadableMessage);
                    }
                } else if (requestCode == REQUEST_ADD_EDITOR_CONTENT) {
                    if (this.mPendingEditorContent != null) {
                        attachEditorContentToConversation(this.mPendingEditorContent);
                    }
                } else if (requestCode == REQUEST_COMMIT_ATTACHMENTS) {
                    commitAttachments();
                } else if (requestCode == REQUEST_START_AUDIO_CALL) {
                    if (mXmppActivity instanceof XmppActivity) {
                        CallManager.triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VOICE_CALL, (XmppActivity) mXmppActivity, conversation);
                    }
                } else if (requestCode == REQUEST_START_VIDEO_CALL) {
                    if (mXmppActivity instanceof XmppActivity) {
                        CallManager.triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VIDEO_CALL, (XmppActivity) mXmppActivity, conversation);
                    }
                } else {
                    attachFile(requestCode);
                }
            } else {
                @StringRes int res;
                String firstDenied = getFirstDenied(grantResults, permissions);
                if (Manifest.permission.RECORD_AUDIO.equals(firstDenied)) {
                    res = R.string.no_microphone_permission;
                } else if (Manifest.permission.CAMERA.equals(firstDenied)) {
                    res = R.string.no_camera_permission;
                } else if (Manifest.permission.ACCESS_COARSE_LOCATION.equals(firstDenied)
                        || Manifest.permission.ACCESS_FINE_LOCATION.equals(firstDenied)) {
                    res = R.string.no_location_permission;
                } else if (Manifest.permission.READ_MEDIA_IMAGES.equals(firstDenied)) {
                    res = R.string.no_media_permission;
                } else if (Manifest.permission.READ_MEDIA_AUDIO.equals(firstDenied)) {
                    res = R.string.no_media_permission;
                } else if (Manifest.permission.READ_MEDIA_VIDEO.equals(firstDenied)) {
                    res = R.string.no_media_permission;
                } else if (!Compatibility.runsThirtyThree() && Manifest.permission.READ_EXTERNAL_STORAGE.equals(firstDenied) || !Compatibility.runsThirtyThree() && Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(firstDenied)) {
                    res = R.string.no_storage_permission;
                } else {
                    res = R.string.error;
                }
                if (Compatibility.runsThirtyThree()){          //TODO: Actually not needed, check this later again
                } else {
                    ToastCompat.makeText(getActivity(), res, ToastCompat.LENGTH_SHORT).show();
                }
            }
            ChatBackgroundHelper.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
        }
        if (readGranted(grantResults, permissions)) {
            if (activity != null && activity.xmppConnectionService != null) {
                activity.xmppConnectionService.getDrawableCache().evictAll();
                activity.xmppConnectionService.restartFileObserver();
            }
            refresh();
        }
        if (cameraGranted(grantResults, permissions) || audioGranted(grantResults, permissions)) {
            XmppConnectionService.toggleForegroundService(activity);
        }
    }

    private void updateChatBG() {
        if (activity != null && conversation != null) {
            if (activity.unicoloredBG()) {
                binding.conversationsFragment.setBackgroundResource(0);
                binding.conversationsFragment.setBackgroundColor(StyledAttributes.getColor(activity, R.attr.color_background_tertiary));
            } else {
                if (Compatibility.runsThirtyThree()
                    && ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    || !Compatibility.runsThirtyThree()
                    && ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    Uri uri = ChatBackgroundHelper.getBgUri(activity, conversation.getUuid());
                    if (uri != null) {
                        binding.backgroundImage.setImageURI(uri);
                        binding.backgroundImage.setVisibility(View.VISIBLE);
                    } else {
                        binding.conversationsFragment.setBackground(ContextCompat.getDrawable(activity, R.drawable.chatbg));
                    }
                } else {
                    binding.conversationsFragment.setBackground(ContextCompat.getDrawable(activity, R.drawable.chatbg));
                }
            }
        }
    }

    public void startDownloadable(Message message) {
        if (!hasPermissions(REQUEST_START_DOWNLOAD, Manifest.permission.WRITE_EXTERNAL_STORAGE) && !hasPermissions(REQUEST_START_DOWNLOAD, Manifest.permission.READ_EXTERNAL_STORAGE) && !Compatibility.runsThirtyThree()) {
            this.mPendingDownloadableMessage = message;
            return;
        } else if (Compatibility.runsThirtyThree() && !hasPermissions(REQUEST_START_DOWNLOAD,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO)) {
            this.mPendingDownloadableMessage = message;
            return;
        }
        Transferable transferable = message.getTransferable();
        if (transferable != null) {
            if (transferable instanceof TransferablePlaceholder && message.hasFileOnRemoteHost()) {
                createNewConnection(message);
                return;
            }
            if (!transferable.start()) {
                Log.d(Config.LOGTAG, "type: " + transferable.getClass().getName());
                ToastCompat.makeText(getActivity(), R.string.not_connected_try_again, ToastCompat.LENGTH_SHORT).show();
            }
        } else if (message.treatAsDownloadable() || message.hasFileOnRemoteHost() || MessageUtils.unInitiatedButKnownSize(message)) {
            createNewConnection(message);
        } else {
            Log.d(Config.LOGTAG, message.getConversation().getAccount() + ": unable to start downloadable");
        }
    }

    private void createNewConnection(final Message message) {
        if (!activity.xmppConnectionService.hasInternetConnection()) {
            ToastCompat.makeText(getActivity(), R.string.not_connected_try_again, ToastCompat.LENGTH_SHORT).show();
            return;
        }
        if (message.getOob() != null && "cid".equalsIgnoreCase(message.getOob().getScheme())) {
            try {
                BobTransfer transfer = new BobTransfer.ForMessage(message, activity.xmppConnectionService);
                message.setTransferable(transfer);
                transfer.start();
            } catch (URISyntaxException e) {
                Log.d(Config.LOGTAG, "BobTransfer failed to parse URI");
            }
        } else {
            activity.xmppConnectionService
                    .getHttpConnectionManager()
                    .createNewDownloadConnection(message, true);
        }
    }

    private OnClickListener OTRwarning = new OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                final Uri uri = Uri.parse("https://monocles.wiki/index.php?title=Monocles_Chat");
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(browserIntent);
            } catch (Exception e) {
                ToastCompat.makeText(activity, R.string.no_application_found_to_open_link, Toast.LENGTH_SHORT).show();
            }
        }
    };


    @SuppressLint("InflateParams")
    protected void clearHistoryDialog(final Conversation conversation) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle(getString(R.string.clear_conversation_history));
        final View dialogView = requireActivity().getLayoutInflater().inflate(R.layout.dialog_clear_history, null);
        final MaterialSwitch endConversationCheckBox = dialogView.findViewById(R.id.end_conversation_checkbox);
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            endConversationCheckBox.setVisibility(View.VISIBLE);
            endConversationCheckBox.setChecked(true);
        }
        builder.setView(dialogView);
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.confirm), (dialog, which) -> {
            this.activity.xmppConnectionService.clearConversationHistory(conversation);
            if (endConversationCheckBox.isChecked()) {
                this.activity.xmppConnectionService.archiveConversation(conversation);
            } else {
                activity.onConversationsListItemUpdated();
                refresh();
            }
        });
        builder.create().show();
    }
    protected void muteConversationDialog(final Conversation conversation) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.disable_notifications);
        final int[] durations = activity.getResources().getIntArray(R.array.mute_options_durations);
        final CharSequence[] labels = new CharSequence[durations.length];
        for (int i = 0; i < durations.length; ++i) {
            if (durations[i] == -1) {
                labels[i] = activity.getString(R.string.until_further_notice);
            } else {
                labels[i] = TimeFrameUtils.resolve(activity, 1000L * durations[i]);
            }
        }
        builder.setItems(labels, (dialog, which) -> {
            final long till;
            if (durations[which] == -1) {
                till = Long.MAX_VALUE;
            } else {
                till = System.currentTimeMillis() + (durations[which] * 1000L);
            }
            conversation.setMutedTill(till);
            activity.xmppConnectionService.updateConversation(conversation);
            activity.onConversationsListItemUpdated();
            refresh();
            activity.invalidateOptionsMenu();
        });
        builder.create().show();
    }

    private boolean hasPermissions(int requestCode, List<String> permissions) {
        final List<String> missingPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || Config.ONLY_INTERNAL_STORAGE) && permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                continue;
            }
            if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (missingPermissions.isEmpty()) {
            return true;
        } else {
            requestPermissions(
                    missingPermissions.toArray(new String[0]),
                    requestCode);
            return false;
        }
    }

    private boolean hasPermissions(int requestCode, String... permissions) {
        return hasPermissions(requestCode, ImmutableList.copyOf(permissions));
    }
    public void unmuteConversation(final Conversation conversation) {
        conversation.setMutedTill(0);
        this.activity.xmppConnectionService.updateConversation(conversation);
        this.activity.onConversationsListItemUpdated();
        refresh();
        this.activity.invalidateOptionsMenu();
    }
    protected void invokeAttachFileIntent(final int attachmentChoice) {
        Intent intent = new Intent();
        boolean chooser = false;
        if (attachmentChoice == ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
            intent.setAction(Intent.ACTION_GET_CONTENT);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.setType("image/*");
            chooser = true;
        } else if (attachmentChoice == ATTACHMENT_CHOICE_CHOOSE_VIDEO) {
            chooser = true;
            intent.setType("video/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setAction(Intent.ACTION_GET_CONTENT);
        } else if (attachmentChoice == ATTACHMENT_CHOICE_RECORD_VIDEO) {
            if (Compatibility.runsThirty()) {
                final List<CameraUtils> cameraApps = CameraUtils.getCameraApps(activity);
                if (cameraApps.size() == 0) {
                    ToastCompat.makeText(activity, R.string.no_application_found, ToastCompat.LENGTH_LONG).show();
                } else if (cameraApps.size() == 1) {
                    getCameraApp(cameraApps.get(0));
                } else {
                    if (!activity.getPreferences().contains(SettingsActivity.CAMERA_CHOICE)) {
                        showCameraChooser(activity, cameraApps);
                    } else {
                        intent.setComponent(getCameraApp(cameraApps.get(activity.getPreferences().getInt(SettingsActivity.CAMERA_CHOICE, 0))));
                    }
                }
            }
            intent.setAction(MediaStore.ACTION_VIDEO_CAPTURE);
        } else if (attachmentChoice == ATTACHMENT_CHOICE_TAKE_PHOTO) {
            final Uri photoUri = activity.xmppConnectionService.getFileBackend().getTakePhotoUri();
            pendingTakePhotoUri.push(photoUri);
            if (Compatibility.runsThirty()) {
                final List<CameraUtils> cameraApps = CameraUtils.getCameraApps(activity);
                if (cameraApps.size() == 0) {
                    ToastCompat.makeText(activity, R.string.no_application_found, ToastCompat.LENGTH_LONG).show();
                } else if (cameraApps.size() == 1) {
                    getCameraApp(cameraApps.get(0));
                } else {
                    if (!activity.getPreferences().contains(SettingsActivity.CAMERA_CHOICE)) {
                        showCameraChooser(activity, cameraApps);
                    } else {
                        intent.setComponent(getCameraApp(cameraApps.get(activity.getPreferences().getInt(SettingsActivity.CAMERA_CHOICE, 0))));
                    }
                }
            }
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            intent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION & Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
        } else if (attachmentChoice == ATTACHMENT_CHOICE_CHOOSE_FILE) {
            chooser = true;
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setAction(Intent.ACTION_GET_CONTENT);
        } else if (attachmentChoice == ATTACHMENT_CHOICE_RECORD_VOICE) {
            backPressedLeaveVoiceRecorder.setEnabled(true);
            recordVoice();
        } else if (attachmentChoice == ATTACHMENT_CHOICE_LOCATION) {
            intent = GeoHelper.getFetchIntent(activity);
        }
        final Context context = getActivity();
        if (context == null) {
            return;
        }
        try {
            Log.d(Config.LOGTAG, "Attachment: " + attachmentChoice);
            if (chooser) {
                startActivityForResult(
                        Intent.createChooser(intent, getString(R.string.perform_action_with)),
                        attachmentChoice);
                activity.overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
            } else {
                startActivityForResult(intent, attachmentChoice);
                activity.overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
            }
        } catch (final ActivityNotFoundException e) {

            if (attachmentChoice == ATTACHMENT_CHOICE_RECORD_VIDEO
                    || attachmentChoice == ATTACHMENT_CHOICE_TAKE_PHOTO
                    || attachmentChoice == ATTACHMENT_CHOICE_CHOOSE_FILE
                    || attachmentChoice == ATTACHMENT_CHOICE_CHOOSE_IMAGE
                    || attachmentChoice == ATTACHMENT_CHOICE_CHOOSE_VIDEO){
                ToastCompat.makeText(context, R.string.no_application_found, ToastCompat.LENGTH_LONG).show();
            }
        }
    }


    public void recordVoice() {
        this.binding.recordingVoiceActivity.setVisibility(View.VISIBLE);
        this.binding.recordVoiceButton.setEnabled(false);
        if (!startRecording()) {
            this.binding.shareButton.setEnabled(false);
            this.binding.timer.setTextAppearance(activity, R.style.TextAppearance_Conversations_Title);
            this.binding.timer.setText(R.string.unable_to_start_recording);
        }
    }

    private static final Set<String> AAC_SENSITIVE_DEVICES =
            new ImmutableSet.Builder<String>()
                    .add("FP4")             // Fairphone 4 https://codeberg.org/monocles/monocles_chat/issues/133
                    .add("ONEPLUS A6000")   // OnePlus 6 https://github.com/iNPUTmice/Conversations/issues/4329
                    .add("ONEPLUS A6003")   // OnePlus 6 https://github.com/iNPUTmice/Conversations/issues/4329
                    .add("ONEPLUS A6010")   // OnePlus 6T https://codeberg.org/monocles/monocles_chat/issues/133
                    .add("ONEPLUS A6013")   // OnePlus 6T https://codeberg.org/monocles/monocles_chat/issues/133
                    .add("Pixel 4a")        // Pixel 4a https://github.com/iNPUTmice/Conversations/issues/4223
                    .add("WP12 Pro")        // Oukitel WP 12 Pro https://github.com/iNPUTmice/Conversations/issues/4223
                    .add("Volla Phone X")   // Volla Phone X https://github.com/iNPUTmice/Conversations/issues/4223
                    .build();

    private boolean startRecording() {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mRecorder.setPreferredMicrophoneDirection(MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mRecorder.setPrivacySensitive(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && activity.xmppConnectionService.getBooleanPreference("alternative_voice_settings", R.bool.alternative_voice_settings)) {
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.WEBM);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS);
            mRecorder.setAudioEncodingBitRate(96_000);
            mRecorder.setAudioSamplingRate(48_000);
        } else {
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            if (AAC_SENSITIVE_DEVICES.contains(Build.MODEL) && Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
                // Changing these three settings for AAC sensitive devices using Android<=13 might lead to sporadically truncated (cut-off) voice messages.
                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC);
                mRecorder.setAudioSamplingRate(24_000);
                mRecorder.setAudioEncodingBitRate(28_000);
            } else {
                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mRecorder.setAudioSamplingRate(44_100);
                mRecorder.setAudioEncodingBitRate(64_000);
            }
        }
        setupOutputFile();
        mRecorder.setOutputFile(mOutputFile.getAbsolutePath());
        binding.timer.clearAnimation();
        try {
            mRecorder.prepare();
            mRecorder.start();
            recording = true;
            //mStartTime = SystemClock.elapsedRealtime();
            mHandler.postDelayed(mTickExecutor, 0);
            Log.d("Voice Recorder", "started recording to " + mOutputFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.e("Voice Recorder", "prepare() failed " + e.getMessage());
            return false;
        }
    }

    protected void stopRecording() {
        try {
            mRecorder.stop();
            mRecorder.release();
            recording = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
        binding.recordVoiceButton.setEnabled(true);
        binding.shareButton.setEnabled(true);
    }

    private void StartTimerAnimation() {
        Animation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(500); //You can manage the blinking time with this parameter
        anim.setStartOffset(20);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        binding.timer.startAnimation(anim);
    }

    protected void pauseRecording() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mRecorder.pause();
                mHandler.removeCallbacks(mTickExecutor);
                recording = false;
                StartTimerAnimation();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void resumeRecording() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mRecorder.resume();
                mHandler.postDelayed(mTickExecutor, 0);
                binding.timer.clearAnimation();
            }
            recording = true;
            Log.e("Voice Recorder", "resume recording");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void stopRecording(final boolean saveFile) {
        resumeRecording();
        try {
            if (recording) {
                stopRecording();
            }
        } catch (Exception e) {
            if (saveFile) {
                ToastCompat.makeText(activity, R.string.unable_to_save_recording, ToastCompat.LENGTH_SHORT).show();
                return;
            }
        } finally {
            mRecorder = null;
            mStartTime = 0;
            mHandler.removeCallbacks(mTickExecutor);
        }
        if (!saveFile && mOutputFile != null) {
            if (mOutputFile.delete()) {
                Log.d(Config.LOGTAG, "deleted canceled recording");
            }
        }
        if (saveFile) {
            new Thread(new Finisher(outputFileWrittenLatch, mOutputFile, activity)).start();
        }
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        activity.setRequestedOrientation(oldOrientation);
        binding.recordVoiceButton.setEnabled(true);
        binding.shareButton.setEnabled(true);
    }

    private class Finisher implements Runnable {

        private final CountDownLatch latch;
        private final File outputFile;
        private final WeakReference<Activity> activityReference;

        private Finisher(CountDownLatch latch, File outputFile, Activity activity) {
            this.latch = latch;
            this.outputFile = outputFile;
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        public void run() {
            if (activity.xmppConnectionService.getBooleanPreference("alternative_voice_settings", R.bool.alternative_voice_settings)) {
                try {
                    if (!latch.await(8, TimeUnit.SECONDS)) {
                        Log.d(Config.LOGTAG, "time out waiting for output file to be written");
                    }
                } catch (InterruptedException e) {
                    Log.d(Config.LOGTAG, "interrupted while waiting for output file to be written", e);
                }
                final Activity activity = activityReference.get();
                if (activity == null) {
                    return;
                }
                activity.runOnUiThread(
                        () -> {
                            activity.setResult(
                                    Activity.RESULT_OK, new Intent().setData(Uri.fromFile(outputFile)));
                            //mediaPreviewAdapter.addMediaPreviews(Attachment.of(getActivity(), Uri.fromFile(outputFile), Attachment.Type.RECORDING));
                            //toggleInputMethod();
                            attachFileToConversation(conversation, Uri.fromFile(outputFile), "audio/webm;codecs=opus");
                            binding.recordingVoiceActivity.setVisibility(View.GONE);
                        });
            } else {
                try {
                    if (!latch.await(8, TimeUnit.SECONDS)) {
                        Log.d(Config.LOGTAG, "time out waiting for output file to be written");
                    }
                } catch (InterruptedException e) {
                    Log.d(Config.LOGTAG, "interrupted while waiting for output file to be written", e);
                }
                final Activity activity = activityReference.get();
                if (activity == null) {
                    return;
                }
                activity.runOnUiThread(
                        () -> {
                            activity.setResult(
                                    Activity.RESULT_OK, new Intent().setData(Uri.fromFile(outputFile)));
                            //mediaPreviewAdapter.addMediaPreviews(Attachment.of(getActivity(), Uri.fromFile(outputFile), Attachment.Type.RECORDING));
                            //toggleInputMethod();
                            attachFileToConversation(conversation, Uri.fromFile(outputFile), "audio/mp4");
                            binding.recordingVoiceActivity.setVisibility(View.GONE);
                        });
            }
        }
    }

    private static File generateOutputFilename(Context context) {
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US);
        if (activity.xmppConnectionService.getBooleanPreference("alternative_voice_settings", R.bool.alternative_voice_settings)) {
            return new File(getConversationsDirectory(context, SENT_AUDIOS)
                    + dateFormat.format(new Date())
                    + ".opus");
        } else {
            return new File(getConversationsDirectory(context, SENT_AUDIOS)
                    + dateFormat.format(new Date())
                    + ".m4a");
        }
    }

    private void setupOutputFile() {
        mOutputFile = generateOutputFilename(activity);
        final File parentDirectory = mOutputFile.getParentFile();
        if (parentDirectory.mkdirs()) {
            Log.d(Config.LOGTAG, "created " + parentDirectory.getAbsolutePath());
        }
        final File noMedia = new File(parentDirectory, ".nomedia");
        if (!noMedia.exists()) {
            try {
                if (noMedia.createNewFile()) {
                    Log.d(Config.LOGTAG, "created nomedia file in " + parentDirectory.getAbsolutePath());
                }
            } catch (IOException e) {
                Log.d(Config.LOGTAG, "unable to create nomedia file in " + parentDirectory.getAbsolutePath(), e);
            }
        }
        setupFileObserver(parentDirectory);
    }

    private void setupFileObserver(final File directory) {
        outputFileWrittenLatch = new CountDownLatch(1);
        mFileObserver = new FileObserver(directory.getAbsolutePath()) {
            @Override
            public void onEvent(int event, String s) {
                if (s != null && s.equals(mOutputFile.getName()) && event == FileObserver.CLOSE_WRITE) {
                    outputFileWrittenLatch.countDown();
                }
            }
        };
        mFileObserver.startWatching();
    }

    private void tick() {
        //this.binding.timer.setText(TimeFrameUtils.formatTimePassed(mStartTime, true));
        int minutes = (mStartTime % 3600) / 60;
        int seconds = mStartTime % 60;

        // Format the seconds into hours, minutes,
        // and seconds.
        String time
                = String
                .format(Locale.getDefault(),
                        "%02d:%02d", minutes,
                        seconds);

        // Set the text view text.
        this.binding.timer.setText(time);

        // If running is true, increment the
        // seconds variable.
        if (recording) {
            mStartTime++;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        disableEncrpytionForExceptions();
        binding.messagesView.post(this::fireReadEvent);
        updateChatBG();
    }

    private void disableEncrpytionForExceptions() {
        if (isEncryptionDisabledException()) {
            disableMessageEncryption();
        }
    }

    private boolean isEncryptionDisabledException() {
        if (conversation != null) {
            return ENCRYPTION_EXCEPTIONS.contains(conversation.getJid().toString());
        }
        return false;
    }
    private void fireReadEvent() {
        if (activity != null && this.conversation != null) {
            String uuid = getLastVisibleMessageUuid();
            if (uuid != null) {
                activity.onConversationRead(this.conversation, uuid);
            }
        }
    }

    private void newSubThread() {
        Element oldThread = conversation.getThread();
        Element thread = new Element("thread", "jabber:client");
        thread.setContent(UUID.randomUUID().toString());
        if (oldThread != null) thread.setAttribute("parent", oldThread.getContent());
    }

    private void newThread() {
        Element thread = new Element("thread", "jabber:client");
        thread.setContent(UUID.randomUUID().toString());
    }

    private void updateThreadFromLastMessage() {
        if (this.conversation != null && !this.conversation.getUserSelectedThread() && TextUtils.isEmpty(binding.textinput.getText())) {
            Message message = getLastVisibleMessage();
            if (message == null) {
                newThread();
            } else {
                if (conversation.getMode() == Conversation.MODE_MULTI) {
                    if (activity == null || activity.xmppConnectionService == null) return;
                    if (message.getStatus() < Message.STATUS_SEND) {
                    }
                }
            }
        }
    }

    private String getLastVisibleMessageUuid() {
        Message message =  getLastVisibleMessage();
        return message == null ? null : message.getUuid();
    }

    private Message getLastVisibleMessage() {
        if (binding == null) {
            return null;
        }
        synchronized (this.messageList) {
            int pos = binding.messagesView.getLastVisiblePosition();
            if (pos >= 0) {
                Message message = null;
                for (int i = pos; i >= 0; --i) {
                    try {
                        message = (Message) binding.messagesView.getItemAtPosition(i);
                    } catch (IndexOutOfBoundsException e) {
                        // should not happen if we synchronize properly. however if that fails we
                        // just gonna try item -1
                        continue;
                    }
                    if (message.getType() != Message.TYPE_STATUS) {
                        break;
                    }
                }
                if (message != null) {
                    while (message.next() != null && message.next().wasMergedIntoPrevious(activity == null ? null : activity.xmppConnectionService)) {
                        message = message.next();
                    }
                    return message;
                }
            }
        }
        return null;
    }

    public void jumpTo(final Message message) {
        if (message.getUuid() == null) return;
        for (int i = 0; i < messageList.size(); i++) {
            final var m = messageList.get(i);
            if (m == null) continue;
            if (message.getUuid().equals(m.getUuid())) {
                binding.messagesView.setSelection(i);
                return;
            }
        }
    }

    private void openWith(final Message message) {
        if (message.isGeoUri()) {
            GeoHelper.view(getActivity(), message);
        } else {
            final DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
            ViewUtil.view(activity, file);
        }
    }

    private void reportMessage(final Message message) {
        BlockContactDialog.show(activity, conversation.getContact(), message.getServerMsgId());
    }

    private void showErrorMessage(final Message message) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle(R.string.error_message);
        final String errorMessage = message.getErrorMessage();
        final String[] errorMessageParts = errorMessage == null ? new String[0] : errorMessage.split("\\u001f");
        final String displayError;
        if (errorMessageParts.length == 2) {
            displayError = errorMessageParts[1];
        } else {
            displayError = errorMessage;
        }
        builder.setMessage(displayError);
        builder.setNegativeButton(R.string.copy_to_clipboard, (dialog, which) -> {
            activity.copyTextToClipboard(displayError, R.string.error_message);
            ToastCompat.makeText(activity, R.string.error_message_copied_to_clipboard, ToastCompat.LENGTH_SHORT).show();
        });
        builder.setPositiveButton(R.string.ok, null);
        builder.create().show();
    }

    private void deleteMessage(final Message message) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setNegativeButton(R.string.cancel, null);
        builder.setTitle(R.string.delete_message_dialog);
        builder.setMessage(R.string.delete_message_dialog_msg);

        final Message finalMessage = message;

        builder.setPositiveButton(R.string.confirm, (dialog, which) -> {

            if (finalMessage.getType() == Message.TYPE_TEXT
                    && !finalMessage.isGeoUri()
                    && finalMessage.getConversation() instanceof Conversation) {

                Message retractedMessage = finalMessage;
                retractedMessage.setMessageDeleted(true);

                long time = System.currentTimeMillis();
                Message retractmessage = new Message(conversation,
                        "This person attempted to retract a previous message, but it's unsupported by your client.",
                        Message.ENCRYPTION_NONE,
                        Message.STATUS_SEND);
                if (retractedMessage.getEditedList().size() > 0) {
                    retractmessage.setRetractId(retractedMessage.getEditedList().get(0).getEditedId());
                } else {
                    retractmessage.setRetractId(retractedMessage.getRemoteMsgId() != null ? retractedMessage.getRemoteMsgId() : retractedMessage.getUuid());
                }

                retractedMessage.putEdited(retractedMessage.getUuid(), retractedMessage.getServerMsgId(), retractedMessage.getBody(), retractedMessage.getTimeSent());
                retractedMessage.setBody(Message.DELETED_MESSAGE_BODY);
                retractedMessage.setServerMsgId(null);
                retractedMessage.setRemoteMsgId(message.getRemoteMsgId());
                retractedMessage.setMessageDeleted(true);

                retractmessage.setType(Message.TYPE_TEXT);
                retractmessage.setCounterpart(message.getCounterpart());
                retractmessage.setTrueCounterpart(message.getTrueCounterpart());
                retractmessage.setTime(time);
                retractmessage.setUuid(UUID.randomUUID().toString());
                retractmessage.setCarbon(false);
                retractmessage.setRemoteMsgId(retractmessage.getUuid());
                retractmessage.setMessageDeleted(true);
                retractedMessage.setTime(time); //set new time here to keep orginal timestamps
                for (Edit itm : retractedMessage.getEditedList()) {
                    Message tmpRetractedMessage = conversation.findMessageWithUuidOrRemoteId(itm.getEditedId());
                    if (tmpRetractedMessage != null) {
                        tmpRetractedMessage.setMessageDeleted(true);
                        activity.xmppConnectionService.updateMessage(tmpRetractedMessage, tmpRetractedMessage.getUuid());
                    }
                }
                activity.xmppConnectionService.updateMessage(retractedMessage, retractedMessage.getUuid());
                if (finalMessage.getStatus() >= Message.STATUS_SEND) {
                    //only send retraction messages vor outgoing messages!
                    sendMessage(retractmessage);
                }
                activity.xmppConnectionService.deleteMessage(conversation, retractedMessage);
                activity.xmppConnectionService.deleteMessage(conversation, retractmessage);
            }
            activity.xmppConnectionService.deleteMessage(conversation, message);
            activity.onConversationsListItemUpdated();
            refresh();
        });
        builder.create().show();
    }

    public boolean onInlineImageLongClicked(Cid cid) {
        DownloadableFile f = activity.xmppConnectionService.getFileForCid(cid);
        if (f == null) return false;

        saveAsSticker(f, null);
        return true;
    }

    private void saveAsSticker(final Message m) {
        String existingName = m.getFileParams() != null && m.getFileParams().getName() != null ? m.getFileParams().getName() : "";
        existingName = existingName.lastIndexOf(".") == -1 ? existingName : existingName.substring(0, existingName.lastIndexOf("."));
        saveAsSticker(activity.xmppConnectionService.getFileBackend().getFile(m), existingName);
    }

    private void saveAsSticker(final File file, final String name) {
        savingAsSticker = file;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(MimeUtils.guessMimeTypeFromUri(activity, activity.xmppConnectionService.getFileBackend().getUriForFile(activity, file)));
        intent.putExtra(Intent.EXTRA_TITLE, name);

        final String dir = "Stickers";
        if (dir.startsWith("content://")) {
            intent.putExtra("android.provider.extra.INITIAL_URI", Uri.parse(dir));
        } else {
            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + APP_DIRECTORY + File.separator + "Stickers").mkdirs();
            Uri uri;
            if (Build.VERSION.SDK_INT >= 29) {
                Intent tmp = ((StorageManager) activity.getSystemService(Context.STORAGE_SERVICE)).getPrimaryStorageVolume().createOpenDocumentTreeIntent();
                uri = tmp.getParcelableExtra("android.provider.extra.INITIAL_URI");
                if (uri != null) {
                    uri = Uri.parse(uri.toString().replace("/root/", "/document/") + "%3ADocuments%2F" + "monocles mod%2F" + dir);
                }
            } else {
                uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments%2F" + "monocles mod%2F" + dir);
            }
            intent.putExtra("android.provider.extra.INITIAL_URI", uri);
            intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        }

        Toast.makeText(activity, R.string.choose_sticker_name, Toast.LENGTH_SHORT).show();
        startActivityForResult(Intent.createChooser(intent, "Choose sticker name"), REQUEST_SAVE_STICKER);
    }

    private void saveAsGif(final Message m) {
        String existingName = m.getFileParams() != null && m.getFileParams().getName() != null ? m.getFileParams().getName() : "";
        existingName = existingName.lastIndexOf(".") == -1 ? existingName : existingName.substring(0, existingName.lastIndexOf("."));
        saveAsGif(activity.xmppConnectionService.getFileBackend().getFile(m), existingName);
    }

    private void saveAsGif(final File file, final String name) {
        savingAsGif = file;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(MimeUtils.guessMimeTypeFromUri(activity, activity.xmppConnectionService.getFileBackend().getUriForFile(activity, file)));
        intent.putExtra(Intent.EXTRA_TITLE, name);

        final String dir = "GIFs";
        if (dir.startsWith("content://")) {
            intent.putExtra("android.provider.extra.INITIAL_URI", Uri.parse(dir));
        } else {
            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + APP_DIRECTORY + File.separator + "Stickers").mkdirs();
            Uri uri;
            if (Build.VERSION.SDK_INT >= 29) {
                Intent tmp = ((StorageManager) activity.getSystemService(Context.STORAGE_SERVICE)).getPrimaryStorageVolume().createOpenDocumentTreeIntent();
                uri = tmp.getParcelableExtra("android.provider.extra.INITIAL_URI");
                if (uri != null) {
                    uri = Uri.parse(uri.toString().replace("/root/", "/document/") + "%3ADocuments%2F" + "monocles mod%2F" + dir);
                }
            } else {
                uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments%2F" + "monocles mod%2F" + dir);
            }
            intent.putExtra("android.provider.extra.INITIAL_URI", uri);
            intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        }

        Toast.makeText(activity, R.string.choose_gif_name, Toast.LENGTH_SHORT).show();
        startActivityForResult(Intent.createChooser(intent, "Choose sticker name"), REQUEST_SAVE_GIF);
    }

    private void deleteFile(final Message message) {
        boolean prefConfirm = activity.xmppConnectionService.getBooleanPreference("confirm_delete_attachment", R.bool.confirm_delete_attachment);
        if(!prefConfirm) {
            deleteMessageFile(message);
            return;
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setNegativeButton(R.string.cancel, null);
        builder.setTitle(R.string.delete_file_dialog);
        builder.setMessage(R.string.delete_file_dialog_msg);
        builder.setPositiveButton(R.string.confirm, (dialog, which) -> {
            deleteMessageFile(message);
        });
        builder.create().show();
    }

    public void resendMessage(final Message message) {
        if (message != null && message.isFileOrImage()) {
            if (!(message.getConversation() instanceof Conversation)) {
                return;
            }
            final Conversation conversation = (Conversation) message.getConversation();
            final DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
            if ((file.exists() && file.canRead()) || message.hasFileOnRemoteHost()) {
                final XmppConnection xmppConnection = conversation.getAccount().getXmppConnection();
                if (!message.hasFileOnRemoteHost()
                        && xmppConnection != null
                        && conversation.getMode() == Conversational.MODE_SINGLE
                        && !xmppConnection.getFeatures().httpUpload(message.getFileParams().getSize())) {
                    activity.selectPresence(conversation, () -> {
                        message.setCounterpart(conversation.getNextCounterpart());
                        activity.xmppConnectionService.resendFailedMessages(message);
                        new Handler().post(() -> {
                            int size = messageList.size();
                            this.binding.messagesView.setSelection(size - 1);
                        });
                    });
                    return;
                }
            } else if (!Compatibility.hasStoragePermission(getActivity())) {
                ToastCompat.makeText(activity, R.string.no_storage_permission, ToastCompat.LENGTH_SHORT).show();
                return;
            } else {
                ToastCompat.makeText(activity, R.string.file_deleted, ToastCompat.LENGTH_SHORT).show();
                message.setFileDeleted(true);
                activity.xmppConnectionService.updateMessage(message, false);
                activity.onConversationsListItemUpdated();
                refresh();
                return;
            }
        }
        activity.xmppConnectionService.resendFailedMessages(message);
        new Handler().post(() -> {
            int size = messageList.size();
            this.binding.messagesView.setSelection(size - 1);
        });
    }

    private void copyUrl(Message message) {
        final String url;
        final int resId;
        if (message.isGeoUri()) {
            resId = R.string.location;
            url = message.getBody();
        } else if (message.isXmppUri()) {
            resId = R.string.contact;
            url = message.getBody();
        } else if (message.hasFileOnRemoteHost()) {
            resId = R.string.file_url;
            url = message.getFileParams().url.toString();
        } else {
            url = message.getBody().trim();
            resId = R.string.file_url;
        }
        if (activity.copyTextToClipboard(url, resId)) {
            ToastCompat.makeText(getActivity(), R.string.url_copied_to_clipboard,
                    ToastCompat.LENGTH_SHORT).show();
        }
    }

    public void cancelTransmission(Message message) {
        Transferable transferable = message.getTransferable();
        if (transferable != null) {
            transferable.cancel();
        } else if (message.getStatus() != Message.STATUS_RECEIVED) {
            activity.xmppConnectionService.markMessage(message, Message.STATUS_SEND_FAILED, Message.ERROR_MESSAGE_CANCELLED);
        }
    }

    private void retryDecryption(Message message) {
        message.setEncryption(Message.ENCRYPTION_PGP);
        activity.onConversationsListItemUpdated();
        refresh();
        conversation.getAccount().getPgpDecryptionService().decrypt(message, false);
    }

    public void privateMessageWith(final Jid counterpart) {
        try {
            final Jid tcp = conversation.getMucOptions().getTrueCounterpart(counterpart);
            if (!getConversation().getMucOptions().isUserInRoom(counterpart) && getConversation().getMucOptions().findUserByRealJid(tcp == null ? null : tcp.asBareJid()) == null) {
                ToastCompat.makeText(getActivity(), activity.getString(R.string.user_has_left_conference, counterpart.getResource()), ToastCompat.LENGTH_SHORT).show();
                return;
            }
            if (conversation.setOutgoingChatState(Config.DEFAULT_CHAT_STATE)) {
                activity.xmppConnectionService.sendChatState(conversation);
            }
            this.binding.textinput.setText("");
            this.conversation.setNextCounterpart(counterpart);
        } catch (Exception e) {
            e.printStackTrace();
            ToastCompat.makeText(getActivity(), activity.getString(R.string.user_has_left_conference, activity.getString(R.string.user)), ToastCompat.LENGTH_SHORT).show();
        } finally {
            updateChatMsgHint();
            updateSendButton();
            updateEditablity();
        }
    }

    private void correctMessage(Message message) {
        while (message.mergeable(message.next())) {
            message = message.next();
        }
        conversation.setUserSelectedThread(true);
        this.conversation.setCorrectingMessage(message);
        final Editable editable = binding.textinput.getText();
        this.conversation.setDraftMessage(editable.toString());
        this.binding.textinput.setText("");
        this.binding.textinput.append(message.getBody(true));
        if (message.getSubject() != null && message.getSubject().length() > 0) {
            this.binding.textinputSubject.setText(message.getSubject());
            this.binding.textinputSubject.setVisibility(View.VISIBLE);
        }
        final var replyTo = message.getInReplyTo();
        if (replyTo != null) {
            setupReply(replyTo);
        }
    }

    private void chooseReaction(Message message) {
        while (message.mergeable(message.next())) {
            message = message.next();
        }
        conversation.setUserSelectedThread(true);
        //Open emoji picker
        if (binding.emojiButton.getVisibility() == VISIBLE && binding.emojisStickerLayout.getHeight() > 100) {
            binding.emojiButton.setVisibility(GONE);
            binding.keyboardButton.setVisibility(VISIBLE);
            hideSoftKeyboard(activity);
            EmojiPickerView emojiPickerView = binding.emojiPicker;
            backPressedLeaveEmojiPicker.setEnabled(true);
            binding.textinput.requestFocus();
            emojiPickerView.setOnEmojiPickedListener(emojiViewItem -> {
                binding.textinput.append(emojiViewItem.getEmoji());
            });
        } else if (binding.emojiButton.getVisibility() == VISIBLE && binding.emojisStickerLayout.getHeight() < 100) {
            LinearLayout emojipickerview = binding.emojisStickerLayout;
            ViewGroup.LayoutParams params = emojipickerview.getLayoutParams();
            params.height = 800;
            emojipickerview.setLayoutParams(params);
            binding.emojiButton.setVisibility(GONE);
            binding.keyboardButton.setVisibility(VISIBLE);
            hideSoftKeyboard(activity);
            EmojiPickerView emojiPickerView = binding.emojiPicker;
            backPressedLeaveEmojiPicker.setEnabled(true);
            binding.textinput.requestFocus();
            emojiPickerView.setOnEmojiPickedListener(emojiViewItem -> {
                binding.textinput.append(emojiViewItem.getEmoji());
            });
        }
        if (binding.emojiPicker.getVisibility() == VISIBLE) {
            binding.emojisButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
            binding.emojisButton.setTypeface(null, Typeface.BOLD);
        } else {
            binding.emojisButton.setBackgroundColor(0);
            binding.emojisButton.setTypeface(null, Typeface.NORMAL);
        }
        if (binding.stickersview.getVisibility() == VISIBLE) {
            binding.stickersButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
            binding.stickersButton.setTypeface(null, Typeface.BOLD);
        } else {
            binding.stickersButton.setBackgroundColor(0);
            binding.stickersButton.setTypeface(null, Typeface.NORMAL);
        }
        if (binding.gifsview.getVisibility() == VISIBLE) {
            binding.gifsButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
            binding.gifsButton.setTypeface(null, Typeface.BOLD);
        } else {
            binding.gifsButton.setBackgroundColor(0);
            binding.gifsButton.setTypeface(null, Typeface.NORMAL);
        }
        // TODO: Directly choose emojis from popup menu
    }

    private void highlightInConference(String nick) {
        final Editable editable = this.binding.textinput.getText();
        String oldString = editable.toString().trim();
        final int pos = this.binding.textinput.getSelectionStart();
        if (oldString.isEmpty() || pos == 0) {
            editable.insert(0, nick + ": ");
        } else {
            final char before = editable.charAt(pos - 1);
            final char after = editable.length() > pos ? editable.charAt(pos) : '\0';
            if (before == '\n') {
                editable.insert(pos, nick + ": ");
            } else {
                if (pos > 2 && editable.subSequence(pos - 2, pos).toString().equals(": ")) {
                    if (NickValidityChecker.check(conversation, Arrays.asList(editable.subSequence(0, pos - 2).toString().split(", ")))) {
                        editable.insert(pos - 2, ", " + nick);
                        return;
                    }
                }
                editable.insert(pos, (Character.isWhitespace(before) ? "" : " ") + nick + (Character.isWhitespace(after) ? "" : " "));
                if (Character.isWhitespace(after)) {
                    this.binding.textinput.setSelection(this.binding.textinput.getSelectionStart() + 1);
                }
            }
        }
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        final Activity activity = getActivity();
        if (activity instanceof ConversationsActivity) {
            ((ConversationsActivity) activity).clearPendingViewIntent();
        }
        super.startActivityForResult(intent, requestCode);
    }

    @Override
    public void onSaveInstanceState(@NotNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (conversation != null) {
            outState.putString(STATE_CONVERSATION_UUID, conversation.getUuid());
            outState.putString(STATE_LAST_MESSAGE_UUID, lastMessageUuid);
            final Uri uri = pendingTakePhotoUri.peek();
            if (uri != null) {
                outState.putString(STATE_PHOTO_URI, uri.toString());
            }
            final ScrollState scrollState = getScrollPosition();
            if (scrollState != null) {
                outState.putParcelable(STATE_SCROLL_POSITION, scrollState);
            }
            final ArrayList<Attachment> attachments = mediaPreviewAdapter == null ? new ArrayList<>() : mediaPreviewAdapter.getAttachments();
            if (attachments.size() > 0) {
                outState.putParcelableArrayList(STATE_MEDIA_PREVIEWS, attachments);
            }
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState == null) {
            return;
        }
        String uuid = savedInstanceState.getString(STATE_CONVERSATION_UUID);
        ArrayList<Attachment> attachments = savedInstanceState.getParcelableArrayList(STATE_MEDIA_PREVIEWS);
        pendingLastMessageUuid.push(savedInstanceState.getString(STATE_LAST_MESSAGE_UUID, null));
        if (uuid != null) {
            QuickLoader.set(uuid);
            this.pendingConversationsUuid.push(uuid);
            if (attachments != null && attachments.size() > 0) {
                this.pendingMediaPreviews.push(attachments);
            }
            String takePhotoUri = savedInstanceState.getString(STATE_PHOTO_URI);
            if (takePhotoUri != null) {
                pendingTakePhotoUri.push(Uri.parse(takePhotoUri));
            }
            pendingScrollState.push(savedInstanceState.getParcelable(STATE_SCROLL_POSITION));
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        disableEncrpytionForExceptions();
        if (this.reInitRequiredOnStart && this.conversation != null) {
            final Bundle extras = pendingExtras.pop();
            reInit(this.conversation, extras != null);
            if (extras != null) {
                processExtras(extras);
            }
        } else if (conversation == null && activity != null && activity.xmppConnectionService != null) {
            final String uuid = pendingConversationsUuid.pop();
            Log.d(Config.LOGTAG, "ConversationFragment.onStart() - activity was bound but no conversation loaded. uuid=" + uuid);
            if (uuid != null) {
                findAndReInitByUuidOrArchive(uuid);
            }
        }
        updateChatBG();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (activity != null) {
            hideSoftKeyboard(activity);
        }
        final Activity activity = getActivity();
        if (messageListAdapter == null) {
            return;
        }
        messageListAdapter.unregisterListenerInAudioPlayer();
        if (activity == null || !activity.isChangingConfigurations()) {
            hideSoftKeyboard(activity);
            messageListAdapter.stopAudioPlayer();
        }
        if (this.conversation != null) {
            final String msg = this.binding.textinput.getText().toString();
            storeNextMessage(msg);
            updateChatState(this.conversation, msg);
            this.activity.xmppConnectionService.getNotificationService().setOpenConversation(null);
        }
        this.reInitRequiredOnStart = true;
    }

    private void updateChatState(final Conversation conversation, final String msg) {
        ChatState state = msg.length() == 0 ? Config.DEFAULT_CHAT_STATE : ChatState.PAUSED;
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(state)) {
            activity.xmppConnectionService.sendChatState(conversation);
        }
    }

    private void saveMessageDraftStopAudioPlayer() {
        final Conversation previousConversation = this.conversation;
        if (this.activity == null || this.binding == null || previousConversation == null) {
            return;
        }
        Log.d(Config.LOGTAG, "ConversationFragment.saveMessageDraftStopAudioPlayer()");
        final String msg = this.binding.textinput.getText().toString();
        storeNextMessage(msg);
        updateChatState(this.conversation, msg);
        messageListAdapter.stopAudioPlayer();
        mediaPreviewAdapter.clearPreviews();
        toggleInputMethod();
    }

    public void reInit(final Conversation conversation, final Bundle extras) {
        QuickLoader.set(conversation.getUuid());
        final boolean changedConversation = this.conversation != conversation;
        if (changedConversation) {
            this.saveMessageDraftStopAudioPlayer();
        }
        this.clearPending();
        if (this.reInit(conversation, extras != null)) {
            if (extras != null) {
                processExtras(extras);
            }
            this.reInitRequiredOnStart = false;
        } else {
            this.reInitRequiredOnStart = true;
            pendingExtras.push(extras);
        }
        resetUnreadMessagesCount();
    }

    private void reInit(Conversation conversation) {
        reInit(conversation, false);
        if (activity != null) {
            activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
    }

    private boolean reInit(final Conversation conversation, final boolean hasExtras) {
        if (conversation == null) {
            return false;
        }
        final Conversation originalConversation = this.conversation;
        this.conversation = conversation;
        //once we set the conversation all is good and it will automatically do the right thing in onStart()
        if (this.activity == null || this.binding == null) {
            return false;
        }
        updateinputfield(canSendMeCommand());
        if (activity != null) {
            activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
        if (!activity.xmppConnectionService.isConversationStillOpen(this.conversation)) {
            activity.onConversationArchived(this.conversation);
            return false;
        }
        setupReply(conversation.getReplyTo());

        stopScrolling();
        Log.d(Config.LOGTAG, "reInit(hasExtras=" + hasExtras + ")");

        if (this.conversation.isRead() && hasExtras) {
            Log.d(Config.LOGTAG, "trimming conversation");
            this.conversation.trim();
        }

        setupIme();

        final boolean scrolledToBottomAndNoPending = this.scrolledToBottom() && pendingScrollState.peek() == null;

        this.binding.textSendButton.setContentDescription(activity.getString(R.string.send_message_to_x, conversation.getName()));
        this.binding.textinput.setKeyboardListener(null);
        this.binding.textinputSubject.setKeyboardListener(null);
        showRecordVoiceButton();
        ConversationMenuConfigurator.reloadFeatures(conversation, activity);
        final boolean participating = conversation.getMode() == Conversational.MODE_SINGLE || conversation.getMucOptions().participating();
        if (participating) {
            this.binding.textinput.setText(this.conversation.getNextMessage());
            this.binding.textinput.setSelection(this.binding.textinput.length());
        } else {
            this.binding.textinput.setText(MessageUtils.EMPTY_STRING);
        }
        this.binding.textinput.setKeyboardListener(this);
        this.binding.textinputSubject.setKeyboardListener(this);
        if (messageListAdapter == null) {
            return true;
        }
        messageListAdapter.updatePreferences();
        refresh(false);
        activity.invalidateOptionsMenu();
        this.conversation.messagesLoaded.set(true);
        Log.d(Config.LOGTAG, "scrolledToBottomAndNoPending=" + Boolean.toString(scrolledToBottomAndNoPending));

        if (hasExtras || scrolledToBottomAndNoPending) {
            resetUnreadMessagesCount();
            synchronized (this.messageList) {
                Log.d(Config.LOGTAG, "jump to first unread message");
                final Message first = conversation.getFirstUnreadMessage();
                final int bottom = Math.max(0, this.messageList.size() - 1);
                final int pos;
                final boolean jumpToBottom;
                if (first == null) {
                    pos = bottom;
                    jumpToBottom = true;
                } else {
                    int i = getIndexOf(first.getUuid(), this.messageList);
                    pos = i < 0 ? bottom : i;
                    jumpToBottom = false;
                }
                setSelection(pos, jumpToBottom);
            }
        }

        this.binding.messagesView.post(this::fireReadEvent);
        // TODO if we only do this when this fragment is running on main it won't *bing* in tablet
        // layout which might be unnecessary since we can *see* it
        activity.xmppConnectionService
                .getNotificationService()
                .setOpenConversation(this.conversation);

        if (commandAdapter != null && conversation != originalConversation) {
            commandAdapter.clear();
            conversation.setupViewPager(binding.conversationViewPager, binding.tabLayout, activity.xmppConnectionService.isOnboarding(), originalConversation);
            refreshCommands(false);
        }
        if (commandAdapter == null && conversation != null) {
            conversation.setupViewPager(binding.conversationViewPager, binding.tabLayout, activity.xmppConnectionService.isOnboarding(), null);
            commandAdapter = new CommandAdapter((XmppActivity) getActivity());
            binding.commandsView.setAdapter(commandAdapter);
            binding.commandsView.setOnItemClickListener((parent, view, position, id) -> {
                if (activity == null) return;

                commandAdapter.getItem(position).start(activity, ConversationFragment.this.conversation);
            });
            refreshCommands(false);
        }

        return true;
    }

    private void hasWriteAccessInMUC() {
        if ((conversation != null && conversation.getMode() == Conversation.MODE_MULTI && !conversation.getMucOptions().participating()) && !activity.xmppConnectionService.hideYouAreNotParticipating()) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getString(R.string.you_are_not_participating));
            builder.setMessage(getString(R.string.no_write_access_in_public_muc));
            builder.setNegativeButton(getString(R.string.hide_warning),
                    (dialog, which) -> {
                        SharedPreferences preferences = activity.getPreferences();
                        preferences.edit().putBoolean(HIDE_YOU_ARE_NOT_PARTICIPATING, true).apply();
                        hideSnackbar();
                    });
            builder.setPositiveButton(getString(R.string.ok),
                    (dialog, which) -> {
                        try {
                            Intent intent = new Intent(getActivity(), ConferenceDetailsActivity.class);
                            intent.setAction(ConferenceDetailsActivity.ACTION_VIEW_MUC);
                            intent.putExtra("uuid", conversation.getUuid());
                            startActivity(intent);
                            activity.overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
            AlertDialog alertDialog = builder.create();
            activity.runOnUiThread(alertDialog::show);
            showSnackbar(R.string.no_write_access_in_public_muc, R.string.ok, clickToMuc);
        }
    }

    @Override
    public void refreshForNewCaps(final Set<Jid> newCapsJids) {
        if (newCapsJids.isEmpty() || newCapsJids.contains(conversation.getJid().asBareJid())) {
            refreshCommands(true);
        }
    }

    protected void refreshCommands(boolean delayShow) {
        if (commandAdapter == null) return;

        final CommandAdapter.MucConfig mucConfig =
                conversation.getMucOptions().getSelf().getAffiliation().ranks(MucOptions.Affiliation.OWNER) ?
                        new CommandAdapter.MucConfig() :
                        null;

        Jid commandJid = conversation.getContact().resourceWhichSupport(Namespace.COMMANDS);
        if (commandJid == null && conversation.getMode() == Conversation.MODE_MULTI && conversation.getMucOptions().hasFeature(Namespace.COMMANDS)) {
            commandJid = conversation.getJid().asBareJid();
        }
        if (commandJid == null && conversation.getJid().isDomainJid()) {
            commandJid = conversation.getJid();
        }
        if (commandJid == null) {
            binding.commandsViewProgressbar.setVisibility(View.GONE);
            if (mucConfig == null) {
                conversation.hideViewPager();
            } else {
                commandAdapter.clear();
                commandAdapter.add(mucConfig);
                conversation.showViewPager();
            }
        } else {
            if (!delayShow) conversation.showViewPager();
            binding.commandsViewProgressbar.setVisibility(View.VISIBLE);
            activity.xmppConnectionService.fetchCommands(conversation.getAccount(), commandJid, (a, iq) -> {
                if (activity == null) return;

                activity.runOnUiThread(() -> {
                    binding.commandsViewProgressbar.setVisibility(View.GONE);
                    commandAdapter.clear();
                    if (iq.getType() == IqPacket.TYPE.RESULT) {
                        for (Element child : iq.query().getChildren()) {
                            if (!"item".equals(child.getName()) || !Namespace.DISCO_ITEMS.equals(child.getNamespace())) continue;
                            commandAdapter.add(new CommandAdapter.Command0050(child));
                        }
                    }

                    if (mucConfig != null) commandAdapter.add(mucConfig);

                    if (commandAdapter.getCount() < 1) {
                        conversation.hideViewPager();
                    } else if (delayShow) {
                        conversation.showViewPager();
                    }
                });
            });
        }
    }

    private void resetUnreadMessagesCount() {
        lastMessageUuid = null;
        hideUnreadMessagesCount();
    }

    private void hideUnreadMessagesCount() {
        if (this.binding == null) {
            return;
        }
        this.binding.scrollToBottomButton.setEnabled(false);
        this.binding.scrollToBottomButton.hide();
        this.binding.unreadCountCustomView.setVisibility(GONE);
    }

    private void setSelection(int pos, boolean jumpToBottom) {
        ListViewUtils.setSelection(this.binding.messagesView, pos, jumpToBottom);
        this.binding.messagesView.post(() -> ListViewUtils.setSelection(this.binding.messagesView, pos, jumpToBottom));
        this.binding.messagesView.post(this::fireReadEvent);
    }

    private boolean scrolledToBottom() {
        return this.binding != null && scrolledToBottom(this.binding.messagesView);
    }

    private void processExtras(final Bundle extras) {
        final String downloadUuid = extras.getString(ConversationsActivity.EXTRA_DOWNLOAD_UUID);
        final String text = extras.getString(Intent.EXTRA_TEXT);
        final String nick = extras.getString(ConversationsActivity.EXTRA_NICK);
        final String node = extras.getString(ConversationsActivity.EXTRA_NODE);
        final String postInitAction =
                extras.getString(ConversationsActivity.EXTRA_POST_INIT_ACTION);
        final boolean asQuote = extras.getBoolean(ConversationsActivity.EXTRA_AS_QUOTE);
        final String user = extras.getString(ConversationsActivity.EXTRA_USER);
        final boolean pm = extras.getBoolean(ConversationsActivity.EXTRA_IS_PRIVATE_MESSAGE, false);
        final boolean doNotAppend =
                extras.getBoolean(ConversationsActivity.EXTRA_DO_NOT_APPEND, false);
        final String type = extras.getString(ConversationsActivity.EXTRA_TYPE);

        final String thread = extras.getString(ConversationsActivity.EXTRA_THREAD);
        if (thread != null) {
            conversation.setLockThread(true);
            backPressedLeaveSingleThread.setEnabled(true);
            refresh();
        }

        final List<Uri> uris = extractUris(extras);
        if (uris != null && uris.size() > 0) {
            if (uris.size() == 1 && "geo".equals(uris.get(0).getScheme())) {
                mediaPreviewAdapter.addMediaPreviews(
                        Attachment.of(getActivity(), uris.get(0), Attachment.Type.LOCATION));
            } else {
                final List<Uri> cleanedUris = cleanUris(new ArrayList<>(uris));
                mediaPreviewAdapter.addMediaPreviews(
                        Attachment.of(getActivity(), cleanedUris, type));
            }
            toggleInputMethod();
            return;
        }
        if (nick != null) {
            if (pm) {
                Jid jid = conversation.getJid();
                try {
                    Jid next = Jid.of(jid.getLocal(), jid.getDomain(), nick);
                    privateMessageWith(next);
                } catch (final IllegalArgumentException ignored) {
                    // do nothing
                }
            } else {
                final MucOptions mucOptions = conversation.getMucOptions();
                if (mucOptions.participating() || conversation.getNextCounterpart() != null) {
                    highlightInConference(nick);
                }
            }
        } else {
            if (text != null && GeoHelper.GEO_URI.matcher(text).matches()) {
                mediaPreviewAdapter.addMediaPreviews(
                        Attachment.of(getActivity(), Uri.parse(text), Attachment.Type.LOCATION));
                toggleInputMethod();
                return;
            } else if (text != null && asQuote) {
                quoteText(text, user);
            } else {
                appendText(text, doNotAppend);
            }
        }
        if (ConversationsActivity.POST_ACTION_RECORD_VOICE.equals(postInitAction)) {
            attachFile(ATTACHMENT_CHOICE_RECORD_VOICE, false);
            return;
        }
        if ("call".equals(postInitAction)) {
            checkPermissionAndTriggerAudioCall();
        }
        if ("message".equals(postInitAction)) {
            binding.conversationViewPager.post(() -> {
                binding.conversationViewPager.setCurrentItem(0);
            });
        }
        if ("command".equals(postInitAction)) {
            binding.conversationViewPager.post(() -> {
                PagerAdapter adapter = binding.conversationViewPager.getAdapter();
                if (adapter != null && adapter.getCount() > 1) {
                    binding.conversationViewPager.setCurrentItem(1);
                }
                final String jid = extras.getString(ConversationsActivity.EXTRA_JID);
                Jid commandJid = null;
                if (jid != null) {
                    try {
                        commandJid = Jid.of(jid);
                    } catch (final IllegalArgumentException e) { }
                }
                if (commandJid == null || !commandJid.isFullJid()) {
                    final Jid discoJid = conversation.getContact().resourceWhichSupport(Namespace.COMMANDS);
                    if (discoJid != null) commandJid = discoJid;
                }
                if (node != null && commandJid != null && activity != null) {
                    conversation.startCommand(commandFor(commandJid, node), activity.xmppConnectionService);
                }
            });
            return;
        }
        Message message =
                downloadUuid == null ? null : conversation.findMessageWithFileAndUuid(downloadUuid);
        if ("webxdc".equals(postInitAction)) {
            if (message == null) {
                message = activity.xmppConnectionService.getMessage(conversation, downloadUuid);
            }
            if (message == null) return;

            Cid webxdcCid = message.getFileParams().getCids().get(0);
            WebxdcPage webxdc = new WebxdcPage(activity, webxdcCid, message, activity.xmppConnectionService);
            Conversation conversation = (Conversation) message.getConversation();
            if (!conversation.switchToSession("webxdc\0" + message.getUuid())) {
                conversation.startWebxdc(webxdc);
            }
        }
        if (message != null) {
            startDownloadable(message);
        }
        if (activity.xmppConnectionService.isOnboarding() && conversation.getJid().equals(Jid.of("cheogram.com"))) {
            if (!conversation.switchToSession("jabber:iq:register")) {
                conversation.startCommand(commandFor(Jid.of("cheogram.com/CHEOGRAM%jabber:iq:register"), "jabber:iq:register"), activity.xmppConnectionService);
            }
        }
    }

    private Element commandFor(final Jid jid, final String node) {
        if (commandAdapter != null) {
            for (int i = 0; i < commandAdapter.getCount(); i++) {
                final CommandAdapter.Command c = commandAdapter.getItem(i);
                if (!(c instanceof CommandAdapter.Command0050)) continue;

                final Element command = ((CommandAdapter.Command0050) c).el;
                final String commandNode = command.getAttribute("node");
                if (commandNode == null || !commandNode.equals(node)) continue;

                final Jid commandJid = command.getAttributeAsJid("jid");
                if (commandJid != null && !commandJid.asBareJid().equals(jid.asBareJid())) continue;

                return command;
            }
        }

        return new Element("command", Namespace.COMMANDS).setAttribute("name", node).setAttribute("node", node).setAttribute("jid", jid);
    }

    private List<Uri> extractUris(final Bundle extras) {
        final List<Uri> uris = extras.getParcelableArrayList(Intent.EXTRA_STREAM);
        if (uris != null) {
            return uris;
        }
        final Uri uri = extras.getParcelable(Intent.EXTRA_STREAM);
        if (uri != null) {
            return Collections.singletonList(uri);
        } else {
            return null;
        }
    }

    private List<Uri> cleanUris(final List<Uri> uris) {
        final Iterator<Uri> iterator = uris.iterator();
        while (iterator.hasNext()) {
            final Uri uri = iterator.next();
            if (FileBackend.dangerousFile(uri)) {
                iterator.remove();
                ToastCompat.makeText(requireActivity(), R.string.security_violation_not_attaching_file, ToastCompat.LENGTH_SHORT).show();
            }
        }
        return uris;
    }

    private boolean showBlockSubmenu(View view) {
        final Jid jid = conversation.getJid();
        final boolean showReject = conversation.getContact().getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST);
        PopupMenu popupMenu = new PopupMenu(getActivity(), view);
        popupMenu.inflate(R.menu.block);
        popupMenu.getMenu().findItem(R.id.block_contact).setVisible(jid.getLocal() != null);
        popupMenu.getMenu().findItem(R.id.reject).setVisible(showReject);
        popupMenu.setOnMenuItemClickListener(menuItem -> {
            Blockable blockable;
            int itemId = menuItem.getItemId();
            if (itemId == R.id.reject) {
                activity.xmppConnectionService.stopPresenceUpdatesTo(conversation.getContact());
                updateSnackBar(conversation);
                return true;
            } else if (itemId == R.id.block_domain) {
                blockable = conversation.getAccount().getRoster().getContact(jid.getDomain());
            } else {
                blockable = conversation;
            }
            BlockContactDialog.show(activity, blockable);
            return true;
        });
        popupMenu.show();
        return true;
    }

    @SuppressLint("StringFormatInvalid")
    private void updateSnackBar(final Conversation conversation) {
        if (conversation == null) {
            return;
        }
        final Account account = conversation.getAccount();
        final XmppConnection connection = account.getXmppConnection();
        final int mode = conversation.getMode();
        final Contact contact = mode == Conversation.MODE_SINGLE ? conversation.getContact() : null;
        if (conversation.getStatus() == Conversation.STATUS_ARCHIVED) {
            return;
        }
        if (account.getStatus() == Account.State.DISABLED) {
            showSnackbar(R.string.this_account_is_disabled, R.string.enable, this.mEnableAccountListener);
        } else if (account.getStatus() == Account.State.LOGGED_OUT) {
            showSnackbar(R.string.this_account_is_logged_out,R.string.log_in,this.mEnableAccountListener);
        } else if (conversation.isBlocked()) {
            showSnackbar(R.string.contact_blocked, R.string.unblock, this.mUnblockClickListener);
        } else if (contact != null && !contact.showInRoster() && contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
            showSnackbar(R.string.contact_added_you, R.string.add_back, this.mAddBackClickListener, this.mLongPressBlockListener);
        } else if (contact != null && contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
            showSnackbar(R.string.contact_asks_for_presence_subscription, R.string.allow, this.mAllowPresenceSubscription, this.mLongPressBlockListener);
        } else if (mode == Conversation.MODE_MULTI
                && !conversation.getMucOptions().online()
                && account.getStatus() == Account.State.ONLINE) {
            MucOptions.Error error = conversation.getMucOptions().getError();
            if (Objects.requireNonNull(error) == MucOptions.Error.NICK_IN_USE) {
                showSnackbar(R.string.nick_in_use, R.string.edit, clickToMuc);
            } else if (error == MucOptions.Error.NO_RESPONSE) {
                showSnackbar(R.string.joining_conference, 0, null);
            } else if (error == MucOptions.Error.SERVER_NOT_FOUND) {
                if (conversation.receivedMessagesCount() > 0) {
                    showSnackbar(R.string.remote_server_not_found, R.string.try_again, joinMuc);
                } else {
                    showSnackbar(R.string.remote_server_not_found, R.string.leave, leaveMuc);
                }
            } else if (error == MucOptions.Error.REMOTE_SERVER_TIMEOUT) {
                if (conversation.receivedMessagesCount() > 0) {
                    showSnackbar(R.string.remote_server_timeout, R.string.try_again, joinMuc);
                } else {
                    showSnackbar(R.string.remote_server_timeout, R.string.leave, leaveMuc);
                }
            } else if (error == MucOptions.Error.PASSWORD_REQUIRED) {
                showSnackbar(R.string.conference_requires_password, R.string.enter_password, enterPassword);
            } else if (error == MucOptions.Error.BANNED) {
                showSnackbar(R.string.conference_banned, R.string.leave, leaveMuc);
            } else if (error == MucOptions.Error.MEMBERS_ONLY) {
                showSnackbar(R.string.conference_members_only, R.string.leave, leaveMuc);
            } else if (error == MucOptions.Error.RESOURCE_CONSTRAINT) {
                showSnackbar(R.string.conference_resource_constraint, R.string.try_again, joinMuc);
            } else if (error == MucOptions.Error.KICKED) {
                showSnackbar(R.string.conference_kicked, R.string.join, joinMuc);
            } else if (error == MucOptions.Error.TECHNICAL_PROBLEMS) {
                showSnackbar(R.string.conference_technical_problems, R.string.try_again, joinMuc);
            } else if (error == MucOptions.Error.UNKNOWN) {
                showSnackbar(R.string.conference_unknown_error, R.string.join, joinMuc);
            } else if (error == MucOptions.Error.INVALID_NICK) {
                showSnackbar(R.string.invalid_muc_nick, R.string.edit, clickToMuc);

                showSnackbar(R.string.conference_shutdown, R.string.try_again, joinMuc);
            } else if (error == MucOptions.Error.SHUTDOWN) {
                showSnackbar(R.string.conference_shutdown, R.string.try_again, joinMuc);
            } else if (error == MucOptions.Error.DESTROYED) {
                showSnackbar(R.string.conference_destroyed, R.string.leave, leaveMuc);
            } else if (error == MucOptions.Error.NON_ANONYMOUS) {
                showSnackbar(R.string.group_chat_will_make_your_jabber_id_public, R.string.join, acceptJoin);
            } else {
                hideSnackbar();
            }
        } else if (account.hasPendingPgpIntent(conversation)) {
            showSnackbar(R.string.openpgp_messages_found, R.string.decrypt, clickToDecryptListener);
        } else if (mode == Conversation.MODE_SINGLE
                && conversation.smpRequested()) {
            showSnackbar(R.string.smp_requested, R.string.verify, this.mAnswerSmpClickListener);
        } else if (mode == Conversation.MODE_SINGLE
                && conversation.hasValidOtrSession()
                && (conversation.getOtrSession().getSessionStatus() == SessionStatus.ENCRYPTED)
                && (!conversation.isOtrFingerprintVerified())) {
            showSnackbar(R.string.unknown_otr_fingerprint, R.string.verify, clickToVerify);
        } else if (connection != null
                && connection.getFeatures().blocking()
                && conversation.countMessages() != 0
                && !conversation.isBlocked()
                && conversation.isWithStranger()) {
            showSnackbar(R.string.received_message_from_stranger, R.string.block, mBlockClickListener);
        } else if (activity != null && activity.warnUnecryptedChat()) {
            if (conversation.getNextEncryption() == Message.ENCRYPTION_NONE && conversation.isSingleOrPrivateAndNonAnonymous() && ((Config.supportOmemo() && Conversation.suitableForOmemoByDefault(conversation)) ||
                    (Config.supportOpenPgp() && account.isPgpDecryptionServiceConnected()) || (
                    mode == Conversation.MODE_SINGLE && Config.supportOtr()))) {
                if (isEncryptionDisabledException() || conversation.getJid().toString().equals(account.getJid().getDomain())) {
                    hideSnackbar();
                } else {
                    hideSnackbar();
                }
            } else {
                hideSnackbar();
            }
        } else if (conversation.getUuid().equalsIgnoreCase(AttachFileToConversationRunnable.isCompressingVideo[0])) {
            Activity activity = getActivity();
            if (activity != null) {
                showSnackbar(getString(R.string.transcoding_video_x, AttachFileToConversationRunnable.isCompressingVideo[1]), 0, null);
            }
        } else {
            hideSnackbar();
        }
    }

    @Override
    public void refresh() {
        if (activity != null) {
            activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
        if (this.binding == null) {
            Log.d(Config.LOGTAG, "ConversationFragment.refresh() skipped updated because view binding was null");
            return;
        }
        updateChatBG();
        disableEncrpytionForExceptions();
        if (this.conversation != null && this.activity != null && this.activity.xmppConnectionService != null) {
            if (!activity.xmppConnectionService.isConversationStillOpen(this.conversation)) {
                activity.onConversationArchived(this.conversation);
                return;
            }
        }
        this.refresh(true);
    }


    private void refresh(boolean notifyConversationRead) {
        synchronized (this.messageList) {
            if (this.conversation != null) {
                conversation.populateWithMessages(this.messageList, activity == null ? null : activity.xmppConnectionService);
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

    protected void messageSent() {
        binding.textinputSubject.setText("");
        binding.textinputSubject.setVisibility(View.GONE);
        conversation.setUserSelectedThread(false);
        mSendingPgpMessage.set(false);
        this.binding.textinput.setText("");
        if (conversation.setCorrectingMessage(null)) {
            this.binding.textinput.append(conversation.getDraftMessage());
            conversation.setDraftMessage(null);
        }
        storeNextMessage();
        updateChatMsgHint();
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
        final boolean prefScrollToBottom = p.getBoolean("scroll_to_bottom", activity.getResources().getBoolean(R.bool.scroll_to_bottom));
        if (prefScrollToBottom || scrolledToBottom()) {
            new Handler().post(() -> {
                int size = messageList.size();
                this.binding.messagesView.setSelection(size - 1);
            });
        }
    }

    private boolean storeNextMessage() {
        return storeNextMessage(this.binding.textinput.getText().toString());
    }

    private boolean storeNextMessage(String msg) {
        final boolean participating = conversation.getMode() == Conversational.MODE_SINGLE || conversation.getMucOptions().participating();
        if (this.conversation.getStatus() != Conversation.STATUS_ARCHIVED &&
                participating &&
                this.conversation.setNextMessage(msg) && activity != null) {
            activity.xmppConnectionService.updateConversation(this.conversation);
            return true;
        }
        return false;
    }

    public void doneSendingPgpMessage() {
        mSendingPgpMessage.set(false);
    }

    public long getMaxHttpUploadSize(Conversation conversation) {
        final XmppConnection connection = conversation.getAccount().getXmppConnection();
        return connection == null ? -1 : connection.getFeatures().getMaxHttpUploadSize();
    }

    private void updateEditablity() {
        boolean canWrite = this.conversation.getMode() == Conversation.MODE_SINGLE || this.conversation.getMucOptions().participating() || this.conversation.getNextCounterpart() != null;
        this.binding.textinput.setFocusable(canWrite);
        this.binding.textinput.setFocusableInTouchMode(canWrite);
        this.binding.textSendButton.setEnabled(canWrite);
        this.binding.textinput.setCursorVisible(canWrite);
        this.binding.textinput.setEnabled(canWrite);
    }

    public void updateSendButton() {
        messageListAdapter.setInputBubbleBackgroundColor(binding.inputArea, isPrivateMessage());
        boolean hasAttachments = mediaPreviewAdapter != null && mediaPreviewAdapter.hasAttachments();
        boolean useSendButtonToIndicateStatus = activity != null && PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("send_button_status", getResources().getBoolean(R.bool.send_button_status));
        final Conversation c = this.conversation;
        final Presence.Status status;
        final String text = this.binding.textinput == null ? "" : this.binding.textinput.getText().toString();
        final SendButtonAction action;
        if (hasAttachments) {
            action = SendButtonAction.TEXT;
        } else {
            action = SendButtonTool.getAction(getActivity(), c, text, binding.textinputSubject.getText().toString());
        }
        if (useSendButtonToIndicateStatus && c.getAccount().getStatus() == Account.State.ONLINE) {
            if (activity != null && activity.xmppConnectionService != null && activity.xmppConnectionService.getMessageArchiveService().isCatchingUp(c)) {
                status = Presence.Status.OFFLINE;
            } else if (c.getMode() == Conversation.MODE_SINGLE) {
                status = c.getContact().getShownStatus();
            } else {
                status = c.getMucOptions().online() ? Presence.Status.ONLINE : Presence.Status.OFFLINE;
            }
        } else {
            status = Presence.Status.OFFLINE;
        }
        this.binding.textSendButton.setTag(action);
        final Activity activity = getActivity();
        if (activity != null) {
            this.binding.textSendButton.setImageResource(
                    SendButtonTool.getSendButtonImageResource(activity, action, status)); // || (c.getThread() != null && binding.textinputSubject.getText().length() > 0))); https://issues.prosody.im/1838
        }
        if (hasAttachments || binding.textinput.getText().toString().replaceFirst("^(\\w|[, ])+:\\s*", "").length() > 0) {
            binding.conversationViewPager.setCurrentItem(0);
            // params.width = conversation.getThread() == null ? 0 : identiconWidth; // TODO: Clean this up
        }
        showRecordVoiceButton();
        updateSnackBar(conversation);
    }

    public void updateinputfield(final boolean me) {
        LinearLayout emojipickerview = binding.emojisStickerLayout;
        ViewGroup.LayoutParams params = emojipickerview.getLayoutParams();
        Fragment secondaryFragment = activity.getFragmentManager().findFragmentById(R.id.secondary_fragment);
        if (Build.VERSION.SDK_INT > 29) {
            ViewCompat.setOnApplyWindowInsetsListener(activity.getWindow().getDecorView(), (v, insets) -> {
                boolean isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
                int keyboardHeight = 0;
                if (activity != null && ViewConfiguration.get(activity).hasPermanentMenuKey()) {
                    keyboardHeight  = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom - insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom - 25;
                } else if (activity != null) {
                    keyboardHeight  = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom - insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom - 25;
                }
                if (keyboardHeight > 100 && !(secondaryFragment instanceof ConversationFragment)) {
                    binding.keyboardButton.setVisibility(GONE);
                    binding.emojiButton.setVisibility(VISIBLE);
                    params.height = keyboardHeight;
                    emojipickerview.setLayoutParams(params);
                } else if (keyboardHeight > 100) {
                    binding.keyboardButton.setVisibility(GONE);
                    binding.emojiButton.setVisibility(VISIBLE);
                    params.height = keyboardHeight - 142;
                    emojipickerview.setLayoutParams(params);
                } else if (binding.emojiButton.getVisibility() == VISIBLE) {
                    binding.keyboardButton.setVisibility(GONE);
                    params.height = 0;
                    emojipickerview.setLayoutParams(params);
                } else if (binding.keyboardButton.getVisibility() == VISIBLE && keyboardHeight == 0) {
                    binding.emojiButton.setVisibility(GONE);
                    params.height = 800;
                    emojipickerview.setLayoutParams(params);
                } else if (binding.keyboardButton.getVisibility() == VISIBLE && keyboardHeight > 100) {
                    binding.emojiButton.setVisibility(GONE);
                    params.height = keyboardHeight;
                    emojipickerview.setLayoutParams(params);
                }
                if (activity != null && activity.xmppConnectionService != null && isKeyboardVisible && activity.xmppConnectionService.showTextFormatting()) {
                    showTextFormat(me);
                } else {
                    hideTextFormat();
                }
                return ViewCompat.onApplyWindowInsets(v, insets);
            });
        } else {
            if (keyboardHeightProvider != null) {
                return;
            }
            RelativeLayout llRoot = binding.conversationsFragment; //The root layout (Linear, Relative, Contraint, etc...)
            keyboardHeightListener = (int keyboardHeight, boolean keyboardOpen, boolean isLandscape) -> {
                Log.i("keyboard listener", "keyboardHeight: " + keyboardHeight + " keyboardOpen: " + keyboardOpen + " isLandscape: " + isLandscape);
                if (keyboardOpen && !(secondaryFragment instanceof ConversationFragment)) {
                    binding.keyboardButton.setVisibility(GONE);
                    binding.emojiButton.setVisibility(VISIBLE);
                    params.height = keyboardHeight - 25;
                    emojipickerview.setLayoutParams(params);
                } else if (keyboardOpen) {
                    binding.keyboardButton.setVisibility(GONE);
                    binding.emojiButton.setVisibility(VISIBLE);
                    params.height = keyboardHeight - 150;
                    emojipickerview.setLayoutParams(params);
                } else if (binding.emojiButton.getVisibility() == VISIBLE) {
                    binding.keyboardButton.setVisibility(GONE);
                    params.height = 0;
                    emojipickerview.setLayoutParams(params);
                } else if (binding.keyboardButton.getVisibility() == VISIBLE && keyboardHeight == 0) {
                    binding.emojiButton.setVisibility(GONE);
                    params.height = 600;
                    emojipickerview.setLayoutParams(params);
                } else if (binding.keyboardButton.getVisibility() == VISIBLE && keyboardHeight > 100) {
                    binding.emojiButton.setVisibility(GONE);
                    params.height = keyboardHeight;
                    emojipickerview.setLayoutParams(params);
                }
                if (activity != null && activity.xmppConnectionService != null && keyboardOpen && activity.xmppConnectionService.showTextFormatting()) {
                    showTextFormat(me);
                } else {
                    hideTextFormat();
                }
            };
            keyboardHeightProvider = new KeyboardHeightProvider(activity, activity.getWindowManager(), llRoot, keyboardHeightListener);
        }
    }

    protected void updateStatusMessages() {
        DateSeparator.addAll(this.messageList);
        if (showLoadMoreMessages(conversation)) {
            this.messageList.add(0, Message.createLoadMoreMessage(conversation));
        }
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            final MucOptions mucOptions = conversation.getMucOptions();
            final List<MucOptions.User> allUsers = mucOptions.getUsers();
            final Set<ReadByMarker> addedMarkers = new HashSet<>();
            if (mucOptions.isPrivateAndNonAnonymous()) {
                for (int i = this.messageList.size() - 1; i >= 0; --i) {
                    final Set<ReadByMarker> markersForMessage = messageList.get(i).getReadByMarkers();
                    final List<MucOptions.User> shownMarkers = new ArrayList<>();
                    for (ReadByMarker marker : markersForMessage) {
                        if (!ReadByMarker.contains(marker, addedMarkers)) {
                            addedMarkers.add(marker); //may be put outside this condition. set should do dedup anyway
                            MucOptions.User user = mucOptions.findUser(marker);
                            if (user != null) {
                                shownMarkers.add(user);
                            }
                        }
                    }
                    final ReadByMarker markerForSender = ReadByMarker.from(messageList.get(i));
                    final Message statusMessage;
                    final int size = shownMarkers.size();
                    if (size > 1) {
                        final String body;
                        if (size <= 4) {
                            body = getString(R.string.contacts_have_read_up_to_this_point, UIHelper.concatNames(shownMarkers));
                        } else if (ReadByMarker.allUsersRepresented(allUsers, markersForMessage, markerForSender)) {
                            body = getString(R.string.everyone_has_read_up_to_this_point);
                        } else {
                            body = getString(R.string.contacts_and_n_more_have_read_up_to_this_point, UIHelper.concatNames(shownMarkers, 3), size - 3);
                        }
                        statusMessage = Message.createStatusMessage(conversation, body);
                        statusMessage.setCounterparts(shownMarkers);
                    } else if (size == 1) {
                        statusMessage = Message.createStatusMessage(conversation, getString(R.string.contact_has_read_up_to_this_point, UIHelper.getDisplayName(shownMarkers.get(0))));
                        statusMessage.setCounterpart(shownMarkers.get(0).getFullJid());
                        statusMessage.setTrueCounterpart(shownMarkers.get(0).getRealJid());
                    } else {
                        statusMessage = null;
                    }
                    if (statusMessage != null) {
                        this.messageList.add(i + 1, statusMessage);
                    }
                    addedMarkers.add(markerForSender);
                    if (ReadByMarker.allUsersRepresented(allUsers, addedMarkers)) {
                        break;
                    }
                }
            }
        }
    }


    private void stopScrolling() {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        binding.messagesView.dispatchTouchEvent(cancel);
    }

    private boolean showLoadMoreMessages(final Conversation c) {
        if (activity == null || activity.xmppConnectionService == null) {
            return false;
        }
        final boolean mam = hasMamSupport(c) && !c.getContact().isBlocked();
        final MessageArchiveService service = activity.xmppConnectionService.getMessageArchiveService();
        return mam && (c.getLastClearHistory().getTimestamp() != 0 || (c.countMessages() == 0 && c.messagesLoaded.get() && c.hasMessagesLeftOnServer() && !service.queryInProgress(c)));
    }

    private boolean hasMamSupport(final Conversation c) {
        if (c.getMode() == Conversation.MODE_SINGLE) {
            final XmppConnection connection = c.getAccount().getXmppConnection();
            return connection != null && connection.getFeatures().mam();
        } else {
            return c.getMucOptions().mamSupport();
        }
    }

    protected void showSnackbar(final String message, final int action, final OnClickListener clickListener) {
        this.binding.snackbar.setVisibility(View.VISIBLE);
        this.binding.snackbar.setOnClickListener(null);
        this.binding.snackbarMessage.setText(message);
        this.binding.snackbarMessage.setOnClickListener(null);
        this.binding.snackbarAction.setVisibility(clickListener == null ? GONE : View.VISIBLE);
        if (action != 0) {
            this.binding.snackbarAction.setText(action);
        }
        this.binding.snackbarAction.setOnClickListener(clickListener);
    }

    protected void showSnackbar(final int message, final int action, final OnClickListener clickListener) {
        showSnackbar(message, action, clickListener, null);
    }

    protected void showSnackbar(final int message, final int action, final OnClickListener clickListener, final View.OnLongClickListener longClickListener) {
        this.binding.snackbar.setVisibility(View.VISIBLE);
        this.binding.snackbar.setOnClickListener(null);
        this.binding.snackbarMessage.setText(message);
        this.binding.snackbarMessage.setOnClickListener(null);
        this.binding.snackbarAction.setVisibility(clickListener == null ? GONE : View.VISIBLE);
        if (action != 0) {
            this.binding.snackbarAction.setText(action);
        }
        this.binding.snackbarAction.setOnClickListener(clickListener);
        this.binding.snackbarAction.setOnLongClickListener(longClickListener);
    }

    protected void hideSnackbar() {
        this.binding.snackbar.setVisibility(GONE);
    }

    protected void sendMessage(Message message) {
        new Thread(() -> activity.xmppConnectionService.sendMessage(message)).start();
        messageSent();
    }

    protected void sendPgpMessage(final Message message) {
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();
        if (!activity.hasPgp()) {
            activity.showInstallPgpDialog();
            return;
        }
        if (conversation.getAccount().getPgpSignature() == null) {
            activity.announcePgp(conversation.getAccount(), conversation, null, activity.onOpenPGPKeyPublished);
            return;
        }
        if (!mSendingPgpMessage.compareAndSet(false, true)) {
            Log.d(Config.LOGTAG, "sending pgp message already in progress");
        }
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            if (contact.getPgpKeyId() != 0) {
                xmppService.getPgpEngine().hasKey(contact,
                        new UiCallback<Contact>() {

                            @Override
                            public void userInputRequired(PendingIntent pi, Contact contact) {
                                startPendingIntent(pi, REQUEST_ENCRYPT_MESSAGE);
                            }

                            @Override
                            public void progress(int progress) {

                            }

                            @Override
                            public void showToast() {
                            }

                            @Override
                            public void success(Contact contact) {
                                encryptTextMessage(message);
                            }

                            @Override
                            public void error(int error, Contact contact) {
                                activity.runOnUiThread(() -> ToastCompat.makeText(activity,
                                        R.string.unable_to_connect_to_keychain,
                                        ToastCompat.LENGTH_SHORT
                                ).show());
                                mSendingPgpMessage.set(false);
                            }
                        });

            } else {
                showNoPGPKeyDialog(false,
                        (dialog, which) -> {
                            conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                            xmppService.updateConversation(conversation);
                            message.setEncryption(Message.ENCRYPTION_NONE);
                            xmppService.sendMessage(message);
                            messageSent();
                        });
            }
        } else {
            if (conversation.getMucOptions().pgpKeysInUse()) {
                if (!conversation.getMucOptions().everybodyHasKeys()) {
                    Toast warning = ToastCompat
                            .makeText(getActivity(),
                                    R.string.missing_public_keys,
                                    ToastCompat.LENGTH_LONG);
                    warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                    warning.show();
                }
                encryptTextMessage(message);
            } else {
                showNoPGPKeyDialog(true,
                        (dialog, which) -> {
                            conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                            message.setEncryption(Message.ENCRYPTION_NONE);
                            xmppService.updateConversation(conversation);
                            xmppService.sendMessage(message);
                            messageSent();
                        });
            }
        }
    }

    public void encryptTextMessage(Message message) {
        activity.xmppConnectionService.getPgpEngine().encrypt(message,
                new UiCallback<Message>() {

                    @Override
                    public void userInputRequired(PendingIntent pi, Message message) {
                        startPendingIntent(pi, REQUEST_SEND_MESSAGE);
                    }

                    @Override
                    public void progress(int progress) {

                    }

                    @Override
                    public void showToast() {
                    }

                    @Override
                    public void success(Message message) {
                        // TODO the following two call can be made before the callback
                        getActivity().runOnUiThread(() -> messageSent());
                    }

                    @Override
                    public void error(final int error, Message message) {
                        getActivity().runOnUiThread(() -> {
                            doneSendingPgpMessage();
                            ToastCompat.makeText(getActivity(), error == 0 ? R.string.unable_to_connect_to_keychain : error, ToastCompat.LENGTH_SHORT).show();
                        });

                    }
                });
    }

    public void showNoPGPKeyDialog(boolean plural, DialogInterface.OnClickListener listener) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        if (plural) {
            builder.setTitle(getString(R.string.no_pgp_keys));
            builder.setMessage(getText(R.string.contacts_have_no_pgp_keys));
        } else {
            builder.setTitle(getString(R.string.no_pgp_key));
            builder.setMessage(getText(R.string.contact_has_no_pgp_key));
        }
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.send_unencrypted), listener);
        builder.create().show();
    }
    protected void sendOtrMessage(final Message message) {
        final ConversationsActivity activity = (ConversationsActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        activity.selectPresence(conversation,
                () -> {
                    message.setCounterpart(conversation.getNextCounterpart());
                    xmppService.sendMessage(message);
                    messageSent();
                });
    }
    public void appendText(String text, final boolean doNotAppend) {
        if (text == null) {
            return;
        }
        final Editable editable = this.binding.textinput.getText();
        String previous = editable == null ? "" : editable.toString();
        if (doNotAppend && !TextUtils.isEmpty(previous)) {
            ToastCompat.makeText(getActivity(), R.string.already_drafting_message, ToastCompat.LENGTH_LONG).show();
            return;
        }
        if (UIHelper.isLastLineQuote(previous)) {
            text = '\n' + text;
        } else if (previous.length() != 0 && !Character.isWhitespace(previous.charAt(previous.length() - 1))) {
            text = " " + text;
        }
        this.binding.textinput.append(text);
    }

    @Override
    public boolean onEnterPressed(final boolean isCtrlPressed) {
        if (isCtrlPressed || enterIsSend()) {
            sendMessage();
            return true;
        }
        return false;
    }

    private boolean enterIsSend() {
        final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(getActivity());
        return p.getBoolean("enter_is_send", getResources().getBoolean(R.bool.enter_is_send));
    }

    public boolean onArrowUpCtrlPressed() {
        final Message lastEditableMessage = conversation == null ? null : conversation.getLastEditableMessage();
        if (lastEditableMessage != null) {
            correctMessage(lastEditableMessage);
            return true;
        } else {
            ToastCompat.makeText(getActivity(), R.string.could_not_correct_message, ToastCompat.LENGTH_LONG).show();
            return false;
        }
    }

    @Override
    public void onTypingStarted() {
        final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
        if (service == null) {
            return;
        }
        final Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.COMPOSING)) {
            service.sendChatState(conversation);
        }
        runOnUiThread(this::updateSendButton);

    }

    @Override
    public void onTypingStopped() {
        final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
        if (service == null) {
            return;
        }
        final Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.PAUSED)) {
            service.sendChatState(conversation);
        }
    }

    @Override
    public void onTextDeleted() {
        final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
        if (service == null) {
            return;
        }
        final Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(Config.DEFAULT_CHAT_STATE)) {
            service.sendChatState(conversation);
        }
        runOnUiThread(() -> {
            if (activity == null) {
                return;
            }
            activity.onConversationsListItemUpdated();
        });
        runOnUiThread(this::updateSendButton);
    }

    @Override
    public void onTextChanged() {
        if (conversation != null && conversation.getCorrectingMessage() != null) {
            runOnUiThread(this::updateSendButton);
        }
    }

    @Override
    public boolean onTabPressed(boolean repeated) {
        if (conversation == null || conversation.getMode() == Conversation.MODE_SINGLE) {
            return false;
        }
        if (repeated) {
            completionIndex++;
        } else {
            lastCompletionLength = 0;
            completionIndex = 0;
            final String content = this.binding.textinput.getText().toString();
            lastCompletionCursor = this.binding.textinput.getSelectionEnd();
            int start = lastCompletionCursor > 0 ? content.lastIndexOf(" ", lastCompletionCursor - 1) + 1 : 0;
            firstWord = start == 0;
            incomplete = content.substring(start, lastCompletionCursor);
        }
        List<String> completions = new ArrayList<>();
        for (MucOptions.User user : conversation.getMucOptions().getUsers()) {
            String name = user.getNick();
            if (name != null && name.startsWith(incomplete)) {
                completions.add(name + (firstWord ? ": " : " "));
            }
        }
        Collections.sort(completions);
        if (completions.size() > completionIndex) {
            String completion = completions.get(completionIndex).substring(incomplete.length());
            this.binding.textinput.getEditableText().delete(lastCompletionCursor, lastCompletionCursor + lastCompletionLength);
            this.binding.textinput.getEditableText().insert(lastCompletionCursor, completion);
            lastCompletionLength = completion.length();
        } else {
            completionIndex = -1;
            this.binding.textinput.getEditableText().delete(lastCompletionCursor, lastCompletionCursor + lastCompletionLength);
            lastCompletionLength = 0;
        }
        return true;
    }

    private boolean messageContainsQuery(Message m, String q) {
        return m != null && m.getMergedBody().toString().toLowerCase().contains(q.toLowerCase());
    }

        private void startPendingIntent(PendingIntent pendingIntent, int requestCode) {
            try {
                getActivity()
                        .startIntentSenderForResult(
                                pendingIntent.getIntentSender(), requestCode, null, 0, 0, 0, Compatibility.pgpStartIntentSenderOptions());
            } catch (final SendIntentException ignored) {
            }
        }

    @Override
    public void onBackendConnected() {
        Log.d(Config.LOGTAG, "ConversationFragment.onBackendConnected()");
        String uuid = pendingConversationsUuid.pop();

        if (uuid != null) {
            if (!findAndReInitByUuidOrArchive(uuid)) {
                return;
            }
        } else {
            if (!activity.xmppConnectionService.isConversationStillOpen(conversation)) {
                clearPending();
                activity.onConversationArchived(conversation);
                return;
            }
        }
        ActivityResult activityResult = postponedActivityResult.pop();
        if (activityResult != null) {
            handleActivityResult(activityResult);
        }
        setupEmojiSearch();
        clearPending();
    }

    private boolean findAndReInitByUuidOrArchive(@NonNull final String uuid) {
        Conversation conversation = activity.xmppConnectionService.findConversationByUuid(uuid);
        if (conversation == null) {
            clearPending();
            activity.onConversationArchived(null);
            return false;
        }
        reInit(conversation);
        ScrollState scrollState = pendingScrollState.pop();
        String lastMessageUuid = pendingLastMessageUuid.pop();
        List<Attachment> attachments = pendingMediaPreviews.pop();
        if (scrollState != null) {
            setScrollPosition(scrollState, lastMessageUuid);
        }
        if (attachments != null && attachments.size() > 0) {
            Log.d(Config.LOGTAG, "had attachments on restore");
            mediaPreviewAdapter.addMediaPreviews(attachments);
            toggleInputMethod();
        }
        return true;
    }

    private void clearPending() {
        if (postponedActivityResult.clear()) {
            Log.e(Config.LOGTAG, "cleared pending intent with unhandled result left");
            if (pendingTakePhotoUri.clear()) {
                Log.e(Config.LOGTAG, "cleared pending photo uri");
            }
        }
        if (pendingScrollState.clear()) {
            Log.e(Config.LOGTAG, "cleared scroll state");
        }
        if (pendingConversationsUuid.clear()) {
            Log.e(Config.LOGTAG, "cleared pending conversations uuid");
        }
        if (pendingMediaPreviews.clear()) {
            Log.e(Config.LOGTAG, "cleared pending media previews");
        }
    }

    public Conversation getConversation() {
        return conversation;
    }

    @Override
    public void onContactPictureLongClicked(View v, final Message message) {
        final String fingerprint;
        if (message.getEncryption() == Message.ENCRYPTION_PGP || message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
            fingerprint = "pgp";
        } else {
            fingerprint = message.getFingerprint();
        }
        final PopupMenu popupMenu = new PopupMenu(getActivity(), v);
        final Contact contact = message.getContact();
        if (message.getStatus() <= Message.STATUS_RECEIVED && (contact == null || !contact.isSelf())) {
            if (message.getConversation().getMode() == Conversation.MODE_MULTI) {
                final Jid cp = message.getCounterpart();
                if (cp == null || cp.isBareJid()) {
                    return;
                }
                final Jid tcp = message.getTrueCounterpart();
                final User userByRealJid = tcp != null ? conversation.getMucOptions().findOrCreateUserByRealJid(tcp, cp) : null;
                final String occupantId = message.getOccupantId();
                final User userByOccupantId =
                        occupantId != null
                                ? conversation.getMucOptions().findUserByOccupantId(occupantId)
                                : null;
                final User user = userByRealJid != null ? userByRealJid : (userByOccupantId != null ? userByOccupantId : conversation.getMucOptions().findUserByFullJid(cp));
                if (user == null) return;
                popupMenu.inflate(R.menu.muc_details_context);
                final Menu menu = popupMenu.getMenu();
                MucDetailsContextMenuHelper.configureMucDetailsContextMenu(activity, menu, conversation, user, true, getUsername(message));
                popupMenu.setOnMenuItemClickListener(menuItem -> MucDetailsContextMenuHelper.onContextItemSelected(menuItem, user, activity, fingerprint));
            } else {
                popupMenu.inflate(R.menu.one_on_one_context);
                popupMenu.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.action_show_avatar) {
                        activity.ShowAvatarPopup(activity, contact);
                    } else if (itemId == R.id.action_contact_details) {
                        activity.switchToContactDetails(message.getContact(), fingerprint);
                    } else if (itemId == R.id.action_show_qr_code) {
                        activity.showQrCode("xmpp:" + message.getContact().getJid().asBareJid().toEscapedString());
                    }
                    return true;
                });
            }
        } else {
            popupMenu.inflate(R.menu.account_context);
            final Menu menu = popupMenu.getMenu();
            popupMenu.setOnMenuItemClickListener(item -> {
                final XmppActivity activity = this.activity;
                if (activity == null) {
                    Log.e(Config.LOGTAG, "Unable to perform action. no context provided");
                    return true;
                }
                int itemId = item.getItemId();
                if (itemId == R.id.action_show_qr_code) {
                    activity.showQrCode(conversation.getAccount().getShareableUri());
                } else if (itemId == R.id.action_account_details) {
                    activity.switchToAccount(message.getConversation().getAccount(), fingerprint);
                }
                return true;
            });
        }
        popupMenu.show();
    }

    public String getUsername(Message message) {
        if (message == null) {
            return null;
        }
        String user;
        try {
            final Contact contact = message.getContact();
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                if (contact != null) {
                    user = contact.getDisplayName();
                } else {
                    user = UIHelper.getDisplayedMucCounterpart(message.getCounterpart());
                }
            } else {
                user = contact != null ? contact.getDisplayName() : null;
            }
            if (message.getStatus() == Message.STATUS_SEND
                    || message.getStatus() == Message.STATUS_SEND_FAILED
                    || message.getStatus() == Message.STATUS_SEND_RECEIVED
                    || message.getStatus() == Message.STATUS_SEND_DISPLAYED) {
                user = getString(R.string.me);
            }
        } catch (Exception e) {
            e.printStackTrace();
            user = null;
        }
        return user;
    }

    @Override
    public void onContactPictureClicked(Message message) {

        final boolean received = message.getStatus() <= Message.STATUS_RECEIVED;
        if (received) {
            if (message.getConversation() instanceof Conversation && message.getConversation().getMode() == Conversation.MODE_MULTI) {
                Jid tcp = message.getTrueCounterpart();
                Jid user = message.getCounterpart();
                if (user != null && !user.isBareJid()) {
                    final MucOptions mucOptions =
                            ((Conversation) message.getConversation()).getMucOptions();
                    if (mucOptions.participating()
                            || ((Conversation) message.getConversation()).getNextCounterpart()
                            != null) {
                        MucOptions.User mucUser = mucOptions.findUserByFullJid(user);
                        MucOptions.User tcpMucUser = mucOptions.findUserByRealJid(tcp == null ? null : tcp.asBareJid());
                        if (mucUser == null && tcpMucUser == null) {
                            Toast.makeText(
                                            getActivity(),
                                            activity.getString(
                                                    R.string.user_has_left_conference,
                                                    user.getResource()),
                                            Toast.LENGTH_SHORT)
                                    .show();
                        }
                        highlightInConference(mucUser == null || mucUser.getNick() == null ? (tcpMucUser == null || tcpMucUser.getNick() == null ? user.getResource() : tcpMucUser.getNick()) : mucUser.getNick());
                    } else {
                        Toast.makeText(
                                        getActivity(),
                                        R.string.you_are_not_participating,
                                        Toast.LENGTH_SHORT)
                                .show();
                    }
                }
            }
        }
    }

    private Activity requireActivity() {
        final Activity activity = getActivity();
        if (activity == null) {
            throw new IllegalStateException("Activity not attached");
        }
        return activity;
    }



    private void deleteMessageFile(final Message message) {
        List<Element> thumbs = selectedMessage.getFileParams() != null ? selectedMessage.getFileParams().getThumbnails() : null;
        if (thumbs != null && !thumbs.isEmpty()) {
            for (Element thumb : thumbs) {
                Uri uri = Uri.parse(thumb.getAttribute("uri"));
                if (uri.getScheme().equals("cid")) {
                    Cid cid = BobTransfer.cid(uri);
                    if (cid == null) continue;
                    DownloadableFile f = activity.xmppConnectionService.getFileForCid(cid);
                    activity.xmppConnectionService.evictPreview(f);
                    f.delete();
                }
            }
        }
        if (activity.xmppConnectionService.getFileBackend().deleteFile(message)) {
            message.setFileDeleted(true);
            activity.xmppConnectionService.evictPreview(activity.xmppConnectionService.getFileBackend().getFile(message));
            activity.xmppConnectionService.updateMessage(message, false);
            activity.onConversationsListItemUpdated();
            refresh();
        }
    }

    private void showTextFormat(final boolean me) {
        this.binding.textformat.setVisibility(View.VISIBLE);
        this.binding.me.setEnabled(me);
        this.binding.me.setOnClickListener(meCommand);
        this.binding.quote.setOnClickListener(quote);
        this.binding.bold.setOnClickListener(boldText);
        this.binding.italic.setOnClickListener(italicText);
        this.binding.monospace.setOnClickListener(monospaceText);
        this.binding.strikethrough.setOnClickListener(strikethroughText);
        this.binding.close.setOnClickListener(close);
        if (Compatibility.runsTwentyEight()) {
            this.binding.me.setTooltipText(activity.getString(R.string.me));
            this.binding.quote.setTooltipText(activity.getString(R.string.quote));
            this.binding.bold.setTooltipText(activity.getString(R.string.bold));
            this.binding.italic.setTooltipText(activity.getString(R.string.italic));
            this.binding.monospace.setTooltipText(activity.getString(R.string.monospace));
            this.binding.monospace.setTooltipText(activity.getString(R.string.monospace));
            this.binding.strikethrough.setTooltipText(activity.getString(R.string.strikethrough));
            this.binding.close.setTooltipText(activity.getString(R.string.close));
        }
    }

    private void hideTextFormat() {
        this.binding.textformat.setVisibility(View.GONE);
    }
}
