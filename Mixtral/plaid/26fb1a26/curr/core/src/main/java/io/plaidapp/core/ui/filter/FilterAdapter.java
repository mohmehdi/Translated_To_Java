




package io.plaidapp.core.ui.filter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import io.plaidapp.core.R;
import io.plaidapp.core.ui.filter.FilterHolderInfo;
import io.plaidapp.core.ui.filter.FilterHolderInfo.FilterHolderInfoCompanion;
import io.plaidapp.core.ui.recyclerview.FilterSwipeDismissListener;

public class FilterAdapter extends ListAdapter<SourceUiModel, FilterViewHolder> implements FilterSwipeDismissListener {

    private static final DiffUtil.ItemCallback<SourceUiModel> SOURCE_UI_MODEL_DIFF = new DiffUtil.ItemCallback<SourceUiModel>() {
        @Override
        public boolean areItemsTheSame(@NonNull SourceUiModel oldItem, @NonNull SourceUiModel newItem) {
            return oldItem.getKey().equals(newItem.getKey());
        }

        @Override
        public boolean areContentsTheSame(@NonNull SourceUiModel oldItem, @NonNull SourceUiModel newItem) {
            return oldItem.equals(newItem);
        }

        @Nullable
        @Override
        public Object getChangePayload(@NonNull SourceUiModel oldItem, @NonNull SourceUiModel newItem) {
            if (!oldItem.isActive() && newItem.isActive()) {
                return FilterHolderInfoCompanion.FILTER_ENABLED;
            }
            if (oldItem.isActive() && !newItem.isActive()) {
                return FilterHolderInfoCompanion.FILTER_DISABLED;
            }
            return null;
        }
    };

    public FilterAdapter() {
        super(SOURCE_UI_MODEL_DIFF);
        setHasStableIds(true);
    }

    public void highlightPositions(List<Integer> positions) {
        for (int position : positions) {
            notifyItemChanged(position, FilterHolderInfo.HIGHLIGHT);
        }
    }

    @NonNull
    @Override
    public FilterViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        FilterViewHolder holder = new FilterViewHolder(
                LayoutInflater.from(viewGroup.getContext())
                        .inflate(R.layout.filter_item, viewGroup, false)
        );
        holder.itemView.setOnClickListener(v -> {
            int position = holder.getAdapterPosition();
            SourceUiModel uiModel = getItem(position);
            uiModel.onSourceClicked(uiModel);
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull FilterViewHolder holder, int position) {
        SourceUiModel filter = getItem(position);
        holder.bind(filter);
    }

    @Override
    public void onBindViewHolder(@NonNull FilterViewHolder holder, int position, @NonNull List<Object> partialChangePayloads) {
        if (!partialChangePayloads.isEmpty()) {
            boolean filterEnabled = partialChangePayloads.contains(FilterHolderInfoCompanion.FILTER_ENABLED);
            boolean filterDisabled = partialChangePayloads.contains(FilterHolderInfoCompanion.FILTER_DISABLED);
            if (filterEnabled || filterDisabled) {
                holder.enableFilter(filterEnabled);
            }
        } else {
            onBindViewHolder(holder, position);
        }
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId().hashCode() & 0x7fffffffL; 
    }

    @Override
    public void onItemDismiss(int position) {
        SourceUiModel uiModel = getItem(position);
        uiModel.onSourceDismissed(uiModel);
    }
}