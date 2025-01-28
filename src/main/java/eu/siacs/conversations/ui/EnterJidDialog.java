package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface;
import android.text.InputType;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Map;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.entities.ServiceDiscoveryResult;
import eu.siacs.conversations.xmpp.OnGatewayResult;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import io.michaelrocks.libphonenumber.android.NumberParseException;


import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.EnterJidDialogBinding;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.KnownHostsAdapter;
import eu.siacs.conversations.ui.interfaces.OnBackendConnected;
import eu.siacs.conversations.ui.util.DelayedHintHelper;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.utils.PhoneNumberUtilWrapper;

public class EnterJidDialog extends DialogFragment implements OnBackendConnected, TextWatcher {


    private static final List<String> SUSPICIOUS_DOMAINS =
            Arrays.asList("conference", "muc", "room", "rooms");

    private OnEnterJidDialogPositiveListener mListener = null;

    private static final String TITLE_KEY = "title";
    private static final String POSITIVE_BUTTON_KEY = "positive_button";
    private static final String SECONDARY_BUTTON_KEY = "secondary_button";
    private static final String PREFILLED_JID_KEY = "prefilled_jid";
    private static final String ACCOUNT_KEY = "account";
    private static final String ALLOW_EDIT_JID_KEY = "allow_edit_jid";
    private static final String MULTIPLE_ACCOUNTS = "multiple_accounts_enabled";
    private static final String ACCOUNTS_LIST_KEY = "activated_accounts_list";
    private static final String SANITY_CHECK_JID = "sanity_check_jid";
    private static final String SHOW_BOOKMARK_CHECKBOX = "show_bookmark_checkbox";


    private KnownHostsAdapter knownHostsAdapter;
    private Collection<String> whitelistedDomains = Collections.emptyList();

    private EnterJidDialogBinding binding;
    private AlertDialog dialog;
    private SanityCheck sanityCheckJid = SanityCheck.NO;

    private boolean issuedWarning = false;
    private GatewayListAdapter gatewayListAdapter = new GatewayListAdapter();

    public static enum SanityCheck {
        NO,
        YES,
        ALLOW_MUC
    }

