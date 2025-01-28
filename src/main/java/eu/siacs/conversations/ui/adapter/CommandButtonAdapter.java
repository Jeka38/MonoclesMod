package eu.siacs.conversations.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;

import eu.siacs.conversations.R;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.databinding.CommandButtonBinding;

public class CommandButtonAdapter extends ArrayAdapter<Element> {
    public CommandButtonAdapter(XmppActivity activity) {
        super(activity, 0);
    }

    @Override
    public View getView(int position, View view, @NonNull ViewGroup parent) {
        CommandButtonBinding binding = DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), R.layout.command_button, parent, false);
        binding.command.setText(getItem(position).getAttribute("name"));
        binding.command.setFocusable(false);
        binding.command.setClickable(false);
        return binding.getRoot();
    }
}
