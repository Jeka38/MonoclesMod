package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.ui.interfaces.OnBackendConnected;
import eu.siacs.conversations.xmpp.Jid;

public class PrivateXmlStorageActivity extends XmppActivity implements OnBackendConnected {

    public static final String EXTRA_ACCOUNT = "account";

    private final List<Bookmark> bookmarks = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private Account account;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_xml_storage);
        setTitle(R.string.title_activity_private_xml_storage);

        final ListView listView = findViewById(R.id.entries);
        final Button addButton = findViewById(R.id.add_entry);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
        listView.setAdapter(adapter);

        addButton.setOnClickListener(v -> showEditDialog(null, -1));
        listView.setOnItemClickListener((parent, view, position, id) -> showEditDialog(bookmarks.get(position), position));
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            final Bookmark bookmark = bookmarks.get(position);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_bookmark)
                    .setMessage(bookmark.getJid().asBareJid().toEscapedString())
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        xmppConnectionService.deleteBookmark(account, bookmark);
                        refreshBookmarks();
                    })
                    .show();
            return true;
        });
    }

    @Override
    void onBackendConnected() {
        final Intent intent = getIntent();
        final String jid = intent == null ? null : intent.getStringExtra(EXTRA_ACCOUNT);
        account = jid == null ? null : xmppConnectionService.findAccountByJid(Jid.ofEscaped(jid));
        refreshBookmarks();
    }

    private void refreshBookmarks() {
        bookmarks.clear();
        labels.clear();
        if (account != null) {
            bookmarks.addAll(account.getBookmarks());
            for (final Bookmark bookmark : bookmarks) {
                final String name = bookmark.getBookmarkName();
                labels.add((name == null || name.trim().isEmpty())
                        ? bookmark.getJid().asBareJid().toEscapedString()
                        : name + " (" + bookmark.getJid().asBareJid().toEscapedString() + ")");
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void showEditDialog(final Bookmark existing, final int position) {
        final LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        final EditText jidField = new EditText(this);
        jidField.setHint(R.string.account_settings_example_jabber_id);
        final EditText editText = new EditText(this);
        editText.setHint(R.string.bookmark_display_name);
        if (existing == null) {
            layout.addView(jidField);
        }
        layout.addView(editText);
        if (existing != null && existing.getBookmarkName() != null) {
            editText.setText(existing.getBookmarkName());
        }

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.add_bookmark_entry : R.string.edit_bookmark)
                .setView(layout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    final String value = editText.getText() == null ? null : editText.getText().toString().trim();
                    if (existing == null) {
                        try {
                            final Jid jid = Jid.ofEscaped(jidField.getText().toString().trim()).asBareJid();
                            final Bookmark bookmark = new Bookmark(account, jid);
                            bookmark.setBookmarkName(value);
                            xmppConnectionService.createBookmark(account, bookmark);
                        } catch (final IllegalArgumentException e) {
                            return;
                        }
                        return;
                    }
                    existing.setBookmarkName(value);
                    xmppConnectionService.createBookmark(account, existing);
                    refreshBookmarks();
                })
                .show();
    }
}
