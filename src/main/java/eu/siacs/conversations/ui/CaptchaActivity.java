package eu.siacs.conversations.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.CaptchaRequest;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.forms.Field;

public class CaptchaActivity extends XmppActivity {

    private String mRequestId;
    private CaptchaRequest mRequest;
    private ImageView mCaptchaImage;
    private EditText mInput;
    private TextView mInstructions;
    private TextView mAccountView;
    private TextView mTargetView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.captcha_activity);
        mRequestId = getIntent().getStringExtra("id");
        if (mRequestId == null && savedInstanceState != null) {
            mRequestId = savedInstanceState.getString("id");
        }
        mCaptchaImage = findViewById(R.id.captcha);
        mInput = findViewById(R.id.input);
        mInstructions = findViewById(R.id.instructions);
        mAccountView = findViewById(R.id.account);
        mTargetView = findViewById(R.id.target);
        Button okButton = findViewById(R.id.ok_button);
        Button cancelButton = findViewById(R.id.cancel_button);

        okButton.setOnClickListener(v -> submit());
        cancelButton.setOnClickListener(v -> cancel());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        mRequestId = intent.getStringExtra("id");
        if (xmppConnectionServiceBound) {
            refresh();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("id", mRequestId);
    }

    @Override
    protected void onBackendConnected() {
        refresh();
    }

    private void refresh() {
        mRequest = xmppConnectionService.getPendingCaptchaRequest(mRequestId);
        if (mRequest == null) {
            finish();
            return;
        }
        String title = mRequest.getData() != null ? mRequest.getData().getTitle() : null;
        if (title != null) {
            setTitle(title);
        } else if (mRequest.getStanzaId().startsWith("reg:")) {
            setTitle(R.string.register_account);
        } else {
            setTitle(R.string.captcha_required);
        }
        mAccountView.setText(mRequest.getAccount().getJid().asBareJid().toString());
        if (mRequest.getTarget() != null) {
            mTargetView.setText(mRequest.getTarget().toString());
            mTargetView.setVisibility(View.VISIBLE);
        } else {
            mTargetView.setVisibility(View.GONE);
        }
        if (mRequest.getBitmap() != null) {
            mCaptchaImage.setImageBitmap(mRequest.getBitmap());
            mCaptchaImage.setVisibility(View.VISIBLE);
        } else {
            mCaptchaImage.setVisibility(View.GONE);
        }
        Data data = mRequest.getData();
        if (data != null) {
            StringBuilder instructions = new StringBuilder();
            String dataInstructions = data.findChildContent("instructions", Namespace.DATA);
            if (dataInstructions != null) {
                instructions.append(dataInstructions);
            }
            for (Field field : data.getFields()) {
                if ("fixed".equals(field.getType())) {
                    if (instructions.length() > 0) instructions.append("\n");
                    instructions.append(field.getValue());
                }
            }
            if (instructions.length() > 0) {
                mInstructions.setVisibility(View.VISIBLE);
                mInstructions.setText(instructions.toString());
            } else {
                mInstructions.setVisibility(View.GONE);
            }
        }
    }

    private void submit() {
        String solutionText = mInput.getText().toString().trim();
        Data solutionForm = mRequest.getData();
        if (solutionForm != null) {
            Field answerField = null;
            for (Field field : solutionForm.getFields()) {
                if (field.hasChild("media", Namespace.MEDIA_ELEMENT)) {
                    answerField = field;
                    break;
                }
            }
            if (answerField == null) {
                answerField = solutionForm.getFieldByName("ocr");
            }
            if (answerField == null) {
                answerField = solutionForm.getFieldByName("answers");
            }
            if (answerField == null) {
                answerField = solutionForm.getFieldByName("captcha");
            }

            if (answerField != null) {
                answerField.setValue(solutionText);
            } else {
                // Fallback: use first text field
                for (Field field : solutionForm.getFields()) {
                    String type = field.getType();
                    if ("text-single".equals(type) || "text-private".equals(type)) {
                        field.setValue(solutionText);
                        break;
                    }
                }
            }
            solutionForm.submit();
        }
        xmppConnectionService.sendCaptchaResponse(mRequestId, solutionForm);
        finish();
    }

    private void cancel() {
        if (mRequestId != null && xmppConnectionServiceBound) {
            xmppConnectionService.sendCaptchaResponse(mRequestId, null);
        }
        finish();
    }

    @Override
    protected void refreshUiReal() {
    }
}
