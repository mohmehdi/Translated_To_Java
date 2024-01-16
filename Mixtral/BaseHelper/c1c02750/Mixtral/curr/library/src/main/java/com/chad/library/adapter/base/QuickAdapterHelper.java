package com.chad.library.adapter.base;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.chad.library.adapter.base.dragswipe.DefaultDragAndSwipe;
import com.chad.library.adapter.base.dragswipe.DragAndSwipeImpl;
import com.chad.library.adapter.base.listener.OnItemDragListener;
import com.chad.library.adapter.base.loadState.LoadState;
import com.chad.library.adapter.base.loadState.leading.DefaultLeadingLoadStateAdapter;
import com.chad.library.adapter.base.loadState.leading.LeadingLoadStateAdapter;
import com.chad.library.adapter.base.loadState.trailing.DefaultTrailingLoadStateAdapter;
import com.chad.library.adapter.base.loadState.trailing.TrailingLoadStateAdapter;

import java.util.ArrayList;

public class QuickAdapterHelper {


private final BaseQuickAdapter<?, ?> contentAdapter;
private final LeadingLoadStateAdapter<?> leadingLoadStateAdapter;
private final TrailingLoadStateAdapter<?> trailingLoadStateAdapter;
private final ConcatAdapter.Config config;
private DragAndSwipeImpl<?> dragAndSwipe;

private final ArrayList<RecyclerView.Adapter<>> mHeaderList = new ArrayList<>(0);
private final ArrayList<RecyclerView.Adapter<>> mFooterList = new ArrayList<>(0);
private final ConcatAdapter mAdapter;

public QuickAdapterHelper(
        BaseQuickAdapter<?, ?> contentAdapter,
        @Nullable LeadingLoadStateAdapter<?> leadingLoadStateAdapter,
        @Nullable TrailingLoadStateAdapter<?> trailingLoadStateAdapter,
        ConcatAdapter.Config config,
        DragAndSwipeImpl<?> dragAndSwipe) {
    this.contentAdapter = contentAdapter;
    this.leadingLoadStateAdapter = leadingLoadStateAdapter;
    this.trailingLoadStateAdapter = trailingLoadStateAdapter;
    this.config = config;
    this.dragAndSwipe = dragAndSwipe;

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
                trailingLoadStateAdapter.checkPreload(
                        contentAdapter.getItemCount(),
                        holder.getBindingAdapterPosition()
                );
            }

            @Override
            public void onViewDetachedFromWindow(RecyclerView.ViewHolder holder) {

            }
        });
    }
}

public RecyclerView.Adapter< ?> getAdapter() {
    return mAdapter;
}

public LoadState getLeadingLoadState() {
    return leadingLoadStateAdapter != null ? leadingLoadStateAdapter.getLoadState() : LoadState.NotLoading(endOfPaginationReached = false);
}

public void setLeadingLoadState(LoadState value) {
    leadingLoadStateAdapter.setLoadState(value);
}

public LoadState getTrailingLoadState() {
    return trailingLoadStateAdapter != null ? trailingLoadStateAdapter.getLoadState() : LoadState.NotLoading(endOfPaginationReached = false);
}

public void setTrailingLoadState(LoadState value) {
    trailingLoadStateAdapter.setLoadState(value);
}

public void addHeader(RecyclerView.Adapter< ?> headerAdapter) {
    addHeader(mHeaderList.size(), headerAdapter);
}

public void addHeader(int index, RecyclerView.Adapter< ?> headerAdapter) {
    if (index < 0 || index > mHeaderList.size()) throw new IndexOutOfBoundsException("Index must be between 0 and " + mHeaderList.size() + ". Given:" + index);

    int startIndex = leadingLoadStateAdapter == null ? 0 : 1;

    mAdapter.addAdapter(startIndex + index, headerAdapter);
    mHeaderList.add(headerAdapter);
}

public void clearHeader() {
    for (RecyclerView.Adapter<?> a : mHeaderList) {
        mAdapter.removeAdapter(a);
    }
    mHeaderList.clear();
}

public void addFooter(RecyclerView.Adapter< ?> footerAdapter) {
    if (trailingLoadStateAdapter == null) {
        mAdapter.addAdapter(footerAdapter);
    } else {
        mAdapter.addAdapter(mAdapter.adapters.size - 1, footerAdapter);
    }
    mFooterList.add(footerAdapter);
}

