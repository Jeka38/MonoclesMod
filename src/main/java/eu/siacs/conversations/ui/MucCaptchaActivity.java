package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;

import eu.siacs.conversations.R;
import eu.siacs.conversations.services.XmppConnectionService;
import me.drakeet.support.toast.ToastCompat;

public class MucCaptchaActivity extends XmppActivity {

    private AlertDialog dialog;
    private String token;

    @Override
    protected void refreshUiReal() {
        // no-op
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        token = getIntent().getStringExtra(XmppConnectionService.EXTRA_MUC_CAPTCHA_TOKEN);
    }

    @Override
    protected void onBackendConnected() {
        final XmppConnectionService.PendingMucCaptchaRequest request =
                xmppConnectionService == null ? null : xmppConnectionService.getPendingMucCaptchaRequest(token);
        if (request == null) {
            finish();
            return;
        }

        final View view = getLayoutInflater().inflate(R.layout.captcha, null);
        final ImageView imageView = view.findViewById(R.id.captcha);
        final EditText input = view.findViewById(R.id.input);
        if (request.captcha != null) {
            imageView.setImageBitmap(request.captcha);
        } else {
            imageView.setVisibility(View.GONE);
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.captcha_required)
                .setMessage(request.challenge == null ? getString(R.string.captcha_hint) : request.challenge)
                .setView(view)
                .setNegativeButton(R.string.cancel, (d, which) -> {
                    if (xmppConnectionService != null) {
                        xmppConnectionService.clearPendingMucCaptchaRequest(token);
                    }
                    finish();
                })
                .setPositiveButton(R.string.ok, null);
        dialog = builder.create();
        dialog.setOnCancelListener(d -> {
            if (xmppConnectionService != null) {
                xmppConnectionService.clearPendingMucCaptchaRequest(token);
            }
            finish();
        });
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String response = input.getText().toString().trim();
            final String expected = request.data.getValue("ocr");
            if (expected != null && !expected.trim().isEmpty() && !expected.trim().equals(response)) {
                ToastCompat.makeText(this, R.string.invalid_answer, ToastCompat.LENGTH_SHORT).show();
                return;
            }
            if (xmppConnectionService != null) {
                xmppConnectionService.submitPendingMucCaptchaRequest(token, response);
            }
            finish();
        }));
        dialog.show();
        input.requestFocus();
    }

    @Override
    protected void onDestroy() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        super.onDestroy();
    }
}
