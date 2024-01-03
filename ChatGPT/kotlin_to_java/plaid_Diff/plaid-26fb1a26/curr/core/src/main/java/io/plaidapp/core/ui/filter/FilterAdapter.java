package io.plaidapp.core.ui.filter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import io.plaidapp.core.R;
import io.plaidapp.core.ui.recyclerview.FilterSwipeDismissListener;

public class FilterAdapter extends ListAdapter<SourceUiModel, FilterViewHolder> implements FilterSwipeDismissListener {

    private static final int FILTER_DISABLED = 0;
    private static final int FILTER_ENABLED = 1;
    private static final int HIGHLIGHT = 2;

    private static final DiffUtil.ItemCallback<SourceUiModel> sourceUiModelDiff = new DiffUtil.ItemCallback<SourceUiModel>() {
        @Override
        public boolean areItemsTheSame(SourceUiModel oldItem, SourceUiModel newItem) {
            return oldItem.getKey().equals(newItem.getKey());
        }

        @Override
        public boolean areContentsTheSame(SourceUiModel oldItem, SourceUiModel newItem) {
            return oldItem.equals(newItem);
        }

        @Override
        public Object getChangePayload(SourceUiModel oldItem, SourceUiModel newItem) {
            if (!oldItem.isActive() && newItem.isActive()) {
                return FILTER_ENABLED;
            }
            if (oldItem.isActive() && !newItem.isActive()) {
                return FILTER_DISABLED;
            }
            return null;
        }
    };

    public FilterAdapter() {
        super(sourceUiModelDiff);
        setHasStableIds(true);
    }

    public void highlightPositions(List<Integer> positions) {
        for (Integer position : positions) {
            notifyItemChanged(position, HIGHLIGHT);
        }
    }

    @Override
    public FilterViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        FilterViewHolder holder = new FilterViewHolder(
                LayoutInflater.from(viewGroup.getContext())
                        .inflate(R.layout.filter_item, viewGroup, false)
        );
        holder.itemView.setOnClickListener(view -> {
            int position = holder.getAdapterPosition();
            SourceUiModel uiModel = getItem(position);
            uiModel.onSourceClicked(uiModel);
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(FilterViewHolder holder, int position) {
        SourceUiModel filter = getItem(position);
        holder.bind(filter);
    }

    @Override
    public void onBindViewHolder(FilterViewHolder holder, int position, List<Object> partialChangePayloads) {
        if (!partialChangePayloads.isEmpty()) {
            boolean filterEnabled = partialChangePayloads.contains(FILTER_ENABLED);
            boolean filterDisabled = partialChangePayloads.contains(FILTER_DISABLED);
            if (filterEnabled || filterDisabled) {
                holder.enableFilter(filterEnabled);
            }
        } else {
            onBindViewHolder(holder, position);
        }
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId().hashCode();
    }

    @Override
    public void onItemDismiss(int position) {
        SourceUiModel uiModel = getItem(position);
        uiModel.onSourceDismissed(uiModel);
    }
}