public void addFooter(int index, RecyclerView.Adapter< ?> footerAdapter) {
    if (index < 0 || index > mFooterList.size()) throw new IndexOutOfBoundsException("Index must be between 0 and " + mFooterList.size() + ". Given:" + index);

    int realIndex = trailingLoadStateAdapter == null ? mAdapter.adapters.size - mFooterList.size + index : mAdapter.adapters.size - 1 - mFooterList.size + index;

    mAdapter.addAdapter(realIndex, footerAdapter);
    mFooterList.add(footerAdapter);
}

public void clearfooter() {
    for (RecyclerView.Adapter<?> a : mFooterList) {
        mAdapter.removeAdapter(a);
    }
    mFooterList.clear();
}

public ArrayList<RecyclerView.Adapter< >> getHeaderList() {
    return mHeaderList;
}

public ArrayList<RecyclerView.Adapter< >> getFooterList() {
    return mFooterList;
}

public void removeAdapter(RecyclerView.Adapter< ?> a) {
    if (a == contentAdapter) {
        return;
    }

    mAdapter.removeAdapter(a);
    mHeaderList.remove(a);
    mFooterList.remove(a);
}

public void startDrag(int position) {
    dragAndSwipe.startDrag(position);
}

public void startDrag(RecyclerView.ViewHolder holder) {
    dragAndSwipe.startDrag(holder);
}

public static class Builder {

    private final BaseQuickAdapter<?, ?> contentAdapter;
    private LeadingLoadStateAdapter<?> leadingLoadStateAdapter;
    private TrailingLoadStateAdapter<?> trailingLoadStateAdapter;

    private ConcatAdapter.Config config = ConcatAdapter.Config.DEFAULT;
    private DragAndSwipeImpl<?> dragAndSwipeImpl;

    public Builder(BaseQuickAdapter<?, ?> contentAdapter) {
        this.contentAdapter = contentAdapter;
    }

    public Builder setTrailingLoadStateAdapter(@Nullable TrailingLoadStateAdapter< ?> loadStateAdapter) {
        this.trailingLoadStateAdapter = loadStateAdapter;
        return this;
    }

    public Builder setTrailingLoadStateAdapter(TrailingLoadStateAdapter.OnTrailingListener loadMoreListener) {
        this.trailingLoadStateAdapter = new DefaultTrailingLoadStateAdapter().setOnLoadMoreListener(loadMoreListener);
        return this;
    }

    public Builder setLeadingLoadStateAdapter(@Nullable LeadingLoadStateAdapter< ?> loadStateAdapter) {
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

    public Builder setDragAndSwipe(DragAndSwipeImpl< ?> dragAndSwipeImpl) {
        this.dragAndSwipeImpl = dragAndSwipeImpl;
        return this;
    }

    public QuickAdapterHelper attachToDragAndSwipe(@Nullable RecyclerView recyclerView, int dragMoveFlags, int swipeMoveFlags) {
        checkDragAndSwipeCallback();
        dragAndSwipeImpl.setBaseQuickAdapter(contentAdapter);
        dragAndSwipeImpl.attachToRecyclerView(recyclerView);
        dragAndSwipeImpl.setDragMoveFlags(dragMoveFlags);
        dragAndSwipeImpl.setSwipeMoveFlags(swipeMoveFlags);
        return new QuickAdapterHelper(contentAdapter, leadingLoadStateAdapter, trailingLoadStateAdapter, config, dragAndSwipeImpl);
    }

    public Builder setItemDragListener(@Nullable OnItemDragListener mOnItemDragListener) {
        checkDragAndSwipeCallback();
        dragAndSwipeImpl.setItemDragListener(mOnItemDragListener);
        return this;
    }

    public Builder setDragMoveFlags(int dragMoveFlags) {
        checkDragAndSwipeCallback();
        dragAndSwipeImpl.setDragMoveFlags(dragMoveFlags);
        return this;
    }

    public Builder setSwipeMoveFlags(int swipeMoveFlags) {
        checkDragAndSwipeCallback();
        dragAndSwipeImpl.setSwipeMoveFlags(swipeMoveFlags);
        return this;
    }

    private void checkDragAndSwipeCallback() {
        if (dragAndSwipeImpl == null) {
            dragAndSwipeImpl = new DefaultDragAndSwipe<>();
        }
    }

    public QuickAdapterHelper build() {
        return new QuickAdapterHelper(
                contentAdapter,
                leadingLoadStateAdapter,
                trailingLoadStateAdapter,
                config,
                dragAndSwipeImpl
        );
    }

}
}