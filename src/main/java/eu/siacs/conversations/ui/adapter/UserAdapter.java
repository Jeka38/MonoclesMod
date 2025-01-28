package eu.siacs.conversations.ui.adapter;

import android.app.PendingIntent;
import android.content.Context;
import android.content.IntentSender;
import android.graphics.drawable.Drawable;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.openintents.openpgp.util.OpenPgpUtils;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.databinding.ContactBinding;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.ConferenceDetailsActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.util.MucDetailsContextMenuHelper;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.xmpp.Jid;

public class UserAdapter extends ListAdapter<MucOptions.User, UserAdapter.ViewHolder> implements View.OnCreateContextMenuListener {

    static final DiffUtil.ItemCallback<MucOptions.User> DIFF = new DiffUtil.ItemCallback<MucOptions.User>() {
        @Override
        public boolean areItemsTheSame(@NonNull MucOptions.User a, @NonNull MucOptions.User b) {
            final Jid fullA = a.getFullJid();
            final Jid fullB = b.getFullJid();
            final Jid realA = a.getRealJid();
            final Jid realB = b.getRealJid();
            if (fullA != null && fullB != null) {
                return fullA.equals(fullB);
            } else if (realA != null && realB != null) {
                return realA.equals(realB);
            } else {
                return false;
            }
        }

        @Override
        public boolean areContentsTheSame(@NonNull MucOptions.User a, @NonNull MucOptions.User b) {
            return a.equals(b);
        }
    };
    private final boolean advancedMode;
    private MucOptions.User selectedUser = null;

    public UserAdapter(final boolean advancedMode) {
        super(DIFF);
        this.advancedMode = advancedMode;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int position) {
        return new ViewHolder(DataBindingUtil.inflate(LayoutInflater.from(viewGroup.getContext()), R.layout.contact, viewGroup, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        final MucOptions.User user = getItem(position);
        AvatarWorkerTask.loadAvatar(user, viewHolder.binding.contactPhoto, R.dimen.avatar_on_details_screen_size);
        viewHolder.binding.getRoot().setOnClickListener(v -> {
            final XmppActivity activity = XmppActivity.find(v);
            if (activity != null) {
                activity.highlightInMuc(user.getConversation(), user.getNick());
            }
        });
        viewHolder.binding.getRoot().setTag(user);
        viewHolder.binding.getRoot().setOnCreateContextMenuListener(this);
        viewHolder.binding.getRoot().setOnLongClickListener(v -> {
            selectedUser = user;
            return false;
        });
        final String name = user.getNick();
        final Contact contact = user.getContact();
        viewHolder.binding.contactJid.setVisibility(View.GONE);
        viewHolder.binding.contactJid.setText("");
        if (contact != null) {
            final String displayName = contact.getDisplayName();
            viewHolder.binding.contactDisplayName.setText(displayName);
            if (name != null && !name.equals(displayName)) {
                viewHolder.binding.contactJid.setVisibility(View.VISIBLE);
                viewHolder.binding.contactJid.setText(name);
            }
        } else {
            viewHolder.binding.contactDisplayName.setText(name == null ? "" : name);
        }
        if (advancedMode && user.getPgpKeyId() != 0) {
            viewHolder.binding.key.setVisibility(View.VISIBLE);
            viewHolder.binding.key.setOnClickListener(v -> {
                final XmppActivity activity = XmppActivity.find(v);
                final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
                final PgpEngine pgpEngine = service == null ? null : service.getPgpEngine();
                if (pgpEngine != null) {
                    PendingIntent intent = pgpEngine.getIntentForKey(user.getPgpKeyId());
                    if (intent != null) {
                        try {
                            activity.startIntentSenderForResult(intent.getIntentSender(), 0, null, 0, 0, 0, Compatibility.pgpStartIntentSenderOptions());
                        } catch (IntentSender.SendIntentException ignored) {

                        }
                    }
                }
            });
            viewHolder.binding.key.setText(OpenPgpUtils.convertKeyIdToHex(user.getPgpKeyId()));
        } else {
            viewHolder.binding.key.setVisibility(View.GONE);
        }

        viewHolder.binding.tags.setVisibility(View.VISIBLE);
        viewHolder.binding.tags.removeAllViewsInLayout();
        for (MucOptions.Hat hat : user.getPseudoHats(viewHolder.binding.getRoot().getContext())) {
            TextView tv = (TextView) LayoutInflater.from(viewHolder.binding.getRoot().getContext()).inflate(R.layout.list_item_tag, viewHolder.binding.tags, false);
            tv.setText(hat.toString());
            Drawable unwrappedDrawable = AppCompatResources.getDrawable(tv.getContext(), R.drawable.rounded_tag);
            Drawable wrappedDrawable = DrawableCompat.wrap(unwrappedDrawable);
            DrawableCompat.setTint(wrappedDrawable, hat.getColor());
            tv.setBackgroundResource(R.drawable.rounded_tag);
            viewHolder.binding.tags.addView(tv);
        }
        for (MucOptions.Hat hat : user.getHats()) {
            TextView tv = (TextView) LayoutInflater.from(viewHolder.binding.getRoot().getContext()).inflate(R.layout.list_item_tag, viewHolder.binding.tags, false);
            tv.setText(hat.toString());
            Drawable unwrappedDrawable = AppCompatResources.getDrawable(tv.getContext(), R.drawable.rounded_tag);
            Drawable wrappedDrawable = DrawableCompat.wrap(unwrappedDrawable);
            DrawableCompat.setTint(wrappedDrawable, hat.getColor());
            tv.setBackgroundResource(R.drawable.rounded_tag);
            viewHolder.binding.tags.addView(tv);
        }

        if (viewHolder.binding.tags.getChildCount() < 1) {
            viewHolder.binding.contactJid.setVisibility(View.VISIBLE);
            viewHolder.binding.tags.setVisibility(View.GONE);
        }
    }

    public MucOptions.User getSelectedUser() {
        return selectedUser;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        MucDetailsContextMenuHelper.onCreateContextMenu(menu, v);
    }

    protected class ViewHolder extends RecyclerView.ViewHolder {

        public final ContactBinding binding;

        private ViewHolder(ContactBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}