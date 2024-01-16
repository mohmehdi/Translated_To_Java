package com.chad.library.adapter.base;

import android.view.View;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class QuickAdapterHelper {


private BaseQuickAdapter contentAdapter;
private LeadingLoadStateAdapter leadingLoadStateAdapter;
private TrailingLoadStateAdapter trailingLoadStateAdapter;
private ConcatAdapter.Config config;
private List<RecyclerView.Adapter<RecyclerView.ViewHolder>> mHeaderList = new ArrayList<>();
private List<RecyclerView.Adapter<RecyclerView.ViewHolder>> mFooterList = new ArrayList<>();
private ConcatAdapter mAdapter;

private QuickAdapterHelper(
        BaseQuickAdapter contentAdapter,
        LeadingLoadStateAdapter leadingLoadStateAdapter,
        TrailingLoadStateAdapter trailingLoadStateAdapter,
        ConcatAdapter.Config config) {
    this.contentAdapter = contentAdapter;
    this.leadingLoadStateAdapter = leadingLoadStateAdapter;
    this.trailingLoadStateAdapter = trailingLoadStateAdapter;
    this.config = config;

    mAdapter = new ConcatAdapter(config);
    if (leadingLoadStateAdapter != null) {
        mAdapter.addAdapter(leadingLoadStateAdapter);
        contentAdapter.addOnViewAttachStateChangeListener(new BaseQuickAdapter.OnViewAttachStateChangeListener() {
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
        contentAdapter.addOnViewAttachStateChangeListener(new BaseQuickAdapter.OnViewAttachStateChangeListener() {
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

public RecyclerView.Adapter<RecyclerView.ViewHolder> getAdapter() {
    return mAdapter;
}

public LoadState getLeadingLoadState() {
    return leadingLoadStateAdapter != null ? leadingLoadStateAdapter.getLoadState() : LoadState.NotLoading(false);
}

public void setLeadingLoadState(LoadState loadState) {
    if (leadingLoadStateAdapter != null) {
        leadingLoadStateAdapter.setLoadState(loadState);
    }
}

public LoadState getTrailingLoadState() {
    return trailingLoadStateAdapter != null ? trailingLoadStateAdapter.getLoadState() : LoadState.NotLoading(false);
}

public void setTrailingLoadState(LoadState loadState) {
    if (trailingLoadStateAdapter != null) {
        trailingLoadStateAdapter.setLoadState(loadState);
    }
}

public void addHeader(RecyclerView.Adapter<RecyclerView.ViewHolder> headerAdapter) {
    addHeader(mHeaderList.size(), headerAdapter);
}

public void addHeader(int index, RecyclerView.Adapter<RecyclerView.ViewHolder> headerAdapter) {
    if (index < 0 || index > mHeaderList.size()) throw new IndexOutOfBoundsException("Index must be between 0 and " + mHeaderList.size() + ". Given:" + index);

    int startIndex = leadingLoadStateAdapter == null ? 0 : 1;

    mAdapter.addAdapter(startIndex + index, headerAdapter);
    mHeaderList.add(headerAdapter);
}

public void clearHeader() {
    for (RecyclerView.Adapter<RecyclerView.ViewHolder> a : mHeaderList) {
        mAdapter.removeAdapter(a);
    }
    mHeaderList.clear();
}

public void addFooter(RecyclerView.Adapter<RecyclerView.ViewHolder> footerAdapter) {
    if (trailingLoadStateAdapter == null) {
        mAdapter.addAdapter(footerAdapter);
    } else {
        mAdapter.addAdapter(mAdapter.adapters.size() - 1, footerAdapter);
    }
    mFooterList.add(footerAdapter);
}

public void addFooter(int index, RecyclerView.Adapter<RecyclerView.ViewHolder> footerAdapter) {
    if (index < 0 || index > mFooterList.size()) throw new IndexOutOfBoundsException("Index must be between 0 and " + mFooterList.size() + ". Given:" + index);

    int realIndex = trailingLoadStateAdapter == null ? mAdapter.adapters.size() - mFooterList.size() + index : mAdapter.adapters.size() - 1 - mFooterList.size() + index;

    mAdapter.addAdapter(realIndex, footerAdapter);
    mFooterList.add(footerAdapter);
}

public void clearfooter() {
    for (RecyclerView.Adapter<RecyclerView.ViewHolder> a : mFooterList) {
        mAdapter.removeAdapter(a);
    }
    mFooterList.clear();
}

public List<RecyclerView.Adapter<RecyclerView.ViewHolder>> getHeaderList() {
    return mHeaderList;
}

public List<RecyclerView.Adapter<RecyclerView.ViewHolder>> getFooterList() {
    return mFooterList;
}

public void removeAdapter(RecyclerView.Adapter<RecyclerView.ViewHolder> a) {
    if (a == contentAdapter) {
        return;
    }

    mAdapter.removeAdapter(a);
    mHeaderList.remove(a);
    mFooterList.remove(a);
}

public static class Builder {

    private BaseQuickAdapter contentAdapter;
    private LeadingLoadStateAdapter leadingLoadStateAdapter;
    private TrailingLoadStateAdapter trailingLoadStateAdapter;
    private ConcatAdapter.Config config = ConcatAdapter.Config.DEFAULT;

    public Builder(BaseQuickAdapter contentAdapter) {
        this.contentAdapter = contentAdapter;
    }

    public Builder setTrailingLoadStateAdapter(TrailingLoadStateAdapter<RecyclerView.ViewHolder> loadStateAdapter) {
        this.trailingLoadStateAdapter = loadStateAdapter;
        return this;
    }

    public Builder setTrailingLoadStateAdapter(TrailingLoadStateAdapter.OnTrailingListener loadMoreListener) {
        this.trailingLoadStateAdapter = new DefaultTrailingLoadStateAdapter().setOnLoadMoreListener(loadMoreListener);
        return this;
    }

    public Builder setLeadingLoadStateAdapter(LeadingLoadStateAdapter<RecyclerView.ViewHolder> loadStateAdapter) {
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
        return new QuickAdapterHelper(
                contentAdapter,
                leadingLoadStateAdapter,
                trailingLoadStateAdapter,
                config
        );
    }
}
}