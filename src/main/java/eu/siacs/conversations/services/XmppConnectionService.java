package eu.siacs.conversations.services;

import static eu.siacs.conversations.persistance.FileBackend.APP_DIRECTORY;
import static eu.siacs.conversations.persistance.FileBackend.AUDIOS;
import static eu.siacs.conversations.persistance.FileBackend.FILES;
import static eu.siacs.conversations.persistance.FileBackend.IMAGES;
import static eu.siacs.conversations.persistance.FileBackend.VIDEOS;
import static eu.siacs.conversations.ui.SettingsActivity.ALLOW_MESSAGE_CORRECTION;
import static eu.siacs.conversations.ui.SettingsActivity.ALLOW_MESSAGE_RETRACTION;
import static eu.siacs.conversations.ui.SettingsActivity.AUTOMATIC_ATTACHMENT_DELETION;
import static eu.siacs.conversations.ui.SettingsActivity.CHAT_STATES;
import static eu.siacs.conversations.ui.SettingsActivity.CONFIRM_MESSAGES;
import static eu.siacs.conversations.ui.SettingsActivity.ENABLE_MULTI_ACCOUNTS;
import static eu.siacs.conversations.ui.SettingsActivity.INDICATE_RECEIVED;
import static eu.siacs.conversations.ui.SettingsActivity.SHOW_OWN_ACCOUNTS;
import static eu.siacs.conversations.ui.SettingsActivity.USE_INNER_STORAGE;
import static eu.siacs.conversations.utils.Compatibility.hasStoragePermission;
import static eu.siacs.conversations.utils.RichPreview.RICH_LINK_METADATA;
import static eu.siacs.conversations.utils.Random.SECURE_RANDOM;
import static eu.siacs.conversations.utils.StorageHelper.getAppMediaDirectory;

import android.content.pm.ServiceInfo;

import com.google.common.io.Files;
import com.kedia.ogparser.JsoupProxy;
import com.kedia.ogparser.OpenGraphCallback;
import com.kedia.ogparser.OpenGraphParser;
import com.kedia.ogparser.OpenGraphResult;
import java.util.HashMap;

import de.monocles.mod.EmojiSearch;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.persistance.UnifiedPushDatabase;
import eu.siacs.conversations.xmpp.OnGatewayResult;
import eu.siacs.conversations.utils.Consumer;
import java.net.URI;
import static eu.siacs.conversations.utils.Compatibility.s;
import android.Manifest;
import androidx.annotation.RequiresApi;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import java.security.SecureRandom;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.security.KeyChain;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.util.Pair;

import net.java.otr4j.OtrException;
import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionID;
import net.java.otr4j.session.SessionImpl;
import net.java.otr4j.session.SessionStatus;

import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.utils.ThemeHelper;
import eu.siacs.conversations.xmpp.jid.OtrJidHelper;
import eu.siacs.conversations.xmpp.Jid;

import androidx.annotation.BoolRes;
import androidx.annotation.IntegerRes;
import androidx.annotation.Nullable;
import androidx.core.app.RemoteInput;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy;

import de.monocles.mod.WebxdcUpdate;

import org.conscrypt.Conscrypt;
import org.openintents.openpgp.IOpenPgpService2;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;
import com.google.common.base.Optional;

