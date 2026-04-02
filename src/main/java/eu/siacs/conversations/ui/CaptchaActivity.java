package eu.siacs.conversations.ui;

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
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.forms.Field;

public class CaptchaActivity extends XmppActivity {

    public static final String EXTRA_ID = "id";
    public static final String EXTRA_DATA = "data";
    public static Bitmap captchaBitmap;

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
        String accountUuid = getIntent().getStringExtra(EXTRA_ACCOUNT);
        String dataXml = getIntent().getStringExtra(EXTRA_DATA);

        if (dataXml != null) {
            try {
                data = Data.parse(Element.parse(dataXml));
            } catch (Exception e) {
                Log.e(Config.LOGTAG, "Failed to parse CAPTCHA data form", e);
            }
        }

        if (accountUuid != null) {
            account = extractAccount(getIntent());
        }

        if (account != null) {
            binding.accountInfo.setText(String.format("%s (%s)", account.getJid().getDomain(), account.getJid().asBareJid().toString()));
        } else {
            binding.accountInfo.setVisibility(View.GONE);
        }

        if (id != null) {
            final String[] parts = id.split(" ", 2);
            final String typePrefix = parts[0];
            if (typePrefix.startsWith("muc:") || typePrefix.startsWith("msg:")) {
                binding.targetInfo.setText(typePrefix.substring(4));
            } else if (typePrefix.startsWith("reg:")) {
                binding.targetInfo.setText(R.string.account_registration);
            } else {
                binding.targetInfo.setVisibility(View.GONE);
            }
        } else {
            binding.targetInfo.setVisibility(View.GONE);
        }

        if (captchaBitmap != null) {
            binding.captchaImage.setImageBitmap(captchaBitmap);
        } else {
            binding.captchaImage.setVisibility(View.GONE);
        }

        if (data != null) {
            String instructions = data.findChildContent("instructions", Namespace.DATA);
            if (instructions != null) {
                binding.instructions.setText(instructions);
            } else {
                binding.instructions.setVisibility(View.GONE);
            }
        }

        binding.submitButton.setOnClickListener(v -> submit());
        binding.cancelButton.setOnClickListener(v -> cancel());
    }

    private void submit() {
        if (data == null || account == null || id == null) {
            finish();
            return;
        }

        String rc = binding.inputEditText.getText().toString();
        if (id.startsWith("reg:")) {
            data.put("username", account.getUsername());
            data.put("password", account.getPassword());
        }

        String captchaField = "ocr";
        if (data.getFieldByName("ocr") != null) {
            captchaField = "ocr";
        } else if (data.getFieldByName("answers") != null) {
            captchaField = "answers";
        } else {
            for (Field field : data.getFields()) {
                if ("text-single".equals(field.getType())) {
                    captchaField = field.getFieldName();
                    break;
                }
            }
        }
        data.put(captchaField, rc);
        data.submit();

        if (xmppConnectionServiceBound) {
            xmppConnectionService.sendCaptchaResponse(account, id, data);
        }
        captchaBitmap = null;
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
        if (id != null && id.startsWith("reg:") && xmppConnectionServiceBound) {
            xmppConnectionService.sendCaptchaResponse(account, id, null);
        }
        captchaBitmap = null;
        finish();
    }

    @Override
    protected void refreshUiReal() {
    }

    @Override
    protected void onBackendConnected() {
        if (account == null) {
            account = extractAccount(getIntent());
        }
    }
}
