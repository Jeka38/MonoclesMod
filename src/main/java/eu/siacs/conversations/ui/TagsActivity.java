package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityTagsBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Note;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.TagAdapter;

/**
 * Overview of all tags across the account's notes (without note titles/bodies) for quick
 * lookup. Backed by a {@link androidx.recyclerview.widget.RecyclerView} so a large number of
 * tags stays responsive. Selecting a tag returns it to {@link NotesActivity} as a search query.
 */
public class TagsActivity extends XmppActivity implements XmppConnectionService.OnNotesUpdated {

    private ActivityTagsBinding binding;
    private final TagAdapter adapter = new TagAdapter();
    private String needle = null;

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
            refresh();
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_tags);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar());
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.all_tags);
        }
        this.binding.tagsList.setLayoutManager(new LinearLayoutManager(this));
        this.binding.tagsList.setAdapter(this.adapter);
        this.adapter.setOnTagSelectedListener(this::returnTag);
        this.binding.tagsSearch.addTextChangedListener(searchWatcher);
    }

    @Override
    protected void onBackendConnected() {
        refresh();
    }

    @Override
    protected void refreshUiReal() {
        refresh();
    }

    @Override
    public void onNotesUpdated() {
        runOnUiThread(this::refresh);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void returnTag(final String tag) {
        final Intent result = new Intent();
        result.putExtra(NotesActivity.EXTRA_TAG, tag);
        setResult(RESULT_OK, result);
        finish();
    }

    private void refresh() {
        final Account account = account();
        final TreeMap<String, Integer> counted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (account != null) {
            for (final Note note : account.getNotes()) {
                for (final String tag : note.getTags()) {
                    counted.merge(tag, 1, Integer::sum);
                }
            }
        }
        final String filter = needle == null ? null : needle.trim().toLowerCase(Locale.US);
        final List<String> tags = new ArrayList<>();
        final List<Integer> counts = new ArrayList<>();
        for (final String tag : counted.keySet()) {
            if (filter == null || filter.isEmpty() || tag.toLowerCase(Locale.US).contains(filter)) {
                tags.add(tag);
                counts.add(counted.get(tag));
            }
        }
        adapter.submit(tags, counts);
        binding.tagsEmpty.setVisibility(tags.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private Account account() {
        final String accountJid = getIntent().getStringExtra(EXTRA_ACCOUNT);
        if (xmppConnectionService == null) {
            return null;
        }
        if (accountJid == null) {
            final List<Account> accounts = xmppConnectionService.getAccounts();
            return accounts.isEmpty() ? null : accounts.get(0);
        }
        for (final Account account : xmppConnectionService.getAccounts()) {
            if (account.getJid().asBareJid().toEscapedString().equals(accountJid)) {
                return account;
            }
        }
        return null;
    }
}
