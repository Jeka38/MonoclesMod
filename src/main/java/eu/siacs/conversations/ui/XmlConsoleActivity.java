package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.StanzaHistory;
import eu.siacs.conversations.xmpp.Jid;

import eu.siacs.conversations.services.XmppConnectionService;

public class XmlConsoleActivity extends XmppActivity implements XmppConnectionService.OnConversationUpdate {

    private Account account;
    private StanzaAdapter adapter;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xml_console);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StanzaAdapter();
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void refreshUiReal() {
        updateStanzas();
    }

    @Override
    protected void registerListeners() {
        super.registerListeners();
        this.xmppConnectionService.registerOnConversationUpdateListener(this);
    }

    @Override
    protected void unregisterListeners() {
        super.unregisterListeners();
        this.xmppConnectionService.unregisterOnConversationUpdateListener(this);
    }

    @Override
    protected void onBackendConnected() {
        try {
            this.account = xmppConnectionService.findAccountByJid(Jid.of(getIntent().getStringExtra("account")));
        } catch (Exception e) {
            this.account = null;
        }
        if (this.account == null) {
            finish();
            return;
        }
        setTitle(getString(R.string.xml_console) + " (" + account.getJid().asBareJid() + ")");
        updateStanzas();
    }

    private void updateStanzas() {
        if (account != null) {
            adapter.setStanzas(account.getStanzaHistory().getStanzas());
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    @Override
    public void onConversationUpdate() {
        runOnUiThread(this::updateStanzas);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.xml_console, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_clear) {
            if (account != null) {
                account.getStanzaHistory().clear();
                updateStanzas();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static class StanzaAdapter extends RecyclerView.Adapter<StanzaViewHolder> {

        private List<StanzaHistory.Stanza> stanzas;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

        public void setStanzas(List<StanzaHistory.Stanza> stanzas) {
            this.stanzas = stanzas;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public StanzaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.stanza_item, parent, false);
            return new StanzaViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull StanzaViewHolder holder, int position) {
            StanzaHistory.Stanza stanza = stanzas.get(position);
            holder.timestamp.setText(dateFormat.format(new Date(stanza.getTimestamp())));
            holder.direction.setText(stanza.getDirection() == StanzaHistory.Stanza.Direction.SENT ? "SENT" : "RECEIVED");
            holder.direction.setTextColor(stanza.getDirection() == StanzaHistory.Stanza.Direction.SENT ? 0xFF009688 : 0xFFE91E63);
            holder.content.setText(stanza.getContent());
        }

        @Override
        public int getItemCount() {
            return stanzas == null ? 0 : stanzas.size();
        }
    }

    private static class StanzaViewHolder extends RecyclerView.ViewHolder {
        TextView timestamp;
        TextView direction;
        TextView content;

        public StanzaViewHolder(@NonNull View itemView) {
            super(itemView);
            timestamp = itemView.findViewById(R.id.timestamp);
            direction = itemView.findViewById(R.id.direction);
            content = itemView.findViewById(R.id.content);
        }
    }
}
