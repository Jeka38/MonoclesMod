package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import eu.siacs.conversations.R;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.widget.TextInputEditText;

public class CaptchaActivity extends XmppActivity {

    private String mRequestId;
    private XmppConnectionService.CaptchaRequest mCaptchaRequest;

    private ImageView mCaptchaImage;
    private TextInputEditText mInput;
    private TextView mInstructions;
    private Button mCancel;
    private Button mOk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.captcha_activity);
        mCaptchaImage = findViewById(R.id.captcha);
        mInput = findViewById(R.id.input);
        mInstructions = findViewById(R.id.instructions);
        mCancel = findViewById(R.id.cancel);
        mOk = findViewById(R.id.ok);

        mCancel.setOnClickListener(v -> {
            xmppConnectionService.sendCaptchaResponse(mRequestId, null);
            finish();
        });

        mOk.setOnClickListener(v -> {
            Toast.makeText(this, R.string.captcha_sending, Toast.LENGTH_SHORT).show();
            xmppConnectionService.sendCaptchaResponse(mRequestId, mInput.getText().toString());
            finish();
        });

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
        if (xmppConnectionServiceBound) {
            onBackendConnected();
        }
    }

    private void handleIntent(Intent intent) {
        mRequestId = intent.getStringExtra("id");
    }

    @Override
    protected void refreshUiReal() {

    }

    @Override
    protected void onBackendConnected() {
        mCaptchaRequest = xmppConnectionService.getPendingCaptchaRequest(mRequestId);
        if (mCaptchaRequest == null) {
            finish();
            return;
        }
        mCaptchaImage.setImageBitmap(mCaptchaRequest.captcha);
        mInstructions.setText(mCaptchaRequest.data.getInstructions());
        String title = mCaptchaRequest.data.getTitle();
        if (title != null) {
            setTitle(title);
        } else {
            if (mRequestId != null && mRequestId.contains(" ")) {
                setTitle(R.string.pass_verification);
            } else {
                setTitle(R.string.account_registration);
            }
        }
    }

    public static void launch(Context context, String id) {
        Intent intent = new Intent(context, CaptchaActivity.class);
        intent.putExtra("id", id);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }
}
