package de.monocles.chat;

import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.adapter.MessageAdapter;

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
		if (item.getItemId() == R.id.quote) {
            int start = text.getSelectionStart();
            int end = text.getSelectionEnd();
            if (start < 0 || end < 0) return false;
            adapter.quoteText(text.getText().subSequence(start, end).toString(), null);
			return true;
		}
		return false;
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {}
}
