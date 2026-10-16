package eu.siacs.conversations.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;

import eu.siacs.conversations.R;
import eu.siacs.conversations.utils.LogcatReader;
import eu.siacs.conversations.utils.ThemeHelper;
import me.drakeet.support.toast.ToastCompat;

/**
 * Shows the device logcat inside the app so a log can be copied or shared without ADB. Handy on
 * devices where ADB is unavailable (Huawei/EMUI) or when reporting a bug. Filtered to this app by
 * default; a menu item switches to the full buffer.
 */
public class LogcatActivity extends XmppActivity {

    public static final String EXTRA_APP_ONLY = "app_only";

    private static final int MAX_LINES = 8000;

    private ScrollView scroll;
    private TextView logView;
    private boolean appOnly = true;
    private boolean loading = false;

    @Override
    protected void refreshUiReal() {
        // nothing periodic
    }

    @Override
    protected void onBackendConnected() {
        // The log is not tied to the connection; a manual refresh loads it.
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(ThemeHelper.find(this));
        setContentView(R.layout.activity_logcat);
        setSupportActionBar(findViewById(R.id.toolbar));
        configureActionBar(getSupportActionBar());
        scroll = findViewById(R.id.log_scroll);
        logView = findViewById(R.id.log_text);
        appOnly = getIntent() == null || getIntent().getBooleanExtra(EXTRA_APP_ONLY, true);
        reload();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.logcat, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        final MenuItem toggle = menu.findItem(R.id.action_logcat_filter);
        if (toggle != null) {
            toggle.setTitle(appOnly ? R.string.logcat_show_all : R.string.logcat_show_app);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_logcat_refresh) {
            reload();
            return true;
        } else if (itemId == R.id.action_logcat_filter) {
            appOnly = !appOnly;
            invalidateOptionsMenu();
            reload();
            return true;
        } else if (itemId == R.id.action_logcat_copy) {
            copyToClipboard();
            return true;
        } else if (itemId == R.id.action_logcat_share) {
            share();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void reload() {
        if (loading) {
            return;
        }
        loading = true;
        logView.setText(R.string.logcat_loading);
        new LoadTask(this).execute();
    }

    private void onLoaded(final LogcatReader.Result result) {
        loading = false;
        if (!result.ok) {
            logView.setText(getString(R.string.logcat_error, result.error == null ? "" : result.error));
            return;
        }
        if (result.text.isEmpty()) {
            logView.setText(R.string.logcat_empty);
            return;
        }
        logView.setText(result.text);
        // Jump to the newest entry (the buffer is oldest-first).
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void copyToClipboard() {
        final CharSequence text = logView.getText();
        if (text == null || text.length() == 0) {
            return;
        }
        final ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("logcat", text));
            ToastCompat.makeText(this, R.string.logcat_copied, ToastCompat.LENGTH_SHORT).show();
        }
    }

    private void share() {
        final CharSequence text = logView.getText();
        if (text == null || text.length() == 0) {
            return;
        }
        // Write to a file and share that: a full log easily exceeds the Binder transaction limit
        // (EXTRA_TEXT), which would throw and lose the share entirely.
        final File file = new File(getCacheDir(), "monocles-log.txt");
        try (final FileWriter writer = new FileWriter(file, false)) {
            writer.write(text.toString());
        } catch (final IOException e) {
            // Fall back to text if the file cannot be written.
            final Intent fallback = new Intent(Intent.ACTION_SEND);
            fallback.setType("text/plain");
            fallback.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.title_activity_logcat));
            fallback.putExtra(Intent.EXTRA_TEXT, text.toString());
            startActivity(Intent.createChooser(fallback, getString(R.string.logcat_share)));
            return;
        }
        final Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.title_activity_logcat));
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.logcat_share)));
    }

    private static final class LoadTask extends AsyncTask<Void, Void, LogcatReader.Result> {
        private final WeakReference<LogcatActivity> activity;
        private final boolean appOnly;

        LoadTask(final LogcatActivity activity) {
            this.activity = new WeakReference<>(activity);
            this.appOnly = activity.appOnly;
        }

        @Override
        protected LogcatReader.Result doInBackground(final Void... voids) {
            return LogcatReader.read(MAX_LINES, appOnly);
        }

        @Override
        protected void onPostExecute(final LogcatReader.Result result) {
            final LogcatActivity activity = this.activity.get();
            if (activity != null && !activity.isFinishing()) {
                activity.onLoaded(result);
            }
        }
    }
}
