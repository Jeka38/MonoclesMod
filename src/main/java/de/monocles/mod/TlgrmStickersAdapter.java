package de.monocles.mod;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;

public class TlgrmStickersAdapter extends BaseAdapter {

    private final Context context;
    private final List<TlgrmStickerSearch.StickerItem> items = new ArrayList<>();

    public TlgrmStickersAdapter(final Context context) {
        this.context = context;
    }

    public void setItems(final List<TlgrmStickerSearch.StickerItem> stickers) {
        items.clear();
        if (stickers != null) {
            items.addAll(stickers);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public TlgrmStickerSearch.StickerItem getItem(final int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(final int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        final View view;
        if (convertView == null) {
            final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.activity_gridview_stickers, parent, false);
        } else {
            view = convertView;
        }
        final ImageView image = view.findViewById(R.id.grid_item);
        Glide.with(context).load(getItem(position).imageUrl).into(image);
        return view;
    }
}
