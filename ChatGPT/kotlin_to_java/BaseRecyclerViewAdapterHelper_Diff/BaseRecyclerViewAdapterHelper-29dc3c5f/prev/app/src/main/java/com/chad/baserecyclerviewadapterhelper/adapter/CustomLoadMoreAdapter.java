
package com.chad.baserecyclerviewadapterhelper.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import com.chad.baserecyclerviewadapterhelper.databinding.ViewLoadMoreBinding;
import com.chad.library.adapter.base.loadState.LoadState;
import com.chad.library.adapter.base.loadState.trailing.TrailingLoadStateAdapter;

public class CustomLoadMoreAdapter extends TrailingLoadStateAdapter<CustomLoadMoreAdapter.CustomVH> {

    @Override
    public CustomVH onCreateViewHolder(ViewGroup parent, LoadState loadState) {
        ViewLoadMoreBinding viewBinding = ViewLoadMoreBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        CustomVH customVH = new CustomVH(viewBinding);
        viewBinding.loadMoreLoadFailView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                invokeFailRetry();
            }
        });
        viewBinding.loadMoreLoadCompleteView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                invokeLoadMore();
            }
        });
        return customVH;
    }

    @Override
    public void onBindViewHolder(CustomVH holder, LoadState loadState) {
        if (loadState instanceof LoadState.NotLoading) {
            LoadState.NotLoading notLoadingState = (LoadState.NotLoading) loadState;
            if (notLoadingState.getEndOfPaginationReached()) {
                holder.viewBinding.loadMoreLoadCompleteView.setVisibility(View.GONE);
                holder.viewBinding.loadMoreLoadingView.setVisibility(View.GONE);
                holder.viewBinding.loadMoreLoadFailView.setVisibility(View.GONE);
                holder.viewBinding.loadMoreLoadEndView.setVisibility(View.VISIBLE);
            } else {
                holder.viewBinding.loadMoreLoadCompleteView.setVisibility(View.VISIBLE);
                holder.viewBinding.loadMoreLoadingView.setVisibility(View.GONE);
                holder.viewBinding.loadMoreLoadFailView.setVisibility(View.GONE);
                holder.viewBinding.loadMoreLoadEndView.setVisibility(View.GONE);
            }
        } else if (loadState instanceof LoadState.Loading) {
            holder.viewBinding.loadMoreLoadCompleteView.setVisibility(View.GONE);
            holder.viewBinding.loadMoreLoadingView.setVisibility(View.VISIBLE);
            holder.viewBinding.loadMoreLoadFailView.setVisibility(View.GONE);
            holder.viewBinding.loadMoreLoadEndView.setVisibility(View.GONE);
        } else if (loadState instanceof LoadState.Error) {
            holder.viewBinding.loadMoreLoadCompleteView.setVisibility(View.GONE);
            holder.viewBinding.loadMoreLoadingView.setVisibility(View.GONE);
            holder.viewBinding.loadMoreLoadFailView.setVisibility(View.VISIBLE);
            holder.viewBinding.loadMoreLoadEndView.setVisibility(View.GONE);
        } else if (loadState instanceof LoadState.None) {
            holder.viewBinding.loadMoreLoadCompleteView.setVisibility(View.GONE);
            holder.viewBinding.loadMoreLoadingView.setVisibility(View.GONE);
            holder.viewBinding.loadMoreLoadFailView.setVisibility(View.GONE);
            holder.viewBinding.loadMoreLoadEndView.setVisibility(View.GONE);
        }
    }

    static class CustomVH extends RecyclerView.ViewHolder {
        ViewLoadMoreBinding viewBinding;

        CustomVH(ViewLoadMoreBinding viewBinding) {
            super(viewBinding.getRoot());
            this.viewBinding = viewBinding;
        }
    }
}
