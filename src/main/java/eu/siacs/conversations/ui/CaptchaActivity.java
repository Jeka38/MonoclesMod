package eu.siacs.conversations.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityCaptchaBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.services.XmppConnectionService.CaptchaRequest;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.forms.Field;

public class CaptchaActivity extends XmppActivity {

    public static final String EXTRA_ID = "id";
    public static final String EXTRA_ACCOUNT = "account";

    private ActivityCaptchaBinding binding;
    private String id;
    private Data data;
    private Account account;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_captcha);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar());
        getSupportActionBar().setTitle(R.string.pass_verification);

        id = getIntent().getStringExtra(EXTRA_ID);

        binding.submitButton.setOnClickListener(v -> submit());
        binding.cancelButton.setOnClickListener(v -> cancel());
        binding.refreshButton.setOnClickListener(v -> refresh());
    }

    private void refresh() {
        if (id != null && xmppConnectionServiceBound) {
            xmppConnectionService.requestNewCaptcha(account, id);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        final String newId = intent.getStringExtra(EXTRA_ID);
        if (newId != null && !newId.equals(id)) {
            Log.d(Config.LOGTAG, "CaptchaActivity: challenge changed from " + id + " to " + newId);
            id = newId;
            if (xmppConnectionServiceBound) {
                onBackendConnected();
            }
        } else {
            Log.d(Config.LOGTAG, "CaptchaActivity: challenge ID " + id + " is still current or new ID is null");
        }
    }

    private void submit() {
        if (data == null || account == null || id == null) {
            finish();
            return;
        }

        String rc = binding.inputEditText.getText().toString().trim();
        if (id.startsWith("reg:")) {
            data.put("username", account.getUsername());
            data.put("password", account.getPassword());
        }

        String captchaField = null;
        for (Field field : data.getFields()) {
            if (field.hasChild("media", "urn:xmpp:media-element")) {
                captchaField = field.getFieldName();
                break;
            }
        }
        if (captchaField == null) {
            if (data.getFieldByName("ocr") != null) {
                captchaField = "ocr";
            } else if (data.getFieldByName("captcha") != null) {
                captchaField = "captcha";
            } else if (data.getFieldByName("answers") != null) {
                captchaField = "answers";
            } else {
                for (Field field : data.getFields()) {
                    final String type = field.getType();
                    if ("text-single".equals(type) || "text-private".equals(type)) {
                        captchaField = field.getFieldName();
                        break;
                    }
                }
            }
        }

        if (captchaField != null) {
            data.put(captchaField, rc);
        }
        data.submit();

        if (xmppConnectionServiceBound) {
            replaceToast(getString(R.string.captcha_sending));
            xmppConnectionService.sendCaptchaResponse(account, id, data);
        }
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            cancel();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        cancel();
    }

    private void cancel() {
        if (id != null && xmppConnectionServiceBound) {
            if (id.startsWith("reg:")) {
                xmppConnectionService.sendCaptchaResponse(account, id, null);
            } else {
                xmppConnectionService.removeCaptchaRequest(id);
            }
        }
        finish();
    }

    @Override
    protected void refreshUiReal() {
    }

    @Override
    protected void onBackendConnected() {
        CaptchaRequest request = xmppConnectionService.getCaptchaRequest(id);
        if (request == null) {
            Log.d(Config.LOGTAG, "CAPTCHA request with id " + id + " not found in service");
            finish();
            return;
        }
        this.account = request.account;
        this.data = request.data;

        if (account != null) {
            binding.accountInfo.setText(String.format("%s (%s)", account.getJid().getDomain(), account.getJid().asBareJid().toString()));
            binding.accountInfo.setVisibility(View.VISIBLE);
        } else {
            binding.accountInfo.setVisibility(View.GONE);
        }

        if (id != null) {
            final String[] parts = id.split(" ", 2);
            final String typePrefix = parts[0];
            if (typePrefix.startsWith("muc:") || typePrefix.startsWith("msg:")) {
                binding.targetInfo.setText(typePrefix.substring(4));
                binding.targetInfo.setVisibility(View.VISIBLE);
            } else if (typePrefix.startsWith("reg:")) {
                binding.targetInfo.setText(R.string.account_registration);
                binding.targetInfo.setVisibility(View.VISIBLE);
            } else {
                binding.targetInfo.setVisibility(View.GONE);
            }
        } else {
            binding.targetInfo.setVisibility(View.GONE);
        }

        if (request.getCaptcha() != null) {
            binding.captchaImage.setImageBitmap(request.getCaptcha());
            binding.captchaImage.setVisibility(View.VISIBLE);
            binding.refreshButton.setVisibility(View.VISIBLE);
        } else {
            binding.captchaImage.setVisibility(View.GONE);
            binding.refreshButton.setVisibility(View.GONE);
        }

        String instructions = request.getInstructions();
        if (instructions != null) {
            binding.instructions.setText(instructions);
            binding.instructions.setVisibility(View.VISIBLE);
        } else {
            binding.instructions.setVisibility(View.GONE);
        }
    }
}
