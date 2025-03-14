package eu.siacs.conversations.entities;

import static eu.siacs.conversations.entities.Bookmark.printableValue;

import android.content.Intent;

import eu.siacs.conversations.databinding.CommandSliderFieldBinding;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.ui.UriHandlerActivity;
import android.content.DialogInterface;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.graphics.drawable.AnimatedImageDrawable;
import android.net.Uri;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.LruCache;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.widget.AbsListView;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebMessage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.GridLayout;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.util.Consumer;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import android.util.Pair;
import android.util.DisplayMetrics;
import com.caverock.androidsvg.SVG;

import android.util.SparseBooleanArray;

import java.text.DecimalFormat;
import java.util.Collection;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.base.Optional;

import de.monocles.mod.ConversationPage;
import de.monocles.mod.Util;
import de.monocles.mod.WebxdcPage;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.CommandCheckboxFieldBinding;
import eu.siacs.conversations.databinding.CommandNoteBinding;
import eu.siacs.conversations.databinding.CommandPageBinding;
import eu.siacs.conversations.databinding.CommandProgressBarBinding;
import eu.siacs.conversations.databinding.CommandRadioEditFieldBinding;
import eu.siacs.conversations.databinding.CommandResultCellBinding;
import eu.siacs.conversations.databinding.CommandResultFieldBinding;
import eu.siacs.conversations.databinding.CommandSearchListFieldBinding;
import eu.siacs.conversations.databinding.CommandSpinnerFieldBinding;
import eu.siacs.conversations.databinding.CommandTextFieldBinding;
import eu.siacs.conversations.databinding.CommandWebviewBinding;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.util.ShareUtil;
import eu.siacs.conversations.databinding.CommandItemCardBinding;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.forms.Option;

import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import me.saket.bettermovementmethod.BetterLinkMovementMethod;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import java.util.HashSet;
import java.util.Set;

import net.java.otr4j.OtrException;
import net.java.otr4j.crypto.OtrCryptoException;
import net.java.otr4j.session.SessionID;
import net.java.otr4j.session.SessionImpl;
import net.java.otr4j.session.SessionStatus;

import java.security.interfaces.DSAPublicKey;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.viewpager.widget.ViewPager;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.crypto.PgpDecryptionService;
import eu.siacs.conversations.databinding.CommandButtonGridFieldBinding;
import eu.siacs.conversations.databinding.DialogQuickeditBinding;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.ui.util.SoftKeyboardUtils;
import eu.siacs.conversations.utils.JidHelper;
import eu.siacs.conversations.utils.MessageUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.mam.MamReference;


public class Conversation extends AbstractEntity implements Blockable, Comparable<Conversation>, Conversational, AvatarService.Avatarable {
    public static final String TABLENAME = "conversations";

    public static final int STATUS_AVAILABLE = 0;
    public static final int STATUS_ARCHIVED = 1;

    public static final String NAME = "name";
    public static final String ACCOUNT = "accountUuid";
    public static final String CONTACT = "contactUuid";
    public static final String CONTACTJID = "contactJid";
    public static final String STATUS = "status";
    public static final String CREATED = "created";
    public static final String MODE = "mode";
    public static final String ATTRIBUTES = "attributes";

    public static final String ATTRIBUTE_MUTED_TILL = "muted_till";
    public static final String ATTRIBUTE_ALWAYS_NOTIFY = "always_notify";

    public static final String ATTRIBUTE_NOTIFY_REPLIES = "notify_replies";
    public static final String ATTRIBUTE_LAST_CLEAR_HISTORY = "last_clear_history";
    public static final String ATTRIBUTE_FORMERLY_PRIVATE_NON_ANONYMOUS = "formerly_private_non_anonymous";
    public static final String ATTRIBUTE_PINNED_ON_TOP = "pinned_on_top";
    static final String ATTRIBUTE_MUC_PASSWORD = "muc_password";
    static final String ATTRIBUTE_MEMBERS_ONLY = "members_only";
    static final String ATTRIBUTE_MODERATED = "moderated";
    static final String ATTRIBUTE_NON_ANONYMOUS = "non_anonymous";
    private static final String ATTRIBUTE_NEXT_MESSAGE = "next_message";
    private static final String ATTRIBUTE_NEXT_MESSAGE_TIMESTAMP = "next_message_timestamp";
    private static final String ATTRIBUTE_CRYPTO_TARGETS = "crypto_targets";
    private static final String ATTRIBUTE_NEXT_ENCRYPTION = "next_encryption";
    private static final String ATTRIBUTE_CORRECTING_MESSAGE = "correcting_message";
    protected final ArrayList<Message> messages = new ArrayList<>();
    public AtomicBoolean messagesLoaded = new AtomicBoolean(true);
    protected Account account = null;
    private String draftMessage;
    private String name;
    private String contactUuid;
    private String accountUuid;
    private Jid contactJid;
    private int status;
    private long created;
    private int mode;
    private JSONObject attributes;
    private Jid nextCounterpart;
    private transient SessionImpl otrSession;
    private transient String otrFingerprint = null;
    private Smp mSmp = new Smp();
    private transient MucOptions mucOptions = null;
    private byte[] symmetricKey;
    private boolean messagesLeftOnServer = true;
    private ChatState mOutgoingChatState = Config.DEFAULT_CHAT_STATE;
    private ChatState mIncomingChatState = Config.DEFAULT_CHAT_STATE;
    private String mLastReceivedOtrMessageId = null;
    private String mFirstMamReference = null;
    private String displayState = null;
    protected int mCurrentTab = -1;
    protected ConversationPagerAdapter pagerAdapter = new ConversationPagerAdapter();
    protected Element thread = null;
    protected boolean lockThread = false;
    protected boolean userSelectedThread = false;
    protected Message replyTo = null;
    protected HashMap<String, Thread> threads = new HashMap<>();
    protected XmppConnectionService xmppConnectionService;

    public Conversation(final String name, final Account account, final Jid contactJid,
                        final int mode) {
        this(java.util.UUID.randomUUID().toString(), name, null, account
                        .getUuid(), contactJid, System.currentTimeMillis(),
                STATUS_AVAILABLE, mode, "");
        this.account = account;
    }

    public Conversation(final String uuid, final String name, final String contactUuid,
                        final String accountUuid, final Jid contactJid, final long created, final int status,
                        final int mode, final String attributes) {
        this.uuid = uuid;
        this.name = name;
        this.contactUuid = contactUuid;
        this.accountUuid = accountUuid;
        this.contactJid = contactJid;
        this.created = created;
        this.status = status;
        this.mode = mode;
        try {
            this.attributes = new JSONObject(attributes == null ? "" : attributes);
        } catch (JSONException e) {
            this.attributes = new JSONObject();
        }
    }

    public static Conversation fromCursor(Cursor cursor) {
        return new Conversation(cursor.getString(cursor.getColumnIndex(UUID)),
                cursor.getString(cursor.getColumnIndex(NAME)),
                cursor.getString(cursor.getColumnIndex(CONTACT)),
                cursor.getString(cursor.getColumnIndex(ACCOUNT)),
                JidHelper.parseOrFallbackToInvalid(cursor.getString(cursor.getColumnIndex(CONTACTJID))),
                cursor.getLong(cursor.getColumnIndex(CREATED)),
                cursor.getInt(cursor.getColumnIndex(STATUS)),
                cursor.getInt(cursor.getColumnIndex(MODE)),
                cursor.getString(cursor.getColumnIndex(ATTRIBUTES)));
    }

    public static Message getLatestMarkableMessage(final List<Message> messages, boolean isPrivateAndNonAnonymousMuc) {
        for (int i = messages.size() - 1; i >= 0; --i) {
            final Message message = messages.get(i);
            if (message.getStatus() <= Message.STATUS_RECEIVED
                    && (message.markable || isPrivateAndNonAnonymousMuc)
                    && !message.isPrivateMessage()) {
                return message;
            }
        }
        return null;
    }

    public static boolean suitableForOmemoByDefault(final Conversation conversation) {
        if (conversation.getJid().asBareJid().equals(Config.BUG_REPORTS)) {
            return false;
        }
        if (conversation.getContact().isOwnServer()) {
            return false;
        }
        final String contact = conversation.getJid().getDomain().toEscapedString();
        final String account = conversation.getAccount().getServer();
        if (Config.OMEMO_EXCEPTIONS.matchesContactDomain(contact) || Config.OMEMO_EXCEPTIONS.ACCOUNT_DOMAINS.contains(account)) {
            return false;
        }
        return conversation.isSingleOrPrivateAndNonAnonymous() || conversation.getBooleanAttribute(ATTRIBUTE_FORMERLY_PRIVATE_NON_ANONYMOUS, false);
    }

    public boolean hasMessagesLeftOnServer() {
        return messagesLeftOnServer;
    }


    public void setHasMessagesLeftOnServer(boolean value) {
        this.messagesLeftOnServer = value;
    }

    public void deleteMessage(Message message) {
        synchronized (this.messages) {
            this.messages.remove(message);
        }
    }

    public Message getFirstUnreadMessage() {
        Message first = null;
        synchronized (this.messages) {
            for (int i = messages.size() - 1; i >= 0; --i) {
                final Message message = messages.get(i);
                if (message.getSubject() != null && !message.isOOb() && (message.getRawBody() == null || message.getRawBody().isEmpty())) continue;
                if (message.isRead()) {
                    return first;
                } else {
                    first = message;
                }
            }
        }
        return first;
    }

    public String findMostRecentRemoteDisplayableId() {
        final boolean multi = mode == Conversation.MODE_MULTI;
        synchronized (this.messages) {
            for(final Message message : Lists.reverse(this.messages)) {
                if (message.getSubject() != null && !message.isOOb() && (message.getRawBody() == null || message.getRawBody().isEmpty())) continue;
                if (message.getStatus() == Message.STATUS_RECEIVED) {
                    final String serverMsgId = message.getServerMsgId();
                    if (serverMsgId != null && multi) {
                        return serverMsgId;
                    }
                    return message.getRemoteMsgId();
                }
            }
        }
        return null;
    }

    public int countFailedDeliveries() {
        int count = 0;
        synchronized (this.messages) {
            for(final Message message : this.messages) {
                if (message.getStatus() == Message.STATUS_SEND_FAILED) {
                    ++count;
                }
            }
        }
        return count;
    }

    public Message getLastEditableMessage() {
        synchronized (this.messages) {
            for(final Message message : Lists.reverse(this.messages)) {
                if (message.isEditable()) {
                    if (message.isGeoUri() || message.getType() != Message.TYPE_TEXT) {
                        return null;
                    }
                    return message;
                }
            }
        }
        return null;
    }


    public Message findUnsentMessageWithUuid(String uuid) {
        synchronized (this.messages) {
            for (final Message message : this.messages) {
                final int s = message.getStatus();
                if ((s == Message.STATUS_UNSEND || s == Message.STATUS_WAITING) && message.getUuid().equals(uuid)) {
                    return message;
                }
            }
        }
        return null;
    }

    public void findWaitingMessages(OnMessageFound onMessageFound) {
        final ArrayList<Message> results = new ArrayList<>();
        synchronized (this.messages) {
            for (Message message : this.messages) {
                if (message.getStatus() == Message.STATUS_WAITING) {
                    results.add(message);
                }
            }
        }
        for (Message result : results) {
            onMessageFound.onMessageFound(result);
        }
    }
    public void findResendAbleFailedMessage(OnMessageFound onMessageFound) {
        final ArrayList<Message> results = new ArrayList<>();
        synchronized (this.messages) {
            for (final Message message : this.messages) {
                if (message.getStatus() == Message.STATUS_SEND_FAILED && !message.isFileOrImage()) {
                    results.add(message);
                }
            }
        }
        for (final Message result : results) {
            onMessageFound.onMessageFound(result);
        }
    }

    public void findUnreadMessagesAndCalls(OnMessageFound onMessageFound) {
        final ArrayList<Message> results = new ArrayList<>();
        synchronized (this.messages) {
            for (final Message message : this.messages) {
                if (message.isRead()) {
                    continue;
                }
                results.add(message);
            }
        }
        for (final Message result : results) {
            onMessageFound.onMessageFound(result);
        }
    }

    public void findFailedMessagesWithFiles(final OnMessageFound onMessageFound) {
        final ArrayList<Message> results = new ArrayList<>();
        synchronized (this.messages) {
            for (final Message m : this.messages) {
                if (m.isFileOrImage() && m.getEncryption() != Message.ENCRYPTION_PGP) {
                    if (m.getStatus() == Message.STATUS_SEND_FAILED && !m.isFileDeleted() && m.needsUploading()) {
                        results.add(m);
                    }
                }
            }
        }
        for (Message result : results) {
            onMessageFound.onMessageFound(result);
        }
    }

    public void findDeletedMessages(final OnMessageFound onMessageFound) {
        final ArrayList<Message> results = new ArrayList<>();
        synchronized (this.messages) {
            for (final Message m : this.messages) {
                if (m.isFileDeleted()) {
                    results.add(m);
                }
            }
        }
        for (Message result : results) {
            onMessageFound.onMessageFound(result);
        }
    }

    public Message findMessageWithFileAndUuid(final String uuid) {
        synchronized (this.messages) {
            for (final Message message : this.messages) {
                final Transferable transferable = message.getTransferable();
                final boolean unInitiatedButKnownSize = MessageUtils.unInitiatedButKnownSize(message);
                if (message.getUuid().equals(uuid)
                        && message.getEncryption() != Message.ENCRYPTION_PGP
                        && (message.isFileOrImage() || message.treatAsDownloadable() || unInitiatedButKnownSize || (transferable != null && transferable.getStatus() != Transferable.STATUS_UPLOADING))) {
                    return message;
                }
            }
        }
        return null;
    }

    public Message findMessageWithUuid(final String uuid) {
        synchronized (this.messages) {
            for (final Message message : this.messages) {
                if (message.getUuid().equals(uuid)) {
                    return message;
                }
            }
        }
        return null;
    }

    public boolean markAsDeleted(final List<String> uuids) {
        boolean deleted = false;
        final PgpDecryptionService pgpDecryptionService = account.getPgpDecryptionService();
        synchronized (this.messages) {
            for (Message message : this.messages) {
                if (uuids.contains(message.getUuid())) {
                    message.setFileDeleted(true);
                    deleted = true;
                    if (message.getEncryption() == Message.ENCRYPTION_PGP && pgpDecryptionService != null) {
                        pgpDecryptionService.discard(message);
                    }
                }
            }
        }
        return deleted;
    }


    public boolean markAsChanged(final List<DatabaseBackend.FilePathInfo> files) {
        boolean changed = false;
        final PgpDecryptionService pgpDecryptionService = account.getPgpDecryptionService();
        synchronized (this.messages) {
            for (Message message : this.messages) {
                for (final DatabaseBackend.FilePathInfo file : files)
                    if (file.uuid.toString().equals(message.getUuid())) {
                        message.setFileDeleted(file.FileDeleted);
                        changed = true;
                        if (file.FileDeleted && message.getEncryption() == Message.ENCRYPTION_PGP && pgpDecryptionService != null) {
                            pgpDecryptionService.discard(message);
                        }
                    }
            }
        }
        return changed;
    }

    public void clearMessages() {
        synchronized (this.messages) {
            this.messages.clear();
        }
    }

    public boolean setIncomingChatState(ChatState state) {
        if (this.mIncomingChatState == state) {
            return false;
        }
        this.mIncomingChatState = state;
        return true;
    }

    public ChatState getIncomingChatState() {
        return this.mIncomingChatState;
    }

    public boolean setOutgoingChatState(ChatState state) {
        if (mode == MODE_SINGLE && !getContact().isSelf() || (isPrivateAndNonAnonymous() && getNextCounterpart() == null)) {
            if (this.mOutgoingChatState != state) {
                this.mOutgoingChatState = state;
                return true;
            }
        }
        return false;
    }

