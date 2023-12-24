
package com.chad.library.adapter.base;

import android.view.ViewGroup;

import androidx.annotation.IntRange;
import androidx.recyclerview.widget.DiffUtil;

import com.chad.library.adapter.base.entity.node.BaseExpandNode;
import com.chad.library.adapter.base.entity.node.BaseNode;
import com.chad.library.adapter.base.entity.node.NodeFooterImp;
import com.chad.library.adapter.base.provider.BaseItemProvider;
import com.chad.library.adapter.base.provider.BaseNodeProvider;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public abstract class BaseNodeAdapter extends BaseProviderMultiAdapter<BaseNode> {

    private HashSet<Integer> fullSpanNodeTypeSet = new HashSet<>();

    public BaseNodeAdapter(List<BaseNode> data) {
        super(data);
        if (data != null && !data.isEmpty()) {
            List<BaseNode> flatData = flatData(data);
            data.clear();
            data.addAll(flatData);
        }
    }

    public void addNodeProvider(BaseNodeProvider provider) {
        addItemProvider(provider);
    }

    public void addFullSpanNodeProvider(BaseNodeProvider provider) {
        fullSpanNodeTypeSet.add(provider.getItemViewType());
        addItemProvider(provider);
    }

    public void addFooterNodeProvider(BaseNodeProvider provider) {
        addFullSpanNodeProvider(provider);
    }

    @Override
    public void addItemProvider(BaseItemProvider<BaseNode> provider) {
        if (provider instanceof BaseNodeProvider) {
            super.addItemProvider(provider);
        } else {
            throw new IllegalStateException("Please add BaseNodeProvider, no BaseItemProvider!");
        }
    }

    @Override
    public boolean isFixedViewType(int type) {
        return super.isFixedViewType(type) || fullSpanNodeTypeSet.contains(type);
    }

    @Override
    public BaseViewHolder onCreateDefViewHolder(ViewGroup parent, int viewType) {
        BaseViewHolder viewHolder = super.onCreateDefViewHolder(parent, viewType);
        if (fullSpanNodeTypeSet.contains(viewType)) {
            setFullSpan(viewHolder);
        }
        return viewHolder;
    }

    @Override
    public void setNewData(List<BaseNode> data) {
        if (data == this.getData()) {
            return;
        }
        super.setNewData(flatData(data != null ? data : new ArrayList<>()));
    }

    @Override
    public void addData(int position, BaseNode data) {
        addData(position, new ArrayList<BaseNode>(){{add(data);}});
    }

    @Override
    public void addData(BaseNode data) {
        addData(new ArrayList<BaseNode>(){{add(data);}});
    }

    @Override
    public void addData(int position, Collection<BaseNode> newData) {
        List<BaseNode> nodes = flatData(newData);
        super.addData(position, nodes);
    }

    @Override
    public void addData(Collection<BaseNode> newData) {
        List<BaseNode> nodes