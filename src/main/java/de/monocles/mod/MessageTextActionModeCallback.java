package de.monocles.mod;

import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.adapter.MessageAdapter;
import me.drakeet.support.toast.ToastCompat;

public class MessageTextActionModeCallback implements ActionMode.Callback {
	final MessageAdapter adapter;
	final TextView text;

	public MessageTextActionModeCallback(MessageAdapter adapter, TextView text) {
		this.adapter = adapter;
		this.text = text;
	}

	@Override
	public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
		final MenuInflater inflater = mode.getMenuInflater();
		inflater.inflate(R.menu.message_text_actions, menu);
		return true;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		return false;
	}

	@Override
	public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
        int start = text.getSelectionStart();
        int end = text.getSelectionEnd();
        if (start < 0 || end < 0) return false;
        CharSequence selection = text.getText().subSequence(start, end);
		if (item.getItemId() == R.id.quote) {
            adapter.quoteText(selection.toString(), null);
            mode.finish();
			return true;
		} else if (item.getItemId() == R.id.copy) {
            if (((XmppActivity) adapter.getActivity()).copyTextToClipboard(selection.toString(), R.string.message)) {
                ToastCompat.makeText(adapter.getActivity(), R.string.message_copied_to_clipboard, ToastCompat.LENGTH_SHORT).show();
            }
            mode.finish();
            return true;
        }
		return false;
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {}
}
