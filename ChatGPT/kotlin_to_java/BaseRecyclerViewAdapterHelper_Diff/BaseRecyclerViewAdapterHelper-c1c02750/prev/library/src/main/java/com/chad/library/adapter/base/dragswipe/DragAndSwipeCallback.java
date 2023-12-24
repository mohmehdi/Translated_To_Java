
package com.chad.library.adapter.base.dragswipe;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemDragListener;
import java.util.Collections;
import java.util.List;

public class DragAndSwipeCallback extends ItemTouchHelper.Callback {

    private int dragMoveFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
    private int swipeMoveFlags = ItemTouchHelper.END;
    private RecyclerView mRecyclerView;
    private ItemTouchHelper mItemTouchHelper;
    private OnItemDragListener mOnItemDragListener;
    private BaseQuickAdapter<?, ?> baseQuickAdapter;

    public void attachToRecyclerView(@Nullable RecyclerView recyclerView) {
        if (this.mRecyclerView == recyclerView) return;
        mRecyclerView = recyclerView;
        if (mItemTouchHelper == null) {
            mItemTouchHelper = new ItemTouchHelper(this);
            mItemTouchHelper.attachToRecyclerView(recyclerView);
        }
    }

    public void setItemDragListener(OnItemDragListener onItemDragListener) {
        this.mOnItemDragListener = onItemDragListener;
    }

    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
        super.onSelectedChanged(viewHolder, actionState);
        switch (actionState) {
            case ItemTouchHelper.ACTION_STATE_DRAG:
                if (mOnItemDragListener != null) {
                    mOnItemDragListener.onItemDragStart(viewHolder, getViewHolderPosition(viewHolder));
                }
                break;
        }
    }

    @Override
    public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        if (isEmptyView(viewHolder)) {
            return makeMovementFlags(0, 0);
        }
        return makeMovementFlags(dragMoveFlags, swipeMoveFlags);
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        return viewHolder.getItemViewType() == target.getItemViewType();
    }

    @Override
    public void onMoved(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int fromPos, RecyclerView.ViewHolder target, int toPos, int x, int y) {
        super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y);
        dataSwap(fromPos, toPos);
        if (mOnItemDragListener != null) {
            mOnItemDragListener.onItemDragMoving(viewHolder, fromPos, target, toPos);
        }
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

    }

    @Override
    public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        if (mOnItemDragListener != null) {
            mOnItemDragListener.onItemDragEnd(viewHolder, getViewHolderPosition(viewHolder));
        }
    }

    private void dataSwap(int fromPosition, int toPosition) {
        if (inRange(fromPosition) && inRange(toPosition)) {
            List<?> data = baseQuickAdapter.getItems();
            if (fromPosition < toPosition) {
                for (int i = fromPosition; i < toPosition; i++) {
                    Collections.swap(data, i, i + 1);
                }
            } else {
                int toP = toPosition + 1;
                for (int i = fromPosition; i >= toP; i--) {
                    Collections.swap(data, i, i - 1);
                }
            }
            baseQuickAdapter.notifyItemMoved(fromPosition, toPosition);
        }
    }

    public void startDrag(RecyclerView.ViewHolder holder) {
        if (mItemTouchHelper != null) {
            mItemTouchHelper.startDrag(holder);
        }
    }

    public void startDrag(int position) {
        if (mRecyclerView != null) {
            RecyclerView.ViewHolder holder = mRecyclerView.findViewHolderForAdapterPosition(position);
            if (holder != null && mItemTouchHelper != null) {
                mItemTouchHelper.startDrag(holder);
            }
        }
    }

    private boolean isEmptyView(RecyclerView.ViewHolder viewHolder) {
        return viewHolder.getItemViewType() == BaseQuickAdapter.EMPTY_VIEW;
    }

    private boolean inRange(int position) {
        int size = baseQuickAdapter.getItems().size();
        return position >= 0 && position < size;
    }

    private int getViewHolderPosition(RecyclerView.ViewHolder viewHolder) {
        return viewHolder != null ? viewHolder.getBindingAdapterPosition() : RecyclerView.NO_POSITION;
    }
}
