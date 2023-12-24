
package com.chad.baserecyclerviewadapterhelper.adapter;

import android.content.Context;
import android.view.ViewGroup;

import com.chad.baserecyclerviewadapterhelper.R;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chad.library.adapter.base.draggable.DraggableItemAdapter;

public class HeaderDragAndSwipeAdapter extends BaseQuickAdapter<String, BaseViewHolder> implements DraggableItemAdapter<BaseViewHolder> {

    public HeaderDragAndSwipeAdapter() {
        super(R.layout.item_draggable_view);
    }

    @Override
    protected void convert(BaseViewHolder holder, String item) {
        switch (holder.getLayoutPosition() % 3) {
            case 0:
                holder.setImageResource(R.id.iv_head, R.mipmap.head_img0);
                break;
            case 1:
                holder.setImageResource(R.id.iv_head, R.mipmap.head_img1);
                break;
            case 2:
                holder.setImageResource(R.id.iv_head, R.mipmap.head_img2);
                break;
            default:
                break;
        }
        holder.setText(R.id.tv, item);
    }

    @Override
    public BaseViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return createBaseViewHolder(parent, getLayoutResId());
    }

    @Override
    public void onItemSwiped(BaseViewHolder viewHolder, int position) {
        removeAt(position);
    }

    @Override
    public boolean onItemMove(int fromPosition, int toPosition) {
        move(fromPosition, toPosition);
        return true;
    }
}
