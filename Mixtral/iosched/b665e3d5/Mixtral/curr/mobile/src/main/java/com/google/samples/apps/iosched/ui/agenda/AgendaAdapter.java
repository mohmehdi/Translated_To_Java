

package com.google.samples.apps.iosched.ui.agenda;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.google.samples.apps.iosched.BR;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.model.Block;
import java.time.ZoneId;
import java.util.Objects;

public class AgendaAdapter extends ListAdapter<Block, AgendaViewHolder> {
    private ZoneId timeZoneId = ZoneId.systemDefault();

    public AgendaAdapter() {
        super(new BlockDiff());
    }

    public AgendaAdapter(ZoneId timeZoneId) {
        super(new BlockDiff());
        this.timeZoneId = timeZoneId;
    }

    @NonNull
    @Override
    public AgendaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewDataBinding binding = DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), viewType, parent, false);
        return new AgendaViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull AgendaViewHolder holder, int position) {
        holder.bind(getItem(position), timeZoneId);
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).isDark() ? R.layout.item_agenda_dark : R.layout.item_agenda_light;
    }
}

class AgendaViewHolder {
    private final ViewDataBinding binding;

    public AgendaViewHolder(ViewDataBinding binding) {
        this.binding = binding;
    }

    public void bind(Block block, ZoneId timeZoneId) {
        binding.setVariable(BR.agenda, block);
        binding.setVariable(BR.timeZoneId, timeZoneId);
        binding.executePendingBindings();
    }
}

class BlockDiff extends DiffUtil.ItemCallback<Block> {
    @Override
    public boolean areItemsTheSame(@NonNull Block oldItem, @NonNull Block newItem) {
        return oldItem.getTitle().equals(newItem.getTitle()) &&
                Objects.equals(oldItem.getStartTime(), newItem.getStartTime()) &&
                Objects.equals(oldItem.getEndTime(), newItem.getEndTime());
    }

    @Override
    public boolean areContentsTheSame(@NonNull Block oldItem, @NonNull Block newItem) {
        return Objects.equals(oldItem, newItem);
    }
}