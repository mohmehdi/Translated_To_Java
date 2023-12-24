
package com.chad.baserecyclerviewadapterhelper.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import com.chad.baserecyclerviewadapterhelper.R;
import com.chad.baserecyclerviewadapterhelper.databinding.DefSectionHeadBinding;
import com.chad.baserecyclerviewadapterhelper.databinding.HomeItemViewBinding;
import com.chad.baserecyclerviewadapterhelper.entity.HomeEntity;
import com.chad.library.adapter.base.BaseMultiItemQuickAdapter;
import com.chad.library.adapter.base.listener.OnMultiItemAdapterListener;
import com.chad.library.adapter.base.module.LoadMoreModule;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HomeAdapter extends BaseMultiItemQuickAdapter<HomeEntity, BaseViewHolder> implements LoadMoreModule {

    private static final int ITEM_TYPE = 0;
    private static final int SECTION_TYPE = 1;

    public HomeAdapter(List<HomeEntity> data) {
        super(data);
        addItemType(ITEM_TYPE, new OnMultiItemAdapterListener<HomeEntity, BaseViewHolder>() {
            @NotNull
            @Override
            public BaseViewHolder onCreate(@NotNull Context context, ViewGroup parent, int viewType) {
                HomeItemViewBinding viewBinding = HomeItemViewBinding.inflate(LayoutInflater.from(context), parent, false);
                return new BaseViewHolder(viewBinding.getRoot());
            }

            @Override
            public void onBind(@NotNull BaseViewHolder holder, HomeEntity item) {
                HomeItemViewBinding viewBinding = HomeItemViewBinding.bind(holder.itemView);
                viewBinding.textView.setText(item.getName());
                viewBinding.icon.setImageResource(item.getImageResource());
            }
        });

        addItemType(SECTION_TYPE, new OnMultiItemAdapterListener<HomeEntity, BaseViewHolder>() {
            @NotNull
            @Override
            public BaseViewHolder onCreate(@NotNull Context context, ViewGroup parent, int viewType) {
                DefSectionHeadBinding viewBinding = DefSectionHeadBinding.inflate(LayoutInflater.from(context), parent, false);
                return new BaseViewHolder(viewBinding.getRoot());
            }

            @Override
            public void onBind(@NotNull BaseViewHolder holder, HomeEntity item) {
                DefSectionHeadBinding viewBinding = DefSectionHeadBinding.bind(holder.itemView);
                viewBinding.more.setVisibility(View.GONE);
                viewBinding.header.setText(item.getSectionTitle());
            }

            @Override
            public boolean isFullSpanItem(int itemType) {
                return true;
            }
        });

        setOnItemViewType((position, list) -> {
            if (list.get(position).isSection()) {
                return SECTION_TYPE;
            } else {
                return ITEM_TYPE;
            }
        });
    }

    @Override
    protected void convert(@NotNull BaseViewHolder baseViewHolder, HomeEntity homeEntity) {
        // No need to implement this method since the binding is done in the onCreate and onBind methods of each item type
    }
}
