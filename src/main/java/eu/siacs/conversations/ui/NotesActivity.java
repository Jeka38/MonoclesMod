package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Note;
import eu.siacs.conversations.ui.adapter.NoteAdapter;

public class NotesActivity extends XmppActivity {

    private RecyclerView recyclerView;
    private NoteAdapter adapter;
    private List<Note> allNotes = new ArrayList<>();
    private TextView noNotes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.action_notebook);
        }

        recyclerView = findViewById(R.id.notes_list);
        noNotes = findViewById(R.id.no_notes);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NoteAdapter(allNotes);
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onBackendConnected() {
        allNotes.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            allNotes.addAll(account.getNotes());
        }
        adapter.notifyDataSetChanged();
        noNotes.setVisibility(allNotes.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void refreshUiReal() {
        // Handle UI refresh if needed
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
