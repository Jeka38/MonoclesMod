package eu.siacs.conversations.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ItemNoteBinding;
import eu.siacs.conversations.databinding.ItemNoteTagBinding;
import eu.siacs.conversations.entities.Note;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.ViewHolder> {

    private final List<Note> notes = new ArrayList<>();
    private final Set<String> selection = new HashSet<>();
    private boolean selectionMode = false;
    private OnNoteClicked listener;
    private OnTagClickListener tagListener;
    private OnSelectionChangedListener selectionListener;

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
        final ItemNoteBinding binding = DataBindingUtil.inflate(
                LayoutInflater.from(parent.getContext()), R.layout.item_note, parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
        final Note note = notes.get(position);
        final String title = note.getTitle();
        if (TextUtils.isEmpty(title)) {
            holder.binding.noteTitle.setVisibility(View.GONE);
        } else {
            holder.binding.noteTitle.setText(title);
            holder.binding.noteTitle.setVisibility(View.VISIBLE);
        }
        bindTags(holder, note);
        final String body = note.getBody();
        if (TextUtils.isEmpty(body)) {
            holder.binding.noteSnippet.setVisibility(View.GONE);
        } else {
            holder.binding.noteSnippet.setText(body);
            holder.binding.noteSnippet.setVisibility(View.VISIBLE);
        }

        final boolean checked = selection.contains(note.getKey());
        holder.binding.noteCheck.setVisibility(checked ? View.VISIBLE : View.GONE);
        holder.binding.noteCard.setChecked(checked);
        if (selectionMode && checked) {
            holder.binding.noteCard.setCardBackgroundColor(
                    resolveColor(holder, android.R.attr.colorControlHighlight));
        } else {
            holder.binding.noteCard.setCardBackgroundColor(
                    resolveColor(holder, R.attr.color_background_secondary));
        }

        holder.binding.noteCard.setOnClickListener(v -> {
            if (selectionMode) {
                toggleSelection(note.getKey());
            } else if (listener != null) {
                listener.onNoteClicked(note);
            }
        });
        holder.binding.noteCard.setOnLongClickListener(v -> {
            if (selectionMode) {
                toggleSelection(note.getKey());
            } else {
                enterSelection(note.getKey());
            }
            return true;
        });
    }

    private static int resolveColor(final ViewHolder holder, final int attr) {
        final android.util.TypedValue value = new android.util.TypedValue();
        if (holder.itemView.getContext().getTheme().resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) {
                return androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), value.resourceId);
            }
            return value.data;
        }
        return 0;
    }

    private void bindTags(final ViewHolder holder, final Note note) {
        final com.google.android.material.chip.ChipGroup group = holder.binding.noteTags;
        group.removeAllViews();
        final List<String> tags = note.getTags();
        if (tags.isEmpty()) {
            group.setVisibility(View.GONE);
            return;
        }
        group.setVisibility(View.VISIBLE);
        final LayoutInflater inflater = LayoutInflater.from(group.getContext());
        for (final String tag : tags) {
            final ItemNoteTagBinding chipBinding = ItemNoteTagBinding.inflate(inflater, group, false);
            final Chip chip = chipBinding.getRoot();
            chip.setText(chip.getContext().getString(R.string.tag_prefix, tag));
            chip.setClickable(!selectionMode);
            chip.setOnClickListener(v -> {
                if (!selectionMode && tagListener != null) {
                    tagListener.onTagClicked(tag);
                }
            });
            chip.setOnLongClickListener(v -> {
                if (selectionMode) {
                    toggleSelection(note.getKey());
                } else {
                    enterSelection(note.getKey());
                }
                return true;
            });
            group.addView(chip);
        }
    }

    private void enterSelection(final String key) {
        selectionMode = true;
        selection.clear();
        selection.add(key);
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    private void toggleSelection(final String key) {
        if (!selection.remove(key)) {
            selection.add(key);
        }
        if (selection.isEmpty()) {
            selectionMode = false;
        }
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public void clearSelection() {
        selectionMode = false;
        selection.clear();
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public int getSelectedCount() {
        return selection.size();
    }

    public Set<String> getSelectedKeys() {
        return Collections.unmodifiableSet(selection);
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    public void setNotes(final List<Note> newNotes) {
        notes.clear();
        notes.addAll(newNotes);
        notifyDataSetChanged();
    }

    public void setOnNoteClickedListener(final OnNoteClicked listener) {
        this.listener = listener;
    }

    public void setOnTagClickListener(final OnTagClickListener listener) {
        this.tagListener = listener;
    }

    public void setOnSelectionChangedListener(final OnSelectionChangedListener listener) {
        this.selectionListener = listener;
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(isSelectionMode(), selection.size());
        }
    }

    public interface OnNoteClicked {
        void onNoteClicked(Note note);
    }

    public interface OnTagClickListener {
        void onTagClicked(String tag);
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(boolean selectionMode, int count);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public final ItemNoteBinding binding;

        private ViewHolder(final ItemNoteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
