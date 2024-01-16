package com.chad.library.adapter.base.module;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.chad.library.R;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.dragswipe.DragAndSwipeCallback;
import com.chad.library.adapter.base.listener.DraggableListenerImp;
import com.chad.library.adapter.base.listener.OnItemDragListener;
import com.chad.library.adapter.base.listener.OnItemSwipeListener;
import java.util.Collections;
import java.util.List;

public interface DraggableModule {
  BaseDraggableModule addDraggableModule(BaseQuickAdapter baseQuickAdapter);
}

public class BaseDraggableModule extends DraggableListenerImp {

  private BaseQuickAdapter baseQuickAdapter;
  private boolean isDragEnabled;
  private boolean isSwipeEnabled;
  private int toggleViewId;
  private ItemTouchHelper itemTouchHelper;
  private DragAndSwipeCallback itemTouchHelperCallback;
  private OnTouchListener mOnToggleViewTouchListener;
  private OnLongClickListener mOnToggleViewLongClickListener;
  private OnItemDragListener mOnItemDragListener;
  private OnItemSwipeListener mOnItemSwipeListener;

  public BaseDraggableModule(BaseQuickAdapter baseQuickAdapter) {
    this.baseQuickAdapter = baseQuickAdapter;
    initItemTouch();
  }

  private void initItemTouch() {
    itemTouchHelperCallback = new DragAndSwipeCallback();
    itemTouchHelper = new ItemTouchHelper(itemTouchHelperCallback);
  }

  public void attachToRecyclerView(RecyclerView recyclerView) {
    itemTouchHelper.attachToRecyclerView(recyclerView);
  }

  public boolean hasToggleView() {
    return toggleViewId != NO_TOGGLE_VIEW;
  }

  public void initView(RecyclerView.ViewHolder holder) {
    if (isDragEnabled) {
      if (hasToggleView()) {
        View toggleView = holder.itemView.findViewById(toggleViewId);
        if (toggleView != null) {
          toggleView.setTag(R.id.BaseQuickAdapter_viewholder_support, holder);
          if (isDragOnLongPressEnabled) {
            toggleView.setOnLongClickListener(mOnToggleViewLongClickListener);
          } else {
            toggleView.setOnTouchListener(mOnToggleViewTouchListener);
          }
        }
      }
    }
  }

  public void addDraggableModule(BaseQuickAdapter baseQuickAdapter) {
    return new BaseDraggableModule(baseQuickAdapter);
  }

  public int getViewHolderPosition(RecyclerView.ViewHolder viewHolder) {
    return viewHolder.getBindingAdapterPosition();
  }

  public void onItemDragStart(RecyclerView.ViewHolder viewHolder) {
    if (mOnItemDragListener != null) {
      mOnItemDragListener.onItemDragStart(
        viewHolder,
        getViewHolderPosition(viewHolder)
      );
    }
  }

  public void onItemDragMoving(
    RecyclerView.ViewHolder source,
    RecyclerView.ViewHolder target
  ) {
    int from = getViewHolderPosition(source);
    int to = getViewHolderPosition(target);
    if (inRange(from) && inRange(to)) {
      if (from < to) {
        for (int i = from; i < to; i++) {
          Collections.swap(baseQuickAdapter.getItems(), i, i + 1);
        }
      } else {
        for (int i = from; i > to; i--) {
          Collections.swap(baseQuickAdapter.getItems(), i, i - 1);
        }
      }
      baseQuickAdapter.notifyItemMoved(
        source.getAdapterPosition(),
        target.getAdapterPosition()
      );
      if (mOnItemDragListener != null) {
        mOnItemDragListener.onItemDragMoving(source, from, target, to);
      }
    }
  }

  public void onItemDragEnd(RecyclerView.ViewHolder viewHolder) {
    if (mOnItemDragListener != null) {
      mOnItemDragListener.onItemDragEnd(
        viewHolder,
        getViewHolderPosition(viewHolder)
      );
    }
  }

  public void onItemSwipeStart(RecyclerView.ViewHolder viewHolder) {
    if (isSwipeEnabled && mOnItemSwipeListener != null) {
      mOnItemSwipeListener.onItemSwipeStart(
        viewHolder,
        getViewHolderPosition(viewHolder)
      );
    }
  }

  public void onItemSwipeClear(RecyclerView.ViewHolder viewHolder) {
    if (isSwipeEnabled && mOnItemSwipeListener != null) {
      mOnItemSwipeListener.clearView(
        viewHolder,
        getViewHolderPosition(viewHolder)
      );
    }
  }

  public void onItemSwiped(RecyclerView.ViewHolder viewHolder) {
    int pos = getViewHolderPosition(viewHolder);
    if (inRange(pos)) {
      baseQuickAdapter.notifyItemRemoved(viewHolder.getAdapterPosition());
      if (isSwipeEnabled && mOnItemSwipeListener != null) {
        mOnItemSwipeListener.onItemSwiped(viewHolder, pos);
      }
    }
  }

  public void onItemSwiping(
    Canvas canvas,
    RecyclerView.ViewHolder viewHolder,
    float dX,
    float dY,
    boolean isCurrentlyActive
  ) {
    if (isSwipeEnabled && mOnItemSwipeListener != null) {
      mOnItemSwipeListener.onItemSwipeMoving(
        canvas,
        viewHolder,
        dX,
        dY,
        isCurrentlyActive
      );
    }
  }

  private boolean inRange(int position) {
    return position >= 0 && position < baseQuickAdapter.getItems().size();
  }

  @Override
  public void setOnItemDragListener(OnItemDragListener onItemDragListener) {
    mOnItemDragListener = onItemDragListener;
  }

  @Override
  public void setOnItemSwipeListener(OnItemSwipeListener onItemSwipeListener) {
    mOnItemSwipeListener = onItemSwipeListener;
  }

  private static final int NO_TOGGLE_VIEW = 0;
}
