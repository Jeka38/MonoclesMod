package eu.siacs.conversations.ui.util;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import com.google.common.base.Optional;

import java.util.ArrayList;
import java.util.List;

import static eu.siacs.conversations.ui.ConversationFragment.REQUEST_START_AUDIO_CALL;
import static eu.siacs.conversations.ui.ConversationFragment.REQUEST_START_VIDEO_CALL;


import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.RtpSessionActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jingle.AbstractJingleConnection;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.jingle.Media;
import eu.siacs.conversations.xmpp.jingle.OngoingRtpSession;
import eu.siacs.conversations.xmpp.jingle.RtpCapability;
import me.drakeet.support.toast.ToastCompat;

public class CallManager {

    public static void checkPermissionAndTriggerAudioCall(XmppActivity activity, Conversation conversation) {
        if (activity.mUseTor || conversation.getAccount().isOnion()) {
            ToastCompat.makeText(activity, R.string.disable_tor_to_make_call, ToastCompat.LENGTH_SHORT).show();
            return;
        }
        if (hasPermissions(REQUEST_START_AUDIO_CALL, activity, Manifest.permission.RECORD_AUDIO)) {
            triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VOICE_CALL, activity, conversation);
        }
    }

    public static void checkPermissionAndTriggerVideoCall(XmppActivity activity, Conversation conversation) {
        if (activity.mUseTor || conversation.getAccount().isOnion()) {
            ToastCompat.makeText(activity, R.string.disable_tor_to_make_call, ToastCompat.LENGTH_SHORT).show();
            return;
        }
        if (hasPermissions(REQUEST_START_VIDEO_CALL, activity, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) {
            triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VIDEO_CALL, activity, conversation);
        }
    }

    public static void triggerRtpSession(final String action, XmppActivity activity, Conversation conversation) {
        if (activity.xmppConnectionService.getJingleConnectionManager().isBusy()) {
            ToastCompat.makeText(activity, R.string.only_one_call_at_a_time, ToastCompat.LENGTH_LONG).show();
            return;
        }

        final Contact contact = conversation.getContact();
        if (RtpCapability.jmiSupport(contact)) {
            triggerRtpSession(contact.getAccount(), contact.getJid().asBareJid(), action, activity);
        } else {
            final RtpCapability.Capability capability;
            if (action.equals(RtpSessionActivity.ACTION_MAKE_VIDEO_CALL)) {
                capability = RtpCapability.Capability.VIDEO;
            } else {
                capability = RtpCapability.Capability.AUDIO;
            }
            PresenceSelector.selectFullJidForDirectRtpConnection(activity, contact, capability, fullJid -> {
                triggerRtpSession(contact.getAccount(), fullJid, action, activity);
            });
        }
    }

    private static void triggerRtpSession(final Account account, final Jid with, final String action, XmppActivity activity) {
        final Intent intent = new Intent(activity, RtpSessionActivity.class);
        intent.setAction(action);
        intent.putExtra(RtpSessionActivity.EXTRA_ACCOUNT, account.getJid().toEscapedString());
        intent.putExtra(RtpSessionActivity.EXTRA_WITH, with.toEscapedString());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
    }

    public static void returnToOngoingCall(XmppActivity activity, Conversation conversation) {
        final Optional<OngoingRtpSession> ongoingRtpSession = activity.xmppConnectionService.getJingleConnectionManager().getOngoingRtpConnection(conversation.getContact());
        if (ongoingRtpSession.isPresent()) {
            final OngoingRtpSession id = ongoingRtpSession.get();
            final Intent intent = new Intent(activity, RtpSessionActivity.class);
            intent.putExtra(RtpSessionActivity.EXTRA_ACCOUNT, id.getAccount().getJid().asBareJid().toEscapedString());
            intent.putExtra(RtpSessionActivity.EXTRA_WITH, id.getWith().toEscapedString());
            if (id instanceof AbstractJingleConnection.Id) {
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra(RtpSessionActivity.EXTRA_SESSION_ID, id.getSessionId());
            } else if (id instanceof JingleConnectionManager.RtpSessionProposal) {
                if (((JingleConnectionManager.RtpSessionProposal) id).media.contains(Media.VIDEO)) {
                    intent.setAction(RtpSessionActivity.ACTION_MAKE_VIDEO_CALL);
                } else {
                    intent.setAction(RtpSessionActivity.ACTION_MAKE_VOICE_CALL);
                }
            }
            activity.startActivity(intent);
        }
    }

    private static boolean hasPermissions(int requestCode, XmppActivity activity, String... permissions) {
        final List<String> missingPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (Config.ONLY_INTERNAL_STORAGE && permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE) && permission.equals(Manifest.permission.READ_EXTERNAL_STORAGE) || Config.ONLY_INTERNAL_STORAGE && permission.equals(Manifest.permission.READ_MEDIA_IMAGES) && permission.equals(Manifest.permission.READ_MEDIA_AUDIO) && permission.equals(Manifest.permission.READ_MEDIA_VIDEO)) {
                continue;
            }
            if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (missingPermissions.size() == 0) {
            return true;
        } else {
            activity.requestPermissions(missingPermissions.toArray(new String[missingPermissions.size()]), requestCode);
            return false;
        }
    }
}
