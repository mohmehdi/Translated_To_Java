package com.chad.library.adapter.base.dragswipe;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemDragListener;

public interface DragAndSwipeImpl {

public void setDragMoveFlags(int dragMoveFlags);

public void setSwipeMoveFlags(int swipeMoveFlags);

public void setItemDragListener(OnItemDragListener onItemDragListener);

public void attachToRecyclerView(@Nullable RecyclerView recyclerView);

public void startDrag(int position);

public void startDrag(RecyclerView.ViewHolder holder);

public void setBaseQuickAdapter(BaseQuickAdapter baseQuickAdapter);
}