
package com.chad.baserecyclerviewadapterhelper.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.chad.baserecyclerviewadapterhelper.R;
import com.chad.library.adapter.base.BaseSingleItemAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

public class FooterAdapter extends BaseSingleItemAdapter<Object, BaseViewHolder> {
    private boolean isDelete;
    private View.OnClickListener clickListener;

    public FooterAdapter(boolean isDelete, View.OnClickListener clickListener) {
        super(R.layout.footer_view);
        this.isDelete = isDelete;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public BaseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(getItemLayout(), parent, false);
        BaseViewHolder viewHolder = new BaseViewHolder(view);
        view.setOnClickListener(clickListener);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull BaseViewHolder holder, int position) {
        if (isDelete) {
            holder.setImageResource(R.id.iv, R.mipmap.rm_icon);
        }
    }
}
