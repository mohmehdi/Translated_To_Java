
package com.chad.baserecyclerviewadapterhelper.adapter.node;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.chad.baserecyclerviewadapterhelper.databinding.ItemNodeEntityBinding;
import com.chad.baserecyclerviewadapterhelper.entity.NodeEntity;
import com.chad.library.adapter.base.BaseSingleItemAdapter;

public class NodeEntityAdapter extends BaseSingleItemAdapter<NodeEntity, NodeEntityAdapter.VH> {

    public NodeEntityAdapter(NodeEntity item) {
        super(item);
    }

    @Override
    public VH onCreateViewHolder(Context context, ViewGroup parent, int viewType) {
        ItemNodeEntityBinding binding = ItemNodeEntityBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(VH holder, NodeEntity item) {
        holder.viewBinding.tvTitle.setText(item.getTitle());
    }

    static class VH extends RecyclerView.ViewHolder {
        ItemNodeEntityBinding viewBinding;

        VH(ItemNodeEntityBinding binding) {
            super(binding.getRoot());
            viewBinding = binding;
        }
    }
}
