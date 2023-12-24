
package com.chad.baserecyclerviewadapterhelper.adapter.node;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.chad.baserecyclerviewadapterhelper.databinding.ItemNodeFirstBinding;
import com.chad.baserecyclerviewadapterhelper.entity.NodeEntity;
import com.chad.library.adapter.base.BaseSingleItemAdapter;

public class FirstNodeAdapter extends BaseSingleItemAdapter<NodeEntity.FirstNode, FirstNodeAdapter.VH> {

    public FirstNodeAdapter(NodeEntity.FirstNode item) {
        super(item);
    }

    @Override
    public VH onCreateViewHolder(Context context, ViewGroup parent, int viewType) {
        return new VH(ItemNodeFirstBinding.inflate(LayoutInflater.from(context), parent, false));
    }

    @Override
    public void onBindViewHolder(VH holder, NodeEntity.FirstNode item) {
        holder.viewBinding.tvContent.setText(item.getContent());
    }

    static class VH extends RecyclerView.ViewHolder {
        ItemNodeFirstBinding viewBinding;

        VH(ItemNodeFirstBinding viewBinding) {
            super(viewBinding.getRoot());
            this.viewBinding = viewBinding;
        }
    }
}
