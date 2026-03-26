package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.XmlStanzaAdapter;

public class XmlConsoleActivity extends XmppActivity implements XmppConnectionService.OnXmlConsoleUpdate {

    private final List<String> stanzas = new ArrayList<>();
    private XmlStanzaAdapter adapter;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xml_console);
        setSupportActionBar(findViewById(R.id.toolbar));
        configureActionBar(getSupportActionBar());

        recyclerView = findViewById(R.id.xml_console_list);
        adapter = new XmlStanzaAdapter(stanzas);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.xml_console, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_clear) {
            if (xmppConnectionService != null) {
                xmppConnectionService.clearXmlBuffer();
            }
            return true;
        } else if (item.getItemId() == R.id.action_copy) {
            copyToClipboard();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void copyToClipboard() {
        StringBuilder builder = new StringBuilder();
        synchronized (this.stanzas) {
            for (String stanza : this.stanzas) {
                builder.append(stanza);
                builder.append('\n');
            }
        }
        if (copyTextToClipboard(builder.toString(), R.string.title_activity_xml_console)) {
            replaceToast(getString(R.string.message_copied_to_clipboard), false);
        }
    }

    @Override
    protected void onBackendConnected() {
        xmppConnectionService.setOnXmlConsoleUpdateListener(this);
        stanzas.clear();
        stanzas.addAll(xmppConnectionService.getXmlBuffer());
        adapter.notifyDataSetChanged();
        scrollToBottom();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (xmppConnectionServiceBound) {
            xmppConnectionService.setOnXmlConsoleUpdateListener(this);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (xmppConnectionService != null) {
            xmppConnectionService.removeOnXmlConsoleUpdateListener(this);
        }
    }

    @Override
    protected void refreshUiReal() {
        // No-op or update stanzas from buffer if needed
    }

    @Override
    public void onXmlConsoleUpdate(String stanza) {
        runOnUiThread(() -> {
            stanzas.add(stanza);
            adapter.notifyItemInserted(stanzas.size() - 1);
            scrollToBottom();
        });
    }

    @Override
    public void onXmlConsoleClear() {
        runOnUiThread(() -> {
            stanzas.clear();
            adapter.notifyDataSetChanged();
        });
    }

    private void scrollToBottom() {
        if (stanzas.size() > 0) {
            recyclerView.scrollToPosition(stanzas.size() - 1);
        }
    }
}
