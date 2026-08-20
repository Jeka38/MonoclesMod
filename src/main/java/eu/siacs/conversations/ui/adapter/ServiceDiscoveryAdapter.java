package eu.siacs.conversations.ui.adapter;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ServiceDiscoveryItemBinding;
import eu.siacs.conversations.entities.ServiceDiscoveryItem;

public class ServiceDiscoveryAdapter extends RecyclerView.Adapter<ServiceDiscoveryAdapter.ViewHolder> {

    public interface OnServiceDiscoveryItemClicked {
        void onServiceDiscoveryItemClicked(ServiceDiscoveryItem item);
    }

    public interface OnServiceDiscoveryItemLongClicked {
        void onServiceDiscoveryItemLongClicked(ServiceDiscoveryItem item);
    }

    private final List<ServiceDiscoveryItem> items = new ArrayList<>();
    private final OnServiceDiscoveryItemClicked listener;
    private final OnServiceDiscoveryItemLongClicked longClickListener;

    public ServiceDiscoveryAdapter(OnServiceDiscoveryItemClicked listener, OnServiceDiscoveryItemLongClicked longClickListener) {
        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    public void submitList(List<ServiceDiscoveryItem> serviceDiscoveryItems) {
        items.clear();
        if (serviceDiscoveryItems != null) {
            items.addAll(serviceDiscoveryItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int position) {
        ServiceDiscoveryItemBinding binding = DataBindingUtil.inflate(LayoutInflater.from(viewGroup.getContext()), R.layout.service_discovery_item, viewGroup, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        final ServiceDiscoveryItem item = items.get(position);
        final String identityName = item.getIdentityName();
        if (identityName != null && !identityName.isEmpty()) {
            viewHolder.binding.name.setText(identityName);
        } else {
            viewHolder.binding.name.setText(item.getDisplayName());
        }
        viewHolder.binding.name.setTypeface(null, Typeface.BOLD);
        viewHolder.binding.jid.setText(item.getJid() == null ? "" : item.getJid().toEscapedString());
        viewHolder.binding.getRoot().setOnClickListener(v -> {
            if (listener != null) {
                listener.onServiceDiscoveryItemClicked(item);
            }
        });
        viewHolder.binding.getRoot().setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onServiceDiscoveryItemLongClicked(item);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    protected class ViewHolder extends RecyclerView.ViewHolder {

        public final ServiceDiscoveryItemBinding binding;

        private ViewHolder(ServiceDiscoveryItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}