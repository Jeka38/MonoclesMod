package de.monocles.mod;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.List;

import eu.siacs.conversations.R;

public class StickerPackAdapter extends RecyclerView.Adapter<StickerPackAdapter.ViewHolder> {

    public interface OnPackSelectedListener {
        void onPackSelected(String packName);
    }

    private final List<String> packNames;
    private final OnPackSelectedListener listener;
    private final eu.siacs.conversations.services.XmppConnectionService service;

    public StickerPackAdapter(List<String> packNames, eu.siacs.conversations.services.XmppConnectionService service, OnPackSelectedListener listener) {
        this.packNames = packNames;
        this.service = service;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.sticker_pack_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String packName = packNames.get(position);
        List<File> stickers = service.getStickersForPack(packName);
        if (stickers != null && !stickers.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(stickers.get(0).getAbsolutePath())
                    .into(holder.imageView);
        }
        holder.itemView.setOnClickListener(v -> listener.onPackSelected(packName));
    }

    @Override
    public int getItemCount() {
        return packNames.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.pack_icon);
        }
    }
}
