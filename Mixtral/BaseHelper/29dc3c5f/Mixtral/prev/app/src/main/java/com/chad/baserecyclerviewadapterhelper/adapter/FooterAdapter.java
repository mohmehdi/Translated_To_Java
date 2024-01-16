package com.chad.baserecyclerviewadapterhelper.adapter;

import android.content.Context;
import android.view.ViewGroup;
import com.chad.baserecyclerviewadapterhelper.R;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.QuickViewHolder;

public class FooterAdapter extends BaseQuickAdapter<Object, QuickViewHolder> {
private Boolean isDelete;
private FooterAdapterClick click;

public FooterAdapter(Boolean isDelete, FooterAdapterClick click) {
super(R.layout.footer_view);
this.isDelete = isDelete;
this.click = click;
}

@Override
public QuickViewHolder onCreateViewHolder(Context context, ViewGroup parent, int viewType) {
QuickViewHolder holder = super.onCreateViewHolder(context, parent, viewType);
holder.itemView.setOnClickListener(v -> click.click(this));
return holder;
}

@Override
public void onBindViewHolder(QuickViewHolder holder, Object item) {
if (isDelete) {
holder.setImageResource(R.id.iv, R.mipmap.rm_icon);
}
}
}