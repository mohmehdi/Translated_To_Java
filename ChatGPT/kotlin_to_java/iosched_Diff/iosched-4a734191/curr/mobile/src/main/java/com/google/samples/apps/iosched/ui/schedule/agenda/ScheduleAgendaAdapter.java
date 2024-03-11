package com.google.samples.apps.iosched.ui.schedule.agenda;

import android.databinding.DataBindingUtil;
import android.databinding.ViewDataBinding;
import android.support.annotation.NonNull;
import android.support.v7.recyclerview.extensions.ListAdapter;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.google.samples.apps.iosched.BR;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.shared.model.Block;

public class ScheduleAgendaAdapter extends ListAdapter<Block, AgendaViewHolder> {

    protected ScheduleAgendaAdapter() {
        super(BlockDiff);
    }

    @NonNull
    @Override
    public AgendaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new AgendaViewHolder(
                DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), viewType, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull AgendaViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    @Override
    public int getItemViewType(int position) {
        if (getItem(position).isDark()) {
            return R.layout.item_agenda_dark;
        } else {
            return R.layout.item_agenda_light;
        }
    }
}

class AgendaViewHolder extends RecyclerView.ViewHolder {
    private final ViewDataBinding binding;

    AgendaViewHolder(ViewDataBinding binding) {
        super(binding.getRoot());
        this.binding = binding;
    }

    void bind(Block block) {
        binding.setVariable(BR.agenda, block);
        binding.executePendingBindings();
    }
}

class BlockDiff extends DiffUtil.ItemCallback<Block> {
    @Override
    public boolean areItemsTheSame(@NonNull Block oldItem, @NonNull Block newItem) {
        return oldItem.getTitle().equals(newItem.getTitle())
                && oldItem.getStartTime().equals(newItem.getStartTime())
                && oldItem.getEndTime().equals(newItem.getEndTime());
    }

    @Override
    public boolean areContentsTheSame(@NonNull Block oldItem, @NonNull Block newItem) {
        return oldItem.equals(newItem);
    }
}