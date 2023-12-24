
package com.chad.baserecyclerviewadapterhelper.activity.dragswipe;

import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.chad.baserecyclerviewadapterhelper.activity.dragswipe.adapter.HeaderDragAndSwipeAdapter;
import com.chad.library.adapter.base.dragswipe.QuickDragAndSwipe;

public class HeaderDragAndSwipe extends QuickDragAndSwipe {

    @Override
    public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        if (recyclerView.getAdapter() instanceof ConcatAdapter) {
            ConcatAdapter adapter = (ConcatAdapter) recyclerView.getAdapter();
            RecyclerView.Adapter absoluteAdapter = adapter.getWrappedAdapterAndPosition(viewHolder.getAbsoluteAdapterPosition()).first;
            if (absoluteAdapter instanceof HeaderDragAndSwipeAdapter) {
                return super.getMovementFlags(recyclerView, viewHolder);
            }
            return makeMovementFlags(0, 0);
        }
        return makeMovementFlags(0, 0);
    }
}
