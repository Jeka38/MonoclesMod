package de.monocles.mod.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import de.monocles.mod.XmppApplication;
import eu.siacs.conversations.R;
import me.drakeet.support.toast.ToastCompat;

public class CrashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String report = getIntent().getStringExtra(XmppApplication.EXTRA_CRASH_REPORT);
        final String text = report == null || report.isEmpty() ? getString(R.string.crash_unknown_error) : report;

        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 32, 48, 32);

        final TextView title = new TextView(this);
        title.setText(R.string.crash_dialog_title);
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        final TextView hint = new TextView(this);
        hint.setText(R.string.crash_dialog_hint);
        hint.setTextSize(14);
        hint.setPadding(0, 0, 0, 16);
        root.addView(hint);

        final TextView body = new TextView(this);
        body.setTextSize(12);
        body.setText(text);
        body.setTextIsSelectable(true);

        final ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.addView(body);
        root.addView(scroll);

        final Button copy = new Button(this);
        copy.setText(R.string.crash_copy);
        copy.setOnClickListener(v -> {
            final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("crash", text));
                ToastCompat.makeText(this, R.string.crash_copied, Toast.LENGTH_SHORT).show();
            }
        });

        final Button close = new Button(this);
        close.setText(R.string.crash_close);
        close.setOnClickListener(v -> {
            finish();
            Process.killProcess(Process.myPid());
        });

        final LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, 24, 0, 0);
        buttons.addView(copy);
        buttons.addView(close);
        root.addView(buttons);

        setContentView(root);
    }
}
