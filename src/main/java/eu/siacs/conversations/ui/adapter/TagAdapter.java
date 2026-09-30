package eu.siacs.conversations.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ItemTagBinding;

public class TagAdapter extends RecyclerView.Adapter<TagAdapter.ViewHolder> {

    private final List<String> tags = new ArrayList<>();
    private final List<Integer> counts = new ArrayList<>();
    private OnTagSelected listener;

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
        final ItemTagBinding binding = DataBindingUtil.inflate(
                LayoutInflater.from(parent.getContext()), R.layout.item_tag, parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
        final String tag = tags.get(position);
        holder.binding.tagName.setText(tag);
        holder.binding.tagCount.setText(holder.itemView.getContext()
                .getString(R.string.tag_notes_count, counts.get(position)));
        holder.binding.getRoot().setOnClickListener(v -> {
            if (listener != null) {
                listener.onTagSelected(tag);
            }
        });
    }

    @Override
    public int getItemCount() {
        return tags.size();
    }

    public void submit(final List<String> newTags, final List<Integer> newCounts) {
        tags.clear();
        tags.addAll(newTags);
        counts.clear();
        counts.addAll(newCounts);
        notifyDataSetChanged();
    }

    public void setOnTagSelectedListener(final OnTagSelected listener) {
        this.listener = listener;
    }

    public interface OnTagSelected {
        void onTagSelected(String tag);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public final ItemTagBinding binding;

        private ViewHolder(final ItemTagBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
