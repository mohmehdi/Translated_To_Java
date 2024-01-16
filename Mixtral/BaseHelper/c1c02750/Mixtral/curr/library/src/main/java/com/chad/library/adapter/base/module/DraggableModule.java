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
import com.chad.library.adapter.base.dragswipe.DefaultDragAndSwipe;
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
  public boolean isDragEnabled = false;
  public boolean isSwipeEnabled = false;
  public int toggleViewId = NO_TOGGLE_VIEW;
  public ItemTouchHelper itemTouchHelper;
  public DefaultDragAndSwipe itemTouchHelperCallback;

  protected OnTouchListener mOnToggleViewTouchListener;
  protected OnLongClickListener mOnToggleViewLongClickListener;
  protected OnItemDragListener mOnItemDragListener;
  protected OnItemSwipeListener mOnItemSwipeListener;

  public BaseDraggableModule(BaseQuickAdapter baseQuickAdapter) {
    this.baseQuickAdapter = baseQuickAdapter;
    initItemTouch();
  }

  private void initItemTouch() {
    itemTouchHelperCallback = new DefaultDragAndSwipe();
    itemTouchHelper = new ItemTouchHelper(itemTouchHelperCallback);
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

  public void attachToRecyclerView(RecyclerView recyclerView) {
    itemTouchHelper.attachToRecyclerView(recyclerView);
  }

  public boolean hasToggleView() {
    return toggleViewId != NO_TOGGLE_VIEW;
  }

  public void setDragOnLongPressEnabled(boolean value) {
    isDragOnLongPressEnabled = value;
    if (value) {
      mOnToggleViewTouchListener = null;
      mOnToggleViewLongClickListener =
        v -> {
          if (isDragEnabled) {
            itemTouchHelper.startDrag(
              v.getTag(R.id.BaseQuickAdapter_viewholder_support)
            );
          }
          return true;
        };
    } else {
      mOnToggleViewTouchListener =
        (v, event) -> {
          if (
            event.getAction() == MotionEvent.ACTION_DOWN &&
            !isDragOnLongPressEnabled
          ) {
            if (isDragEnabled) {
              itemTouchHelper.startDrag(
                v.getTag(R.id.BaseQuickAdapter_viewholder_support)
              );
            }
            return true;
          } else {
            return false;
          }
        };
      mOnToggleViewLongClickListener = null;
    }
  }

  protected int getViewHolderPosition(RecyclerView.ViewHolder viewHolder) {
    return viewHolder.getBindingAdapterPosition();
  }

  public void onItemDragStart(RecyclerView.ViewHolder viewHolder) {
    mOnItemDragListener.onItemDragStart(
      viewHolder,
      getViewHolderPosition(viewHolder)
    );
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
      mOnItemDragListener.onItemDragMoving(source, from, target, to);
    }
  }

  public void onItemDragEnd(RecyclerView.ViewHolder viewHolder) {
    mOnItemDragListener.onItemDragEnd(
      viewHolder,
      getViewHolderPosition(viewHolder)
    );
  }

  public void onItemSwipeStart(RecyclerView.ViewHolder viewHolder) {
    if (isSwipeEnabled) {
      mOnItemSwipeListener.onItemSwipeStart(
        viewHolder,
        getViewHolderPosition(viewHolder)
      );
    }
  }

  public void onItemSwipeClear(RecyclerView.ViewHolder viewHolder) {
    if (isSwipeEnabled) {
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
      if (isSwipeEnabled) {
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
    if (isSwipeEnabled) {
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
    this.mOnItemDragListener = onItemDragListener;
  }

  @Override
  public void setOnItemSwipeListener(OnItemSwipeListener onItemSwipeListener) {
    this.mOnItemSwipeListener = onItemSwipeListener;
  }

  public static final int NO_TOGGLE_VIEW = 0;
}
