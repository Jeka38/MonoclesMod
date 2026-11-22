package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityMediaBrowserBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.adapter.MediaAdapter;
import eu.siacs.conversations.ui.interfaces.OnMediaLoaded;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.ui.util.GridManager;
import eu.siacs.conversations.utils.MenuDoubleTabUtil;
import eu.siacs.conversations.xmpp.Jid;
import me.drakeet.support.toast.ToastCompat;


public class MediaBrowserActivity extends XmppActivity implements OnMediaLoaded {

    private ActivityMediaBrowserBinding binding;
    private MediaAdapter mMediaAdapter;
    private boolean OnlyImagesVideos = false;
    ArrayList<Attachment> allAttachments = new ArrayList<>();
    ArrayList<Attachment> filteredAttachments = new ArrayList<>();
    private String mSavedInstanceAccount;
    private String mSavedInstanceJid;
    private String account;
    private String jid;
    private ActionMode actionMode;

    private final ActionMode.Callback selectionActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
            mode.getMenuInflater().inflate(R.menu.media_browser_selection, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
            final int count = mMediaAdapter.getSelectedCount();
            mode.setTitle(getResources().getQuantityString(R.plurals.selected_count, count, count));
            final boolean single = count == 1;
            menu.findItem(R.id.action_open_media).setVisible(single);
            menu.findItem(R.id.action_share_media).setVisible(single);
            return true;
        }

        @Override
        public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_save_media:
                    saveSelectedAttachments();
                    return true;
                case R.id.action_delete_media:
                    confirmDeleteSelected();
                    return true;
                case R.id.action_select_all:
                    mMediaAdapter.selectAll();
                    return true;
                case R.id.action_open_media:
                    final Attachment open = getSingleSelectedAttachment();
                    if (open != null) mMediaAdapter.open(open);
                    return true;
                case R.id.action_share_media:
                    final Attachment share = getSingleSelectedAttachment();
                    if (share != null) mMediaAdapter.share(share);
                    return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(final ActionMode mode) {
            actionMode = null;
            mMediaAdapter.clearSelection();
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        filter(OnlyImagesVideos);
        invalidateOptionsMenu();
        refreshUiReal();
    }

    public static void launch(Context context, Contact contact) {
        launch(context, contact.getAccount(), contact.getJid().asBareJid().toEscapedString());
    }

    public static void launch(Context context, Conversation conversation) {
        launch(context, conversation.getAccount(), conversation.getJid().asBareJid().toEscapedString());
    }

