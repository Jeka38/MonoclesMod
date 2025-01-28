package eu.siacs.conversations.ui;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import de.monocles.chat.SignUpPage;
import de.monocles.chat.services.ProviderService;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityMagicCreateBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.ui.util.CustomTab;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.InstallReferrerUtils;
import eu.siacs.conversations.xmpp.Jid;
import me.drakeet.support.toast.ToastCompat;

public class MagicCreateActivity extends XmppActivity implements TextWatcher, AdapterView.OnItemSelectedListener, CompoundButton.OnCheckedChangeListener {


    private boolean useOwnProvider = false;
    private boolean registerFromUri = false;
    public static final String EXTRA_DOMAIN = "domain";
    public static final String EXTRA_PRE_AUTH = "pre_auth";
    public static final String EXTRA_USERNAME = "username";
    public static final String EXTRA_REGISTER = "register";

    private ActivityMagicCreateBinding binding;
    private String domain;
    private String username;
    private String preAuth;

    @Override
    protected void refreshUiReal() {

    }

    @Override
    protected void onBackendConnected() {

    }

    @Override
    public void onStart() {
        super.onStart();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        final Intent data = getIntent();
        if (data != null) {
            this.domain = data.getStringExtra(EXTRA_DOMAIN);
            this.preAuth = data.getStringExtra(EXTRA_PRE_AUTH);
            this.username = data.getStringExtra(EXTRA_USERNAME);
            this.registerFromUri = data.getBooleanExtra(EXTRA_REGISTER, false);
        } else {
            this.domain = null;
            this.preAuth = null;
            this.username = null;
            this.registerFromUri = false;
        }
        if (getResources().getBoolean(R.bool.portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_magic_create);
        // Try fetching current providers list
        final List<String> domains = ProviderService.getProviders();
        Collections.sort(domains, String::compareToIgnoreCase);
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_selectable_list_item, domains);
        try {
            if (new ProviderService().execute().get()) {
                adapter.notifyDataSetChanged();
            }
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        // final List<String> domains = Arrays.asList(getResources().getStringArray(R.array.domains));
        int defaultServer = adapter.getPosition(de.monocles.chat.Config.DOMAIN.getRandomServer());
        if (registerFromUri && !useOwnProvider && (this.preAuth != null || domain != null)) {
            binding.server.setEnabled(false);
            binding.server.setVisibility(View.GONE);
            binding.useOwn.setEnabled(false);
            binding.useOwn.setChecked(true);
            binding.useOwn.setVisibility(View.GONE);
            binding.servertitle.setText(R.string.your_server);
            binding.yourserver.setVisibility(View.VISIBLE);
            binding.yourserver.setText(domain);
        } else {
            binding.yourserver.setVisibility(View.GONE);
        }
        binding.useOwn.setOnCheckedChangeListener(this);
        binding.server.setAdapter(adapter);
        binding.server.setSelection(defaultServer);
        binding.server.setOnItemSelectedListener(this);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        setSupportActionBar((Toolbar) this.binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar(), this.domain == null);
        if (username != null && domain != null) {
            binding.title.setText(R.string.your_server_invitation);
            binding.instructions.setText(getString(R.string.magic_create_text_fixed, domain));
            binding.username.setEnabled(false);
            binding.username.setText(this.username);
            updateFullJidInformation(this.username);
        } else if (domain != null) {
            binding.instructions.setText(getString(R.string.magic_create_text_on_x, domain));
        }
        binding.createAccount.setOnClickListener(v -> {
            try {
                final String username = binding.username.getText().toString();
                final boolean fixedUsername;
                final Jid jid;
                if (this.domain != null && this.username != null) {
                    fixedUsername = true;
                    jid = Jid.ofLocalAndDomainEscaped(this.username, this.domain);
                } else if (this.domain != null) {
                    fixedUsername = false;
                    jid = Jid.ofLocalAndDomainEscaped(username, this.domain);
                } else {
                    fixedUsername = false;
                    domain = updateDomain();
                    jid = Jid.ofLocalAndDomainEscaped(username, domain);
                }
                if (!jid.getEscapedLocal().equals(jid.getLocal())) {
                    binding.username.setError(getString(R.string.invalid_username));
                    binding.username.requestFocus();
                } else {
                    binding.username.setError(null);
                    Account account = xmppConnectionService.findAccountByJid(jid);
                    String password = CryptoHelper.createPassword(new SecureRandom());
                    if (account == null) {
                        account = new Account(jid, password);
                        account.setOption(Account.OPTION_REGISTER, true);
                        account.setOption(Account.OPTION_DISABLED, true);
                        account.setOption(Account.OPTION_MAGIC_CREATE, true);
                        account.setOption(Account.OPTION_FIXED_USERNAME, fixedUsername);
                        if (this.preAuth != null) {
                            account.setKey(Account.KEY_PRE_AUTH_REGISTRATION_TOKEN, this.preAuth);
                        }
                        xmppConnectionService.createAccount(account);
                    }
                    Intent intent = new Intent(MagicCreateActivity.this, EditAccountActivity.class);
                    intent.putExtra("jid", account.getJid().asBareJid().toString());
                    intent.putExtra("init", true);
                    intent.putExtra("existing", false);
                    intent.putExtra("useownprovider", useOwnProvider);
                    intent.putExtra("register", registerFromUri);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(getString(R.string.create_account));
                    builder.setCancelable(false);
                    StringBuilder messasge = new StringBuilder();
                    messasge.append(getString(R.string.secure_password_generated));
                    messasge.append("\n\n");
                    messasge.append(getString(R.string.password));
                    messasge.append(": ");
                    messasge.append(password);
                    messasge.append("\n\n");
                    messasge.append(getString(R.string.change_password_in_next_step));
                    builder.setMessage(messasge);
                    builder.setPositiveButton(getString(R.string.copy_to_clipboard), (dialogInterface, i) -> {
                        if (copyTextToClipboard(password, R.string.create_account)) {
                            StartConversationActivity.addInviteUri(intent, getIntent());
                            startActivity(intent);
                            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                            finish();
                            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                        }
                    });
                    builder.create().show();
                }
            } catch (IllegalArgumentException e) {
                binding.username.setError(getString(R.string.invalid_username));
                binding.username.requestFocus();
            }
        });
        binding.username.addTextChangedListener(this);

        Button SignUpButton = (Button) findViewById(R.id.activity_main_link);
        SignUpButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, SignUpPage.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        });
    }

    private String updateDomain() {
        String getUpdatedDomain = null;
        if (domain == null && !useOwnProvider) {
            getUpdatedDomain = Config.MAGIC_CREATE_DOMAIN;
        }
        if (useOwnProvider) {
            getUpdatedDomain = "your-domain.com";
        }
        return getUpdatedDomain;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        updateFullJidInformation(s.toString());
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        updateFullJidInformation(binding.username.getText().toString());
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        updateFullJidInformation(binding.username.getText().toString());
    }

    private void updateFullJidInformation(String username) {
        if (useOwnProvider && !registerFromUri) {
            this.domain = updateDomain();
        } else if (!registerFromUri && !binding.server.getSelectedItem().toString().isEmpty()) {
            this.domain = binding.server.getSelectedItem().toString();
        }
        if (username.trim().isEmpty()) {
            binding.fullJid.setVisibility(View.INVISIBLE);
        } else {
            try {
                binding.fullJid.setVisibility(View.VISIBLE);
                final Jid jid;
                if (this.domain == null) {
                    jid = Jid.ofLocalAndDomainEscaped(username, Config.MAGIC_CREATE_DOMAIN);
                } else {
                    jid = Jid.ofLocalAndDomainEscaped(username, this.domain);
                }
                binding.fullJid.setText(getString(R.string.your_full_jid_will_be, jid.toEscapedString()));
            } catch (IllegalArgumentException e) {
                binding.fullJid.setVisibility(View.INVISIBLE);
            }

        }
    }

    @Override
    public void onDestroy() {
        InstallReferrerUtils.markInstallReferrerExecuted(this);
        super.onDestroy();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (binding.useOwn.isChecked()) {
            binding.server.setEnabled(false);
            binding.fullJid.setVisibility(View.GONE);
            useOwnProvider = true;

        } else {
            binding.server.setEnabled(true);
            binding.fullJid.setVisibility(View.VISIBLE);
            useOwnProvider = false;
        }
        registerFromUri = false;
        updateFullJidInformation(binding.username.getText().toString());
    }
}