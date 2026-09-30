package eu.siacs.conversations.ui;

import static eu.siacs.conversations.utils.AccountUtils.MANAGE_ACCOUNT_ACTIVITY;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityNotesBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Note;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.NoteAdapter;

/**
 * Lists the notes stored in the account's private XML storage (XEP-0049, Psi+/Miranda
 * compatible). Search covers title, tags and body; tapping a tag inserts it into the
 * search field. A long press enters selection mode for group deletion.
 */
public class NotesActivity extends XmppActivity implements XmppConnectionService.OnNotesUpdated {

    public static final String EXTRA_TAG = "tag";
    private static final int REQUEST_TAGS = 0x9E5;

    private ActivityNotesBinding binding;
    private final NoteAdapter adapter = new NoteAdapter();
    private List<Note> allNotes = Collections.emptyList();
    private Account account;
    private String needle = null;
    private ActionMode actionMode;

    private final ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
            mode.getMenuInflater().inflate(R.menu.note_selection, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
            if (item.getItemId() == R.id.action_delete_notes) {
                confirmDeleteSelected();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(final ActionMode mode) {
            actionMode = null;
            adapter.clearSelection();
        }
    };

    private final TextWatcher searchWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            needle = editable == null ? null : editable.toString();
            applyFilter();
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_notes);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar());
        this.binding.notesList.setLayoutManager(new LinearLayoutManager(this));
        this.binding.notesList.setAdapter(this.adapter);
        this.adapter.setOnNoteClickedListener(note -> openEditor(note.getKey()));
        this.adapter.setOnTagClickListener(this::searchForTag);
        this.adapter.setOnSelectionChangedListener((selectionMode, count) -> {
            if (selectionMode) {
                if (actionMode == null) {
                    actionMode = startSupportActionMode(actionModeCallback);
                }
                if (actionMode != null) {
                    actionMode.setTitle(getResources().getQuantityString(
                            R.plurals.selected_count, count, count));
                }
            } else if (actionMode != null) {
                actionMode.finish();
            }
        });
        this.binding.notesFab.setOnClickListener(v -> openEditor(null));
        this.binding.notesSearch.addTextChangedListener(searchWatcher);
        final BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            final int id = item.getItemId();
            if (id == R.id.chats) {
                startActivity(new Intent(getApplicationContext(), ConversationsActivity.class));
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                return true;
            } else if (id == R.id.contactslist) {
                final Intent intent = new Intent(getApplicationContext(), StartConversationActivity.class);
                intent.putExtra("show_nav_bar", true);
                startActivity(intent);
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                return true;
            } else if (id == R.id.manageaccounts) {
                final Intent intent = new Intent(getApplicationContext(), MANAGE_ACCOUNT_ACTIVITY);
                intent.putExtra("show_nav_bar", true);
                startActivity(intent);
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                return true;
            }
            return id == R.id.notes;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        final BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.notes);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.notes, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        final MenuItem selectAccount = menu.findItem(R.id.action_select_account);
        if (selectAccount != null) {
            selectAccount.setVisible(xmppConnectionService != null && xmppConnectionService.getAccounts().size() > 1);
        }
        final MenuItem sync = menu.findItem(R.id.action_sync_notes);
        if (sync != null) {
            sync.setVisible(xmppConnectionServiceBound && this.account != null);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.action_select_account) {
            chooseAccount(true);
            return true;
        } else if (id == R.id.action_sync_notes) {
            if (this.account != null) {
                xmppConnectionService.fetchNotes(this.account);
            }
            return true;
        } else if (id == R.id.action_all_tags) {
            if (this.account != null) {
                final Intent intent = new Intent(this, TagsActivity.class);
                intent.putExtra(EXTRA_ACCOUNT, this.account.getJid().asBareJid().toEscapedString());
                startActivityForResult(intent, REQUEST_TAGS);
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_TAGS && resultCode == RESULT_OK && data != null) {
            final String tag = data.getStringExtra(EXTRA_TAG);
            if (tag != null) {
                searchForTag(tag);
            }
        }
    }

    @Override
    protected void onBackendConnected() {
        if (this.account == null) {
            chooseAccount(false);
        } else {
            refresh();
        }
    }

    @Override
    protected void refreshUiReal() {
        refresh();
    }

    @Override
    public void onNotesUpdated() {
        runOnUiThread(this::refresh);
    }

    private void chooseAccount(final boolean force) {
        final List<Account> accounts = xmppConnectionService.getAccounts();
        if (accounts.isEmpty()) {
            this.account = null;
            refresh();
            return;
        }
        if (accounts.size() == 1) {
            this.account = accounts.get(0);
            invalidateOptionsMenu();
            refresh();
            return;
        }
        if (this.account != null && !force) {
            refresh();
            return;
        }
        final String[] labels = new String[accounts.size()];
        int checked = 0;
        for (int i = 0; i < accounts.size(); i++) {
            labels[i] = accounts.get(i).getJid().asBareJid().toString();
            if (accounts.get(i) == this.account) {
                checked = i;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_account)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    this.account = accounts.get(which);
                    dialog.dismiss();
                    invalidateOptionsMenu();
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void searchForTag(final String tag) {
        this.needle = tag;
        binding.notesSearch.setText(tag);
        binding.notesSearch.setSelection(binding.notesSearch.getText().length());
        applyFilter();
    }

    private void refresh() {
        if (this.account == null) {
            this.allNotes = Collections.emptyList();
        } else {
            final List<Note> notes = new ArrayList<>(this.account.getNotes());
            Collections.sort(notes, (a, b) -> titleOf(a).compareToIgnoreCase(titleOf(b)));
            this.allNotes = notes;
        }
        binding.notesSyncProgress.setVisibility(this.account != null && !this.account.areNotesLoaded() ? View.VISIBLE : View.GONE);
        applyFilter();
    }

    private static String titleOf(final Note note) {
        final String title = note.getTitle();
        return title == null ? "" : title;
    }

    private void applyFilter() {
        final List<Note> filtered = new ArrayList<>();
        for (final Note note : this.allNotes) {
            if (note.matches(needle, Note.Field.ALL)) {
                filtered.add(note);
            }
        }
        adapter.setNotes(filtered);
        binding.notesEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        binding.notesEmpty.setText(this.allNotes.isEmpty() ? R.string.no_notes : R.string.no_notes_in_account);
    }

    private void openEditor(final String key) {
        if (this.account == null || adapter.isSelectionMode()) {
            return;
        }
        final Intent intent = new Intent(this, NoteEditActivity.class);
        intent.putExtra(EXTRA_ACCOUNT, this.account.getJid().asBareJid().toEscapedString());
        if (key != null) {
            intent.putExtra(NoteEditActivity.EXTRA_NOTE, key);
        }
        startActivity(intent);
        overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
    }

    private void confirmDeleteSelected() {
        if (this.account == null) {
            return;
        }
        final List<String> keys = new ArrayList<>(adapter.getSelectedKeys());
        if (keys.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_notes)
                .setMessage(getResources().getQuantityString(R.plurals.delete_notes_confirm, keys.size(), keys.size()))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    xmppConnectionService.deleteNotes(this.account, keys);
                    replaceToast(getString(R.string.notes_deleted), false);
                    if (actionMode != null) {
                        actionMode.finish();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
