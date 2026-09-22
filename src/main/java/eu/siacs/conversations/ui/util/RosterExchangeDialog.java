package eu.siacs.conversations.ui.util;

import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xmpp.rosterx.RosterExchangeManager;
import eu.siacs.conversations.xmpp.rosterx.RosterItem;

/**
 * Presents an incoming XEP-0144 roster item exchange to the user and, on confirmation, applies the
 * accepted suggestions to the local roster. XEP-0144 requires explicit user approval before the
 * roster is touched.
 */
public final class RosterExchangeDialog {

    private RosterExchangeDialog() {
    }

    public static void show(
            final Activity activity,
            final RosterExchangeManager manager,
            final Account account,
            final List<RosterItem> items) {
        // This is called from the network/parser thread (MessageParser), where an AlertDialog
        // cannot be created ("Can't create handler inside thread that has not called
        // Looper.prepare()"), so always marshal to the UI thread.
        activity.runOnUiThread(() -> showOnUiThread(activity, manager, account, items));
    }

    private static void showOnUiThread(
            final Activity activity,
            final RosterExchangeManager manager,
            final Account account,
            final List<RosterItem> items) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        final List<RosterItem> addable = new ArrayList<>();
        for (final RosterItem item : items) {
            if (item.getAction() == RosterItem.Action.ADD || item.getAction() == RosterItem.Action.MODIFY) {
                addable.add(item);
            }
        }
        if (addable.isEmpty()) {
            // delete/modify-only suggestions from a plain user are not applied automatically
            return;
        }
        final List<CharSequence> labels = new ArrayList<>();
        for (final RosterItem item : addable) {
            final StringBuilder label = new StringBuilder();
            label.append(item.getName() == null || item.getName().isEmpty()
                    ? item.getJid().asBareJid().toString()
                    : item.getName());
            label.append('\n').append(item.getJid().asBareJid().toString());
            if (!item.getGroups().isEmpty()) {
                label.append(" (").append(join(item.getGroups())).append(')');
            }
            if (item.alreadyInRoster(account)) {
                label.append(' ').append(activity.getString(R.string.roster_exchange_already));
            }
            labels.add(label);
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.roster_exchange_received)
                .setItems(labels.toArray(new CharSequence[0]), null)
                .setPositiveButton(R.string.roster_exchange_add, (dialog, which) -> {
                    for (final RosterItem item : addable) {
                        manager.apply(account, item);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static String join(final List<String> values) {
        final StringBuilder builder = new StringBuilder();
        for (final String value : values) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value);
        }
        return builder.toString();
    }
}
