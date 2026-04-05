package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityCaptchaBinding;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.forms.Field;

public class CaptchaActivity extends XmppActivity {

    private ActivityCaptchaBinding binding;
    private String id;
    private XmppConnectionService.CaptchaRequest request;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_captcha);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        id = getIntent().getStringExtra("id");
        binding.cancelButton.setOnClickListener(v -> finish());
        binding.okButton.setOnClickListener(v -> submit());
    }

    @Override
    protected void onBackendConnected() {
        request = xmppConnectionService.getPendingCaptchaRequest(id);
        if (request == null) {
            finish();
            return;
        }
        String title = request.data.getTitle();
        if (title != null) {
            setTitle(title);
        } else {
            setTitle(R.string.captcha_required);
        }
        String instructions = request.data.getInstructions();
        if (instructions != null) {
            binding.instructions.setText(instructions);
            binding.instructions.setVisibility(View.VISIBLE);
        } else {
            binding.instructions.setVisibility(View.GONE);
        }
        binding.captcha.setImageBitmap(request.bitmap);
    }

    private void submit() {
        String challenge = binding.input.getText().toString();
        replaceToast(getString(R.string.captcha_sending), false);
        Data data = request.data;
        if (id.startsWith("reg:")) {
            data.put("username", request.account.getUsername());
            data.put("password", request.account.getPassword());
            data.put("ocr", challenge);
            data.submit();
        } else {
            Field field = data.getFieldByName("answers");
            if (field == null) {
                field = data.getFieldByName("captcha");
            }
            if (field == null) {
                for (Field f : data.getFields()) {
                    if ("text-single".equals(f.getType())) {
                        field = f;
                        break;
                    }
                }
            }
            if (field != null) {
                field.setValue(challenge);
            } else {
                data.put("answers", challenge);
            }
        }
        xmppConnectionService.sendCaptchaResponse(request.account, id, data);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void refreshUiReal() {
        // Nothing to do
    }
}
