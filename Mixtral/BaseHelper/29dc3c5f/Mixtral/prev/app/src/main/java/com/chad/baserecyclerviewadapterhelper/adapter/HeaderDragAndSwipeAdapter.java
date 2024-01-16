package com.chad.baserecyclerviewadapterhelper.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.baserecyclerviewadapterhelper.R;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.QuickViewHolder;
import com.chad.library.adapter.base.dragswipe.listener.DragAndSwipeDataCallback;

import java.util.List;

public class HeaderDragAndSwipeAdapter extends BaseQuickAdapter<String, QuickViewHolder> implements DragAndSwipeDataCallback {


public HeaderDragAndSwipeAdapter(int layoutResId, List<String> data) {
    super(layoutResId, data);
}

@Override
public QuickViewHolder onCreateViewHolder(View view, int viewType) {
    return new QuickViewHolder(view);
}

@Override
public QuickViewHolder onCreateViewHolder(Context context, ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(context).inflate(R.layout.item_draggable_view, parent, false);
    return new QuickViewHolder(view);
}

@Override
public void onBindViewHolder(QuickViewHolder holder, int position, String item) {
    int layoutPosition = holder.getLayoutPosition();
    switch (layoutPosition % 3) {
        case 0:
            ((ImageView) holder.getView(R.id.iv_head)).setImageResource(R.mipmap.head_img0);
            break;
        case 1:
            ((ImageView) holder.getView(R.id.iv_head)).setImageResource(R.mipmap.head_img1);
            break;
        case 2:
            ((ImageView) holder.getView(R.id.iv_head)).setImageResource(R.mipmap.head_img2);
            break;
        default:
            break;
    }
    ((TextView) holder.getView(R.id.tv)).setText(item);
}

@Override
public void dataSwap(int fromPosition, int toPosition) {
    swap(fromPosition, toPosition);
}

@Override
public void dataRemoveAt(int position) {
    removeAt(position);
}
}