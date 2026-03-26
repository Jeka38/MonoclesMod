package eu.siacs.conversations.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import eu.siacs.conversations.R;

public class XmlStanzaAdapter extends RecyclerView.Adapter<XmlStanzaAdapter.ViewHolder> {

    private final List<String> stanzas;

    public XmlStanzaAdapter(List<String> stanzas) {
        this.stanzas = stanzas;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.xml_console_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.stanzaText.setText(stanzas.get(position));
    }

    @Override
    public int getItemCount() {
        return stanzas.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView stanzaText;

        public ViewHolder(View view) {
            super(view);
            stanzaText = view.findViewById(R.id.stanza_text);
        }
    }
}
