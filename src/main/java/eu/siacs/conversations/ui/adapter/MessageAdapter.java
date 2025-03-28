package eu.siacs.conversations.ui.adapter;

import static android.view.View.GONE;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static de.monocles.mod.Util.getReadmakerType;
import static eu.siacs.conversations.entities.Message.DELETED_MESSAGE_BODY;
import static eu.siacs.conversations.entities.Message.DELETED_MESSAGE_BODY_OLD;
import static eu.siacs.conversations.persistance.FileBackend.formatTime;
import static eu.siacs.conversations.persistance.FileBackend.safeLongToInt;
import static eu.siacs.conversations.ui.SettingsActivity.PLAY_GIF_INSIDE;
import static eu.siacs.conversations.ui.SettingsActivity.SHOW_LINKS_INSIDE;
import static eu.siacs.conversations.ui.SettingsActivity.SHOW_MAPS_INSIDE;
import static eu.siacs.conversations.ui.util.MyLinkify.removeTrackingParameter;
import static eu.siacs.conversations.ui.util.MyLinkify.removeTrailingBracket;
import static eu.siacs.conversations.ui.util.MyLinkify.replaceYoutube;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.daimajia.swipe.SwipeLayout;
import com.google.common.base.Strings;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.monocles.mod.BobTransfer;
import de.monocles.mod.MessageTextActionModeCallback;
import de.monocles.mod.Util;
import de.monocles.mod.WebxdcPage;
import de.monocles.mod.WebxdcUpdate;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Message.FileParams;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.RtpSessionStatus;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.AudioPlayer;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.services.NotificationService;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.text.DividerSpan;
import eu.siacs.conversations.ui.text.QuoteSpan;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.util.MyLinkify;
import eu.siacs.conversations.ui.util.QuoteHelper;
import eu.siacs.conversations.ui.util.ShareUtil;
import eu.siacs.conversations.ui.util.StyledAttributes;
import eu.siacs.conversations.ui.util.ViewUtil;
import eu.siacs.conversations.ui.widget.ClickableMovementMethod;
import eu.siacs.conversations.ui.widget.RichLinkView;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.Emoticons;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.MessageUtils;
import eu.siacs.conversations.utils.RichPreview;
import eu.siacs.conversations.utils.StylingHelper;
import eu.siacs.conversations.utils.ThemeHelper;
import eu.siacs.conversations.utils.TimeFrameUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.mam.MamReference;
import io.ipfs.cid.Cid;
import me.drakeet.support.toast.ToastCompat;
import me.saket.bettermovementmethod.BetterLinkMovementMethod;


public class MessageAdapter extends ArrayAdapter<Message> {

    public static final String DATE_SEPARATOR_BODY = "DATE_SEPARATOR";
    private static final int SENT = 0;
    private static final int RECEIVED = 1;
    private static final int STATUS = 2;
    private static final int DATE_SEPARATOR = 3;
    private static final int RTP_SESSION = 4;
    boolean isResendable = false;

    private final XmppActivity activity;
    private final AudioPlayer audioPlayer;
    private List<String> highlightedTerm = null;
    private final DisplayMetrics metrics;
    private OnContactPictureClicked mOnContactPictureClickedListener;
    private OnContactPictureClicked mOnMessageBoxClickedListener;
    private OnContactPictureClicked mOnMessageBoxSwipedListener;
    private OnContactPictureLongClicked mOnContactPictureLongClickedListener;
    private OnInlineImageLongClicked mOnInlineImageLongClickedListener;
    private boolean mIndicateReceived = false;
    private boolean mPlayGifInside = false;
    private boolean mShowLinksInside = false;
    private boolean mShowMapsInside = false;
    private final boolean mForceNames;
    private final Map<String, WebxdcUpdate> lastWebxdcUpdate = new HashMap<>();
    private String readmarkervalue;
    private ConversationFragment mConversationFragment = null;
    private boolean expandable;
    private boolean expand;


    public MessageAdapter(final XmppActivity activity, final List<Message> messages, final boolean forceNames) {
        super(activity, 0, messages);
        this.activity = activity;
        this.audioPlayer = new AudioPlayer(this);
        metrics = getContext().getResources().getDisplayMetrics();
        updatePreferences();
        this.mForceNames = forceNames;
    }

    public MessageAdapter(final XmppActivity activity, final List<Message> messages) {
        this(activity, messages, false);
    }

    private static void resetClickListener(View... views) {
        for (View view : views) {
            if (view != null) view.setOnClickListener(null);
        }
    }

    public void flagDisableInputs() {
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    public void flagEnableInputs() {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    public void flagScreenOn() {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void flagScreenOff() {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public boolean autoPauseVoice() {
        return activity.xmppConnectionService.pauseVoiceOnMoveFromEar();
    }

    public void setVolumeControl(final int stream) {
        activity.setVolumeControlStream(stream);
    }

    public void setOnContactPictureClicked(OnContactPictureClicked listener) {
        this.mOnContactPictureClickedListener = listener;
    }

    public void setOnContactPictureLongClicked(
            OnContactPictureLongClicked listener) {
        this.mOnContactPictureLongClickedListener = listener;
    }

    public void setOnInlineImageLongClicked(OnInlineImageLongClicked listener) {
        this.mOnInlineImageLongClickedListener = listener;
    }

    public void setOnMessageBoxSwiped(OnContactPictureClicked listener) {
        this.mOnMessageBoxSwipedListener = listener;
    }

    public void setOnMessageBoxClicked(OnContactPictureClicked listener) {
        this.mOnMessageBoxClickedListener = listener;
    }

    public Activity getActivity() {
        return activity;
    }

    @Override
    public int getViewTypeCount() {
        return 5;
    }

    private int getItemViewType(Message message) {
        if (message.getType() == Message.TYPE_STATUS) {
            if (DATE_SEPARATOR_BODY.equals(message.getBody())) {
                return DATE_SEPARATOR;
            } else {
                return STATUS;
            }
        } else if (message.getType() == Message.TYPE_RTP_SESSION) {
            return RTP_SESSION;
        } else if (message.getStatus() <= Message.STATUS_RECEIVED) {
            return RECEIVED;
        } else {
            return SENT;
        }
    }

    @Override
    public int getItemViewType(int position) {
        return this.getItemViewType(getItem(position));
    }

    private void displayStatus(ViewHolder viewHolder, final Message message, int type, boolean darkBackground) {
        String filesize = null;
        String info = null;
        boolean error = false;
        viewHolder.user.setText(UIHelper.getDisplayedMucCounterpart(message.getCounterpart()));
        if (viewHolder.indicatorReceived != null) {
            viewHolder.indicatorReceived.setVisibility(GONE);
        }
        if (viewHolder.edit_indicator != null) {
            if (message.edited() && message.getModerated() == null) {
                viewHolder.edit_indicator.setVisibility(View.VISIBLE);
                viewHolder.edit_indicator.setImageResource(darkBackground ? R.drawable.ic_mode_edit_white_18dp : R.drawable.ic_mode_edit_black_18dp);
                viewHolder.edit_indicator.setAlpha(darkBackground ? 0.7f : 0.57f);
            } else {
                viewHolder.edit_indicator.setVisibility(GONE);
            }
        }
        if (viewHolder.retract_indicator != null) {
            if (message.getRetractId() != null) {
                viewHolder.retract_indicator.setVisibility(View.VISIBLE);
                viewHolder.retract_indicator.setImageResource(darkBackground ? R.drawable.ic_delete_white_18dp : R.drawable.ic_delete_black_18dp);
                viewHolder.retract_indicator.setAlpha(darkBackground ? 0.7f : 0.57f);
            } else {
                viewHolder.retract_indicator.setVisibility(GONE);
            }
        }
        final Transferable transferable = message.getTransferable();
        boolean multiReceived = message.getConversation().getMode() == Conversation.MODE_MULTI
                && message.getMergedStatus() <= Message.STATUS_RECEIVED;
        boolean singleReceived = message.getConversation().getMode() == Conversation.MODE_SINGLE
                && message.getMergedStatus() <= Message.STATUS_RECEIVED;
        if (message.isFileOrImage() || transferable != null || MessageUtils.unInitiatedButKnownSize(message)) {
            FileParams params = message.getFileParams();
            filesize = params.size != null ? UIHelper.filesizeToString(params.size) : null;
            if (transferable != null && (transferable.getStatus() == Transferable.STATUS_FAILED || transferable.getStatus() == Transferable.STATUS_CANCELLED)) {
                error = true;
            }
        }
        switch (message.getMergedStatus()) {
            case Message.STATUS_WAITING:
                info = getContext().getString(R.string.waiting);
                break;
            case Message.STATUS_UNSEND:
                if (transferable != null) {
                    info = getContext().getString(R.string.sending);
                    showProgress(viewHolder, transferable, message);
                } else {
                    info = getContext().getString(R.string.sending);
                }
                break;
            case Message.STATUS_OFFERED:
                info = getContext().getString(R.string.offering);
                break;
            case Message.STATUS_SEND_RECEIVED:
                if (mIndicateReceived) {
                    if (viewHolder.indicatorReceived != null) {
                        viewHolder.indicatorReceived.setVisibility(View.VISIBLE);
                        viewHolder.indicatorReceived.setImageResource(getReadmakerType(darkBackground, readmarkervalue, Util.ReadmarkerType.RECEIVED));
                        viewHolder.indicatorReceived.setAlpha(darkBackground ? 0.7f : 0.57f);
                    }
                } else {
                    viewHolder.indicatorReceived.setVisibility(GONE);
                }
                break;
            case Message.STATUS_SEND_DISPLAYED:
                if (mIndicateReceived) {
                    if (viewHolder.indicatorReceived != null) {
                        viewHolder.indicatorReceived.setVisibility(View.VISIBLE);
                        viewHolder.indicatorReceived.setImageResource(getReadmakerType(darkBackground, readmarkervalue, Util.ReadmarkerType.DISPLAYED));
                        viewHolder.indicatorReceived.setAlpha(darkBackground ? 0.7f : 0.57f);
                    }
                } else {
                    viewHolder.indicatorReceived.setVisibility(GONE);
                }
                break;
            case Message.STATUS_SEND_FAILED:
                DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
                if (isResendable && file.exists() || message.getResendCount() < activity.xmppConnectionService.maxResendTime()) {
                    info = getContext().getString(R.string.send_failed_resend);
                } else {
                    final String errorMessage = message.getErrorMessage();
                    if (Message.ERROR_MESSAGE_CANCELLED.equals(errorMessage)) {
                        info = getContext().getString(R.string.cancelled);
                    } else {
                        if (errorMessage != null) {
                            final String[] errorParts = errorMessage.split("\\u001f", 2);
                            if (errorParts.length == 2) {
                                switch (errorParts[0]) {
                                    case "file-too-large":
                                        info = getContext().getString(R.string.file_too_large);
                                        break;
                                    default:
                                        info = getContext().getString(R.string.send_failed);
                                        break;
                                }
                            } else {
                                info = getContext().getString(R.string.send_failed);
                            }
                        } else {
                            info = getContext().getString(R.string.send_failed);
                        }
                    }
                }
                error = true;
                break;
            default:
                if (mForceNames || multiReceived || (message.getTrueCounterpart() != null && message.getContact() != null)) {
                    final int shadowSize = 10;
                    viewHolder.username.setVisibility(View.VISIBLE);
                    viewHolder.username.setText(UIHelper.getColoredUsername(activity.xmppConnectionService, message));
                } else if (singleReceived) {
                    viewHolder.username.setVisibility(GONE);
                }
                break;
        }
        if (error && type == SENT) {
            if (darkBackground) {
                viewHolder.time.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Caption_Warning_OnDark);
            } else {
                viewHolder.time.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Caption_Warning);
            }
            DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
            if (error==false && file.exists()) {    //TODO: Improve this quick fix
                if (activity.xmppConnectionService.mHttpConnectionManager.getAutoAcceptFileSize() >= message.getFileParams().size && (transferable != null && transferable.getStatus() == Transferable.STATUS_FAILED)) {
                    isResendable = true;
                    viewHolder.resend_button.setVisibility(GONE);
                } else {
                    isResendable = false;
                    viewHolder.resend_button.setVisibility(GONE);
                    /*
                    viewHolder.resend_button.setVisibility(View.VISIBLE);
                    viewHolder.resend_button.setText(R.string.send_again);
                    viewHolder.resend_button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_resend_grey600_48dp, 0, 0, 0);
                    viewHolder.resend_button.setOnClickListener(v -> mConversationFragment.resendMessage(message));
                    */
                }
            } else {
                isResendable = false;
                viewHolder.resend_button.setVisibility(GONE);
            }
        } else {
            if (darkBackground) {
                viewHolder.time.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Caption_OnDark);
            } else {
                viewHolder.time.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Caption);
            }
            viewHolder.time.setTextColor(ThemeHelper.getMessageTextColor(getContext(), darkBackground, false));
        }
        if (!error && type == SENT) {
            viewHolder.resend_button.setVisibility(GONE);
        }
        if (message.getEncryption() == Message.ENCRYPTION_NONE) {
            viewHolder.indicator.setVisibility(GONE);
        } else {
            boolean verified = false;
            if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
                final FingerprintStatus status = message.getConversation().getAccount().getAxolotlService().getFingerprintTrust(message.getFingerprint());
                if (status != null && status.isVerified()) {
                    verified = true;
                }
            }
            if (verified) {
                viewHolder.indicator.setImageResource(darkBackground ? R.drawable.ic_verified_user_white_18dp : R.drawable.ic_verified_user_black_18dp);
            } else {
                viewHolder.indicator.setImageResource(darkBackground ? R.drawable.ic_lock_white_18dp : R.drawable.ic_lock_black_18dp);
            }
            if (darkBackground) {
                viewHolder.indicator.setAlpha(0.7f);
            } else {
                viewHolder.indicator.setAlpha(0.57f);
            }
            viewHolder.indicator.setVisibility(View.VISIBLE);
        }

