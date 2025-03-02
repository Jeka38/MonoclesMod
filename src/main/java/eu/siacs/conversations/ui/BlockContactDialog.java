package eu.siacs.conversations.ui;

import android.view.View;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.databinding.DataBindingUtil;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.DialogBlockContactBinding;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.util.JidDialog;
import me.drakeet.support.toast.ToastCompat;

public final class BlockContactDialog {

    public static void show(final XmppActivity xmppActivity, final Blockable blockable) {
        show(xmppActivity, blockable, null);
    }
    public static void show(final XmppActivity xmppActivity, final Blockable blockable, final String serverMsgId) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(xmppActivity);
        final boolean isBlocked = blockable.isBlocked();
        builder.setNegativeButton(R.string.cancel, null);
        DialogBlockContactBinding binding = DataBindingUtil.inflate(xmppActivity.getLayoutInflater(), R.layout.dialog_block_contact, null, false);
        final boolean reporting = blockable.getAccount().getXmppConnection().getFeatures().spamReporting();
        if (reporting && !isBlocked) {
            binding.reportSpam.setVisibility(View.VISIBLE);
            if (serverMsgId != null) {
                binding.reportSpam.setChecked(true);
                binding.reportSpam.setEnabled(false);
            } else {
                binding.reportSpam.setEnabled(true);
            }
        } else {
            binding.reportSpam.setVisibility(View.GONE);
        }
        builder.setView(binding.getRoot());

        final String value;
        @StringRes int res;
        if (blockable.getJid().isFullJid()) {
            builder.setTitle(isBlocked ? R.string.action_unblock_participant : R.string.action_block_participant);
            value = blockable.getJid().toEscapedString();
            res = isBlocked ? R.string.unblock_contact_text : R.string.block_contact_text;
        } else if (blockable.getJid().getLocal() == null || blockable.getAccount().isBlocked(blockable.getJid().getDomain())) {
            builder.setTitle(isBlocked ? R.string.action_unblock_domain : R.string.action_block_domain);
            value =blockable.getJid().getDomain().toEscapedString();
            res = isBlocked ? R.string.unblock_domain_text : R.string.block_domain_text;
        } else {
            if (isBlocked) {
                builder.setTitle(R.string.action_unblock_contact);
            } else if (serverMsgId != null) {
                builder.setTitle(R.string.report_spam_and_block);
            } else {
                final int resBlockAction =
                        blockable instanceof Conversation
                                && ((Conversation) blockable).isWithStranger()
                                ? R.string.block_stranger
                                : R.string.action_block_contact;
                builder.setTitle(resBlockAction);
            }
            value = blockable.getJid().asBareJid().toEscapedString();
            res = isBlocked ? R.string.unblock_contact_text : R.string.block_contact_text;
        }
        binding.text.setText(JidDialog.style(xmppActivity, res, value));
        builder.setPositiveButton(isBlocked ? R.string.unblock : R.string.block, (dialog, which) -> {
            if (isBlocked) {
                xmppActivity.xmppConnectionService.sendUnblockRequest(blockable);
            } else {
                boolean toastShown = false;
                if (xmppActivity.xmppConnectionService.sendBlockRequest(blockable, binding.reportSpam.isChecked(), serverMsgId)) {
                    ToastCompat.makeText(xmppActivity, R.string.corresponding_conversations_closed, ToastCompat.LENGTH_SHORT).show();
                    toastShown = true;
                }
                if (xmppActivity instanceof ContactDetailsActivity) {
                    if (!toastShown) {
                        ToastCompat.makeText(xmppActivity, R.string.contact_blocked_past_tense, ToastCompat.LENGTH_SHORT).show();
                    }
                    xmppActivity.finish();
                }
            }
        });
        builder.create().show();
    }
}