import eu.siacs.conversations.utils.Emoticons;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.File;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.android.JabberIdContact;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.crypto.PgpDecryptionService;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlMessage;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.OnRenameListener;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.entities.PresenceTemplate;
import eu.siacs.conversations.entities.Roster;
import eu.siacs.conversations.entities.ServiceDiscoveryResult;
import eu.siacs.conversations.generator.AbstractGenerator;
import eu.siacs.conversations.generator.IqGenerator;
import eu.siacs.conversations.generator.MessageGenerator;
import eu.siacs.conversations.generator.PresenceGenerator;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.parser.AbstractParser;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.parser.MessageParser;
import eu.siacs.conversations.parser.PresenceParser;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.ChooseAccountForProfilePictureActivity;
import eu.siacs.conversations.ui.RtpSessionActivity;
import eu.siacs.conversations.ui.SettingsActivity;
import eu.siacs.conversations.ui.UiCallback;
import eu.siacs.conversations.ui.interfaces.OnAvatarPublication;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.ui.interfaces.OnMediaLoaded;
import eu.siacs.conversations.ui.interfaces.OnSearchResultsAvailable;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.ConversationsFileObserver;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.EasyOnboardingInvite;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.MimeUtils;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.utils.QuickLoader;
import eu.siacs.conversations.utils.ReplacingSerialSingleThreadExecutor;
import eu.siacs.conversations.utils.ReplacingTaskManager;
import eu.siacs.conversations.utils.Resolver;
import eu.siacs.conversations.utils.SerialSingleThreadExecutor;
import eu.siacs.conversations.utils.StorageHelper;
import eu.siacs.conversations.utils.StringUtils;
import eu.siacs.conversations.utils.TorServiceUtils;
import eu.siacs.conversations.utils.TranscoderStrategies;
import eu.siacs.conversations.utils.WakeLockHelper;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.LocalizedContent;
import eu.siacs.conversations.xmpp.InvalidJid;
import eu.siacs.conversations.xmpp.OnBindListener;
import eu.siacs.conversations.xmpp.OnContactStatusChanged;
import eu.siacs.conversations.xmpp.OnGatewayPromptResult;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.OnKeyStatusUpdated;
import eu.siacs.conversations.xmpp.OnMessageAcknowledged;
import eu.siacs.conversations.xmpp.OnMessagePacketReceived;
import eu.siacs.conversations.xmpp.OnPresencePacketReceived;
import eu.siacs.conversations.xmpp.OnStatusChanged;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.jingle.AbstractJingleConnection;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import eu.siacs.conversations.xmpp.jingle.Media;
import eu.siacs.conversations.xmpp.jingle.RtpEndUserState;
import eu.siacs.conversations.xmpp.mam.MamReference;
import eu.siacs.conversations.xmpp.pep.Avatar;
import eu.siacs.conversations.xmpp.pep.PublishOptions;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import io.ipfs.cid.Cid;
import me.leolin.shortcutbadger.ShortcutBadger;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class XmppConnectionService extends Service {

    public static final String ACTION_REPLY_TO_CONVERSATION = "reply_to_conversations";
    public static final String ACTION_MARK_AS_READ = "mark_as_read";
    public static final String ACTION_SNOOZE = "snooze";
    public static final String ACTION_CLEAR_MESSAGE_NOTIFICATION = "clear_message_notification";
    public static final String ACTION_CLEAR_MISSED_CALL_NOTIFICATION = "clear_missed_call_notification";
    public static final String ACTION_DISMISS_ERROR_NOTIFICATIONS = "dismiss_error";
    public static final String ACTION_TRY_AGAIN = "try_again";
    public static final String ACTION_TEMPORARILY_DISABLE = "temporarily_disable";
    public static final String ACTION_PING = "ping";
    public static final String ACTION_IDLE_PING = "idle_ping";
    public static final String ACTION_INTERNAL_PING = "internal_ping";
    public static final String ACTION_FCM_TOKEN_REFRESH = "fcm_token_refresh";
    public static final String ACTION_FCM_MESSAGE_RECEIVED = "fcm_message_received";
    public static final String ACTION_DISMISS_CALL = "dismiss_call";
    public static final String ACTION_END_CALL = "end_call";
    public static final String ACTION_STARTING_CALL = "starting_call";
    public static final String ACTION_PROVISION_ACCOUNT = "provision_account";
    public static final String ACTION_CALL_INTEGRATION_SERVICE_STARTED = "call_integration_service_started";
    private static final String ACTION_POST_CONNECTIVITY_CHANGE = "eu.siacs.conversations.POST_CONNECTIVITY_CHANGE";
    public static final String ACTION_RENEW_UNIFIED_PUSH_ENDPOINTS = "eu.siacs.conversations.UNIFIED_PUSH_RENEW";
    public static final String ACTION_QUICK_LOG = "eu.siacs.conversations.QUICK_LOG";
    public static final String FDroid = "org.fdroid.fdroid";
    public static final String PlayStore = "com.android.vending";
    private static final String SETTING_LAST_ACTIVITY_TS = "last_activity_timestamp";
    private EmojiSearch emojiSearch = null;

    public final CountDownLatch restoredFromDatabaseLatch = new CountDownLatch(1);
    private final static Executor FILE_OBSERVER_EXECUTOR = Executors.newSingleThreadExecutor();
    private final static Executor FILE_ATTACHMENT_EXECUTOR = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService internalPingExecutor = Executors.newSingleThreadScheduledExecutor();
    private final static SerialSingleThreadExecutor VIDEO_COMPRESSION_EXECUTOR = new SerialSingleThreadExecutor("VideoCompression");
    private final SerialSingleThreadExecutor mDatabaseWriterExecutor = new SerialSingleThreadExecutor("DatabaseWriter");
    private final SerialSingleThreadExecutor mDatabaseReaderExecutor = new SerialSingleThreadExecutor("DatabaseReader");
    private final SerialSingleThreadExecutor mNotificationExecutor = new SerialSingleThreadExecutor("NotificationExecutor");
    public final Executor mWebPreviewExecutor = Executors.newFixedThreadPool(3);
    public final SerialSingleThreadExecutor mNotificationChannelExecutor = new SerialSingleThreadExecutor("updateNotificationChannels");
    public final SerialSingleThreadExecutor mMessageResendTaskExecuter = new SerialSingleThreadExecutor("MessageResender");
    private final ReplacingTaskManager mRosterSyncTaskManager = new ReplacingTaskManager();
    private final IBinder mBinder = new XmppConnectionBinder();
    private final List<Conversation> conversations = new CopyOnWriteArrayList<>();
    private final IqGenerator mIqGenerator = new IqGenerator(this);
    private final Set<String> mInProgressAvatarFetches = new HashSet<>();
    private final Set<String> mOmittedPepAvatarFetches = new HashSet<>();
    private final HashSet<Jid> mLowPingTimeoutMode = new HashSet<>();
    private final OnIqPacketReceived mDefaultIqHandler = (account, packet) -> {
        if (packet.getType() != IqPacket.TYPE.RESULT) {
            Element error = packet.findChild("error");
            String text = error != null ? error.findChildContent("text") : null;
            if (text != null) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received iq error - " + text);
            }
        }
    };
    public DatabaseBackend databaseBackend;
    private Multimap<String, String> mutedMucUsers;
    private final ReplacingSerialSingleThreadExecutor mContactMergerExecutor = new ReplacingSerialSingleThreadExecutor("ContactMerger");
    private final ReplacingSerialSingleThreadExecutor mStickerScanExecutor = new ReplacingSerialSingleThreadExecutor("StickerScan");
    private long mLastActivity = 0;
    private long mLastMucPing = 0;
    private Map<String, Message> mScheduledMessages = new HashMap<>();
    private long mLastStickerRescan = 0;
    public final FileBackend fileBackend = new FileBackend(this);
    private MemorizingTrustManager mMemorizingTrustManager;
    private final NotificationService mNotificationService = new NotificationService(this);
    private final UnifiedPushBroker unifiedPushBroker = new UnifiedPushBroker(this);
    private final ChannelDiscoveryService mChannelDiscoveryService = new ChannelDiscoveryService(this);
    private final ShortcutService mShortcutService = new ShortcutService(this);
    private final AtomicBoolean mInitialAddressbookSyncCompleted = new AtomicBoolean(false);
    private final AtomicBoolean mForceForegroundService = new AtomicBoolean(false);
    private final AtomicBoolean mForceDuringOnCreate = new AtomicBoolean(false);
    private final AtomicReference<OngoingCall> ongoingCall = new AtomicReference<>();
    private final OnMessagePacketReceived mMessageParser = new MessageParser(this);
    private final OnPresencePacketReceived mPresenceParser = new PresenceParser(this);
    private final IqParser mIqParser = new IqParser(this);
    private final MessageGenerator mMessageGenerator = new MessageGenerator(this);
    public OnContactStatusChanged onContactStatusChanged = (contact, online) -> {
        Conversation conversation = find(getConversations(), contact);
        if (conversation != null) {
            if (online) {
                conversation.endOtrIfNeeded();
                if (contact.getPresences().size() == 1) {
                    sendUnsentMessages(conversation);
                }
            } else {
                //check if the resource we are haveing a conversation with is still online
                if (conversation.hasValidOtrSession()) {
                    String otrResource = conversation.getOtrSession().getSessionID().getUserID();
                    if (!(Arrays.asList(contact.getPresences().toResourceArray()).contains(otrResource))) {
                        conversation.endOtrIfNeeded();
                    }
                }
            }
        }
        if (contact.getPresences().anyIdentity("gateway", "pstn")) {
            contact.registerAsPhoneAccount(this);
        }
    };
    private final PresenceGenerator mPresenceGenerator = new PresenceGenerator(this);
    private List<Account> accounts;
    private final JingleConnectionManager mJingleConnectionManager = new JingleConnectionManager(this);
    public final HttpConnectionManager mHttpConnectionManager = new HttpConnectionManager(this);
    private final AvatarService mAvatarService = new AvatarService(this);
    private final MessageArchiveService mMessageArchiveService = new MessageArchiveService(this);
    private final PushManagementService mPushManagementService = new PushManagementService(this);
    private final QuickConversationsService mQuickConversationsService = new QuickConversationsService(this);
    private final ConversationsFileObserver fileObserver = new ConversationsFileObserver(
            Environment.getExternalStorageDirectory().getAbsolutePath()
    ) {
        @Override
        public void onEvent(final int event, final File file) {
            markFileDeleted(file);
        }
    };
    private final OnMessageAcknowledged mOnMessageAcknowledgedListener = new OnMessageAcknowledged() {

        @Override
        public boolean onMessageAcknowledged(final Account account, final Jid to, final String id) {
            if (id.startsWith(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX)) {
                final String sessionId = id.substring(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX.length());
                mJingleConnectionManager.updateProposedSessionDiscovered(
                        account,
                        to,
                        sessionId,
                        JingleConnectionManager.DeviceDiscoveryState.SEARCHING_ACKNOWLEDGED
                );
            }


            final Jid bare = to.asBareJid();

            for (final Conversation conversation : getConversations()) {
                if (conversation.getAccount() == account && conversation.getJid().asBareJid().equals(bare)) {
                    final Message message = conversation.findUnsentMessageWithUuid(id);
                    if (message != null) {
                        message.setStatus(Message.STATUS_SEND);
                        message.setErrorMessage(null);
                        databaseBackend.updateMessage(message, false);
                        return true;
                    }
                }
            }
            return false;
        }
    };
    private final AtomicBoolean isPhoneInCall = new AtomicBoolean(false);
    private final AtomicBoolean diallerIntegrationActive = new AtomicBoolean(false);

    private LruCache<String, Drawable> mDrawableCache;

    public void setDiallerIntegrationActive(boolean active) {
        diallerIntegrationActive.set(active);
    }

    //Ui callback listeners
    private final Set<OnConversationUpdate> mOnConversationUpdates = Collections.newSetFromMap(new WeakHashMap<OnConversationUpdate, Boolean>());
    private final Set<OnShowErrorToast> mOnShowErrorToasts = Collections.newSetFromMap(new WeakHashMap<OnShowErrorToast, Boolean>());
    private final Set<OnAccountUpdate> mOnAccountUpdates = Collections.newSetFromMap(new WeakHashMap<OnAccountUpdate, Boolean>());
    private final Set<OnCaptchaRequested> mOnCaptchaRequested = Collections.newSetFromMap(new WeakHashMap<OnCaptchaRequested, Boolean>());
    private final Set<OnRosterUpdate> mOnRosterUpdates = Collections.newSetFromMap(new WeakHashMap<OnRosterUpdate, Boolean>());
    private final Set<OnUpdateBlocklist> mOnUpdateBlocklist = Collections.newSetFromMap(new WeakHashMap<OnUpdateBlocklist, Boolean>());
    private final Set<OnMucRosterUpdate> mOnMucRosterUpdate = Collections.newSetFromMap(new WeakHashMap<OnMucRosterUpdate, Boolean>());
    private final Set<OnKeyStatusUpdated> mOnKeyStatusUpdated = Collections.newSetFromMap(new WeakHashMap<OnKeyStatusUpdated, Boolean>());
    private final Set<OnJingleRtpConnectionUpdate> onJingleRtpConnectionUpdate = Collections.newSetFromMap(new WeakHashMap<OnJingleRtpConnectionUpdate, Boolean>());

    private final Object LISTENER_LOCK = new Object();
    public final Set<String> FILENAMES_TO_IGNORE_DELETION = new HashSet<>();
    private final OnBindListener mOnBindListener = new OnBindListener() {

        @Override
        public void onBind(final Account account) {
            synchronized (mInProgressAvatarFetches) {
                for (Iterator<String> iterator = mInProgressAvatarFetches.iterator(); iterator.hasNext(); ) {
                    final String KEY = iterator.next();
                    if (KEY.startsWith(account.getJid().asBareJid() + "_")) {
                        iterator.remove();
                    }
                }
            }
            boolean loggedInSuccessfully = account.setOption(Account.OPTION_LOGGED_IN_SUCCESSFULLY, true);
            boolean gainedFeature = account.setOption(Account.OPTION_HTTP_UPLOAD_AVAILABLE, account.getXmppConnection().getFeatures().httpUpload(0));
            if (loggedInSuccessfully || gainedFeature) {
                databaseBackend.updateAccount(account);
            }

            if (loggedInSuccessfully) {
                if (!TextUtils.isEmpty(account.getDisplayName())) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": display name wasn't empty on first log in. publishing");
                    publishDisplayName(account);
                }
            }

            account.getRoster().clearPresences();
            synchronized (account.inProgressConferenceJoins) {
                account.inProgressConferenceJoins.clear();
            }
            synchronized (account.inProgressConferencePings) {
                account.inProgressConferencePings.clear();
            }
            mJingleConnectionManager.notifyRebound(account);
            mQuickConversationsService.considerSyncBackground(false);
            fetchRosterFromServer(account);
            final XmppConnection connection = account.getXmppConnection();
            if (connection != null && connection.getFeatures().bookmarks2()) {
                fetchBookmarks2(account);
            } else if (!account.getXmppConnection().getFeatures().bookmarksConversion()) {
                fetchBookmarks(account);
            }

            if (connection != null && connection.getFeatures().mds()) {
                fetchMessageDisplayedSynchronization(account);
            } else {
                Log.d(Config.LOGTAG,account.getJid()+": server has no support for mds");
            }
            final boolean flexible = account.getXmppConnection().getFeatures().flexibleOfflineMessageRetrieval();
            final boolean catchup = getMessageArchiveService().inCatchup(account);
            final boolean trackOfflineMessageRetrieval;
            if (flexible && catchup && account.getXmppConnection().isMamPreferenceAlways()) {
                trackOfflineMessageRetrieval = false;
                sendIqPacket(account, mIqGenerator.purgeOfflineMessages(), (acc, packet) -> {
                    if (packet.getType() == IqPacket.TYPE.RESULT) {
                        Log.d(Config.LOGTAG, acc.getJid().asBareJid() + ": successfully purged offline messages");
                    }
                });
            } else {
                trackOfflineMessageRetrieval = true;
            }
            sendPresence(account);
            account.getXmppConnection().trackOfflineMessageRetrieval(trackOfflineMessageRetrieval);
            if (mPushManagementService.available(account)) {
                mPushManagementService.registerPushTokenOnServer(account);
            }
            connectMultiModeConversations(account);
            syncDirtyContacts(account);

            unifiedPushBroker.renewUnifiedPushEndpointsOnBind(account);
        }
    };
    private boolean destroyed = false;
    private int unreadCount = -1;
    private final AtomicLong mLastExpiryRun = new AtomicLong(0);
    private SecureRandom mRandom;
    private final LruCache<Pair<String, String>, ServiceDiscoveryResult> discoCache = new LruCache<>(20);
    private final OnStatusChanged statusListener = new OnStatusChanged() {

        @Override
        public void onStatusChanged(final Account account) {
            XmppConnection connection = account.getXmppConnection();
            updateAccountUi();

            if (account.getStatus() == Account.State.ONLINE || account.getStatus().isError()) {
                mQuickConversationsService.signalAccountStateChange();
            }

            if (account.getStatus() == Account.State.ONLINE) {
                synchronized (mLowPingTimeoutMode) {
                    if (mLowPingTimeoutMode.remove(account.getJid().asBareJid())) {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": leaving low ping timeout mode");
                    }
                }
                if (account.setShowErrorNotification(true)) {
                    databaseBackend.updateAccount(account);
                }
                mMessageArchiveService.executePendingQueries(account);
                if (connection != null && connection.getFeatures().csi()) {
                    if (checkListeners()) {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + " sending csi//inactive");
                        connection.sendInactive();
                    } else {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + " sending csi//active");
                        connection.sendActive();
                    }
                }
                List<Conversation> conversations = getConversations();
                for (Conversation conversation : conversations) {
                    final boolean inProgressJoin;
                    synchronized (account.inProgressConferenceJoins) {
                        inProgressJoin = account.inProgressConferenceJoins.contains(conversation);
                    }
                    final boolean pendingJoin;
                    synchronized (account.pendingConferenceJoins) {
                        pendingJoin = account.pendingConferenceJoins.contains(conversation);
                    }
                    if (conversation.getAccount() == account
                            && !pendingJoin
                            && !inProgressJoin) {
                        if (!conversation.startOtrIfNeeded()) {
                            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": couldn't start OTR with " + conversation.getContact().getJid() + " when needed");
                        }
                        sendUnsentMessages(conversation);
                    }
                }
                final List<Conversation> pendingLeaves;
                synchronized (account.pendingConferenceLeaves) {
                    pendingLeaves = new ArrayList<>(account.pendingConferenceLeaves);
                    account.pendingConferenceLeaves.clear();

                }
                for (Conversation conversation : pendingLeaves) {
                    leaveMuc(conversation);
                }
                final List<Conversation> pendingJoins;
                synchronized (account.pendingConferenceJoins) {
                    pendingJoins = new ArrayList<>(account.pendingConferenceJoins);
                    account.pendingConferenceJoins.clear();
                }
                for (Conversation conversation : pendingJoins) {
                    joinMuc(conversation);
                }
                scheduleWakeUpCall(Config.PING_MAX_INTERVAL, account.getUuid().hashCode());
            } else if (account.getStatus() == Account.State.OFFLINE || account.getStatus() == Account.State.DISABLED || account.getStatus() == Account.State.LOGGED_OUT) {
                resetSendingToWaiting(account);
                if (account.isConnectionEnabled() && isInLowPingTimeoutMode(account)) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": went into offline state during low ping mode. reconnecting now");
                    reconnectAccount(account, true, false);
                } else {
                    final int timeToReconnect = SECURE_RANDOM.nextInt(10) + 2;
                    scheduleWakeUpCall(timeToReconnect, account.getUuid().hashCode());
                }
            } else if (account.getStatus() == Account.State.REGISTRATION_SUCCESSFUL) {
                databaseBackend.updateAccount(account);
                reconnectAccount(account, true, false);
            } else if (account.getStatus() != Account.State.CONNECTING && account.getStatus() != Account.State.NO_INTERNET) {
                resetSendingToWaiting(account);
                if (connection != null && account.getStatus().isAttemptReconnect()) {
                    final boolean aggressive = account.getStatus() == Account.State.SEE_OTHER_HOST
                            || hasJingleRtpConnection(account);
                    final int next = connection.getTimeToNextAttempt(aggressive);
                    final boolean lowPingTimeoutMode = isInLowPingTimeoutMode(account);
                    if (next <= 0) {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": error connecting account. reconnecting now. lowPingTimeout=" + lowPingTimeoutMode);
                        reconnectAccount(account, true, false);
                    } else {
                        final int attempt = connection.getAttempt() + 1;
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": error connecting account. try again in " + next + "s for the " + attempt + " time. lowPingTimeout=" + lowPingTimeoutMode+", aggressive="+aggressive);
                        scheduleWakeUpCall(next, account.getUuid().hashCode());
                        if (aggressive) {
                            internalPingExecutor.schedule(
                                    XmppConnectionService.this::manageAccountConnectionStatesInternal,
                                    (next * 1000L) + 50,
                                    TimeUnit.MILLISECONDS
                            );
                        }
                    }
                }
            }
            getNotificationService().updateErrorNotification();
        }
    };
    private OpenPgpServiceConnection pgpServiceConnection;
    private PgpEngine mPgpEngine = null;
    private WakeLock wakeLock;
    private final BroadcastReceiver mInternalEventReceiver = new InternalEventReceiver();
    private final BroadcastReceiver mInternalRestrictedEventReceiver = new RestrictedEventReceiver(Arrays.asList(TorServiceUtils.ACTION_STATUS));

    private final BroadcastReceiver mInternalScreenEventReceiver = new InternalEventReceiver();

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

    public void LoadStickers() {
        if (!hasStoragePermission(this)) return;
        // Load and show Stickers
        if (!dirStickers.exists()) {
            dirStickers.mkdir();
        }
        if (dirStickers.listFiles() != null) {
            if (dirStickers.isDirectory() && dirStickers.listFiles() != null) {
                filesStickers = dirStickers.listFiles();
                filesPathsStickers = new String[filesStickers.length];
                filesNamesStickers = new String[filesStickers.length];
                for (int i = 0; i < filesStickers.length; i++) {
                    filesPathsStickers[i] = filesStickers[i].getAbsolutePath();
                    filesNamesStickers[i] = filesStickers[i].getName();
                }
            }
        }
    }

    public void LoadGifs() {
        if (!hasStoragePermission(this)) return;
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
    }

    private static String generateFetchKey(Account account, final Avatar avatar) {
        return account.getJid().asBareJid() + "_" + avatar.owner + "_" + avatar.sha1sum;
    }

    private boolean isInLowPingTimeoutMode(Account account) {
        synchronized (mLowPingTimeoutMode) {
            return mLowPingTimeoutMode.contains(account.getJid().asBareJid());
        }
    }

    public void startForcingForegroundNotification() {
        mForceForegroundService.set(true);
        toggleForegroundService();
    }

    public void stopForcingForegroundNotification() {
        mForceForegroundService.set(false);
        toggleForegroundService();
    }

    public boolean areMessagesInitialized() {
        return this.restoredFromDatabaseLatch.getCount() == 0;
    }

    public PgpEngine getPgpEngine() {
        if (!Config.supportOpenPgp()) {
            return null;
        } else if (pgpServiceConnection != null && pgpServiceConnection.isBound()) {
            if (this.mPgpEngine == null) {
                this.mPgpEngine = new PgpEngine(new OpenPgpApi(
                        getApplicationContext(),
                        pgpServiceConnection.getService()), this);
            }
            return mPgpEngine;
        } else {
            return null;
        }

    }

    public OpenPgpApi getOpenPgpApi() {
        if (!Config.supportOpenPgp()) {
            return null;
        } else if (pgpServiceConnection != null && pgpServiceConnection.isBound()) {
            return new OpenPgpApi(this, pgpServiceConnection.getService());
        } else {
            return null;
        }
    }

    public FileBackend getFileBackend() {
        return this.fileBackend;
    }

    public void blockMedia(Cid cid) {
        this.databaseBackend.blockMedia(cid);
    }

    public void clearBlockedMedia() {
        this.databaseBackend.clearBlockedMedia();
    }

    public Message getMessage(Conversation conversation, String uuid) {
        return this.databaseBackend.getMessage(conversation, uuid);
    }

    public Map<String, Message> getMessageFuzzyIds(Conversation conversation, Collection<String> ids) {
        return this.databaseBackend.getMessageFuzzyIds(conversation, ids);
    }

    public void insertWebxdcUpdate(final WebxdcUpdate update) {
        this.databaseBackend.insertWebxdcUpdate(update);
    }

    public WebxdcUpdate findLastWebxdcUpdate(Message message) {
        return this.databaseBackend.findLastWebxdcUpdate(message);
    }

    public List<WebxdcUpdate> findWebxdcUpdates(Message message, long serial) {
        return this.databaseBackend.findWebxdcUpdates(message, serial);
    }

    public AvatarService getAvatarService() {
        return this.mAvatarService;
    }

    public void attachLocationToConversation(final Conversation conversation, final Uri uri, final String subject, final UiCallback<Message> callback) {
        int encryption = conversation.getNextEncryption();
        if (encryption == Message.ENCRYPTION_PGP) {
            encryption = Message.ENCRYPTION_DECRYPTED;
        }
        Message message = new Message(conversation, uri.toString(), encryption);
        if (subject != null && !subject.isEmpty()) message.setSubject(subject);
        message.setThread(conversation.getThread());
        Message.configurePrivateMessage(message);
        if (encryption == Message.ENCRYPTION_DECRYPTED) {
            getPgpEngine().encrypt(message, callback);
        } else {
            sendMessage(message);
            callback.success(message);
        }
    }

    public void attachFileToConversation(final Conversation conversation, final Uri uri, final String type, final String subject, final UiCallback<Message> callback) {
        final Message message;
        if (conversation.getReplyTo() == null) {
            message = new Message(conversation, "", conversation.getNextEncryption());
        } else {
            message = conversation.getReplyTo().reply();
            message.setEncryption(conversation.getNextEncryption());
        }
        if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP) {
            message.setEncryption(Message.ENCRYPTION_DECRYPTED);
        }
        if (subject != null && !subject.isEmpty()) message.setSubject(subject);
        message.setThread(conversation.getThread());
        if (!Message.configurePrivateFileMessage(message)) {
            message.setCounterpart(conversation.getNextCounterpart());
            message.setType(Message.TYPE_FILE);
        }
        Log.d(Config.LOGTAG, "attachFile: type=" + message.getType());
        Log.d(Config.LOGTAG, "counterpart=" + message.getCounterpart());
        final AttachFileToConversationRunnable runnable = new AttachFileToConversationRunnable(this, uri, type, message, conversation, callback, getMaxHttpUploadSize(conversation));
        if (runnable.isVideoMessage()) {
            VIDEO_COMPRESSION_EXECUTOR.execute(runnable);
        } else {
            FILE_ATTACHMENT_EXECUTOR.execute(runnable);
        }
    }

    public long getMaxHttpUploadSize(Conversation conversation) {
        final XmppConnection connection = conversation.getAccount().getXmppConnection();
        return connection == null ? -1 : connection.getFeatures().getMaxHttpUploadSize();
    }

    public void attachImageToConversation(final Conversation conversation, final Uri uri, final String type, final String subject, final UiCallback<Message> callback) {
        final String mimeType = MimeUtils.guessMimeTypeFromUriAndMime(this, uri, type);
        final boolean compressPictures = getCompressImageResolutionPreference() != 0;
        if (!compressPictures
                || getFileBackend().useImageAsIs(uri)
                || (mimeType != null && mimeType.endsWith("/gif"))
                || getFileBackend().unusualBounds(uri) || "data".equals(uri.getScheme())) {
            Log.d(Config.LOGTAG, conversation.getAccount().getJid().asBareJid() + ": not compressing picture. sending as file");
            attachFileToConversation(conversation, uri, mimeType, subject, callback);
            return;
        } else {
            // there will be a delay so the caller can be informed to show an info to the user
            callback.showToast();
        }

        final Message message;

        if (conversation.getReplyTo() == null) {
            message = new Message(conversation, "", conversation.getNextEncryption());
        } else {
            message = conversation.getReplyTo().reply();
            message.setEncryption(conversation.getNextEncryption());
        }
        if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP) {
            message.setEncryption(Message.ENCRYPTION_DECRYPTED);
        }
        if (subject != null && !subject.isEmpty()) message.setSubject(subject);
        message.setThread(conversation.getThread());
        if (!Message.configurePrivateFileMessage(message)) {
            message.setCounterpart(conversation.getNextCounterpart());
            message.setType(Message.TYPE_IMAGE);
        }
        Log.d(Config.LOGTAG, "attachImage: type=" + message.getType());
        FILE_ATTACHMENT_EXECUTOR.execute(() -> {
            try {
                getFileBackend().copyImageToPrivateStorage(message, uri);
            } catch (FileBackend.ImageCompressionException e) {
                Log.d(Config.LOGTAG, "unable to compress image. fall back to file transfer", e);
                attachFileToConversation(conversation, uri, mimeType, subject, callback);
                return;
            } catch (final FileBackend.FileCopyException e) {
                callback.error(e.getResId(), message);
                return;
            }
            if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP) {
                final PgpEngine pgpEngine = getPgpEngine();
                if (pgpEngine != null) {
                    pgpEngine.encrypt(message, callback);
                } else if (callback != null) {
                    callback.error(R.string.unable_to_connect_to_keychain, null);
                }
            } else {
                sendMessage(message);
                callback.success(message);
            }
        });
    }

    public Conversation find(Bookmark bookmark) {
        return find(bookmark.getAccount(), bookmark.getJid());
    }

    public Conversation find(final Account account, final Jid jid) {
        return find(getConversations(), account, jid);
    }

    public boolean isMuc(final Account account, final Jid jid) {
        final Conversation c = find(account, jid);
        return c != null && c.getMode() == Conversational.MODE_MULTI;
    }

    public void search(final List<String> term, final String uuid, final OnSearchResultsAvailable onSearchResultsAvailable) {
        MessageSearchTask.search(this, term, uuid, onSearchResultsAvailable);
    }

    @SuppressLint("InvalidWakeLockTag")
    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        final String action = Strings.nullToEmpty(intent == null ? null : intent.getAction());
        final boolean needsForegroundService = intent != null && intent.getBooleanExtra(EventReceiver.EXTRA_NEEDS_FOREGROUND_SERVICE, false);
        if (needsForegroundService) {
            Log.d(Config.LOGTAG, "toggle forced foreground service after receiving event (action=" + action + ")");
            toggleForegroundService(true, action.equals(ACTION_STARTING_CALL));
        }
        final String uuid = intent == null ? null : intent.getStringExtra("uuid");
        switch (action) {
            case QuickConversationsService.SMS_RETRIEVED_ACTION:
                mQuickConversationsService.handleSmsReceived(intent);
                break;
            case ConnectivityManager.CONNECTIVITY_ACTION:
                if (hasInternetConnection()) {
                    if (Config.POST_CONNECTIVITY_CHANGE_PING_INTERVAL > 0) {
                        schedulePostConnectivityChange();
                    }
                    if (Config.RESET_ATTEMPT_COUNT_ON_NETWORK_CHANGE) {
                        resetAllAttemptCounts(true, false);
                    }
                    Resolver.clearCache();
                }
                break;
            case Intent.ACTION_SHUTDOWN:
                logoutAndSave(true);
                return START_NOT_STICKY;
            case ACTION_CLEAR_MESSAGE_NOTIFICATION:
                mNotificationExecutor.execute(() -> {
                    try {
                        final Conversation c = findConversationByUuid(uuid);
                        if (c != null) {
                            mNotificationService.clearMessages(c);
                        } else {
                            mNotificationService.clearMessages();
                        }
                        restoredFromDatabaseLatch.await();

                    } catch (InterruptedException e) {
                        Log.d(Config.LOGTAG, "unable to process clear message notification");
                    }
                });
                break;
            case ACTION_CLEAR_MISSED_CALL_NOTIFICATION:
                mNotificationExecutor.execute(() -> {
                    try {
                        final Conversation c = findConversationByUuid(uuid);
                        if (c != null) {
                            mNotificationService.clearMissedCalls(c);
                        } else {
                            mNotificationService.clearMissedCalls();
                        }
                        restoredFromDatabaseLatch.await();

                    } catch (InterruptedException e) {
                        Log.d(Config.LOGTAG, "unable to process clear missed call notification");
                    }
                });
                break;
            case ACTION_DISMISS_CALL: {
                if (intent == null) {
                    break;
                }
                final String sessionId = intent.getStringExtra(RtpSessionActivity.EXTRA_SESSION_ID);
                Log.d(Config.LOGTAG, "received intent to dismiss call with session id " + sessionId);
                mJingleConnectionManager.rejectRtpSession(sessionId);
                break;
            }
            case TorServiceUtils.ACTION_STATUS:
                final String status = intent == null ? null : intent.getStringExtra(TorServiceUtils.EXTRA_STATUS);
                //TODO port and host are in 'extras' - but this may not be a reliable source?
                if ("ON".equals(status)) {
                    handleOrbotStartedEvent();
                    return START_STICKY;
                }
                break;
            case ACTION_END_CALL: {
                if (intent == null) {
                    break;
                }
                final String sessionId = intent.getStringExtra(RtpSessionActivity.EXTRA_SESSION_ID);
                Log.d(Config.LOGTAG, "received intent to end call with session id " + sessionId);
                mJingleConnectionManager.endRtpSession(sessionId);
            }
            break;
            case ACTION_PROVISION_ACCOUNT: {
                if (intent == null) {
                    break;
                }
                final String address = intent.getStringExtra("address");
                final String password = intent.getStringExtra("password");
                if (QuickConversationsService.isQuicksy() || Strings.isNullOrEmpty(address) || Strings.isNullOrEmpty(password)) {
                    break;
                }
                provisionAccount(address, password);
                break;
            }
            case ACTION_DISMISS_ERROR_NOTIFICATIONS:
                dismissErrorNotifications();
                break;
            case ACTION_TRY_AGAIN:
                resetAllAttemptCounts(false, true);
                break;
            case ACTION_REPLY_TO_CONVERSATION:
                final Bundle remoteInput = intent == null ? null : RemoteInput.getResultsFromIntent(intent);
                if (remoteInput == null) {
                    break;
                }
                final CharSequence body = remoteInput.getCharSequence("text_reply");
                final boolean dismissNotification = intent.getBooleanExtra("dismiss_notification", false);
                final String lastMessageUuid = intent.getStringExtra("last_message_uuid");
                if (body == null || body.length() <= 0) {
                    break;
                }
                mNotificationExecutor.execute(() -> {
                    try {
                        restoredFromDatabaseLatch.await();
                        final Conversation c = findConversationByUuid(uuid);
                        if (c != null) {
                            directReply(c, body.toString(), lastMessageUuid, dismissNotification);
                        }
                    } catch (InterruptedException e) {
                        Log.d(Config.LOGTAG, "unable to process direct reply");
                    }
                });
                break;
            case ACTION_MARK_AS_READ:
                mNotificationExecutor.execute(() -> {
                    final Conversation c = findConversationByUuid(uuid);
                    if (c == null) {
                        Log.d(Config.LOGTAG, "received mark read intent for unknown conversation (" + uuid + ")");
                        return;
                    }
                    try {
                        restoredFromDatabaseLatch.await();
                        sendReadMarker(c, null);
                    } catch (InterruptedException e) {
                        Log.d(Config.LOGTAG, "unable to process notification read marker for conversation " + c.getName());
                    }

                });
                break;
            case ACTION_SNOOZE:
                mNotificationExecutor.execute(() -> {
                    final Conversation c = findConversationByUuid(uuid);
                    if (c == null) {
                        Log.d(Config.LOGTAG, "received snooze intent for unknown conversation (" + uuid + ")");
                        return;
                    }
                    c.setMutedTill(System.currentTimeMillis() + 30 * 60 * 1000);
                    mNotificationService.clearMessages(c);
                    updateConversation(c);
                });
            case AudioManager.RINGER_MODE_CHANGED_ACTION:
            case NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED:
                if (dndOnSilentMode()) {
                    refreshAllPresences();
                }
                break;
            case Intent.ACTION_SCREEN_ON:
                deactivateGracePeriod();
            case Intent.ACTION_USER_PRESENT:
            case Intent.ACTION_SCREEN_OFF:
                if (awayWhenScreenLocked()) {
                    refreshAllPresences();
                }
                break;
            case ACTION_FCM_TOKEN_REFRESH:
                refreshAllFcmTokens();
                break;
            case ACTION_RENEW_UNIFIED_PUSH_ENDPOINTS:
                if (intent == null) {
                    break;
                }
                final String instance = intent.getStringExtra("instance");
                final String application = intent.getStringExtra("application");
                final Messenger messenger = intent.getParcelableExtra("messenger");
                final UnifiedPushBroker.PushTargetMessenger pushTargetMessenger;
                if (messenger != null && application != null && instance != null) {
                    pushTargetMessenger = new UnifiedPushBroker.PushTargetMessenger(new UnifiedPushDatabase.PushTarget(application, instance),messenger);
                    Log.d(Config.LOGTAG,"found push target messenger");
                } else {
                    pushTargetMessenger = null;
                }
                final Optional<UnifiedPushBroker.Transport> transport = renewUnifiedPushEndpoints(pushTargetMessenger);
                if (instance != null && transport.isPresent()) {
                    unifiedPushBroker.rebroadcastEndpoint(messenger, instance, transport.get());
                }
                break;
            case ACTION_IDLE_PING:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    scheduleNextIdlePing();
                }
                break;
            case ACTION_FCM_MESSAGE_RECEIVED:
                Log.d(Config.LOGTAG, "push message arrived in service. account");
                break;
            case ACTION_QUICK_LOG:
                final String message = intent == null ? null : intent.getStringExtra("message");
                if (message != null && Config.QUICK_LOG) {
                    quickLog(message);
                }
                break;
            case Intent.ACTION_SEND:
                final Uri uri = intent == null ? null : intent.getData();
                if (uri != null) {
                    Log.d(Config.LOGTAG, "received uri permission for " + uri);
                }
                return START_STICKY;
            case ACTION_TEMPORARILY_DISABLE:
                toggleSoftDisabled(true);
                if (checkListeners()) {
                    stopSelf();
                }
                return START_NOT_STICKY;
        }
        sendScheduledMessages();
        final var extras =  intent == null ? null : intent.getExtras();
        try {
            internalPingExecutor.execute(() -> manageAccountConnectionStates(action, extras));
        } catch (final RejectedExecutionException e) {
            Log.e(Config.LOGTAG, "can not schedule connection states manager");
        }
        if (SystemClock.elapsedRealtime() - mLastExpiryRun.get() >= Config.EXPIRY_INTERVAL) {
            expireOldMessages();
            expireOldFiles();
            deleteWebpreviewCache();
        }
        // move files from /monocles chat/ --> /Android/data/ for Android >= 30
        if (Compatibility.runsThirty() && (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) && !Compatibility.runsThirtyThree()) {
            StorageHelper.migrateStorage(this);
        } else if (Compatibility.runsThirtyThree() && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED) {
            StorageHelper.migrateStorage(this);
        }
        return START_STICKY;
    }

    private void quickLog(final String message) {
        if (Strings.isNullOrEmpty(message)) {
            return;
        }
        final Account account = AccountUtils.getFirstEnabled(this);
        if (account == null) {
            return;
        }
        final Conversation conversation =
                findOrCreateConversation(account, Config.BUG_REPORTS, false, true);
        final Message report = new Message(conversation, message, Message.ENCRYPTION_NONE);
        report.setStatus(Message.STATUS_RECEIVED);
        conversation.add(report);
        databaseBackend.createMessage(report);
        updateConversationUi();
    }

    private void manageAccountConnectionStatesInternal() {
        manageAccountConnectionStates(ACTION_INTERNAL_PING, null);
    }

    private synchronized void manageAccountConnectionStates(
            final String action, final Bundle extras) {
        final String pushedAccountHash = extras == null ? null : extras.getString("account");
        final boolean interactive = java.util.Objects.equals(ACTION_TRY_AGAIN, action);
        WakeLockHelper.acquire(wakeLock);
        boolean pingNow =
                ConnectivityManager.CONNECTIVITY_ACTION.equals(action)
                        || (Config.POST_CONNECTIVITY_CHANGE_PING_INTERVAL > 0
                        && ACTION_POST_CONNECTIVITY_CHANGE.equals(action));
        final HashSet<Account> pingCandidates = new HashSet<>();
        final String androidId = pushedAccountHash == null ? null : PhoneHelper.getAndroidId(this);
        for (final Account account : accounts) {
            final boolean pushWasMeantForThisAccount =
                    androidId != null
                            && CryptoHelper.getAccountFingerprint(account, androidId)
                            .equals(pushedAccountHash);
            pingNow |=
                    processAccountState(
                            account,
                            interactive,
                            "ui".equals(action),
                            pushWasMeantForThisAccount,
                            pingCandidates);
        }
        if (pingNow) {
            for (final Account account : pingCandidates) {
                final boolean lowTimeout = isInLowPingTimeoutMode(account);
                account.getXmppConnection().sendPing();
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + " send ping (action="
                                + action
                                + ",lowTimeout="
                                + lowTimeout
                                + ")");
                scheduleWakeUpCall(
                        lowTimeout ? Config.LOW_PING_TIMEOUT : Config.PING_TIMEOUT,
                        account.getUuid().hashCode());
            }
            long msToMucPing = (mLastMucPing + (Config.PING_MAX_INTERVAL * 2000L)) - SystemClock.elapsedRealtime();
            if (msToMucPing <= 0) {
                Log.d(Config.LOGTAG, "ping MUCs");
                mLastMucPing = SystemClock.elapsedRealtime();
                for (Conversation c : getConversations()) {
                    if (c.getMode() == Conversation.MODE_MULTI && c.getMucOptions().online()) {
                        mucSelfPingAndRejoin(c);
                    }
                }
            }
        }
        WakeLockHelper.release(wakeLock);
    }

    private void sendScheduledMessages() {
        Log.d(Config.LOGTAG, "looking for and sending scheduled messages");

        for (final var message : new ArrayList<>(mScheduledMessages.values())) {
            if (message.getTimeSent() > System.currentTimeMillis()) continue;

            final var conversation = message.getConversation();
            final var account = conversation.getAccount();
            final boolean inProgressJoin;
            synchronized (account.inProgressConferenceJoins) {
                inProgressJoin = account.inProgressConferenceJoins.contains(conversation);
            }
            final boolean pendingJoin;
            synchronized (account.pendingConferenceJoins) {
                pendingJoin = account.pendingConferenceJoins.contains(conversation);
            }
            if (conversation.getAccount() == account
                    && !pendingJoin
                    && !inProgressJoin) {
                resendMessage(message, false);
            }
        }
    }

    private void handleOrbotStartedEvent() {
        for (final Account account : accounts) {
            if (account.getStatus() == Account.State.TOR_NOT_AVAILABLE || account.getStatus() == Account.State.I2P_NOT_AVAILABLE) {
                reconnectAccount(account, true, false);
            }
        }
    }
    public int maxResendTime(){
        return Integer.parseInt(getPreferences().getString(SettingsActivity.MAX_RESEND_TIME, getResources().getString(R.string.max_resend_time)));
    }
    private void deleteWebpreviewCache() {
        new Thread(() -> {
            try {
                long start = SystemClock.elapsedRealtime();
                final Calendar time = Calendar.getInstance();
                time.add(Calendar.DAY_OF_YEAR, -7);
                final File directory = new File(getCacheDir().getAbsolutePath(), File.separator + RICH_LINK_METADATA);
                if (!directory.exists()) {
                    return;
                }
                final File[] files = directory.listFiles();
                if (files != null) {
                    int count = 0;
                    for (File file : files) {
                        Date lastModified = new Date(file.lastModified());
                        if (lastModified.before(time.getTime())) {
                            file.delete();
                            count++;
                        }
                    }
                    Log.d(Config.LOGTAG, "Deleted " + count + " expired webpreview cache files in " + (SystemClock.elapsedRealtime() - start) + "ms");
                }
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "Deleted no expired webpreview cache files because of " + e);
            }
        }).start();
    }

    private boolean processAccountState(final Account account, final boolean interactive, final boolean isUiAction, final boolean isAccountPushed, final HashSet<Account> pingCandidates) {
        if (!account.getStatus().isAttemptReconnect()) {
            return false;
        }
        if (!hasInternetConnection()) {
            account.setStatus(Account.State.NO_INTERNET);
            statusListener.onStatusChanged(account);
        } else {
            if (account.getStatus() == Account.State.NO_INTERNET) {
                account.setStatus(Account.State.OFFLINE);
                statusListener.onStatusChanged(account);
            }
            if (account.getStatus() == Account.State.ONLINE) {
                synchronized (mLowPingTimeoutMode) {
                    long lastReceived = account.getXmppConnection().getLastPacketReceived();
                    long lastSent = account.getXmppConnection().getLastPingSent();
                    long pingInterval = isUiAction ? Config.PING_MIN_INTERVAL * 1000 : Config.PING_MAX_INTERVAL * 1000;
                    long msToNextPing = (Math.max(lastReceived, lastSent) + pingInterval) - SystemClock.elapsedRealtime();
                    int pingTimeout = mLowPingTimeoutMode.contains(account.getJid().asBareJid()) ? Config.LOW_PING_TIMEOUT * 1000 : Config.PING_TIMEOUT * 1000;
                    long pingTimeoutIn = (lastSent + pingTimeout) - SystemClock.elapsedRealtime();
                    if (lastSent > lastReceived) {
                        if (pingTimeoutIn < 0) {
                            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": ping timeout");
                            this.reconnectAccount(account, true, interactive);
                        } else {
                            int secs = (int) (pingTimeoutIn / 1000);
                            this.scheduleWakeUpCall(secs, account.getUuid().hashCode());
                        }
                    } else {
                        pingCandidates.add(account);
                        if (isAccountPushed) {
                            if (mLowPingTimeoutMode.add(account.getJid().asBareJid())) {
                                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": entering low ping timeout mode");
                            }
                            return true;
                        } else if (msToNextPing <= 0) {
                            return true;
                        } else {
                            this.scheduleWakeUpCall((int) (msToNextPing / 1000), account.getUuid().hashCode());
                            if (mLowPingTimeoutMode.remove(account.getJid().asBareJid())) {
                                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": leaving low ping timeout mode");
                            }
                        }
                    }
                }
            } else if (account.getStatus() == Account.State.OFFLINE) {
                reconnectAccount(account, true, interactive);
            } else if (account.getStatus() == Account.State.CONNECTING) {
                long secondsSinceLastConnect = (SystemClock.elapsedRealtime() - account.getXmppConnection().getLastConnect()) / 1000;
                long secondsSinceLastDisco = (SystemClock.elapsedRealtime() - account.getXmppConnection().getLastDiscoStarted()) / 1000;
                long discoTimeout = Config.CONNECT_DISCO_TIMEOUT - secondsSinceLastDisco;
                long timeout = Config.CONNECT_TIMEOUT - secondsSinceLastConnect;
                if (!areMessagesInitialized()) return false; // No point in thrashing a reconnect while still loading
                if (timeout < 0) {
                    Log.d(Config.LOGTAG, account.getJid() + ": time out during connect reconnecting (secondsSinceLast=" + secondsSinceLastConnect + ")");
                    account.getXmppConnection().resetAttemptCount(false);
                    reconnectAccount(account, true, interactive);
                } else if (discoTimeout < 0) {
                    account.getXmppConnection().sendDiscoTimeout();
                    scheduleWakeUpCall((int) Math.min(timeout, discoTimeout), account.getUuid().hashCode());
                } else {
                    scheduleWakeUpCall((int) Math.min(timeout, discoTimeout), account.getUuid().hashCode());
                }
            } else {
                final boolean aggressive = account.getStatus() == Account.State.SEE_OTHER_HOST || hasJingleRtpConnection(account);
                if (account.getXmppConnection().getTimeToNextAttempt(aggressive) <= 0) {
                    reconnectAccount(account, true, interactive);
                }
            }
        }
        return false;
    }

    private void toggleSoftDisabled(final boolean softDisabled) {
        for(final Account account : this.accounts) {
            if (account.isEnabled()) {
                if (account.setOption(Account.OPTION_SOFT_DISABLED, softDisabled)) {
                    updateAccount(account);
                }
            }
        }
    }

    private void storeNumberOfAccounts(int accounts) {
        final SharedPreferences.Editor editor = getPreferences().edit();
        Log.d(Config.LOGTAG, "Number of accounts is " + accounts);
        editor.putInt(SettingsActivity.NUMBER_OF_ACCOUNTS, accounts);
        editor.apply();
        if (accounts > 1 && !multipleAccounts()) {
            editor.putBoolean(ENABLE_MULTI_ACCOUNTS, true);
        }
    }
    public boolean processUnifiedPushMessage(final Account account, final Jid transport, final Element push) {
        return unifiedPushBroker.processPushMessage(account, transport, push);
    }

    public void reinitializeMuclumbusService() {
        mChannelDiscoveryService.initializeMuclumbusService();
    }

    public void discoverChannels(String query, ChannelDiscoveryService.Method method, Map<Jid, Account> mucServices, ChannelDiscoveryService.OnChannelSearchResultsFound onChannelSearchResultsFound) {
        mChannelDiscoveryService.discover(Strings.nullToEmpty(query).trim(), method, mucServices, onChannelSearchResultsFound);
    }

    public boolean isDataSaverDisabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return true;
        }
        final ConnectivityManager connectivityManager = getSystemService(ConnectivityManager.class);
        return !Compatibility.isActiveNetworkMetered(connectivityManager)
                || Compatibility.getRestrictBackgroundStatus(connectivityManager)
                == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED;
    }

    private void directReply(final Conversation conversation, final String body, final String lastMessageUuid, final boolean dismissAfterReply) {
        final Message inReplyTo = lastMessageUuid == null ? null : conversation.findMessageWithUuid(lastMessageUuid);
        Message message = new Message(conversation, body, conversation.getNextEncryption());
        if (inReplyTo != null) {
            if (Emoticons.isEmoji(body)) {
                message = inReplyTo.react(body);
            } else {
                message = inReplyTo.reply();
            }
            message.clearFallbacks("urn:xmpp:reply:0");
            message.setBody(body);
            message.setEncryption(conversation.getNextEncryption());
        }
        if (inReplyTo != null && inReplyTo.isPrivateMessage()) {
            Message.configurePrivateMessage(message, inReplyTo.getCounterpart());
        }
        message.markUnread();
        if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            getPgpEngine().encrypt(message, new UiCallback<Message>() {
                @Override
                public void success(Message message) {
                    if (dismissAfterReply) {
                        markRead((Conversation) message.getConversation(), true);
                    } else {
                        mNotificationService.pushFromDirectReply(message);
                    }
                }

                @Override
                public void error(int errorCode, Message object) {

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
        } else {
            sendMessage(message);
            if (dismissAfterReply) {
                markRead(conversation, true);
            } else {
                mNotificationService.pushFromDirectReply(message);
            }
        }
    }

    public boolean pauseVoiceOnMoveFromEar() {
        return getBooleanPreference(SettingsActivity.PAUSE_VOICE, R.bool.pause_voice);
    }

    public boolean easyDownloader() {
        return getBooleanPreference(SettingsActivity.EASY_DOWNLOADER, R.bool.easy_downloader);
    }

    private boolean dndOnSilentMode() {
        return getBooleanPreference(SettingsActivity.DND_ON_SILENT_MODE, R.bool.dnd_on_silent_mode);
    }

    private boolean manuallyChangePresence() {
        return getBooleanPreference(SettingsActivity.MANUALLY_CHANGE_PRESENCE, R.bool.manually_change_presence);
    }

    private boolean treatVibrateAsSilent() {
        return getBooleanPreference(SettingsActivity.TREAT_VIBRATE_AS_SILENT, R.bool.treat_vibrate_as_silent);
    }

    private boolean awayWhenScreenLocked() {
        return getBooleanPreference(SettingsActivity.AWAY_WHEN_SCREEN_IS_OFF, R.bool.away_when_screen_off);
    }

    public boolean alternativeVoiceSettings() {
        return getBooleanPreference("alternative_voice_settings", R.bool.alternative_voice_settings);
    }

    public boolean colored_muc_names() {
        return getBooleanPreference("colored_muc_names", R.bool.use_colored_muc_names);
    }

    public int getCompressImageResolutionPreference() {
        switch (getPreferences().getString("image_compression", getResources().getString(R.string.image_compression))) {
            case "low":
                return 720;
            case "mid":
                return 1920;
            case "high":
                return 3840;
            case "uncompressed":
                return 0;
            default:
                return 1920;
        }
    }

    public int getCompressImageSizePreference() {
        switch (getPreferences().getString("image_compression", getResources().getString(R.string.image_compression))) {
            case "low":
                return 209715; // 0.2 * 1024 * 1024 = 209715 (0.2 MiB)
            case "mid":
                return 524288; // 0.5 * 1024 * 1024 = 524288 (0.5 MiB)
            case "high":
                return 1048576; // 1 * 1024 * 1024 = 1048576 (1 MiB)
            case "uncompressed":
                return 0;
            default:
                return 524288;
        }
    }

    public int getCompressVideoResolutionPreference() {
        switch (getPreferences().getString("video_compression", getResources().getString(R.string.video_compression))) {
            case "verylow":
                return getResources().getInteger(R.integer.verylow_video_res);
            case "low":
                return getResources().getInteger(R.integer.low_video_res);
            case "mid":
                return getResources().getInteger(R.integer.mid_video_res);
            case "high":
                return getResources().getInteger(R.integer.high_video_res);
            case "uncompressed":
                return 0;
            default:
                return getResources().getInteger(R.integer.mid_video_res);
        }
    }

    public int getCompressVideoBitratePreference() {
        switch (getPreferences().getString("video_compression", getResources().getString(R.string.video_compression))) {
            case "verylow":
                return getResources().getInteger(R.integer.verylow_video_bitrate);
            case "low":
                return getResources().getInteger(R.integer.low_video_bitrate);
            case "mid":
                return getResources().getInteger(R.integer.mid_video_bitrate);
            case "high":
                return getResources().getInteger(R.integer.high_video_bitrate);
            case "uncompressed":
                return 0;
            default:
                return getResources().getInteger(R.integer.mid_video_bitrate);
        }
    }

    public DefaultAudioStrategy getCompressAudioPreference() {
        switch (getPreferences().getString("video_compression", getResources().getString(R.string.video_compression))) {
            case "verylow":
                return TranscoderStrategies.AUDIO_LQ;
            case "low":
                return TranscoderStrategies.AUDIO_LQ;
            case "mid":
                return TranscoderStrategies.AUDIO_MQ;
            case "high":
                return TranscoderStrategies.AUDIO_HQ;
            case "uncompressed":
                return TranscoderStrategies.AUDIO_HQ;
            default:
                return TranscoderStrategies.AUDIO_MQ;
        }
    }

    public boolean getAttachmentChoicePreference() {
        return getBooleanPreference(SettingsActivity.QUICK_SHARE_ATTACHMENT_CHOICE, R.bool.quick_share_attachment_choice);
    }

    public long getIndividualNotificationPreference(final Conversation conversation) {
        final String uuid = conversation.getUuid();
        return getPreferences().getLong(SettingsActivity.INDIVIDUAL_NOTIFICATION_PREFIX + uuid, 0L);
    }

    public void setIndividualNotificationPreference(final Conversation conversation, final boolean reset) {
        final String uuid = conversation.getUuid();
        long value = System.currentTimeMillis();
        if (reset) {
            value = 0;
        }
        getPreferences().edit().putLong(SettingsActivity.INDIVIDUAL_NOTIFICATION_PREFIX + uuid, value).apply();
    }

    public boolean hasIndividualNotification(Conversation conversation) {
        if (conversation == null) {
            return false;
        }
        return getIndividualNotificationPreference(conversation) > 0L;
    }

    private Presence.Status getTargetPresence() {
        if (dndOnSilentMode() && isPhoneSilenced()) {
            return Presence.Status.DND;
        } else if (awayWhenScreenLocked() && isScreenLocked()) {
            return Presence.Status.AWAY;
        } else {
            return Presence.Status.ONLINE;
        }
    }

    public boolean isScreenLocked() {
        final KeyguardManager keyguardManager = getSystemService(KeyguardManager.class);
        final PowerManager powerManager = getSystemService(PowerManager.class);
        final boolean locked = keyguardManager != null && keyguardManager.isKeyguardLocked();
        final boolean interactive;
        try {
            interactive = powerManager != null && powerManager.isInteractive();
        } catch (final Exception e) {
            return false;
        }
        return locked || !interactive;
    }

    private boolean isPhoneSilenced() {
        final NotificationManager notificationManager = getSystemService(NotificationManager.class);
        final int filter = notificationManager == null ? NotificationManager.INTERRUPTION_FILTER_UNKNOWN : notificationManager.getCurrentInterruptionFilter();
        final boolean notificationDnd = filter >= NotificationManager.INTERRUPTION_FILTER_PRIORITY;
        final AudioManager audioManager = getSystemService(AudioManager.class);
        final int ringerMode = audioManager == null ? AudioManager.RINGER_MODE_NORMAL : audioManager.getRingerMode();
        try {
            if (treatVibrateAsSilent()) {
                return notificationDnd || ringerMode != AudioManager.RINGER_MODE_NORMAL;
            } else {
                return notificationDnd || ringerMode == AudioManager.RINGER_MODE_SILENT;
            }
        } catch (final Throwable throwable) {
            Log.d(Config.LOGTAG, "platform bug in isPhoneSilenced (" + throwable.getMessage() + ")");
            return notificationDnd;
        }
    }

    private void resetAllAttemptCounts(boolean reallyAll, boolean retryImmediately) {
        Log.d(Config.LOGTAG, "resetting all attempt counts");
        for (Account account : accounts) {
            if (account.hasErrorStatus() || reallyAll) {
                final XmppConnection connection = account.getXmppConnection();
                if (connection != null) {
                    connection.resetAttemptCount(retryImmediately);
                }
            }
            if (account.setShowErrorNotification(true)) {
                mDatabaseWriterExecutor.execute(() -> databaseBackend.updateAccount(account));
            }
        }
        mNotificationService.updateErrorNotification();
    }

    private void dismissErrorNotifications() {
        for (final Account account : this.accounts) {
            if (account.hasErrorStatus()) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": dismissing error notification");
                if (account.setShowErrorNotification(false)) {
                    mDatabaseWriterExecutor.execute(() -> databaseBackend.updateAccount(account));
                }
            }
        }
    }

    private void expireOldMessages() {
        expireOldMessages(false);
    }

    public void expireOldMessages(final boolean resetHasMessagesLeftOnServer) {
        mLastExpiryRun.set(SystemClock.elapsedRealtime());
        final Runnable runnable = () -> {
            long timestamp = getAutomaticMessageDeletionDate();
            if (timestamp > 0) {
                expireOldMessages(timestamp, true);
                synchronized (XmppConnectionService.this.conversations) {
                    for (Conversation conversation : XmppConnectionService.this.conversations) {
                        conversation.expireOldMessages(timestamp);
                        if (resetHasMessagesLeftOnServer) {
                            conversation.messagesLoaded.set(true);
                            conversation.setHasMessagesLeftOnServer(true);
                        }
                    }
                }
                updateConversationUi();
            }
        };
        mDatabaseWriterExecutor.execute((runnable));
    }

    public boolean hasInternetConnection() {
        final ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return true; //if internet connection can not be checked it is probably best to just try
        }
        try {
            if (Compatibility.runsTwentyNine()) {
                final Network activeNetwork = cm.getActiveNetwork();
                final NetworkCapabilities capabilities = activeNetwork == null ? null : cm.getNetworkCapabilities(activeNetwork);
                return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            } else {
                final NetworkInfo networkInfo = cm.getActiveNetworkInfo();
                return networkInfo != null && (networkInfo.isConnected() || networkInfo.getType() == ConnectivityManager.TYPE_ETHERNET);
            }
        } catch (final RuntimeException e) {
            Log.d(Config.LOGTAG, "unable to check for internet connection", e);
            return true; //if internet connection can not be checked it is probably best to just try
        }
    }

    public boolean isWIFI() {
        final ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            final NetworkInfo activeNetwork = cm == null ? null : cm.getActiveNetworkInfo();
            if (activeNetwork != null) { // connected to the internet
                if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                    return true;
                }
            }
        } catch (RuntimeException e) {
            Log.d(Config.LOGTAG, "unable to check for WIFI connection", e);
            return false;
        }
        return false;
    }

    public boolean isMobile() {
        final ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            final NetworkInfo activeNetwork = cm == null ? null : cm.getActiveNetworkInfo();
            if (activeNetwork != null) { // connected to the internet
                if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                    if (!activeNetwork.isRoaming()) {
                        return true;
                    }
                }
            }
        } catch (RuntimeException e) {
            Log.d(Config.LOGTAG, "unable to check for mobile connection", e);
            return false;
        }
        return false;
    }

    public boolean isMobileRoaming() {
        final ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            final NetworkInfo activeNetwork = cm == null ? null : cm.getActiveNetworkInfo();
            if (activeNetwork != null) { // connected to the internet
                if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                    if (activeNetwork.isRoaming()) {
                        return true;
                    }
                }
            }
        } catch (RuntimeException e) {
            Log.d(Config.LOGTAG, "unable to check for roaming connection", e);
            return false;
        }
        return false;
    }

    @SuppressLint("TrulyRandom")
    @Override
    public void onCreate() {
        org.jxmpp.stringprep.libidn.LibIdnXmppStringprep.setup();
        emojiSearch = new EmojiSearch(this);
        updateNotificationChannels();
        setTheme(ThemeHelper.find(this));
        ThemeHelper.applyCustomColors(this);
        if (Compatibility.runsTwentySix()) {
            cleanOldNotificationChannels();
        }
        mChannelDiscoveryService.initializeMuclumbusService();
        mForceDuringOnCreate.set(Compatibility.runsAndTargetsTwentySix(this));
        toggleForegroundService();
        this.destroyed = false;
        OmemoSetting.load(this);
        ExceptionHelper.init(getApplicationContext());
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1);
        } catch (Throwable throwable) {
            Log.e(Config.LOGTAG, "unable to initialize security provider", throwable);
        }
        Resolver.init(this);
        this.mRandom = new SecureRandom();
        updateMemorizingTrustmanager();
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 15;
        ActivityManager manager = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
        this.mDrawableCache = new LruCache<String, Drawable>(cacheSize) {
            @Override
            protected int sizeOf(final String key, final Drawable drawable) {
                if (drawable instanceof BitmapDrawable) {
                    Bitmap bitmap =  ((BitmapDrawable) drawable).getBitmap();
                    if (bitmap == null) return 1024;

                    return bitmap.getByteCount() / 1024;
                } else {
                    return drawable.getIntrinsicWidth() * drawable.getIntrinsicHeight() * 40 / 1024;
                }
            }
        };
        if (mLastActivity == 0) {
            mLastActivity = getPreferences().getLong(SETTING_LAST_ACTIVITY_TS, System.currentTimeMillis());
        }

        Log.d(Config.LOGTAG, "initializing database...");
        this.databaseBackend = DatabaseBackend.getInstance(getApplicationContext());
        Log.d(Config.LOGTAG, "restoring accounts...");
        this.accounts = databaseBackend.getAccounts();
        for (Account account : this.accounts) {
            final int color = getPreferences().getInt("account_color:" + account.getUuid(), 0);
            if (color != 0) account.setColor(color);
        }
        final SharedPreferences.Editor editor = getPreferences().edit();
        if (this.accounts.size() == 0 && Arrays.asList("Sony", "Sony Ericsson").contains(Build.MANUFACTURER)) {
            editor.putBoolean(SettingsActivity.SHOW_FOREGROUND_SERVICE, true);
            Log.d(Config.LOGTAG, Build.MANUFACTURER + " is on blacklist. enabling foreground service");
        }
        final boolean hasEnabledAccounts = hasEnabledAccounts();
        editor.putBoolean(EventReceiver.SETTING_ENABLED_ACCOUNTS, hasEnabledAccounts).apply();
        editor.apply();
        toggleSetProfilePictureActivity(hasEnabledAccounts);
        reconfigurePushDistributor();

        if (CallIntegration.hasSystemFeature(this)) {
            CallIntegrationConnectionService.togglePhoneAccountsAsync(this, this.accounts);
        }

        restoreFromDatabase();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            startContactObserver();
        }
        FileBackend.switchStorage(usingInnerStorage());
        FILE_OBSERVER_EXECUTOR.execute(fileBackend::deleteHistoricAvatarPath);
        if (Compatibility.hasStoragePermission(this)) {
            Log.d(Config.LOGTAG, "starting file observer");
            FILE_OBSERVER_EXECUTOR.execute(this.fileObserver::startWatching);
            FILE_OBSERVER_EXECUTOR.execute(this::checkForDeletedFiles);
        }
        if (Config.supportOpenPgp()) {
            this.pgpServiceConnection = new OpenPgpServiceConnection(this, "org.sufficientlysecure.keychain", new OpenPgpServiceConnection.OnBound() {
                @Override
                public void onBound(final IOpenPgpService2 service) {
                    for (Account account : accounts) {
                        final PgpDecryptionService pgp = account.getPgpDecryptionService();
                        if (pgp != null) {
                            pgp.continueDecryption(true);
                        }
                    }
                }

                @Override
                public void onError(final Exception exception) {
                    Log.e(Config.LOGTAG,"could not bind to OpenKeyChain", exception);
                }
            });
            this.pgpServiceConnection.bindToService();
        }

        final PowerManager pm = ContextCompat.getSystemService(this, PowerManager.class);
        this.wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Config.LOGTAG + ":Service");
        toggleForegroundService();
        updateUnreadCountBadge();
        toggleScreenEventReceiver();
        final IntentFilter systemBroadcastFilter = new IntentFilter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            scheduleNextIdlePing();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                systemBroadcastFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            }
            systemBroadcastFilter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
        }
        ContextCompat.registerReceiver(
                this,
                this.mInternalEventReceiver,
                systemBroadcastFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        final IntentFilter exportedBroadcastFilter = new IntentFilter();
        exportedBroadcastFilter.addAction(TorServiceUtils.ACTION_STATUS);
        ContextCompat.registerReceiver(
                this,
                this.mInternalRestrictedEventReceiver,
                exportedBroadcastFilter,
                ContextCompat.RECEIVER_EXPORTED);
        mForceDuringOnCreate.set(false);
        toggleForegroundService();
        rescanStickers();
        internalPingExecutor.scheduleAtFixedRate(this::manageAccountConnectionStatesInternal,120,120,TimeUnit.SECONDS);
        //start export log service every day at given time
        ScheduleAutomaticExport();
        // cancel scheduled exporter
        CancelAutomaticExport(false);
    }

    public void updateNotificationChannels() {
        if (Compatibility.runsTwentySix()) {
            new Thread(mNotificationService::updateChannels).start();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void cleanOldNotificationChannels() {
        new Thread(() -> {
            try {
                mNotificationService.cleanAllOldNotificationChannels(this);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private void checkForDeletedFiles() {
        if (destroyed) {
            Log.d(Config.LOGTAG, "Do not check for deleted files because service has been destroyed");
            return;
        }
        final long start = SystemClock.elapsedRealtime();
        final List<DatabaseBackend.FilePathInfo> relativeFilePaths = databaseBackend.getFilePathInfo();
        final List<DatabaseBackend.FilePathInfo> changed = new ArrayList<>();
        for (final DatabaseBackend.FilePathInfo filePath : relativeFilePaths) {
            if (destroyed) {
                Log.d(Config.LOGTAG, "Stop checking for deleted files because service has been destroyed");
                return;
            }
            final File file = fileBackend.getFileForPath(filePath.path);
            if (file != null && filePath.setFileDeleted(!file.exists())) {
                changed.add(filePath);
            }
        }
        final long duration = SystemClock.elapsedRealtime() - start;
        Log.d(Config.LOGTAG, "found " + changed.size() + " changed files on start up. total=" + relativeFilePaths.size() + ". (" + duration + "ms)");
        if (changed.size() > 0) {
            databaseBackend.markFilesAsChanged(changed);
            markChangedFiles(changed);
        }
    }

    public void startContactObserver() {
        getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                if (restoredFromDatabaseLatch.getCount() == 0) {
                    loadPhoneContacts();
                }
            }
        });
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_COMPLETE) {
            Log.d(Config.LOGTAG, "clear cache due to low memory");
            getDrawableCache().evictAll();
        }
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(this.mInternalEventReceiver);
            unregisterReceiver(this.mInternalRestrictedEventReceiver);
            unregisterReceiver(this.mInternalScreenEventReceiver);
        } catch (final IllegalArgumentException e) {
            //ignored
        }
        destroyed = false;
        fileObserver.stopWatching();
        super.onDestroy();
        internalPingExecutor.shutdown();
        // cancel scheduled exporter
        CancelAutomaticExport(true);
    }

    public void restartFileObserver() {
        Log.d(Config.LOGTAG, "restarting file observer");
        FILE_OBSERVER_EXECUTOR.execute(this.fileObserver::restartWatching);
        FILE_OBSERVER_EXECUTOR.execute(this::checkForDeletedFiles);
    }

    public void toggleScreenEventReceiver() {
        if (awayWhenScreenLocked() && !manuallyChangePresence()) {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            registerReceiver(this.mInternalScreenEventReceiver, filter);
        } else {
            try {
                unregisterReceiver(this.mInternalScreenEventReceiver);
            } catch (IllegalArgumentException e) {
                //ignored
            }
        }
    }

    public void toggleForegroundService() {
        toggleForegroundService(false, false);
    }

    public void setOngoingCall(AbstractJingleConnection.Id id, Set<Media> media, final boolean reconnecting) {
        ongoingCall.set(new OngoingCall(id, media, reconnecting));
        toggleForegroundService(false, true);
    }

    public void removeOngoingCall() {
        ongoingCall.set(null);
        toggleForegroundService(false, false);
    }

    private void toggleForegroundService(boolean force, boolean needMic) {
        final boolean status;
        final OngoingCall ongoing = ongoingCall.get();
        final boolean showOngoing = ongoing != null && !diallerIntegrationActive.get();
        if (force || mForceDuringOnCreate.get() || mForceForegroundService.get() || showOngoing || (Compatibility.keepForegroundService(this) && hasEnabledAccounts())) {
            final Notification notification;
            final int id;
            if (showOngoing) {
                notification = this.mNotificationService.getOngoingCallNotification(ongoing);
                id = NotificationService.ONGOING_CALL_NOTIFICATION_ID;
                startForegroundOrCatch(id, notification, true);
                mNotificationService.cancel(NotificationService.FOREGROUND_NOTIFICATION_ID);
            } else {
                notification = this.mNotificationService.createForegroundNotification();
                id = NotificationService.FOREGROUND_NOTIFICATION_ID;
                startForegroundOrCatch(id, notification, needMic || ongoing != null || diallerIntegrationActive.get());
            }

            if (!mForceForegroundService.get()) {
                mNotificationService.notify(id, notification);
            }
            status = true;
        } else {
            stopForeground(true);
            status = false;
        }
        if (!mForceForegroundService.get()) {
            mNotificationService.cancel(NotificationService.FOREGROUND_NOTIFICATION_ID);
        }
        if (!showOngoing) {
            mNotificationService.cancel(NotificationService.ONGOING_CALL_NOTIFICATION_ID);
        }
        Log.d(Config.LOGTAG, "ForegroundService: " + (status ? "on" : "off"));
    }

    private void startForegroundOrCatch(final int id, final Notification notification, boolean needMic) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                int foregroundServiceType;
                if (getSystemService(PowerManager.class)
                        .isIgnoringBatteryOptimizations(getPackageName())) {
                    foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED;
                } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
                    foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) {
                    foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
                } else {
                    foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
                    Log.w(Config.LOGTAG,"falling back to special use foreground service type");
                }

                if (needMic && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
                    foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                 }

                startForeground(id, notification, foregroundServiceType);
            } else {
                startForeground(id, notification);
            }
        } catch (final IllegalStateException | SecurityException e) {
            Log.e(Config.LOGTAG, "Could not start foreground service", e);
        }
    }

    public boolean foregroundNotificationNeedsUpdatingWhenErrorStateChanges() {
        return !mForceForegroundService.get() && ongoingCall.get() == null && Compatibility.keepForegroundService(this) && hasEnabledAccounts();
    }

    @Override
    public void onTaskRemoved(final Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if ((Compatibility.keepForegroundService(this) && hasEnabledAccounts()) || mForceForegroundService.get() || ongoingCall.get() != null) {
            Log.d(Config.LOGTAG, "ignoring onTaskRemoved because foreground service is activated");
        } else {
            this.logoutAndSave(false);
        }
    }

    private void logoutAndSave(boolean stop) {
        int activeAccounts = 0;
        if (accounts != null) {
            for (final Account account : accounts) {
                if (account.isConnectionEnabled()) {
                    databaseBackend.writeRoster(account.getRoster());
                    activeAccounts++;
                }
                if (account.getXmppConnection() != null) {
                    new Thread(() -> disconnect(account, false)).start();
                }
            }
        }
        if (stop || activeAccounts == 0) {
            Log.d(Config.LOGTAG, "good bye");
            stopSelf();
        }
    }

    private void schedulePostConnectivityChange() {
        final AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        final long triggerAtMillis = SystemClock.elapsedRealtime() + (Config.POST_CONNECTIVITY_CHANGE_PING_INTERVAL * 1000);
        final Intent intent = new Intent(this, EventReceiver.class);
        intent.setAction(ACTION_POST_CONNECTIVITY_CHANGE);
        try {
            final PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 1, intent, s()
                    ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                    : PendingIntent.FLAG_UPDATE_CURRENT);
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent);
        } catch (RuntimeException e) {
            Log.e(Config.LOGTAG, "unable to schedule alarm for post connectivity change", e);
        }
    }

    public void scheduleWakeUpCall(int seconds, int requestCode) {
        final long timeToWake = SystemClock.elapsedRealtime() + (seconds < 0 ? 1 : seconds + 1) * 1000L;
        final AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        final Intent intent = new Intent(this, EventReceiver.class);
        intent.setAction("ping");
        try {
            final PendingIntent pendingIntent;
            pendingIntent =
                    PendingIntent.getBroadcast(
                            this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE);
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, timeToWake, pendingIntent);
        } catch (RuntimeException e) {
            Log.e(Config.LOGTAG, "unable to schedule alarm for ping", e);
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "unable to schedule alarm for ping", e);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void scheduleNextIdlePing() {
        long timeUntilWake = Config.IDLE_PING_INTERVAL * 1000;
        final var now = System.currentTimeMillis();
        for (final var message : mScheduledMessages.values()) {
            if (message.getTimeSent() <= now) continue; // Just in case
            if (message.getTimeSent() - now < timeUntilWake) timeUntilWake = message.getTimeSent() - now;
        }
        final var timeToWake = SystemClock.elapsedRealtime() + timeUntilWake;
        final AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.d(Config.LOGTAG, "no alarm manager?");
            return;
        }
        final Intent intent = new Intent(this, EventReceiver.class);
        intent.setAction(ACTION_IDLE_PING);
        try {
            final PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, s()
                    ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                    : PendingIntent.FLAG_UPDATE_CURRENT);
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, timeToWake, pendingIntent);
        } catch (RuntimeException e) {
            Log.d(Config.LOGTAG, "unable to schedule alarm for idle ping", e);
        }
    }

    public XmppConnection createConnection(final Account account) {
        final XmppConnection connection = new XmppConnection(account, this);
        connection.setOnMessagePacketReceivedListener(this.mMessageParser);
        connection.setOnStatusChangedListener(this.statusListener);
        connection.setOnPresencePacketReceivedListener(this.mPresenceParser);
        connection.setOnUnregisteredIqPacketReceivedListener(this.mIqParser);
        connection.setOnJinglePacketReceivedListener((mJingleConnectionManager::deliverPacket));
        connection.setOnBindListener(this.mOnBindListener);
        connection.setOnMessageAcknowledgeListener(this.mOnMessageAcknowledgedListener);
        connection.addOnAdvancedStreamFeaturesAvailableListener(this.mMessageArchiveService);
        connection.addOnAdvancedStreamFeaturesAvailableListener(this.mAvatarService);
        AxolotlService axolotlService = account.getAxolotlService();
        if (axolotlService != null) {
            connection.addOnAdvancedStreamFeaturesAvailableListener(axolotlService);
        }
        return connection;
    }

    public void sendChatState(Conversation conversation) {
        if (sendChatStates()) {
            MessagePacket packet = mMessageGenerator.generateChatState(conversation);
            sendMessagePacket(conversation.getAccount(), packet);
        }
    }

    private void sendFileMessage(final Message message, final boolean delay) {
        Log.d(Config.LOGTAG, "send file message");
        final Account account = message.getConversation().getAccount();
        if (account.httpUploadAvailable(fileBackend.getFile(message, false).getSize())
                || message.getConversation().getMode() == Conversation.MODE_MULTI) {
            mHttpConnectionManager.createNewUploadConnection(message, delay);
        } else {
            mJingleConnectionManager.startJingleFileTransfer(message);
        }
    }

    public void sendMessage(final Message message) {
        sendMessage(message, false, false, false);
    }

    private void sendMessage(final Message message, final boolean resend, final boolean previewedLinks, final boolean delay) {
        if (resend) {
            message.setTime(System.currentTimeMillis());
        }
        final Account account = message.getConversation().getAccount();
        if (account.setShowErrorNotification(true)) {
            databaseBackend.updateAccount(account);
            mNotificationService.updateErrorNotification();
        }
        final Conversation conversation = (Conversation) message.getConversation();
        account.deactivateGracePeriod();
        if (QuickConversationsService.isQuicksy() && conversation.getMode() == Conversation.MODE_SINGLE) {
            final Contact contact = conversation.getContact();
            if (!contact.showInRoster() && contact.getOption(Contact.Options.SYNCED_VIA_OTHER)) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": adding " + contact.getJid() + " on sending message");
                createContact(contact, true);
            }
        }
        MessagePacket packet = null;
        final boolean addToConversation = !message.edited() && message.getRawBody() != null;
        boolean saveInDb = addToConversation;
        message.setStatus(Message.STATUS_WAITING);

        if (message.getEncryption() != Message.ENCRYPTION_NONE && conversation.getMode() == Conversation.MODE_MULTI && conversation.isPrivateAndNonAnonymous()) {
            if (conversation.setAttribute(Conversation.ATTRIBUTE_FORMERLY_PRIVATE_NON_ANONYMOUS, true)) {
                databaseBackend.updateConversation(conversation);
            }
        }
        if (!resend && message.getEncryption() != Message.ENCRYPTION_OTR) {
            conversation.endOtrIfNeeded();
            conversation.findUnsentMessagesWithEncryption(Message.ENCRYPTION_OTR,
                    message1 -> markMessage(message1, Message.STATUS_SEND_FAILED));
        }

        final boolean inProgressJoin = isJoinInProgress(conversation);

        if (message.getCounterpart() == null && !message.isPrivateMessage()) {
            message.setCounterpart(message.getConversation().getJid().asBareJid());
        }

        boolean waitForPreview = false;
        if (getPreferences().getBoolean("send_link_previews", true) && !previewedLinks && !message.needsUploading()) {
            final List<URI> links = message.getLinks();
            if (!links.isEmpty()) {
                waitForPreview = true;
                if (account.isOnlineAndConnected()) {
                    FILE_ATTACHMENT_EXECUTOR.execute(() -> {
                        for (URI link : links) {
                            if ("https".equals(link.getScheme())) {
                                try {
                                    HttpUrl url = HttpUrl.parse(link.toString());
                                    OkHttpClient http = getHttpConnectionManager().buildHttpClient(url, account, false);
                                    okhttp3.Response response = http.newCall(new okhttp3.Request.Builder().url(url).head().build()).execute();
                                    final String mimeType = response.header("Content-Type") == null ? "" : response.header("Content-Type");
                                    final boolean image = mimeType.startsWith("image/");
                                    final boolean audio = mimeType.startsWith("audio/");
                                    final boolean video = mimeType.startsWith("video/");
                                    final boolean pdf = mimeType.equals("application/pdf");
                                    final boolean html = mimeType.startsWith("text/html") || mimeType.startsWith("application/xhtml+xml");
                                    if (response.isSuccessful() && (image || audio || video || pdf)) {
                                        Message.FileParams params = message.getFileParams();
                                        params.url = url.toString();
                                        if (response.header("Content-Length") != null) params.size = Long.parseLong(response.header("Content-Length"), 10);
                                        if (!Message.configurePrivateFileMessage(message)) {
                                            message.setType(image ? Message.TYPE_IMAGE : Message.TYPE_FILE);
                                        }
                                        params.setName(HttpConnectionManager.extractFilenameFromResponse(response));

                                        if (link.toString().equals(message.getRawBody())) {
                                            Element fallback = new Element("fallback", "urn:xmpp:fallback:0").setAttribute("for", Namespace.OOB);
                                            fallback.addChild("body", "urn:xmpp:fallback:0");
                                            message.addPayload(fallback);
                                        } else if (message.getRawBody().indexOf(link.toString()) >= 0) {
                                            // Part of the real body, not just a fallback
                                            Element fallback = new Element("fallback", "urn:xmpp:fallback:0").setAttribute("for", Namespace.OOB);
                                            fallback.addChild("body", "urn:xmpp:fallback:0")
                                                    .setAttribute("start", "0")
                                                    .setAttribute("end", "0");
                                            message.addPayload(fallback);
                                        }

                                        final int encryption = message.getEncryption();
                                        getHttpConnectionManager().createNewDownloadConnection(message, false, (file) -> {
                                            message.setEncryption(encryption);
                                            synchronized (message.getConversation()) {
                                                if (message.getStatus() == Message.STATUS_WAITING) sendMessage(message, true, true, false);
                                            }
                                        });
                                        return;
                                    } else if (response.isSuccessful() && html && !useI2PToConnect()) {
                                        Semaphore waiter = new Semaphore(0);
                                        OpenGraphParser.Builder openGraphBuilder = new OpenGraphParser.Builder(new OpenGraphCallback() {
                                            @Override
                                            public void onPostResponse(OpenGraphResult result) {
                                                Element rdf = new Element("Description", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
                                                rdf.setAttribute("xmlns:rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
                                                rdf.setAttribute("rdf:about", link.toString());
                                                if (result.getTitle() != null && !"".equals(result.getTitle())) {
                                                    rdf.addChild("title", "https://ogp.me/ns#").setContent(result.getTitle());
                                                }
                                                if (result.getDescription() != null && !"".equals(result.getDescription())) {
                                                    rdf.addChild("description", "https://ogp.me/ns#").setContent(result.getDescription());
                                                }
                                                if (result.getUrl() != null) {
                                                    rdf.addChild("url", "https://ogp.me/ns#").setContent(result.getUrl());
                                                }
                                                if (result.getImage() != null) {
                                                    rdf.addChild("image", "https://ogp.me/ns#").setContent(result.getImage());
                                                }
                                                if (result.getType() != null) {
                                                    rdf.addChild("type", "https://ogp.me/ns#").setContent(result.getType());
                                                }
                                                if (result.getSiteName() != null) {
                                                    rdf.addChild("site_name", "https://ogp.me/ns#").setContent(result.getSiteName());
                                                }
                                                message.addPayload(rdf);
                                                waiter.release();
                                            }

                                            public void onError(String error) {
                                                waiter.release();
                                            }
                                        })
                                                .showNullOnEmpty(true)
                                                .maxBodySize(4000)
                                                .timeout(5000);
                                        if (useTorToConnect()) {
                                            openGraphBuilder = openGraphBuilder.jsoupProxy(new JsoupProxy("127.0.0.1", 8118));
                                        }
                                        openGraphBuilder.build().parse(link.toString());
                                        waiter.tryAcquire(10L, TimeUnit.SECONDS);
                                    }
                                } catch (final IOException | InterruptedException e) {  }
                            }
                        }
                        synchronized (message.getConversation()) {
                            if (message.getStatus() == Message.STATUS_WAITING) sendMessage(message, true, true, false);
                        }
                    });
                }
            }
        }

        if (account.isOnlineAndConnected() && !inProgressJoin && !waitForPreview && message.getTimeSent() <= System.currentTimeMillis()) {
            switch (message.getEncryption()) {
                case Message.ENCRYPTION_NONE:
                    if (message.needsUploading()) {
                        if (account.httpUploadAvailable(fileBackend.getFile(message, false).getSize())
                                || conversation.getMode() == Conversation.MODE_MULTI
                                || message.fixCounterpart()) {
                            this.sendFileMessage(message, delay);
                        } else {
                            break;
                        }
                    } else {
                        packet = mMessageGenerator.generateChat(message);
                    }
                    break;
                case Message.ENCRYPTION_PGP:
                case Message.ENCRYPTION_DECRYPTED:
                    if (message.needsUploading()) {
                        if (account.httpUploadAvailable(fileBackend.getFile(message, false).getSize())
                                || conversation.getMode() == Conversation.MODE_MULTI
                                || message.fixCounterpart()) {
                            this.sendFileMessage(message, delay);
                        } else {
                            break;
                        }
                    } else {
                        packet = mMessageGenerator.generatePgpChat(message);
                    }
                    break;
                case Message.ENCRYPTION_OTR:
                    SessionImpl otrSession = conversation.getOtrSession();
                    if (otrSession != null && otrSession.getSessionStatus() == SessionStatus.ENCRYPTED) {
                        try {
                            message.setCounterpart(OtrJidHelper.fromSessionID(otrSession.getSessionID()));
                        } catch (IllegalArgumentException e) {
                            break;
                        }
                        if (message.needsUploading()) {
                            mJingleConnectionManager.startJingleFileTransfer(message);
                        } else {
                            packet = mMessageGenerator.generateOtrChat(message);
                        }
                    } else if (otrSession == null) {
                        if (message.fixCounterpart()) {
                            conversation.startOtrSession(message.getCounterpart().getResource(), true);
                        } else {
                            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": could not fix counterpart for OTR message to contact " + message.getCounterpart());
                            break;
                        }
                    } else {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + " OTR session with " + message.getContact() + " is in wrong state: " + otrSession.getSessionStatus().toString());
                    }
                    break;
                case Message.ENCRYPTION_AXOLOTL:
                    message.setFingerprint(account.getAxolotlService().getOwnFingerprint());
                    if (message.needsUploading()) {
                        if (account.httpUploadAvailable(fileBackend.getFile(message, false).getSize())
                                || conversation.getMode() == Conversation.MODE_MULTI
                                || message.fixCounterpart()) {
                            this.sendFileMessage(message, delay);
                        } else {
                            break;
                        }
                    } else {
                        XmppAxolotlMessage axolotlMessage = account.getAxolotlService().fetchAxolotlMessageFromCache(message);
                        if (axolotlMessage == null) {
                            account.getAxolotlService().preparePayloadMessage(message, delay);
                        } else {
                            packet = mMessageGenerator.generateAxolotlChat(message, axolotlMessage);
                        }
                    }
                    break;

            }
            if (packet != null) {
                if (account.getXmppConnection().getFeatures().sm() || (conversation.getMode() == Conversation.MODE_MULTI && message.getCounterpart().isBareJid())) {
                    message.setStatus(Message.STATUS_UNSEND);
                } else {
                    message.setStatus(Message.STATUS_SEND);
                }
            }
        } else {
            switch (message.getEncryption()) {
                case Message.ENCRYPTION_DECRYPTED:
                    if (!message.needsUploading()) {
                        String pgpBody = message.getEncryptedBody();
                        String decryptedBody = message.getBody();
                        message.setBody(pgpBody); //TODO might throw NPE
                        message.setEncryption(Message.ENCRYPTION_PGP);
                        if (message.edited()) {
                            message.setBody(decryptedBody);
                            message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                            if (!databaseBackend.updateMessage(message, message.getEditedId())) {
                                Log.e(Config.LOGTAG, "error updated message in DB after edit");
                            }
                            updateConversationUi();
                            return;
                        } else {
                            databaseBackend.createMessage(message);
                            saveInDb = false;
                            message.setBody(decryptedBody);
                            message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                        }
                    }
                    break;
                case Message.ENCRYPTION_OTR:
                    if (!conversation.hasValidOtrSession() && message.getCounterpart() != null) {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": create otr session without starting for " + message.getContact().getJid());
                        conversation.startOtrSession(message.getCounterpart().getResource(), false);
                    }
                    break;
                case Message.ENCRYPTION_AXOLOTL:
                    message.setFingerprint(account.getAxolotlService().getOwnFingerprint());
                    break;
            }
        }
        synchronized (mScheduledMessages) {
            if (message.getTimeSent() > System.currentTimeMillis()) {
                mScheduledMessages.put(message.getUuid(), message);
                scheduleNextIdlePing();
            } else {
                mScheduledMessages.remove(message.getUuid());
            }
        }

        boolean mucMessage = conversation.getMode() == Conversation.MODE_MULTI && !message.isPrivateMessage();
        if (mucMessage) {
            message.setCounterpart(conversation.getMucOptions().getSelf().getFullJid());
        }

        if (resend) {
            if (packet != null && addToConversation) {
                if (account.getXmppConnection().getFeatures().sm() || mucMessage) {
                    markMessage(message, Message.STATUS_UNSEND);
                } else {
                    markMessage(message, Message.STATUS_SEND);
                }
            }
        } else {
            if (addToConversation) {
                conversation.add(message);
            }
            if (saveInDb) {
                databaseBackend.createMessage(message);
            } else if (message.edited()) {
                if (!databaseBackend.updateMessage(message, message.getEditedId())) {
                    Log.e(Config.LOGTAG, "error updated message in DB after edit");
                }
            }
            updateConversationUi();
        }
        if (packet != null) {
            if (delay) {
                mMessageGenerator.addDelay(packet, message.getTimeSent());
            }
            if (conversation.setOutgoingChatState(Config.DEFAULT_CHAT_STATE)) {
                if (this.sendChatStates()) {
                    packet.addChild(ChatState.toElement(conversation.getOutgoingChatState()));
                }
            }
            sendMessagePacket(account, packet);
            if (message.getConversation().getMode() == Conversation.MODE_MULTI && message.hasCustomEmoji()) {
                if (message.getConversation() instanceof Conversation) presenceToMuc((Conversation) message.getConversation());
            }
        }
    }

    private boolean isJoinInProgress(final Conversation conversation) {
        final Account account = conversation.getAccount();
        synchronized (account.inProgressConferenceJoins) {
            if (conversation.getMode() == Conversational.MODE_MULTI) {
                final boolean inProgress = account.inProgressConferenceJoins.contains(conversation);
                final boolean pending = account.pendingConferenceJoins.contains(conversation);
                final boolean inProgressJoin = inProgress || pending;
                if (inProgressJoin) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": holding back message to group. inProgress=" + inProgress + ", pending=" + pending);
                }
                return inProgressJoin;
            } else {
                return false;
            }
        }
    }

    private void sendUnsentMessages(final Conversation conversation) {
        synchronized (conversation) {
            conversation.findWaitingMessages(message -> resendMessage(message, true));
        }
    }
    private void resendFailedMessages(final Conversation conversation) {
        final Runnable runnable = () -> {
            conversation.findResendAbleFailedMessage(message -> {
                if (message.increaseResendCount() < maxResendTime()) {
                    Log.d(Config.LOGTAG, "Resend failed message " + message.getErrorMessage() + " at times " + message.getResendCount() + " bytes for " + conversation.getJid());
                    // because it'll use a custom delay here, only the last time will delay the message.
                    resendFailedMessages(message);
                }
            });
        };
        mMessageResendTaskExecuter.execute((runnable));
    }
    private void resendFailedFileMessages(final Conversation conversation) {
        final Runnable runnable = () -> {
            conversation.findFailedMessagesWithFiles(message -> {
                if (mHttpConnectionManager.getAutoAcceptFileSize() >= message.getFileParams().size) {
                    Log.d(Config.LOGTAG, "Resend failed message with size " + message.getFileParams().size + " bytes for " + conversation.getJid());
                    resendMessage(message, true);
                }
            });
        };
        mDatabaseWriterExecutor.execute((runnable));
    }

    public void resendMessage(final Message message, final boolean delay) {
        sendMessage(message, true, false, delay);
    }

    public void requestEasyOnboardingInvite(final Account account, final EasyOnboardingInvite.OnInviteRequested callback) {
        final XmppConnection connection = account.getXmppConnection();
        final Jid jid = connection == null ? null : connection.getJidForCommand(Namespace.EASY_ONBOARDING_INVITE);
        if (jid == null) {
            callback.inviteRequestFailed(getString(R.string.server_does_not_support_easy_onboarding_invites));
            return;
        }
        final IqPacket request = new IqPacket(IqPacket.TYPE.SET);
        request.setTo(jid);
        final Element command = request.addChild("command", Namespace.COMMANDS);
        command.setAttribute("node", Namespace.EASY_ONBOARDING_INVITE);
        command.setAttribute("action", "execute");
        sendIqPacket(account, request, (a, response) -> {
            if (response.getType() == IqPacket.TYPE.RESULT) {
                final Element resultCommand = response.findChild("command", Namespace.COMMANDS);
                final Element x = resultCommand == null ? null : resultCommand.findChild("x", Namespace.DATA);
                if (x != null) {
                    final Data data = Data.parse(x);
                    final String uri = data.getValue("uri");
                    final String landingUrl = data.getValue("landing-url");
                    if (uri != null) {
                        final EasyOnboardingInvite invite = new EasyOnboardingInvite(jid.getDomain().toEscapedString(), uri, landingUrl);
                        callback.inviteRequested(invite);
                        return;
                    }
                }
                callback.inviteRequestFailed(getString(R.string.unable_to_parse_invite));
                Log.d(Config.LOGTAG, response.toString());
            } else if (response.getType() == IqPacket.TYPE.ERROR) {
                callback.inviteRequestFailed(IqParser.errorMessage(response));
            } else {
                callback.inviteRequestFailed(getString(R.string.remote_server_timeout));
            }
        });

    }

    public void fetchRosterFromServer(final Account account) {
        final IqPacket iqPacket = new IqPacket(IqPacket.TYPE.GET);
        if (!"".equals(account.getRosterVersion())) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid()
                    + ": fetching roster version " + account.getRosterVersion());
        } else {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": fetching roster");
        }
        iqPacket.query(Namespace.ROSTER).setAttribute("ver", account.getRosterVersion());
        sendIqPacket(account, iqPacket, mIqParser);
    }

    public void fetchBookmarks(final Account account) {
        final IqPacket iqPacket = new IqPacket(IqPacket.TYPE.GET);
        final Element query = iqPacket.query("jabber:iq:private");
        query.addChild("storage", Namespace.BOOKMARKS);
        final OnIqPacketReceived callback = (a, response) -> {
            if (response.getType() == IqPacket.TYPE.RESULT) {
                final Element query1 = response.query();
                final Element storage = query1.findChild("storage", "storage:bookmarks");
                Map<Jid, Bookmark> bookmarks = Bookmark.parseFromStorage(storage, account);
                processBookmarksInitial(a, bookmarks, false);
            } else {
                Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": could not fetch bookmarks");
            }
        };
        sendIqPacket(account, iqPacket, callback);
    }

    public void fetchBookmarks2(final Account account) {
        final IqPacket retrieve = mIqGenerator.retrieveBookmarks();
        sendIqPacket(account, retrieve, (a, response) -> {
            if (response.getType() == IqPacket.TYPE.RESULT) {
                final Element pubsub = response.findChild("pubsub", Namespace.PUBSUB);
                final Map<Jid, Bookmark> bookmarks = Bookmark.parseFromPubsub(pubsub, a);
                processBookmarksInitial(a, bookmarks, true);
            }
        });
    }

    private void fetchMessageDisplayedSynchronization(final Account account) {
        Log.d(Config.LOGTAG, account.getJid() + ": retrieve mds");
        final var retrieve = mIqGenerator.retrieveMds();
        sendIqPacket(
                account,
                retrieve,
                (a, response) -> {
                    if (response.getType() != IqPacket.TYPE.RESULT) {
                        return;
                    }
                    final var pubSub = response.findChild("pubsub", Namespace.PUBSUB);
                    final Element items = pubSub == null ? null : pubSub.findChild("items");
                    if (items == null
                            || !Namespace.MDS_DISPLAYED.equals(items.getAttribute("node"))) {
                        return;
                    }
                    for (final Element child : items.getChildren()) {
                        if ("item".equals(child.getName())) {
                            processMdsItem(account, child);
                        }
                    }
                });
    }


    public void processMdsItem(final Account account, final Element item) {
        final Jid jid =
                item == null ? null : InvalidJid.getNullForInvalid(item.getAttributeAsJid("id"));
        if (jid == null) {
            return;
        }
        final Element displayed = item.findChild("displayed", Namespace.MDS_DISPLAYED);
        final Element stanzaId =
                displayed == null ? null : displayed.findChild("stanza-id", Namespace.STANZA_IDS);
        final String id = stanzaId == null ? null : stanzaId.getAttribute("id");
        final Conversation conversation = find(account, jid);
        if (id != null && conversation != null) {
            conversation.setDisplayState(id);
            markReadUpToStanzaId(conversation, id);
        }
    }

    public void markReadUpToStanzaId(final Conversation conversation, final String stanzaId) {
        final Message message = conversation.findMessageWithServerMsgId(stanzaId);
        if (message == null) { // do we want to check if isRead?
            return;
        }
        markReadUpTo(conversation, message);
    }

    public void markReadUpTo(final Conversation conversation, final Message message) {
        final boolean isDismissNotification = isDismissNotification(message);
        final var uuid = message.getUuid();
        Log.d(
                Config.LOGTAG,
                conversation.getAccount().getJid().asBareJid()
                        + ": mark "
                        + conversation.getJid().asBareJid()
                        + " as read up to "
                        + uuid);
        markRead(conversation, uuid, isDismissNotification);
    }

    private static boolean isDismissNotification(final Message message) {
        Message next = message.next();
        while (next != null) {
            if (message.getStatus() == Message.STATUS_RECEIVED) {
                return false;
            }
            next = next.next();
        }
        return true;
    }

    public void processBookmarksInitial(Account account, Map<Jid, Bookmark> bookmarks, final boolean pep) {
        final Set<Jid> previousBookmarks = account.getBookmarkedJids();
        final boolean synchronizeWithBookmarks = synchronizeWithBookmarks();
        for (Bookmark bookmark : bookmarks.values()) {
            previousBookmarks.remove(bookmark.getJid().asBareJid());
            processModifiedBookmark(bookmark, pep, synchronizeWithBookmarks);
        }
        if (pep && synchronizeWithBookmarks) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": " + previousBookmarks.size() + " bookmarks have been removed");
            for (Jid jid : previousBookmarks) {
                processDeletedBookmark(account, jid);
            }
        }
        account.setBookmarks(bookmarks);
    }

    public void processDeletedBookmark(Account account, Jid jid) {
        final Conversation conversation = find(account, jid);
        if (conversation != null && conversation.getMucOptions().getError() == MucOptions.Error.DESTROYED) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": archiving destroyed conference (" + conversation.getJid() + ") after receiving pep");
            archiveConversation(conversation, false);
        }
    }

    private void processModifiedBookmark(Bookmark bookmark, final boolean pep, final boolean synchronizeWithBookmarks) {
        final Account account = bookmark.getAccount();
        Conversation conversation = find(bookmark);
        if (conversation != null) {
            if (conversation.getMode() != Conversation.MODE_MULTI) {
                return;
            }
            bookmark.setConversation(conversation);
            if (pep && synchronizeWithBookmarks && !bookmark.autojoin()) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": archiving conference (" + conversation.getJid() + ") after receiving pep");
                archiveConversation(conversation, false);
            } else {
                final MucOptions mucOptions = conversation.getMucOptions();
                if (mucOptions.getError() == MucOptions.Error.NICK_IN_USE) {
                    final String current = mucOptions.getActualNick();
                    final String proposed = mucOptions.getProposedNick();
                    if (current != null && !current.equals(proposed)) {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": proposed nick changed after bookmark push " + current + "->" + proposed);
                        joinMuc(conversation);
                    }
                }
            }
        } else if (synchronizeWithBookmarks && bookmark.autojoin()) {
            conversation = findOrCreateConversation(account, bookmark.getFullJid(), true, true, false);
            bookmark.setConversation(conversation);
        }
    }

    public void processModifiedBookmark(Bookmark bookmark) {
        final boolean synchronizeWithBookmarks = synchronizeWithBookmarks();
        processModifiedBookmark(bookmark, true, synchronizeWithBookmarks);
    }

    public void createBookmark(final Account account, final Bookmark bookmark) {
        account.putBookmark(bookmark);
        final XmppConnection connection = account.getXmppConnection();
        if (connection == null) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid()+": no connection. ignoring bookmark creation");
        } else if (connection != null && connection.getFeatures().bookmarks2()) {
            Log.d(Config.LOGTAG,account.getJid().asBareJid() + ": pushing bookmark via Bookmarks 2");
            final Element item = mIqGenerator.publishBookmarkItem(bookmark);
            pushNodeAndEnforcePublishOptions(account, Namespace.BOOKMARKS2, item, bookmark.getJid().asBareJid().toEscapedString(), PublishOptions.persistentWhitelistAccessMaxItems());
        } else if (connection != null && connection.getFeatures().bookmarksConversion()) {
            pushBookmarksPep(account);
        } else {
            pushBookmarksPrivateXml(account);
        }
    }

    public void deleteBookmark(final Account account, final Bookmark bookmark) {
        if (bookmark.getJid().toString().equals("support@conference.monocles.eu")) {
            getPreferences().edit().putBoolean("monocles_support_bookmark_deleted", true).apply();
        }
                        /*              //TODO: Add bridges as contacts
        if (bookmark.getJid().toString().equals("whatsapp.monocles.eu")) {
            getPreferences().edit().putBoolean("whatsapp_bridge_bookmark_deleted", true).apply();
        }
        if (bookmark.getJid().toString().equals("signal.monocles.eu")) {
            getPreferences().edit().putBoolean("signal_bridge_bookmark_deleted", true).apply();
        }
        if (bookmark.getJid().toString().equals("telegram.monocles.eu")) {
            getPreferences().edit().putBoolean("telegram_bridge_bookmark_deleted", true).apply();
        }
        if (bookmark.getJid().toString().equals("cheogram.com")) {
            getPreferences().edit().putBoolean("sms_bridge_bookmark_deleted", true).apply();
        }
         */

        account.removeBookmark(bookmark);
        final XmppConnection connection = account.getXmppConnection();
        if (connection == null) return;

        if (connection.getFeatures().bookmarks2()) {
            final IqPacket request = mIqGenerator.deleteItem(Namespace.BOOKMARKS2, bookmark.getJid().asBareJid().toEscapedString());
            Log.d(Config.LOGTAG,account.getJid().asBareJid() + ": removing bookmark via Bookmarks 2");
            sendIqPacket(account, request, (a, response) -> {
                if (response.getType() == IqPacket.TYPE.ERROR) {
                    Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": unable to delete bookmark " + response.getErrorCondition());
                }
            });
        } else if (connection.getFeatures().bookmarksConversion()) {
            pushBookmarksPep(account);
        } else {
            pushBookmarksPrivateXml(account);
        }
    }

    public void pushBookmarks(Account account) {
        if (account.getXmppConnection().getFeatures().bookmarksConversion()) {
            pushBookmarksPep(account);
        } else {
            pushBookmarksPrivateXml(account);
        }
    }

    private void pushBookmarksPrivateXml(Account account) {
        if (!account.areBookmarksLoaded()) return;

        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": pushing bookmarks via private xml");
        IqPacket iqPacket = new IqPacket(IqPacket.TYPE.SET);
        Element query = iqPacket.query("jabber:iq:private");
        Element storage = query.addChild("storage", "storage:bookmarks");
        for (final Bookmark bookmark : account.getBookmarks()) {
            storage.addChild(bookmark);
        }
        sendIqPacket(account, iqPacket, mDefaultIqHandler);
    }

    private void pushBookmarksPep(Account account) {
        if (!account.areBookmarksLoaded()) return;

        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": pushing bookmarks via pep");
        final Element storage = new Element("storage", "storage:bookmarks");
        for (final Bookmark bookmark : account.getBookmarks()) {
            storage.addChild(bookmark);
        }
        pushNodeAndEnforcePublishOptions(account, Namespace.BOOKMARKS, storage, "current", PublishOptions.persistentWhitelistAccess());
    }

    private void pushNodeAndEnforcePublishOptions(final Account account, final String node, final Element element, final String id, final Bundle options) {
        pushNodeAndEnforcePublishOptions(account, node, element, id, options, true);
    }

    private void pushNodeAndEnforcePublishOptions(final Account account, final String node, final Element element, final String id, final Bundle options, final boolean retry) {
        final IqPacket packet = mIqGenerator.publishElement(node, element, id, options);
        sendIqPacket(account, packet, (a, response) -> {
            if (response.getType() == IqPacket.TYPE.RESULT) {
                return;
            }
            if (retry && PublishOptions.preconditionNotMet(response)) {
                pushNodeConfiguration(account, node, options, new OnConfigurationPushed() {
                    @Override
                    public void onPushSucceeded() {
                        pushNodeAndEnforcePublishOptions(account, node, element, id, options, false);
                    }

                    @Override
                    public void onPushFailed() {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": unable to push node configuration (" + node + ")");
                    }
                });
            } else {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": error publishing "+node+" (retry=" + retry + ") " + response);
            }
        });
    }

    private void restoreFromDatabase() {
        synchronized (this.conversations) {
            try {
                final Map<String, Account> accountLookupTable = new Hashtable<>();
                for (Account account : this.accounts) {
                    accountLookupTable.put(account.getUuid(), account);
                }
                Log.d(Config.LOGTAG, "restoring conversations...");
                final long startTimeConversationsRestore = SystemClock.elapsedRealtime();
                this.conversations.addAll(databaseBackend.getConversations(Conversation.STATUS_AVAILABLE));
                for (Iterator<Conversation> iterator = this.conversations.listIterator(); iterator.hasNext(); ) {
                    Conversation conversation = iterator.next();
                    Account account = accountLookupTable.get(conversation.getAccountUuid());
                    if (account != null) {
                        conversation.setAccount(account);
                    } else {
                        Log.e(Config.LOGTAG, "unable to restore Conversations with " + conversation.getJid());
                        conversations.remove(conversation);
                    }
                }
                long diffConversationsRestore = SystemClock.elapsedRealtime() - startTimeConversationsRestore;
                Log.d(Config.LOGTAG, "finished restoring conversations in " + diffConversationsRestore + "ms");
                Runnable runnable = () -> {
                    if (DatabaseBackend.requiresMessageIndexRebuild()) {
                        DatabaseBackend.getInstance(this).rebuildMessagesIndex();
                    }
                    mutedMucUsers = databaseBackend.loadMutedMucUsers();
                    final long deletionDate = getAutomaticMessageDeletionDate();
                    mLastExpiryRun.set(SystemClock.elapsedRealtime());
                    if (deletionDate > 0) {
                        Log.d(Config.LOGTAG, "deleting messages that are older than " + AbstractGenerator.getTimestamp(deletionDate));
                        expireOldMessages(deletionDate, false);
                    }
                    Log.d(Config.LOGTAG, "restoring roster...");
                for (final Account account : accounts) {
                        databaseBackend.readRoster(account.getRoster());
                        account.initAccountServices(XmppConnectionService.this); //roster needs to be loaded at this stage
                    }
                    getDrawableCache().evictAll();
                    loadPhoneContacts();
                    Log.d(Config.LOGTAG, "restoring messages...");
                    final long startMessageRestore = SystemClock.elapsedRealtime();
                    final Conversation quickLoad = QuickLoader.get(this.conversations);
                    if (quickLoad != null) {

                        restoreMessages(quickLoad);
                        updateConversationUi();
                        final long diffMessageRestore = SystemClock.elapsedRealtime() - startMessageRestore;
                        Log.d(Config.LOGTAG, "quickly restored " + quickLoad.getName() + " after " + diffMessageRestore + "ms");
                    }
                    for (Conversation conversation : this.conversations) {
                        if (quickLoad != conversation) {
                            restoreMessages(conversation);
                        }
                    }
                    mNotificationService.finishBacklog();
                    restoredFromDatabaseLatch.countDown();
                    final long diffMessageRestore = SystemClock.elapsedRealtime() - startMessageRestore;
                    Log.d(Config.LOGTAG, "finished restoring messages in " + diffMessageRestore + "ms");
                    updateConversationUi();
                };
                mDatabaseReaderExecutor.execute(runnable); //will contain one write command (expiry) but that's fine
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "error restoring messages: " + e);
            }
        }
    }

    private void restoreMessages(Conversation conversation) {
        conversation.addAll(0, databaseBackend.getMessages(conversation, Config.PAGE_SIZE));
        conversation.findUnsentTextMessages(message -> markMessage(message, Message.STATUS_WAITING));
        conversation.findUnreadMessagesAndCalls(mNotificationService::pushFromBacklog);
    }

    public void loadPhoneContacts() {
        mContactMergerExecutor.execute(() -> {
            final Map<Jid, JabberIdContact> contacts = JabberIdContact.load(this);
            Log.d(Config.LOGTAG, "start merging phone contacts with roster");
            for (final Account account : accounts) {
                final List<Contact> withSystemAccounts = account.getRoster().getWithSystemAccounts(JabberIdContact.class);
                for (final JabberIdContact jidContact : contacts.values()) {
                    final Contact contact = account.getRoster().getContact(jidContact.getJid());
                    boolean needsCacheClean = contact.setPhoneContact(jidContact);
                    if (needsCacheClean) {
                        getAvatarService().clear(contact);
                    }
                    withSystemAccounts.remove(contact);
                }
                for (final Contact contact : withSystemAccounts) {
                    boolean needsCacheClean = contact.unsetPhoneContact(JabberIdContact.class);
                    if (needsCacheClean) {
                        getAvatarService().clear(contact);
                    }
                }
            }
            Log.d(Config.LOGTAG, "finished merging phone contacts");
            mShortcutService.refresh(mInitialAddressbookSyncCompleted.compareAndSet(false, true));
            updateRosterUi(UpdateRosterReason.INIT);
            mQuickConversationsService.considerSync();
        });
    }

    public void syncRoster(final Account account) {
        mRosterSyncTaskManager.execute(account, () -> {
            unregisterPhoneAccounts(account);
            databaseBackend.writeRoster(account.getRoster());
            try { Thread.sleep(500); } catch (InterruptedException e) { }
        });
    }

    public List<Conversation> getConversations() {
        return this.conversations;
    }

    private void markFileDeleted(final File file) {
        synchronized (FILENAMES_TO_IGNORE_DELETION) {
            if (FILENAMES_TO_IGNORE_DELETION.remove(file.getAbsolutePath())) {
                Log.d(Config.LOGTAG, "ignored deletion of " + file.getAbsolutePath());
                return;
            }
        }
        final boolean isInternalFile = fileBackend.isInternalFile(file);
        final List<String> uuids = databaseBackend.markFileAsDeleted(file, isInternalFile);
        Log.d(Config.LOGTAG, "deleted file " + file.getAbsolutePath() + " internal=" + isInternalFile + ", database hits=" + uuids.size());
        markUuidsAsDeletedFiles(uuids);
    }

    private void markUuidsAsDeletedFiles(List<String> uuids) {
        boolean deleted = false;
        for (Conversation conversation : getConversations()) {
            deleted |= conversation.markAsDeleted(uuids);
        }
        for (final String uuid : uuids) {
            evictPreview(uuid);
        }
        if (deleted) {
            updateConversationUi();
        }
    }

    private void markChangedFiles(List<DatabaseBackend.FilePathInfo> infos) {
        final boolean[] changed = {false};
        for (Conversation conversation : getConversations()) {
            changed[0] |= conversation.markAsChanged(infos);
        }
        if (changed[0]) {
            updateConversationUi();
        }
    }

    public void populateWithOrderedConversations(final List<Conversation> list) {
        populateWithOrderedConversations(list, true, true);
    }

    public void populateWithOrderedConversations(final List<Conversation> list, final boolean includeNoFileUpload) {
        populateWithOrderedConversations(list, includeNoFileUpload, true);
    }

    public void populateWithOrderedConversations(final List<Conversation> list, final boolean includeNoFileUpload, final boolean sort) {
        final List<String> orderedUuids;
        if (sort) {
            orderedUuids = null;
        } else {
            orderedUuids = new ArrayList<>();
            for (Conversation conversation : list) {
                orderedUuids.add(conversation.getUuid());
            }
        }
        list.clear();
        if (includeNoFileUpload) {
            list.addAll(getConversations());
        } else {
            for (Conversation conversation : getConversations()) {
                if (conversation.getMode() == Conversation.MODE_SINGLE
                        || (conversation.getAccount().httpUploadAvailable() && conversation.getMucOptions().participating())) {
                    list.add(conversation);
                }
            }
        }
        try {
            if (orderedUuids != null) {
                Collections.sort(list, (a, b) -> {
                    final int indexA = orderedUuids.indexOf(a.getUuid());
                    final int indexB = orderedUuids.indexOf(b.getUuid());
                    if (indexA == -1 || indexB == -1 || indexA == indexB) {
                        return a.compareTo(b);
                    }
                    return indexA - indexB;
                });
            } else {
                Collections.sort(list);
            }
        } catch (IllegalArgumentException e) {
            //ignore
        }
    }

    public void loadMoreMessages(final Conversation conversation, final long timestamp, final OnMoreMessagesLoaded callback) {
        if (XmppConnectionService.this.getMessageArchiveService().queryInProgress(conversation, callback)) {
            return;
        } else if (timestamp == 0) {
            return;
        }
        Log.d(Config.LOGTAG, "load more messages for " + conversation.getName() + " prior to " + MessageGenerator.getTimestamp(timestamp));
        final Runnable runnable = () -> {
            final Account account = conversation.getAccount();
            List<Message> messages = databaseBackend.getMessages(conversation, 50, timestamp);
            if (messages.size() > 0) {
                conversation.addAll(0, messages);
                callback.onMoreMessagesLoaded(messages.size(), conversation);
            } else if (conversation.hasMessagesLeftOnServer()
                    && account.isOnlineAndConnected()
                    && conversation.getLastClearHistory().getTimestamp() == 0) {
                final boolean mamAvailable;
                if (conversation.getMode() == Conversation.MODE_SINGLE) {
                    mamAvailable = account.getXmppConnection().getFeatures().mam() && !conversation.getContact().isBlocked();
                } else {
                    mamAvailable = conversation.getMucOptions().mamSupport();
                }
                if (mamAvailable) {
                    MessageArchiveService.Query query = getMessageArchiveService().query(conversation, new MamReference(0), timestamp, false);
                    if (query != null) {
                        query.setCallback(callback);
                        callback.informUser(R.string.fetching_history_from_server);
                    } else {
                        callback.informUser(R.string.not_fetching_history_retention_period);
                    }
                }
            }
        };
        mDatabaseReaderExecutor.execute(runnable);
    }

    public List<Account> getAccounts() {
        return this.accounts;
    }

    /**
     * This will find all conferences with the contact as member and also the conference that is the contact (that 'fake' contact is used to store the avatar)
     */
    public List<Conversation> findAllConferencesWith(Contact contact) {
        final ArrayList<Conversation> results = new ArrayList<>();
        for (final Conversation c : conversations) {
            if (c.getMode() != Conversation.MODE_MULTI) {
                continue;
            }
            final MucOptions mucOptions = c.getMucOptions();
            if (c.getJid().asBareJid().equals(contact.getJid().asBareJid()) || (mucOptions != null && mucOptions.isContactInRoom(contact))) {
                results.add(c);
            }
        }
        return results;
    }

    public Conversation find(final Iterable<Conversation> haystack, final Contact contact) {
        for (final Conversation conversation : haystack) {
            if (conversation.getContact() == contact) {
                return conversation;
            }
        }
        return null;
    }

    public Conversation find(final Iterable<Conversation> haystack, final Account account, final Jid jid) {
        if (jid == null) {
            return null;
        }
        for (final Conversation conversation : haystack) {
            if ((account == null || conversation.getAccount() == account)
                    && (conversation.getJid().asBareJid().equals(jid.asBareJid()))) {
                return conversation;
            }
        }
        return null;
    }

    public boolean isConversationsListEmpty(final Conversation ignore) {
        synchronized (this.conversations) {
            final int size = this.conversations.size();
            return size == 0 || size == 1 && this.conversations.get(0) == ignore;
        }
    }

    public boolean isConversationStillOpen(final Conversation conversation) {
        synchronized (this.conversations) {
            for (Conversation current : this.conversations) {
                if (current == conversation) {
                    return true;
                }
            }
        }
        return false;
    }

    public void maybeRegisterWithMuc(Conversation c, String nickArg) {
        final var nick = nickArg == null ? c.getMucOptions().getSelf().getFullJid().getResource() : nickArg;
        final IqPacket register = new IqPacket(IqPacket.TYPE.GET);
        register.query(Namespace.REGISTER);
        register.setTo(c.getJid().asBareJid());
        sendIqPacket(c.getAccount(), register, (a, response) -> {
            if (response.getType() == IqPacket.TYPE.RESULT) {
                final Element query = response.query(Namespace.REGISTER);
                String username = query.findChildContent("username", Namespace.REGISTER);
                if (username == null) username = query.findChildContent("nick", Namespace.REGISTER);
                if (username != null && username.equals(nick)) {
                    // Already registered with this nick, done
                    Log.d(Config.LOGTAG, "Already registered with " + c.getJid().asBareJid() + " as " + username);
                    return;
                }
                Data form = Data.parse(query.findChild("x", Namespace.DATA));
                if (form != null) {
                    final var field = form.getFieldByName("muc#register_roomnick");
                    if (field != null && nick.equals(field.getValue())) {
                        Log.d(Config.LOGTAG, "Already registered with " + c.getJid().asBareJid() + " as " + field.getValue());
                        return;
                    }
                }
                if (form == null || !"form".equals(form.getFormType()) || !form.getFields().stream().anyMatch(f -> f.isRequired() && !"muc#register_roomnick".equals(f.getFieldName()))) {
                    // No form, result form, or no required fields other than nickname, let's just send nickname
                    if (form == null || !"form".equals(form.getFormType())) {
                        form = new Data();
                        form.put("FORM_TYPE", "http://jabber.org/protocol/muc#register");
                    }
                    form.put("muc#register_roomnick", nick);
                    form.submit();
                    final IqPacket finish = new IqPacket(IqPacket.TYPE.SET);
                    finish.query(Namespace.REGISTER).addChild(form);
                    finish.setTo(c.getJid().asBareJid());
                    sendIqPacket(c.getAccount(), finish, (a2, response2) -> {
                        if (response.getType() == IqPacket.TYPE.RESULT) {
                            Log.w(Config.LOGTAG, "Success registering with channel " + c.getJid().asBareJid() + "/" + nick);
                        } else {
                            Log.w(Config.LOGTAG, "Error registering with channel: " + response2);
                        }
                    });
                } else {
                    // TODO: offer registration form to user
                    Log.d(Config.LOGTAG, "Complex registration form for " + c.getJid().asBareJid() + ": " + response);
                }
            } else {
                // We said maybe. Guess not
                Log.d(Config.LOGTAG, "Could not register with " + c.getJid().asBareJid() + ": " + response);
            }
        });
    }

    public void deregisterWithMuc(Conversation c) {
        final IqPacket register = new IqPacket(IqPacket.TYPE.GET);
        register.query(Namespace.REGISTER).addChild("remove");
        register.setTo(c.getJid().asBareJid());
        sendIqPacket(c.getAccount(), register, (a, response) -> {
            if (response.getType() == IqPacket.TYPE.RESULT) {
                Log.d(Config.LOGTAG, "deregistered with " + c.getJid().asBareJid());
            } else {
                Log.w(Config.LOGTAG, "Could not deregister with " + c.getJid().asBareJid() + ": " + response);
            }
        });
    }

    public Conversation findOrCreateConversation(Account account, Jid jid, boolean muc, final boolean async) {
        return this.findOrCreateConversation(account, jid, muc, false, async);
    }

    public Conversation findOrCreateConversation(final Account account, final Jid jid, final boolean muc, final boolean joinAfterCreate, final boolean async) {
        return this.findOrCreateConversation(account, jid, muc, joinAfterCreate, null, async, null);
    }

    public Conversation findOrCreateConversation(final Account account, final Jid jid, final boolean muc, final boolean joinAfterCreate, final MessageArchiveService.Query query, final boolean async) {
        return this.findOrCreateConversation(account, jid, muc, joinAfterCreate, query, async, null);
    }

    public Conversation findOrCreateConversation(final Account account, final Jid jid, final boolean muc, final boolean joinAfterCreate, final MessageArchiveService.Query query, final boolean async, final String password) {
        synchronized (this.conversations) {
            Conversation conversation = find(account, jid);
            if (conversation != null) {
                return conversation;
            }
            conversation = databaseBackend.findConversation(account, jid);
            final boolean loadMessagesFromDb;
            if (conversation != null) {
                conversation.setStatus(Conversation.STATUS_AVAILABLE);
                conversation.setAccount(account);
                if (muc) {
                    conversation.setMode(Conversation.MODE_MULTI);
                    conversation.setContactJid(jid);
                    if (password != null) conversation.getMucOptions().setPassword(password);
                } else {
                    conversation.setMode(Conversation.MODE_SINGLE);
                    conversation.setContactJid(jid.asBareJid());
                }
                databaseBackend.updateConversation(conversation);
                loadMessagesFromDb = conversation.messagesLoaded.compareAndSet(true, false);
            } else {
                String conversationName;
                Contact contact = account.getRoster().getContact(jid);
                if (contact != null) {
                    conversationName = contact.getDisplayName();
                } else {
                    conversationName = jid.getLocal();
                }
                if (muc) {
                    conversation = new Conversation(conversationName, account, jid,
                            Conversation.MODE_MULTI);
                    if (password != null) conversation.getMucOptions().setPassword(password);
                } else {
                    conversation = new Conversation(conversationName, account, jid.asBareJid(),
                            Conversation.MODE_SINGLE);
                }
                this.databaseBackend.createConversation(conversation);
                loadMessagesFromDb = false;
            }
            final Conversation c = conversation;
            final Runnable runnable = () -> {
                if (loadMessagesFromDb) {
                    c.addAll(0, databaseBackend.getMessages(c, Config.PAGE_SIZE));
                    updateConversationUi();
                    c.messagesLoaded.set(true);
                }
                if (account.getXmppConnection() != null
                        && !c.getContact().isBlocked()
                        && account.getXmppConnection().getFeatures().mam()
                        && !muc) {
                    if (query == null) {
                        mMessageArchiveService.query(c);
                    } else {
                        if (query.getConversation() == null) {
                            mMessageArchiveService.query(c, query.getStart(), query.isCatchup());
                        }
                    }
                }
                if (joinAfterCreate) {
                    joinMuc(c);

                }
            };
            if (async) {
                mDatabaseReaderExecutor.execute(runnable);
            } else {
                runnable.run();
            }
            this.conversations.add(conversation);
            updateConversationUi();
            return conversation;
        }
    }

    public Conversation findConversation(final Account account, final Jid jid, final boolean muc) {
        synchronized (this.conversations) {
            Conversation conversation = find(account, jid);
            if (conversation != null) {
                return conversation;
            }
            conversation = databaseBackend.findConversation(account, jid);
            if (conversation != null) {
                conversation.setStatus(Conversation.STATUS_AVAILABLE);
                conversation.setAccount(account);
                if (muc) {
                    conversation.setMode(Conversation.MODE_MULTI);
                    conversation.setContactJid(jid);
                } else {
                    conversation.setMode(Conversation.MODE_SINGLE);
                    conversation.setContactJid(jid.asBareJid());
                }
                databaseBackend.updateConversation(conversation);
            } else {
                String conversationName;
                Contact contact = account.getRoster().getContact(jid);
                if (contact != null) {
                    conversationName = contact.getDisplayName();
                } else {
                    conversationName = jid.getLocal();
                }
                if (muc) {
                    conversation = new Conversation(conversationName, account, jid,
                            Conversation.MODE_MULTI);
                } else {
                    conversation = new Conversation(conversationName, account, jid.asBareJid(),
                            Conversation.MODE_SINGLE);
                }
            }
            return conversation;
        }
    }

    public void archiveConversation(Conversation conversation) {
        archiveConversation(conversation, true);
    }

    private void archiveConversation(Conversation conversation, final boolean maySynchronizeWithBookmarks) {
        if (isOnboarding()) return;

        getNotificationService().clear(conversation);
        conversation.setStatus(Conversation.STATUS_ARCHIVED);
        conversation.setNextMessage(null);
        if (Compatibility.runsTwentySix()) {
            try {
                mNotificationService.cleanNotificationChannels(this, conversation.getUuid());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        synchronized (this.conversations) {
            getMessageArchiveService().kill(conversation);
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                if (conversation.getAccount().getStatus() == Account.State.ONLINE) {
                    final Bookmark bookmark = conversation.getBookmark();
                    if (maySynchronizeWithBookmarks && bookmark != null && synchronizeWithBookmarks()) {
                        if (conversation.getMucOptions().getError() == MucOptions.Error.DESTROYED) {
                            Account account = bookmark.getAccount();
                            bookmark.setConversation(null);
                            deleteBookmark(account, bookmark);
                        } else if (bookmark.autojoin()) {
                            bookmark.setAutojoin(false);
                            createBookmark(bookmark.getAccount(), bookmark);
                        }
                    }
                }
                deregisterWithMuc(conversation);
                leaveMuc(conversation);
            } else {
                if (conversation.getContact().getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
                    stopPresenceUpdatesTo(conversation.getContact());
                }
            }
            updateConversation(conversation);
            this.conversations.remove(conversation);
            updateConversationUi();
        }
    }

    public void stopPresenceUpdatesTo(Contact contact) {
        Log.d(Config.LOGTAG, "Canceling presence request from " + contact.getJid().toString());
        sendPresencePacket(contact.getAccount(), mPresenceGenerator.stopPresenceUpdatesTo(contact));
        contact.resetOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST);
    }

    public void createAccount(final Account account) {
        account.initAccountServices(this);
        databaseBackend.createAccount(account);
        if (CallIntegration.hasSystemFeature(this)) {
            CallIntegrationConnectionService.togglePhoneAccountAsync(this, account);
        }
        this.accounts.add(account);
        this.reconnectAccountInBackground(account);
        updateAccountUi();
        syncEnabledAccountSetting();
        toggleForegroundService();
    }

    private void syncEnabledAccountSetting() {
        final boolean hasEnabledAccounts = hasEnabledAccounts();
        getPreferences().edit().putBoolean(EventReceiver.SETTING_ENABLED_ACCOUNTS, hasEnabledAccounts).apply();
        toggleSetProfilePictureActivity(hasEnabledAccounts);
    }

    private void toggleSetProfilePictureActivity(final boolean enabled) {
        try {
            final ComponentName name = new ComponentName(this, ChooseAccountForProfilePictureActivity.class);
            final int targetState = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            getPackageManager().setComponentEnabledSetting(name, targetState, PackageManager.DONT_KILL_APP);
        } catch (IllegalStateException e) {
            Log.d(Config.LOGTAG, "unable to toggle profile picture activity");
        }
    }
    public boolean reconfigurePushDistributor() {
        return this.unifiedPushBroker.reconfigurePushDistributor();
    }

    private Optional<UnifiedPushBroker.Transport> renewUnifiedPushEndpoints(final UnifiedPushBroker.PushTargetMessenger pushTargetMessenger) {
        return this.unifiedPushBroker.renewUnifiedPushEndpoints(pushTargetMessenger);
    }

    public Optional<UnifiedPushBroker.Transport> renewUnifiedPushEndpoints() {
        return this.unifiedPushBroker.renewUnifiedPushEndpoints(null);
    }

    private void provisionAccount(final String address, final String password) {
        final Jid jid = Jid.ofEscaped(address);
        final Account account = new Account(jid, password);
        account.setOption(Account.OPTION_DISABLED, true);
        Log.d(Config.LOGTAG, jid.asBareJid().toEscapedString() + ": provisioning account");
        createAccount(account);
    }

    public void createAccountFromKey(final String alias, final OnAccountCreated callback) {
        new Thread(() -> {
            try {
                final X509Certificate[] chain = KeyChain.getCertificateChain(this, alias);
                final X509Certificate cert = chain != null && chain.length > 0 ? chain[0] : null;
                if (cert == null) {
                    callback.informUser(R.string.unable_to_parse_certificate);
                    return;
                }
                Pair<Jid, String> info = CryptoHelper.extractJidAndName(cert);
                if (info == null) {
                    callback.informUser(R.string.certificate_does_not_contain_jid);
                    return;
                }
                if (findAccountByJid(info.first) == null) {
                    final Account account = new Account(info.first, "");
                    account.setPrivateKeyAlias(alias);
                    account.setOption(Account.OPTION_DISABLED, true);
                    account.setOption(Account.OPTION_FIXED_USERNAME, true);
                    account.setDisplayName(info.second);
                    createAccount(account);
                    callback.onAccountCreated(account);
                    if (Config.X509_VERIFICATION) {
                        try {
                            getMemorizingTrustManager().getNonInteractive(account.getServer(), null, 0, null).checkClientTrusted(chain, "RSA");
                        } catch (CertificateException e) {
                            callback.informUser(R.string.certificate_chain_is_not_trusted);
                        }
                    }
                } else {
                    callback.informUser(R.string.account_already_exists);
                }
            } catch (Exception e) {
                callback.informUser(R.string.unable_to_parse_certificate);
            }
        }).start();
    }

    public void updateKeyInAccount(final Account account, final String alias) {
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": update key in account " + alias);
        try {
            X509Certificate[] chain = KeyChain.getCertificateChain(XmppConnectionService.this, alias);
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + " loaded certificate chain");
            Pair<Jid, String> info = CryptoHelper.extractJidAndName(chain[0]);
            if (info == null) {
                showErrorToastInUi(R.string.certificate_does_not_contain_jid);
                return;
            }
            if (account.getJid().asBareJid().equals(info.first)) {
                account.setPrivateKeyAlias(alias);
                account.setDisplayName(info.second);
                databaseBackend.updateAccount(account);
                if (Config.X509_VERIFICATION) {
                    try {
                        getMemorizingTrustManager().getNonInteractive().checkClientTrusted(chain, "RSA");
                    } catch (CertificateException e) {
                        showErrorToastInUi(R.string.certificate_chain_is_not_trusted);
                    }
                    account.getAxolotlService().regenerateKeys(true);
                }
            } else {
                showErrorToastInUi(R.string.jid_does_not_match_certificate);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean updateAccount(final Account account) {
        if (databaseBackend.updateAccount(account)) {
            Integer color = account.getColorToSave();
            if (color == null) {
                getPreferences().edit().remove("account_color:" + account.getUuid()).commit();
            } else {
                getPreferences().edit().putInt("account_color:" + account.getUuid(), color.intValue()).commit();
            }
            account.setShowErrorNotification(true);
            this.statusListener.onStatusChanged(account);
            databaseBackend.updateAccount(account);
            reconnectAccountInBackground(account);
            updateAccountUi();
            getNotificationService().updateErrorNotification();
            toggleForegroundService();
            syncEnabledAccountSetting();
            mChannelDiscoveryService.cleanCache();
            if (CallIntegration.hasSystemFeature(this)) {
                CallIntegrationConnectionService.togglePhoneAccountAsync(this, account);
            }
            return true;
        } else {
            return false;
        }
    }

    public void updateAccountPasswordOnServer(final Account account, final String newPassword, final OnAccountPasswordChanged callback) {
        final IqPacket iq = getIqGenerator().generateSetPassword(account, newPassword);
        sendIqPacket(account, iq, (a, packet) -> {
            if (packet.getType() == IqPacket.TYPE.RESULT) {
                a.setPassword(newPassword);
                a.setOption(Account.OPTION_MAGIC_CREATE, false);
                databaseBackend.updateAccount(a);
                callback.onPasswordChangeSucceeded();
            } else {
                callback.onPasswordChangeFailed();
            }
        });
    }

    public void deleteAccountFromServer(final Account account) {
        account.getXmppConnection().sendDeleteRequest();
        deleteAccount(account);
    }

    public void deleteAccount(final Account account) {
        getPreferences().edit().remove("onboarding_continued").commit();
        final boolean connected = account.getStatus() == Account.State.ONLINE;
        synchronized (this.conversations) {
            if (connected) {
                account.getAxolotlService().deleteOmemoIdentity();
            }
            for (final Conversation conversation : conversations) {
                if (conversation.getAccount() == account) {
                    if (conversation.getMode() == Conversation.MODE_MULTI) {
                        if (Compatibility.runsTwentySix()) {
                            try {
                                mNotificationService.cleanNotificationChannels(this, conversation.getUuid());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        if (connected) {
                            leaveMuc(conversation);
                        }
                    }
                    conversations.remove(conversation);
                    mNotificationService.clear(conversation);
                }
            }
            new Thread(() -> {
                for (final Contact contact : account.getRoster().getContacts()) {
                    contact.unregisterAsPhoneAccount(this);
                }
            }).start();
            if (account.getXmppConnection() != null) {
                new Thread(() -> disconnect(account, !connected)).start();
            }
            final Runnable runnable = () -> {
                if (!databaseBackend.deleteAccount(account)) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": unable to delete account");
                }
            };
            mDatabaseWriterExecutor.execute(runnable);
            this.accounts.remove(account);
            CallIntegrationConnectionService.unregisterPhoneAccount(this, account);
            this.mRosterSyncTaskManager.clear(account);
            updateAccountUi();
            mNotificationService.updateErrorNotification();
            syncEnabledAccountSetting();
            toggleForegroundService();
        }
    }

    public void setOnConversationListChangedListener(OnConversationUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.mOnConversationUpdates.add(listener)) {
                Log.w(Config.LOGTAG, listener.getClass().getName() + " is already registered as ConversationListChangedListener");
            }
            this.mNotificationService.setIsInForeground(this.mOnConversationUpdates.size() > 0);
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeOnConversationListChangedListener(OnConversationUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.mOnConversationUpdates.remove(listener);
            this.mNotificationService.setIsInForeground(this.mOnConversationUpdates.size() > 0);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public void setOnShowErrorToastListener(OnShowErrorToast listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.mOnShowErrorToasts.add(listener)) {
                Log.w(Config.LOGTAG, listener.getClass().getName() + " is already registered as OnShowErrorToastListener");
            }
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeOnShowErrorToastListener(OnShowErrorToast onShowErrorToast) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.mOnShowErrorToasts.remove(onShowErrorToast);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public void setOnAccountListChangedListener(OnAccountUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.mOnAccountUpdates.add(listener)) {
                Log.w(Config.LOGTAG, listener.getClass().getName() + " is already registered as OnAccountListChangedtListener");
            }
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeOnAccountListChangedListener(OnAccountUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.mOnAccountUpdates.remove(listener);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public void setOnCaptchaRequestedListener(OnCaptchaRequested listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.mOnCaptchaRequested.add(listener)) {
                Log.w(Config.LOGTAG, listener.getClass().getName() + " is already registered as OnCaptchaRequestListener");
            }
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeOnCaptchaRequestedListener(OnCaptchaRequested listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.mOnCaptchaRequested.remove(listener);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public void setOnRosterUpdateListener(final OnRosterUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.mOnRosterUpdates.add(listener)) {
                Log.w(Config.LOGTAG, listener.getClass().getName() + " is already registered as OnRosterUpdateListener");
            }
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeOnRosterUpdateListener(final OnRosterUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.mOnRosterUpdates.remove(listener);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public void setOnUpdateBlocklistListener(final OnUpdateBlocklist listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.mOnUpdateBlocklist.add(listener)) {
                Log.w(Config.LOGTAG, listener.getClass().getName() + " is already registered as OnUpdateBlocklistListener");
            }
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeOnUpdateBlocklistListener(final OnUpdateBlocklist listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.mOnUpdateBlocklist.remove(listener);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public void setOnKeyStatusUpdatedListener(final OnKeyStatusUpdated listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.mOnKeyStatusUpdated.add(listener)) {
                Log.w(Config.LOGTAG, listener.getClass().getName() + " is already registered as OnKeyStatusUpdateListener");
            }
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeOnNewKeysAvailableListener(final OnKeyStatusUpdated listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.mOnKeyStatusUpdated.remove(listener);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public void setOnRtpConnectionUpdateListener(final OnJingleRtpConnectionUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.onJingleRtpConnectionUpdate.add(listener)) {
                Log.w(Config.LOGTAG, listener.getClass().getName() + " is already registered as OnJingleRtpConnectionUpdate");
            }
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeRtpConnectionUpdateListener(final OnJingleRtpConnectionUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.onJingleRtpConnectionUpdate.remove(listener);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public void setOnMucRosterUpdateListener(OnMucRosterUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.mOnMucRosterUpdate.add(listener)) {
                Log.w(Config.LOGTAG, listener.getClass().getName() + " is already registered as OnMucRosterListener");
            }
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeOnMucRosterUpdateListener(final OnMucRosterUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.mOnMucRosterUpdate.remove(listener);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public boolean checkListeners() {
        return (this.mOnAccountUpdates.size() == 0
                && this.mOnConversationUpdates.size() == 0
                && this.mOnRosterUpdates.size() == 0
                && this.mOnCaptchaRequested.size() == 0
                && this.mOnMucRosterUpdate.size() == 0
                && this.mOnUpdateBlocklist.size() == 0
                && this.mOnShowErrorToasts.size() == 0
                && this.onJingleRtpConnectionUpdate.size() == 0
                && this.mOnKeyStatusUpdated.size() == 0);
    }

    private void switchToForeground() {
        toggleSoftDisabled(false);
        final boolean broadcastLastActivity = broadcastLastActivity();
        for (Conversation conversation : getConversations()) {
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                conversation.getMucOptions().resetChatState();
            } else {
                conversation.setIncomingChatState(Config.DEFAULT_CHAT_STATE);
            }
        }
        for (Account account : getAccounts()) {
            if (account.getStatus() == Account.State.ONLINE) {
                account.deactivateGracePeriod();
                final XmppConnection connection = account.getXmppConnection();
                if (connection != null) {
                    if (connection.getFeatures().csi()) {
                        connection.sendActive();
                    }
                    if (broadcastLastActivity) {
                        sendPresence(account, false); //send new presence but don't include idle because we are not
                    }
                }
            }
        }
        Log.d(Config.LOGTAG, "app switched into foreground");
    }

    private void switchToBackground() {
        final boolean broadcastLastActivity = broadcastLastActivity();
        if (broadcastLastActivity) {
            mLastActivity = System.currentTimeMillis();
            final SharedPreferences.Editor editor = getPreferences().edit();
            editor.putLong(SETTING_LAST_ACTIVITY_TS, mLastActivity);
            editor.apply();
        }
        for (Account account : getAccounts()) {
            if (account.getStatus() == Account.State.ONLINE) {
                XmppConnection connection = account.getXmppConnection();
                if (connection != null) {
                    if (broadcastLastActivity) {
                        sendPresence(account, true);
                    }
                    if (connection.getFeatures().csi()) {
                        connection.sendInactive();
                    }
                }
            }
        }
        this.mNotificationService.setIsInForeground(false);
        Log.d(Config.LOGTAG, "app switched into background");
    }

    private void connectMultiModeConversations(Account account) {
        List<Conversation> conversations = getConversations();
        for (Conversation conversation : conversations) {
            if (conversation.getMode() == Conversation.MODE_MULTI && conversation.getAccount() == account) {
                joinMuc(conversation);
            }
        }
    }

    public void mucSelfPingAndRejoin(final Conversation conversation) {
        final Account account = conversation.getAccount();
        synchronized (account.inProgressConferenceJoins) {
            if (account.inProgressConferenceJoins.contains(conversation)) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": canceling muc self ping because join is already under way");
                return;
            }
        }
        synchronized (account.inProgressConferencePings) {
            if (!account.inProgressConferencePings.add(conversation)) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": canceling muc self ping because ping is already under way");
                return;
            }
        }
        final Jid self = conversation.getMucOptions().getSelf().getFullJid();
        final IqPacket ping = new IqPacket(IqPacket.TYPE.GET);
        ping.setTo(self);
        ping.addChild("ping", Namespace.PING);
        sendIqPacket(conversation.getAccount(), ping, (a, response) -> {
            if (response.getType() == IqPacket.TYPE.ERROR) {
                Element error = response.findChild("error");
                if (error == null || error.hasChild("service-unavailable") || error.hasChild("feature-not-implemented") || error.hasChild("item-not-found")) {
                    Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": ping to " + self + " came back as ignorable error");
                } else {
                    Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": ping to " + self + " failed. attempting rejoin");
                    joinMuc(conversation);
                }
            } else if (response.getType() == IqPacket.TYPE.RESULT) {
                Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": ping to " + self + " came back fine");
            }
            synchronized (account.inProgressConferencePings) {
                account.inProgressConferencePings.remove(conversation);
            }
        });
    }
    public void joinMuc(Conversation conversation) {
        joinMuc(conversation, null, false);
    }

    public void joinMuc(Conversation conversation, boolean followedInvite) {
        joinMuc(conversation, null, followedInvite);
    }

    private void joinMuc(Conversation conversation, final OnConferenceJoined onConferenceJoined) {
        joinMuc(conversation, onConferenceJoined, false);
    }

    private void joinMuc(Conversation conversation, final OnConferenceJoined onConferenceJoined, final boolean followedInvite) {
        final Account account = conversation.getAccount();
        synchronized (account.pendingConferenceJoins) {
            account.pendingConferenceJoins.remove(conversation);
        }
        synchronized (account.pendingConferenceLeaves) {
            account.pendingConferenceLeaves.remove(conversation);
        }
        if (account.getStatus() == Account.State.ONLINE) {
            synchronized (account.inProgressConferenceJoins) {
                account.inProgressConferenceJoins.add(conversation);
            }
            if (Config.MUC_LEAVE_BEFORE_JOIN) {
                sendPresencePacket(account, mPresenceGenerator.leave(conversation.getMucOptions()));
            }
            conversation.resetMucOptions();
            if (onConferenceJoined != null) {
                conversation.getMucOptions().flagNoAutoPushConfiguration();
            }
            conversation.setHasMessagesLeftOnServer(false);
            fetchConferenceConfiguration(conversation, new OnConferenceConfigurationFetched() {

                private void join(Conversation conversation) {
                    Account account = conversation.getAccount();
                    final MucOptions mucOptions = conversation.getMucOptions();
                    if (mucOptions.nonanonymous() && !mucOptions.membersOnly() && !conversation.getBooleanAttribute("accept_non_anonymous", false)) {
                        synchronized (account.inProgressConferenceJoins) {
                            account.inProgressConferenceJoins.remove(conversation);
                        }
                        mucOptions.setError(MucOptions.Error.NON_ANONYMOUS);
                        updateConversationUi();
                        if (onConferenceJoined != null) {
                            onConferenceJoined.onConferenceJoined(conversation);
                        }
                        return;
                    }
                    final Jid joinJid = mucOptions.getSelf().getFullJid();
                    Log.d(Config.LOGTAG, account.getJid().asBareJid().toString() + ": joining conversation " + joinJid.toString());
                    PresencePacket packet = mPresenceGenerator.selfPresence(account, Presence.Status.ONLINE, mucOptions.nonanonymous() || onConferenceJoined != null, mucOptions.getSelf().getNick());
                    packet.setTo(joinJid);
                    Element x = packet.addChild("x", "http://jabber.org/protocol/muc");
                    if (conversation.getMucOptions().getPassword() != null) {
                        x.addChild("password").setContent(mucOptions.getPassword());
                    }

                    if (mucOptions.mamSupport()) {
                        // Use MAM instead of the limited muc history to get history
                        x.addChild("history").setAttribute("maxchars", "0");
                    } else {
                        // Fallback to muc history
                        x.addChild("history").setAttribute("since", PresenceGenerator.getTimestamp(conversation.getLastMessageTransmitted().getTimestamp()));
                    }
                    sendPresencePacket(account, packet);
                    if (onConferenceJoined != null) {
                        onConferenceJoined.onConferenceJoined(conversation);
                    }
                    if (!joinJid.equals(conversation.getJid())) {
                        conversation.setContactJid(joinJid);
                        databaseBackend.updateConversation(conversation);
                    }

                    maybeRegisterWithMuc(conversation, null);

                    if (mucOptions.mamSupport()) {
                        getMessageArchiveService().catchupMUC(conversation);
                    }
                    if (mucOptions.isPrivateAndNonAnonymous()) {
                        fetchConferenceMembers(conversation);

                        if (followedInvite) {
                            final Bookmark bookmark = conversation.getBookmark();
                            if (bookmark != null) {
                                if (!bookmark.autojoin()) {
                                    bookmark.setAutojoin(true);
                                    createBookmark(account, bookmark);
                                }
                            } else {
                                saveConversationAsBookmark(conversation, null);
                            }
                        }
                    }
                    synchronized (account.inProgressConferenceJoins) {
                        account.inProgressConferenceJoins.remove(conversation);
                        sendUnsentMessages(conversation);
                    }
                }

                @Override
                public void onConferenceConfigurationFetched(Conversation conversation) {
                    if (conversation.getStatus() == Conversation.STATUS_ARCHIVED) {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": conversation (" + conversation.getJid() + ") got archived before IQ result");
                        return;
                    }
                    join(conversation);
                }

                @Override
                public void onFetchFailed(final Conversation conversation, final String errorCondition) {
                    if (conversation.getStatus() == Conversation.STATUS_ARCHIVED) {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": conversation (" + conversation.getJid() + ") got archived before IQ result");
                        return;
                    }
                    if ("remote-server-not-found".equals(errorCondition)) {
                        synchronized (account.inProgressConferenceJoins) {
                            account.inProgressConferenceJoins.remove(conversation);
                        }
                        conversation.getMucOptions().setError(MucOptions.Error.SERVER_NOT_FOUND);
                        updateConversationUi();
                    } else {
                        join(conversation);
                        fetchConferenceConfiguration(conversation);
                    }
                }
            });
            updateConversationUi();
        } else {
            synchronized (account.pendingConferenceJoins) {
                account.pendingConferenceJoins.add(conversation);
            }
            conversation.resetMucOptions();
            conversation.setHasMessagesLeftOnServer(false);
            updateConversationUi();
        }
    }

    private void fetchConferenceMembers(final Conversation conversation) {
        final Account account = conversation.getAccount();
        final AxolotlService axolotlService = account.getAxolotlService();
        final String[] affiliations = {"member", "admin", "owner"};
        OnIqPacketReceived callback = new OnIqPacketReceived() {

            private int i = 0;
            private boolean success = true;

            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                final boolean omemoEnabled = conversation.getNextEncryption() == Message.ENCRYPTION_AXOLOTL;
                Element query = packet.query("http://jabber.org/protocol/muc#admin");
                if (packet.getType() == IqPacket.TYPE.RESULT && query != null) {
                    for (Element child : query.getChildren()) {
                        if ("item".equals(child.getName())) {
                            MucOptions.User user = AbstractParser.parseItem(conversation, child);
                            if (!user.realJidMatchesAccount()) {
                                boolean isNew = conversation.getMucOptions().updateUser(user);
                                Contact contact = user.getContact();
                                if (omemoEnabled
                                        && isNew
                                        && user.getRealJid() != null
                                        && (contact == null || !contact.mutualPresenceSubscription())
                                        && axolotlService.hasEmptyDeviceList(user.getRealJid())) {
                                    axolotlService.fetchDeviceIds(user.getRealJid());
                                }
                            }
                        }
                    }
                } else {
                    success = false;
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": could not request affiliation " + affiliations[i] + " in " + conversation.getJid().asBareJid());
                }
                ++i;
                if (i >= affiliations.length) {
                    List<Jid> members = conversation.getMucOptions().getMembers(true);
                    if (success) {
                        List<Jid> cryptoTargets = conversation.getAcceptedCryptoTargets();
                        boolean changed = false;
                        for (ListIterator<Jid> iterator = cryptoTargets.listIterator(); iterator.hasNext(); ) {
                            Jid jid = iterator.next();
                            if (!members.contains(jid) && !members.contains(jid.getDomain())) {
                                try {
                                    iterator.remove();
                                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": removed " + jid + " from crypto targets of " + conversation.getName());
                                    changed = true;
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        if (changed) {
                            conversation.setAcceptedCryptoTargets(cryptoTargets);
                            updateConversation(conversation);
                        }
                    }
                    getAvatarService().clear(conversation);
                    updateMucRosterUi();
                    updateConversationUi();
                }
            }
        };
        for (String affiliation : affiliations) {
            sendIqPacket(account, mIqGenerator.queryAffiliation(conversation, affiliation), callback);
        }
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": fetching members for " + conversation.getName());
    }

    public void providePasswordForMuc(Conversation conversation, String password) {
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            conversation.getMucOptions().setPassword(password);
            if (conversation.getBookmark() != null) {
                final Bookmark bookmark = conversation.getBookmark();
                if (synchronizeWithBookmarks()) {
                    bookmark.setAutojoin(true);
                }
                createBookmark(conversation.getAccount(), bookmark);
            }
            updateConversation(conversation);
            joinMuc(conversation);
        }
    }

    public void deleteAvatar(final Account account) {
        final AtomicBoolean executed = new AtomicBoolean(false);
        final Runnable onDeleted =
                () -> {
                    if (executed.compareAndSet(false, true)) {
                        account.setAvatar(null);
                        databaseBackend.updateAccount(account);
                        getAvatarService().clear(account);
                        updateAccountUi();
                    }
                };
        deleteVcardAvatar(account, onDeleted);
        deletePepNode(account, Namespace.AVATAR_DATA);
        deletePepNode(account, Namespace.AVATAR_METADATA, onDeleted);
    }

    public void deletePepNode(final Account account, final String node) {
        deletePepNode(account, node, null);
    }

    private void deletePepNode(final Account account, final String node, final Runnable runnable) {
        final IqPacket request = mIqGenerator.deleteNode(node);
        sendIqPacket(account, request, (a, packet) -> {
            if (packet.getType() == IqPacket.TYPE.RESULT) {
                Log.d(Config.LOGTAG,a.getJid().asBareJid()+": successfully deleted pep node "+node);
                if (runnable != null) {
                    runnable.run();
                }
            } else {
                Log.d(Config.LOGTAG,a.getJid().asBareJid()+": failed to delete "+ packet);
            }
        });
    }

    private void deleteVcardAvatar(final Account account, @NonNull final Runnable runnable) {
        final IqPacket retrieveVcard = mIqGenerator.retrieveVcardAvatar(account.getJid().asBareJid());
        sendIqPacket(account, retrieveVcard, (a, response) -> {
            if (response.getType() != IqPacket.TYPE.RESULT) {
                Log.d(Config.LOGTAG,a.getJid().asBareJid()+": no vCard set. nothing to do");
                return;
            }
            final Element vcard = response.findChild("vCard", "vcard-temp");
            if (vcard == null) {
                Log.d(Config.LOGTAG,a.getJid().asBareJid()+": no vCard set. nothing to do");
                return;
            }
            Element photo = vcard.findChild("PHOTO");
            if (photo == null) {
                photo = vcard.addChild("PHOTO");
            }
            photo.clearChildren();
            IqPacket publication = new IqPacket(IqPacket.TYPE.SET);
            publication.setTo(a.getJid().asBareJid());
            publication.addChild(vcard);
            sendIqPacket(account, publication, (a1, publicationResponse) -> {
                if (publicationResponse.getType() == IqPacket.TYPE.RESULT) {
                    Log.d(Config.LOGTAG,a1.getJid().asBareJid()+": successfully deleted vcard avatar");
                    runnable.run();
                } else {
                    Log.d(Config.LOGTAG, "failed to publish vcard " + publicationResponse.getErrorCondition());
                }
            });
        });
    }


    private boolean hasEnabledAccounts() {
        if (this.accounts == null) {
            return false; // set to false if accounts could not be fetched - used for notifications
        }
        for (final Account account : this.accounts) {
            if (account.isConnectionEnabled()) {
                return true;
            }
        }
        return false;
    }

    public void getAttachments(final Conversation conversation, int limit, final OnMediaLoaded onMediaLoaded) {
        getAttachments(conversation.getAccount(), conversation.getJid().asBareJid(), limit, onMediaLoaded);
    }

    public void getAttachments(final Account account, final Jid jid, final int limit, final OnMediaLoaded onMediaLoaded) {
        getAttachments(account.getUuid(), jid.asBareJid(), limit, onMediaLoaded);
    }

    public void getAttachments(final String account, final Jid jid, final int limit, final OnMediaLoaded onMediaLoaded) {
        new Thread(() -> onMediaLoaded.onMediaLoaded(fileBackend.convertToAttachments(databaseBackend.getRelativeFilePaths(account, jid, limit)))).start();
    }

    public void persistSelfNick(MucOptions.User self) {
        final Conversation conversation = self.getConversation();
        final boolean tookProposedNickFromBookmark = conversation.getMucOptions().isTookProposedNickFromBookmark();
        Jid full = self.getFullJid();
        if (!full.equals(conversation.getJid())) {
            Log.d(Config.LOGTAG, "nick changed. updating");
            conversation.setContactJid(full);
            databaseBackend.updateConversation(conversation);
        }
        final String nick = self.getNick();
        final Bookmark bookmark = conversation.getBookmark();
        final String bookmarkedNick = bookmark == null ? null : bookmark.getNick();
        if (bookmark != null && (tookProposedNickFromBookmark || Strings.isNullOrEmpty(bookmarkedNick)) && !full.getResource().equals(bookmarkedNick)) {
            final Account account = conversation.getAccount();
            final String defaultNick = MucOptions.defaultNick(account);
            if (Strings.isNullOrEmpty(bookmarkedNick) && full.getResource().equals(defaultNick)) {
                return;
            }
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": persist nick '" + nick + "' into bookmark for " + conversation.getJid().asBareJid());
            bookmark.setNick(nick);
            createBookmark(bookmark.getAccount(), bookmark);
        }
    }

    public void presenceToMuc(final Conversation conversation) {
        final MucOptions options = conversation.getMucOptions();
        if (options.online()) {
            Account account = conversation.getAccount();
            final Jid joinJid = options.getSelf().getFullJid();
            final PresencePacket packet = mPresenceGenerator.selfPresence(account, Presence.Status.ONLINE, options.nonanonymous(), options.getSelf().getNick());
            packet.setTo(joinJid);
            sendPresencePacket(account, packet);
        }
    }

    public boolean renameInMuc(final Conversation conversation, final String nick, final UiCallback<Conversation> callback) {
        final MucOptions options = conversation.getMucOptions();
        final Jid joinJid = options.createJoinJid(nick);
        if (joinJid == null) {
            return false;
        }
        if (options.online()) {
            maybeRegisterWithMuc(conversation, nick);

            Account account = conversation.getAccount();
            options.setOnRenameListener(new OnRenameListener() {

                @Override
                public void onSuccess() {
                    final PresencePacket packet = mPresenceGenerator.selfPresence(account, Presence.Status.ONLINE, options.nonanonymous(), nick);
                    packet.setTo(joinJid);
                    sendPresencePacket(account, packet);
                    callback.success(conversation);
                }

                @Override
                public void onFailure() {
                    callback.error(R.string.nick_in_use, conversation);
                }
            });
            final PresencePacket packet = mPresenceGenerator.selfPresence(account, Presence.Status.ONLINE, options.nonanonymous(), nick);
            packet.setTo(joinJid);
            sendPresencePacket(account, packet);
        } else {
            conversation.setContactJid(joinJid);
            databaseBackend.updateConversation(conversation);
            if (conversation.getAccount().getStatus() == Account.State.ONLINE) {
                Bookmark bookmark = conversation.getBookmark();
                if (bookmark != null) {
                    bookmark.setNick(nick);
                    createBookmark(bookmark.getAccount(), bookmark);
                }
                joinMuc(conversation);
            }
        }
        return true;
    }

    public void leaveMuc(Conversation conversation) {
        leaveMuc(conversation, false);
    }

    private void leaveMuc(Conversation conversation, boolean now) {
        if (Compatibility.runsTwentySix()) {
            try {
                mNotificationService.cleanNotificationChannels(this, conversation.getUuid());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        final Account account = conversation.getAccount();
        synchronized (account.pendingConferenceJoins) {
            account.pendingConferenceJoins.remove(conversation);
        }
        synchronized (account.pendingConferenceLeaves) {
            account.pendingConferenceLeaves.remove(conversation);
        }
        if (account.getStatus() == Account.State.ONLINE || now) {
            sendPresencePacket(conversation.getAccount(), mPresenceGenerator.leave(conversation.getMucOptions()));
            conversation.getMucOptions().setOffline();
            Bookmark bookmark = conversation.getBookmark();
            if (bookmark != null) {
                bookmark.setConversation(null);
            }
            Log.d(Config.LOGTAG, conversation.getAccount().getJid().asBareJid() + ": leaving muc " + conversation.getJid());
        } else {
            synchronized (account.pendingConferenceLeaves) {
                account.pendingConferenceLeaves.add(conversation);
            }
        }
    }

    public String findConferenceServer(final Account account) {
        String server;
        if (account.getXmppConnection() != null) {
            server = account.getXmppConnection().getMucServer();
            if (server != null) {
                return server;
            }
        }
        for (Account other : getAccounts()) {
            if (other != account && other.getXmppConnection() != null) {
                server = other.getXmppConnection().getMucServer();
                if (server != null) {
                    return server;
                }
            }
        }
        return null;
    }

    public void createPublicChannel(final Account account, final String name, final Jid address, final UiCallback<Conversation> callback) {
        joinMuc(findOrCreateConversation(account, address, true, false, true), conversation -> {
            final Bundle configuration = IqGenerator.defaultChannelConfiguration();
            if (!TextUtils.isEmpty(name)) {
                configuration.putString("muc#roomconfig_roomname", name);
            }
            pushConferenceConfiguration(conversation, configuration, new OnConfigurationPushed() {
                @Override
                public void onPushSucceeded() {
                    saveConversationAsBookmark(conversation, name);
                    callback.success(conversation);
                }

                @Override
                public void onPushFailed() {
                    if (conversation.getMucOptions().getSelf().getAffiliation().ranks(MucOptions.Affiliation.OWNER)) {
                        callback.error(R.string.unable_to_set_channel_configuration, conversation);
                    } else {
                        callback.error(R.string.joined_an_existing_channel, conversation);
                    }
                }
            });
        });
    }

    public boolean createAdhocConference(final Account account,
                                         final String name,
                                         final Iterable<Jid> jids,
                                         final UiCallback<Conversation> callback) {
        Log.d(Config.LOGTAG, account.getJid().asBareJid().toString() + ": creating adhoc conference with " + jids.toString());
        if (account.getStatus() == Account.State.ONLINE) {
            try {
                String server = findConferenceServer(account);
                if (server == null) {
                    if (callback != null) {
                        callback.error(R.string.no_conference_server_found, null);
                    }
                    return false;
                }
                final Jid jid = Jid.of(CryptoHelper.pronounceable(), server, null);
                final Conversation conversation = findOrCreateConversation(account, jid, true, false, true);
                joinMuc(conversation, new OnConferenceJoined() {
                    @Override
                    public void onConferenceJoined(final Conversation conversation) {
                        final Bundle configuration = IqGenerator.defaultGroupChatConfiguration();
                        if (!TextUtils.isEmpty(name)) {
                            configuration.putString("muc#roomconfig_roomname", name);
                        }
                        pushConferenceConfiguration(conversation, configuration, new OnConfigurationPushed() {
                            @Override
                            public void onPushSucceeded() {
                                for (Jid invite : jids) {
                                    invite(conversation, invite);
                                }
                                for (String resource : account.getSelfContact().getPresences().toResourceArray()) {
                                    if (resource == null || "".equals(resource)) continue;
                                    Jid other = account.getJid().withResource(resource);
                                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": sending direct invite to " + other);
                                    directInvite(conversation, other);
                                }
                                saveConversationAsBookmark(conversation, name);
                                if (callback != null) {
                                    callback.success(conversation);
                                }
                            }

                            @Override
                            public void onPushFailed() {
                                archiveConversation(conversation);
                                if (callback != null) {
                                    callback.error(R.string.conference_creation_failed, conversation);
                                }
                            }
                        });
                    }
                });
                return true;
            } catch (IllegalArgumentException e) {
                if (callback != null) {
                    callback.error(R.string.conference_creation_failed, null);
                }
                return false;
            }
        } else {
            if (callback != null) {
                callback.error(R.string.not_connected_try_again, null);
            }
            return false;
        }
    }

    public void checkIfMuc(final Account account, final Jid jid, Consumer<Boolean> cb) {
        if (jid.isDomainJid()) {
            // Spec basically says MUC needs to have a node
            // And also specifies that MUC and MUC service should have the same identity...
            cb.accept(false);
            return;
        }

        IqPacket request = mIqGenerator.queryDiscoInfo(jid.asBareJid());
        sendIqPacket(account, request, (acct, reply) -> {
            ServiceDiscoveryResult result = new ServiceDiscoveryResult(reply);
            cb.accept(
                    result.getFeatures().contains("http://jabber.org/protocol/muc") &&
                            result.hasIdentity("conference", null)
            );
        });
    }

    public void fetchConferenceConfiguration(final Conversation conversation) {
        fetchConferenceConfiguration(conversation, null);
    }

    public void fetchConferenceConfiguration(final Conversation conversation, final OnConferenceConfigurationFetched callback) {
        IqPacket request = mIqGenerator.queryDiscoInfo(conversation.getJid().asBareJid());
        sendIqPacket(conversation.getAccount(), request, new OnIqPacketReceived() {
            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                if (packet.getType() == IqPacket.TYPE.RESULT) {
                    final MucOptions mucOptions = conversation.getMucOptions();
                    final Bookmark bookmark = conversation.getBookmark();
                    final boolean sameBefore = StringUtils.equals(bookmark == null ? null : bookmark.getBookmarkName(), mucOptions.getName());

                    if (mucOptions.updateConfiguration(new ServiceDiscoveryResult(packet))) {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": muc configuration changed for " + conversation.getJid().asBareJid());
                        updateConversation(conversation);
                    }

                    if (bookmark != null && (sameBefore || bookmark.getBookmarkName() == null)) {
                        if (bookmark.setBookmarkName(StringUtils.nullOnEmpty(mucOptions.getName()))) {
                            createBookmark(account, bookmark);
                        }
                    }

                    if (callback != null) {
                        callback.onConferenceConfigurationFetched(conversation);
                    }

                    updateConversationUi();
                } else if (packet.getType() == IqPacket.TYPE.TIMEOUT) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received timeout waiting for conference configuration fetch");
                } else {
                    if (callback != null) {
                        callback.onFetchFailed(conversation, packet.getErrorCondition());
                    }
                }
            }
        });
    }

    public void pushNodeConfiguration(Account account, final String node, final Bundle options, final OnConfigurationPushed callback) {
        pushNodeConfiguration(account, account.getJid().asBareJid(), node, options, callback);
    }

    public void pushNodeConfiguration(Account account, final Jid jid, final String node, final Bundle options, final OnConfigurationPushed callback) {
        Log.d(Config.LOGTAG, "pushing node configuration");
        sendIqPacket(account, mIqGenerator.requestPubsubConfiguration(jid, node), new OnIqPacketReceived() {
            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                if (packet.getType() == IqPacket.TYPE.RESULT) {
                    Element pubsub = packet.findChild("pubsub", "http://jabber.org/protocol/pubsub#owner");
                    Element configuration = pubsub == null ? null : pubsub.findChild("configure");
                    Element x = configuration == null ? null : configuration.findChild("x", Namespace.DATA);
                    if (x != null) {
                        final Data data = Data.parse(x);
                        data.submit(options);
                        sendIqPacket(account, mIqGenerator.publishPubsubConfiguration(jid, node, data), new OnIqPacketReceived() {
                            @Override
                            public void onIqPacketReceived(Account account, IqPacket packet) {
                                if (packet.getType() == IqPacket.TYPE.RESULT && callback != null) {
                                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": successfully changed node configuration for node " + node);
                                    callback.onPushSucceeded();
                                } else if (packet.getType() == IqPacket.TYPE.ERROR && callback != null) {
                                    callback.onPushFailed();
                                }
                            }
                        });
                    } else if (callback != null) {
                        callback.onPushFailed();
                    }
                } else if (packet.getType() == IqPacket.TYPE.ERROR && callback != null) {
                    callback.onPushFailed();
                }
            }
        });
    }

    public void pushConferenceConfiguration(final Conversation conversation, final Bundle options, final OnConfigurationPushed callback) {
        if (options.getString("muc#roomconfig_whois", "moderators").equals("anyone")) {
            conversation.setAttribute("accept_non_anonymous", true);
            updateConversation(conversation);
        }
        if (options.containsKey("muc#roomconfig_moderatedroom")) {
            final boolean moderated = "1".equals(options.getString("muc#roomconfig_moderatedroom"));
            options.putString("members_by_default", moderated ? "0" : "1");
        }
        if (options.containsKey("muc#roomconfig_allowpm")) {
            // ejabberd :-/
            final boolean allow = "anyone".equals(options.getString("muc#roomconfig_allowpm"));
            options.putString("allow_private_messages", allow ? "1" : "0");
            options.putString("allow_private_messages_from_visitors", allow ? "anyone" : "nobody");
        }
        final IqPacket request = new IqPacket(IqPacket.TYPE.GET);
        request.setTo(conversation.getJid().asBareJid());
        request.query("http://jabber.org/protocol/muc#owner");
        sendIqPacket(conversation.getAccount(), request, new OnIqPacketReceived() {
            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                if (packet.getType() == IqPacket.TYPE.RESULT) {
                    final Data data = Data.parse(packet.query().findChild("x", Namespace.DATA));
                    data.submit(options);
                    final IqPacket set = new IqPacket(IqPacket.TYPE.SET);
                    set.setTo(conversation.getJid().asBareJid());
                    set.query("http://jabber.org/protocol/muc#owner").addChild(data);
                    sendIqPacket(account, set, new OnIqPacketReceived() {
                        @Override
                        public void onIqPacketReceived(Account account, IqPacket packet) {
                            if (callback != null) {
                                if (packet.getType() == IqPacket.TYPE.RESULT) {
                                    callback.onPushSucceeded();
                                } else {
                                    Log.d(Config.LOGTAG,"failed: "+packet.toString());
                                    callback.onPushFailed();
                                }
                            }
                        }
                    });
                } else {
                    if (callback != null) {
                        callback.onPushFailed();
                    }
                }
            }
        });
    }

    public void pushSubjectToConference(final Conversation conference, final String subject) {
        MessagePacket packet = this.getMessageGenerator().conferenceSubject(conference, StringUtils.nullOnEmpty(subject));
        this.sendMessagePacket(conference.getAccount(), packet);
    }

    public void changeAffiliationInConference(final Conversation conference, Jid user, final MucOptions.Affiliation affiliation, final OnAffiliationChanged callback) {
        final Jid jid = user.asBareJid();
        final IqPacket request = this.mIqGenerator.changeAffiliation(conference, jid, affiliation.toString());
        sendIqPacket(conference.getAccount(), request, (account, response) -> {
            if (response.getType() == IqPacket.TYPE.RESULT) {
                conference.getMucOptions().changeAffiliation(jid, affiliation);
                getAvatarService().clear(conference);
                if (callback != null) {
                    callback.onAffiliationChangedSuccessful(jid);
                } else {
                    Log.d(Config.LOGTAG, "changed affiliation of " + user + " to " + affiliation);
                }
            } else if (callback != null) {
                callback.onAffiliationChangeFailed(jid, R.string.could_not_change_affiliation);
            } else {
                Log.d(Config.LOGTAG, "unable to change affiliation");
            }
        });
    }

    public void changeRoleInConference(final Conversation conference, final String nick, MucOptions.Role role) {
        IqPacket request = this.mIqGenerator.changeRole(conference, nick, role.toString());
        sendIqPacket(conference.getAccount(), request, (account, packet) -> {
            if (packet.getType() != IqPacket.TYPE.RESULT) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + " unable to change role of " + nick);
            }
        });
    }
    public void moderateMessage(final Account account, final Message m, final String reason) {
        IqPacket request = this.mIqGenerator.moderateMessage(account, m, reason);
        sendIqPacket(account, request, (a, packet) -> {
            if (packet.getType() != IqPacket.TYPE.RESULT) {
                showErrorToastInUi(R.string.unable_to_moderate);
                Log.d(Config.LOGTAG, a.getJid().asBareJid() + " unable to moderate: " + packet);
            }
        });
    }


    public void destroyRoom(final Conversation conversation, final OnRoomDestroy callback) {
        try {
            IqPacket request = new IqPacket(IqPacket.TYPE.SET);
            request.setTo(conversation.getJid().asBareJid());
            request.query("http://jabber.org/protocol/muc#owner").addChild("destroy");
            sendIqPacket(conversation.getAccount(), request, new OnIqPacketReceived() {
                @Override
                public void onIqPacketReceived(Account account, IqPacket packet) {
                    if (packet.getType() == IqPacket.TYPE.RESULT) {
                        if (callback != null) {
                            callback.onRoomDestroySucceeded();
                        }
                    } else if (packet.getType() == IqPacket.TYPE.ERROR) {
                        if (callback != null) {
                            callback.onRoomDestroyFailed();
                        }
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void disconnect(final Account account, boolean force) {
        final XmppConnection connection = account.getXmppConnection();
        if (connection == null) {
            return;
        }
        if (!force) {
            final List<Conversation> conversations = getConversations();
            for (Conversation conversation : conversations) {
                if (conversation.getAccount() == account) {
                    if (conversation.getMode() == Conversation.MODE_MULTI) {
                        leaveMuc(conversation, true);
                    } else {
                        if (conversation.endOtrIfNeeded()) {
                            Log.d(Config.LOGTAG, account.getJid().asBareJid()
                                    + ": ended otr session with "
                                    + conversation.getJid());
                        }
                    }
                }
            }
            sendOfflinePresence(account);
        }
        connection.disconnect(force);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void updateMessage(Message message) {
        updateMessage(message, true);
    }

    public void updateMessage(Message message, boolean includeBody) {
        databaseBackend.updateMessage(message, includeBody);
        updateConversationUi();
    }

    public void createMessageAsync(final Message message) {
        mDatabaseWriterExecutor.execute(() -> databaseBackend.createMessage(message));
    }

    public void updateMessage(Message message, String uuid) {
        if (!databaseBackend.updateMessage(message, uuid)) {
            Log.e(Config.LOGTAG, "error updated message in DB after edit");
        }
        updateConversationUi();
    }

    protected void syncDirtyContacts(Account account) {
        for (Contact contact : account.getRoster().getContacts()) {
            if (contact.getOption(Contact.Options.DIRTY_PUSH)) {
                pushContactToServer(contact);
            }
            if (contact.getOption(Contact.Options.DIRTY_DELETE)) {
                deleteContactOnServer(contact);
            }
        }
    }

    protected void unregisterPhoneAccounts(final Account account) {
        for (final Contact contact : account.getRoster().getContacts()) {
            if (!contact.showInRoster()) {
                contact.unregisterAsPhoneAccount(this);
            }
        }
    }


    public void createContact(final Contact contact, final boolean autoGrant) {
        createContact(contact, autoGrant, null);
    }

    public void createContact(final Contact contact, final boolean autoGrant, final String preAuth) {
        if (autoGrant) {
            contact.setOption(Contact.Options.PREEMPTIVE_GRANT);
            contact.setOption(Contact.Options.ASKING);
        }
        pushContactToServer(contact, preAuth);
    }
    public void onOtrSessionEstablished(Conversation conversation) {
        final Account account = conversation.getAccount();
        final Session otrSession = conversation.getOtrSession();
        Log.d(Config.LOGTAG,
                account.getJid().asBareJid() + " otr session established with "
                        + conversation.getJid() + "/"
                        + otrSession.getSessionID().getUserID());
        conversation.findUnsentMessagesWithEncryption(Message.ENCRYPTION_OTR, new Conversation.OnMessageFound() {

            @Override
            public void onMessageFound(Message message) {
                SessionID id = otrSession.getSessionID();
                try {
                    message.setCounterpart(Jid.of(id.getAccountID() + "/" + id.getUserID()));
                } catch (IllegalArgumentException e) {
                    return;
                }
                if (message.needsUploading()) {
                    mJingleConnectionManager.startJingleFileTransfer(message);
                } else {
                    MessagePacket outPacket = mMessageGenerator.generateOtrChat(message);
                    if (outPacket != null) {
                        mMessageGenerator.addDelay(outPacket, message.getTimeSent());
                        message.setStatus(Message.STATUS_SEND);
                        databaseBackend.updateMessage(message, false);
                        sendMessagePacket(account, outPacket);
                    }
                }
                updateConversationUi();
            }
        });
    }

    public boolean renewSymmetricKey(Conversation conversation) {
        Account account = conversation.getAccount();
        byte[] symmetricKey = new byte[32];
        this.mRandom.nextBytes(symmetricKey);
        Session otrSession = conversation.getOtrSession();
        if (otrSession != null) {
            MessagePacket packet = new MessagePacket();
            packet.setType(MessagePacket.TYPE_CHAT);
            packet.setFrom(account.getJid());
            MessageGenerator.addMessageHints(packet);
            packet.setAttribute("to", otrSession.getSessionID().getAccountID() + "/"
                    + otrSession.getSessionID().getUserID());
            try {
                packet.setBody(otrSession
                        .transformSending(CryptoHelper.FILETRANSFER
                                + CryptoHelper.bytesToHex(symmetricKey))[0]);
                sendMessagePacket(account, packet);
                conversation.setSymmetricKey(symmetricKey);
                return true;
            } catch (OtrException e) {
                return false;
            }
        }
        return false;
    }


    public void pushContactToServer(final Contact contact) {
        pushContactToServer(contact, null);
    }

    private void pushContactToServer(final Contact contact, final String preAuth) {
        contact.resetOption(Contact.Options.DIRTY_DELETE);
        contact.setOption(Contact.Options.DIRTY_PUSH);
        final Account account = contact.getAccount();
        if (account.getStatus() == Account.State.ONLINE) {
            final boolean ask = contact.getOption(Contact.Options.ASKING);
            final boolean sendUpdates = contact
                    .getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)
                    && contact.getOption(Contact.Options.PREEMPTIVE_GRANT);
            final IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
            iq.query(Namespace.ROSTER).addChild(contact.asElement());
            account.getXmppConnection().sendIqPacket(iq, mDefaultIqHandler);
            if (sendUpdates) {
                sendPresencePacket(account, mPresenceGenerator.sendPresenceUpdatesTo(contact));
            }
            if (ask) {
                sendPresencePacket(account, mPresenceGenerator.requestPresenceUpdatesFrom(contact, preAuth));
            }
        } else {
            syncRoster(contact.getAccount());
        }
    }

    public void publishMucAvatar(final Conversation conversation, final Uri image, final OnAvatarPublication callback) {
        new Thread(() -> {
            final Bitmap.CompressFormat format = Config.AVATAR_FORMAT;
            final int size = Config.AVATAR_SIZE;
            final Avatar avatar = getFileBackend().getPepAvatar(image, size, format);
            if (avatar != null) {
                if (!getFileBackend().save(avatar)) {
                    Log.d(Config.LOGTAG, "unable to save avatar");
                    callback.onAvatarPublicationFailed(R.string.error_saving_avatar);
                    return;
                }
                avatar.owner = conversation.getJid().asBareJid();
                publishMucAvatar(conversation, avatar, callback);
            } else {
                callback.onAvatarPublicationFailed(R.string.error_publish_avatar_converting);
            }
        }).start();
    }

    public void publishAvatar(final Account account, final Uri image, final OnAvatarPublication callback) {
        new Thread(() -> {
            final Bitmap.CompressFormat format = Config.AVATAR_FORMAT;
            final int size = Config.AVATAR_SIZE;
            final Avatar avatar = getFileBackend().getPepAvatar(image, size, format);
            if (avatar != null) {
                if (!getFileBackend().save(avatar)) {
                    Log.d(Config.LOGTAG, "unable to save vcard avatar");
                    callback.onAvatarPublicationFailed(R.string.error_saving_avatar);
                    return;
                }
                publishAvatar(account, avatar, callback);
            } else {
                callback.onAvatarPublicationFailed(R.string.error_publish_avatar_converting);
            }
        }).start();
    }

    private void publishMucAvatar(Conversation conversation, Avatar avatar, OnAvatarPublication callback) {
        final IqPacket retrieve = mIqGenerator.retrieveVcardAvatar(avatar);
        sendIqPacket(conversation.getAccount(), retrieve, (account, response) -> {
            boolean itemNotFound = response.getType() == IqPacket.TYPE.ERROR && response.hasChild("error") && response.findChild("error").hasChild("item-not-found");
            if (response.getType() == IqPacket.TYPE.RESULT || itemNotFound) {
                Element vcard = response.findChild("vCard", "vcard-temp");
                if (vcard == null) {
                    vcard = new Element("vCard", "vcard-temp");
                }
                Element photo = vcard.findChild("PHOTO");
                if (photo == null) {
                    photo = vcard.addChild("PHOTO");
                }
                photo.clearChildren();
                photo.addChild("TYPE").setContent(avatar.type);
                photo.addChild("BINVAL").setContent(avatar.image);
                IqPacket publication = new IqPacket(IqPacket.TYPE.SET);
                publication.setTo(conversation.getJid().asBareJid());
                publication.addChild(vcard);
                sendIqPacket(account, publication, (a1, publicationResponse) -> {
                    if (publicationResponse.getType() == IqPacket.TYPE.RESULT) {
                        callback.onAvatarPublicationSucceeded();
                    } else {
                        Log.d(Config.LOGTAG, "failed to publish vcard " + publicationResponse.getErrorCondition());
                        callback.onAvatarPublicationFailed(R.string.error_publish_avatar_server_reject);
                    }
                });
            } else {
                Log.d(Config.LOGTAG, "failed to request vcard " + response);
                callback.onAvatarPublicationFailed(R.string.error_publish_avatar_no_server_support);
            }
        });
    }

    public void publishAvatar(Account account, final Avatar avatar, final OnAvatarPublication callback) {
        final Bundle options;
        if (account.getXmppConnection().getFeatures().pepPublishOptions()) {
            options = PublishOptions.openAccess();
        } else {
            options = null;
        }
        publishAvatar(account, avatar, options, true, callback);
    }

    public void publishAvatar(Account account, final Avatar avatar, final Bundle options, final boolean retry, final OnAvatarPublication callback) {
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": publishing avatar. options=" + options);
        IqPacket packet = this.mIqGenerator.publishAvatar(avatar, options);
        this.sendIqPacket(account, packet, new OnIqPacketReceived() {

            @Override
            public void onIqPacketReceived(Account account, IqPacket result) {
                if (result.getType() == IqPacket.TYPE.RESULT) {
                    publishAvatarMetadata(account, avatar, options, true, callback);
                } else if (retry && PublishOptions.preconditionNotMet(result)) {
                    pushNodeConfiguration(account, Namespace.AVATAR_DATA, options, new OnConfigurationPushed() {
                        @Override
                        public void onPushSucceeded() {
                            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": changed node configuration for avatar node");
                            publishAvatar(account, avatar, options, false, callback);
                        }

                        @Override
                        public void onPushFailed() {
                            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": unable to change node configuration for avatar node");
                            publishAvatar(account, avatar, null, false, callback);
                        }
                    });
                } else {
                    Element error = result.findChild("error");
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": server rejected avatar " + (avatar.size / 1024) + "KiB " + (error != null ? error.toString() : ""));
                    if (callback != null) {
                        callback.onAvatarPublicationFailed(R.string.error_publish_avatar_server_reject);
                    }
                }
            }
        });
    }

    public void publishAvatarMetadata(Account account, final Avatar avatar, final Bundle options, final boolean retry, final OnAvatarPublication callback) {
        final IqPacket packet = XmppConnectionService.this.mIqGenerator.publishAvatarMetadata(avatar, options);
        sendIqPacket(account, packet, new OnIqPacketReceived() {
            @Override
            public void onIqPacketReceived(Account account, IqPacket result) {
                if (result.getType() == IqPacket.TYPE.RESULT) {
                    if (account.setAvatar(avatar.getFilename())) {
                        getAvatarService().clear(account);
                        databaseBackend.updateAccount(account);
                        notifyAccountAvatarHasChanged(account);
                    }
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": published avatar " + (avatar.size / 1024) + "KiB");
                    if (callback != null) {
                        callback.onAvatarPublicationSucceeded();
                    }
                } else if (retry && PublishOptions.preconditionNotMet(result)) {
                    pushNodeConfiguration(account, Namespace.AVATAR_METADATA, options, new OnConfigurationPushed() {
                        @Override
                        public void onPushSucceeded() {
                            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": changed node configuration for avatar meta data node");
                            publishAvatarMetadata(account, avatar, options, false, callback);
                        }

                        @Override
                        public void onPushFailed() {
                            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": unable to change node configuration for avatar meta data node");
                            publishAvatarMetadata(account, avatar, null, false, callback);
                        }
                    });
                } else {
                    if (callback != null) {
                        callback.onAvatarPublicationFailed(R.string.error_publish_avatar_server_reject);
                    }
                }
            }
        });
    }

    public void republishAvatarIfNeeded(Account account) {
        if (account.getAxolotlService().isPepBroken()) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": skipping republication of avatar because pep is broken");
            return;
        }
        IqPacket packet = this.mIqGenerator.retrieveAvatarMetaData(null);
        this.sendIqPacket(account, packet, new OnIqPacketReceived() {

            private Avatar parseAvatar(IqPacket packet) {
                Element pubsub = packet.findChild("pubsub", "http://jabber.org/protocol/pubsub");
                if (pubsub != null) {
                    Element items = pubsub.findChild("items");
                    if (items != null) {
                        return Avatar.parseMetadata(items);
                    }
                }
                return null;
            }

            private boolean errorIsItemNotFound(IqPacket packet) {
                Element error = packet.findChild("error");
                return packet.getType() == IqPacket.TYPE.ERROR
                        && error != null
                        && error.hasChild("item-not-found");
            }

            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                if (packet.getType() == IqPacket.TYPE.RESULT || errorIsItemNotFound(packet)) {
                    Avatar serverAvatar = parseAvatar(packet);
                    if (serverAvatar == null && account.getAvatar() != null) {
                        Avatar avatar = fileBackend.getStoredPepAvatar(account.getAvatar());
                        if (avatar != null) {
                            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": avatar on server was null. republishing");
                            publishAvatar(account, fileBackend.getStoredPepAvatar(account.getAvatar()), null);
                        } else {
                            Log.e(Config.LOGTAG, account.getJid().asBareJid() + ": error rereading avatar");
                        }
                    }
                }
            }
        });
    }

    public void fetchAvatar(Account account, Avatar avatar) {
        fetchAvatar(account, avatar, null);
    }

    public void fetchAvatar(Account account, final Avatar avatar, final UiCallback<Avatar> callback) {
        if (databaseBackend.isBlockedMedia(avatar.cid())) {
            if (callback != null) callback.error(0, null);
            return;
        }

        final String KEY = generateFetchKey(account, avatar);
        synchronized (this.mInProgressAvatarFetches) {
            if (mInProgressAvatarFetches.add(KEY)) {
                switch (avatar.origin) {
                    case PEP:
                        this.mInProgressAvatarFetches.add(KEY);
                        fetchAvatarPep(account, avatar, callback);
                        break;
                    case VCARD:
                        this.mInProgressAvatarFetches.add(KEY);
                        fetchAvatarVcard(account, avatar, callback);
                        break;
                }
            } else if (avatar.origin == Avatar.Origin.PEP) {
                mOmittedPepAvatarFetches.add(KEY);
            } else {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": already fetching " + avatar.origin + " avatar for " + avatar.owner);
            }
        }
    }

    private void fetchAvatarPep(Account account, final Avatar avatar, final UiCallback<Avatar> callback) {
        IqPacket packet = this.mIqGenerator.retrievePepAvatar(avatar);
        sendIqPacket(account, packet, (a, result) -> {
            synchronized (mInProgressAvatarFetches) {
                mInProgressAvatarFetches.remove(generateFetchKey(a, avatar));
            }
            final String ERROR = a.getJid().asBareJid() + ": fetching avatar for " + avatar.owner + " failed ";
            if (result.getType() == IqPacket.TYPE.RESULT) {
                avatar.image = mIqParser.avatarData(result);
                if (avatar.image != null) {
                    if (getFileBackend().save(avatar)) {
                        if (a.getJid().asBareJid().equals(avatar.owner)) {
                            if (a.setAvatar(avatar.getFilename())) {
                                databaseBackend.updateAccount(a);
                            }
                            getAvatarService().clear(a);
                            updateConversationUi();
                            updateAccountUi();
                        } else {
                            final Contact contact = a.getRoster().getContact(avatar.owner);
                            contact.setAvatar(avatar);
                            syncRoster(account);
                            getAvatarService().clear(contact);
                            updateConversationUi();
                            updateRosterUi(UpdateRosterReason.AVATAR);
                        }
                        if (callback != null) {
                            callback.success(avatar);
                        }
                        Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": successfully fetched pep avatar for " + avatar.owner);
                        return;
                    }
                } else {
                    Log.d(Config.LOGTAG, ERROR + "(parsing error)");
                }
            } else {
                Element error = result.findChild("error");
                if (error == null) {
                    Log.d(Config.LOGTAG, ERROR + "(server error)");
                } else {
                    Log.d(Config.LOGTAG, ERROR + error.toString());
                }
            }
            if (callback != null) {
                callback.error(0, null);
            }
        });
    }

    private void fetchAvatarVcard(final Account account, final Avatar avatar, final UiCallback<Avatar> callback) {
        IqPacket packet = this.mIqGenerator.retrieveVcardAvatar(avatar);
        this.sendIqPacket(account, packet, new OnIqPacketReceived() {
            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                final boolean previouslyOmittedPepFetch;
                synchronized (mInProgressAvatarFetches) {
                    final String KEY = generateFetchKey(account, avatar);
                    mInProgressAvatarFetches.remove(KEY);
                    previouslyOmittedPepFetch = mOmittedPepAvatarFetches.remove(KEY);
                }
                if (packet.getType() == IqPacket.TYPE.RESULT) {
                    Element vCard = packet.findChild("vCard", "vcard-temp");
                    Element photo = vCard != null ? vCard.findChild("PHOTO") : null;
                    String image = photo != null ? photo.findChildContent("BINVAL") : null;
                    if (image != null) {
                        avatar.image = image;
                        if (getFileBackend().save(avatar)) {
                            Log.d(Config.LOGTAG, account.getJid().asBareJid()
                                    + ": successfully fetched vCard avatar for " + avatar.owner + " omittedPep=" + previouslyOmittedPepFetch);
                            if (avatar.owner.isBareJid()) {
                                if (account.getJid().asBareJid().equals(avatar.owner) && account.getAvatar() == null) {
                                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": had no avatar. replacing with vcard");
                                    account.setAvatar(avatar.getFilename());
                                    databaseBackend.updateAccount(account);
                                    getAvatarService().clear(account);
                                    updateAccountUi();
                                } else {
                                    final Contact contact = account.getRoster().getContact(avatar.owner);
                                    contact.setAvatar(avatar, previouslyOmittedPepFetch);
                                    syncRoster(account);
                                    getAvatarService().clear(contact);
                                    updateRosterUi(UpdateRosterReason.AVATAR);
                                }
                                updateConversationUi();
                            } else {
                                Conversation conversation = find(account, avatar.owner.asBareJid());
                                if (conversation != null && conversation.getMode() == Conversation.MODE_MULTI) {
                                    MucOptions.User user = conversation.getMucOptions().findUserByFullJid(avatar.owner);
                                    if (user != null) {
                                        if (user.setAvatar(avatar)) {
                                            getAvatarService().clear(user);
                                            updateConversationUi();
                                            updateMucRosterUi();
                                        }
                                        if (user.getRealJid() != null) {
                                            Contact contact = account.getRoster().getContact(user.getRealJid());
                                            contact.setAvatar(avatar);
                                            syncRoster(account);
                                            getAvatarService().clear(contact);
                                            updateRosterUi(UpdateRosterReason.AVATAR);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    public void checkForAvatar(Account account, final UiCallback<Avatar> callback) {
        IqPacket packet = this.mIqGenerator.retrieveAvatarMetaData(null);
        this.sendIqPacket(account, packet, new OnIqPacketReceived() {

            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                if (packet.getType() == IqPacket.TYPE.RESULT) {
                    Element pubsub = packet.findChild("pubsub", "http://jabber.org/protocol/pubsub");
                    if (pubsub != null) {
                        Element items = pubsub.findChild("items");
                        if (items != null) {
                            Avatar avatar = Avatar.parseMetadata(items);
                            if (avatar != null) {
                                avatar.owner = account.getJid().asBareJid();
                                if (fileBackend.isAvatarCached(avatar)) {
                                    if (account.setAvatar(avatar.getFilename())) {
                                        databaseBackend.updateAccount(account);
                                    }
                                    getAvatarService().clear(account);
                                    callback.success(avatar);
                                } else {
                                    fetchAvatarPep(account, avatar, callback);
                                }
                                return;
                            }
                        }
                    }
                }
                callback.error(0, null);
            }
        });
    }

    public void notifyAccountAvatarHasChanged(final Account account) {
        final XmppConnection connection = account.getXmppConnection();
        if (connection != null && connection.getFeatures().bookmarksConversion()) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": avatar changed. resending presence to online group chats");
            for (Conversation conversation : conversations) {
                if (conversation.getAccount() == account && conversation.getMode() == Conversational.MODE_MULTI) {
                    presenceToMuc(conversation);
                }
            }
        }
    }

    public void fetchVcard4(Account account, final Contact contact, final Consumer<Element> callback) {
        IqPacket packet = this.mIqGenerator.retrieveVcard4(contact.getJid());
        sendIqPacket(account, packet, (a, result) -> {
            if (result.getType() == IqPacket.TYPE.RESULT) {
                final Element item = mIqParser.getItem(result);
                if (item != null) {
                    final Element vcard4 = item.findChild("vcard", Namespace.VCARD4);
                    if (vcard4 != null) {
                        if (callback != null) {
                            callback.accept(vcard4);
                        }
                        return;
                    }
                }
            } else {
                Element error = result.findChild("error");
                if (error == null) {
                    Log.d(Config.LOGTAG, "fetchVcard4 (server error)");
                } else {
                    Log.d(Config.LOGTAG, "fetchVcard4 " + error.toString());
                }
            }
            if (callback != null) {
                callback.accept(null);
            }

        });
    }
    public void deleteContactOnServer(Contact contact) {
        contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
        contact.resetOption(Contact.Options.DIRTY_PUSH);
        contact.setOption(Contact.Options.DIRTY_DELETE);
        Account account = contact.getAccount();
        if (account.getStatus() == Account.State.ONLINE) {
            IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
            Element item = iq.query(Namespace.ROSTER).addChild("item");
            item.setAttribute("jid", contact.getJid());
            item.setAttribute("subscription", "remove");
            account.getXmppConnection().sendIqPacket(iq, mDefaultIqHandler);
        }
    }

    public void updateConversation(final Conversation conversation) {
        mDatabaseWriterExecutor.execute(() -> databaseBackend.updateConversation(conversation));
    }

    private void reconnectAccount(final Account account) {
        account.setOption(Account.OPTION_DISABLED, true);
        if (!updateAccount(account)) {
            Log.d(Config.LOGTAG, getString(R.string.unable_to_update_account));
        }
        account.setOption(Account.OPTION_DISABLED, false);
        final XmppConnection connection = account.getXmppConnection();
        if (connection != null) {
            connection.resetEverything();
        }
        if (!updateAccount(account)) {
            Log.d(Config.LOGTAG, getString(R.string.unable_to_update_account));
        }
    }


    private void reconnectAccount(final Account account, final boolean force, final boolean interactive) {
        synchronized (account) {
            final XmppConnection existingConnection = account.getXmppConnection();
            final XmppConnection connection;
            if (existingConnection != null) {
                connection = existingConnection;
            } else if (account.isConnectionEnabled()) {
                connection = createConnection(account);
                account.setXmppConnection(connection);
            } else {
                return;
            }
            final boolean hasInternet = hasInternetConnection();
            if (account.isConnectionEnabled() && hasInternet) {
                if (!force) {
                    disconnect(account, false);
                }
                Thread thread = new Thread(connection);
                connection.setInteractive(interactive);
                connection.prepareNewConnection();
                connection.interrupt();
                thread.start();
                scheduleWakeUpCall(Config.CONNECT_DISCO_TIMEOUT, account.getUuid().hashCode());
            } else {
                disconnect(account, force || account.getTrueStatus().isError() || !hasInternet);
                account.getRoster().clearPresences();
                connection.resetEverything();
                final AxolotlService axolotlService = account.getAxolotlService();
                if (axolotlService != null) {
                    axolotlService.resetBrokenness();
                }
                if (!hasInternet) {
                    account.setStatus(Account.State.NO_INTERNET);
                }
            }
        }
    }

    public void reconnectAccountInBackground(final Account account) {
        new Thread(() -> reconnectAccount(account, false, true)).start();
    }

    public void invite(final Conversation conversation, final Jid contact) {
        Log.d(Config.LOGTAG, conversation.getAccount().getJid().asBareJid() + ": inviting " + contact + " to " + conversation.getJid().asBareJid());
        final MucOptions.User user = conversation.getMucOptions().findUserByRealJid(contact.asBareJid());
        if (user == null || user.getAffiliation() == MucOptions.Affiliation.OUTCAST) {
            changeAffiliationInConference(conversation, contact, MucOptions.Affiliation.NONE, null);
        }
        final MessagePacket packet = mMessageGenerator.invite(conversation, contact);
        sendMessagePacket(conversation.getAccount(), packet);
    }

    public void directInvite(Conversation conversation, Jid jid) {
        MessagePacket packet = mMessageGenerator.directInvite(conversation, jid);
        sendMessagePacket(conversation.getAccount(), packet);
    }

    public void resetSendingToWaiting(Account account) {
        for (Conversation conversation : getConversations()) {
            if (conversation.getAccount() == account) {
                conversation.findUnsentTextMessages(message -> markMessage(message, Message.STATUS_WAITING));
            }
        }
    }

    public Message markMessage(final Account account, final Jid recipient, final String uuid, final int status) {
        return markMessage(account, recipient, uuid, status, null);
    }

    public Message markMessage(final Account account, final Jid recipient, final String uuid, final int status, String errorMessage) {
        if (uuid == null) {
            return null;
        }
        for (Conversation conversation : getConversations()) {
            if (conversation.getJid().asBareJid().equals(recipient) && conversation.getAccount() == account) {
                final Message message = conversation.findSentMessageWithUuidOrRemoteId(uuid);
                if (message != null) {
                    markMessage(message, status, errorMessage);
                }
                return message;
            }
        }
        return null;
    }

    public boolean markMessage(final Conversation conversation, final String uuid, final int status, final String serverMessageId) {
        return markMessage(conversation, uuid, status, serverMessageId, null, null, null, null, null);
    }

    public boolean markMessage(final Conversation conversation, final String uuid, final int status, final String serverMessageId, final LocalizedContent body, final Element html, final String subject, final Element thread, final Set<Message.FileParams> attachments) {
        if (uuid == null) {
            return false;
        } else {
            final Message message = conversation.findSentMessageWithUuid(uuid);
            if (message != null) {
                if (message.getServerMsgId() == null) {
                    message.setServerMsgId(serverMessageId);
                }
                if (message.getEncryption() == Message.ENCRYPTION_NONE && (body != null || html != null || subject != null || thread != null || attachments != null)) {
                    message.setBody(body.content);
                    if (body.count > 1) {
                        message.setBodyLanguage(body.language);
                    }
                    message.setHtml(html);
                    message.setSubject(subject);
                    message.setThread(thread);
                    if (attachments != null && attachments.isEmpty()) {
                        message.setRelativeFilePath(null);
                        message.resetFileParams();
                    }
                    markMessage(message, status, null, true);
                } else {
                    markMessage(message, status);
                }
                return true;
            } else {
                return false;
            }
        }
    }

    public void markMessage(Message message, int status) {
        markMessage(message, status, null);
    }

    public void markMessage(final Message message, final int status, final String errorMessage) {
        markMessage(message, status, errorMessage, false);
    }

    public void markMessage(final Message message, final int status, final String errorMessage, final boolean includeBody) {
        final int oldStatus = message.getStatus();
        if (status == Message.STATUS_SEND_FAILED && (oldStatus == Message.STATUS_SEND_RECEIVED || oldStatus == Message.STATUS_SEND_DISPLAYED)) {
            return;
        }
        if (status == Message.STATUS_SEND_RECEIVED && oldStatus == Message.STATUS_SEND_DISPLAYED) {
            return;
        }
        message.setErrorMessage(errorMessage);
        message.setStatus(status);
        databaseBackend.updateMessage(message, includeBody);
        updateConversationUi();
        if (oldStatus != status && status == Message.STATUS_SEND_FAILED) {
            mNotificationService.pushFailedDelivery(message);
            // resend it
            mMessageResendTaskExecuter.execute(() -> {
                try {
                    if (message.increaseResendCount() <= maxResendTime() / 2) {
                        Thread.sleep(resendDelay());
                        resendFailedMessages(message);
                    }
                }
                catch (Exception ignore){
                    // if system halt, give it up
                    Log.w(Config.LOGTAG,"System Halt, so the message resend give up");
                }
            });

        }
    }
    private long resendDelay() {
        return Long.parseLong(getPreferences().getString(SettingsActivity.RESEND_DELAY, getResources().getString(R.string.resend_delay)));
    }

    public SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    public long getAutomaticMessageDeletionDate() {
        final long timeout = getLongPreference(SettingsActivity.AUTOMATIC_MESSAGE_DELETION, R.integer.automatic_message_deletion);
        return timeout == 0 ? timeout : (System.currentTimeMillis() - (timeout * 1000));
    }

    public long getAutomaticAttachmentDeletionDate() {
        final long timeout = getLongPreference(AUTOMATIC_ATTACHMENT_DELETION, R.integer.automatic_attachment_deletion);
        return timeout == 0 ? timeout : (System.currentTimeMillis() - (timeout * 1000));
    }

    public long getLongPreference(String name, @IntegerRes int res) {
        long defaultValue = getResources().getInteger(res);
        try {
            return Long.parseLong(getPreferences().getString(name, String.valueOf(defaultValue)));
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    public boolean getBooleanPreference(String name, @BoolRes int res) {
        return getPreferences().getBoolean(name, getResources().getBoolean(res));
    }

    public boolean confirmMessages() {
        return getBooleanPreference(CONFIRM_MESSAGES, R.bool.confirm_messages);
    }

    public boolean usingInnerStorage() {
        return getBooleanPreference(USE_INNER_STORAGE, R.bool.use_inner_storage);
    }

    public boolean allowMessageCorrection() {
        return getBooleanPreference(ALLOW_MESSAGE_CORRECTION, R.bool.allow_message_correction);
    }

    public boolean allowMessageRetraction() {
        return getBooleanPreference(ALLOW_MESSAGE_RETRACTION, R.bool.allow_message_retraction);
    }

    public boolean sendChatStates() {
        return getBooleanPreference(CHAT_STATES, R.bool.chat_states);
    }

    private boolean synchronizeWithBookmarks() {
        return getBooleanPreference("autojoin", R.bool.autojoin);
    }

    public boolean indicateReceived() {
        return getBooleanPreference(INDICATE_RECEIVED, R.bool.indicate_received);
    }

    public boolean useTorToConnect() {
        return getBooleanPreference("use_tor", R.bool.use_tor);
    }

    public boolean useI2PToConnect() {
        return getBooleanPreference("use_i2p", R.bool.use_i2p);
    }

    public boolean showExtendedConnectionOptions() {
        return getBooleanPreference("show_connection_options", R.bool.show_connection_options);
    }

    public boolean warnUnecryptedChat() {
        return getBooleanPreference(SettingsActivity.WARN_UNENCRYPTED_CHAT, R.bool.warn_unencrypted_chat);
    }

    public boolean hideYouAreNotParticipating() {
        return getBooleanPreference(SettingsActivity.HIDE_YOU_ARE_NOT_PARTICIPATING, R.bool.hide_you_are_not_participating);
    }


    public boolean appIsLocked() {
        return getBooleanPreference(SettingsActivity.APP_IS_LOCKED, R.bool.app_is_locked);
    }

    public boolean hideMemoryWarning() {
        return getBooleanPreference(SettingsActivity.HIDE_MEMORY_WARNING, R.bool.hide_memory_warning);
    }

    public boolean broadcastLastActivity() {
        return getBooleanPreference(SettingsActivity.BROADCAST_LAST_ACTIVITY, R.bool.last_activity);
    }

    public boolean multipleAccounts() {
        return getBooleanPreference(ENABLE_MULTI_ACCOUNTS, R.bool.enable_multi_accounts);
    }

    public boolean showOwnAccounts() {
        return getBooleanPreference(SHOW_OWN_ACCOUNTS, R.bool.show_own_accounts);
    }

    public boolean allowMergeMessages() {
        return false;
        //return getBooleanPreference("allowmergemessages", R.bool.allowmergemessages);
    }

    public boolean showTextFormatting() {
        return getBooleanPreference("showtextformatting", R.bool.showtextformatting);
    }

    public int unreadCount() {
        int count = 0;
        for (Conversation conversation : getConversations()) {
            count += conversation.unreadCount();
        }
        return count;
    }

    public void vibrate() {
        try {
            final boolean vibrateInChat = getBooleanPreference("vibrate_in_chat", R.bool.vibrate_in_chat);
            if (!isPhoneSilenced() && vibrateInChat) {
                Log.d(Config.LOGTAG, "Notification: short vibrate");
                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                vibrator.vibrate(100);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private <T> List<T> threadSafeList(Set<T> set) {
        synchronized (LISTENER_LOCK) {
            return set.size() == 0 ? Collections.emptyList() : new ArrayList<>(set);
        }
    }

    public void showErrorToastInUi(int resId) {
        for (OnShowErrorToast listener : threadSafeList(this.mOnShowErrorToasts)) {
            listener.onShowErrorToast(resId);
        }
    }

    public void updateConversationUi() {
        updateConversationUi(false);
    }

    public void updateConversationUi(boolean newCaps) {
        for (OnConversationUpdate listener : threadSafeList(this.mOnConversationUpdates)) {
            listener.onConversationUpdate(newCaps);
        }
    }

    public void notifyJingleRtpConnectionUpdate(final Account account, final Jid with, final String sessionId, final RtpEndUserState state) {
        for (OnJingleRtpConnectionUpdate listener : threadSafeList(this.onJingleRtpConnectionUpdate)) {
            listener.onJingleRtpConnectionUpdate(account, with, sessionId, state);
        }
    }

    public void notifyJingleRtpConnectionUpdate(CallIntegration.AudioDevice selectedAudioDevice, Set<CallIntegration.AudioDevice> availableAudioDevices) {
        for (OnJingleRtpConnectionUpdate listener : threadSafeList(this.onJingleRtpConnectionUpdate)) {
            listener.onAudioDeviceChanged(selectedAudioDevice, availableAudioDevices);
        }
    }

    public void updateAccountUi() {
        for (OnAccountUpdate listener : threadSafeList(this.mOnAccountUpdates)) {
            listener.onAccountUpdate();
        }
    }

    public void updateRosterUi(final UpdateRosterReason reason) {
        if (reason == UpdateRosterReason.PRESENCE) throw new IllegalArgumentException("PRESENCE must also come with a contact");
        updateRosterUi(reason, null);
    }

    public void updateRosterUi(final UpdateRosterReason reason, final Contact contact) {
        for (OnRosterUpdate listener : threadSafeList(this.mOnRosterUpdates)) {
            listener.onRosterUpdate(reason, contact);
        }
    }

    public boolean displayCaptchaRequest(Account account, String id, Data data, Bitmap captcha) {
        if (mOnCaptchaRequested.size() > 0) {
            DisplayMetrics metrics = getApplicationContext().getResources().getDisplayMetrics();
            Bitmap scaled = Bitmap.createScaledBitmap(captcha, (int) (captcha.getWidth() * metrics.scaledDensity),
                    (int) (captcha.getHeight() * metrics.scaledDensity), false);
            for (OnCaptchaRequested listener : threadSafeList(this.mOnCaptchaRequested)) {
                listener.onCaptchaRequested(account, id, data, scaled);
            }
            return true;
        }
        return false;
    }

    public void updateBlocklistUi(final OnUpdateBlocklist.Status status) {
        for (OnUpdateBlocklist listener : threadSafeList(this.mOnUpdateBlocklist)) {
            listener.OnUpdateBlocklist(status);
        }
    }

    public void updateMucRosterUi() {
        for (OnMucRosterUpdate listener : threadSafeList(this.mOnMucRosterUpdate)) {
            listener.onMucRosterUpdate();
        }
    }

    public void keyStatusUpdated(AxolotlService.FetchStatus report) {
        for (OnKeyStatusUpdated listener : threadSafeList(this.mOnKeyStatusUpdated)) {
            listener.onKeyStatusUpdated(report);
        }
    }

    public Account findAccountByJid(final Jid jid) {
        for (final Account account : this.accounts) {
            if (account.getJid().asBareJid().equals(jid.asBareJid())) {
                return account;
            }
        }
        return null;
    }

    public Account findAccountByUuid(final String uuid) {
        for (Account account : this.accounts) {
            if (account.getUuid().equals(uuid)) {
                return account;
            }
        }
        return null;
    }

    public Conversation findConversationByUuid(String uuid) {
        for (Conversation conversation : getConversations()) {
            if (conversation.getUuid().equals(uuid)) {
                return conversation;
            }
        }
        return null;
    }

    public Conversation findUniqueConversationByJid(XmppUri xmppUri) {
        List<Conversation> findings = new ArrayList<>();
        for (Conversation c : getConversations()) {
            if (c.getAccount().isEnabled() && c.getJid().asBareJid().equals(xmppUri.getJid().asBareJid()) && ((c.getMode() == Conversational.MODE_MULTI) == xmppUri.isAction(XmppUri.ACTION_JOIN))) {
                findings.add(c);
            }
        }
        return findings.size() == 1 ? findings.get(0) : null;
    }

    public boolean markRead(final Conversation conversation, boolean dismiss) {
        return markRead(conversation, null, dismiss).size() > 0;
    }

    public void markRead(final Conversation conversation) {
        markRead(conversation, null, true);
    }

    public List<Message> markRead(final Conversation conversation, String upToUuid, boolean dismiss) {
        if (dismiss) {
            mNotificationService.clear(conversation);
        }
        final List<Message> readMessages = conversation.markRead(upToUuid);
        if (readMessages.size() > 0) {
            final Runnable runnable = () -> {
                for (Message message : readMessages) {
                    databaseBackend.updateMessage(message, false);
                }
            };
            mDatabaseWriterExecutor.execute(runnable);
            updateConversationUi();
            updateUnreadCountBadge();
            return readMessages;
        } else {
            return readMessages;
        }
    }

    public synchronized void updateUnreadCountBadge() {
        final Runnable runnable = () -> {
            int count = unreadCount();
            if (unreadCount != count) {
                Log.d(Config.LOGTAG, "update unread count to " + count);
                if (count > 0) {
                    ShortcutBadger.applyCount(getApplicationContext(), count);
                } else {
                    ShortcutBadger.removeCount(getApplicationContext());
                }
                unreadCount = count;
            }
        };
        mDatabaseWriterExecutor.execute(runnable);
    }

    public void sendReadMarker(final Conversation conversation, final String upToUuid) {
        final boolean isPrivateAndNonAnonymousMuc =
                conversation.getMode() == Conversation.MODE_MULTI
                        && conversation.isPrivateAndNonAnonymous();
        final List<Message> readMessages = this.markRead(conversation, upToUuid, true);
        if (readMessages.isEmpty()) {
            return;
        }
        final var account = conversation.getAccount();
        final var connection = account.getXmppConnection();
        updateConversationUi();
        final var last =
                Iterables.getLast(
                        Collections2.filter(
                                readMessages,
                                m ->
                                        !m.isPrivateMessage()
                                                && m.getStatus() == Message.STATUS_RECEIVED),
                        null);
        if (last == null) {
            return;
        }

        final boolean sendDisplayedMarker =
                confirmMessages()
                        && (last.trusted() || isPrivateAndNonAnonymousMuc)
                        && last.getRemoteMsgId() != null
                        && (last.markable || isPrivateAndNonAnonymousMuc);
        final boolean serverAssist =
                connection != null && connection.getFeatures().mdsServerAssist();

        final String stanzaId = last.getServerMsgId();

        if (sendDisplayedMarker && serverAssist) {
            final var mdsDisplayed = mIqGenerator.mdsDisplayed(stanzaId, conversation);
            final MessagePacket packet = mMessageGenerator.confirm(last);
            packet.addChild(mdsDisplayed);
            if (!last.isPrivateMessage()) {
                packet.setTo(packet.getTo().asBareJid());
            }
            Log.d(Config.LOGTAG,account.getJid().asBareJid()+": server assisted "+packet);
            this.sendMessagePacket(account, packet);
        } else {
            publishMds(last);
            // read markers will be sent after MDS to flush the CSI stanza queue
            if (sendDisplayedMarker) {
                Log.d(
                        Config.LOGTAG,
                        conversation.getAccount().getJid().asBareJid()
                                + ": sending displayed marker to "
                                + last.getCounterpart().toString());
                final MessagePacket packet = mMessageGenerator.confirm(last);
                this.sendMessagePacket(account, packet);
            }
        }
    }

    private void publishMds(@Nullable final Message message) {
        final String stanzaId = message == null ? null : message.getServerMsgId();
        if (Strings.isNullOrEmpty(stanzaId)) {
            return;
        }
        final Conversation conversation;
        final var conversational = message.getConversation();
        if (conversational instanceof Conversation c) {
            conversation = c;
        } else {
            return;
        }
        final var account = conversation.getAccount();
        final var connection = account.getXmppConnection();
        if (connection == null || !connection.getFeatures().mds()) {
            return;
        }
        final Jid itemId;
        if (message.isPrivateMessage()) {
            itemId = message.getCounterpart();
        } else {
            itemId = conversation.getJid().asBareJid();
        }
        Log.d(Config.LOGTAG,"publishing mds for "+itemId+"/"+stanzaId);
        publishMds(account, itemId, stanzaId, conversation);
    }

    private void publishMds(
            final Account account, final Jid itemId, final String stanzaId, final Conversation conversation) {
        final var item = mIqGenerator.mdsDisplayed(stanzaId, conversation);
        pushNodeAndEnforcePublishOptions(
                account,
                Namespace.MDS_DISPLAYED,
                item,
                itemId.toEscapedString(),
                PublishOptions.persistentWhitelistAccessMaxItems());
    }

    public MemorizingTrustManager getMemorizingTrustManager() {
        return this.mMemorizingTrustManager;
    }

    public void setMemorizingTrustManager(MemorizingTrustManager trustManager) {
        this.mMemorizingTrustManager = trustManager;
    }

    public void updateMemorizingTrustmanager() {
        final MemorizingTrustManager tm;
        final boolean dontTrustSystemCAs = getBooleanPreference("dont_trust_system_cas", R.bool.dont_trust_system_cas);
        if (dontTrustSystemCAs) {
            tm = new MemorizingTrustManager(getApplicationContext(), null);
        } else {
            tm = new MemorizingTrustManager(getApplicationContext());
        }
        setMemorizingTrustManager(tm);
    }

    public void syncRosterToDisk(final Account account) {
        Runnable runnable = () -> databaseBackend.writeRoster(account.getRoster());
        mDatabaseWriterExecutor.execute(runnable);
    }

    public Collection<String> getKnownHosts() {
        final Set<String> hosts = new HashSet<>();
        for (final Account account : getAccounts()) {
            hosts.add(account.getServer());
            for (final Contact contact : account.getRoster().getContacts()) {
                if (contact.showInRoster()) {
                    final String server = contact.getServer();
                    if (server != null) {
                        hosts.add(server);
                    }
                }
            }
        }
        if (Config.QUICKSY_DOMAIN != null) {
            hosts.remove(Config.QUICKSY_DOMAIN.toEscapedString()); //we only want to show this when we type a e164 number
        }
        if (Config.DOMAIN_LOCK != null) {
            hosts.add(Config.DOMAIN_LOCK);
        }
        if (Config.MAGIC_CREATE_DOMAIN != null) {
            hosts.add(Config.MAGIC_CREATE_DOMAIN);
        }
        hosts.add("monocles.eu");
        return hosts;
    }

    public Collection<String> getKnownConferenceHosts() {
        final Set<String> mucServers = new HashSet<>();
        for (final Account account : accounts) {
            if (account.getXmppConnection() != null) {
                mucServers.addAll(account.getXmppConnection().getMucServers());
                for (final Bookmark bookmark : account.getBookmarks()) {
                    final Jid jid = bookmark.getJid();
                    final String s = jid == null ? null : jid.getDomain().toEscapedString();
                    if (s != null) {
                        mucServers.add(s);
                    }
                }
            }
        }
        return mucServers;
    }

    public void sendMessagePacket(Account account, MessagePacket packet) {
        final XmppConnection connection = account.getXmppConnection();
        if (connection != null) {
            connection.sendMessagePacket(packet);
        }
    }

    public void sendPresencePacket(Account account, PresencePacket packet) {
        XmppConnection connection = account.getXmppConnection();
        if (connection != null) {
            connection.sendPresencePacket(packet);
        }
    }

    public void sendCreateAccountWithCaptchaPacket(Account account, String id, Data data) {
        final XmppConnection connection = account.getXmppConnection();
        if (connection != null) {
            IqPacket request = mIqGenerator.generateCreateAccountWithCaptcha(account, id, data);
            connection.sendUnmodifiedIqPacket(request, connection.registrationResponseListener, true);
        }
    }

    public void sendIqPacket(final Account account, final IqPacket packet, final OnIqPacketReceived callback) {
        sendIqPacket(account, packet, callback, null);
    }

    public void sendIqPacket(final Account account, final IqPacket packet, final OnIqPacketReceived callback, Long timeout) {
        final XmppConnection connection = account.getXmppConnection();
        if (connection != null) {
            connection.sendIqPacket(packet, callback, timeout);
        } else if (callback != null) {
            callback.onIqPacketReceived(account, new IqPacket(IqPacket.TYPE.TIMEOUT));
        }
    }

    public void sendPresence(final Account account) {
        sendPresence(account, checkListeners() && broadcastLastActivity());
    }

    public void sendPresence(final Account account, final boolean includeIdleTimestamp) {
        final Presence.Status status;
        if (manuallyChangePresence()) {
            status = account.getPresenceStatus();
        } else {
            status = getTargetPresence();
        }
        final PresencePacket packet = mPresenceGenerator.selfPresence(account, status);
        if (mLastActivity > 0 && includeIdleTimestamp) {
            long since = Math.min(mLastActivity, System.currentTimeMillis()); //don't send future dates
            packet.addChild("idle", Namespace.IDLE).setAttribute("since", AbstractGenerator.getTimestamp(since));
        }
        sendPresencePacket(account, packet);
    }

    private void deactivateGracePeriod() {
        for (Account account : getAccounts()) {
            account.deactivateGracePeriod();
        }
    }

    public void refreshAllPresences() {
        boolean includeIdleTimestamp = checkListeners() && broadcastLastActivity();
        for (Account account : getAccounts()) {
            if (account.isConnectionEnabled()) {
                sendPresence(account, includeIdleTimestamp);
            }
        }
    }

    private void refreshAllFcmTokens() {
        for (Account account : getAccounts()) {
            if (account.isOnlineAndConnected() && mPushManagementService.available(account)) {
                mPushManagementService.registerPushTokenOnServer(account);
            }
        }
    }


    private void sendOfflinePresence(final Account account) {
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": sending offline presence");
        sendPresencePacket(account, mPresenceGenerator.sendOfflinePresence(account));
    }

    public MessageGenerator getMessageGenerator() {
        return this.mMessageGenerator;
    }

    public PresenceGenerator getPresenceGenerator() {
        return this.mPresenceGenerator;
    }

    public IqGenerator getIqGenerator() {
        return this.mIqGenerator;
    }

    public IqParser getIqParser() {
        return this.mIqParser;
    }

    public JingleConnectionManager getJingleConnectionManager() {
        return this.mJingleConnectionManager;
    }

    private boolean hasJingleRtpConnection(final Account account) {
        return this.mJingleConnectionManager.hasJingleRtpConnection(account);
    }

    public MessageArchiveService getMessageArchiveService() {
        return this.mMessageArchiveService;
    }

    public QuickConversationsService getQuickConversationsService() {
        return this.mQuickConversationsService;
    }

    public List<Contact> findContacts(Jid jid, String accountJid) {
        ArrayList<Contact> contacts = new ArrayList<>();
        for (Account account : getAccounts()) {
            if ((account.isEnabled() || accountJid != null)
                    && (accountJid == null || accountJid.equals(account.getJid().asBareJid().toString()))) {
                Contact contact = account.getRoster().getContactFromContactList(jid);
                if (contact != null) {
                    contacts.add(contact);
                }
            }
        }
        return contacts;
    }

    public Conversation findFirstMuc(Jid jid) {
        return findFirstMuc(jid, null);
    }

    public Conversation findFirstMuc(Jid jid, String accountJid) {
        for (Conversation conversation : getConversations()) {
            if ((conversation.getAccount().isEnabled() || accountJid != null)
                    && (accountJid == null || accountJid.equals(conversation.getAccount().getJid().asBareJid().toString()))
                    && conversation.getJid().asBareJid().equals(jid.asBareJid()) && conversation.getMode() == Conversation.MODE_MULTI) {
                return conversation;
            }
        }
        return null;
    }

    public NotificationService getNotificationService() {
        return this.mNotificationService;
    }

    public HttpConnectionManager getHttpConnectionManager() {
        return this.mHttpConnectionManager;
    }

    public void resendFailedMessages(final Message message) {
        final Collection<Message> messages = new ArrayList<>();
        Message current = message;
        while (current.getStatus() == Message.STATUS_SEND_FAILED) {
            messages.add(current);
            if (current.mergeable(current.next())) {
                current = current.next();
            } else {
                break;
            }
        }
        for (final Message msg : messages) {
            msg.setTime(System.currentTimeMillis());
            markMessage(msg, Message.STATUS_WAITING);
            this.resendMessage(msg, false);
        }
        if (message.getConversation() instanceof Conversation) {
            ((Conversation) message.getConversation()).sort();
        }
        updateConversationUi();
    }

    public void deleteMessage(final Conversation conversation, final Message message) {
        conversation.deleteMessage(message);
        message.setMessageDeleted(true);
        final Runnable runnable = () -> {
            databaseBackend.deleteMessageInConversation(message);
            databaseBackend.updateConversation(conversation);
        };
        mDatabaseWriterExecutor.execute(runnable);
    }

    public void clearConversationHistory(final Conversation conversation) {
        try {
            final long clearDate;
            final String reference;
            if (conversation.countMessages() > 0) {
                final Message latestMessage = conversation.getLatestMessage();
                clearDate = latestMessage.getTimeSent() + 1000;
                reference = latestMessage.getServerMsgId();
            } else {
                clearDate = System.currentTimeMillis();
                reference = null;
            }
            conversation.clearMessages();
            conversation.setHasMessagesLeftOnServer(false); //avoid messages getting loaded through mam
            conversation.setLastClearHistory(clearDate, reference);
            final Runnable runnable = () -> {
                databaseBackend.deleteMessagesInConversation(conversation);
                databaseBackend.updateConversation(conversation);
            };
            mDatabaseWriterExecutor.execute(runnable);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean sendBlockRequest(final Blockable blockable, final boolean reportSpam, final String serverMsgId) {
        if (blockable != null && blockable.getBlockedJid() != null) {
            final Jid jid = blockable.getBlockedJid();
            this.sendIqPacket(blockable.getAccount(), getIqGenerator().generateSetBlockRequest(jid, reportSpam, serverMsgId), (a, response) -> {
                if (response.getType() == IqPacket.TYPE.RESULT) {
                    a.getBlocklist().add(jid);
                    updateBlocklistUi(OnUpdateBlocklist.Status.BLOCKED);
                }
            });
            if (blockable.getBlockedJid().isFullJid()) {
                return false;
            } else if (removeBlockedConversations(blockable.getAccount(), jid)) {
                updateConversationUi();
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean removeBlockedConversations(final Account account, final Jid blockedJid) {
        boolean removed = false;
        synchronized (this.conversations) {
            boolean domainJid = blockedJid.getLocal() == null;
            for (Conversation conversation : this.conversations) {
                boolean jidMatches = (domainJid && blockedJid.getDomain().equals(conversation.getJid().getDomain()))
                        || blockedJid.equals(conversation.getJid().asBareJid());
                if (conversation.getAccount() == account
                        && conversation.getMode() == Conversation.MODE_SINGLE
                        && jidMatches) {
                    this.conversations.remove(conversation);
                    markRead(conversation);
                    conversation.setStatus(Conversation.STATUS_ARCHIVED);
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": archiving conversation " + conversation.getJid().asBareJid() + " because jid was blocked");
                    updateConversation(conversation);
                    removed = true;
                }
            }
        }
        return removed;
    }

    public void sendUnblockRequest(final Blockable blockable) {
        if (blockable != null && blockable.getJid() != null) {
            final Jid jid = blockable.getBlockedJid();
            this.sendIqPacket(blockable.getAccount(), getIqGenerator().generateSetUnblockRequest(jid), new OnIqPacketReceived() {
                @Override
                public void onIqPacketReceived(final Account account, final IqPacket packet) {
                    if (packet.getType() == IqPacket.TYPE.RESULT) {
                        account.getBlocklist().remove(jid);
                        updateBlocklistUi(OnUpdateBlocklist.Status.UNBLOCKED);
                        if (blockable.getJid().isFullJid()) {
                            reconnectAccount(account);
                        }
                    }
                }
            });
        }
    }

    public void publishDisplayName(Account account) {
        String displayName = account.getDisplayName();
        final IqPacket request;
        if (TextUtils.isEmpty(displayName)) {
            request = mIqGenerator.deleteNode(Namespace.NICK);
        } else {
            request = mIqGenerator.publishNick(displayName);
        }
        mAvatarService.clear(account);
        sendIqPacket(account, request, (account1, packet) -> {
            if (packet.getType() == IqPacket.TYPE.ERROR) {
                Log.d(Config.LOGTAG, account1.getJid().asBareJid() + ": unable to modify nick name " + packet);
            }
        });
    }

    public ServiceDiscoveryResult getCachedServiceDiscoveryResult(Pair<String, String> key) {
        if (key.first == null || key.second == null) return null;
        ServiceDiscoveryResult result = discoCache.get(key);
        if (result != null) {
            return result;
        } else {
            result = databaseBackend.findDiscoveryResult(key.first, key.second);
            if (result != null) {
                discoCache.put(key, result);
            }
            return result;
        }
    }
    public void fetchFromGateway(Account account, final Jid jid, final String input, final OnGatewayResult callback) {
        IqPacket request = new IqPacket(input == null ? IqPacket.TYPE.GET : IqPacket.TYPE.SET);
        request.setTo(jid);
        Element query = request.query("jabber:iq:gateway");
        if (input != null) {
            Element prompt = query.addChild("prompt");
            prompt.setContent(input);
        }
        sendIqPacket(account, request, (Account acct, IqPacket packet) -> {
            if (packet.getType() == IqPacket.TYPE.RESULT) {
                callback.onGatewayResult(packet.query().findChildContent(input == null ? "prompt" : "jid"), null);
            } else {
                Element error = packet.findChild("error");
                callback.onGatewayResult(null, error == null ? null : error.findChildContent("text"));
            }
        });
    }

    public void fetchGatewayPrompt(Account account, final Jid jid, final OnGatewayPromptResult callback) {
        IqPacket request = new IqPacket(IqPacket.TYPE.GET);
        request.setTo(jid);
        request.query("jabber:iq:gateway");
        sendIqPacket(account, request, new OnIqPacketReceived() {
            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                if (packet.getType() == IqPacket.TYPE.RESULT) {
                    callback.onGatewayPromptResult(packet.query().findChildContent("prompt"), null);
                } else {
                    Element error = packet.findChild("error");
                    callback.onGatewayPromptResult(null, error == null ? null : error.findChildContent("text"));
                }
            }
        });
    }


    public void fetchCaps(Account account, final Jid jid, final Presence presence) {

        fetchCaps(account, jid, presence, null);
    }

    public void fetchCaps(Account account, final Jid jid, final Presence presence, Runnable cb) {
        final Pair<String, String> key = presence == null ? null : new Pair<>(presence.getHash(), presence.getVer());
        final ServiceDiscoveryResult disco = key == null ? null : getCachedServiceDiscoveryResult(key);
        if (disco != null) {
            presence.setServiceDiscoveryResult(disco);
            final Contact contact = account.getRoster().getContact(jid);
            if (contact.refreshRtpCapability()) {
                syncRoster(account);
            }
            if (disco.hasIdentity("gateway", "pstn")) {
                contact.registerAsPhoneAccount(this);
                mQuickConversationsService.considerSyncBackground(false);
            }
            updateConversationUi(true);
        } else {
            final IqPacket request = new IqPacket(IqPacket.TYPE.GET);
            request.setTo(jid);
            final String node = presence == null ? null : presence.getNode();
            final String ver = presence == null ? null : presence.getVer();
            final Element query = request.query(Namespace.DISCO_INFO);
            if (node != null && ver != null) {
                query.setAttribute("node", node + "#" + ver);
            }
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": making disco request for " + (key == null ? "" : key.second) + " to " + jid);
            sendIqPacket(account, request, (a, response) -> {
                if (response.getType() == IqPacket.TYPE.RESULT) {
                    final ServiceDiscoveryResult discoveryResult = new ServiceDiscoveryResult(response);
                    if (presence == null || presence.getVer() == null || presence.getVer().equals(discoveryResult.getVer())) {
                        databaseBackend.insertDiscoveryResult(discoveryResult);
                        injectServiceDiscoveryResult(a.getRoster(), presence == null ? null : presence.getHash(), presence == null ? null : presence.getVer(), jid.getResource(), discoveryResult);
                        if (discoveryResult.hasIdentity("gateway", "pstn")) {
                            final Contact contact = account.getRoster().getContact(jid);
                            contact.registerAsPhoneAccount(this);
                            mQuickConversationsService.considerSyncBackground(false);
                        }
                        updateConversationUi(true);
                        if (cb != null) cb.run();
                    } else {
                        Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": mismatch in caps for contact " + jid + " " + presence.getVer() + " vs " + discoveryResult.getVer());
                    }
                } else {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": unable to fetch caps from " + jid);
                }
            });
        }
    }


    public void fetchCommands(Account account, final Jid jid, OnIqPacketReceived callback) {
        final IqPacket request = mIqGenerator.queryDiscoItems(jid, "http://jabber.org/protocol/commands");
        sendIqPacket(account, request, callback);
    }

    private void injectServiceDiscoveryResult(Roster roster, String hash, String ver, String resource, ServiceDiscoveryResult disco) {
        boolean rosterNeedsSync = false;
        for (final Contact contact : roster.getContacts()) {
            boolean serviceDiscoverySet = false;
            Presence onePresence = contact.getPresences().get(resource == null ? "" : resource);
            if (onePresence != null) {
                onePresence.setServiceDiscoveryResult(disco);
                serviceDiscoverySet = true;
            } else if (resource == null && hash == null && ver == null) {
                Presence p = new Presence(Presence.Status.OFFLINE, null, null, null, "");
                p.setServiceDiscoveryResult(disco);
                contact.updatePresence("", p);
                serviceDiscoverySet = true;
            }
            if (hash != null && ver != null) {
                for (final Presence presence : contact.getPresences().getPresences()) {
                    if (hash.equals(presence.getHash()) && ver.equals(presence.getVer())) {
                        presence.setServiceDiscoveryResult(disco);
                        serviceDiscoverySet = true;
                    }
                }
            }
            if (serviceDiscoverySet) {
                rosterNeedsSync |= contact.refreshRtpCapability();
            }
        }
        if (rosterNeedsSync) {
            syncRoster(roster.getAccount());
        }
    }

    public void fetchMamPreferences(Account account, final OnMamPreferencesFetched callback) {
        final MessageArchiveService.Version version = MessageArchiveService.Version.get(account);
        IqPacket request = new IqPacket(IqPacket.TYPE.GET);
        request.addChild("prefs", version.namespace);
        sendIqPacket(account, request, (account1, packet) -> {
            Element prefs = packet.findChild("prefs", version.namespace);
            if (packet.getType() == IqPacket.TYPE.RESULT && prefs != null) {
                callback.onPreferencesFetched(prefs);
            } else {
                callback.onPreferencesFetchFailed();
            }
        });
    }

    public PushManagementService getPushManagementService() {
        return mPushManagementService;
    }

    public void changeStatus(Account account, PresenceTemplate template, String signature) {
        if (!template.getStatusMessage().isEmpty()) {
            databaseBackend.insertPresenceTemplate(template);
        }
        account.setPgpSignature(signature);
        account.setPresenceStatus(template.getStatus());
        account.setPresenceStatusMessage(template.getStatusMessage());
        databaseBackend.updateAccount(account);
        sendPresence(account);
    }

    public List<PresenceTemplate> getPresenceTemplates(Account account) {
        List<PresenceTemplate> templates = databaseBackend.getPresenceTemplates();
        for (PresenceTemplate template : account.getSelfContact().getPresences().asTemplates()) {
            if (!templates.contains(template)) {
                templates.add(0, template);
            }
        }
        return templates;
    }

    public void saveConversationAsBookmark(Conversation conversation, String name) {
        final Account account = conversation.getAccount();
        final Bookmark bookmark = new Bookmark(account, conversation.getJid().asBareJid());
        String nick = conversation.getMucOptions().getActualNick();
        if (nick == null) nick = conversation.getJid().getResource();
        if (nick != null && !nick.isEmpty() && !nick.equals(MucOptions.defaultNick(account))) {
            bookmark.setNick(nick);
        }
        if (!TextUtils.isEmpty(name)) {
            bookmark.setBookmarkName(name);
        }
        bookmark.setAutojoin(getPreferences().getBoolean("autojoin", getResources().getBoolean(R.bool.autojoin)));
        createBookmark(account, bookmark);
        bookmark.setConversation(conversation);
    }

    public boolean verifyFingerprints(Contact contact, List<XmppUri.Fingerprint> fingerprints) {
        boolean needsRosterWrite = false;
        boolean performedVerification = false;
        final AxolotlService axolotlService = contact.getAccount().getAxolotlService();
        for (XmppUri.Fingerprint fp : fingerprints) {
            if (fp.type == XmppUri.FingerprintType.OTR) {
                performedVerification |= contact.addOtrFingerprint(fp.fingerprint);
                needsRosterWrite |= performedVerification;
            } else if (fp.type == XmppUri.FingerprintType.OMEMO) {
                String fingerprint = "05" + fp.fingerprint.replaceAll("\\s", "");
                FingerprintStatus fingerprintStatus = axolotlService.getFingerprintTrust(fingerprint);
                if (fingerprintStatus != null) {
                    if (!fingerprintStatus.isVerified()) {
                        performedVerification = true;
                        axolotlService.setFingerprintTrust(fingerprint, fingerprintStatus.toVerified());
                    }
                } else {
                    axolotlService.preVerifyFingerprint(contact, fingerprint);
                }
            }
        }
        if (needsRosterWrite) {
            syncRosterToDisk(contact.getAccount());
        }
        return performedVerification;
    }

    public boolean verifyFingerprints(Account account, List<XmppUri.Fingerprint> fingerprints) {
        final AxolotlService axolotlService = account.getAxolotlService();
        boolean verifiedSomething = false;
        for (XmppUri.Fingerprint fp : fingerprints) {
            if (fp.type == XmppUri.FingerprintType.OMEMO) {
                String fingerprint = "05" + fp.fingerprint.replaceAll("\\s", "");
                Log.d(Config.LOGTAG, "trying to verify own fp=" + fingerprint);
                FingerprintStatus fingerprintStatus = axolotlService.getFingerprintTrust(fingerprint);
                if (fingerprintStatus != null) {
                    if (!fingerprintStatus.isVerified()) {
                        axolotlService.setFingerprintTrust(fingerprint, fingerprintStatus.toVerified());
                        verifiedSomething = true;
                    }
                } else {
                    axolotlService.preVerifyFingerprint(account, fingerprint);
                    verifiedSomething = true;
                }
            }
        }
        return verifiedSomething;
    }

    public boolean blindTrustBeforeVerification() {
        return getBooleanPreference(SettingsActivity.BLIND_TRUST_BEFORE_VERIFICATION, R.bool.btbv);
    }

    public void ScheduleAutomaticExport() {
        //start export log service every day at given time
        if (Config.ExportLogs) {
            if (Config.ExportLogs_Hour >= 0 && Config.ExportLogs_Hour <= 23 && Config.ExportLogs_Minute >= 0 && Config.ExportLogs_Minute <= 59) {
                try {
                    Calendar now = Calendar.getInstance();
                    now.setTimeInMillis(System.currentTimeMillis());
                    now.setTimeZone(TimeZone.getDefault());
                    Calendar timetoexport = Calendar.getInstance();
                    timetoexport.setTimeInMillis(System.currentTimeMillis());
                    timetoexport.setTimeZone(TimeZone.getDefault());
                    Intent intent = new Intent(this, AlarmReceiver.class);
                    intent.setAction("exportlogs");
                    Log.d(Config.LOGTAG, "Schedule automatic export logs at " + Config.ExportLogs_Hour + ":" + Config.ExportLogs_Minute + " (" + timetoexport.getTimeZone().getDisplayName() + ")");
                    timetoexport.set(Calendar.HOUR_OF_DAY, Config.ExportLogs_Hour);
                    timetoexport.set(Calendar.MINUTE, Config.ExportLogs_Minute);
                    if (timetoexport.before(now)) {
                        SimpleDateFormat newDate = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                        timetoexport.add(Calendar.DAY_OF_YEAR, 1); //DATE or DAY_OF_MONTH
                        Log.d(Config.LOGTAG, "Schedule automatic export logs, for today, the export time is in the past, scheduling first export run for the next day (" + newDate.format(timetoexport.getTimeInMillis()) + ").");
                    }
                    final PendingIntent ScheduleExportIntent = PendingIntent.getBroadcast(this, AlarmReceiver.SCHEDULE_ALARM_REQUEST_CODE, intent, s()
                            ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                            : PendingIntent.FLAG_UPDATE_CURRENT);
                    ((AlarmManager) getSystemService(ALARM_SERVICE)).setInexactRepeating(AlarmManager.RTC_WAKEUP, timetoexport.getTimeInMillis(), AlarmManager.INTERVAL_DAY, ScheduleExportIntent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void CancelAutomaticExport(boolean force) {
        if (!Config.ExportLogs || force) {
            Log.d(Config.LOGTAG, "Cancel scheduled automatic export");
            Intent intent = new Intent(this, AlarmReceiver.class);
            final PendingIntent ScheduleExportIntent = PendingIntent.getBroadcast(this, AlarmReceiver.SCHEDULE_ALARM_REQUEST_CODE, intent, s()
                    ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                    : PendingIntent.FLAG_UPDATE_CURRENT);
            ((AlarmManager) this.getSystemService(ALARM_SERVICE)).cancel(ScheduleExportIntent);
        }
    }

    public String installedFrom() {
        final PackageManager packageManager = this.getPackageManager();
        final String packageID = BuildConfig.APPLICATION_ID;
        final String installedFrom = packageManager.getInstallerPackageName(packageID);
        Log.d(Config.LOGTAG, "Messenger installed from " + installedFrom);
        return installedFrom;
    }

    public ShortcutService getShortcutService() {
        return mShortcutService;
    }

    public void pushMamPreferences(Account account, Element prefs) {
        IqPacket set = new IqPacket(IqPacket.TYPE.SET);
        set.addChild(prefs);
        sendIqPacket(account, set, null);
    }

    public void evictPreview(File f) {
        if (f == null) return;

        if (mDrawableCache.remove(f.getAbsolutePath()) != null) {
            Log.d(Config.LOGTAG, "deleted cached preview");
        }
    }

    public void evictPreview(String uuid) {
        if (mDrawableCache.remove(uuid) != null) {
            Log.d(Config.LOGTAG, "deleted cached preview");
        }
    }

    public void showInvitationNotification(final Conversation conversation, final Contact contact, final LocalizedContent id) {
        final String messageId = "MUC_INVITATION_" + id;
        final Message message = new Message(
                conversation,
                Message.STATUS_RECEIVED,
                Message.TYPE_PRIVATE,
                messageId
        );
        String from;
        if (contact == null) {
            from = getString(R.string.a_user);
        } else {
            from = contact.getJid().asBareJid().toEscapedString();
        }
        String to = conversation.getJid().toString();
        final String toDisplayName = conversation.getName().toString();
        if (toDisplayName != null && toDisplayName.length() > 0) {
            to = toDisplayName + " (" + to + ")";
        }
        message.setBody(String.format(getString(R.string.got_invitation_from), from, to));
        message.setServerMsgId(messageId);
        message.setTime(System.currentTimeMillis());
        if (conversation instanceof Conversation) {
            ((Conversation) conversation).add(message);
            createMessageAsync(message);
            message.markUnread();
            updateConversationUi();
        } else {
            throw new IllegalStateException("Somehow the conversation in a message was a stub");
        }
    }

    public DownloadableFile getFileForCid(Cid cid) {
        return this.databaseBackend.getFileForCid(cid);
    }

    public String getUrlForCid(Cid cid) {
        return this.databaseBackend.getUrlForCid(cid);
    }

    public void saveCid(Cid cid, File file) throws BlockedMediaException {
        saveCid(cid, file, null);
    }

    public void saveCid(Cid cid, File file, String url) throws BlockedMediaException {
        if (this.databaseBackend.isBlockedMedia(cid)) {
            throw new BlockedMediaException();
        }
        this.databaseBackend.saveCid(cid, file, url);
    }


    public boolean muteMucUser(MucOptions.User user) {
        boolean muted = databaseBackend.muteMucUser(user);
        if (!muted) return false;
        mutedMucUsers.put(user.getMuc().toString(), user.getOccupantId());
        return true;
    }

    public boolean unmuteMucUser(MucOptions.User user) {
        boolean unmuted = databaseBackend.unmuteMucUser(user);
        if (!unmuted) return false;
        mutedMucUsers.remove(user.getMuc().toString(), user.getOccupantId());
        return true;
    }

    public boolean isMucUserMuted(MucOptions.User user) {
        return mutedMucUsers.containsEntry("" + user.getMuc(), user.getOccupantId());
    }

    public void blockMedia(File f) {
        try {
            Cid[] cids = getFileBackend().calculateCids(new FileInputStream(f));
            for (Cid cid : cids) {
                blockMedia(cid);
            }
        } catch (final IOException e) { }
    }

    public LruCache<String, Drawable> getDrawableCache() {
        return this.mDrawableCache;
    }

    public boolean isOnboarding() {
        // return getAccounts().size() == 1 && getAccounts().get(0).getJid().getDomain().equals(Config.ONBOARDING_DOMAIN);
        return false;
    }

    public interface OnMamPreferencesFetched {
        void onPreferencesFetched(Element prefs);

        void onPreferencesFetchFailed();
    }

    public interface OnAccountCreated {
        void onAccountCreated(Account account);

        void informUser(int r);
    }

    public interface OnMoreMessagesLoaded {
        void onMoreMessagesLoaded(int count, Conversation conversation);

        void informUser(int r);
    }

    public interface OnAccountPasswordChanged {
        void onPasswordChangeSucceeded();

        void onPasswordChangeFailed();
    }

    public interface OnAffiliationChanged {
        void onAffiliationChangedSuccessful(Jid jid);

        void onAffiliationChangeFailed(Jid jid, int resId);
    }

    public interface OnRoomDestroy {
        void onRoomDestroySucceeded();

        void onRoomDestroyFailed();
    }

    public interface OnConversationUpdate {
        default void onConversationUpdate() { onConversationUpdate(false); }
        default void onConversationUpdate(boolean newCaps) { onConversationUpdate(); }
    }

    public interface OnJingleRtpConnectionUpdate {
        void onJingleRtpConnectionUpdate(final Account account, final Jid with, final String sessionId, final RtpEndUserState state);

        void onAudioDeviceChanged(CallIntegration.AudioDevice selectedAudioDevice, Set<CallIntegration.AudioDevice> availableAudioDevices);
    }

    public interface OnAccountUpdate {
        void onAccountUpdate();
    }

    public interface OnCaptchaRequested {
        void onCaptchaRequested(Account account, String id, Data data, Bitmap captcha);
    }

    public interface OnRosterUpdate {
        void onRosterUpdate(final UpdateRosterReason reason, final Contact contact);
    }

    public interface OnMucRosterUpdate {
        void onMucRosterUpdate();
    }

    public interface OnConferenceConfigurationFetched {
        void onConferenceConfigurationFetched(Conversation conversation);

        void onFetchFailed(Conversation conversation, String errorCondition);
    }

    public interface OnConferenceJoined {
        void onConferenceJoined(Conversation conversation);
    }

    public interface OnConfigurationPushed {
        void onPushSucceeded();

        void onPushFailed();
    }

    public interface OnShowErrorToast {
        void onShowErrorToast(int resId);
    }

    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }

    private class InternalEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            onStartCommand(intent, 0, 0);
        }
    }

    private class RestrictedEventReceiver extends BroadcastReceiver {

        private final Collection<String> allowedActions;

        private RestrictedEventReceiver(final Collection<String> allowedActions) {
            this.allowedActions = allowedActions;
        }

        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent == null ? null : intent.getAction();
            if (allowedActions.contains(action)) {
                onStartCommand(intent,0,0);
            } else {
                Log.e(Config.LOGTAG,"restricting broadcast of event "+action);
            }
        }
    }

    public static class OngoingCall {
        public final AbstractJingleConnection.Id id;
        public final Set<Media> media;
        public final boolean reconnecting;

        public OngoingCall(AbstractJingleConnection.Id id, Set<Media> media, final boolean reconnecting) {
            this.id = id;
            this.media = media;
            this.reconnecting = reconnecting;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OngoingCall that = (OngoingCall) o;
            return reconnecting == that.reconnecting && Objects.equal(id, that.id) && Objects.equal(media, that.media);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id, media, reconnecting);
        }
    }

    private void expireOldMessages(long timestamp, boolean stepped) {
        if (stepped) {
            final long expiredMessagesCount = databaseBackend.countExpireOldMessages(timestamp);
            final long days = TimeUnit.MILLISECONDS.toDays(Calendar.getInstance().getTimeInMillis() - databaseBackend.getOldestMessages());
            final long day = (long) 24 * 60 * 60 * 1000;
            int count = 0;
            int messagesCount = 0;
            while (count <= days) {
                try {
                    messagesCount += databaseBackend.expireOldMessages(timestamp - ((days - count) * day));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                count++;
                if (expiredMessagesCount == messagesCount) {
                    break;
                }
            }
        } else {
            databaseBackend.expireOldMessages(timestamp);
        }
    }

    public void expireOldFiles() {
        mLastExpiryRun.set(SystemClock.elapsedRealtime());
        new Thread(() -> {
            long timestamp = getAutomaticAttachmentDeletionDate();
            if (timestamp > 0) {
                getFileBackend().expireOldFiles(new File(getAppMediaDirectory(this, AUDIOS)), timestamp);
                getFileBackend().expireOldFiles(new File(getAppMediaDirectory(this, FILES)), timestamp);
                getFileBackend().expireOldFiles(new File(getAppMediaDirectory(this, IMAGES)), timestamp);
                getFileBackend().expireOldFiles(new File(getAppMediaDirectory(this, VIDEOS)), timestamp);
                updateConversationUi();
            }
        }).start();
    }
    public static class BlockedMediaException extends Exception { }

    public static void toggleForegroundService(final XmppConnectionService service) {
        if (service == null) {
            return;
        }
        service.toggleForegroundService();
    }

    public static void toggleForegroundService(final ConversationsActivity activity) {
        if (activity == null) {
            return;
        }
        toggleForegroundService(activity.xmppConnectionService);
    }

    public static enum UpdateRosterReason {
        INIT,
        AVATAR,
        PUSH,
        PRESENCE
    }


    private File stickerDir() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + APP_DIRECTORY + File.separator + "Stickers");
    }

    public void rescanStickers() {
        long msToRescan = (mLastStickerRescan + 600000L) - SystemClock.elapsedRealtime();
        if (msToRescan > 0) return;
        Log.d(Config.LOGTAG, "rescanStickers");

        mLastStickerRescan = SystemClock.elapsedRealtime();
        mStickerScanExecutor.execute(() -> {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
            try {
                for (File file : Files.fileTraverser().breadthFirst(stickerDir())) {
                    try {
                        if (file.isFile() && file.canRead()) {
                            DownloadableFile df = new DownloadableFile(file.getAbsolutePath());
                            Drawable icon = fileBackend.getThumbnail(df, getResources(), (int) (getResources().getDisplayMetrics().density * 288), false);
                            final String filename = Files.getNameWithoutExtension(df.getName());
                            Cid[] cids = fileBackend.calculateCids(new FileInputStream(df));
                            for (Cid cid : cids) {
                                saveCid(cid, file);
                            }
                            if (file.length() < 129000) {
                                emojiSearch.addEmoji(new EmojiSearch.CustomEmoji(filename, cids[0].toString(), icon, file.getParentFile().getName()));
                            }
                        }
                    } catch (final Exception e) {
                        Log.w(Config.LOGTAG, "rescanStickers: " + e);
                    }
                }
            } catch (final Exception e) {
                Log.w(Config.LOGTAG, "rescanStickers: " + e);
            }
        });
    }

    public EmojiSearch emojiSearch() {
        return emojiSearch;
    }
}