    public static EnterJidDialog newInstance(
            final List<String> activatedAccounts,
            final String title,
            final String positiveButton,
            final String secondaryButton,
            final String prefilledJid,
            final String account,
            boolean allowEditJid,
            boolean showBookmarkCheckbox,
            boolean multipleAccounts,
            final SanityCheck sanity_check_jid) {
        EnterJidDialog dialog = new EnterJidDialog();
        Bundle bundle = new Bundle();
        bundle.putString(TITLE_KEY, title);
        bundle.putString(POSITIVE_BUTTON_KEY, positiveButton);
        bundle.putString(SECONDARY_BUTTON_KEY, secondaryButton);
        bundle.putString(PREFILLED_JID_KEY, prefilledJid);
        bundle.putString(ACCOUNT_KEY, account);
        bundle.putBoolean(ALLOW_EDIT_JID_KEY, allowEditJid);
        bundle.putBoolean(MULTIPLE_ACCOUNTS, multipleAccounts);
        bundle.putStringArrayList(ACCOUNTS_LIST_KEY, (ArrayList<String>) activatedAccounts);
        bundle.putInt(SANITY_CHECK_JID, sanity_check_jid.ordinal());
        bundle.putBoolean(SHOW_BOOKMARK_CHECKBOX, showBookmarkCheckbox);
        dialog.setArguments(bundle);
        return dialog;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        final Activity activity = getActivity();
        if (activity instanceof XmppActivity && ((XmppActivity) activity).xmppConnectionService != null) {
            refreshKnownHosts();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getArguments().getString(TITLE_KEY));
        binding = DataBindingUtil.inflate(getActivity().getLayoutInflater(), R.layout.enter_jid_dialog, null, false);
        this.knownHostsAdapter = new KnownHostsAdapter(getActivity(), R.layout.simple_list_item);
        binding.jid.setAdapter(this.knownHostsAdapter);
        binding.jid.addTextChangedListener(this);
        String prefilledJid = getArguments().getString(PREFILLED_JID_KEY);
        if (prefilledJid != null) {
            binding.jid.append(prefilledJid);
            if (!getArguments().getBoolean(ALLOW_EDIT_JID_KEY)) {
                binding.jid.setFocusable(false);
                binding.jid.setFocusableInTouchMode(false);
                binding.jid.setClickable(false);
                binding.jid.setCursorVisible(false);
            }
        }
        sanityCheckJid = SanityCheck.values()[getArguments().getInt(SANITY_CHECK_JID, SanityCheck.NO.ordinal())];

        if (!getArguments().getBoolean(SHOW_BOOKMARK_CHECKBOX, false)) {
            binding.bookmark.setVisibility(View.GONE);
        }

        DelayedHintHelper.setHint(R.string.account_settings_example_jabber_id, binding.jid);

        String account = getArguments().getString(ACCOUNT_KEY);

        if (getArguments().getBoolean(MULTIPLE_ACCOUNTS)) {
            binding.yourAccount.setVisibility(View.VISIBLE);
            binding.account.setVisibility(View.VISIBLE);
        } else {
            binding.yourAccount.setVisibility(View.GONE);
            binding.account.setVisibility(View.GONE);
        }

        if (account == null) {
            StartConversationActivity.populateAccountSpinner(getActivity(), getArguments().getStringArrayList(ACCOUNTS_LIST_KEY), binding.account);
        } else {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    getActivity(), R.layout.simple_list_item,
                    new String[]{account});
            binding.account.setEnabled(false);
            adapter.setDropDownViewResource(R.layout.simple_list_item);
            binding.account.setAdapter(adapter);
        }
        binding.gatewayList.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false));
        binding.gatewayList.setAdapter(gatewayListAdapter);
        gatewayListAdapter.setOnEmpty(() -> binding.gatewayList.setVisibility(View.GONE));
        gatewayListAdapter.setOnNonEmpty(() -> binding.gatewayList.setVisibility(View.VISIBLE));

        binding.account.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView accountSpinner, View view, int position, long id) {
                XmppActivity context = (XmppActivity) getActivity();
                if (context == null || context.xmppConnectionService == null || accountJid() == null) return;

                gatewayListAdapter.clear();
                final Account account = context.xmppConnectionService.findAccountByJid(accountJid());
                if (account == null) return;

                for (final Contact contact : account.getRoster().getContacts()) {
                    if (contact.showInRoster() && contact.getPresences().size() > 0 && (contact.getPresences().anyIdentity("gateway", null) || contact.getPresences().anySupport("jabber:iq:gateway"))) {
                        context.xmppConnectionService.fetchFromGateway(account, contact.getJid(), null, (final String prompt, String errorMessage) -> {
                            if (prompt == null && !contact.getPresences().anyIdentity("gateway", null)) return;
                            context.runOnUiThread(() -> {
                                gatewayListAdapter.add(contact, prompt);
                            });
                        });
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView accountSpinner) {
                gatewayListAdapter.clear();
            }
        });

        builder.setView(binding.getRoot());
        builder.setPositiveButton(getArguments().getString(POSITIVE_BUTTON_KEY), null);
        if (getArguments().getString(SECONDARY_BUTTON_KEY) == null) {
            builder.setNegativeButton(R.string.cancel, null);
        } else {
            builder.setNegativeButton(getArguments().getString(SECONDARY_BUTTON_KEY), null);
            builder.setNeutralButton(R.string.cancel, null);
        }
        this.dialog = builder.create();

        binding.jid.setOnEditorActionListener((v, actionId, event) -> {
            handleEnter(binding, account, false);
            return true;
        });

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener((v) -> handleEnter(binding, account, false));
        if (getArguments().getString(SECONDARY_BUTTON_KEY) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener((v) -> handleEnter(binding, account, true));
        }
        return dialog;
    }

    protected Jid accountJid() {
        try {
            if (Config.DOMAIN_LOCK != null) {
                return Jid.ofEscaped((String) binding.account.getSelectedItem(), Config.DOMAIN_LOCK, null);
            } else {
                return Jid.ofEscaped((String) binding.account.getSelectedItem());
            }
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    private void handleEnter(EnterJidDialogBinding binding, String account, boolean secondary) {
        if (!binding.account.isEnabled() && account == null) {
            return;
        }
        final Jid accountJid = accountJid();
        final OnGatewayResult finish = (final String jidString, final String errorMessage) -> {
            Activity context = getActivity();
            if (context == null) return; // Race condition, we got the reply after the UI was closed

            context.runOnUiThread(() -> {
                if (errorMessage != null) {
                    binding.jidLayout.setError(errorMessage);
                    return;
                }
                if (jidString == null) {
                    binding.jidLayout.setError(getActivity().getString(R.string.invalid_jid));
                    return;
                }

                Jid contactJid = null;
                try {
                    contactJid = Jid.ofEscaped(jidString);
                } catch (final IllegalArgumentException e) {
                    binding.jidLayout.setError(getActivity().getString(R.string.invalid_jid));
                    return;
                }

                if (!issuedWarning && sanityCheckJid != SanityCheck.NO) {
                    if (contactJid.isDomainJid()) {
                        binding.jidLayout.setError(getActivity().getString(R.string.this_looks_like_a_domain));
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.add_anyway);
                        issuedWarning = true;
                        return;
                    }
                    if (sanityCheckJid != SanityCheck.ALLOW_MUC && suspiciousSubDomain(contactJid.getDomain().toEscapedString())) {
                        binding.jidLayout.setError(getActivity().getString(R.string.this_looks_like_channel));
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.add_anyway);
                        issuedWarning = true;
                        return;
                    }
                }

                if (mListener != null) {
                    try {
                        if (mListener.onEnterJidDialogPositive(accountJid, contactJid, secondary, binding.bookmark.isChecked())) {
                            dialog.dismiss();
                        }
                    } catch (JidError error) {
                        binding.jidLayout.setError(error.toString());
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.add);
                        issuedWarning = false;
                    }
                }
            });
        };

        Pair<String,Pair<Jid,Presence>> p = gatewayListAdapter.getSelected();
        final String type = gatewayListAdapter.getSelectedType();

        // Resolve based on local settings before submission
        if (type != null && (type.equals("pstn") || type.equals("sms"))) {
            try {
                binding.jid.setText(PhoneNumberUtilWrapper.normalize(getActivity(), binding.jid.getText().toString(), true));
            } catch (NumberParseException | IllegalArgumentException | NullPointerException e) { }
        }

        if (p == null) {
            finish.onGatewayResult(binding.jid.getText().toString(), null);
        } else if (p.first != null) { // Gateway already responsed to jabber:iq:gateway once
            final Account acct = ((XmppActivity) getActivity()).xmppConnectionService.findAccountByJid(accountJid);
            ((XmppActivity) getActivity()).xmppConnectionService.fetchFromGateway(acct, p.second.first, binding.jid.getText().toString(), finish);
        } else if (p.second.first.isDomainJid() && p.second.second.getServiceDiscoveryResult().getFeatures().contains("jid\\20escaping")) {
            finish.onGatewayResult(Jid.ofLocalAndDomain(binding.jid.getText().toString(), p.second.first.getDomain().toString()).toString(), null);
        } else if (p.second.first.isDomainJid()) {
            finish.onGatewayResult(Jid.ofLocalAndDomain(binding.jid.getText().toString().replace("@", "%"), p.second.first.getDomain().toString()).toString(), null);
        } else {
            finish.onGatewayResult(null, null);
        }
    }

    public void setOnEnterJidDialogPositiveListener(OnEnterJidDialogPositiveListener listener) {
        this.mListener = listener;
    }

    @Override
    public void onBackendConnected() {
        refreshKnownHosts();
    }

    private void refreshKnownHosts() {
        final Activity activity = getActivity();
        if (activity instanceof XmppActivity) {
            final XmppConnectionService service = ((XmppActivity) activity).xmppConnectionService;
            if (service == null) {
                return;
            }
            final Collection<String> hosts = service.getKnownHosts();
            this.knownHostsAdapter.refresh(hosts);
            this.whitelistedDomains = hosts;
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        if (issuedWarning) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.add);
            binding.jidLayout.setError(null);
            issuedWarning = false;
        }
    }

    public interface OnEnterJidDialogPositiveListener {
        boolean onEnterJidDialogPositive(Jid account, Jid contact, boolean secondary, boolean save) throws EnterJidDialog.JidError;
    }

    public static class JidError extends Exception {
        final String msg;

        public JidError(final String msg) {
            this.msg = msg;
        }

        @NonNull
        public String toString() {
            return msg;
        }
    }

    @Override
    public void onDestroyView() {
        Dialog dialog = getDialog();
        if (dialog != null && getRetainInstance()) {
            dialog.setDismissMessage(null);
        }
        super.onDestroyView();
    }

    private boolean suspiciousSubDomain(String domain) {
        if (this.whitelistedDomains.contains(domain)) {
            return false;
        }
        final String[] parts = domain.split("\\.");
        return parts.length >= 3 && SUSPICIOUS_DOMAINS.contains(parts[0]);
    }

    protected class GatewayListAdapter extends RecyclerView.Adapter<GatewayListAdapter.ViewHolder> {
        protected class ViewHolder extends RecyclerView.ViewHolder {
            protected ToggleButton button;
            protected int index;

            public ViewHolder(View view, int i) {
                super(view);
                this.button = (ToggleButton) view.findViewById(R.id.button);
                setIndex(i);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        button.setChecked(true); // Force visual not to flap to unchecked
                        setSelected(index);
                    }
                });
            }

            public void setIndex(int i) {
                this.index = i;
                button.setChecked(selected == i);
            }

            public void useButton(int res) {
                button.setText(res);
                button.setTextOff(button.getText());
                button.setTextOn(button.getText());
                button.setChecked(selected == this.index);
                binding.gatewayList.setVisibility(View.VISIBLE);
                button.setVisibility(View.VISIBLE);
            }

            public void useButton(String txt) {
                button.setTextOff(txt);
                button.setTextOn(txt);
                button.setChecked(selected == this.index);
                binding.gatewayList.setVisibility(View.VISIBLE);
                button.setVisibility(View.VISIBLE);
            }
        }

        protected List<Pair<Contact,String>> gateways = new ArrayList();
        protected int selected = 0;
        protected Runnable onEmpty = () -> {};
        protected Runnable onNonEmpty = () -> {};
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.enter_jid_dialog_gateway_list_item, null);
            return new ViewHolder(view, i);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, int i) {
            viewHolder.setIndex(i);

            if(i == 0) {
                viewHolder.useButton(R.string.account_settings_jabber_id);
            } else {
                viewHolder.useButton(getLabel(i));
            }
        }

        @Override
        public int getItemCount() {
            return this.gateways.size() + 1;
        }

        public void setSelected(int i) {
            int old = this.selected;
            this.selected = i;

            if(i == 0) {
                binding.jid.setThreshold(1);
                binding.jid.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
                binding.jidLayout.setHint(R.string.account_settings_jabber_id);
                if(binding.jid.hasFocus()) {
                    binding.jid.setHint(R.string.account_settings_example_jabber_id);
                } else {
                    DelayedHintHelper.setHint(R.string.account_settings_example_jabber_id, binding.jid);
                }
            } else {
                binding.jid.setThreshold(999999); // do not autocomplete
                binding.jid.setHint(null);
                binding.jid.setOnFocusChangeListener((v, hasFocus) -> {});
                binding.jidLayout.setHint(this.gateways.get(i-1).second);

                String type = getType(i);
                if (type == null) type = "";
                if (type.equals("pstn") || type.equals("sms")) {
                    binding.jid.setInputType(InputType.TYPE_CLASS_PHONE);
                } else if (type.equals("email") || type.equals("sip")) {
                    binding.jid.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

                    if(binding.jid.hasFocus()) {
                        binding.jid.setHint(R.string.account_settings_example_jabber_id);
                    } else {
                        DelayedHintHelper.setHint(R.string.account_settings_example_jabber_id, binding.jid);
                    }
                } else {
                    binding.jid.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                }
            }

            notifyItemChanged(old);
            notifyItemChanged(i);
        }

        public String getLabel(Contact gateway) {
            String type = getType(gateway);
            if ("pstn".equals(type)) return "📞";
            if (type != null) return type;

            return gateway.getDisplayName();
        }

        public String getLabel(int i) {
            if (i == 0) return null;

            return getLabel(this.gateways.get(i-1).first);
        }

        public String getType(int i) {
            if (i == 0) return null;
            return getType(this.gateways.get(i-1).first);
        }

        public String getType(Contact gateway) {
            List<String> types = getTypes(gateway);
            return types.isEmpty() ? null : types.get(0);
        }

        public List<String> getTypes(Contact gateway) {
            List<String> types = new ArrayList<>();

            for(Presence p : gateway.getPresences().getPresences()) {
                if(p.getServiceDiscoveryResult() != null) {
                    for (ServiceDiscoveryResult.Identity id : p.getServiceDiscoveryResult().getIdentities()) {
                        if ("gateway".equals(id.getCategory())) types.add(id.getType());
                    }
                }
            }

            return types;
        }

        public String getSelectedType() {
            return getType(selected);
        }

        public Pair<String, Pair<Jid,Presence>> getSelected() {
            if(this.selected == 0) {
                return null; // No gateway, just use direct JID entry
            }

            Pair<Contact,String> gateway = this.gateways.get(this.selected - 1);

            Pair<Jid,Presence> presence = null;
            for (Map.Entry<String,Presence> e : gateway.first.getPresences().getPresencesMap().entrySet()) {
                Presence p = e.getValue();
                if (p.getServiceDiscoveryResult() != null) {
                    if (p.getServiceDiscoveryResult().getFeatures().contains("jabber:iq:gateway")) {
                        if (e.getKey().equals("")) {
                            presence = new Pair<>(gateway.first.getJid(), p);
                        } else {
                            presence = new Pair<>(gateway.first.getJid().withResource(e.getKey()), p);
                        }
                        break;
                    }
                    if (p.getServiceDiscoveryResult().hasIdentity("gateway", null)) {
                        if (e.getKey().equals("")) {
                            presence = new Pair<>(gateway.first.getJid(), p);
                        } else {
                            presence = new Pair<>(gateway.first.getJid().withResource(e.getKey()), p);
                        }
                    }
                }
            }

            return presence == null ? null : new Pair(gateway.second, presence);
        }

        public void setOnEmpty(Runnable r) {
            onEmpty = r;
        }

        public void setOnNonEmpty(Runnable r) {
            onNonEmpty = r;
        }

        public void clear() {
            gateways.clear();
            onEmpty.run();
            notifyDataSetChanged();
            setSelected(0);
        }

        public void add(Contact gateway, String prompt) {
            if (getItemCount() < 2) onNonEmpty.run();
            this.gateways.add(new Pair<>(gateway, prompt));
            Collections.sort(this.gateways, (x, y) -> getLabel(x.first).compareTo(getLabel(y.first)));
            notifyDataSetChanged();
        }
    }
}
