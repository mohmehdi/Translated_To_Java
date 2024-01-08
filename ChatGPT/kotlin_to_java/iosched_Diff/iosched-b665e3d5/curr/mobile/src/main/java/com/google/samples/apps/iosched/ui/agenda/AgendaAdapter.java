package com.google.samples.apps.iosched.ui.agenda;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.google.samples.apps.iosched.BR;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.model.Block;
import org.threeten.bp.ZoneId;

public class AgendaAdapter extends ListAdapter<Block, AgendaAdapter.AgendaViewHolder> {

    private ZoneId timeZoneId;

    public AgendaAdapter(ZoneId timeZoneId) {
        super(BlockDiff);
        this.timeZoneId = timeZoneId;
    }

    @Override
    public AgendaViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new AgendaViewHolder(
                DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), viewType, parent, false)
        );
    }

    @Override
    public void onBindViewHolder(AgendaViewHolder holder, int position) {
        holder.bind(getItem(position), timeZoneId);
    }

    @Override
    public int getItemViewType(int position) {
        if (getItem(position).isDark()) {
            return R.layout.item_agenda_dark;
        } else {
            return R.layout.item_agenda_light;
        }
    }

    public static class AgendaViewHolder extends RecyclerView.ViewHolder {
        private ViewDataBinding binding;

        public AgendaViewHolder(ViewDataBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Block block, ZoneId timeZoneId) {
            binding.setVariable(BR.agenda, block);
            binding.setVariable(BR.timeZoneId, timeZoneId);
            binding.executePendingBindings();
        }
    }

    public static final DiffUtil.ItemCallback<Block> BlockDiff = new DiffUtil.ItemCallback<Block>() {
        @Override
        public boolean areItemsTheSame(Block oldItem, Block newItem) {
            return oldItem.getTitle().equals(newItem.getTitle()) &&
                    oldItem.getStartTime().equals(newItem.getStartTime()) &&
                    oldItem.getEndTime().equals(newItem.getEndTime());
        }

        @Override
        public boolean areContentsTheSame(Block oldItem, Block newItem) {
            return oldItem.equals(newItem);
        }
    };
}