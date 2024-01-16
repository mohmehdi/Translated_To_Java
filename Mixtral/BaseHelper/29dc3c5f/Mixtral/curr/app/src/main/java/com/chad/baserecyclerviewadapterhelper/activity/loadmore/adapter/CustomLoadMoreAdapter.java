package com.chad.baserecyclerviewadapterhelper.activity.loadmore.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import com.chad.baserecyclerviewadapterhelper.databinding.ViewLoadMoreBinding;
import com.chad.library.adapter.base.loadState.LoadState;
import com.chad.library.adapter.base.loadState.trailing.TrailingLoadStateAdapter;

public class CustomLoadMoreAdapter
  extends TrailingLoadStateAdapter<CustomLoadMoreAdapter.CustomVH> {

  @Override
  public CustomVH onCreateViewHolder(ViewGroup parent, LoadState loadState) {
    ViewLoadMoreBinding viewBinding = ViewLoadMoreBinding.inflate(
      LayoutInflater.from(parent.getContext()),
      parent,
      false
    );
    return new CustomVH(viewBinding) {
      {
        viewBinding.loadMoreLoadFailView.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              invokeFailRetry();
            }
          }
        );
        viewBinding.loadMoreLoadCompleteView.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              invokeLoadMore();
            }
          }
        );
      }
    };
  }

  @Override
  public void onBindViewHolder(CustomVH holder, LoadState loadState) {
    switch (loadState.getType()) {
      case LoadState.Type.NOT_LOADING:
        if (loadState.getEndOfPaginationReached()) {
          holder.viewBinding.loadMoreLoadCompleteView.setVisibility(View.GONE);
          holder.viewBinding.loadMoreLoadingView.setVisibility(View.GONE);
          holder.viewBinding.loadMoreLoadFailView.setVisibility(View.GONE);
          holder.viewBinding.loadMoreLoadEndView.setVisibility(View.VISIBLE);
        } else {
          holder.viewBinding.loadMoreLoadCompleteView.setVisibility(
            View.VISIBLE
          );
          holder.viewBinding.loadMoreLoadingView.setVisibility(View.GONE);
          holder.viewBinding.loadMoreLoadFailView.setVisibility(View.GONE);
          holder.viewBinding.loadMoreLoadEndView.setVisibility(View.GONE);
        }
        break;
      case LoadState.Type.LOADING:
        holder.viewBinding.loadMoreLoadCompleteView.setVisibility(View.GONE);
        holder.viewBinding.loadMoreLoadingView.setVisibility(View.VISIBLE);
        holder.viewBinding.loadMoreLoadFailView.setVisibility(View.GONE);
        holder.viewBinding.loadMoreLoadEndView.setVisibility(View.GONE);
        break;
      case LoadState.Type.ERROR:
        holder.viewBinding.loadMoreLoadCompleteView.setVisibility(View.GONE);
        holder.viewBinding.loadMoreLoadingView.setVisibility(View.GONE);
        holder.viewBinding.loadMoreLoadFailView.setVisibility(View.VISIBLE);
        holder.viewBinding.loadMoreLoadEndView.setVisibility(View.GONE);
        break;
      case LoadState.Type.NONE:
      default:
        holder.viewBinding.loadMoreLoadCompleteView.setVisibility(View.GONE);
        holder.viewBinding.loadMoreLoadingView.setVisibility(View.GONE);
        holder.viewBinding.loadMoreLoadFailView.setVisibility(View.GONE);
        holder.viewBinding.loadMoreLoadEndView.setVisibility(View.GONE);
    }
  }

  public static class CustomVH extends RecyclerView.ViewHolder {

    public final ViewLoadMoreBinding viewBinding;

    public CustomVH(ViewLoadMoreBinding viewBinding) {
      super(viewBinding.getRoot());
      this.viewBinding = viewBinding;
    }
  }
}
