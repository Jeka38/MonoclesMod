package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import de.monocles.mod.Util;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityMucContactDetailsBinding;
import eu.siacs.conversations.databinding.CommandRowBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.ui.util.ShareUtil;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.utils.IrregularUnicodeDetector;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;

public class ConferenceContactDetailsActivity extends XmppActivity {
    public static final String ACTION_VIEW_CONTACT = "view_contact";

    private Conversation mConversation;
    ActivityMucContactDetailsBinding binding;
    private Jid accountJid;
    private Jid contactJid;
    private MucOptions.User user = null;

    @Override
    protected void refreshUiReal() {
        invalidateOptionsMenu();
        populateView();
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getAction().equals(ACTION_VIEW_CONTACT)) {
            try {
                this.accountJid = Jid.ofEscaped(getIntent().getExtras().getString(EXTRA_ACCOUNT));
            } catch (final IllegalArgumentException ignored) {
            }
            try {
                this.contactJid = Jid.ofEscaped(getIntent().getExtras().getString("user"));
            } catch (final IllegalArgumentException ignored) {
            }
        }
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_muc_contact_details);
        setSupportActionBar((Toolbar) binding.toolbar.getRoot());
        configureActionBar(getSupportActionBar());
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        }
    }

    private void populateView() {
        if (getSupportActionBar() != null) {
            final ActionBar ab = getSupportActionBar();
            if (ab != null) {
                ab.setCustomView(R.layout.ab_title);
                ab.setDisplayShowCustomEnabled(true);
                TextView abtitle = findViewById(android.R.id.text1);
                TextView absubtitle = findViewById(android.R.id.text2);
                abtitle.setText(R.string.contact_details);
                abtitle.setSelected(true);
                abtitle.setClickable(false);
                absubtitle.setVisibility(View.GONE);
                absubtitle.setClickable(false);
            }
        }
        if (user == null) {
            return;
        }
        binding.contactDisplayName.setText(user.getName());
        binding.jid.setText(IrregularUnicodeDetector.style(this, contactJid));
        String account = accountJid.asBareJid().toEscapedString();
        binding.detailsAccount.setText(getString(R.string.using_account, account));
        AvatarWorkerTask.loadAvatar(user, binding.detailsContactBadge, R.dimen.avatar_on_details_screen_size);
        binding.detailsContactBadge.setOnLongClickListener(v -> {
            ShowAvatarPopup(ConferenceContactDetailsActivity.this, user);
            return true;
        });
        if (xmppConnectionService.multipleAccounts()) {
            binding.detailsAccount.setVisibility(View.VISIBLE);
        } else {
            binding.detailsAccount.setVisibility(View.GONE);
        }
    }

    public void onBackendConnected() {
        if (accountJid != null && contactJid != null) {
            Account account = xmppConnectionService.findAccountByJid(accountJid);
            if (account == null) {
                return;
            }
            this.mConversation = xmppConnectionService.findConversation(account, contactJid, false);
            final MucOptions mucOptions = ((Conversation) this.mConversation).getMucOptions();
            this.user = mucOptions.findUserByFullJid(contactJid);
            setupVcard(account);
            populateView();
        }
    }

    private void setupVcard(final Account account) {
        final Jid realJid = user == null ? null : user.getRealJid();
        if (realJid == null) {
            binding.profile.setVisibility(View.GONE);
            return;
        }
        final Contact contact = account.getRoster().getContact(realJid.asBareJid());
        final VcardAdapter items = new VcardAdapter();
        binding.profileItems.setAdapter(items);
        binding.profileItems.setOnItemClickListener((a0, v, pos, a3) -> {
            final Uri uri = items.getUri(pos);
            if (uri == null) {
                return;
            }
            try {
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, uri));
            } catch (Exception e) {
                Toast.makeText(this, R.string.no_application_found_to_open_link, Toast.LENGTH_SHORT).show();
            }
        });
        binding.profileItems.setOnItemLongClickListener((a0, v, pos, a3) -> {
            String toCopy = null;
            final Uri uri = items.getUri(pos);
            if (uri != null) {
                toCopy = uri.toString();
            }
            if (toCopy == null) {
                final Element item = items.getItem(pos);
                toCopy = item == null ? null : item.findChildContent("text", Namespace.VCARD4);
            }
            if (toCopy == null) {
                return false;
            }
            if (ShareUtil.copyTextToClipboard(ConferenceContactDetailsActivity.this, toCopy, R.string.message)) {
                Toast.makeText(ConferenceContactDetailsActivity.this, R.string.message_copied_to_clipboard, Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        xmppConnectionService.fetchVcard4(account, contact, vcard4 -> {
            if (vcard4 == null) {
                return;
            }
            runOnUiThread(() -> {
                items.clear();
                for (Element el : vcard4.getChildren()) {
                    if (el.findChildEnsureSingle("uri", Namespace.VCARD4) != null || el.findChildEnsureSingle("text", Namespace.VCARD4) != null) {
                        items.add(el);
                    }
                }
                if (items.getCount() > 0) {
                    binding.profile.setVisibility(View.VISIBLE);
                    Util.justifyListViewHeightBasedOnChildren(binding.profileItems);
                } else {
                    binding.profile.setVisibility(View.GONE);
                }
            });
        });
    }

    class VcardAdapter extends ArrayAdapter<Element> {
        VcardAdapter() {
            super(ConferenceContactDetailsActivity.this, 0);
        }

        @Override
        public View getView(int position, View view, @NonNull ViewGroup parent) {
            final CommandRowBinding rowBinding = DataBindingUtil.inflate(
                    LayoutInflater.from(parent.getContext()),
                    R.layout.command_row,
                    parent,
                    false
            );
            final Element item = getItem(position);
            final Uri uri = getUri(position);
            if (uri != null && uri.getScheme() != null) {
                if ("xmpp".equals(uri.getScheme()) || "tel".equals(uri.getScheme()) || "mailto".equals(uri.getScheme())) {
                    rowBinding.command.setText(uri.getSchemeSpecificPart());
                } else {
                    rowBinding.command.setText(uri.toString());
                }
            } else if (item != null) {
                rowBinding.command.setText(item.findChildContent("text", Namespace.VCARD4));
            }
            return rowBinding.getRoot();
        }

        public Uri getUri(int pos) {
            final Element item = getItem(pos);
            if (item == null) {
                return null;
            }
            final String uriS = item.findChildContent("uri", Namespace.VCARD4);
            if (uriS != null) {
                return Uri.parse(uriS).normalizeScheme();
            }
            if ("email".equals(item.getName())) {
                return Uri.parse("mailto:" + item.findChildContent("text", Namespace.VCARD4));
            }
            return null;
        }
    }
}
