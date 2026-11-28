package eu.siacs.conversations.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.List;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.R;
import eu.siacs.conversations.utils.ThemeHelper;
import eu.siacs.conversations.utils.XmlConsole;

public class XmlConsoleActivity extends XmppActivity implements XmlConsole.Listener {

    private TextView consoleText;
    private ScrollView scrollView;
    private final StringBuilder buffer = new StringBuilder();

    @Override
    protected void refreshUiReal() {
        // nothing
    }

    @Override
    protected void onBackendConnected() {
        XmlConsole.init(this);
        reload();
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(ThemeHelper.find(this));
        setContentView(R.layout.activity_xml_console);
        setSupportActionBar(findViewById(R.id.toolbar));
        configureActionBar(getSupportActionBar());
        consoleText = findViewById(R.id.xml_console_content);
        scrollView = findViewById(R.id.xml_console_scroll);
        consoleText.setTextIsSelectable(true);
        XmlConsole.init(this);
        reload();
    }

    @Override
    protected void onStart() {
        super.onStart();
        XmlConsole.addListener(this);
        invalidateOptionsMenu();
        reload();
    }

    @Override
    protected void onStop() {
        XmlConsole.removeListener(this);
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.xml_console, menu);
        final MenuItem toggle = menu.findItem(R.id.action_toggle_xml_console);
        if (toggle != null) {
            toggle.setTitle(XmlConsole.isEnabled() ? R.string.disable_xml_console : R.string.enable_xml_console);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.action_toggle_xml_console) {
            XmlConsole.setEnabled(this, !XmlConsole.isEnabled());
            invalidateOptionsMenu();
            reload();
            return true;
        } else if (id == R.id.action_copy_xml_console) {
            copySelectedOrAll();
            return true;
        } else if (id == R.id.action_export_xml_console) {
            export();
            return true;
        } else if (id == R.id.action_clear_xml_console) {
            confirmClear();
            return true;
        } else if (id == R.id.action_share_xml_console) {
            share();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void reload() {
        buffer.setLength(0);
        final List<String> entries = XmlConsole.getEntries();
        for (final String entry : entries) {
            buffer.append(entry).append("\n\n");
        }
        consoleText.setText(buffer.toString());
        scrollToBottom();
    }

    private void copySelectedOrAll() {
        final int start = consoleText.getSelectionStart();
        final int end = consoleText.getSelectionEnd();
        final CharSequence text;
        if (start >= 0 && end > start) {
            text = consoleText.getText().subSequence(start, end);
        } else {
            text = consoleText.getText();
        }
        if (text == null || text.length() == 0) {
            Toast.makeText(this, R.string.xml_console_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        final ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.xml_console), text));
            Toast.makeText(this, R.string.xml_console_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_xml_console)
                .setMessage(R.string.clear_xml_console_confirm)
                .setPositiveButton(R.string.clear_txt, (d, w) -> {
                    XmlConsole.clear();
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private File exportFile() throws Exception {
        return XmlConsole.export(getExternalFilesDir("Logs"));
    }

    private void export() {
        try {
            final File file = exportFile();
            Toast.makeText(this, getString(R.string.xml_console_exported, file.getAbsolutePath()), Toast.LENGTH_LONG).show();
        } catch (final Exception e) {
            Toast.makeText(this, R.string.xml_console_export_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void share() {
        try {
            final File file = exportFile();
            final Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".files", file);
            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.share_xml_console)));
        } catch (final Exception e) {
            Toast.makeText(this, R.string.xml_console_export_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    public void onEntry(final String entry) {
        runOnUiThread(() -> {
            buffer.append(entry).append("\n\n");
            consoleText.setText(buffer.toString());
            scrollToBottom();
        });
    }
}
