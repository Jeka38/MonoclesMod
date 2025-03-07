package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.DialogJoinConferenceBinding;
import eu.siacs.conversations.ui.adapter.KnownHostsAdapter;
import eu.siacs.conversations.ui.interfaces.OnBackendConnected;
import eu.siacs.conversations.ui.util.DelayedHintHelper;

public class JoinConferenceDialog extends DialogFragment implements OnBackendConnected {

    private static final String PREFILLED_JID_KEY = "prefilled_jid";
    private static final String PREFILLED_PASSWORD_KEY = "prefilled_password";
    private static final String ACCOUNTS_LIST_KEY = "activated_accounts_list";
    private static final String MULTIPLE_ACCOUNTS = "multiple_accounts_enabled";
    private JoinConferenceDialogListener mListener;
    private KnownHostsAdapter knownHostsAdapter;

    public static JoinConferenceDialog newInstance(String prefilledJid, String password, List<String> accounts, boolean multipleAccounts) {
        JoinConferenceDialog dialog = new JoinConferenceDialog();
        Bundle bundle = new Bundle();
        bundle.putString(PREFILLED_JID_KEY, prefilledJid);
        bundle.putString(PREFILLED_PASSWORD_KEY, password);
        bundle.putBoolean(MULTIPLE_ACCOUNTS, multipleAccounts);
        bundle.putStringArrayList(ACCOUNTS_LIST_KEY, (ArrayList<String>) accounts);

        dialog.setArguments(bundle);
        return dialog;
    }

    public static JoinConferenceDialog newInstance(String prefilledJid, List<String> accounts, boolean multipleAccounts) {
        JoinConferenceDialog dialog = new JoinConferenceDialog();
        Bundle bundle = new Bundle();
        bundle.putString(PREFILLED_JID_KEY, prefilledJid);
        bundle.putBoolean(MULTIPLE_ACCOUNTS, multipleAccounts);
        bundle.putStringArrayList(ACCOUNTS_LIST_KEY, (ArrayList<String>) accounts);

        dialog.setArguments(bundle);
        return dialog;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setRetainInstance(true);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.join_public_channel);
        DialogJoinConferenceBinding binding = DataBindingUtil.inflate(getActivity().getLayoutInflater(), R.layout.dialog_join_conference, null, false);
        DelayedHintHelper.setHint(R.string.channel_full_jid_example, binding.jid);
        this.knownHostsAdapter = new KnownHostsAdapter(getActivity(), R.layout.simple_list_item);
        binding.jid.setAdapter(knownHostsAdapter);
        String prefilledJid = getArguments().getString(PREFILLED_JID_KEY);
        if (prefilledJid != null) {
            binding.jid.append(prefilledJid);
        }
        if (getArguments().getBoolean(MULTIPLE_ACCOUNTS)) {
            binding.yourAccount.setVisibility(View.VISIBLE);
            binding.account.setVisibility(View.VISIBLE);
        } else {
            binding.yourAccount.setVisibility(View.GONE);
            binding.account.setVisibility(View.GONE);
        }
        StartConversationActivity.populateAccountSpinner(getActivity(), getArguments().getStringArrayList(ACCOUNTS_LIST_KEY), binding.account);
        builder.setView(binding.getRoot());
        builder.setPositiveButton(R.string.join, null);
        builder.setNegativeButton(R.string.cancel, null);
        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> mListener.onJoinDialogPositiveClick(dialog, binding.account, binding.accountJidLayout, binding.jid, binding.jid.getText().toString().equals(getArguments().getString(PREFILLED_JID_KEY)) ? getArguments().getString(PREFILLED_PASSWORD_KEY) : null, binding.bookmark.isChecked()));
        binding.jid.setOnEditorActionListener((v, actionId, event) -> {
            mListener.onJoinDialogPositiveClick(dialog, binding.account, binding.accountJidLayout, binding.jid, binding.jid.getText().toString().equals(getArguments().getString(PREFILLED_JID_KEY)) ? getArguments().getString(PREFILLED_PASSWORD_KEY) : null, binding.bookmark.isChecked());
            return true;
        });
        return dialog;
    }

    @Override
    public void onBackendConnected() {
        refreshKnownHosts();
    }

    private void refreshKnownHosts() {
        Activity activity = getActivity();
        if (activity instanceof XmppActivity) {
            Collection<String> hosts = ((XmppActivity) activity).xmppConnectionService.getKnownConferenceHosts();
            this.knownHostsAdapter.refresh(hosts);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (JoinConferenceDialogListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement JoinConferenceDialogListener");
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

    @Override
    public void onStart() {
        super.onStart();
        final Activity activity = getActivity();
        if (activity instanceof XmppActivity && ((XmppActivity) activity).xmppConnectionService != null) {
            refreshKnownHosts();
        }
    }

    public interface JoinConferenceDialogListener {
        void onJoinDialogPositiveClick(Dialog dialog, Spinner spinner, TextInputLayout jidLayout, AutoCompleteTextView jid, String password, boolean isBookmarkChecked);
    }
}