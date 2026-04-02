package eu.siacs.conversations.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.forms.Data;

public class CaptchaActivity extends XmppActivity implements XmppConnectionService.OnCaptchaRequested {

    private String mCaptchaId;
    private Data mData;
    private Account mAccount;

    private ImageView mImageView;
    private EditText mInput;
    private TextView mInstructions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.captcha_activity);
        getWindow().setLayout(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        mImageView = findViewById(R.id.captcha);
        mInput = findViewById(R.id.input);
        mInstructions = findViewById(R.id.instructions);
        Button cancel = findViewById(R.id.cancel);
        Button ok = findViewById(R.id.ok);

        cancel.setOnClickListener(v -> finish());
        ok.setOnClickListener(v -> submit());

        if (getIntent() != null) {
            mCaptchaId = getIntent().getStringExtra("id");
            String accountUuid = getIntent().getStringExtra("account");
            if (xmppConnectionServiceBound) {
                mAccount = xmppConnectionService.findAccountByUuid(accountUuid);
                XmppConnectionService.CaptchaRequest request = xmppConnectionService.getPendingCaptchaRequest(mCaptchaId);
                if (request != null) {
                    this.mData = request.data;
                    updateUi();
                }
            }
        }
    }

    @Override
    protected void onBackendConnected() {
        if (mAccount == null && getIntent() != null) {
            mAccount = xmppConnectionService.findAccountByUuid(getIntent().getStringExtra("account"));
        }
        if (mData == null && mCaptchaId != null) {
            XmppConnectionService.CaptchaRequest request = xmppConnectionService.getPendingCaptchaRequest(mCaptchaId);
            if (request != null) {
                this.mData = request.data;
                updateUi();
            }
        }
    }

    private void updateUi() {
        if (mData == null) return;
        runOnUiThread(() -> {
            String instructions = mData.getTitle();
            if (instructions == null || instructions.isEmpty()) {
                instructions = getString(R.string.captcha_required);
            }
            mInstructions.setText(instructions);
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (xmppConnectionServiceBound && mCaptchaId != null) {
            xmppConnectionService.removePendingCaptchaRequest(mCaptchaId);
        }
    }

    private void submit() {
        if (mAccount != null && mData != null) {
            String ocr = mInput.getText().toString();
            boolean fieldFound = false;
            for (eu.siacs.conversations.xmpp.forms.Field field : mData.getFields()) {
                if (!"hidden".equals(field.getAttribute("type"))) {
                    field.setValue(ocr);
                    fieldFound = true;
                }
            }
            if (!fieldFound) {
                mData.put("ocr", ocr);
            }
            xmppConnectionService.sendCaptchaResponse(mAccount, mCaptchaId, mData);
        }
        finish();
    }

    @Override
    public void onCaptchaRequested(Account account, String id, Data data, Bitmap captcha) {
        if (id.equals(mCaptchaId)) {
            runOnUiThread(() -> {
                this.mData = data;
                if (captcha != null) {
                    mImageView.setImageBitmap(captcha);
                    mImageView.setVisibility(View.VISIBLE);
                } else {
                    mImageView.setVisibility(View.GONE);
                }
                String instructions = data.getTitle();
                if (instructions == null || instructions.isEmpty()) {
                    instructions = getString(R.string.captcha_required);
                }
                mInstructions.setText(instructions);
            });
        }
    }

    @Override
    protected void refreshUiReal() {

    }
}
