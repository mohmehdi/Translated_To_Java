
package com.chad.baserecyclerviewadapterhelper.activity.dragswipe.adapter;

import android.content.Context;
import android.view.ViewGroup;

import com.chad.baserecyclerviewadapterhelper.R;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chad.library.adapter.base.dragswipe.listener.DragAndSwipeDataCallback;

public class HeaderDragAndSwipeAdapter extends BaseQuickAdapter<String, BaseViewHolder> implements DragAndSwipeDataCallback {

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
    public void dataSwap(int fromPosition, int toPosition) {
        swapData(fromPosition, toPosition);
    }

    @Override
    public void dataRemoveAt(int position) {
        removeAt(position);
    }

    @Override
    public BaseViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return createBaseViewHolder(parent, R.layout.item_draggable_view);
    }
}
