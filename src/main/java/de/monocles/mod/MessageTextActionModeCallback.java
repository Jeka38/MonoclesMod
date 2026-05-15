package de.monocles.mod;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
		menu.add(Menu.NONE, android.R.id.copy, 0, android.R.string.copy);
		return true;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		return false;
	}

	@Override
	public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
		if (item.getItemId() == android.R.id.copy) {
			final int start = text.getSelectionStart();
			final int end = text.getSelectionEnd();
			if (start < 0 || end < 0 || start == end) return false;
			final ClipboardManager clipboard = (ClipboardManager) text.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
			if (clipboard != null) {
				clipboard.setPrimaryClip(ClipData.newPlainText("message", text.getText().subSequence(start, end)));
			}
			mode.finish();
			return true;
		}
		if (item.getItemId() == R.id.quote) {
            int start = text.getSelectionStart();
            int end = text.getSelectionEnd();
            if (start < 0 || end < 0 || start == end) return false;
            adapter.quoteText(text.getText().subSequence(start, end).toString(), null);
			mode.finish();
			return true;
		}
		return false;
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {}
}