    public ChatState getOutgoingChatState() {
        return this.mOutgoingChatState;
    }

    public void trim() {
        synchronized (this.messages) {
            final int size = messages.size();
            int maxsize = Config.PAGE_SIZE * Config.MAX_NUM_PAGES;
            if (getAccount() != null && getAccount().getXmppConnection() != null && getAccount().getXmppConnection().getXmppConnectionService() != null) {
                SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getAccount().getXmppConnection().getXmppConnectionService());
                int pagesize = Integer.parseInt(pref.getString("pagesize", String.valueOf(Config.PAGE_SIZE)));
                int maxnumpages = Integer.parseInt(pref.getString("max_num_pages", String.valueOf(Config.MAX_NUM_PAGES)));
                maxsize = pagesize * maxnumpages;
            }
            if (size > maxsize) {
                List<Message> discards = this.messages.subList(0, size - maxsize);
                final PgpDecryptionService pgpDecryptionService = account.getPgpDecryptionService();
                if (pgpDecryptionService != null) {
                    pgpDecryptionService.discard(discards);
                }
                discards.clear();
                untieMessages();
            }
        }
    }

    public void findUnsentMessagesWithEncryption(int encryptionType, OnMessageFound onMessageFound) {
        synchronized (this.messages) {
            for (Message message : this.messages) {
                if ((message.getStatus() == Message.STATUS_UNSEND || message.getStatus() == Message.STATUS_WAITING)
                        && (message.getEncryption() == encryptionType)) {
                    onMessageFound.onMessageFound(message);
                }
            }
        }
    }


    public void findUnsentTextMessages(OnMessageFound onMessageFound) {
        final ArrayList<Message> results = new ArrayList<>();
        synchronized (this.messages) {
            for (Message message : this.messages) {
                if ((message.getType() == Message.TYPE_TEXT || message.hasFileOnRemoteHost()) && message.getStatus() == Message.STATUS_UNSEND) {
                    results.add(message);
                }
            }
        }
        for (Message result : results) {
            onMessageFound.onMessageFound(result);
        }
    }

    public Message findSentMessageWithUuidOrRemoteId(String id) {
        return findSentMessageWithUuidOrRemoteId(id, false, false);
    }

    public Message findSentMessageWithUuidOrRemoteId(String id, boolean ignorestatus, boolean withedits) {
        synchronized (this.messages) {
            for (Message message : this.messages) {

                if (id.equals(message.getUuid()) || ((message.getStatus() >= Message.STATUS_SEND || ignorestatus) && id.equals(message.getRemoteMsgId()))) {
                    return message;
                }

                if (withedits) {
                    for (Edit itm : message.edits) {
                        if (id.equals(itm.getEditedId())) {
                            return message;
                        }
                    }
                }
            }
        }
        return null;
    }

    public Message findMessageWithRemoteIdAndCounterpart(String id, Jid counterpart) {
        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                final Message message = messages.get(i);
                final Jid mcp = message.getCounterpart();
                if (mcp == null && counterpart != null) {
                    continue;
                }
                if (counterpart == null || mcp.equals(counterpart) || mcp.asBareJid().equals(counterpart)) {
                    final boolean idMatch = id.equals(message.getUuid()) || id.equals(message.getRemoteMsgId()) || message.remoteMsgIdMatchInEdit(id) || (getMode() == MODE_MULTI && id.equals(message.getServerMsgId()));
                    if (idMatch) return message;
                }
            }
        }
        return null;
    }

    public void setReplyTo(Message m) {
        this.replyTo = m;
    }

    public Message getReplyTo() {
        return this.replyTo;
    }

    public Message findSentMessageWithUuid(String id) {
        synchronized (this.messages) {
            for (Message message : this.messages) {
                if (id.equals(message.getUuid())) {
                    return message;
                }
            }
        }
        return null;
    }

    public Message findMessageWithRemoteId(String id, Jid counterpart) {
        synchronized (this.messages) {
            for (Message message : this.messages) {
                if (counterpart.equals(message.getCounterpart())
                        && (id.equals(message.getRemoteMsgId()) || id.equals(message.getUuid()))) {
                    return message;
                }
            }
        }
        return null;
    }

    public Message findReceivedWithRemoteId(final String id) {
        synchronized (this.messages) {
            for (final Message message : this.messages) {
                if (message.getStatus() == Message.STATUS_RECEIVED && id.equals(message.getRemoteMsgId())) {
                    return message;
                }
            }
        }
        return null;
    }

    public Message findMessageWithServerMsgId(String id) {
        synchronized (this.messages) {
            for (Message message : this.messages) {
                if (id != null && id.equals(message.getServerMsgId())) {
                    return message;
                }
            }
        }
        return null;
    }

    public boolean hasMessageWithCounterpart(Jid counterpart) {
        synchronized (this.messages) {
            for (Message message : this.messages) {
                if (counterpart.equals(message.getCounterpart())) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<Message> filterDuplicates(List<Message> list) {
        HashMap<String, Message> items = new HashMap<String, Message>();
        for (Message item : list) {
            items.put(item.getUuid(), item);
        }

        ArrayList<Message> result = new ArrayList<Message>(items.values());
        Collections.sort(result, (o1, o2) -> {
            if (o1.getTimeSent() < o2.getTimeSent())
                return -1;
            if (o1.getTimeSent() > o2.getTimeSent())
                return 1;
            return 0;
        });
        return result;
    }

    public Message findMessageReactingTo(String id, Jid reactor) {
        if (id == null) return null;

        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                final Message message = messages.get(i);
                if (reactor == null && message.getStatus() < Message.STATUS_SEND) continue;
                if (reactor != null && message.getCounterpart() == null) continue;
                if (reactor != null && !(message.getCounterpart().equals(reactor) || message.getCounterpart().asBareJid().equals(reactor))) continue;

                final Element r = message.getReactions();
                if (r != null && r.getAttribute("id") != null && id.equals(r.getAttribute("id"))) {
                    return message;
                }
            }
        }
        return null;
    }

    public List<Message> findMessagesBy(MucOptions.User user) {
        List<Message> result = new ArrayList<>();
        synchronized (this.messages) {
            for (Message m : this.messages) {
                // occupant id?
                final Jid trueCp = m.getTrueCounterpart();
                if (m.getCounterpart().equals(user.getFullJid()) || (trueCp != null && trueCp.equals(user.getRealJid()))) {
                    result.add(m);
                }
            }
        }
        return result;
    }

    public Set<String> findReactionsTo(String id, Jid reactor) {
        Set<String> reactionEmoji = new HashSet<>();
        Message reactM = findMessageReactingTo(id, reactor);
        Element reactions = reactM == null ? null : reactM.getReactions();
        if (reactions != null) {
            for (Element el : reactions.getChildren()) {
                if (el.getName().equals("reaction") && el.getNamespace().equals("urn:xmpp:reactions:0")) {
                    reactionEmoji.add(el.getContent());
                }
            }
        }
        return reactionEmoji;
    }


    public Set<Message> findReplies(String id) {
        Set<Message> replies = new HashSet<>();
        if (id == null) return replies;

        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                final Message message = messages.get(i);
                if (id.equals(message.getServerMsgId())) break;
                if (id.equals(message.getUuid())) break;
                final Element r = message.getReply();
                if (r != null && r.getAttribute("id") != null && id.equals(r.getAttribute("id"))) {
                    replies.add(message);
                }
            }
        }
        return replies;
    }
    public long loadMoreTimestamp() {
        if (messages.size() < 1) return 0;
        if (getLockThread() && messages.size() > 5000) return 0;

        if (messages.get(0).getType() == Message.TYPE_STATUS && messages.size() >= 2) {
            return messages.get(1).getTimeSent();
        } else {
            return messages.get(0).getTimeSent();
        }
    }

    public void populateWithMessages(final List<Message> messages, XmppConnectionService xmppConnectionService) {
        synchronized (this.messages) {
            messages.clear();
            messages.addAll(this.messages);
            threads.clear();
        }
        Set<String> extraIds = new HashSet<>();
        for (ListIterator<Message> iterator = messages.listIterator(messages.size()); iterator.hasPrevious(); ) {
            Message m = iterator.previous();
            final Element mthread = m.getThread();
            if (mthread != null) {
                Thread thread = threads.get(mthread.getContent());
                if (thread == null) {
                    thread = new Thread(mthread.getContent());
                    threads.put(mthread.getContent(), thread);
                }
                if (thread.subject == null && (m.getSubject() != null && !m.isOOb() && (m.getRawBody() == null || m.getRawBody().isEmpty()))) {
                    thread.subject = m;
                } else {
                    if (thread.last == null) thread.last = m;
                    thread.first = m;
                }
            }
            if (m.wasMergedIntoPrevious(xmppConnectionService) || (m.getSubject() != null && !m.isOOb() && (m.getRawBody() == null || m.getRawBody().isEmpty())) || (getLockThread() && !extraIds.contains(m.replyId()) && (mthread == null || !mthread.getContent().equals(getThread() == null ? "" : getThread().getContent())))) {
                iterator.remove();
            } else if (getLockThread() && mthread != null) {
                Element reply = m.getReply();
                if (reply != null && reply.getAttribute("id") != null) extraIds.add(reply.getAttribute("id"));
                Element reactions = m.getReactions();
                if (reactions != null && reactions.getAttribute("id") != null) extraIds.add(reactions.getAttribute("id"));
            }
        }
    }

    public Thread getThread(String id) {
        return threads.get(id);
    }

    public List<Thread> recentThreads() {
        final ArrayList<Thread> recent = new ArrayList<>();
        recent.addAll(threads.values());
        recent.sort((a, b) -> b.getLastTime() == a.getLastTime() ? 0 : (b.getLastTime() > a.getLastTime() ? 1 : -1));
        return recent.size() < 5 ? recent : recent.subList(0, 5);
    }

    @Override
    public boolean isBlocked() {
        return getContact().isBlocked();
    }

    @Override
    public boolean isDomainBlocked() {
        return getContact().isDomainBlocked();
    }

    @Override
    public Jid getBlockedJid() {
        return getContact().getBlockedJid();
    }

    public String getLastReceivedOtrMessageId() {
        return this.mLastReceivedOtrMessageId;
    }

    public void setLastReceivedOtrMessageId(String id) {
        this.mLastReceivedOtrMessageId = id;
    }


    public int countMessages() {
        synchronized (this.messages) {
            return this.messages.size();
        }
    }

    public String getFirstMamReference() {
        return this.mFirstMamReference;
    }

    public void setFirstMamReference(String reference) {
        this.mFirstMamReference = reference;
    }

    public void setLastClearHistory(long time, String reference) {
        if (reference != null) {
            setAttribute(ATTRIBUTE_LAST_CLEAR_HISTORY, String.valueOf(time) + ":" + reference);
        } else {
            setAttribute(ATTRIBUTE_LAST_CLEAR_HISTORY, time);
        }
    }
    public Element getThread() {
        return this.thread;
    }

    public void setThread(Element thread) {
        this.thread = thread;
    }

    public MamReference getLastClearHistory() {
        return MamReference.fromAttribute(getAttribute(ATTRIBUTE_LAST_CLEAR_HISTORY));
    }

    public List<Jid> getAcceptedCryptoTargets() {
        if (mode == MODE_SINGLE) {
            return Collections.singletonList(getJid().asBareJid());
        } else {
            return getJidListAttribute(ATTRIBUTE_CRYPTO_TARGETS);
        }
    }

    public void setAcceptedCryptoTargets(List<Jid> acceptedTargets) {
        setAttribute(ATTRIBUTE_CRYPTO_TARGETS, acceptedTargets);
    }

    public boolean setCorrectingMessage(Message correctingMessage) {
        setAttribute(ATTRIBUTE_CORRECTING_MESSAGE, correctingMessage == null ? null : correctingMessage.getUuid());
        return correctingMessage == null && draftMessage != null;
    }

    public Message getCorrectingMessage() {
        final String uuid = getAttribute(ATTRIBUTE_CORRECTING_MESSAGE);
        return uuid == null ? null : findSentMessageWithUuid(uuid);
    }

    public boolean withSelf() {
        return getContact().isSelf();
    }

    @Override
    public int compareTo(@NonNull Conversation another) {
        return ComparisonChain.start()
                .compareFalseFirst(another.getBooleanAttribute(ATTRIBUTE_PINNED_ON_TOP, false), getBooleanAttribute(ATTRIBUTE_PINNED_ON_TOP,false))
                .compareFalseFirst(another.getAccount().isEnabled(), getAccount().isEnabled())
                .compare(another.getSortableTime(), getSortableTime())
                .result();
    }

    public long getSortableTime() {
        Draft draft = getDraft();
        long messageTime = getLatestMessage().getTimeReceived();
        if (draft == null) {
            return messageTime;
        } else {
            return Math.max(messageTime, draft.getTimestamp());
        }
    }

    public String getDraftMessage() {
        return draftMessage;
    }

    public void setDraftMessage(String draftMessage) {
        this.draftMessage = draftMessage;
    }

    public void setLockThread(boolean flag) {
        this.lockThread = flag;
        if (flag) setUserSelectedThread(true);
    }

    public boolean getLockThread() {
        return this.lockThread;
    }

    public void setUserSelectedThread(boolean flag) {
        this.userSelectedThread = flag;
    }

    public boolean getUserSelectedThread() {
        return this.userSelectedThread;
    }

    public boolean isRead() {
        return (this.messages.isEmpty()) || this.messages.get(this.messages.size() - 1).isRead();
    }

    public List<Message> markRead(final String upToUuid) {
        final ImmutableList.Builder<Message> unread = new ImmutableList.Builder<>();
        synchronized (this.messages) {
            for (final Message message : this.messages) {
                if (!message.isRead()) {
                    message.markRead();
                    unread.add(message);
                }
                if (message.getUuid().equals(upToUuid)) {
                    return unread.build();
                }
            }
        }
        return unread.build();
    }

    public Message getLatestMessage() {
        synchronized (this.messages) {
            if (this.messages.isEmpty()) {
                Message message = new Message(this, "", Message.ENCRYPTION_NONE);
                message.setType(Message.TYPE_STATUS);
                message.setTime(Math.max(getCreated(), getLastClearHistory().getTimestamp()));
                message.setTimeReceived(Math.max(getCreated(), getLastClearHistory().getTimestamp()));
                return message;
            } else {
                return this.messages.get(this.messages.size() - 1);
            }
        }
    }

    public @NonNull
    CharSequence getName() {
        if (getMode() == MODE_MULTI) {
            final String roomName = getMucOptions().getName();
            final String subject = getMucOptions().getSubject();
            final Bookmark bookmark = getBookmark();
            final String bookmarkName = bookmark != null ? bookmark.getBookmarkName() : null;
            if (printableValue(roomName)) {
                return roomName;
            } else if (printableValue(subject)) {
                return subject;
            } else if (printableValue(bookmarkName, false)) {
                return bookmarkName;
            } else {
                final String generatedName = getMucOptions().createNameFromParticipants();
                if (printableValue(generatedName)) {
                    return generatedName;
                } else {
                    return contactJid.getLocal() != null ? contactJid.getLocal() : contactJid;
                }
            }
        } else {
            return this.getContact().getDisplayName();
        }
    }

    public String getAccountUuid() {
        return this.accountUuid;
    }

    public Account getAccount() {
        return this.account;
    }

    public void setAccount(final Account account) {
        this.account = account;
    }

    public Contact getContact() {
        return this.account.getRoster().getContact(this.contactJid);
    }

    @Override
    public Jid getJid() {
        return this.contactJid;
    }

    public int getStatus() {
        return this.status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public long getCreated() {
        return this.created;
    }

    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(UUID, uuid);
        values.put(NAME, name);
        values.put(CONTACT, contactUuid);
        values.put(ACCOUNT, accountUuid);
        values.put(CONTACTJID, contactJid.toString());
        values.put(CREATED, created);
        values.put(STATUS, status);
        values.put(MODE, mode);
        synchronized (this.attributes) {
            values.put(ATTRIBUTES, attributes.toString());
        }
        return values;
    }

    public int getMode() {
        return this.mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }
    public SessionImpl startOtrSession(String presence, boolean sendStart) {
        if (this.otrSession != null) {
            return this.otrSession;
        } else {
            final SessionID sessionId = new SessionID(this.getJid().asBareJid().toString(),
                    presence,
                    "xmpp");
            this.otrSession = new SessionImpl(sessionId, getAccount().getOtrService());
            try {
                if (sendStart) {
                    this.otrSession.startSession();
                    return this.otrSession;
                }
                return this.otrSession;
            } catch (OtrException e) {
                return null;
            }
        }

    }

    public SessionImpl getOtrSession() {
        return this.otrSession;
    }

    public void resetOtrSession() {
        this.otrFingerprint = null;
        this.otrSession = null;
        this.mSmp.hint = null;
        this.mSmp.secret = null;
        this.mSmp.status = Smp.STATUS_NONE;
    }

    public Smp smp() {
        return mSmp;
    }

    public boolean startOtrIfNeeded() {
        if (this.otrSession != null && this.otrSession.getSessionStatus() != SessionStatus.ENCRYPTED) {
            try {
                this.otrSession.startSession();
                return true;
            } catch (OtrException e) {
                this.resetOtrSession();
                return false;
            }
        } else {
            return true;
        }
    }

    public boolean endOtrIfNeeded() {
        if (this.otrSession != null) {
            if (this.otrSession.getSessionStatus() == SessionStatus.ENCRYPTED) {
                try {
                    this.otrSession.endSession();
                    this.resetOtrSession();
                    return true;
                } catch (OtrException e) {
                    this.resetOtrSession();
                    return false;
                }
            } else {
                this.resetOtrSession();
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean hasValidOtrSession() {
        return this.otrSession != null;
    }

    public synchronized String getOtrFingerprint() {
        if (this.otrFingerprint == null) {
            try {
                if (getOtrSession() == null || getOtrSession().getSessionStatus() != SessionStatus.ENCRYPTED) {
                    return null;
                }
                DSAPublicKey remotePubKey = (DSAPublicKey) getOtrSession().getRemotePublicKey();
                this.otrFingerprint = getAccount().getOtrService().getFingerprint(remotePubKey).toLowerCase(Locale.US);
            } catch (final OtrCryptoException ignored) {
                return null;
            } catch (final UnsupportedOperationException ignored) {
                return null;
            }
        }
        return this.otrFingerprint;
    }

    public boolean verifyOtrFingerprint() {
        final String fingerprint = getOtrFingerprint();
        if (fingerprint != null) {
            getContact().addOtrFingerprint(fingerprint);
            return true;
        } else {
            return false;
        }
    }

    public boolean isOtrFingerprintVerified() {
        return getContact().getOtrFingerprints().contains(getOtrFingerprint());
    }

    public class Smp {
        public static final int STATUS_NONE = 0;
        public static final int STATUS_CONTACT_REQUESTED = 1;
        public static final int STATUS_WE_REQUESTED = 2;
        public static final int STATUS_FAILED = 3;
        public static final int STATUS_VERIFIED = 4;

        public String secret = null;
        public String hint = null;
        public int status = 0;
    }

    /**
     * short for is Private and Non-anonymous
     */
    public boolean isSingleOrPrivateAndNonAnonymous() {
        return mode == MODE_SINGLE || isPrivateAndNonAnonymous();
    }

    public boolean isPrivateAndNonAnonymous() {
        return getMucOptions().isPrivateAndNonAnonymous();
    }

    public synchronized MucOptions getMucOptions() {
        if (this.mucOptions == null) {
            this.mucOptions = new MucOptions(this);
        }
        return this.mucOptions;
    }

    public void resetMucOptions() {
        this.mucOptions = null;
    }

    public void setContactJid(final Jid jid) {
        this.contactJid = jid;
    }

    public Jid getNextCounterpart() {
        return this.nextCounterpart;
    }

    public void setNextCounterpart(Jid jid) {
        this.nextCounterpart = jid;
    }

    public int getNextEncryption() {
        if (!Config.supportOmemo() && !Config.supportOpenPgp() && !Config.supportOtr()) {
            return Message.ENCRYPTION_NONE;
        }
        if (OmemoSetting.isAlways()) {
            return suitableForOmemoByDefault(this) ? Message.ENCRYPTION_AXOLOTL : Message.ENCRYPTION_NONE;
        }
        if (OmemoSetting.isNever()) {
            return Message.ENCRYPTION_NONE;
        }
        final int defaultEncryption;
        if (suitableForOmemoByDefault(this)) {
            defaultEncryption = OmemoSetting.getEncryption();
        } else {
            defaultEncryption = Message.ENCRYPTION_NONE;
        }
        int encryption = this.getIntAttribute(ATTRIBUTE_NEXT_ENCRYPTION, defaultEncryption);
        if (encryption < 0) {
            return defaultEncryption;
        } else if (encryption == Message.ENCRYPTION_OTR) {
            return encryption;
        } else {
            return encryption;
        }
    }

    public boolean setNextEncryption(int encryption) {
        return this.setAttribute(ATTRIBUTE_NEXT_ENCRYPTION, encryption);
    }

    public String getNextMessage() {
        final String nextMessage = getAttribute(ATTRIBUTE_NEXT_MESSAGE);
        return nextMessage == null ? "" : nextMessage;
    }

    public boolean smpRequested() {
        return smp().status == Smp.STATUS_CONTACT_REQUESTED;
    }


    public @Nullable
    Draft getDraft() {
        final long timestamp = getLongAttribute(ATTRIBUTE_NEXT_MESSAGE_TIMESTAMP, 0);
        if (timestamp > getLatestMessage().getTimeSent()) {
            String message = getAttribute(ATTRIBUTE_NEXT_MESSAGE);
            if (!TextUtils.isEmpty(message) && timestamp != 0) {
                return new Draft(message, timestamp);
            }
        }
        return null;
    }

    public boolean setNextMessage(final String input) {
        final String message = input == null || input.trim().isEmpty() ? null : input;
        boolean changed = !getNextMessage().equals(message);
        this.setAttribute(ATTRIBUTE_NEXT_MESSAGE, message);
        if (changed) {
            this.setAttribute(ATTRIBUTE_NEXT_MESSAGE_TIMESTAMP, message == null ? 0 : System.currentTimeMillis());
        }
        return changed;
    }

    public void setSymmetricKey(byte[] key) {
        this.symmetricKey = key;
    }

    public byte[] getSymmetricKey() {
        return this.symmetricKey;
    }


    public Bookmark getBookmark() {
        return this.account.getBookmark(this.contactJid);
    }

    public Message findDuplicateMessage(Message message, boolean withremoteid) {
        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                if (this.messages.get(i).similar(message)) {
                    return this.messages.get(i);
                }
                if (withremoteid) {
                    if (this.messages.get(i).remoteMsgId != null && message.getRemoteMsgId() != null && this.messages.get(i).remoteMsgId.equals(message.getRemoteMsgId())) {
                        return this.messages.get(i);
                    }
                }
            }
        }
        return null;
    }

    public boolean hasDuplicateMessage(Message message) {
        return findDuplicateMessage(message) != null;
    }

    public Message findDuplicateMessage(Message message) {
        return findDuplicateMessage(message, false);
    }

    public boolean hasDuplicateMessage(Message message, boolean withremoteid) {
        return findDuplicateMessage(message, withremoteid) != null;
    }

    public Message findMessageWithUuidOrRemoteId(final String id) {
        synchronized (this.messages) {
            for (final Message message : this.messages) {
                if (message.getRemoteMsgId() != null && message.getRemoteMsgId().equals(id) || message.getUuid().equals(id)) {
                    return message;
                }
            }
        }
        return null;
    }

    public Message findSentMessageWithBody(String body) {
        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                Message message = this.messages.get(i);
                if (message.getStatus() == Message.STATUS_UNSEND || message.getStatus() == Message.STATUS_SEND) {
                    String otherBody;
                    if (message.hasFileOnRemoteHost()) {
                        otherBody = message.getFileParams().url;
                    } else {
                        otherBody = message.body;
                    }
                    if (otherBody != null && otherBody.equals(body)) {
                        return message;
                    }
                }
            }
            return null;
        }
    }

    public Message findRtpSession(final String sessionId, final int s) {
        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                final Message message = this.messages.get(i);
                if ((message.getStatus() == s) && (message.getType() == Message.TYPE_RTP_SESSION) && sessionId.equals(message.getRemoteMsgId())) {
                    return message;
                }
            }
        }
        return null;
    }

    public boolean possibleDuplicate(final String serverMsgId, final String remoteMsgId) {
        if (serverMsgId == null || remoteMsgId == null) {
            return false;
        }
        synchronized (this.messages) {
            for (Message message : this.messages) {
                if (serverMsgId.equals(message.getServerMsgId()) || remoteMsgId.equals(message.getRemoteMsgId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public MamReference getLastMessageTransmitted() {
        final MamReference lastClear = getLastClearHistory();
        MamReference lastReceived = new MamReference(0);
        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                final Message message = this.messages.get(i);
                if (message.isPrivateMessage()) {
                    continue; //it's unsafe to use private messages as anchor. They could be coming from user archive
                }
                if (message.getStatus() == Message.STATUS_RECEIVED || message.isCarbon() || message.getServerMsgId() != null) {
                    lastReceived = new MamReference(message.getTimeSent(), message.getServerMsgId());
                    break;
                }
            }
        }
        return MamReference.max(lastClear, lastReceived);
    }

    public void setMutedTill(long value) {
        this.setAttribute(ATTRIBUTE_MUTED_TILL, String.valueOf(value));
    }

    public boolean isMuted() {
        return System.currentTimeMillis() < this.getLongAttribute(ATTRIBUTE_MUTED_TILL, 0);
    }

    public boolean alwaysNotify() {
        return mode == MODE_SINGLE || getBooleanAttribute(ATTRIBUTE_ALWAYS_NOTIFY, Config.ALWAYS_NOTIFY_BY_DEFAULT || isPrivateAndNonAnonymous());
    }

    public boolean notifyReplies() {
        return alwaysNotify() || getBooleanAttribute(ATTRIBUTE_NOTIFY_REPLIES, false);
    }

    public boolean setAttribute(String key, boolean value) {
        return setAttribute(key, String.valueOf(value));
    }

    private boolean setAttribute(String key, long value) {
        return setAttribute(key, Long.toString(value));
    }

    private boolean setAttribute(String key, int value) {
        return setAttribute(key, String.valueOf(value));
    }

    public boolean setAttribute(String key, String value) {
        synchronized (this.attributes) {
            try {
                if (value == null) {
                    if (this.attributes.has(key)) {
                        this.attributes.remove(key);
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    final String prev = this.attributes.optString(key, null);
                    this.attributes.put(key, value);
                    return !value.equals(prev);
                }
            } catch (JSONException e) {
                throw new AssertionError(e);
            }
        }
    }

    public boolean setAttribute(String key, List<Jid> jids) {
        JSONArray array = new JSONArray();
        for (Jid jid : jids) {
            array.put(jid.asBareJid().toString());
        }
        synchronized (this.attributes) {
            try {
                this.attributes.put(key, array);
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    public String getAttribute(String key) {
        synchronized (this.attributes) {
            return this.attributes.optString(key, null);
        }
    }

    private List<Jid> getJidListAttribute(String key) {
        ArrayList<Jid> list = new ArrayList<>();
        synchronized (this.attributes) {
            try {
                JSONArray array = this.attributes.getJSONArray(key);
                for (int i = 0; i < array.length(); ++i) {
                    try {
                        list.add(Jid.of(array.getString(i)));
                    } catch (IllegalArgumentException e) {
                        //ignored
                    }
                }
            } catch (JSONException e) {
                //ignored
            }
        }
        return list;
    }

    private int getIntAttribute(String key, int defaultValue) {
        String value = this.getAttribute(key);
        if (value == null) {
            return defaultValue;
        } else {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }

    public long getLongAttribute(String key, long defaultValue) {
        String value = this.getAttribute(key);
        if (value == null) {
            return defaultValue;
        } else {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }

    public boolean getBooleanAttribute(String key, boolean defaultValue) {
        String value = this.getAttribute(key);
        if (value == null) {
            return defaultValue;
        } else {
            return Boolean.parseBoolean(value);
        }
    }

    public void add(Message message) {
        synchronized (this.messages) {
            this.messages.add(message);
        }
    }

    public void prepend(int offset, Message message) {
        synchronized (this.messages) {
            this.messages.add(Math.min(offset, this.messages.size()), message);
        }
    }

    public void addAll(int index, List<Message> messages) {
        synchronized (this.messages) {
            this.messages.addAll(index, messages);
        }
        account.getPgpDecryptionService().decrypt(messages);
    }

    public void expireOldMessages(long timestamp) {
        synchronized (this.messages) {
            for (ListIterator<Message> iterator = this.messages.listIterator(); iterator.hasNext(); ) {
                if (iterator.next().getTimeSent() < timestamp) {
                    iterator.remove();
                }
            }
            untieMessages();
        }
    }

    public void sort() {
        synchronized (this.messages) {
            Collections.sort(this.messages, (left, right) -> {
                if (left.getTimeSent() < right.getTimeSent()) {
                    return -1;
                } else if (left.getTimeSent() > right.getTimeSent()) {
                    return 1;
                } else {
                    return 0;
                }
            });
            untieMessages();
        }
    }

    private void untieMessages() {
        for (Message message : this.messages) {
            message.untie();
        }
    }

    public int unreadCount() {
        synchronized (this.messages) {
            int count = 0;
            for(final Message message : Lists.reverse(this.messages)) {
                if (message.getSubject() != null && !message.isOOb() && (message.getRawBody() == null || message.getRawBody().isEmpty())) continue;
                if (message.isRead()) {
                    if (message.getType() == Message.TYPE_RTP_SESSION) {
                        continue;
                    }
                    return count;
                }
                ++count;
            }
            return count;
        }
    }

    public int failedCount() {
        synchronized (this.messages) {
            int count = 0;
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                Message message = this.messages.get(i);
                if ((message.getType() == Message.TYPE_IMAGE || message.getType() == Message.TYPE_FILE) && message.getEncryption() != Message.ENCRYPTION_PGP) {
                    if (message.getStatus() == Message.STATUS_SEND_FAILED && !message.isFileDeleted() && message.needsUploading()) {
                        ++count;
                    }
                }
            }
            return count;
        }
    }

    public int receivedMessagesCount() {
        int count = 0;
        synchronized (this.messages) {
            for (Message message : messages) {
                if (message.getSubject() != null && !message.isOOb() && (message.getRawBody() == null || message.getRawBody().isEmpty())) continue;
                if (message.getStatus() == Message.STATUS_RECEIVED) {
                    ++count;
                }
            }
        }
        return count;
    }

    public int sentMessagesCount() {
        int count = 0;
        synchronized (this.messages) {
            for (Message message : messages) {
                if (message.getStatus() != Message.STATUS_RECEIVED) {
                    ++count;
                }
            }
        }
        return count;
    }

    public boolean canInferPresence() {
        final Contact contact = getContact();
        if (contact != null && contact.canInferPresence()) return true;
        return sentMessagesCount() > 0;
    }

    public boolean isWithStranger() {
        final Contact contact = getContact();
        return mode == MODE_SINGLE
                && !contact.isOwnServer()
                && !contact.showInContactList()
                && !contact.isSelf()
                && !(contact.getJid().isDomainJid() && JidHelper.isQuicksyDomain(contact.getJid()))
                && sentMessagesCount() == 0;
    }

    public int getReceivedMessagesCountSinceUuid(String uuid) {
        if (uuid == null) {
            return 0;
        }
        int count = 0;
        synchronized (this.messages) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                final Message message = messages.get(i);
                if (uuid.equals(message.getUuid())) {
                    return count;
                }
                if (message.getStatus() <= Message.STATUS_RECEIVED) {
                    ++count;
                }
            }
        }
        return 0;
    }

    @Override
    public int getAvatarBackgroundColor() {
        return UIHelper.getColorForName(getName().toString());
    }

    @Override
    public String getAvatarName() {
        return getName().toString();
    }

    public void setCurrentTab(int tab) {
        mCurrentTab = tab;
    }

    public int getCurrentTab() {
        if (xmppConnectionService != null && xmppConnectionService.getBooleanPreference("jump_to_commands_tab", R.bool.jump_to_commands_tab)) {
            if (mCurrentTab >= 0) return mCurrentTab;

            if (!isRead() || getContact().resourceWhichSupport(Namespace.COMMANDS) == null) {
                return 0;
            }

            return 1;
        } else return 0;
    }

    public void refreshSessions() {
        pagerAdapter.refreshSessions();
    }

    public void startWebxdc(WebxdcPage page) {
        pagerAdapter.startWebxdc(page);
    }

    public void webxdcRealtimeData(final Element thread, final String base64) {
        pagerAdapter.webxdcRealtimeData(thread, base64);
    }

    public void startCommand(Element command, XmppConnectionService xmppConnectionService) {
        pagerAdapter.startCommand(command, xmppConnectionService);
    }

    public void startMucConfig(XmppConnectionService xmppConnectionService) {
        pagerAdapter.startMucConfig(xmppConnectionService);
    }

    public boolean switchToSession(final String node) {
        return pagerAdapter.switchToSession(node);
    }

    public void setupViewPager(ViewPager pager, TabLayout tabs, boolean onboarding, Conversation oldConversation) {
        pagerAdapter.setupViewPager(pager, tabs, onboarding, oldConversation);
    }

    public void showViewPager() {
        pagerAdapter.show();
    }

    public void hideViewPager() {
        pagerAdapter.hide();
    }

    public void setDisplayState(final String stanzaId) {
        this.displayState = stanzaId;
    }

    public String getDisplayState() {
        return this.displayState;
    }

    public interface OnMessageFound {
        void onMessageFound(final Message message);
    }

    public static class Draft {
        private final String message;
        private final long timestamp;

        private Draft(String message, long timestamp) {
            this.message = message;
            this.timestamp = timestamp;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getMessage() {
            return message;
        }
    }

    public class ConversationPagerAdapter extends PagerAdapter {
        protected ViewPager mPager = null;
        protected TabLayout mTabs = null;
        ArrayList<ConversationPage> sessions = null;
        protected View page1 = null;
        protected View page2 = null;
        protected boolean mOnboarding = false;

        public void setupViewPager(ViewPager pager, TabLayout tabs, boolean onboarding, Conversation oldConversation) {
            mPager = pager;
            mTabs = tabs;
            mOnboarding = onboarding;

            if (oldConversation != null) {
                oldConversation.pagerAdapter.mPager = null;
                oldConversation.pagerAdapter.mTabs = null;
            }

            if (mPager == null) {
                page1 = null;
                page2 = null;
                return;
            }
            if (sessions != null) show();

            if (pager.getChildAt(0) != null) page1 = pager.getChildAt(0);
            if (pager.getChildAt(1) != null) page2 = pager.getChildAt(1);
            if (page2 != null && page2.findViewById(R.id.commands_view) == null) {
                page1 = null;
                page2 = null;
            }
            if (page1 == null) page1 = oldConversation.pagerAdapter.page1;
            if (page2 == null) page2 = oldConversation.pagerAdapter.page2;
            if (page1 == null || page2 == null) {
                throw new IllegalStateException("page1 or page2 were not present as child or in model?");
            }
            pager.removeView(page1);
            pager.removeView(page2);
            pager.setAdapter(this);
            tabs.setupWithViewPager(mPager);
            pager.post(() -> pager.setCurrentItem(getCurrentTab()));

            mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                public void onPageScrollStateChanged(int state) { }
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) { }

                public void onPageSelected(int position) {
                    setCurrentTab(position);
                }
            });
        }

        public void show() {
            if (sessions == null) {
                sessions = new ArrayList<>();
                notifyDataSetChanged();
            }
            if (!mOnboarding && mTabs != null) mTabs.setVisibility(View.VISIBLE);
        }

        public void hide() {
            if (sessions != null && !sessions.isEmpty()) return; // Do not hide during active session
            if (mPager != null) mPager.setCurrentItem(0);
            if (mTabs != null) mTabs.setVisibility(View.GONE);
            sessions = null;
            notifyDataSetChanged();
        }

        public void refreshSessions() {
            if (sessions == null) return;

            for (ConversationPage session : sessions) {
                session.refresh();
            }
        }

        public void webxdcRealtimeData(final Element thread, final String base64) {
            if (sessions == null) return;

            for (ConversationPage session : sessions) {
                if (session instanceof WebxdcPage) {
                    if (((WebxdcPage) session).threadMatches(thread)) {
                        ((WebxdcPage) session).realtimeData(base64);
                    }
                }
            }
        }

        public void startWebxdc(WebxdcPage page) {
            show();
            sessions.add(page);
            notifyDataSetChanged();
            if (mPager != null) mPager.setCurrentItem(getCount() - 1);
        }

        public void startCommand(Element command, XmppConnectionService xmppConnectionService) {
            show();
            CommandSession session = new CommandSession(command.getAttribute("name"), command.getAttribute("node"), xmppConnectionService);

            final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
            packet.setTo(command.getAttributeAsJid("jid"));
            final Element c = packet.addChild("command", Namespace.COMMANDS);
            c.setAttribute("node", command.getAttribute("node"));
            c.setAttribute("action", "execute");

            final TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    if (getAccount().getStatus() != Account.State.ONLINE) {
                        final TimerTask self = this;
                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                self.run();
                            }
                        }, 1000);
                    } else {
                        xmppConnectionService.sendIqPacket(getAccount(), packet, (a, iq) -> {
                            session.updateWithResponse(iq);
                        }, 120L);
                    }
                }
            };

            task.run();

            sessions.add(session);
            notifyDataSetChanged();
            if (mPager != null) mPager.setCurrentItem(getCount() - 1);
        }

        public void startMucConfig(XmppConnectionService xmppConnectionService) {
            MucConfigSession session = new MucConfigSession(xmppConnectionService);
            final IqPacket packet = new IqPacket(IqPacket.TYPE.GET);
            packet.setTo(Conversation.this.getJid().asBareJid());
            packet.addChild("query", "http://jabber.org/protocol/muc#owner");
            Locale currentLocale = Locale.getDefault();

            if (currentLocale.getLanguage().equals(new Locale("ru").getLanguage())) {
                packet.setAttribute("xml:lang", "ru");
            }

            final TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    if (getAccount().getStatus() != Account.State.ONLINE) {
                        final TimerTask self = this;
                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                self.run();
                            }
                        }, 1000);
                    } else {
                        xmppConnectionService.sendIqPacket(getAccount(), packet, (a, iq) -> {
                            session.updateWithResponse(iq);
                        }, 120L);
                    }
                }
            };
            task.run();

            sessions.add(session);
            notifyDataSetChanged();
            if (mPager != null) mPager.setCurrentItem(getCount() - 1);
        }

        public void removeSession(ConversationPage session) {
            sessions.remove(session);
            notifyDataSetChanged();
            if (session instanceof WebxdcPage) mPager.setCurrentItem(0);
        }

        public boolean switchToSession(final String node) {
            if (sessions == null) return false;

            int i = 0;
            for (ConversationPage session : sessions) {
                if (session.getNode().equals(node)) {
                    if (mPager != null) mPager.setCurrentItem(i + 2);
                    return true;
                }
                i++;
            }

            return false;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            if (position == 0) {
                if (page1 != null && page1.getParent() != null) {
                    ((ViewGroup) page1.getParent()).removeView(page1);
                }
                container.addView(page1);
                return page1;
            }
            if (position == 1) {
                if (page2 != null && page2.getParent() != null) {
                    ((ViewGroup) page2.getParent()).removeView(page2);
                }
                container.addView(page2);
                return page2;
            }

            if (position-2 > sessions.size()) return null;
            ConversationPage session = sessions.get(position-2);
            View v = session.inflateUi(container.getContext(), (s) -> removeSession(s));
            if (v != null && v.getParent() != null) {
                ((ViewGroup) v.getParent()).removeView(v);
            }
            container.addView(v);
            return session;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, Object o) {
            if (position < 2) {
                container.removeView((View) o);
                return;
            }

            container.removeView(((ConversationPage) o).getView());
        }

        @Override
        public int getItemPosition(Object o) {
            if (mPager != null) {
                if (o == page1) return PagerAdapter.POSITION_UNCHANGED;
                if (o == page2) return PagerAdapter.POSITION_UNCHANGED;
            }

            int pos = sessions == null ? -1 : sessions.indexOf(o);
            if (pos < 0) return PagerAdapter.POSITION_NONE;
            return pos + 2;
        }

        @Override
        public int getCount() {
            if (sessions == null) return 1;

            int count = 2 + sessions.size();
            if (mTabs == null) return count;

            if (count > 2) {
                mTabs.setTabMode(TabLayout.MODE_SCROLLABLE);
            } else {
                mTabs.setTabMode(TabLayout.MODE_FIXED);
            }
            return count;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object o) {
            if (view == o) return true;

            if (o instanceof ConversationPage) {
                return ((ConversationPage) o).getView() == view;
            }

            return false;
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Conversation";
                case 1:
                    return "Commands";
                default:
                    ConversationPage session = sessions.get(position-2);
                    if (session == null) return super.getPageTitle(position);
                    return session.getTitle();
            }
        }

        class CommandSession extends RecyclerView.Adapter<CommandSession.ViewHolder> implements ConversationPage {
            abstract class ViewHolder<T extends ViewDataBinding> extends RecyclerView.ViewHolder {
                protected T binding;

                public ViewHolder(T binding) {
                    super(binding.getRoot());
                    this.binding = binding;
                }

                abstract public void bind(Item el);

                protected void setTextOrHide(TextView v, Optional<String> s) {
                    if (s == null || !s.isPresent()) {
                        v.setVisibility(View.GONE);
                    } else {
                        v.setVisibility(View.VISIBLE);
                        v.setText(s.get());
                    }
                }

                protected void setupInputType(Element field, TextView textinput, TextInputLayout layout) {
                    int flags = 0;
                    if (layout != null) layout.setEndIconMode(TextInputLayout.END_ICON_NONE);
                    textinput.setInputType(flags | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT);

                    String type = field.getAttribute("type");
                    if (type != null) {
                        if (type.equals("text-multi") || type.equals("jid-multi")) {
                            flags |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
                        }

                        textinput.setInputType(flags | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT);

                        if (type.equals("jid-single") || type.equals("jid-multi")) {
                            textinput.setInputType(flags | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                        }

                        if (type.equals("text-private")) {
                            textinput.setInputType(flags | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                            if (layout != null) layout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
                        }
                    }

                    Element validate = field.findChild("validate", "http://jabber.org/protocol/xdata-validate");
                    if (validate == null) return;
                    String datatype = validate.getAttribute("datatype");
                    if (datatype == null) return;

                    if (datatype.equals("xs:integer") || datatype.equals("xs:int") || datatype.equals("xs:long") || datatype.equals("xs:short") || datatype.equals("xs:byte")) {
                        textinput.setInputType(flags | InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
                    }

                    if (datatype.equals("xs:decimal") || datatype.equals("xs:double")) {
                        textinput.setInputType(flags | InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    }

                    if (datatype.equals("xs:date")) {
                        textinput.setInputType(flags | InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
                    }

                    if (datatype.equals("xs:dateTime")) {
                        textinput.setInputType(flags | InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_NORMAL);
                    }

                    if (datatype.equals("xs:time")) {
                        textinput.setInputType(flags | InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
                    }

                    if (datatype.equals("xs:anyURI")) {
                        textinput.setInputType(flags | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
                    }

                    if (datatype.equals("html:tel")) {
                        textinput.setInputType(flags | InputType.TYPE_CLASS_PHONE);
                    }

                    if (datatype.equals("html:email")) {
                        textinput.setInputType(flags | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                    }
                }

                protected String formatValue(String datatype, String value, boolean compact) {
                    if ("xs:dateTime".equals(datatype)) {
                        ZonedDateTime zonedDateTime = null;
                        try {
                            zonedDateTime = ZonedDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
                        } catch (final DateTimeParseException e) {
                            try {
                                DateTimeFormatter almostIso = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss] X");
                                zonedDateTime = ZonedDateTime.parse(value, almostIso);
                            } catch (final DateTimeParseException e2) { }
                        }
                        if (zonedDateTime == null) return value;
                        ZonedDateTime localZonedDateTime = zonedDateTime.withZoneSameInstant(ZoneId.systemDefault());
                        DateTimeFormatter outputFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
                        return localZonedDateTime.toLocalDateTime().format(outputFormat);
                    }

                    if ("html:tel".equals(datatype) && !compact) {
                        return PhoneNumberUtils.formatNumber(value, value, null);
                    }

                    return value;
                }
            }

            class ErrorViewHolder extends ViewHolder<CommandNoteBinding> {
                public ErrorViewHolder(CommandNoteBinding binding) { super(binding); }

                @Override
                public void bind(Item iq) {
                    binding.errorIcon.setVisibility(View.VISIBLE);

                    if (iq == null || iq.el == null) return;
                    Element error = iq.el.findChild("error");
                    if (error == null) {
                        binding.message.setText("Unexpected response: " + iq);
                        return;
                    }
                    String text = error.findChildContent("text", "urn:ietf:params:xml:ns:xmpp-stanzas");
                    if (text == null || text.equals("")) {
                        text = error.getChildren().get(0).getName();
                    }
                    binding.message.setText(text);
                }
            }

            class NoteViewHolder extends ViewHolder<CommandNoteBinding> {
                public NoteViewHolder(CommandNoteBinding binding) { super(binding); }

                @Override
                public void bind(Item note) {
                    binding.message.setText(note != null && note.el != null ? note.el.getContent() : "");

                    String type = note.el.getAttribute("type");
                    if (type != null && type.equals("error")) {
                        binding.errorIcon.setVisibility(View.VISIBLE);
                    }
                }
            }

            class ResultFieldViewHolder extends ViewHolder<CommandResultFieldBinding> {
                public ResultFieldViewHolder(CommandResultFieldBinding binding) { super(binding); }

                @Override
                public void bind(Item item) {
                    Field field = (Field) item;
                    setTextOrHide(binding.label, field.getLabel());
                    setTextOrHide(binding.desc, field.getDesc());

                    Element media = field.el.findChild("media", "urn:xmpp:media-element");
                    if (media == null) {
                        binding.mediaImage.setVisibility(View.GONE);
                    } else {
                        final LruCache<String, Drawable> cache = xmppConnectionService.getDrawableCache();
                        final HttpConnectionManager httpManager = xmppConnectionService.getHttpConnectionManager();
                        for (Element uriEl : media.getChildren()) {
                            if (!"uri".equals(uriEl.getName())) continue;
                            if (!"urn:xmpp:media-element".equals(uriEl.getNamespace())) continue;
                            String mimeType = uriEl.getAttribute("type");
                            String uriS = uriEl.getContent();
                            if (mimeType == null || uriS == null) continue;
                            Uri uri = Uri.parse(uriS);
                            if (mimeType.startsWith("image/") && "https".equals(uri.getScheme())) {
                                final Drawable d = getDrawableForUrl(uri.toString());
                                if (d != null) {
                                    binding.mediaImage.setImageDrawable(d);
                                    binding.mediaImage.setVisibility(View.VISIBLE);
                                }
                            }
                        }
                    }

                    Element validate = field.el.findChild("validate", "http://jabber.org/protocol/xdata-validate");
                    String datatype = validate == null ? null : validate.getAttribute("datatype");

                    ArrayAdapter<Option> values = new ArrayAdapter<>(binding.getRoot().getContext(), R.layout.simple_list_item);
                    for (Element el : field.el.getChildren()) {
                        if (el.getName().equals("value") && el.getNamespace().equals("jabber:x:data")) {
                            values.add(new Option(el.getContent(), formatValue(datatype, el.getContent(), false)));
                        }
                    }
                    binding.values.setAdapter(values);
                    Util.justifyListViewHeightBasedOnChildren(binding.values);

                    binding.values.setOnItemLongClickListener((arg0, arg1, pos, id) -> {
                        if (ShareUtil.copyTextToClipboard(binding.getRoot().getContext(), values.getItem(pos).getValue(), R.string.message)) {
                            Toast.makeText(binding.getRoot().getContext(), R.string.message_copied_to_clipboard, Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    });
                }
            }

            class ResultCellViewHolder extends ViewHolder<CommandResultCellBinding> {
                public ResultCellViewHolder(CommandResultCellBinding binding) { super(binding); }

                @Override
                public void bind(Item item) {
                    Cell cell = (Cell) item;

                    if (cell.el == null) {
                        binding.text.setTextAppearance(binding.getRoot().getContext(), com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
                        setTextOrHide(binding.text, cell.reported.getLabel());
                    } else {
                        Element validate = cell.reported.el.findChild("validate", "http://jabber.org/protocol/xdata-validate");
                        String datatype = validate == null ? null : validate.getAttribute("datatype");
                        String value = formatValue(datatype, cell.el.findChildContent("value", "jabber:x:data"), true);
                        SpannableStringBuilder text = new SpannableStringBuilder(value == null ? "" : value);

                        binding.text.setTextAppearance(binding.getRoot().getContext(), com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
                        binding.text.setText(text);

                        BetterLinkMovementMethod method = BetterLinkMovementMethod.newInstance();
                        method.setOnLinkLongClickListener((tv, url) -> {
                            tv.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0));
                            ShareUtil.copyLinkToClipboard(binding.getRoot().getContext(), url);
                            return true;
                        });
                        binding.text.setMovementMethod(method);
                    }
                }
            }

            class ItemCardViewHolder extends ViewHolder<CommandItemCardBinding> {
                public ItemCardViewHolder(CommandItemCardBinding binding) { super(binding); }

                @Override
                public void bind(Item item) {
                    binding.fields.removeAllViews();

                    for (Field field : reported) {
                        CommandResultFieldBinding row = DataBindingUtil.inflate(LayoutInflater.from(binding.getRoot().getContext()), R.layout.command_result_field, binding.fields, false);
                        GridLayout.LayoutParams param = new GridLayout.LayoutParams();
                        param.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL, 1f);
                        param.width = 0;
                        row.getRoot().setLayoutParams(param);
                        binding.fields.addView(row.getRoot());
                        for (Element el : item.el.getChildren()) {
                            if (el.getName().equals("field") && el.getNamespace().equals("jabber:x:data") && el.getAttribute("var") != null && el.getAttribute("var").equals(field.getVar())) {
                                for (String label : field.getLabel().asSet()) {
                                    el.setAttribute("label", label);
                                }
                                for (String desc : field.getDesc().asSet()) {
                                    el.setAttribute("desc", desc);
                                }
                                for (String type : field.getType().asSet()) {
                                    el.setAttribute("type", type);
                                }
                                Element validate = field.el.findChild("validate", "http://jabber.org/protocol/xdata-validate");
                                if (validate != null) el.addChild(validate);
                                new ResultFieldViewHolder(row).bind(new Field(eu.siacs.conversations.xmpp.forms.Field.parse(el), -1));
                            }
                        }
                    }
                }
            }

            class CheckboxFieldViewHolder extends ViewHolder<CommandCheckboxFieldBinding> implements CompoundButton.OnCheckedChangeListener {
                public CheckboxFieldViewHolder(CommandCheckboxFieldBinding binding) {
                    super(binding);
                    binding.row.setOnClickListener((v) -> {
                        binding.checkbox.toggle();
                    });
                    binding.checkbox.setOnCheckedChangeListener(this);
                }
                protected Element mValue = null;

                @Override
                public void bind(Item item) {
                    Field field = (Field) item;
                    binding.label.setText(field.getLabel().or(""));
                    setTextOrHide(binding.desc, field.getDesc());
                    mValue = field.getValue();
                    binding.checkbox.setChecked(mValue.getContent() != null && (mValue.getContent().equals("true") || mValue.getContent().equals("1")));
                }

                @Override
                public void onCheckedChanged(CompoundButton checkbox, boolean isChecked) {
                    if (mValue == null) return;

                    mValue.setContent(isChecked ? "true" : "false");
                }
            }

            class SearchListFieldViewHolder extends ViewHolder<CommandSearchListFieldBinding> implements TextWatcher {
                public SearchListFieldViewHolder(CommandSearchListFieldBinding binding) {
                    super(binding);
                    binding.search.addTextChangedListener(this);
                }
                protected Field field = null;
                Set<String> filteredValues;
                List<Option> options = new ArrayList<>();
                protected ArrayAdapter<Option> adapter;
                protected boolean open;
                protected boolean multi;
                protected int textColor = -1;

                @Override
                public void bind(Item item) {
                    ViewGroup.LayoutParams layout = binding.list.getLayoutParams();
                    final float density = xmppConnectionService.getResources().getDisplayMetrics().density;
                    if (fillableFieldCount > 1) {
                        layout.height = (int) (density * 200);
                    } else {
                        layout.height = (int) Math.max(density * 200, xmppConnectionService.getResources().getDisplayMetrics().heightPixels / 2);
                    }
                    binding.list.setLayoutParams(layout);

                    field = (Field) item;
                    setTextOrHide(binding.label, field.getLabel());
                    setTextOrHide(binding.desc, field.getDesc());

                    if (textColor == -1) textColor = binding.desc.getCurrentTextColor();
                    if (field.error != null) {
                        binding.desc.setVisibility(View.VISIBLE);
                        binding.desc.setText(field.error);
                        binding.desc.setTextColor(com.google.android.material.R.attr.colorError);
                    } else {
                        binding.desc.setTextColor(textColor);
                    }

                    Element validate = field.el.findChild("validate", "http://jabber.org/protocol/xdata-validate");
                    open = validate != null && validate.findChild("open", "http://jabber.org/protocol/xdata-validate") != null;
                    setupInputType(field.el, binding.search, null);

                    multi = field.getType().equals(Optional.of("list-multi"));
                    if (multi) {
                        binding.list.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
                    } else {
                        binding.list.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
                    }

                    options = field.getOptions();
                    binding.list.setOnItemClickListener((parent, view, position, id) -> {
                        Set<String> values = new HashSet<>();
                        if (multi) {
                            values.addAll(field.getValues());
                            for (final String value : field.getValues()) {
                                if (filteredValues.contains(value)) {
                                    values.remove(value);
                                }
                            }
                        }

                        SparseBooleanArray positions = binding.list.getCheckedItemPositions();
                        for (int i = 0; i < positions.size(); i++) {
                            if (positions.valueAt(i)) values.add(adapter.getItem(positions.keyAt(i)).getValue());
                        }
                        field.setValues(values);

                        if (!multi && open) binding.search.setText(String.join("\n", field.getValues()));
                    });
                    search("");
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (!multi && open) field.setValues(List.of(s.toString()));
                    search(s.toString());
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int count, int after) { }

                protected void search(String s) {
                    List<Option> filteredOptions;
                    final String q = s.replaceAll("\\W", "").toLowerCase();
                    if (q == null || q.equals("")) {
                        filteredOptions = options;
                    } else {
                        filteredOptions = options.stream().filter(o -> o.toString().replaceAll("\\W", "").toLowerCase().contains(q)).collect(Collectors.toList());
                    }
                    filteredValues = filteredOptions.stream().map(o -> o.getValue()).collect(Collectors.toSet());
                    adapter = new ArrayAdapter(binding.getRoot().getContext(), R.layout.simple_list_item, filteredOptions);
                    binding.list.setAdapter(adapter);

                    for (final String value : field.getValues()) {
                        int checkedPos = filteredOptions.indexOf(new Option(value, ""));
                        if (checkedPos >= 0) binding.list.setItemChecked(checkedPos, true);
                    }
                }
            }

            class RadioEditFieldViewHolder extends ViewHolder<CommandRadioEditFieldBinding> implements CompoundButton.OnCheckedChangeListener, TextWatcher {
                public RadioEditFieldViewHolder(CommandRadioEditFieldBinding binding) {
                    super(binding);
                    binding.open.addTextChangedListener(this);
                    options = new ArrayAdapter<Option>(binding.getRoot().getContext(), R.layout.radio_grid_item) {
                        @Override
                        public View getView(int position, View convertView, ViewGroup parent) {
                            CompoundButton v = (CompoundButton) super.getView(position, convertView, parent);
                            v.setId(position);
                            v.setChecked(getItem(position).getValue().equals(mValue.getContent()));
                            v.setOnCheckedChangeListener(RadioEditFieldViewHolder.this);
                            return v;
                        }
                    };
                }
                protected Element mValue = null;
                protected ArrayAdapter<Option> options;
                protected int textColor = -1;

                @Override
                public void bind(Item item) {
                    Field field = (Field) item;
                    setTextOrHide(binding.label, field.getLabel());
                    setTextOrHide(binding.desc, field.getDesc());

                    if (textColor == -1) textColor = binding.desc.getCurrentTextColor();
                    if (field.error != null) {
                        binding.desc.setVisibility(View.VISIBLE);
                        binding.desc.setText(field.error);
                        binding.desc.setTextColor(com.google.android.material.R.attr.colorError);
                    } else {
                        binding.desc.setTextColor(textColor);
                    }

                    mValue = field.getValue();

                    Element validate = field.el.findChild("validate", "http://jabber.org/protocol/xdata-validate");
                    binding.open.setVisibility((validate != null && validate.findChild("open", "http://jabber.org/protocol/xdata-validate") != null) ? View.VISIBLE : View.GONE);
                    binding.open.setText(mValue.getContent());
                    setupInputType(field.el, binding.open, null);

                    options.clear();
                    List<Option> theOptions = field.getOptions();
                    options.addAll(theOptions);

                    float screenWidth = binding.getRoot().getContext().getResources().getDisplayMetrics().widthPixels;
                    TextPaint paint = ((TextView) LayoutInflater.from(binding.getRoot().getContext()).inflate(R.layout.radio_grid_item, null)).getPaint();
                    float maxColumnWidth = theOptions.stream().map((x) ->
                            StaticLayout.getDesiredWidth(x.toString(), paint)
                    ).max(Float::compare).orElse(new Float(0.0));
                    if (maxColumnWidth * theOptions.size() < 0.90 * screenWidth) {
                        binding.radios.setNumColumns(theOptions.size());
                    } else if (maxColumnWidth * (theOptions.size() / 2) < 0.90 * screenWidth) {
                        binding.radios.setNumColumns(theOptions.size() / 2);
                    } else {
                        binding.radios.setNumColumns(1);
                    }
                    binding.radios.setAdapter(options);
                }

                @Override
                public void onCheckedChanged(CompoundButton radio, boolean isChecked) {
                    if (mValue == null) return;

                    if (isChecked) {
                        mValue.setContent(options.getItem(radio.getId()).getValue());
                        binding.open.setText(mValue.getContent());
                    }
                    options.notifyDataSetChanged();
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (mValue == null) return;

                    mValue.setContent(s.toString());
                    options.notifyDataSetChanged();
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int count, int after) { }
            }

            class SpinnerFieldViewHolder extends ViewHolder<CommandSpinnerFieldBinding> implements AdapterView.OnItemSelectedListener {
                public SpinnerFieldViewHolder(CommandSpinnerFieldBinding binding) {
                    super(binding);
                    binding.spinner.setOnItemSelectedListener(this);
                }
                protected Element mValue = null;

                @Override
                public void bind(Item item) {
                    Field field = (Field) item;
                    setTextOrHide(binding.label, field.getLabel());
                    binding.spinner.setPrompt(field.getLabel().or(""));
                    setTextOrHide(binding.desc, field.getDesc());

                    mValue = field.getValue();

                    ArrayAdapter<Option> options = new ArrayAdapter<Option>(binding.getRoot().getContext(), android.R.layout.simple_spinner_item);
                    options.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    options.addAll(field.getOptions());

                    binding.spinner.setAdapter(options);
                    binding.spinner.setSelection(options.getPosition(new Option(mValue.getContent(), null)));
                }

                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    Option o = (Option) parent.getItemAtPosition(pos);
                    if (mValue == null) return;

                    mValue.setContent(o == null ? "" : o.getValue());
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    mValue.setContent("");
                }
            }

            class ButtonGridFieldViewHolder extends ViewHolder<CommandButtonGridFieldBinding> {
                public ButtonGridFieldViewHolder(CommandButtonGridFieldBinding binding) {
                    super(binding);
                    options = new ArrayAdapter<Option>(binding.getRoot().getContext(), R.layout.button_grid_item) {
                        protected int height = 0;

                        @Override
                        public View getView(int position, View convertView, ViewGroup parent) {
                            Button v = (Button) super.getView(position, convertView, parent);
                            v.setOnClickListener((view) -> {
                                mValue.setContent(getItem(position).getValue());
                                execute();
                                loading = true;
                            });

                            final SVG icon = getItem(position).getIcon();
                            if (icon != null) {
                                final Element iconEl = getItem(position).getIconEl();
                                if (height < 1) {
                                    v.measure(0, 0);
                                    height = v.getMeasuredHeight();
                                }
                                if (height < 1) return v;
                                if (mediaSelector) {
                                    final Drawable d = getDrawableForSVG(icon, iconEl, height * 4);
                                    if (d != null) {
                                        final int boundsHeight = 35 + (int)((height * 4) / xmppConnectionService.getResources().getDisplayMetrics().density);
                                        d.setBounds(0, 0, d.getIntrinsicWidth(), boundsHeight);
                                    }
                                    v.setCompoundDrawables(null, d, null, null);
                                } else {
                                    v.setCompoundDrawablesRelativeWithIntrinsicBounds(getDrawableForSVG(icon, iconEl, height), null, null, null);
                                }
                            }

                            return v;
                        }
                    };
                }
                protected Element mValue = null;
                protected ArrayAdapter<Option> options;
                protected Option defaultOption = null;
                protected boolean mediaSelector = false;
                protected int textColor = -1;

                @Override
                public void bind(Item item) {
                    Field field = (Field) item;
                    setTextOrHide(binding.label, field.getLabel());
                    setTextOrHide(binding.desc, field.getDesc());

                    if (textColor == -1) textColor = binding.desc.getCurrentTextColor();
                    if (field.error != null) {
                        binding.desc.setVisibility(View.VISIBLE);
                        binding.desc.setText(field.error);
                        binding.desc.setTextColor(com.google.android.material.R.attr.colorError);
                    } else {
                        binding.desc.setTextColor(textColor);
                    }

                    mValue = field.getValue();
                    mediaSelector = field.el.findChild("media-selector", "https://ns.cheogram.com/") != null;

                    Element validate = field.el.findChild("validate", "http://jabber.org/protocol/xdata-validate");
                    binding.openButton.setVisibility((validate != null && validate.findChild("open", "http://jabber.org/protocol/xdata-validate") != null) ? View.VISIBLE : View.GONE);
                    binding.openButton.setOnClickListener((view) -> {
                        AlertDialog.Builder builder = new AlertDialog.Builder(binding.getRoot().getContext());
                        DialogQuickeditBinding dialogBinding = DataBindingUtil.inflate(LayoutInflater.from(binding.getRoot().getContext()), R.layout.dialog_quickedit, null, false);
                        builder.setPositiveButton(R.string.action_execute, null);
                        if (field.getDesc().isPresent()) {
                            dialogBinding.inputLayout.setHint(field.getDesc().get());
                        }
                        dialogBinding.inputEditText.requestFocus();
                        dialogBinding.inputEditText.getText().append(mValue.getContent());
                        builder.setView(dialogBinding.getRoot());
                        builder.setNegativeButton(R.string.cancel, null);
                        final AlertDialog dialog = builder.create();
                        dialog.setOnShowListener(d -> SoftKeyboardUtils.showKeyboard(dialogBinding.inputEditText));
                        dialog.show();
                        View.OnClickListener clickListener = v -> {
                            String value = dialogBinding.inputEditText.getText().toString();
                            mValue.setContent(value);
                            SoftKeyboardUtils.hideSoftKeyboard(dialogBinding.inputEditText);
                            dialog.dismiss();
                            execute();
                            loading = true;
                        };
                        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(clickListener);
                        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener((v -> {
                            SoftKeyboardUtils.hideSoftKeyboard(dialogBinding.inputEditText);
                            dialog.dismiss();
                        }));
                        dialog.setCanceledOnTouchOutside(false);
                        dialog.setOnDismissListener(dialog1 -> {
                            SoftKeyboardUtils.hideSoftKeyboard(dialogBinding.inputEditText);
                        });
                    });

                    options.clear();
                    List<Option> theOptions = field.getType().equals(Optional.of("boolean")) ? new ArrayList<>(List.of(new Option("false", binding.getRoot().getContext().getString(R.string.no)), new Option("true", binding.getRoot().getContext().getString(R.string.yes)))) : field.getOptions();

                    defaultOption = null;
                    for (Option option : theOptions) {
                        if (option.getValue().equals(mValue.getContent())) {
                            defaultOption = option;
                            break;
                        }
                    }
                    if (defaultOption == null && !mValue.getContent().equals("")) {
                        // Synthesize default option for custom value
                        defaultOption = new Option(mValue.getContent(), mValue.getContent());
                    }
                    if (defaultOption == null) {
                        binding.defaultButton.setVisibility(View.GONE);
                    } else {
                        theOptions.remove(defaultOption);
                        binding.defaultButton.setVisibility(View.VISIBLE);

                        final SVG defaultIcon = defaultOption.getIcon();
                        if (defaultIcon != null) {
                            DisplayMetrics display = mPager.getContext().getResources().getDisplayMetrics();
                            int height = (int)(display.heightPixels*display.density/4);
                            binding.defaultButton.setCompoundDrawablesRelativeWithIntrinsicBounds(null, getDrawableForSVG(defaultIcon, defaultOption.getIconEl(), height), null, null);
                        }

                        binding.defaultButton.setText(defaultOption.toString());
                        binding.defaultButton.setOnClickListener((view) -> {
                            mValue.setContent(defaultOption.getValue());
                            execute();
                            loading = true;
                        });
                    }

                    options.addAll(theOptions);
                    binding.buttons.setAdapter(options);
                }
            }

            class TextFieldViewHolder extends ViewHolder<CommandTextFieldBinding> implements TextWatcher {
                public TextFieldViewHolder(CommandTextFieldBinding binding) {
                    super(binding);
                    binding.textinput.addTextChangedListener(this);
                }
                protected Field field = null;

                @Override
                public void bind(Item item) {
                    field = (Field) item;
                    binding.textinputLayout.setHint(field.getLabel().or(""));

                    binding.textinputLayout.setHelperTextEnabled(field.getDesc().isPresent());
                    for (String desc : field.getDesc().asSet()) {
                        binding.textinputLayout.setHelperText(desc);
                    }

                    binding.textinputLayout.setErrorEnabled(field.error != null);
                    if (field.error != null) binding.textinputLayout.setError(field.error);

                    binding.textinput.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
                    String suffixLabel = field.el.findChildContent("x", "https://ns.cheogram.com/suffix-label");
                    if (suffixLabel == null) {
                        binding.textinputLayout.setSuffixText("");
                    } else {
                        binding.textinputLayout.setSuffixText(suffixLabel);
                        binding.textinput.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
                    }

                    String prefixLabel = field.el.findChildContent("x", "https://ns.cheogram.com/prefix-label");
                    binding.textinputLayout.setPrefixText(prefixLabel == null ? "" : prefixLabel);

                    binding.textinput.setText(String.join("\n", field.getValues()));
                    setupInputType(field.el, binding.textinput, binding.textinputLayout);
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (field == null) return;

                    field.setValues(List.of(s.toString().split("\n")));
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int count, int after) { }
            }

            class SliderFieldViewHolder extends ViewHolder<CommandSliderFieldBinding> {
                public SliderFieldViewHolder(CommandSliderFieldBinding binding) { super(binding); }
                protected Field field = null;

                @Override
                public void bind(Item item) {
                    field = (Field) item;
                    setTextOrHide(binding.label, field.getLabel());
                    setTextOrHide(binding.desc, field.getDesc());
                    final Element validate = field.el.findChild("validate", "http://jabber.org/protocol/xdata-validate");
                    final String datatype = validate == null ? null : validate.getAttribute("datatype");
                    final Element range = validate == null ? null : validate.findChild("range", "http://jabber.org/protocol/xdata-validate");
                    // NOTE: range also implies open, so we don't have to be bound by the options strictly
                    // Also, we don't have anywhere to put labels so we show only values, which might sometimes be bad...
                    Float min = null;
                    try { min = range.getAttribute("min") == null ? null : Float.valueOf(range.getAttribute("min")); } catch (NumberFormatException e) { }
                    Float max = null;
                    try { max = range.getAttribute("max") == null ? null : Float.valueOf(range.getAttribute("max"));  } catch (NumberFormatException e) { }

                    List<Float> options = field.getOptions().stream().map(o -> Float.valueOf(o.getValue())).collect(Collectors.toList());
                    Collections.sort(options);
                    if (options.size() > 0) {
                        // min/max should be on the range, but if you have options and didn't put them on the range we can imply
                        if (min == null) min = options.get(0);
                        if (max == null) max = options.get(options.size()-1);
                    }

                    if (field.getValues().size() > 0) {
                        binding.slider.setValue(Float.valueOf(field.getValue().getContent()));
                    } else {
                        binding.slider.setValue(min == null ? Float.MIN_VALUE : min);
                    }
                    binding.slider.setValueFrom(min == null ? Float.MIN_VALUE : min);
                    binding.slider.setValueTo(max == null ? Float.MAX_VALUE : max);
                    if ("xs:integer".equals(datatype) || "xs:int".equals(datatype) || "xs:long".equals(datatype) || "xs:short".equals(datatype) || "xs:byte".equals(datatype)) {
                        binding.slider.setStepSize(1);
                    } else {
                        binding.slider.setStepSize(0);
                    }

                    if (options.size() > 0) {
                        float step = -1;
                        Float prev = null;
                        for (final Float option : options) {
                            if (prev != null) {
                                float nextStep = option - prev;
                                if (step > 0 && step != nextStep) {
                                    step = -1;
                                    break;
                                }
                                step = nextStep;
                            }
                            prev = option;
                        }
                        if (step > 0) binding.slider.setStepSize(step);
                    }

                    binding.slider.addOnChangeListener((slider, value, fromUser) -> {
                        field.setValues(List.of(new DecimalFormat().format(value)));
                    });
                }
            }

            class WebViewHolder extends ViewHolder<CommandWebviewBinding> {
                public WebViewHolder(CommandWebviewBinding binding) { super(binding); }
                protected String boundUrl = "";

                @Override
                public void bind(Item oob) {
                    setTextOrHide(binding.desc, Optional.fromNullable(oob.el.findChildContent("desc", "jabber:x:oob")));
                    binding.webview.getSettings().setJavaScriptEnabled(true);
                    binding.webview.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.106 Mobile Safari/537.36");
                    binding.webview.getSettings().setDatabaseEnabled(true);
                    binding.webview.getSettings().setDomStorageEnabled(true);
                    binding.webview.setWebChromeClient(new WebChromeClient() {
                        @Override
                        public void onProgressChanged(WebView view, int newProgress) {
                            binding.progressbar.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
                            binding.progressbar.setProgress(newProgress);
                        }
                    });
                    binding.webview.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            super.onPageFinished(view, url);
                            mTitle = view.getTitle();
                            ConversationPagerAdapter.this.notifyDataSetChanged();
                        }
                    });
                    final String url = oob.el.findChildContent("url", "jabber:x:oob");
                    if (!boundUrl.equals(url)) {
                        binding.webview.addJavascriptInterface(new JsObject(), "xmpp_xep0050");
                        binding.webview.loadUrl(url);
                        boundUrl = url;
                    }
                }

                class JsObject {
                    @JavascriptInterface
                    public void execute() { execute("execute"); }

                    @JavascriptInterface
                    public void execute(String action) {
                        getView().post(() -> {
                            actionToWebview = null;
                            if(CommandSession.this.execute(action)) {
                                removeSession(CommandSession.this);
                            }
                        });
                    }

                    @JavascriptInterface
                    public void preventDefault() {
                        actionToWebview = binding.webview;
                    }
                }
            }

            class ProgressBarViewHolder extends ViewHolder<CommandProgressBarBinding> {
                public ProgressBarViewHolder(CommandProgressBarBinding binding) { super(binding); }

                @Override
                public void bind(Item item) {
                    binding.text.setVisibility(loadingHasBeenLong ? View.VISIBLE : View.GONE);
                }
            }

            class Item {
                protected Element el;
                protected int viewType;
                protected String error = null;

                Item(Element el, int viewType) {
                    this.el = el;
                    this.viewType = viewType;
                }

                public boolean validate() {
                    error = null;
                    return true;
                }
            }

            class Field extends Item {
                Field(eu.siacs.conversations.xmpp.forms.Field el, int viewType) { super(el, viewType); }

                @Override
                public boolean validate() {
                    if (!super.validate()) return false;
                    if (el.findChild("required", "jabber:x:data") == null) return true;
                    if (getValue().getContent() != null && !getValue().getContent().equals("")) return true;

                    error = "this value is required";
                    return false;
                }

                public String getVar() {
                    return el.getAttribute("var");
                }

                public Optional<String> getType() {
                    return Optional.fromNullable(el.getAttribute("type"));
                }

                public Optional<String> getLabel() {
                    String label = el.getAttribute("label");
                    if (label == null) label = getVar();
                    return Optional.fromNullable(label);
                }

                public Optional<String> getDesc() {
                    return Optional.fromNullable(el.findChildContent("desc", "jabber:x:data"));
                }

                public Element getValue() {
                    Element value = el.findChild("value", "jabber:x:data");
                    if (value == null) {
                        value = el.addChild("value", "jabber:x:data");
                    }
                    return value;
                }

                public void setValues(Collection<String> values) {
                    for(Element child : el.getChildren()) {
                        if ("value".equals(child.getName()) && "jabber:x:data".equals(child.getNamespace())) {
                            el.removeChild(child);
                        }
                    }

                    for (String value : values) {
                        el.addChild("value", "jabber:x:data").setContent(value);
                    }
                }

                public List<String> getValues() {
                    List<String> values = new ArrayList<>();
                    for(Element child : el.getChildren()) {
                        if ("value".equals(child.getName()) && "jabber:x:data".equals(child.getNamespace())) {
                            values.add(child.getContent());
                        }
                    }
                    return values;
                }

                public List<Option> getOptions() {
                    return Option.forField(el);
                }
            }

            class Cell extends Item {
                protected Field reported;

                Cell(Field reported, Element item) {
                    super(item, TYPE_RESULT_CELL);
                    this.reported = reported;
                }
            }

            protected Field mkField(Element el) {
                int viewType = -1;

                String formType = responseElement.getAttribute("type");
                if (formType != null) {
                    String fieldType = el.getAttribute("type");
                    if (fieldType == null) fieldType = "text-single";

                    if (formType.equals("result") || fieldType.equals("fixed")) {
                        viewType = TYPE_RESULT_FIELD;
                    } else if (formType.equals("form")) {
                        final Element validate = el.findChild("validate", "http://jabber.org/protocol/xdata-validate");
                        final String datatype = validate == null ? null : validate.getAttribute("datatype");
                        final Element range = validate == null ? null : validate.findChild("range", "http://jabber.org/protocol/xdata-validate");
                        if (fieldType.equals("boolean")) {
                            if (fillableFieldCount == 1 && actionsAdapter.countProceed() < 1) {
                                viewType = TYPE_BUTTON_GRID_FIELD;
                            } else {
                                viewType = TYPE_CHECKBOX_FIELD;
                            }
                        } else if (
                                range != null && range.getAttribute("min") != null && range.getAttribute("max") != null && (
                                        "xs:integer".equals(datatype) || "xs:int".equals(datatype) || "xs:long".equals(datatype) || "xs:short".equals(datatype) || "xs:byte".equals(datatype) ||
                                                "xs:decimal".equals(datatype) || "xs:double".equals(datatype)
                                )
                        ) {
                            // has a range and is numeric, use a slider
                            viewType = TYPE_SLIDER_FIELD;
                        } else if (fieldType.equals("list-single")) {
                            if (fillableFieldCount == 1 && actionsAdapter.countProceed() < 1 && Option.forField(el).size() < 50) {
                                viewType = TYPE_BUTTON_GRID_FIELD;
                            } else if (Option.forField(el).size() > 9) {
                                viewType = TYPE_SEARCH_LIST_FIELD;
                            } else if (el.findChild("value", "jabber:x:data") == null || (validate != null && validate.findChild("open", "http://jabber.org/protocol/xdata-validate") != null)) {
                                viewType = TYPE_RADIO_EDIT_FIELD;
                            } else {
                                viewType = TYPE_SPINNER_FIELD;
                            }
                        } else if (fieldType.equals("list-multi")) {
                            viewType = TYPE_SEARCH_LIST_FIELD;
                        } else {
                            viewType = TYPE_TEXT_FIELD;
                        }
                    }

                    return new Field(eu.siacs.conversations.xmpp.forms.Field.parse(el), viewType);
                }

                return null;
            }

            protected Item mkItem(Element el, int pos) {
                int viewType = TYPE_ERROR;

                if (response != null && response.getType() == IqPacket.TYPE.RESULT) {
                    if (el.getName().equals("note")) {
                        viewType = TYPE_NOTE;
                    } else if (el.getNamespace().equals("jabber:x:oob")) {
                        viewType = TYPE_WEB;
                    } else if (el.getName().equals("instructions") && el.getNamespace().equals("jabber:x:data")) {
                        viewType = TYPE_NOTE;
                    } else if (el.getName().equals("field") && el.getNamespace().equals("jabber:x:data")) {
                        Field field = mkField(el);
                        if (field != null) {
                            items.put(pos, field);
                            return field;
                        }
                    }
                }

                Item item = new Item(el, viewType);
                items.put(pos, item);
                return item;
            }

            class ActionsAdapter extends ArrayAdapter<Pair<String, String>> {
                protected Context ctx;

                public ActionsAdapter(Context ctx) {
                    super(ctx, R.layout.simple_list_item);
                    this.ctx = ctx;
                }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View v = super.getView(position, convertView, parent);
                    TextView tv = (TextView) v.findViewById(android.R.id.text1);
                    tv.setGravity(Gravity.CENTER);
                    tv.setText(getItem(position).second);
                    int resId = ctx.getResources().getIdentifier("action_" + getItem(position).first, "string" , ctx.getPackageName());
                    if (resId != 0 && getItem(position).second.equals(getItem(position).first)) tv.setText(ctx.getResources().getString(resId));
                    final var colors = MaterialColors.getColorRoles(ctx, UIHelper.getColorForName(getItem(position).first));
                    tv.setTextColor(colors.getOnAccent());
                    tv.setBackgroundColor(MaterialColors.harmonizeWithPrimary(ctx, colors.getAccent()));
                    return v;
                }

                public int getPosition(String s) {
                    for(int i = 0; i < getCount(); i++) {
                        if (getItem(i).first.equals(s)) return i;
                    }
                    return -1;
                }

                public int countProceed() {
                    int count = 0;
                    for(int i = 0; i < getCount(); i++) {
                        if (!"cancel".equals(getItem(i).first) && !"prev".equals(getItem(i).first)) count++;
                    }
                    return count;
                }

                public int countExceptCancel() {
                    int count = 0;
                    for(int i = 0; i < getCount(); i++) {
                        if (!getItem(i).first.equals("cancel")) count++;
                    }
                    return count;
                }

                public void clearProceed() {
                    Pair<String,String> cancelItem = null;
                    Pair<String,String> prevItem = null;
                    for(int i = 0; i < getCount(); i++) {
                        if (getItem(i).first.equals("cancel")) cancelItem = getItem(i);
                        if (getItem(i).first.equals("prev")) prevItem = getItem(i);
                    }
                    clear();
                    if (cancelItem != null) add(cancelItem);
                    if (prevItem != null) add(prevItem);
                }
            }

            final int TYPE_ERROR = 1;
            final int TYPE_NOTE = 2;
            final int TYPE_WEB = 3;
            final int TYPE_RESULT_FIELD = 4;
            final int TYPE_TEXT_FIELD = 5;
            final int TYPE_CHECKBOX_FIELD = 6;
            final int TYPE_SPINNER_FIELD = 7;
            final int TYPE_RADIO_EDIT_FIELD = 8;
            final int TYPE_RESULT_CELL = 9;
            final int TYPE_PROGRESSBAR = 10;
            final int TYPE_SEARCH_LIST_FIELD = 11;
            final int TYPE_ITEM_CARD = 12;
            final int TYPE_BUTTON_GRID_FIELD = 13;
            final int TYPE_SLIDER_FIELD = 14;

            protected boolean executing = false;
            protected boolean loading = false;
            protected boolean loadingHasBeenLong = false;
            protected Timer loadingTimer = new Timer();
            protected String mTitle;
            protected String mNode;
            protected CommandPageBinding mBinding = null;
            protected IqPacket response = null;
            protected Element responseElement = null;
            protected boolean expectingRemoval = false;
            protected List<Field> reported = null;
            protected SparseArray<Item> items = new SparseArray<>();
            protected XmppConnectionService xmppConnectionService;
            protected ActionsAdapter actionsAdapter = null;
            protected GridLayoutManager layoutManager;
            protected WebView actionToWebview = null;
            protected int fillableFieldCount = 0;
            protected IqPacket pendingResponsePacket = null;
            protected boolean waitingForRefresh = false;

            CommandSession(String title, String node, XmppConnectionService xmppConnectionService) {
                loading();
                mTitle = title;
                mNode = node;
                this.xmppConnectionService = xmppConnectionService;
                if (mPager != null) setupLayoutManager(mPager.getContext());
            }

            public String getTitle() {
                return mTitle;
            }

            public String getNode() {
                return mNode;
            }

            public void updateWithResponse(final IqPacket iq) {
                if (getView() != null && getView().isAttachedToWindow()) {
                    getView().post(() -> updateWithResponseUiThread(iq));
                } else {
                    pendingResponsePacket = iq;
                }
            }

            protected void updateWithResponseUiThread(final IqPacket iq) {
                Timer oldTimer = this.loadingTimer;
                this.loadingTimer = new Timer();
                oldTimer.cancel();
                this.executing = false;
                this.loading = false;
                this.loadingHasBeenLong = false;
                this.responseElement = null;
                this.fillableFieldCount = 0;
                this.reported = null;
                this.response = iq;
                this.items.clear();
                this.actionsAdapter.clear();
                layoutManager.setSpanCount(1);

                boolean actionsCleared = false;
                Element command = iq.findChild("command", "http://jabber.org/protocol/commands");
                if (iq.getType() == IqPacket.TYPE.RESULT && command != null) {
                    if (mNode.equals("jabber:iq:register") && command.getAttribute("status") != null && command.getAttribute("status").equals("completed")) {
                        xmppConnectionService.createContact(getAccount().getRoster().getContact(iq.getFrom()), true);
                    }

                    if (xmppConnectionService.isOnboarding() && mNode.equals("jabber:iq:register") && !"canceled".equals(command.getAttribute("status")) && xmppConnectionService.getPreferences().contains("onboarding_action")) {
                        xmppConnectionService.getPreferences().edit().putBoolean("onboarding_continued", true).commit();
                    }

                    Element actions = command.findChild("actions", "http://jabber.org/protocol/commands");
                    if (actions != null) {
                        for (Element action : actions.getChildren()) {
                            if (!"http://jabber.org/protocol/commands".equals(action.getNamespace())) continue;
                            if ("execute".equals(action.getName())) continue;

                            actionsAdapter.add(Pair.create(action.getName(), action.getName()));
                        }
                    }

                    for (Element el : command.getChildren()) {
                        if ("x".equals(el.getName()) && "jabber:x:data".equals(el.getNamespace())) {
                            Data form = Data.parse(el);
                            String title = form.getTitle();
                            if (title != null) {
                                mTitle = title;
                                ConversationPagerAdapter.this.notifyDataSetChanged();
                            }

                            if ("result".equals(el.getAttribute("type")) || "form".equals(el.getAttribute("type"))) {
                                this.responseElement = el;
                                setupReported(el.findChild("reported", "jabber:x:data"));
                                if (mBinding != null) mBinding.form.setLayoutManager(setupLayoutManager(mBinding.getRoot().getContext()));
                            }

                            eu.siacs.conversations.xmpp.forms.Field actionList = form.getFieldByName("http://jabber.org/protocol/commands#actions");
                            if (actionList != null) {
                                actionsAdapter.clear();

                                for (Option action : actionList.getOptions()) {
                                    actionsAdapter.add(Pair.create(action.getValue(), action.toString()));
                                }
                            }

                            eu.siacs.conversations.xmpp.forms.Field fillableField = null;
                            for (eu.siacs.conversations.xmpp.forms.Field field : form.getFields()) {
                                if ((field.getType() == null || (!field.getType().equals("hidden") && !field.getType().equals("fixed"))) && field.getFieldName() != null && !field.getFieldName().equals("http://jabber.org/protocol/commands#actions")) {
                                    final var validate = field.findChild("validate", "http://jabber.org/protocol/xdata-validate");
                                    final var range = validate == null ? null : validate.findChild("range", "http://jabber.org/protocol/xdata-validate");
                                    fillableField = range == null ? field : null;
                                    fillableFieldCount++;
                                }
                            }

                            if (fillableFieldCount == 1 && fillableField != null && actionsAdapter.countProceed() < 2 && (("list-single".equals(fillableField.getType()) && Option.forField(fillableField).size() < 50) || ("boolean".equals(fillableField.getType()) && fillableField.getValue() == null))) {
                                actionsCleared = true;
                                actionsAdapter.clearProceed();
                            }
                            break;
                        }
                        if (el.getName().equals("x") && el.getNamespace().equals("jabber:x:oob")) {
                            String url = el.findChildContent("url", "jabber:x:oob");
                            if (url != null) {
                                String scheme = Uri.parse(url).getScheme();
                                if (scheme.equals("http") || scheme.equals("https")) {
                                    this.responseElement = el;
                                    break;
                                }
                                if (scheme.equals("xmpp")) {
                                    expectingRemoval = true;
                                    final Intent intent = new Intent(getView().getContext(), UriHandlerActivity.class);
                                    intent.setAction(Intent.ACTION_VIEW);
                                    intent.setData(Uri.parse(url));
                                    getView().getContext().startActivity(intent);
                                    break;
                                }
                            }
                        }
                        if (el.getName().equals("note") && el.getNamespace().equals("http://jabber.org/protocol/commands")) {
                            this.responseElement = el;
                            break;
                        }
                    }

                    if (responseElement == null && command.getAttribute("status") != null && (command.getAttribute("status").equals("completed") || command.getAttribute("status").equals("canceled"))) {
                        if ("jabber:iq:register".equals(mNode) && "canceled".equals(command.getAttribute("status"))) {
                            if (xmppConnectionService.isOnboarding()) {
                                if (xmppConnectionService.getPreferences().contains("onboarding_action")) {
                                    xmppConnectionService.deleteAccount(getAccount());
                                } else {
                                    if (xmppConnectionService.getPreferences().getBoolean("onboarding_continued", false)) {
                                        removeSession(this);
                                        return;
                                    } else {
                                        xmppConnectionService.getPreferences().edit().putString("onboarding_action", "cancel").commit();
                                        xmppConnectionService.deleteAccount(getAccount());
                                    }
                                }
                            }
                            xmppConnectionService.archiveConversation(Conversation.this);
                        }

                        expectingRemoval = true;
                        removeSession(this);
                        return;
                    }

                    if ("executing".equals(command.getAttribute("status")) && actionsAdapter.countExceptCancel() < 1 && !actionsCleared) {
                        // No actions have been given, but we are not done?
                        // This is probably a spec violation, but we should do *something*
                        actionsAdapter.add(Pair.create("execute", "execute"));
                    }

                    if (!actionsAdapter.isEmpty() || fillableFieldCount > 0) {
                        if ("completed".equals(command.getAttribute("status")) || "canceled".equals(command.getAttribute("status"))) {
                            actionsAdapter.add(Pair.create("close", actionsAdapter.ctx.getString(R.string.close)));
                        } else if (actionsAdapter.getPosition("cancel") < 0 && !xmppConnectionService.isOnboarding()) {
                            actionsAdapter.insert(Pair.create("cancel", actionsAdapter.ctx.getString(R.string.cancel)), 0);
                        }
                    }
                }

                if (actionsAdapter.isEmpty()) {
                    actionsAdapter.add(Pair.create("close", actionsAdapter.ctx.getString(R.string.close)));
                }

                actionsAdapter.sort((x, y) -> {
                    if (x.first.equals("cancel")) return -1;
                    if (y.first.equals("cancel")) return 1;
                    if (x.first.equals("prev") && xmppConnectionService.isOnboarding()) return -1;
                    if (y.first.equals("prev") && xmppConnectionService.isOnboarding()) return 1;
                    return 0;
                });

                Data dataForm = null;
                if (responseElement != null && responseElement.getName().equals("x") && responseElement.getNamespace().equals("jabber:x:data")) dataForm = Data.parse(responseElement);
                if (mNode.equals("jabber:iq:register") &&
                        xmppConnectionService.getPreferences().contains("onboarding_action") &&
                        dataForm != null && dataForm.getFieldByName("gateway-jid") != null) {


                    dataForm.put("gateway-jid", xmppConnectionService.getPreferences().getString("onboarding_action", ""));
                    execute();
                }
                xmppConnectionService.getPreferences().edit().remove("onboarding_action").commit();
                notifyDataSetChanged();
            }

            protected void setupReported(Element el) {
                if (el == null) {
                    reported = null;
                    return;
                }

                reported = new ArrayList<>();
                for (Element fieldEl : el.getChildren()) {
                    if (!fieldEl.getName().equals("field") || !fieldEl.getNamespace().equals("jabber:x:data")) continue;
                    reported.add(mkField(fieldEl));
                }
            }

            @Override
            public int getItemCount() {
                if (loading) return 1;
                if (response == null) return 0;
                if (response.getType() == IqPacket.TYPE.RESULT && responseElement != null && responseElement.getNamespace().equals("jabber:x:data")) {
                    int i = 0;
                    for (Element el : responseElement.getChildren()) {
                        if (!el.getNamespace().equals("jabber:x:data")) continue;
                        if (el.getName().equals("title")) continue;
                        if (el.getName().equals("field")) {
                            String type = el.getAttribute("type");
                            if (type != null && type.equals("hidden")) continue;
                            if (el.getAttribute("var") != null && el.getAttribute("var").equals("http://jabber.org/protocol/commands#actions")) continue;
                        }

                        if (el.getName().equals("reported") || el.getName().equals("item")) {
                            if ((layoutManager == null ? 1 : layoutManager.getSpanCount()) < reported.size()) {
                                if (el.getName().equals("reported")) continue;
                                i += 1;
                            } else {
                                if (reported != null) i += reported.size();
                            }
                            continue;
                        }

                        i++;
                    }
                    return i;
                }
                return 1;
            }

            public Item getItem(int position) {
                if (loading) return new Item(null, TYPE_PROGRESSBAR);
                if (items.get(position) != null) return items.get(position);
                if (response == null) return null;

                if (response.getType() == IqPacket.TYPE.RESULT && responseElement != null) {
                    if (responseElement.getNamespace().equals("jabber:x:data")) {
                        int i = 0;
                        for (Element el : responseElement.getChildren()) {
                            if (!el.getNamespace().equals("jabber:x:data")) continue;
                            if (el.getName().equals("title")) continue;
                            if (el.getName().equals("field")) {
                                String type = el.getAttribute("type");
                                if (type != null && type.equals("hidden")) continue;
                                if (el.getAttribute("var") != null && el.getAttribute("var").equals("http://jabber.org/protocol/commands#actions")) continue;
                            }

                            if (el.getName().equals("reported") || el.getName().equals("item")) {
                                Cell cell = null;

                                if (reported != null) {
                                    if ((layoutManager == null ? 1 : layoutManager.getSpanCount()) < reported.size()) {
                                        if (el.getName().equals("reported")) continue;
                                        if (i == position) {
                                            items.put(position, new Item(el, TYPE_ITEM_CARD));
                                            return items.get(position);
                                        }
                                    } else {
                                        if (reported.size() > position - i) {
                                            Field reportedField = reported.get(position - i);
                                            Element itemField = null;
                                            if (el.getName().equals("item")) {
                                                for (Element subel : el.getChildren()) {
                                                    if (subel.getAttribute("var").equals(reportedField.getVar())) {
                                                        itemField = subel;
                                                        break;
                                                    }
                                                }
                                            }
                                            cell = new Cell(reportedField, itemField);
                                        } else {
                                            i += reported.size();
                                            continue;
                                        }
                                    }
                                }

                                if (cell != null) {
                                    items.put(position, cell);
                                    return cell;
                                }
                            }

                            if (i < position) {
                                i++;
                                continue;
                            }

                            return mkItem(el, position);
                        }
                    }
                }

                return mkItem(responseElement == null ? response : responseElement, position);
            }

            @Override
            public int getItemViewType(int position) {
                return getItem(position).viewType;
            }

            @Override
            public ViewHolder onCreateViewHolder(ViewGroup container, int viewType) {
                switch(viewType) {
                    case TYPE_ERROR: {
                        CommandNoteBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_note, container, false);
                        return new ErrorViewHolder(binding);
                    }
                    case TYPE_NOTE: {
                        CommandNoteBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_note, container, false);
                        return new NoteViewHolder(binding);
                    }
                    case TYPE_WEB: {
                        CommandWebviewBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_webview, container, false);
                        return new WebViewHolder(binding);
                    }
                    case TYPE_RESULT_FIELD: {
                        CommandResultFieldBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_result_field, container, false);
                        return new ResultFieldViewHolder(binding);
                    }
                    case TYPE_RESULT_CELL: {
                        CommandResultCellBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_result_cell, container, false);
                        return new ResultCellViewHolder(binding);
                    }
                    case TYPE_ITEM_CARD: {
                        CommandItemCardBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_item_card, container, false);
                        return new ItemCardViewHolder(binding);
                    }
                    case TYPE_CHECKBOX_FIELD: {
                        CommandCheckboxFieldBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_checkbox_field, container, false);
                        return new CheckboxFieldViewHolder(binding);
                    }
                    case TYPE_SEARCH_LIST_FIELD: {
                        CommandSearchListFieldBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_search_list_field, container, false);
                        return new SearchListFieldViewHolder(binding);
                    }
                    case TYPE_RADIO_EDIT_FIELD: {
                        CommandRadioEditFieldBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_radio_edit_field, container, false);
                        return new RadioEditFieldViewHolder(binding);
                    }
                    case TYPE_SPINNER_FIELD: {
                        CommandSpinnerFieldBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_spinner_field, container, false);
                        return new SpinnerFieldViewHolder(binding);
                    }
                    case TYPE_BUTTON_GRID_FIELD: {
                        CommandButtonGridFieldBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_button_grid_field, container, false);
                        return new ButtonGridFieldViewHolder(binding);
                    }
                    case TYPE_TEXT_FIELD: {
                        CommandTextFieldBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_text_field, container, false);
                        return new TextFieldViewHolder(binding);
                    }
                    case TYPE_SLIDER_FIELD: {
                        CommandSliderFieldBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_slider_field, container, false);
                        return new SliderFieldViewHolder(binding);
                    }
                    case TYPE_PROGRESSBAR: {
                        CommandProgressBarBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_progress_bar, container, false);
                        return new ProgressBarViewHolder(binding);
                    }
                    default:
                        if (expectingRemoval) {
                            CommandNoteBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_note, container, false);
                            return new NoteViewHolder(binding);
                        }

                        throw new IllegalArgumentException("Unknown viewType: " + viewType + " based on: " + response + ", " + responseElement + ", " + expectingRemoval);
                }
            }

            @Override
            public void onBindViewHolder(ViewHolder viewHolder, int position) {
                viewHolder.bind(getItem(position));
            }

            public View getView() {
                if (mBinding == null) return null;
                return mBinding.getRoot();
            }

            public boolean validate() {
                int count = getItemCount();
                boolean isValid = true;
                for (int i = 0; i < count; i++) {
                    boolean oneIsValid = getItem(i).validate();
                    isValid = isValid && oneIsValid;
                }
                notifyDataSetChanged();
                return isValid;
            }

            public boolean execute() {
                return execute("execute");
            }

            public boolean execute(int actionPosition) {
                return execute(actionsAdapter.getItem(actionPosition).first);
            }

            public synchronized boolean execute(String action) {
                if (!"cancel".equals(action) && executing) {
                    loadingHasBeenLong = true;
                    notifyDataSetChanged();
                    return false;
                }
                if (!action.equals("cancel") && !action.equals("prev") && !validate()) return false;

                if (response == null) return true;
                Element command = response.findChild("command", "http://jabber.org/protocol/commands");
                if (command == null) return true;
                String status = command.getAttribute("status");
                if (status == null || (!status.equals("executing") && !action.equals("prev"))) return true;

                if (actionToWebview != null && !action.equals("cancel") && Build.VERSION.SDK_INT >= 23) {
                    actionToWebview.postWebMessage(new WebMessage("xmpp_xep0050/" + action), Uri.parse("*"));
                    return false;
                }

                final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
                packet.setTo(response.getFrom());
                final Element c = packet.addChild("command", Namespace.COMMANDS);
                c.setAttribute("node", mNode);
                c.setAttribute("sessionid", command.getAttribute("sessionid"));

                String formType = responseElement == null ? null : responseElement.getAttribute("type");
                if (!action.equals("cancel") &&
                        !action.equals("prev") &&
                        responseElement != null &&
                        responseElement.getName().equals("x") &&
                        responseElement.getNamespace().equals("jabber:x:data") &&
                        formType != null && formType.equals("form")) {

                    Data form = Data.parse(responseElement);
                    eu.siacs.conversations.xmpp.forms.Field actionList = form.getFieldByName("http://jabber.org/protocol/commands#actions");
                    if (actionList != null) {
                        actionList.setValue(action);
                        c.setAttribute("action", "execute");
                    }

                    if (mNode.equals("jabber:iq:register") && xmppConnectionService.isOnboarding() && form.getFieldByName("gateway-jid") != null) {
                        if (form.getValue("gateway-jid") == null) {
                            xmppConnectionService.getPreferences().edit().remove("onboarding_action").commit();
                        } else {
                            xmppConnectionService.getPreferences().edit().putString("onboarding_action", form.getValue("gateway-jid")).commit();
                        }
                    }

                    responseElement.setAttribute("type", "submit");
                    Element rsm = responseElement.findChild("set", "http://jabber.org/protocol/rsm");
                    if (rsm != null) {
                        Element max = new Element("max", "http://jabber.org/protocol/rsm");
                        max.setContent("1000");
                        rsm.addChild(max);
                    }

                    c.addChild(responseElement);
                }

                if (c.getAttribute("action") == null) c.setAttribute("action", action);

                executing = true;
                xmppConnectionService.sendIqPacket(getAccount(), packet, (a, iq) -> {
                    updateWithResponse(iq);
                }, 120L);

                loading();
                return false;
            }

            public void refresh() {
                synchronized(this) {
                    if (waitingForRefresh) notifyDataSetChanged();
                }
            }

            protected void loading() {
                View v = getView();
                try {
                    loadingTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            View v2 = getView();
                            loading = true;

                            try {
                                loadingTimer.schedule(new TimerTask() {
                                    @Override
                                    public void run() {
                                        loadingHasBeenLong = true;
                                        if (v == null && v2 == null) return;
                                        (v == null ? v2 : v).post(() -> notifyDataSetChanged());
                                    }
                                }, 3000);
                            } catch (final IllegalStateException e) { }

                            if (v == null && v2 == null) return;
                            (v == null ? v2 : v).post(() -> notifyDataSetChanged());
                        }
                    }, 500);
                } catch (final IllegalStateException e) { }
            }

            protected GridLayoutManager setupLayoutManager(final Context ctx) {
                int spanCount = 1;

                if (reported != null) {
                    float screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
                    TextPaint paint = ((TextView) LayoutInflater.from(mPager.getContext()).inflate(R.layout.command_result_cell, null)).getPaint();
                    float tableHeaderWidth = reported.stream().reduce(
                            0f,
                            (total, field) -> total + StaticLayout.getDesiredWidth(field.getLabel().or("--------") + "\t", paint),
                            (a, b) -> a + b
                    );

                    spanCount = tableHeaderWidth > 0.59 * screenWidth ? 1 : this.reported.size();
                }

                if (layoutManager != null && layoutManager.getSpanCount() != spanCount) {
                    items.clear();
                    notifyDataSetChanged();
                }

                layoutManager = new GridLayoutManager(ctx, spanCount);
                layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                    @Override
                    public int getSpanSize(int position) {
                        if (getItemViewType(position) != TYPE_RESULT_CELL) return layoutManager.getSpanCount();
                        return 1;
                    }
                });
                return layoutManager;
            }

            protected void setBinding(CommandPageBinding b) {
                mBinding = b;
                // https://stackoverflow.com/a/32350474/8611
                mBinding.form.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
                    @Override
                    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
                        if(rv.getChildCount() > 0) {
                            int[] location = new int[2];
                            rv.getLocationOnScreen(location);
                            View childView = rv.findChildViewUnder(e.getX(), e.getY());
                            if (childView instanceof ViewGroup) {
                                childView = findViewAt((ViewGroup) childView, location[0] + e.getX(), location[1] + e.getY());
                            }
                            int action = e.getAction();
                            switch (action) {
                                case MotionEvent.ACTION_DOWN:
                                    if ((childView instanceof AbsListView && ((AbsListView) childView).canScrollList(1)) || childView instanceof WebView) {
                                        rv.requestDisallowInterceptTouchEvent(true);
                                    }
                                case MotionEvent.ACTION_UP:
                                    if ((childView instanceof AbsListView && ((AbsListView) childView).canScrollList(-11)) || childView instanceof WebView) {
                                        rv.requestDisallowInterceptTouchEvent(true);
                                    }
                            }
                        }

                        return false;
                    }

                    @Override
                    public void onRequestDisallowInterceptTouchEvent(boolean disallow) { }

                    @Override
                    public void onTouchEvent(RecyclerView rv, MotionEvent e) { }
                });
                mBinding.form.setLayoutManager(setupLayoutManager(mBinding.getRoot().getContext()));
                mBinding.form.setAdapter(this);

                if (actionsAdapter == null) {
                    actionsAdapter = new ActionsAdapter(mBinding.getRoot().getContext());
                    actionsAdapter.registerDataSetObserver(new DataSetObserver() {
                        @Override
                        public void onChanged() {
                            if (mBinding == null) return;

                            mBinding.actions.setNumColumns(actionsAdapter.getCount() > 1 ? 2 : 1);
                        }

                        @Override
                        public void onInvalidated() {}
                    });
                }

                mBinding.actions.setAdapter(actionsAdapter);
                mBinding.actions.setOnItemClickListener((parent, v, pos, id) -> {
                    if (execute(pos)) {
                        removeSession(CommandSession.this);
                    }
                });

                actionsAdapter.notifyDataSetChanged();

                if (pendingResponsePacket != null) {
                    final IqPacket pending = pendingResponsePacket;
                    pendingResponsePacket = null;
                    updateWithResponseUiThread(pending);
                }
            }

            private Drawable getDrawableForSVG(SVG svg, Element svgElement, int size) {
                if (svgElement != null && svgElement.getChildren().size() == 1 && svgElement.getChildren().get(0).getName().equals("image"))  {
                    return getDrawableForUrl(svgElement.getChildren().get(0).getAttribute("href"));
                } else {
                    return xmppConnectionService.getFileBackend().drawSVG(svg, size);
                }
            }

            private Drawable getDrawableForUrl(final String url) {
                final LruCache<String, Drawable> cache = xmppConnectionService.getDrawableCache();
                final HttpConnectionManager httpManager = xmppConnectionService.getHttpConnectionManager();
                final Drawable d = cache.get(url);
                if (Build.VERSION.SDK_INT >= 28 && d instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) d).start();
                if (d == null) {
                    synchronized (CommandSession.this) {
                        waitingForRefresh = true;
                    }
                    int size = (int)(xmppConnectionService.getResources().getDisplayMetrics().density * 288);
                    Message dummy = new Message(Conversation.this, url, Message.ENCRYPTION_NONE);
                    dummy.setFileParams(new Message.FileParams(url));
                    httpManager.createNewDownloadConnection(dummy, true, (file) -> {
                        if (file == null) {
                            dummy.getTransferable().start();
                        } else {
                            try {
                                xmppConnectionService.getFileBackend().getThumbnail(file, xmppConnectionService.getResources(), size, false, url);
                            } catch (final Exception e) { }
                        }
                    });
                }
                return d;
            }

            public View inflateUi(Context context, Consumer<ConversationPage> remover) {
                CommandPageBinding binding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.command_page, null, false);
                setBinding(binding);
                return binding.getRoot();
            }

            // https://stackoverflow.com/a/36037991/8611
            private View findViewAt(ViewGroup viewGroup, float x, float y) {
                for(int i = 0; i < viewGroup.getChildCount(); i++) {
                    View child = viewGroup.getChildAt(i);
                    if (child instanceof ViewGroup && !(child instanceof AbsListView) && !(child instanceof WebView)) {
                        View foundView = findViewAt((ViewGroup) child, x, y);
                        if (foundView != null && foundView.isShown()) {
                            return foundView;
                        }
                    } else {
                        int[] location = new int[2];
                        child.getLocationOnScreen(location);
                        Rect rect = new Rect(location[0], location[1], location[0] + child.getWidth(), location[1] + child.getHeight());
                        if (rect.contains((int)x, (int)y)) {
                            return child;
                        }
                    }
                }

                return null;
            }
        }

        class MucConfigSession extends CommandSession {
            MucConfigSession(XmppConnectionService xmppConnectionService) {
                super("Configure Channel", null, xmppConnectionService);
            }

            @Override
            protected void updateWithResponseUiThread(final IqPacket iq) {
                Timer oldTimer = this.loadingTimer;
                this.loadingTimer = new Timer();
                oldTimer.cancel();
                this.executing = false;
                this.loading = false;
                this.loadingHasBeenLong = false;
                this.responseElement = null;
                this.fillableFieldCount = 0;
                this.reported = null;
                this.response = iq;
                this.items.clear();
                this.actionsAdapter.clear();
                layoutManager.setSpanCount(1);

                final Element query = iq.findChild("query", "http://jabber.org/protocol/muc#owner");
                if (iq.getType() == IqPacket.TYPE.RESULT && query != null) {
                    final Data form = Data.parse(query.findChild("x", "jabber:x:data"));
                    final String title = form.getTitle();
                    if (title != null) {
                        mTitle = title;
                        ConversationPagerAdapter.this.notifyDataSetChanged();
                    }

                    this.responseElement = form;
                    setupReported(form.findChild("reported", "jabber:x:data"));
                    if (mBinding != null)
                        mBinding.form.setLayoutManager(setupLayoutManager(mBinding.getRoot().getContext()));

                    if (actionsAdapter.countExceptCancel() < 1) {
                        // Добавляем перевод для кнопки "save"
                        actionsAdapter.add(Pair.create("save", actionsAdapter.ctx.getString(R.string.save)));
                    }

                    if (actionsAdapter.getPosition("cancel") < 0) {
                        // Добавляем перевод для кнопки "cancel"
                        actionsAdapter.insert(Pair.create("cancel", actionsAdapter.ctx.getString(R.string.cancel)), 0);
                    }
                } else if (iq.getType() == IqPacket.TYPE.RESULT) {
                    expectingRemoval = true;
                    removeSession(this);
                    return;
                } else {
                    // Добавляем перевод для кнопки "close"
                    actionsAdapter.add(Pair.create("close", actionsAdapter.ctx.getString(R.string.close)));
                }

                notifyDataSetChanged();
            }

            @Override
            public synchronized boolean execute(String action) {
                if ("cancel".equals(action)) {
                    final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
                    packet.setTo(response.getFrom());
                    final Element form = packet
                            .addChild("query", "http://jabber.org/protocol/muc#owner")
                            .addChild("x", "jabber:x:data");
                    form.setAttribute("type", "cancel");
                    xmppConnectionService.sendIqPacket(getAccount(), packet, null);
                    return true;
                }

                if (!"save".equals(action)) return true;

                final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
                packet.setTo(response.getFrom());

                String formType = responseElement == null ? null : responseElement.getAttribute("type");
                if (responseElement != null &&
                        responseElement.getName().equals("x") &&
                        responseElement.getNamespace().equals("jabber:x:data") &&
                        formType != null && formType.equals("form")) {

                    responseElement.setAttribute("type", "submit");
                    packet
                            .addChild("query", "http://jabber.org/protocol/muc#owner")
                            .addChild(responseElement);
                }

                executing = true;
                xmppConnectionService.sendIqPacket(getAccount(), packet, (a, iq) -> {
                    updateWithResponse(iq);
                }, 120L);

                loading();

                return false;
            }
        }
    }

    public static class Thread {
        protected Message subject = null;
        protected Message first = null;
        protected Message last = null;
        protected final String threadId;

        protected Thread(final String threadId) {
            this.threadId = threadId;
        }

        public String getThreadId() {
            return threadId;
        }

        public String getSubject() {
            if (subject == null) return null;

            return subject.getSubject();
        }

        public String getDisplay() {
            final String s = getSubject();
            if (s != null) return s;

            if (first != null) {
                return first.getBody();
            }

            return "";
        }

        public long getLastTime() {
            if (last == null) return 0;

            return last.getTimeSent();
        }
    }
}
