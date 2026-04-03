package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.R;
import eu.siacs.conversations.services.XmppConnectionService;

public class XmlConsoleActivity extends XmppActivity implements XmppConnectionService.OnStanzaLogged {

    private final SimpleDateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private StanzaAdapter adapter;
    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xml_console);
        setSupportActionBar(findViewById(R.id.toolbar));
        configureActionBar(getSupportActionBar());
        listView = findViewById(R.id.console_list);
    }

    @Override
    protected void onBackendConnected() {
        List<XmppConnectionService.StanzaLogEntry> logs = xmppConnectionService.getStanzaLogs();
        adapter = new StanzaAdapter(new ArrayList<>(logs));
        listView.setAdapter(adapter);
        xmppConnectionService.addOnStanzaLoggedListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (xmppConnectionService != null) {
            xmppConnectionService.removeOnStanzaLoggedListener(this);
        }
    }

    @Override
    public void refreshUiReal() {

    }

    @Override
    public void onStanzaLogged(XmppConnectionService.StanzaLogEntry entry) {
        runOnUiThread(() -> {
            if (adapter != null) {
                adapter.add(entry);
                if (adapter.getCount() > 500) {
                    adapter.remove(adapter.getItem(0));
                }
            }
        });
    }

    private class StanzaAdapter extends ArrayAdapter<XmppConnectionService.StanzaLogEntry> {

        public StanzaAdapter(List<XmppConnectionService.StanzaLogEntry> objects) {
            super(XmlConsoleActivity.this, 0, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            XmppConnectionService.StanzaLogEntry entry = getItem(position);
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.xml_console_item, parent, false);
            }
            TextView header = convertView.findViewById(R.id.stanza_header);
            TextView body = convertView.findViewById(R.id.stanza_body);

            String direction = entry.incoming ? "RECV" : "SENT";
            header.setText(String.format("[%s] %s - %s", timestampFormat.format(new Date(entry.timestamp)), entry.account, direction));
            body.setText(entry.xml);

            return convertView;
        }
    }
}
