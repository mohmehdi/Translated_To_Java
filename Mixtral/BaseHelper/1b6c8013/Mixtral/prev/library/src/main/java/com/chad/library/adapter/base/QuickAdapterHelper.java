package com.chad.library.adapter.base;

import android.view.View;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class QuickAdapterHelper {


private final BaseQuickAdapter<?, ?> contentAdapter;
private final LeadingLoadStateAdapter<Object> leadingLoadStateAdapter;
private final TrailingLoadStateAdapter<Object> trailingLoadStateAdapter;
private final ConcatAdapter.Config config;

private final List<RecyclerView.Adapter<Object>> mHeaderList = new ArrayList<>();
private final List<RecyclerView.Adapter<Object>> mFooterList = new ArrayList<>();
private final ConcatAdapter mAdapter;

public QuickAdapterHelper(BaseQuickAdapter<?, ?> contentAdapter, LeadingLoadStateAdapter<Object> leadingLoadStateAdapter, TrailingLoadStateAdapter<Object> trailingLoadStateAdapter, ConcatAdapter.Config config) {
    this.contentAdapter = contentAdapter;
    this.leadingLoadStateAdapter = leadingLoadStateAdapter;
    this.trailingLoadStateAdapter = trailingLoadStateAdapter;
    this.config = config;

    mAdapter = new ConcatAdapter(config);

    if (leadingLoadStateAdapter != null) {
        mAdapter.addAdapter(leadingLoadStateAdapter);

        contentAdapter.addOnAttachStateChangeListener(new BaseQuickAdapter.OnAttachStateChangeListener() {
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

        contentAdapter.addOnAttachStateChangeListener(new BaseQuickAdapter.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
                trailingLoadStateAdapter.checkPreload(contentAdapter.getItemCount(), holder.getBindingAdapterPosition());
            }

            @Override
            public void onViewDetachedFromWindow(RecyclerView.ViewHolder holder) {

            }
        });
    }
}

public RecyclerView.Adapter<Object> getAdapter() {
    return mAdapter;
}

public LoadState getLeadingLoadState() {
    return leadingLoadStateAdapter != null ? leadingLoadStateAdapter.getLoadState() : LoadState.NotLoading(false);
}

public void setLeadingLoadState(LoadState value) {
    if (leadingLoadStateAdapter != null) {
        leadingLoadStateAdapter.setLoadState(value);
    }
}

public LoadState getTrailingLoadState() {
    return trailingLoadStateAdapter != null ? trailingLoadStateAdapter.getLoadState() : LoadState.NotLoading(false);
}

public void setTrailingLoadState(LoadState value) {
    if (trailingLoadStateAdapter != null) {
        trailingLoadStateAdapter.setLoadState(value);
    }
}

public void addHeader(RecyclerView.Adapter<Object> headerAdapter) {
    addHeader(mHeaderList.size(), headerAdapter);
}

public void addHeader(int index, RecyclerView.Adapter<Object> headerAdapter) {
    if (index < 0 || index > mHeaderList.size()) throw new IndexOutOfBoundsException("Index must be between 0 and " + mHeaderList.size() + ". Given:" + index);

    int startIndex = leadingLoadStateAdapter == null ? 0 : 1;

    mAdapter.addAdapter(startIndex + index, headerAdapter);
    mHeaderList.add(headerAdapter);
}

public void clearHeader() {
    for (RecyclerView.Adapter<Object> a : mHeaderList) {
        mAdapter.removeAdapter(a);
    }
    mHeaderList.clear();
}

public void addFooter(RecyclerView.Adapter<Object> footerAdapter) {
    if (trailingLoadStateAdapter == null) {
        mAdapter.addAdapter(footerAdapter);
    } else {
        mAdapter.addAdapter(mAdapter.adapters.size - 1, footerAdapter);
    }
    mFooterList.add(footerAdapter);
}

public void addFooter(int index, RecyclerView.Adapter<Object> footerAdapter) {
    if (index < 0 || index > mFooterList.size()) throw new IndexOutOfBoundsException("Index must be between 0 and " + mFooterList.size() + ". Given:" + index);

    int realIndex = trailingLoadStateAdapter == null ? mAdapter.adapters.size - mFooterList.size + index : mAdapter.adapters.size - 1 - mFooterList.size + index;

    mAdapter.addAdapter(realIndex, footerAdapter);
    mFooterList.add(footerAdapter);
}

public void clearFooter() {
    for (RecyclerView.Adapter<Object> a : mFooterList) {
        mAdapter.removeAdapter(a);
    }
    mFooterList.clear();
}

public List<RecyclerView.Adapter<Object>> getHeaderList() {
    return mHeaderList;
}

public List<RecyclerView.Adapter<Object>> getFooterList() {
    return mFooterList;
}

public void removeAdapter(RecyclerView.Adapter<Object> a) {
    if (a == contentAdapter) {
        return;
    }

    mAdapter.removeAdapter(a);
    mHeaderList.remove(a);
    mFooterList.remove(a);
}

public static class Builder {

    private final BaseQuickAdapter<?, ?> contentAdapter;
    private LeadingLoadStateAdapter<Object> leadingLoadStateAdapter;
    private TrailingLoadStateAdapter<Object> trailingLoadStateAdapter;
    private ConcatAdapter.Config config = ConcatAdapter.Config.DEFAULT;

    public Builder(BaseQuickAdapter<?, ?> contentAdapter) {
        this.contentAdapter = contentAdapter;
    }

    public Builder setTrailingLoadStateAdapter(TrailingLoadStateAdapter<Object> loadStateAdapter) {
        this.trailingLoadStateAdapter = loadStateAdapter;
        return this;
    }

    public Builder setTrailingLoadStateAdapter(TrailingLoadStateAdapter.OnTrailingListener loadMoreListener) {
        this.trailingLoadStateAdapter = new DefaultTrailingLoadStateAdapter().setOnLoadMoreListener(loadMoreListener);
        return this;
    }

    public Builder setLeadingLoadStateAdapter(LeadingLoadStateAdapter<Object> loadStateAdapter) {
        this.leadingLoadStateAdapter = loadStateAdapter;
        return this;
    }

    public Builder setLeadingLoadStateAdapter(LeadingLoadStateAdapter.OnLeadingListener loadListener) {
        this.leadingLoadStateAdapter = new DefaultLeadingLoadStateAdapter().setOnLeadingListener(loadListener);
        return this;
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