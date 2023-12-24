
package com.chad.baserecyclerviewadapterhelper.activity.headerfooter.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.baserecyclerviewadapterhelper.R;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import java.util.List;

public class FooterAdapter extends BaseQuickAdapter<Object, BaseViewHolder> {
    private boolean isDelete;
    private View.OnClickListener clickListener;

    public FooterAdapter(boolean isDelete, View.OnClickListener clickListener) {
        super(R.layout.footer_view);
        this.isDelete = isDelete;
        this.clickListener = clickListener;
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, Object item) {
        if (isDelete) {
            ImageView imageView = holder.getView(R.id.iv);
            imageView.setImageResource(R.mipmap.rm_icon);
        }
    }

    @NonNull
    @Override
    public BaseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.footer_view, parent, false);
        BaseViewHolder viewHolder = new BaseViewHolder(view);
        view.setOnClickListener(clickListener);
        return viewHolder;
    }
}
