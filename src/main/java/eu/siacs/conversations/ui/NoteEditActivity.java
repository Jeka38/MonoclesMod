package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityNoteEditBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Note;

/**
 * Editor for a single note. The note is stored on the account's private PEP node
 * (XEP-0223) via {@link eu.siacs.conversations.services.XmppConnectionService#saveNote}.
 */
public class NoteEditActivity extends XmppActivity {

    public static final String EXTRA_NOTE = "note";

    private ActivityNoteEditBinding binding;
    private Account account;
    private String noteKey;
    private Note note;
    private boolean existing;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_note_edit);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar());
        final Intent intent = getIntent();
        final String accountJid = intent.getStringExtra(EXTRA_ACCOUNT);
        this.noteKey = intent.getStringExtra(EXTRA_NOTE);
        this.existing = this.noteKey != null;
        if (accountJid == null) {
            finish();
        }
    }

    @Override
    protected void onBackendConnected() {
        if (this.account == null) {
            final String accountJid = getIntent().getStringExtra(EXTRA_ACCOUNT);
            for (final Account candidate : xmppConnectionService.getAccounts()) {
                if (candidate.getJid().asBareJid().toEscapedString().equals(accountJid)) {
                    this.account = candidate;
                    break;
                }
            }
        }
        if (this.account == null) {
            finish();
            return;
        }
        if (this.note == null) {
            loadNote();
        }
    }

    private void loadNote() {
        if (this.existing) {
            this.note = this.account.getNote(this.noteKey);
        }
        if (this.note == null) {
            this.note = new Note();
            this.existing = false;
            this.noteKey = null;
        }
        binding.noteTitle.setText(this.note.getTitle() == null ? "" : this.note.getTitle());
        binding.noteText.setText(this.note.getBody() == null ? "" : this.note.getBody());
        binding.noteTags.setText(TextUtils.join(" ", this.note.getTags()));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(this.existing ? R.string.edit_note : R.string.new_note);
        }
        invalidateOptionsMenu();
    }

    @Override
    protected void refreshUiReal() {
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.note_edit, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        final MenuItem delete = menu.findItem(R.id.action_delete_note);
        if (delete != null) {
            delete.setVisible(this.existing);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.action_save_note) {
            save();
            return true;
        } else if (id == R.id.action_discard_note) {
            finish();
            return true;
        } else if (id == R.id.action_delete_note) {
            confirmDelete();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void save() {
        if (this.account == null) {
            return;
        }
        final String title = text(binding.noteTitle);
        final String body = text(binding.noteText);
        final List<String> tags = parseTags(text(binding.noteTags));
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(body) && tags.isEmpty()) {
            replaceToast(getString(R.string.note_empty), false);
            return;
        }
        xmppConnectionService.saveNote(this.account, new Note(title, body, tags), this.noteKey);
        replaceToast(getString(R.string.note_saved), false);
        finish();
    }

    private void confirmDelete() {
        if (this.account == null || !this.existing || this.noteKey == null) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_note)
                .setMessage(R.string.delete_note_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    xmppConnectionService.deleteNote(this.account, this.noteKey);
                    replaceToast(getString(R.string.note_deleted), false);
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static String text(final android.widget.EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private static List<String> parseTags(final String raw) {
        final List<String> tags = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return tags;
        }
        // Space separated to match the Psi+/Miranda storage format.
        for (final String part : raw.trim().split("\\s+")) {
            final String tag = part.trim();
            if (!tag.isEmpty() && !tags.contains(tag)) {
                tags.add(tag);
            }
        }
        return tags;
    }
}