    private static void launch(Context context, Account account, String jid) {
        final Intent intent = new Intent(context, MediaBrowserActivity.class);
        intent.putExtra("account", account.getUuid());
        intent.putExtra("jid", jid);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            this.mSavedInstanceAccount = savedInstanceState.getString("account");
            this.mSavedInstanceJid = savedInstanceState.getString("jid");
        }
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_media_browser);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar());
        mMediaAdapter = new MediaAdapter(this, R.dimen.media_size);
        mMediaAdapter.setSelectionEnabled(true);
        mMediaAdapter.setOnSelectionChangedListener(this::onSelectionChanged);
        mMediaAdapter.setOnAttachmentsDeletedListener(this::onAttachmentsDeleted);
        this.binding.media.setAdapter(mMediaAdapter);
        GridManager.setupLayoutManager(this, this.binding.media, R.dimen.browser_media_size);
        this.binding.noMedia.setVisibility(View.GONE);
        this.binding.progressbar.setVisibility(View.VISIBLE);
        this.OnlyImagesVideos = getPreferences().getBoolean("show_videos_images_only", this.getResources().getBoolean(R.bool.show_videos_images_only));
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        savedInstanceState.putString("account", account);
        savedInstanceState.putString("jid", jid);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void refreshUiReal() {
        mMediaAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        MenuItem showImagesVideosOnly = menu.findItem(R.id.show_videos_images_only);
        showImagesVideosOnly.setChecked(OnlyImagesVideos);
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.media_browser, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem menuItem) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.show_videos_images_only:
                this.OnlyImagesVideos = !menuItem.isChecked();
                menuItem.setChecked(this.OnlyImagesVideos);
                getPreferences().edit().putBoolean("show_videos_images_only", OnlyImagesVideos).apply();
                filter(OnlyImagesVideos);
                invalidateOptionsMenu();
                refreshUiReal();
                break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    protected void onBackendConnected() {
        final Intent intent = getIntent();
        if (mSavedInstanceAccount != null) {
            try {
                account = mSavedInstanceAccount;
            } catch (Exception e) {
                account = intent == null ? null : intent.getStringExtra("account");
            }
        } else {
            account = intent == null ? null : intent.getStringExtra("account");
        }
        if (mSavedInstanceJid != null) {
            try {
                jid = mSavedInstanceJid;
            } catch (Exception e) {
                jid = intent == null ? null : intent.getStringExtra("jid");
            }
        } else {
            jid = intent == null ? null : intent.getStringExtra("jid");
        }
        if (account != null && jid != null) {
            xmppConnectionService.getAttachments(account, Jid.ofEscaped(jid), 0, this);
        }
    }

    @Override
    public void onMediaLoaded(List<Attachment> attachments) {
        allAttachments.clear();
        allAttachments.addAll(attachments);
        runOnUiThread(() -> {
            filter(OnlyImagesVideos);
        });
    }

    private void loadAttachments(List<Attachment> attachments) {
        if (attachments.size() > 0) {
            if (mMediaAdapter.getItemCount() != attachments.size()) {
                mMediaAdapter.setAttachments(attachments);
            }
            this.binding.noMedia.setVisibility(View.GONE);
            this.binding.progressbar.setVisibility(View.GONE);
        } else {
            this.binding.noMedia.setVisibility(View.VISIBLE);
            this.binding.progressbar.setVisibility(View.GONE);
        }
    }

    protected void filter(boolean needle) {
        if (xmppConnectionServiceBound) {
            filterAttachments(needle);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        filter(OnlyImagesVideos);
    }

    protected void filterAttachments(boolean needle) {
        if (allAttachments.size() > 0) {
            if (needle) {
                final ArrayList<Attachment> attachments = new ArrayList<>(allAttachments);
                filteredAttachments.clear();
                for (Attachment attachment : attachments) {
                    if (attachment.getMime() != null && (attachment.getMime().startsWith("image/") || attachment.getMime().startsWith("video/"))) {
                        filteredAttachments.add(attachment);
                    }
                }
                loadAttachments(filteredAttachments);
            } else {
                loadAttachments(allAttachments);
            }
        } else {
            loadAttachments(allAttachments);
        }
    }

    private void onSelectionChanged(final boolean selectionMode, final int count) {
        if (selectionMode && count > 0) {
            if (actionMode == null) {
                actionMode = startSupportActionMode(selectionActionModeCallback);
            }
            if (actionMode != null) {
                actionMode.setTitle(getResources().getQuantityString(R.plurals.selected_count, count, count));
                actionMode.invalidate();
            }
        } else if (actionMode != null) {
            actionMode.finish();
            actionMode = null;
        }
    }

    private Attachment getSingleSelectedAttachment() {
        final List<Attachment> selected = mMediaAdapter.getSelectedAttachments();
        return selected.size() == 1 ? selected.get(0) : null;
    }

    private void confirmDeleteSelected() {
        final int count = mMediaAdapter.getSelectedCount();
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_selected_files)
                .setMessage(getResources().getQuantityString(R.plurals.delete_files_confirm, count, count))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    final List<Attachment> deleted = mMediaAdapter.deleteSelectedAttachments();
                    ToastCompat.makeText(this,
                            getString(R.string.files_deleted_result, deleted.size(), count),
                            ToastCompat.LENGTH_SHORT).show();
                })
                .create().show();
    }

    private void onAttachmentsDeleted(final Collection<Attachment> deleted) {
        final Set<UUID> ids = new HashSet<>();
        for (final Attachment attachment : deleted) {
            ids.add(attachment.getUuid());
        }
        allAttachments.removeIf(attachment -> ids.contains(attachment.getUuid()));
        filteredAttachments.removeIf(attachment -> ids.contains(attachment.getUuid()));
        filter(OnlyImagesVideos);
    }

    private void saveSelectedAttachments() {
        final List<Attachment> selected = mMediaAdapter.getSelectedAttachments();
        if (selected.isEmpty()) return;
        new Thread(() -> {
            int saved = 0;
            if (xmppConnectionService != null) {
                for (final Attachment attachment : selected) {
                    final File file = new File(attachment.getUri().getPath());
                    try {
                        if (xmppConnectionService.getFileBackend().saveFileToDownloads(
                                file, attachment.getUri().getLastPathSegment())) {
                            saved++;
                        }
                    } catch (Exception e) {
                        Log.e(Config.LOGTAG, "could not save attachment", e);
                    }
                }
            }
            final int savedCount = saved;
            runOnUiThread(() -> {
                ToastCompat.makeText(this,
                        getString(R.string.files_saved_result, savedCount, selected.size()),
                        ToastCompat.LENGTH_SHORT).show();
                if (actionMode != null) {
                    actionMode.finish();
                } else {
                    mMediaAdapter.clearSelection();
                }
            });
        }).start();
    }
}
