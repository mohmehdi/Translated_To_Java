
package com.chad.library.adapter.base.dragswipe;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemDragListener;

public interface DragAndSwipeImpl {

    void setDragMoveFlags(int dragMoveFlags);

    void setSwipeMoveFlags(int swipeMoveFlags);

    void setItemDragListener(OnItemDragListener onItemDragListener);

    void attachToRecyclerView(@Nullable RecyclerView recyclerView);

    void startDrag(int position);

    void startDrag(RecyclerView.ViewHolder holder);

    void setBaseQuickAdapter(BaseQuickAdapter<?, ?> baseQuickAdapter);
}
