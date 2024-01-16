package com.chad.library.adapter.base.dragswipe;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemDragListener;
import java.util.Collections;

public class DefaultDragAndSwipe implements ItemTouchHelper.Callback, DragAndSwipeImpl {

    private int _dragMoveFlags =
            ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
    private int _swipeMoveFlags = ItemTouchHelper.END;
    private RecyclerView recyclerView;
    private ItemTouchHelper _itemTouchHelper;
    private OnItemDragListener mOnItemDragListener;
    private BaseQuickAdapter mBaseQuickAdapter;

    @Override
    public void attachToRecyclerView(@Nullable RecyclerView recyclerView) {
        if (this.recyclerView == recyclerView) return;
        this.recyclerView = recyclerView;
        if (null == _itemTouchHelper) {
            _itemTouchHelper = new ItemTouchHelper(this);
            _itemTouchHelper.attachToRecyclerView(recyclerView);
        }
    }

    @Override
    public void setDragMoveFlags(int dragMoveFlags) {
        _dragMoveFlags = dragMoveFlags;
    }

    @Override
    public void setSwipeMoveFlags(int swipeMoveFlags) {
        _swipeMoveFlags = swipeMoveFlags;
    }

    @Override
    public void setItemDragListener(OnItemDragListener onItemDragListener) {
        this.mOnItemDragListener = onItemDragListener;
    }
    
    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
        super.onSelectedChanged(viewHolder, actionState);
        switch (actionState) {
            case ItemTouchHelper.ACTION_STATE_DRAG:
                if (mOnItemDragListener != null) {
                    mOnItemDragListener.onItemDragStart(
                            viewHolder,
                            getViewHolderPosition(viewHolder)
                    );
                }
                break;
        }
    }

    @Override
    public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        if (isEmptyView(viewHolder)) {
            return makeMovementFlags(0, 0);
        }
        return makeMovementFlags(_dragMoveFlags, _swipeMoveFlags);
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        return viewHolder.getItemViewType() == target.getItemViewType();
    }

    @Override
    public void onMoved(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int fromPos, RecyclerView.ViewHolder target, int toPos, int x, int y) {
        super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y);
        dataSwap(fromPos, toPos);
        mOnItemDragListener.onItemDragMoving(viewHolder, fromPos, target, toPos);
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

    }

    @Override
    public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        mOnItemDragListener.onItemDragEnd(viewHolder, getViewHolderPosition(viewHolder));
    }

    private void dataSwap(int fromPosition, int toPosition) {
        if (inRange(fromPosition) && inRange(toPosition)) {
            Object data = mBaseQuickAdapter.getData();
            if (fromPosition < toPosition) {
                for (int i = fromPosition; i < toPosition; i++) {
                    Collections.swap((List<?>) data, i, i + 1);
                }
            } else {
                int toP = toPosition + 1;
                for (int i = fromPosition; i > toP; i--) {
                    Collections.swap((List<?>) data, i, i - 1);
                }
            }
            mBaseQuickAdapter.notifyItemMoved(fromPosition, toPosition);
        }
    }

    @Override
    public void startDrag(RecyclerView.ViewHolder holder) {
        _itemTouchHelper.startDrag(holder);
    }

    @Override
    public void setBaseQuickAdapter(BaseQuickAdapter baseQuickAdapter) {
        mBaseQuickAdapter = baseQuickAdapter;
    }

    @Override
    public void startDrag(int position) {
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (holder == null) return;
        _itemTouchHelper.startDrag(holder);
    }

    private boolean isEmptyView(RecyclerView.ViewHolder viewHolder) {
        return viewHolder.getItemViewType() == BaseQuickAdapter.EMPTY_VIEW;
    }

    private boolean inRange(int position) {
        int size = mBaseQuickAdapter.getData().size();
        return position >= 0 && position < size;
    }

    private int getViewHolderPosition(RecyclerView.ViewHolder viewHolder) {
        return viewHolder != null ? viewHolder.getAdapterPosition() : RecyclerView.NO_POSITION;
    }
}