        final String formattedTime = UIHelper.readableTimeDifferenceFull(getContext(), message.getMergedTimeSent());
        final String bodyLanguage = message.getBodyLanguage();
        final String bodyLanguageInfo = bodyLanguage == null ? "" : String.format(" \u00B7 %s", bodyLanguage.toUpperCase(Locale.US));
        if (message.getStatus() <= Message.STATUS_RECEIVED) {
            if ((filesize != null) && (info != null)) {
                viewHolder.time.setText(formattedTime + " \u00B7 " + filesize + " \u00B7 " + info + bodyLanguageInfo);
            } else if ((filesize == null) && (info != null)) {
                viewHolder.time.setText(formattedTime + " \u00B7 " + info + bodyLanguageInfo);
            } else if ((filesize != null) && (info == null)) {
                viewHolder.time.setText(formattedTime + " \u00B7 " + filesize + bodyLanguageInfo);
            } else {
                viewHolder.time.setText(formattedTime + bodyLanguageInfo);
            }
        } else {
            if ((filesize != null) && (info != null)) {
                viewHolder.time.setText(filesize + " \u00B7 " + info + bodyLanguageInfo);
            } else if ((filesize == null) && (info != null)) {
                if (error) {
                    viewHolder.time.setText(info + " \u00B7 " + formattedTime + bodyLanguageInfo);
                } else {
                    viewHolder.time.setText(info);
                }
            } else if ((filesize != null) && (info == null)) {
                viewHolder.time.setText(filesize + " \u00B7 " + formattedTime + bodyLanguageInfo);
            } else {
                viewHolder.time.setText(formattedTime + bodyLanguageInfo);
            }
        }
    }

    private void displayInfoMessage(ViewHolder viewHolder, CharSequence text, boolean darkBackground, Message message) {
        viewHolder.download_button.setVisibility(GONE);
        viewHolder.audioPlayer.setVisibility(GONE);
        showImages(false, viewHolder);
        viewHolder.richlinkview.setVisibility(GONE);
        viewHolder.transfer.setVisibility(GONE);
        viewHolder.messageBody.setVisibility(View.VISIBLE);
        viewHolder.messageBody.setText(text);
        showProgress(viewHolder, message.getTransferable(), message);
        if (darkBackground) {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_Secondary_OnDark);
        } else {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_Secondary);
        }
        viewHolder.messageBody.setTextIsSelectable(false);
    }

    private void showProgress(final ViewHolder viewHolder, final Transferable transferable, final Message message) {
        if (transferable != null) {
            if (message.fileIsTransferring()) {
                viewHolder.transfer.setVisibility(View.VISIBLE);
                viewHolder.progressBar.setProgress(transferable.getProgress());
                Drawable icon = activity.getResources().getDrawable(R.drawable.ic_cancel_black_24dp);
                Drawable drawable = DrawableCompat.wrap(icon);
                DrawableCompat.setTint(drawable, StyledAttributes.getColor(getContext(), R.attr.colorAccent));
                viewHolder.cancel_transfer.setImageDrawable(drawable);
                viewHolder.cancel_transfer.setEnabled(true);
                viewHolder.cancel_transfer.setOnClickListener(v -> {
                    try {
                        if (activity instanceof ConversationsActivity) {
                            ConversationFragment conversationFragment = ConversationFragment.get(activity);
                            if (conversationFragment != null) {
                                activity.invalidateOptionsMenu();
                                conversationFragment.cancelTransmission(message);
                            }
                        }
                    } catch (Exception e) {
                        viewHolder.cancel_transfer.setEnabled(false);
                        e.printStackTrace();
                    }
                });
            } else {
                viewHolder.transfer.setVisibility(GONE);
            }
        } else {
            viewHolder.transfer.setVisibility(GONE);
        }
    }

    private void displayEmojiMessage(final ViewHolder viewHolder, final SpannableStringBuilder body, final boolean darkBackground) {
        viewHolder.download_button.setVisibility(GONE);
        viewHolder.audioPlayer.setVisibility(GONE);
        showImages(false, viewHolder);
        viewHolder.richlinkview.setVisibility(GONE);
        viewHolder.transfer.setVisibility(GONE);
        viewHolder.messageBody.setVisibility(View.VISIBLE);
        if (darkBackground) {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_Emoji_OnDark);
        } else {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_Emoji);
        }
        ImageSpan[] imageSpans = body.getSpans(0, body.length(), ImageSpan.class);
        float size = imageSpans.length == 1 || Emoticons.isEmoji(body.toString()) ? 5.0f : 3.0f;
        body.setSpan(new RelativeSizeSpan(size), 0, body.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        viewHolder.messageBody.setText(body);
    }

    private void displayXmppMessage(final ViewHolder viewHolder, final String body) {
        String contact = body.toLowerCase();
        contact = contact.split(":")[1];
        boolean group;
        try {
            group = ((contact.split("\\?")[1]) != null && (contact.split("\\?")[1]).length() > 0 && (contact.split("\\?")[1]).equalsIgnoreCase("join"));
        } catch (Exception e) {
            group = false;
        }
        contact = contact.split("\\?")[0];
        final String add_contact = activity.getString(R.string.add_to_contact_list) + " (" + contact + ")";
        viewHolder.audioPlayer.setVisibility(GONE);
        viewHolder.download_button.setVisibility(View.VISIBLE);
        viewHolder.download_button.setText(add_contact);
        if (group) {
            final Drawable icon = activity.getResources().getDrawable(R.drawable.ic_account_multiple_plus_grey600_48dp);
            final Drawable drawable = DrawableCompat.wrap(icon);
            DrawableCompat.setTint(drawable, StyledAttributes.getColor(getContext(), R.attr.colorAccent));
            viewHolder.download_button.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
        } else {
            final Drawable icon = activity.getResources().getDrawable(R.drawable.ic_account_plus_grey600_48dp);
            final Drawable drawable = DrawableCompat.wrap(icon);
            DrawableCompat.setTint(drawable, StyledAttributes.getColor(getContext(), R.attr.colorAccent));
            viewHolder.download_button.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
        }
        viewHolder.download_button.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(body));
                activity.startActivity(intent);
                activity.overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
            } catch (Exception e) {
                ToastCompat.makeText(activity, R.string.no_application_found_to_view_contact, ToastCompat.LENGTH_LONG).show();
            }

        });
        showImages(false, viewHolder);
        viewHolder.richlinkview.setVisibility(GONE);
        viewHolder.transfer.setVisibility(GONE);
        viewHolder.messageBody.setVisibility(GONE);
    }

    private void applyQuoteSpan(
            final TextView textView,
            Editable body,
            int start,
            int end,
            boolean darkBackground,
            final boolean makeEdits) {
        if (makeEdits && start > 1 && !"\n\n".equals(body.subSequence(start - 2, start).toString())) {
            body.insert(start++, "\n");
            body.setSpan(
                    new DividerSpan(false), start - 2, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            end++;
        }
        if (makeEdits && end < body.length() - 1 && !"\n\n".equals(body.subSequence(end, end + 2).toString())) {
            body.insert(end, "\n");
            body.setSpan(
                    new DividerSpan(false),
                    end,
                    end + ("\n".equals(body.subSequence(end + 1, end + 2).toString()) ? 2 : 1),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        int color = ThemeHelper.messageTextColor(activity);
        final DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        body.setSpan(new QuoteSpan(color, metrics), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    public boolean handleTextQuotes(final TextView textView, final Editable body) {
        return handleTextQuotes(textView, body, true);
    }

    public boolean handleTextQuotes(final TextView textView, final Editable body, final boolean deleteMarkers) {
        boolean darkBackground = activity.isDarkTheme();
        return handleTextQuotes(textView, body, darkBackground, deleteMarkers);
    }
    /**
     * Applies QuoteSpan to group of lines which starts with > or » characters. Appends likebreaks
     * and applies DividerSpan to them to show a padding between quote and text.
     */
    public boolean handleTextQuotes(
            final TextView textView,
            final Editable body,
            final boolean darkBackground,
            final boolean deleteMarkers) {
        boolean startsWithQuote = false;
        int quoteDepth = 1;
        while (QuoteHelper.bodyContainsQuoteStart(body) && quoteDepth <= Config.QUOTE_MAX_DEPTH) {
            char previous = '\n';
            int lineStart = -1;
            int lineTextStart = -1;
            int quoteStart = -1;
            int skipped = 0;
            for (int i = 0; i <= body.length(); i++) {
                if (!deleteMarkers && QuoteHelper.isRelativeSizeSpanned(body, i)) {
                    skipped++;
                    continue;
                }
                char current = body.length() > i ? body.charAt(i) : '\n';
                if (lineStart == -1) {
                    if (previous == '\n') {
                        if (i < body.length() && QuoteHelper.isPositionQuoteStart(body, i)) {
                            // Line start with quote
                            lineStart = i;
                            if (quoteStart == -1) quoteStart = i - skipped;
                            if (i == 0) startsWithQuote = true;
                        } else if (quoteStart >= 0) {
                            // Line start without quote, apply spans there
                            applyQuoteSpan(textView, body, quoteStart, i - 1, darkBackground, deleteMarkers);
                            quoteStart = -1;
                        }
                    }
                } else {
                    // Remove extra spaces between > and first character in the line
                    // > character will be removed too
                    if (current != ' ' && lineTextStart == -1) {
                        lineTextStart = i;
                    }
                    if (current == '\n') {
                        if (deleteMarkers) {
                            i -= lineTextStart - lineStart;
                            body.delete(lineStart, lineTextStart);
                            if (i == lineStart) {
                                // Avoid empty lines because span over empty line can be hidden
                                body.insert(i++, " ");
                            }
                        } else {
                            body.setSpan(new RelativeSizeSpan(i - (lineTextStart - lineStart) == lineStart ? 1 : 0), lineStart, lineTextStart, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE | StylingHelper.XHTML_REMOVE << Spanned.SPAN_USER_SHIFT);
                        }
                        lineStart = -1;
                        lineTextStart = -1;
                    }
                }
                previous = current;
                skipped = 0;
            }
            if (quoteStart >= 0) {
                // Apply spans to finishing open quote
                applyQuoteSpan(textView, body, quoteStart, body.length(), darkBackground, deleteMarkers);
            }
            quoteDepth++;
        }
        return startsWithQuote;
    }

    private SpannableStringBuilder getSpannableBody(final Message message) {
        Drawable fallbackImg = ResourcesCompat.getDrawable(activity.getResources(), activity.getThemeResource(R.attr.ic_attach_photo, R.drawable.ic_attach_photo), null);
        return replaceYoutube(activity.getApplicationContext(), message.getMergedBody((cid) -> {
            try {
                DownloadableFile f = activity.xmppConnectionService.getFileForCid(cid);
                if (f == null || !f.canRead()) {
                    if (!message.trusted() && !message.getConversation().canInferPresence()) return null;

                    try {
                        new BobTransfer(BobTransfer.uri(cid), message.getConversation().getAccount(), message.getCounterpart(), activity.xmppConnectionService).start();
                    } catch (final NoSuchAlgorithmException | URISyntaxException e) { }
                    return null;
                }

                Drawable d = activity.xmppConnectionService.getFileBackend().getThumbnail(f, activity.getResources(), (int) (metrics.density * 288), true);
                if (d == null) {
                    new ThumbnailTask().execute(f);
                }
                return d;
            } catch (final IOException e) {
                return null;
            }
        }, fallbackImg));
    }

    private void displayTextMessage(final ViewHolder viewHolder,
                                    final Message message, boolean darkBackground, int type) {
        viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.richlinkview.setVisibility(View.GONE);
        viewHolder.transfer.setVisibility(View.GONE);
        viewHolder.audioPlayer.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.GONE);
        viewHolder.images.setVisibility(View.GONE);

        Log.d("ChatDebug", "Starting displayTextMessage for message: " + message.getBody());

        if (darkBackground) {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_OnDark);
        } else {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1);
        }
        viewHolder.messageBody.setHighlightColor(darkBackground ? type == SENT ?
                StyledAttributes.getColor(activity, R.attr.colorAccent) :
                StyledAttributes.getColor(activity, R.attr.colorAccent) :
                StyledAttributes.getColor(activity, R.attr.colorAccent));
        viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);

        if (message.getBody() != null && !message.getBody().equals("")) {
            Log.d("ChatDebug", "Processing message: " + message.getBody());
            viewHolder.messageBody.setTextIsSelectable(true);
            viewHolder.messageBody.setVisibility(View.VISIBLE);

            String trimmedBody = message.getBody().trim();
            Log.d("ChatDebug", "Trimmed body: " + trimmedBody);

            String imageUrl = extractFirstImageUrl(trimmedBody);
            Log.d("ChatDebug", "Extracted image URL: " + imageUrl);

            if (imageUrl != null && isDirectImageUrl(imageUrl)) {
                Log.d("ChatDebug", "Image URL detected: " + imageUrl);
                viewHolder.images.setVisibility(View.VISIBLE);
                viewHolder.image.setVisibility(View.VISIBLE);

                final float maxWidth = activity.getResources().getDimension(R.dimen.image_preview_width);
                final float maxHeight = maxWidth * 1.5f;

                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                layoutParams.setMargins(0, (int) (metrics.density * 4), 0, (int) (metrics.density * 4));
                viewHolder.images.setLayoutParams(layoutParams);

                Glide.with(activity)
                        .load(imageUrl)
                        .override((int) maxWidth, (int) maxHeight)
                        .fitCenter()
                        .placeholder(R.drawable.ic_image_grey600_48dp)
                        .error(R.drawable.ic_error_white_24dp)
                        .listener(new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                Log.e("GlideDebug", "Failed to load image: " + imageUrl + ", error: " + (e != null ? e.getMessage() : "unknown"));
                                viewHolder.images.setVisibility(View.GONE);
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                Log.d("GlideDebug", "Image loaded successfully: " + imageUrl);
                                viewHolder.images.setVisibility(View.VISIBLE);
                                viewHolder.image.setVisibility(View.VISIBLE);

                                int intrinsicWidth = resource.getIntrinsicWidth();
                                int intrinsicHeight = resource.getIntrinsicHeight();
                                float aspectRatio = (float) intrinsicWidth / intrinsicHeight;

                                int finalWidth, finalHeight;
                                if (intrinsicWidth > maxWidth || intrinsicHeight > maxHeight) {
                                    if (aspectRatio > 1) {
                                        finalWidth = (int) maxWidth;
                                        finalHeight = (int) (maxWidth / aspectRatio);
                                    } else {
                                        finalHeight = (int) maxHeight;
                                        finalWidth = (int) (maxHeight * aspectRatio);
                                    }
                                } else {
                                    finalWidth = intrinsicWidth;
                                    finalHeight = intrinsicHeight;
                                }

                                finalWidth = Math.max(finalWidth, (int) (maxWidth * 0.5f));
                                finalHeight = Math.max(finalHeight, (int) (maxWidth * 0.5f));

                                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                                        finalWidth,
                                        finalHeight
                                );
                                viewHolder.image.setLayoutParams(imageParams);

                                Log.d("ChatDebug", "Image dimensions - width: " + finalWidth + ", height: " + finalHeight);
                                return false;
                            }
                        })
                        .into(viewHolder.image);

                viewHolder.image.setOnClickListener(v -> {
                    Uri uri = Uri.parse(imageUrl);
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, "image/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    if (intent.resolveActivity(activity.getPackageManager()) != null) {
                        activity.startActivity(intent);
                        activity.overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                    } else {
                        ToastCompat.makeText(activity, R.string.error_message, ToastCompat.LENGTH_LONG).show();
                    }
                });

                if (!message.getBody().equals(trimmedBody)) {
                    SpannableStringBuilder body = getSpannableBody(message);
                    viewHolder.messageBody.setText(body);
                } else {
                    viewHolder.messageBody.setVisibility(View.GONE);
                }
            }

            // Обработка текста сообщения
            SpannableStringBuilder body = getSpannableBody(message);
            boolean hasMeCommand = message.hasMeCommand();
            final SpannableString nick = UIHelper.getColoredUsername(activity.xmppConnectionService, message);

            if (hasMeCommand) {
                body = body.replace(0, Message.ME_COMMAND.length(), nick);
            }

            // Обработка цитат
            final boolean startsWithQuote = handleTextQuotes(viewHolder.messageBody, body, darkBackground, true);
            for (final android.text.style.QuoteSpan quote : body.getSpans(0, body.length(), android.text.style.QuoteSpan.class)) {
                int start = body.getSpanStart(quote);
                int end = body.getSpanEnd(quote);
                body.removeSpan(quote);
                applyQuoteSpan(viewHolder.messageBody, body, start, end, darkBackground, true);
            }

            // Стилизация для /me
            if (!message.isPrivateMessage() && hasMeCommand) {
                body.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 0, nick.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            MyLinkify.addLinks(body, message.getConversation().getAccount(), message.getConversation().getJid());
            viewHolder.messageBody.setText(body);
            viewHolder.messageBody.setAutoLinkMask(0);
            viewHolder.messageBody.setMovementMethod(BetterLinkMovementMethod.getInstance());
        } else {
            Log.d("ChatDebug", "Message is empty");
            viewHolder.messageBody.setText("");
            viewHolder.messageBody.setTextIsSelectable(false);
            toggleWhisperInfo(viewHolder, message, false, darkBackground);
            viewHolder.images.setVisibility(View.GONE);
        }
    }

    private boolean isDirectImageUrl(String url) {
        if (url == null) return false;
        boolean matches = url.matches("(?i)^(http|https)://.*(\\.(jpg|jpeg|png|gif|webp|bmp|tiff|ico|webm|heif|heic|apng)(\\?.*)?$|\\?q=tbn:.*)");
        Log.d("ChatDebug", "Checking URL: " + url + ", isDirectImageUrl: " + matches);
        return matches;
    }

    private String extractFirstImageUrl(String text) {
        if (text == null) return null;
        // Обновлённое регулярное выражение
        Pattern pattern = Pattern.compile("(?i)(http|https)://[^\\s]+?\\.(jpg|jpeg|png|gif|webp|bmp|tiff|ico|webm|heif|heic|apng)(?:\\?.*?)?(?=\\s|$)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String url = matcher.group();
            Log.d("ChatDebug", "Found potential image URL: " + url);
            return url;
        }
        return null;
    }

    private void displayDownloadableMessage(ViewHolder viewHolder, final Message message, String text, final boolean darkBackground, final int type) {
        displayTextMessage(viewHolder, message, darkBackground, type);
        viewHolder.image.setVisibility(GONE);
        List<Element> thumbs = message.getFileParams() != null ? message.getFileParams().getThumbnails() : null;
        if (thumbs != null && !thumbs.isEmpty()) {
            for (Element thumb : thumbs) {
                Uri uri = Uri.parse(thumb.getAttribute("uri"));
                if (uri.getScheme().equals("data")) {
                    String[] parts = uri.getSchemeSpecificPart().split(",", 2);
                    parts = parts[0].split(";");
                    if (!parts[0].equals("image/blurhash") && !parts[0].equals("image/thumbhash") && !parts[0].equals("image/jpeg") && !parts[0].equals("image/png") && !parts[0].equals("image/webp") && !parts[0].equals("image/gif")) continue;
                } else if (uri.getScheme().equals("cid")) {
                    Cid cid = BobTransfer.cid(uri);
                    if (cid == null) continue;
                    DownloadableFile f = activity.xmppConnectionService.getFileForCid(cid);
                    if (f == null || !f.canRead()) {
                        if (!message.trusted() && !message.getConversation().canInferPresence()) continue;

                        try {
                            new BobTransfer(BobTransfer.uri(cid), message.getConversation().getAccount(), message.getCounterpart(), activity.xmppConnectionService).start();
                        } catch (final NoSuchAlgorithmException | URISyntaxException e) { }
                        continue;
                    }
                } else {
                    continue;
                }

                int width = message.getFileParams().width;
                if (width < 1 && thumb.getAttribute("width") != null) width = Integer.parseInt(thumb.getAttribute("width"));
                if (width < 1) width = 1920;

                int height = message.getFileParams().height;
                if (height < 1 && thumb.getAttribute("height") != null) height = Integer.parseInt(thumb.getAttribute("height"));
                if (height < 1) height = 1080;

                viewHolder.image.setVisibility(View.VISIBLE);
                imagePreviewLayout(width, height, viewHolder.image);
                activity.loadBitmap(message, viewHolder.image);
                viewHolder.image.setOnClickListener(v -> ConversationFragment.downloadFile(activity, message));

                break;
            }
        }
        viewHolder.audioPlayer.setVisibility(GONE);
        showImages(false, viewHolder);
        viewHolder.richlinkview.setVisibility(GONE);
        viewHolder.transfer.setVisibility(GONE);
        viewHolder.download_button.setVisibility(View.VISIBLE);
        viewHolder.download_button.setText(text);
        final Drawable icon = activity.getResources().getDrawable(R.drawable.ic_download_grey600_48dp);
        final Drawable drawable = DrawableCompat.wrap(icon);
        DrawableCompat.setTint(drawable, StyledAttributes.getColor(getContext(), R.attr.colorAccent));
        viewHolder.download_button.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
        viewHolder.download_button.setOnClickListener(v -> ConversationFragment.downloadFile(activity, message));
    }


    private void displayWebxdcMessage(ViewHolder viewHolder, final Message message, final boolean darkBackground, final int type) {
        Cid webxdcCid = message.getFileParams().getCids().get(0);
        WebxdcPage webxdc = new WebxdcPage(activity, webxdcCid, message, activity.xmppConnectionService);
        displayTextMessage(viewHolder, message, darkBackground, type);
        viewHolder.image.setVisibility(GONE);
        viewHolder.audioPlayer.setVisibility(GONE);
        viewHolder.download_button.setVisibility(View.VISIBLE);
        viewHolder.download_button.setText(activity.getResources().getString(R.string.open) + " " + webxdc.getName());
        viewHolder.download_button.setOnClickListener(v -> {
            Conversation conversation = (Conversation) message.getConversation();
            if (!conversation.switchToSession("webxdc\0" + message.getUuid())) {
                conversation.startWebxdc(webxdc);
            }
        });

        final WebxdcUpdate lastUpdate;
        synchronized(lastWebxdcUpdate) { lastUpdate = lastWebxdcUpdate.get(message.getUuid()); }
        if (lastUpdate == null) {
            new Thread(() -> {
                final WebxdcUpdate update = activity.xmppConnectionService.findLastWebxdcUpdate(message);
                if (update != null) {
                    synchronized(lastWebxdcUpdate) { lastWebxdcUpdate.put(message.getUuid(), update); }
                    activity.xmppConnectionService.updateConversationUi();
                }
            }).start();
        } else {
            if (lastUpdate != null && (lastUpdate.getSummary() != null || lastUpdate.getDocument() != null)) {
                viewHolder.messageBody.setVisibility(View.VISIBLE);
                viewHolder.messageBody.setText(
                        (lastUpdate.getDocument() == null ? "" : lastUpdate.getDocument() + "\n") +
                                (lastUpdate.getSummary() == null ? "" : lastUpdate.getSummary())
                );
            }
        }

        final LruCache<String, Drawable> cache = activity.xmppConnectionService.getDrawableCache();
        final Drawable d = cache.get("webxdc:icon:" + webxdcCid);
        if (d == null) {
            new Thread(() -> {
                Drawable icon = webxdc.getIcon();
                if (icon != null) {
                    cache.put("webxdc:icon:" + webxdcCid, icon);
                    activity.xmppConnectionService.updateConversationUi();
                }
            }).start();
        } else {
            viewHolder.images.setVisibility(View.VISIBLE);
            viewHolder.image.setVisibility(View.VISIBLE);
            viewHolder.image.setImageDrawable(d);
        }
    }

    private void displayOpenableMessage(ViewHolder viewHolder, final Message message, final boolean darkBackground, final int type) {
        displayTextMessage(viewHolder, message, darkBackground, type);
        viewHolder.download_button.setVisibility(View.VISIBLE);
        viewHolder.audioPlayer.setVisibility(GONE);
        showImages(false, viewHolder);
        viewHolder.richlinkview.setVisibility(GONE);
        viewHolder.transfer.setVisibility(GONE);
        final String mimeType = message.getMimeType();
        if (mimeType != null && message.getMimeType().contains("vcard")) {
            try {
                showVCard(message, viewHolder);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (mimeType != null && message.getMimeType().contains("calendar")) {
            final Drawable icon = activity.getResources().getDrawable(R.drawable.ic_calendar_grey600_48dp);
            final Drawable drawable = DrawableCompat.wrap(icon);
            DrawableCompat.setTint(drawable, StyledAttributes.getColor(getContext(), R.attr.colorAccent));
            viewHolder.download_button.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
            viewHolder.download_button.setText(activity.getString(R.string.open_x_file, UIHelper.getFileDescriptionString(activity, message)));
        } else if (mimeType != null && message.getMimeType().equals("application/vnd.android.package-archive")) {
            try {
                showAPK(message, viewHolder);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (mimeType != null && message.getMimeType().contains("video")) {
            final Drawable icon = activity.getResources().getDrawable(R.drawable.ic_video_grey600_48dp);
            final Drawable drawable = DrawableCompat.wrap(icon);
            DrawableCompat.setTint(drawable, StyledAttributes.getColor(getContext(), R.attr.colorAccent));
            viewHolder.download_button.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
            viewHolder.download_button.setText(activity.getString(R.string.open_x_file, UIHelper.getFileDescriptionString(activity, message)));
        } else if (mimeType != null && message.getMimeType().contains("image")) {
            final Drawable icon = activity.getResources().getDrawable(R.drawable.ic_image_grey600_48dp);
            final Drawable drawable = DrawableCompat.wrap(icon);
            DrawableCompat.setTint(drawable, StyledAttributes.getColor(getContext(), R.attr.colorAccent));
            viewHolder.download_button.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
            viewHolder.download_button.setText(activity.getString(R.string.open_x_file, UIHelper.getFileDescriptionString(activity, message)));
        } else if (mimeType != null && message.getMimeType().contains("audio")) {
            final Drawable icon = activity.getResources().getDrawable(R.drawable.ic_audio_grey600_48dp);
            final Drawable drawable = DrawableCompat.wrap(icon);
            DrawableCompat.setTint(drawable, StyledAttributes.getColor(getContext(), R.attr.colorAccent));
            viewHolder.download_button.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
            viewHolder.download_button.setText(activity.getString(R.string.open_x_file, UIHelper.getFileDescriptionString(activity, message)));
        } else {
            final Drawable icon = activity.getResources().getDrawable(R.drawable.ic_file_grey600_48dp);
            final  Drawable drawable = DrawableCompat.wrap(icon);
            DrawableCompat.setTint(drawable, StyledAttributes.getColor(getContext(), R.attr.colorAccent));
            viewHolder.download_button.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
            viewHolder.download_button.setText(activity.getString(R.string.open_x_file, UIHelper.getFileDescriptionString(activity, message)));
        }
        viewHolder.download_button.setOnClickListener(v -> openDownloadable(message));
    }

    private void showAPK(final Message message, final ViewHolder viewHolder) {
        String APKName = "";
        if (message.getFileParams().subject.length() != 0) {
            try {
                byte[] data = Base64.decode(message.getFileParams().subject, Base64.DEFAULT);
                APKName = new String(data, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                APKName = "";
                e.printStackTrace();
            }
        }
        final Drawable icon = activity.getResources().getDrawable(R.drawable.ic_android_grey600_48dp);
        final Drawable drawable = DrawableCompat.wrap(icon);
        DrawableCompat.setTint(drawable, StyledAttributes.getColor(getContext(), R.attr.colorAccent));
        viewHolder.download_button.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
        viewHolder.download_button.setText(activity.getString(R.string.open_x_file, UIHelper.getFileDescriptionString(activity, message) + APKName));
    }

    private void showVCard(final Message message, ViewHolder viewHolder) {
        String VCardName = "";
        if (message.getFileParams().subject.length() != 0) {
            try {
                byte[] data = Base64.decode(message.getFileParams().subject, Base64.DEFAULT);
                VCardName = new String(data, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                VCardName = "";
                e.printStackTrace();
            }
        }
        final  Drawable icon = activity.getResources().getDrawable(R.drawable.ic_account_card_details_grey600_48dp);
        final  Drawable drawable = DrawableCompat.wrap(icon);
        DrawableCompat.setTint(drawable, StyledAttributes.getColor(getContext(), R.attr.colorAccent));
        viewHolder.download_button.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
        viewHolder.download_button.setText(activity.getString(R.string.open_x_file, UIHelper.getFileDescriptionString(activity, message) + VCardName));
    }

    private void displayRichLinkMessage(final ViewHolder viewHolder, final Message message, boolean darkBackground) {
        toggleWhisperInfo(viewHolder, message, true, darkBackground);
        viewHolder.audioPlayer.setVisibility(GONE);
        showImages(false, viewHolder);
        viewHolder.download_button.setVisibility(GONE);
        viewHolder.transfer.setVisibility(GONE);
        String url;
        if (message.isWebUri()) {
            url = removeTrackingParameter(Uri.parse(message.getBody().trim())).toString();
        } else {
            url = removeTrackingParameter(Uri.parse(message.getWebUri())).toString();
        }
        final String link = replaceYoutube(activity.getApplicationContext(), url);
        Log.d(Config.LOGTAG, "Weburi body for preview: " + link);
        final boolean dataSaverDisabled = activity.xmppConnectionService.isDataSaverDisabled();
        viewHolder.richlinkview.setVisibility(mShowLinksInside ? View.VISIBLE : GONE);
        if (mShowLinksInside) {
            final int color = ThemeHelper.messageTextColor(activity);
            final float target = activity.getResources().getDimension(R.dimen.image_preview_width);
            final int scaledH;
            if (Math.max(100, 100) * metrics.density <= target) {
                scaledH = (int) (100 * metrics.density);
            } else if (Math.max(100, 100) <= target) {
                scaledH = 100;
            } else {
                scaledH = (int) (100 / ((double) 100 / target));
            }
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            layoutParams.setMargins(0, (int) (metrics.density * 4), 0, (int) (metrics.density * 4));
            viewHolder.richlinkview.setLayoutParams(layoutParams);
            viewHolder.richlinkview.setMinimumHeight(scaledH);
            final String weburl;
            if (link.startsWith("http://") || link.startsWith("https://")) {
                weburl = removeTrailingBracket(link);
            } else {
                weburl = "http://" + removeTrailingBracket(link);
            }
            Log.d(Config.LOGTAG, "Weburi for preview: " + weburl);
            viewHolder.richlinkview.setLink(weburl, message.getUuid(), dataSaverDisabled, activity.xmppConnectionService, color, new RichPreview.ViewListener() {

                @Override
                public void onSuccess(boolean status) {
                }

                @Override
                public void onError(Exception e) {
                    e.printStackTrace();
                    viewHolder.richlinkview.setVisibility(GONE);
                }
            });
        }
    }

    private void displayLocationMessage(ViewHolder viewHolder, final Message message, final boolean darkBackground, final int type) {
        displayTextMessage(viewHolder, message, darkBackground, type);
        viewHolder.audioPlayer.setVisibility(GONE);
        final String url = GeoHelper.MapPreviewUri(message, activity);
        showImages(false, viewHolder);
        viewHolder.richlinkview.setVisibility(GONE);
        viewHolder.transfer.setVisibility(GONE);
        if (mShowMapsInside) {
            showImages(mShowMapsInside, 0, false, viewHolder);
            final double target = activity.getResources().getDimension(R.dimen.image_preview_width);
            final int scaledW;
            final int scaledH;
            if (Math.max(500, 500) * metrics.density <= target) {
                scaledW = (int) (500 * metrics.density);
                scaledH = (int) (500 * metrics.density);
            } else if (Math.max(500, 500) <= target) {
                scaledW = 500;
                scaledH = 500;
            } else {
                scaledW = (int) target;
                scaledH = (int) (500 / ((double) 500 / target));
            }
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(scaledW, scaledH);
            layoutParams.setMargins(0, (int) (metrics.density * 4), 0, (int) (metrics.density * 4));
            viewHolder.images.setLayoutParams(layoutParams);
            viewHolder.image.setOnClickListener(v -> showLocation(message));
            Glide.with(activity)
                    .load(Uri.parse(url))
                    .placeholder(R.drawable.ic_map_marker_grey600_48dp)
                    .error(R.drawable.ic_map_marker_grey600_48dp)
                    .into(viewHolder.image);
            viewHolder.image.setMaxWidth(500);
            viewHolder.image.setAdjustViewBounds(true);
            viewHolder.download_button.setVisibility(GONE);
        } else {
            showImages(false, viewHolder);
            viewHolder.download_button.setVisibility(View.VISIBLE);
            viewHolder.download_button.setText(R.string.show_location);
            final Drawable icon = activity.getResources().getDrawable(R.drawable.ic_map_marker_grey600_48dp);
            final Drawable drawable = DrawableCompat.wrap(icon);
            DrawableCompat.setTint(drawable, StyledAttributes.getColor(getContext(), R.attr.colorAccent));
            viewHolder.download_button.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
            viewHolder.download_button.setOnClickListener(v -> showLocation(message));
        }
    }

    private void displayAudioMessage(ViewHolder viewHolder, Message message, boolean darkBackground, final int type) {
        final Resources res = activity.getResources();
        viewHolder.messageBody.setWidth((int) res.getDimension(R.dimen.audio_player_width));
        displayTextMessage(viewHolder, message, darkBackground, type);
        showImages(false, viewHolder);
        viewHolder.richlinkview.setVisibility(GONE);
        viewHolder.transfer.setVisibility(GONE);
        viewHolder.download_button.setVisibility(GONE);
        final RelativeLayout audioPlayer = viewHolder.audioPlayer;
        audioPlayer.setVisibility(View.VISIBLE);
        AudioPlayer.ViewHolder.get(audioPlayer).setTheme(darkBackground);
        this.audioPlayer.init(audioPlayer, message);
    }

    private boolean showTitle(Message message) {
        boolean show = false;
        if (message.getFileParams().subject.length() != 0) {
            try {
                byte[] data = Base64.decode(message.getFileParams().subject, Base64.DEFAULT);
                show = (new String(data, "UTF-8").length() != 0);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return show;
    }

    private String getTitle(Message message) {
        if (message.getFileParams().subject.length() != 0) {
            try {
                byte[] data = Base64.decode(message.getFileParams().subject, Base64.DEFAULT);
                return new String(data, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    private void displayMediaPreviewMessage(ViewHolder viewHolder, final Message message, final boolean darkBackground, final int type) {
        displayTextMessage(viewHolder, message, darkBackground, type);
        viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.audioPlayer.setVisibility(View.GONE);
        viewHolder.richlinkview.setVisibility(View.GONE);
        viewHolder.transfer.setVisibility(View.GONE);

        final DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
        if (file != null && !file.exists() && !message.isFileDeleted()) {
            new Thread(new markFileDeletedFinisher(message, activity)).start();
            displayInfoMessage(viewHolder, activity.getString(R.string.file_deleted), darkBackground, message);
            ToastCompat.makeText(activity, R.string.file_deleted, ToastCompat.LENGTH_SHORT).show();
            return;
        }

        final String mime = file.getMimeType();
        final boolean isGif = mime != null && mime.equals("image/gif");
        final int mediaRuntime = message.getFileParams().runtime;

        // Устанавливаем максимальные размеры превью
        final float maxWidth = activity.getResources().getDimension(R.dimen.image_preview_width);
        final float maxHeight = maxWidth * 1.5f; // Ограничение по высоте (настраиваемое)

        FileParams params = message.getFileParams();
        int width = params.width > 0 ? params.width : 1920; // Значение по умолчанию, если ширина неизвестна
        int height = params.height > 0 ? params.height : 1080; // Значение по умолчанию, если высота неизвестна
        float aspectRatio = (float) width / height;

        // Вычисляем размеры с учётом максимальной ширины и соотношения сторон
        int scaledW = (int) maxWidth;
        int scaledH = (int) (maxWidth / aspectRatio);
        if (scaledH > maxHeight) {
            scaledH = (int) maxHeight;
            scaledW = (int) (maxHeight * aspectRatio);
        }

        // Уменьшаем размеры для GIF
        if (isGif) {
            scaledW = (int) (scaledW * 0.5f); // Уменьшаем ширину на 30%
            scaledH = (int) (scaledH * 0.5f); // Уменьшаем высоту на 30%
        }

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(scaledW, scaledH);
        layoutParams.setMargins(0, (int) (metrics.density * 4), 0, (int) (metrics.density * 4));
        viewHolder.images.setLayoutParams(layoutParams);

        if (isGif && mPlayGifInside) {
            showImages(true, mediaRuntime, true, viewHolder);
            Log.d(Config.LOGTAG, "Gif Image file");

            Glide.with(activity)
                    .load(file)
                    .override(scaledW, scaledH)
                    .fitCenter() // Сохраняем пропорции
                    .into(viewHolder.image);
        } else {
            showImages(true, mediaRuntime, false, viewHolder);

            Glide.with(activity)
                    .load(file)
                    .override(scaledW, scaledH)
                    .fitCenter() // Сохраняем пропорции
                    .into(viewHolder.image);
        }

        viewHolder.image.setOnClickListener(v -> openDownloadable(message));
    }
    private void imagePreviewLayout(int w, int h, ImageView image) {
        final float target = activity.getResources().getDimension(R.dimen.image_preview_width);
        final int scaledW;
        final int scaledH;
        if (Math.max(h, w) * metrics.density <= target) {
            scaledW = (int) (w * metrics.density);
            scaledH = (int) (h * metrics.density);
        } else if (Math.max(h, w) <= target) {
            scaledW = w;
            scaledH = h;
        } else if (w <= h) {
            scaledW = (int) (w / ((double) h / target));
            scaledH = (int) target;
        } else {
            scaledW = (int) target;
            scaledH = (int) (h / ((double) w / target));
        }
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(scaledW, scaledH);
        layoutParams.setMargins(0, (int) (metrics.density * 4), 0, (int) (metrics.density * 4));
        image.setLayoutParams(layoutParams);
    }

    private void showImages(final boolean show, final ViewHolder viewHolder) {
        showImages(show, 0, false, viewHolder);
    }

    private void showImages(final boolean show, final int duration, final boolean isGif, final ViewHolder viewHolder) {
        boolean hasDuration = duration > 0;
        if (show) {
            viewHolder.images.setVisibility(View.VISIBLE);
            viewHolder.image.setVisibility(View.VISIBLE);
            if (hasDuration) {
                viewHolder.mediaduration.setVisibility(View.VISIBLE);
                viewHolder.mediaduration.setText(formatTime(safeLongToInt(duration)));
            } else {
                viewHolder.mediaduration.setVisibility(GONE);
            }
        } else {
            viewHolder.images.setVisibility(GONE);
            viewHolder.image.setVisibility(GONE);
            viewHolder.mediaduration.setVisibility(GONE);
        }
    }

    private void toggleWhisperInfo(ViewHolder viewHolder, final Message message,
                                   final boolean includeBody, final boolean darkBackground) {
        SpannableStringBuilder messageBody = new SpannableStringBuilder(replaceYoutube(activity.getApplicationContext(), message.getBody()));

        final String mimeType = message.getMimeType();
        if (mimeType != null && message.getMimeType().contains("audio")) {
            messageBody.clear();
            messageBody.append(getTitle(message));
        }
        Editable body;
        if (darkBackground) {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_OnDark);
        } else {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1);
        }
        if (message.isPrivateMessage()) {
            final String privateMarker;
            if (message.getStatus() <= Message.STATUS_RECEIVED) {
                privateMarker = activity.getString(R.string.private_message);
            } else {
                Jid cp = message.getCounterpart();
                privateMarker = activity.getString(R.string.private_message_to, Strings.nullToEmpty(cp == null ? null : cp.getResource()));
            }
            body = new SpannableStringBuilder(privateMarker);
            viewHolder.messageBody.setVisibility(View.VISIBLE);
            if (includeBody) {
                body.append("\n");
                body.append(messageBody);
            }
            body.setSpan(new ForegroundColorSpan(ThemeHelper.getMessageTextColorPrivate(activity)), 0, privateMarker.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            body.setSpan(new StyleSpan(Typeface.BOLD), 0, privateMarker.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            MyLinkify.addLinks(body, false);
            viewHolder.messageBody.setText(body);
            viewHolder.messageBody.setAutoLinkMask(0);
            viewHolder.messageBody.setTextIsSelectable(true);
            viewHolder.messageBody.setMovementMethod(ClickableMovementMethod.getInstance());
        } else {
            if (includeBody) {
                viewHolder.messageBody.setVisibility(View.VISIBLE);
                body = new SpannableStringBuilder(messageBody);
                MyLinkify.addLinks(body, false);
                viewHolder.messageBody.setText(body);
                viewHolder.messageBody.setAutoLinkMask(0);
                viewHolder.messageBody.setTextIsSelectable(true);
                viewHolder.messageBody.setMovementMethod(ClickableMovementMethod.getInstance());
            } else {
                viewHolder.messageBody.setVisibility(GONE);
            }
        }
    }

    private void loadMoreMessages(Conversation conversation) {
        conversation.setLastClearHistory(0, null);
        activity.xmppConnectionService.updateConversation(conversation);
        conversation.setHasMessagesLeftOnServer(true);
        conversation.setFirstMamReference(null);
        long timestamp = conversation.getLastMessageTransmitted().getTimestamp();
        if (timestamp == 0) {
            timestamp = System.currentTimeMillis();
        }
        conversation.messagesLoaded.set(true);
        MessageArchiveService.Query query = activity.xmppConnectionService.getMessageArchiveService().query(conversation, new MamReference(0), timestamp, false);
        if (query != null) {
            ToastCompat.makeText(activity, R.string.fetching_history_from_server, ToastCompat.LENGTH_LONG).show();
        } else {
            ToastCompat.makeText(activity, R.string.not_fetching_history_retention_period, ToastCompat.LENGTH_SHORT).show();
        }
    }

    @SuppressLint({"StringFormatInvalid", "ClickableViewAccessibility"})
    @Override
    public View getView(int position, View view, ViewGroup parent) {
        final Message message = getItem(position);
        final boolean omemoEncryption = message.getEncryption() == Message.ENCRYPTION_AXOLOTL;
        final boolean isInValidSession = message.isValidInSession() && (!omemoEncryption || message.isTrusted());
        final Conversational conversation = message.getConversation();
        final Account account = conversation.getAccount();
        final List<Element> commands = message.getCommands();
        final int type = getItemViewType(position);
        ViewHolder viewHolder;
        if (view == null) {
            viewHolder = new ViewHolder();
            switch (type) {
                case DATE_SEPARATOR:
                    view = activity.getLayoutInflater().inflate(R.layout.message_date_bubble, parent, false);
                    viewHolder.status_message = view.findViewById(R.id.status_message);
                    viewHolder.message_box = view.findViewById(R.id.message_box);
                    viewHolder.indicatorReceived = view.findViewById(R.id.indicator_received);
                    break;
                case RTP_SESSION:
                    view = activity.getLayoutInflater().inflate(R.layout.message_rtp_session, parent, false);
                    viewHolder.status_message = view.findViewById(R.id.message_body);
                    viewHolder.message_box = view.findViewById(R.id.message_box);
                    viewHolder.indicatorReceived = view.findViewById(R.id.indicator_received);
                    break;
                case SENT:
                    view = activity.getLayoutInflater().inflate(R.layout.message_sent, parent, false);
                    viewHolder.message_box = view.findViewById(R.id.message_box);
                    viewHolder.contact_picture = view.findViewById(R.id.message_photo);
                    if (activity.xmppConnectionService != null && activity.xmppConnectionService.getBooleanPreference("show_less_avatars", R.bool.show_less_avatars)) {
                        view.findViewById(R.id.message_photo).setVisibility(GONE);
                    }
                    viewHolder.username = view.findViewById(R.id.username);
                    viewHolder.audioPlayer = view.findViewById(R.id.audio_player);
                    viewHolder.download_button = view.findViewById(R.id.download_button);
                    viewHolder.resend_button = view.findViewById(R.id.resend_button);
                    viewHolder.indicator = view.findViewById(R.id.security_indicator);
                    viewHolder.edit_indicator = view.findViewById(R.id.edit_indicator);
                    viewHolder.retract_indicator = view.findViewById(R.id.retract_indicator);
                    viewHolder.images = view.findViewById(R.id.images);
                    viewHolder.mediaduration = view.findViewById(R.id.media_duration);
                    viewHolder.image = view.findViewById(R.id.message_image);
                    viewHolder.richlinkview = view.findViewById(R.id.richLinkView);
                    if (activity.xmppConnectionService.getBooleanPreference("set_text_collapsable", R.bool.set_text_collapsable)) {
                        viewHolder.messageBody = view.findViewById(R.id.message_body_collapsable);
                    } else if (!activity.xmppConnectionService.getBooleanPreference("set_text_collapsable", R.bool.set_text_collapsable)) {
                        viewHolder.messageBody = view.findViewById(R.id.message_body);
                    }
                    viewHolder.user = view.findViewById(R.id.message_user);
                    viewHolder.time = view.findViewById(R.id.message_time);
                    viewHolder.subject = view.findViewById(R.id.message_subject);
//                    viewHolder.inReplyToBox = view.findViewById(R.id.in_reply_to_box);
                    viewHolder.indicatorReceived = view.findViewById(R.id.indicator_received);
                    viewHolder.transfer = view.findViewById(R.id.transfer);
                    viewHolder.progressBar = view.findViewById(R.id.progressBar);
                    viewHolder.cancel_transfer = view.findViewById(R.id.cancel_transfer);
                    break;
                case RECEIVED:
                    view = activity.getLayoutInflater().inflate(R.layout.message_received, parent, false);
                    viewHolder.message_box = view.findViewById(R.id.message_box);
                    viewHolder.contact_picture = view.findViewById(R.id.message_photo);
                    if (message.getConversation().getMode() == Conversation.MODE_SINGLE && activity.xmppConnectionService.getBooleanPreference("show_less_avatars", R.bool.show_less_avatars)) {
                        view.findViewById(R.id.message_photo).setVisibility(GONE);
                    }
                    viewHolder.username = view.findViewById(R.id.username);
                    viewHolder.audioPlayer = view.findViewById(R.id.audio_player);
                    viewHolder.download_button = view.findViewById(R.id.download_button);
                    viewHolder.answer_button = view.findViewById(R.id.answer);
                    viewHolder.indicator = view.findViewById(R.id.security_indicator);
                    viewHolder.edit_indicator = view.findViewById(R.id.edit_indicator);
                    viewHolder.retract_indicator = view.findViewById(R.id.retract_indicator);
                    viewHolder.images = view.findViewById(R.id.images);
                    viewHolder.mediaduration = view.findViewById(R.id.media_duration);
                    viewHolder.image = view.findViewById(R.id.message_image);
                    viewHolder.richlinkview = view.findViewById(R.id.richLinkView);
                    if (activity.xmppConnectionService.getBooleanPreference("set_text_collapsable", R.bool.set_text_collapsable)) {
                        viewHolder.messageBody = view.findViewById(R.id.message_body_collapsable);
                    } else if (!activity.xmppConnectionService.getBooleanPreference("set_text_collapsable", R.bool.set_text_collapsable)) {
                        viewHolder.messageBody = view.findViewById(R.id.message_body);
                    }
                    viewHolder.user = view.findViewById(R.id.message_user);
                    viewHolder.time = view.findViewById(R.id.message_time);
                    viewHolder.subject = view.findViewById(R.id.message_subject);
//                    viewHolder.inReplyToBox = view.findViewById(R.id.in_reply_to_box);
                    viewHolder.indicatorReceived = view.findViewById(R.id.indicator_received);
                    viewHolder.encryption = view.findViewById(R.id.message_encryption);
                    viewHolder.transfer = view.findViewById(R.id.transfer);
                    viewHolder.progressBar = view.findViewById(R.id.progressBar);
                    viewHolder.cancel_transfer = view.findViewById(R.id.cancel_transfer);
                    viewHolder.commands_list = view.findViewById(R.id.commands_list);
                    break;
                case STATUS:
                    view = activity.getLayoutInflater().inflate(R.layout.message_status, parent, false);
                    viewHolder.contact_picture = view.findViewById(R.id.message_photo);
                    viewHolder.status_message = view.findViewById(R.id.status_message);
                    viewHolder.load_more_messages = view.findViewById(R.id.load_more_messages);
                    break;
                default:
                    throw new AssertionError("Unknown view type");
            }
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
            if (viewHolder == null) {
                return view;
            }
        }

        if (viewHolder.messageBody != null) {
            viewHolder.messageBody.setCustomSelectionActionModeCallback(new MessageTextActionModeCallback(this, viewHolder.messageBody));
        }

        boolean darkBackground = activity.isDarkTheme();

        if (type == DATE_SEPARATOR) {
            if (UIHelper.today(message.getTimeSent())) {
                viewHolder.status_message.setText(R.string.today);
            } else if (UIHelper.yesterday(message.getTimeSent())) {
                viewHolder.status_message.setText(R.string.yesterday);
            } else {
                viewHolder.status_message.setText(DateUtils.formatDateTime(activity, message.getTimeSent(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR));
            }
            viewHolder.status_message.setTextColor(ThemeHelper.getCallTextColor(getContext(), darkBackground));
            viewHolder.message_box.setBackgroundResource(darkBackground ? R.drawable.date_bubble_dark : R.drawable.date_bubble);
            int date_bubble_color = ColorUtils.setAlphaComponent(StyledAttributes.getColor(activity, R.attr.text_Color_Main), 50); //set alpha to date_bubble
            activity.setBubbleColor(viewHolder.message_box, (date_bubble_color), -1); // themed color
            return view;
        } else if (type == RTP_SESSION) {
            final boolean isDarkTheme = activity.isDarkTheme();
            final boolean received = message.getStatus() <= Message.STATUS_RECEIVED;
            final String formattedTime = UIHelper.readableTimeDifferenceFull(activity, message.getMergedTimeSent());
            final RtpSessionStatus rtpSessionStatus = RtpSessionStatus.of(message.getBody());
            final long duration = rtpSessionStatus.duration;
            if (received) {
                if (duration > 0) {
                    viewHolder.status_message.setText(activity.getString(R.string.incoming_call_duration_timestamp, TimeFrameUtils.resolve(activity, duration), UIHelper.readableTimeDifferenceFull(activity, message.getTimeSent())));
                    viewHolder.status_message.setTextColor(ThemeHelper.getCallTextColor(getContext(), darkBackground));
                } else if (rtpSessionStatus.successful) {
                    viewHolder.status_message.setText(R.string.incoming_call);
                    viewHolder.status_message.setTextColor(ThemeHelper.getCallTextColor(getContext(), darkBackground));
                } else {
                    viewHolder.status_message.setText(activity.getString(R.string.missed_call_timestamp, UIHelper.readableTimeDifferenceFull(activity, message.getTimeSent())));
                    viewHolder.status_message.setTextColor(ThemeHelper.getMissedCallTextColor(activity, darkBackground));
                }
            } else {
                if (duration > 0) {
                    viewHolder.status_message.setText(activity.getString(R.string.outgoing_call_duration_timestamp, TimeFrameUtils.resolve(activity, duration), UIHelper.readableTimeDifferenceFull(activity, message.getTimeSent())));
                    viewHolder.status_message.setTextColor(ThemeHelper.getCallTextColor(getContext(), darkBackground));
                } else {
                    viewHolder.status_message.setText(activity.getString(R.string.outgoing_call_timestamp, UIHelper.readableTimeDifferenceFull(activity, message.getTimeSent())));
                    viewHolder.status_message.setTextColor(ThemeHelper.getCallTextColor(getContext(), darkBackground));
                }
            }
            viewHolder.indicatorReceived.setImageResource(RtpSessionStatus.getDrawable(received, rtpSessionStatus.successful, isDarkTheme));
            viewHolder.message_box.setBackgroundResource(darkBackground ? R.drawable.date_bubble_dark : R.drawable.date_bubble);
            int date_bubble_color = ColorUtils.setAlphaComponent(StyledAttributes.getColor(activity, R.attr.text_Color_Main), 50); //set alpha to date bubble
            activity.setBubbleColor(viewHolder.message_box, (date_bubble_color), -1); //themed color
            return view;
        } else if (type == STATUS) {
            if ("LOAD_MORE".equals(message.getBody())) {
                viewHolder.status_message.setVisibility(GONE);
                viewHolder.contact_picture.setVisibility(GONE);
                viewHolder.load_more_messages.setVisibility(View.VISIBLE);
                viewHolder.load_more_messages.setOnClickListener(v -> loadMoreMessages((Conversation) message.getConversation()));
            } else {
                viewHolder.status_message.setVisibility(View.VISIBLE);
                viewHolder.load_more_messages.setVisibility(GONE);
                viewHolder.status_message.setText(message.getBody());
                boolean showAvatar;
                if (conversation.getMode() == Conversation.MODE_SINGLE) {
                    showAvatar = true;
                    AvatarWorkerTask.loadAvatar(message, viewHolder.contact_picture, R.dimen.avatar_on_status_message);
                } else if (message.getCounterpart() != null || message.getTrueCounterpart() != null || (message.getCounterparts() != null && message.getCounterparts().size() > 0)) {
                    showAvatar = true;
                    AvatarWorkerTask.loadAvatar(message, viewHolder.contact_picture, R.dimen.avatar_on_status_message);
                } else {
                    showAvatar = false;
                }
                if (showAvatar) {
                    viewHolder.contact_picture.setAlpha(0.5f);
                    viewHolder.contact_picture.setVisibility(View.VISIBLE);
                } else {
                    viewHolder.contact_picture.setVisibility(GONE);
                }
            }
            return view;
        } else {
            AvatarWorkerTask.loadAvatar(message, viewHolder.contact_picture, R.dimen.avatar);
        }

        resetClickListener(viewHolder.message_box, viewHolder.messageBody);
        if (activity != null && activity.xmppConnectionService != null && activity.xmppConnectionService.getBooleanPreference("show_thread_feature", R.bool.show_thread_feature)) {
            viewHolder.message_box.setOnClickListener(v -> {
                if (MessageAdapter.this.mOnMessageBoxClickedListener != null) {
                    MessageAdapter.this.mOnMessageBoxClickedListener
                            .onContactPictureClicked(message);
                }
            });
            viewHolder.messageBody.setOnClickListener(v -> {
                if (MessageAdapter.this.mOnMessageBoxClickedListener != null) {
                    MessageAdapter.this.mOnMessageBoxClickedListener
                            .onContactPictureClicked(message);
                }
            });
        }

        viewHolder.messageBody.setOnClickListener(v -> {
            if (MessageAdapter.this.mOnMessageBoxClickedListener != null) {
                MessageAdapter.this.mOnMessageBoxClickedListener
                        .onContactPictureClicked(message);
            }
        });


        viewHolder.contact_picture.setOnClickListener(v -> {
            if (MessageAdapter.this.mOnContactPictureClickedListener != null) {
                MessageAdapter.this.mOnContactPictureClickedListener.onContactPictureClicked(message);
            }
        });




        SwipeLayout swipeLayout = view.findViewById(R.id.layout_swipe);

// Найдите bottom_wrapper как ViewGroup (общий родительский класс для LinearLayout и RelativeLayout)
        ViewGroup bottomWrapper = view.findViewById(R.id.bottom_wrapper);
        ImageView swipeArrow = view.findViewById(R.id.swipe_arrow);

// Создайте анимацию пульсации
        ObjectAnimator pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                swipeArrow,
                PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.2f),
                PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.2f)
        );
        pulseAnimator.setDuration(1000);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);

// Установите режим отображения
        swipeLayout.setShowMode(SwipeLayout.ShowMode.PullOut);

// Добавьте край перетаскивания
        swipeLayout.addDrag(SwipeLayout.DragEdge.Right, bottomWrapper);

// Добавьте слушатель свайпа
        swipeLayout.addSwipeListener(new SwipeLayout.SwipeListener() {
            @Override
            public void onClose(SwipeLayout layout) {
                swipeLayout.refreshDrawableState();
                swipeLayout.clearAnimation();
                swipeArrow.setVisibility(View.GONE);
                pulseAnimator.cancel();
            }

            @Override
            public void onUpdate(SwipeLayout layout, int leftOffset, int topOffset) {
                layout.setClickToClose(true);
                swipeArrow.setVisibility(View.VISIBLE);
                float progress = Math.abs((float) leftOffset / layout.getWidth());
                swipeArrow.setAlpha(progress);
                if (!pulseAnimator.isRunning()) {
                    pulseAnimator.start();
                }
                Log.d("SwipeDebug", "Смещение: " + leftOffset + ", Верх: " + topOffset + ", Ширина: " + layout.getWidth());
            }

            @Override
            public void onStartOpen(SwipeLayout layout) {
                swipeLayout.setClickToClose(true);
                swipeArrow.setVisibility(View.VISIBLE);
                swipeArrow.setAlpha(0.5f);
                pulseAnimator.start();
            }

            @Override
            public void onOpen(SwipeLayout layout) {
                swipeLayout.refreshDrawableState();
                swipeArrow.setAlpha(1f);
                if (!pulseAnimator.isRunning()) {
                    pulseAnimator.start();
                }
                if (mOnMessageBoxSwipedListener != null) {
                    mOnMessageBoxSwipedListener.onContactPictureClicked(message);
                }
                swipeLayout.close(true);
                swipeLayout.setClickToClose(true);
            }

            @Override
            public void onStartClose(SwipeLayout layout) {
                swipeLayout.close(true);
                swipeLayout.setClickToClose(true);
                swipeArrow.setVisibility(View.GONE);
                pulseAnimator.cancel();
            }

            @Override
            public void onHandRelease(SwipeLayout layout, float xvel, float yvel) {
                swipeLayout.refreshDrawableState();
                swipeLayout.close(true);
                swipeArrow.setVisibility(View.GONE);
                pulseAnimator.cancel();
            }
        });

        // Treat touch-up as click so we don't have to touch twice
        // (touch twice is because it's waiting to see if you double-touch for text selection)
        viewHolder.messageBody.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (MessageAdapter.this.mOnMessageBoxClickedListener != null) {
                    MessageAdapter.this.mOnMessageBoxClickedListener
                            .onContactPictureClicked(message);
                }
            }
            return false;
        });

        viewHolder.contact_picture.setOnLongClickListener(v -> {
            if (MessageAdapter.this.mOnContactPictureLongClickedListener != null) {
                MessageAdapter.this.mOnContactPictureLongClickedListener.onContactPictureLongClicked(v, message);
                return true;
            } else {
                return false;
            }
        });

        final Transferable transferable = message.getTransferable();
        final boolean unInitiatedButKnownSize = MessageUtils.unInitiatedButKnownSize(message);

        final boolean muted = message.getStatus() == Message.STATUS_RECEIVED && conversation.getMode() == Conversation.MODE_MULTI && activity.xmppConnectionService.isMucUserMuted(new MucOptions.User(null, conversation.getJid(), message.getOccupantId(), null, null));
        if (muted) {
            // Muted MUC participant
            displayInfoMessage(viewHolder, "Muted", darkBackground, message);
        } else if (unInitiatedButKnownSize || message.isFileDeleted() || (transferable != null && transferable.getStatus() != Transferable.STATUS_UPLOADING)) {
            if (unInitiatedButKnownSize || (message.isMessageDeleted() && message.getModerated() == null)  || transferable != null && transferable.getStatus() == Transferable.STATUS_OFFER) {
                displayDownloadableMessage(viewHolder, message, activity.getString(R.string.download_x_file, UIHelper.getFileDescriptionString(activity, message)), darkBackground, type);
            } else if (transferable != null && transferable.getStatus() == Transferable.STATUS_OFFER_CHECK_FILESIZE) {
                displayDownloadableMessage(viewHolder, message, activity.getString(R.string.check_x_filesize, UIHelper.getFileDescriptionString(activity, message)), darkBackground, type);
            } else {
                /* todo why should we mark a file as deleted? --> causing strange side effects
                if (!activity.xmppConnectionService.getFileBackend().getFile(message).exists() && !message.isFileDeleted()) {
                    new Thread(new markFileDeletedFinisher(message, activity)).start();
                    displayInfoMessage(viewHolder, activity.getString(R.string.file_deleted), darkBackground, message);
                }*/
                if (checkFileExistence(message, view, viewHolder)) {
                    new Thread(new markFileExistingFinisher(message, activity)).start();
                }
                displayInfoMessage(viewHolder, UIHelper.getMessagePreview(activity.xmppConnectionService, message).first, darkBackground, message);
            }
        } else if (message.isFileOrImage() && message.getEncryption() != Message.ENCRYPTION_PGP && message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED) {
            if (message.getFileParams().width > 0 && message.getFileParams().height > 0) {
                displayMediaPreviewMessage(viewHolder, message, darkBackground, type);
            } else if (message.getFileParams().runtime > 0 && (message.getFileParams().width == 0 && message.getFileParams().height == 0)) {
                displayAudioMessage(viewHolder, message, darkBackground, type);
            } else if ("application/webxdc+zip".equals(message.getFileParams().getMediaType()) && message.getConversation() instanceof Conversation && !message.getFileParams().getCids().isEmpty()) {
                displayWebxdcMessage(viewHolder, message, darkBackground, type);
            } else {
                displayOpenableMessage(viewHolder, message, darkBackground, type);
            }
        } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            if (account.isPgpDecryptionServiceConnected()) {
                if (conversation instanceof Conversation && !account.hasPendingPgpIntent((Conversation) conversation)) {
                    displayInfoMessage(viewHolder, activity.getString(R.string.message_decrypting), darkBackground, message);
                } else {
                    displayInfoMessage(viewHolder, activity.getString(R.string.pgp_message), darkBackground, message);
                }
            } else {
                displayInfoMessage(viewHolder, activity.getString(R.string.install_openkeychain), darkBackground, message);
                viewHolder.message_box.setOnClickListener(this::promptOpenKeychainInstall);
                viewHolder.messageBody.setOnClickListener(this::promptOpenKeychainInstall);
            }
        } else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
            displayInfoMessage(viewHolder, activity.getString(R.string.decryption_failed), darkBackground, message);
        } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE) {
            displayInfoMessage(viewHolder, activity.getString(R.string.not_encrypted_for_this_device), darkBackground, message);
        } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_FAILED) {
            displayInfoMessage(viewHolder, activity.getString(R.string.omemo_decryption_failed), darkBackground, message);
        } else {
            if (message.isGeoUri()) {
                displayLocationMessage(viewHolder, message, darkBackground, type);
            } else if (message.isXmppUri()) {
                displayXmppMessage(viewHolder, message.getBody().trim());
            } else if (message.treatAsDownloadable()) {
                try {
                    final URI uri = message.getOob();
                    displayDownloadableMessage(viewHolder,
                            message,
                            activity.getString(R.string.check_x_filesize_on_host,
                                    UIHelper.getFileDescriptionString(activity, message),
                                    uri.getHost()),
                            darkBackground, type);
                } catch (Exception e) {
                    displayDownloadableMessage(viewHolder,
                            message,
                            activity.getString(R.string.check_x_filesize,
                                    UIHelper.getFileDescriptionString(activity, message)),
                            darkBackground, type);
                }
            } else if (message.bodyIsOnlyEmojis() && message.getType() != Message.TYPE_PRIVATE) {
                displayEmojiMessage(viewHolder, getSpannableBody(message), darkBackground);
            } else {
                displayTextMessage(viewHolder, message, darkBackground, type);
            }
        }

        if (type == RECEIVED) {
            if (!muted && commands != null && conversation instanceof Conversation) {
                CommandButtonAdapter adapter = new CommandButtonAdapter(activity);
                adapter.addAll(commands);
                viewHolder.commands_list.setAdapter(adapter);
                viewHolder.commands_list.setVisibility(View.VISIBLE);
                viewHolder.commands_list.setOnItemClickListener((p, v, pos, id) -> {
                    final Element command = adapter.getItem(pos);
                    activity.startCommand(conversation.getAccount(), command.getAttributeAsJid("jid"), command.getAttribute("node"));
                });
            } else {
                // It's unclear if we can set this to null...
                ListAdapter adapter = viewHolder.commands_list.getAdapter();
                if (adapter instanceof ArrayAdapter) {
                    ((ArrayAdapter<?>) adapter).clear();
                }
                viewHolder.commands_list.setVisibility(GONE);
                viewHolder.commands_list.setOnItemClickListener(null);
            }
            if (message.isPrivateMessage()) {
                viewHolder.answer_button.setVisibility(View.VISIBLE);
                Drawable icon = activity.getResources().getDrawable(R.drawable.ic_reply_circle_black_24dp);
                Drawable drawable = DrawableCompat.wrap(icon);
                DrawableCompat.setTint(drawable, StyledAttributes.getColor(getContext(), R.attr.colorAccent));
                viewHolder.answer_button.setImageDrawable(drawable);
                viewHolder.answer_button.setOnClickListener(v -> {
                    try {
                        if (activity instanceof ConversationsActivity) {
                            ConversationFragment conversationFragment = ConversationFragment.get(activity);
                            if (conversationFragment != null) {
                                activity.invalidateOptionsMenu();
                                conversationFragment.privateMessageWith(message.getCounterpart());
                            }
                        }
                    } catch (Exception e) {
                        viewHolder.answer_button.setVisibility(GONE);
                        e.printStackTrace();
                    }
                });
            } else {
                viewHolder.answer_button.setVisibility(GONE);
            }
            if (isInValidSession) {
                setBubbleBackgroundColor(viewHolder.message_box, type, message.isPrivateMessage(), isInValidSession);
                viewHolder.encryption.setVisibility(GONE);
                viewHolder.encryption.setTextColor(ThemeHelper.getMessageTextColor(activity, darkBackground, false));
            } else {
                setBubbleBackgroundColor(viewHolder.message_box, type, message.isPrivateMessage(), isInValidSession);
                viewHolder.encryption.setVisibility(View.VISIBLE);
                viewHolder.encryption.setTextColor(ThemeHelper.getWarningTextColor(activity, darkBackground));
                if (omemoEncryption && !message.isTrusted()) {
                    viewHolder.encryption.setText(R.string.not_trusted);
                } else {
                    viewHolder.encryption.setText(CryptoHelper.encryptionTypeToText(message.getEncryption()));
                }
            }
        }
        if (type == RECEIVED || type == SENT) {
            String subject = message.getSubject();
            if (subject == null && message.getThread() != null) {
                final var thread = ((Conversation) message.getConversation()).getThread(message.getThread().getContent());
                if (thread != null) subject = thread.getSubject();
            }
            if (muted || subject == null) {
                viewHolder.subject.setVisibility(View.GONE);
            } else {
                viewHolder.subject.setVisibility(View.VISIBLE);
                viewHolder.subject.setText(subject);
            }

//            else {
//                viewHolder.inReplyToBox.setVisibility(View.VISIBLE);
//                viewHolder.inReplyTo.setText(UIHelper.getMessageDisplayName(message.getInReplyTo()));
//                viewHolder.inReplyTo.setOnClickListener((v) -> mConversationFragment.jumpTo(message.getInReplyTo()));
//                setBubbleBackgroundColor(viewHolder.inReplyTo, type, message.isPrivateMessage(), isInValidSession);
//            }
        }

        if (type == SENT) {
            setBubbleBackgroundColor(viewHolder.message_box, type, message.isPrivateMessage(), isInValidSession);
        }
        displayStatus(viewHolder, message, type, darkBackground);
        return view;
    }

    private static class markFileExistingFinisher implements Runnable {
        private final Message message;
        private final WeakReference<XmppActivity> activityReference;

        private markFileExistingFinisher(Message message, XmppActivity activity) {
            this.message = message;
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        public void run() {
            final XmppActivity activity = activityReference.get();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(
                    () -> {
                        Log.d(Config.LOGTAG, "Found and restored orphaned file " + message.getRelativeFilePath());
                        message.setFileDeleted(false);
                        activity.xmppConnectionService.updateMessage(message, false);
                        activity.xmppConnectionService.updateConversation((Conversation) message.getConversation());
                    });
        }
    }

    private static class markFileDeletedFinisher implements Runnable {
        private final Message message;
        private final WeakReference<XmppActivity> activityReference;

        private markFileDeletedFinisher(Message message, XmppActivity activity) {
            this.message = message;
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        public void run() {
            final XmppActivity activity = activityReference.get();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(
                    () -> {
                        Log.d(Config.LOGTAG, "Mark file deleted " + message.getRelativeFilePath());
                        message.setFileDeleted(true);
                        activity.xmppConnectionService.updateMessage(message, false);
                        activity.xmppConnectionService.updateConversation((Conversation) message.getConversation());
                    });
        }
    }

    private boolean checkFileExistence(Message message, View view, ViewHolder viewHolder) {
        final Rect scrollBounds = new Rect();
        view.getHitRect(scrollBounds);
        if (message.isFileDeleted() && viewHolder.messageBody.getLocalVisibleRect(scrollBounds)) {
            return activity.xmppConnectionService.getFileBackend().getFile(message).exists();
        } else {
            return false;
        }
    }

    private void promptOpenKeychainInstall(View view) {
        activity.showInstallPgpDialog();
    }

    public FileBackend getFileBackend() {
        return activity.xmppConnectionService.getFileBackend();
    }

    public void stopAudioPlayer() {
        audioPlayer.stop();
    }

    public void unregisterListenerInAudioPlayer() {
        audioPlayer.unregisterListener();
    }

    public void startStopPending() {
        audioPlayer.startStopPending();
    }

    public void openDownloadable(Message message) {
        if (Compatibility.runsThirtyThree() && ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
            ConversationFragment.registerPendingMessage(activity, message);
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO}, ConversationsActivity.REQUEST_OPEN_MESSAGE);
            return;
        } else if (!Compatibility.runsThirtyThree() && ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ConversationFragment.registerPendingMessage(activity, message);
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, ConversationsActivity.REQUEST_OPEN_MESSAGE);
            return;
        }
        final DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
        ViewUtil.view(activity, file);
    }

    private void showLocation(Message message) {
        for (Intent intent : GeoHelper.createGeoIntentsFromMessage(this.getContext(), message)) {
            if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                getContext().startActivity(intent);
                activity.overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                return;
            }
        }
    }

    public void updatePreferences() {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
        this.mIndicateReceived = p.getBoolean("indicate_received", activity.getResources().getBoolean(R.bool.indicate_received));
        this.mPlayGifInside = p.getBoolean(PLAY_GIF_INSIDE, activity.getResources().getBoolean(R.bool.play_gif_inside));
        this.mShowLinksInside = p.getBoolean(SHOW_LINKS_INSIDE, activity.getResources().getBoolean(R.bool.show_links_inside));
        this.mShowMapsInside = p.getBoolean(SHOW_MAPS_INSIDE, activity.getResources().getBoolean(R.bool.show_maps_inside));
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
        this.readmarkervalue = sharedPref.getString("readmarker_style", "blue_readmarkers");
    }

    public void setHighlightedTerm(List<String> terms) {
        this.highlightedTerm = terms == null ? null : StylingHelper.filterHighlightedWords(terms);
    }

    public void setConversationFragment(ConversationFragment frag) {
        mConversationFragment = frag;
    }

    public void quoteText(String text, String user) {
        if (mConversationFragment != null) mConversationFragment.quoteText(text, null);
    }

    public interface OnContactPictureClicked {
        void onContactPictureClicked(Message message);
    }

    public interface OnContactPictureLongClicked {
        void onContactPictureLongClicked(View v, Message message);
    }

    public interface OnInlineImageLongClicked {
        boolean onInlineImageLongClicked(Cid cid);
    }


    private static class ViewHolder {

        public Button load_more_messages;
        public ImageView edit_indicator;
        public ImageView retract_indicator;
        public RelativeLayout audioPlayer;
        public LinearLayout images;
        protected LinearLayout message_box;
        protected Button download_button;
        protected Button resend_button;
        protected ImageButton answer_button;
        protected ImageView image;
        protected TextView mediaduration;
        protected RichLinkView richlinkview;
        protected ImageView indicator;
        protected ImageView indicatorReceived;
        protected TextView time;
        protected TextView subject;
        //        protected TextView inReplyTo;
//        protected LinearLayout inReplyToBox;
        protected TextView messageBody;
        protected TextView user;
        protected TextView username;
        protected ImageView contact_picture;
        protected TextView status_message;
        protected TextView encryption;
        protected ListView commands_list;
        protected RelativeLayout transfer;
        protected ProgressBar progressBar;
        protected ImageButton cancel_transfer;
    }

    public void setBubbleBackgroundColor(final View viewHolder, final int type,
                                         final boolean isPrivateMessage, final boolean isInValidSession) {
        if (type == RECEIVED) {
            if (isInValidSession) {
                if (isPrivateMessage) {
                    viewHolder.setBackgroundResource(R.drawable.message_bubble_received_light_private);
                    activity.setBubbleColor(viewHolder, StyledAttributes.getColor(activity, R.attr.color_bubble_light), StyledAttributes.getColor(activity, R.attr.colorAccent));
                } else {
                    viewHolder.setBackgroundResource(R.drawable.message_bubble_received_light);
                    activity.setBubbleColor(viewHolder, StyledAttributes.getColor(activity, R.attr.color_bubble_light), -1);
                }
            } else {
                if (isPrivateMessage) {
                    viewHolder.setBackgroundResource(R.drawable.message_bubble_received_warning_private);
                    activity.setBubbleColor(viewHolder, StyledAttributes.getColor(activity, R.attr.color_bubble_warning), StyledAttributes.getColor(activity, R.attr.colorAccent));
                } else {
                    viewHolder.setBackgroundResource(R.drawable.message_bubble_received_warning);
                    activity.setBubbleColor(viewHolder, StyledAttributes.getColor(activity, R.attr.color_bubble_warning), -1);
                }
            }
        }

        if (type == SENT) {
            if (isPrivateMessage) {
                viewHolder.setBackgroundResource(R.drawable.message_bubble_sent_private);
                activity.setBubbleColor(viewHolder, StyledAttributes.getColor(activity, R.attr.color_bubble_dark), StyledAttributes.getColor(activity, R.attr.colorAccent));
            } else {
                viewHolder.setBackgroundResource(R.drawable.message_bubble_sent);
                activity.setBubbleColor(viewHolder, StyledAttributes.getColor(activity, R.attr.color_bubble_dark), -1);
            }
        }
    }

    public void setInputBubbleBackgroundColor(final View viewHolder,
                                              final boolean isPrivateMessage) {
        if (isPrivateMessage) {
            viewHolder.setBackgroundResource(R.drawable.input_bubble_sent_private);
            activity.setBubbleColor(viewHolder, StyledAttributes.getColor(activity, R.attr.color_bubble_dark), StyledAttributes.getColor(activity, R.attr.colorAccent));
        } else {
            viewHolder.setBackgroundResource(R.drawable.input_bubble_light);
            activity.setBubbleColor(viewHolder, StyledAttributes.getColor(activity, R.attr.color_bubble_dark), -1);
        }
    }

    class ThumbnailTask extends AsyncTask<DownloadableFile, Void, Drawable[]> {
        @Override
        protected Drawable[] doInBackground(DownloadableFile... params) {
            if (isCancelled()) return null;

            Drawable[] d = new Drawable[params.length];
            for (int i = 0; i < params.length; i++) {
                try {
                    d[i] = activity.xmppConnectionService.getFileBackend().getThumbnail(params[i], activity.getResources(), (int) (metrics.density * 288), false);
                } catch (final IOException e) {
                    d[i] = null;
                }
            }

            return d;
        }

        @Override
        protected void onPostExecute(final Drawable[] d) {
            if (isCancelled()) return;
            activity.xmppConnectionService.updateConversationUi();
        }
    }
}
