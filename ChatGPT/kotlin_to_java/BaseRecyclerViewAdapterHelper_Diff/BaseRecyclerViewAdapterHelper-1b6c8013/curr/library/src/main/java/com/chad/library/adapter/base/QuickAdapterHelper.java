package com.chad.library.adapter.base;

import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.chad.library.adapter.base.loadState.LoadState;
import com.chad.library.adapter.base.loadState.leading.DefaultLeadingLoadStateAdapter;
import com.chad.library.adapter.base.loadState.leading.LeadingLoadStateAdapter;
import com.chad.library.adapter.base.loadState.trailing.DefaultTrailingLoadStateAdapter;
import com.chad.library.adapter.base.loadState.trailing.TrailingLoadStateAdapter;

import java.util.ArrayList;
import java.util.List;

public class QuickAdapterHelper {

    private BaseQuickAdapter<?, ?> contentAdapter;
    private LeadingLoadStateAdapter<?> leadingLoadStateAdapter;
    private TrailingLoadStateAdapter<?> trailingLoadStateAdapter;
    private ConcatAdapter mAdapter;
    private List<RecyclerView.Adapter<?>> mHeaderList;
    private List<RecyclerView.Adapter<?>> mFooterList;

    public QuickAdapterHelper(BaseQuickAdapter<?, ?> contentAdapter, LeadingLoadStateAdapter<?> leadingLoadStateAdapter, TrailingLoadStateAdapter<?> trailingLoadStateAdapter, ConcatAdapter.Config config) {
        this.contentAdapter = contentAdapter;
        this.leadingLoadStateAdapter = leadingLoadStateAdapter;
        this.trailingLoadStateAdapter = trailingLoadStateAdapter;
        this.mAdapter = new ConcatAdapter(config);
        this.mHeaderList = new ArrayList<>();
        this.mFooterList = new ArrayList<>();

        if (leadingLoadStateAdapter != null) {
            mAdapter.addAdapter(leadingLoadStateAdapter);

            contentAdapter.addOnViewAttachStateChangeListener(new BrvahAdapter.OnViewAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
                    leadingLoadStateAdapter.checkPreload(holder.getBindingAdapterPosition());
                }

                @Override
                public void onViewDetachedFromWindow(RecyclerView.ViewHolder holder) {

                }
            });
        }

        mAdapter.addAdapter(contentAdapter);

        if (trailingLoadStateAdapter != null) {
            mAdapter.addAdapter(trailingLoadStateAdapter);

            contentAdapter.addOnViewAttachStateChangeListener(new BrvahAdapter.OnViewAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
                    trailingLoadStateAdapter.checkPreload(contentAdapter.getItems().size(), holder.getBindingAdapterPosition());
                }

                @Override
                public void onViewDetachedFromWindow(RecyclerView.ViewHolder holder) {

                }
            });
        }
    }

    public QuickAdapterHelper addHeader(RecyclerView.Adapter<?> headerAdapter) {
        return addHeader(mHeaderList.size(), headerAdapter);
    }

    public QuickAdapterHelper addHeader(int index, RecyclerView.Adapter<?> headerAdapter) {
        if (index < 0 || index > mHeaderList.size()) {
            throw new IndexOutOfBoundsException("Index must be between 0 and " + mHeaderList.size() + ". Given:" + index);
        }

        int startIndex = leadingLoadStateAdapter == null ? 0 : 1;

        mAdapter.addAdapter(startIndex + index, headerAdapter);
        mHeaderList.add(headerAdapter);
        return this;
    }

    public QuickAdapterHelper clearHeader() {
        for (RecyclerView.Adapter<?> header : mHeaderList) {
            mAdapter.removeAdapter(header);
        }
        mHeaderList.clear();
        return this;
    }

    public QuickAdapterHelper addFooter(RecyclerView.Adapter<?> footerAdapter) {
        if (trailingLoadStateAdapter == null) {
            mAdapter.addAdapter(footerAdapter);
        } else {
            mAdapter.addAdapter(mAdapter.getAdapters().size() - 1, footerAdapter);
        }
        mFooterList.add(footerAdapter);
        return this;
    }

    public QuickAdapterHelper addFooter(int index, RecyclerView.Adapter<?> footerAdapter) {
        if (index < 0 || index > mFooterList.size()) {
            throw new IndexOutOfBoundsException("Index must be between 0 and " + mFooterList.size() + ". Given:" + index);
        }

        int realIndex = trailingLoadStateAdapter == null ? mAdapter.getAdapters().size() - mFooterList.size() + index : mAdapter.getAdapters().size() - 1 - mFooterList.size() + index;

        mAdapter.addAdapter(realIndex, footerAdapter);
        mFooterList.add(footerAdapter);
        return this;
    }

    public QuickAdapterHelper clearFooter() {
        for (RecyclerView.Adapter<?> footer : mFooterList) {
            mAdapter.removeAdapter(footer);
        }
        mFooterList.clear();
        return this;
    }

    public List<RecyclerView.Adapter<?>> getHeaderList() {
        return mHeaderList;
    }

    public List<RecyclerView.Adapter<?>> getFooterList() {
        return mFooterList;
    }

    public void removeAdapter(RecyclerView.Adapter<?> a) {
        if (a == contentAdapter) {
            return;
        }

        mAdapter.removeAdapter(a);
        mHeaderList.remove(a);
        mFooterList.remove(a);
    }

    public void setLeadingLoadState(LoadState value) {
        if (leadingLoadStateAdapter != null) {
            leadingLoadStateAdapter.setLoadState(value);
        }
    }

    public LoadState getLeadingLoadState() {
        return leadingLoadStateAdapter != null ? leadingLoadStateAdapter.getLoadState() : new LoadState(false);
    }

    public void setTrailingLoadState(LoadState value) {
        if (trailingLoadStateAdapter != null) {
            trailingLoadStateAdapter.setLoadState(value);
        }
    }

    public LoadState getTrailingLoadState() {
        return trailingLoadStateAdapter != null ? trailingLoadStateAdapter.getLoadState() : new LoadState(false);
    }

    public RecyclerView.Adapter<?> getAdapter() {
        return mAdapter;
    }

    public static class Builder {
        private BaseQuickAdapter<?, ?> contentAdapter;
        private LeadingLoadStateAdapter<?> leadingLoadStateAdapter;
        private TrailingLoadStateAdapter<?> trailingLoadStateAdapter;
        private ConcatAdapter.Config config;

        public Builder(BaseQuickAdapter<?, ?> contentAdapter) {
            this.contentAdapter = contentAdapter;
            this.config = ConcatAdapter.Config.DEFAULT;
        }

        public Builder setTrailingLoadStateAdapter(TrailingLoadStateAdapter<?> loadStateAdapter) {
            this.trailingLoadStateAdapter = loadStateAdapter;
            return this;
        }

        public Builder setTrailingLoadStateAdapter(TrailingLoadStateAdapter.OnTrailingListener loadMoreListener) {
            return setTrailingLoadStateAdapter(new DefaultTrailingLoadStateAdapter().setOnLoadMoreListener(loadMoreListener));
        }

        public Builder setLeadingLoadStateAdapter(LeadingLoadStateAdapter<?> loadStateAdapter) {
            this.leadingLoadStateAdapter = loadStateAdapter;
            return this;
        }

        public Builder setLeadingLoadStateAdapter(LeadingLoadStateAdapter.OnLeadingListener loadListener) {
            return setLeadingLoadStateAdapter(new DefaultLeadingLoadStateAdapter().setOnLeadingListener(loadListener));
        }

        public Builder setConfig(ConcatAdapter.Config config) {
            this.config = config;
            return this;
        }

        public QuickAdapterHelper build() {
            return new QuickAdapterHelper(contentAdapter, leadingLoadStateAdapter, trailingLoadStateAdapter, config);
        }
    }
}