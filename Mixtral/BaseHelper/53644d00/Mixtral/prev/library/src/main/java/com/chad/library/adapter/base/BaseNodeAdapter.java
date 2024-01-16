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

public abstract class BaseNodeAdapter<T extends BaseNode> extends BaseProviderMultiAdapter<T> {

    private final HashSet<Integer> fullSpanNodeTypeSet = new HashSet<>();

    public BaseNodeAdapter(List<T> data) {
        super(data);
        if (data != null && !data.isEmpty()) {
            List<T> flatData = flatData(data);
            data.clear();
            data.addAll(flatData);
        }
    }

    public void addNodeProvider(BaseNodeProvider<T> provider) {
        addItemProvider(provider);
    }

    public void addFullSpanNodeProvider(BaseNodeProvider<T> provider) {
        fullSpanNodeTypeSet.add(provider.getItemViewType());
        addItemProvider(provider);
    }

    public void addFooterNodeProvider(BaseNodeProvider<T> provider) {
        addFullSpanNodeProvider(provider);
    }

    @Override
    public void addItemProvider(BaseItemProvider<T> provider) {
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
        BaseViewHolder holder = super.onCreateDefViewHolder(parent, viewType);
        if (fullSpanNodeTypeSet.contains(viewType)) {
            setFullSpan(holder);
        }
        return holder;
    }

    @Override
    public void setNewData(List<T> data) {
        if (data == this.data) {
            return;
        }
        super.setNewData(flatData(data));
    }

    @Override
    public void addData(int position, T data) {
        addData(position, new ArrayList<T>() {{
            add(data);
        }});
    }

    @Override
    public void addData(T data) {
        addData(new ArrayList<T>() {{
            add(data);
        }});
    }

    @Override
    public void addData(int position, Collection<T> newData) {
        List<T> nodes = flatData(newData);
        super.addData(position, nodes);
    }

    @Override
    public void addData(Collection<T> newData) {
        List<T> nodes = flatData(newData);
        super.addData(nodes);
    }

    @Override
    public void remove(int position) {
        int removeCount = removeAt(position);
        notifyItemRangeRemoved(position + getHeaderLayoutCount(), removeCount);
        compatibilityDataSizeChanged(0);
    }

    @Override
    public void setData(int index, T data) {
        int removeCount = removeAt(index);

        List<T> newFlatData = flatData(new ArrayList<T>() {{
            add(data);
        }});
        this.data.addAll(index, newFlatData);

        notifyItemRangeChanged(index + getHeaderLayoutCount(), Math.max(removeCount, newFlatData.size));
    }

    @Override
    public void replaceData(Collection<T> newData) {
        if (newData != this.data) {
            super.replaceData(flatData(newData));
        }
    }

    @Override
    public void setDiffNewData(List<T> newData) {
        if (hasEmptyView()) {
            setNewData(newData);
            return;
        }
        super.setDiffNewData(flatData(newData));
    }

    @Override
    public void setDiffNewData(DiffUtil.DiffResult diffResult, List<T> newData) {
        if (hasEmptyView()) {
            setNewData(newData);
            return;
        }
        super.setDiffNewData(diffResult, flatData(newData));
    }

    private int removeAt(int position) {
        if (position >= data.size()) {
            return 0;
        }
        int removeCount = 0;

        T node = this.data.get(position);
        if (node.getChildNode() != null && !node.getChildNode().isEmpty()) {
            if (node instanceof BaseExpandNode) {
                if (((BaseExpandNode) node).isExpanded()) {
                    List<T> items = flatData(node.getChildNode());
                    this.data.removeAll(items);
                    removeCount = items.size();
                }
            } else {
                List<T> items = flatData(node.getChildNode());
                this.data.removeAll(items);
                removeCount = items.size();
            }
        }
        this.data.removeAt(position);
        removeCount += 1;

        if (node instanceof NodeFooterImp && node.getFooterNode() != null) {
            this.data.removeAt(position);
            removeCount += 1;
        }
        return removeCount;
    }

    private int removeChildAt(int position) {
        if (position >= data.size()) {
            return 0;
        }
        int removeCount = 0;

        T node = this.data.get(position);
        if (node.getChildNode() != null && !node.getChildNode().isEmpty()) {
            if (node instanceof BaseExpandNode) {
                if (((BaseExpandNode) node).isExpanded()) {
                    List<T> items = flatData(node.getChildNode());
                    this.data.removeAll(items);
                    removeCount = items.size();
                }
            } else {
                List<T> items = flatData(node.getChildNode());
                this.data.removeAll(items);
                removeCount = items.size();
            }
        }
        return removeCount;
    }

    public void nodeAddData(T parentNode, T data) {
        if (parentNode.getChildNode() != null) {
            parentNode.getChildNode().add(data);
            int childIndex = parentNode.getChildNode().size();

            if (parentNode instanceof BaseExpandNode && !((BaseExpandNode) parentNode).isExpanded()) {
                return;
            }

            int parentIndex = this.data.indexOf(parentNode);
            int pos = parentIndex + 1 + childIndex;
            addData(pos, data);
        }
    }

    public void nodeAddData(T parentNode, int childIndex, T data) {
        if (parentNode.getChildNode() != null) {
            parentNode.getChildNode().add(childIndex, data);

            if (parentNode instanceof BaseExpandNode && !((BaseExpandNode) parentNode).isExpanded()) {
                return;
            }

            int parentIndex = this.data.indexOf(parentNode);
            int pos = parentIndex + 1 + childIndex;
            addData(pos, data);
        }
    }

    public void nodeAddData(T parentNode, int childIndex, Collection<T> newData) {
        if (parentNode.getChildNode() != null) {
            parentNode.getChildNode().addAll(childIndex, newData);

            if (parentNode instanceof BaseExpandNode && !((BaseExpandNode) parentNode).isExpanded()) {
                return;
            }
            int parentIndex = this.data.indexOf(parentNode);
            int pos = parentIndex + 1 + childIndex;
            addData(pos, newData);
        }
    }

    public void nodeRemoveData(T parentNode, int childIndex) {
        if (parentNode.getChildNode() != null) {
            if (childIndex >= parentNode.getChildNode().size()) {
                return;
            }

            if (parentNode instanceof BaseExpandNode && !((BaseExpandNode) parentNode).isExpanded()) {
                parentNode.getChildNode().removeAt(childIndex);
                return;
            }

            int parentIndex = this.data.indexOf(parentNode);
            int pos = parentIndex + 1 + childIndex;
            remove(pos);

            parentNode.getChildNode().removeAt(childIndex);
        }
    }

    public void nodeRemoveData(T parentNode, T childNode) {
        if (parentNode.getChildNode() != null) {
            if (parentNode instanceof BaseExpandNode && !((BaseExpandNode) parentNode).isExpanded()) {
                parentNode.getChildNode().remove(childNode);
                return;
            }
            remove(childNode);

            parentNode.getChildNode().remove(childNode);
        }
    }

    public void nodeSetData(T parentNode, int childIndex, T data) {
        if (parentNode.getChildNode() != null) {
            if (childIndex >= parentNode.getChildNode().size()) {
                return;
            }

            if (parentNode instanceof BaseExpandNode && !((BaseExpandNode) parentNode).isExpanded()) {
                parentNode.getChildNode().set(childIndex, data);
                return;
            }

            int parentIndex = this.data.indexOf(parentNode);
            int pos = parentIndex + 1 + childIndex;
            setData(pos, data);

            parentNode.getChildNode().set(childIndex, data);
        }
    }

    public void nodeReplaceChildData(T parentNode, Collection<T> newData) {
        if (parentNode.getChildNode() != null) {
            if (parentNode instanceof BaseExpandNode && !((BaseExpandNode) parentNode).isExpanded()) {
                parentNode.getChildNode().clear();
                parentNode.getChildNode().addAll(newData);
                return;
            }

            int parentIndex = this.data.indexOf(parentNode);
            int removeCount = removeChildAt(parentIndex);
            notifyItemRangeRemoved(parentIndex + 1 + getHeaderLayoutCount(), removeCount);

            parentNode.getChildNode().clear();
            parentNode.getChildNode().addAll(newData);

            List<T> newFlatData = flatData(newData);
            this.data.addAll(parentIndex + 1, newFlatData);

            notifyItemRangeInserted(parentIndex + 1 + getHeaderLayoutCount(), newFlatData.size());
        }
    }

    private List<T> flatData(Collection<T> list, Boolean isExpanded) {
        List<T> newList = new ArrayList<>();

        for (T element : list) {
            newList.add(element);

            if (element instanceof BaseExpandNode) {
                if (isExpanded == true || ((BaseExpandNode) element).isExpanded()) {
                    List<T> childNode = element.getChildNode();
                    if (childNode != null && !childNode.isEmpty()) {
                        List<T> items = flatData(childNode, isExpanded);
                        newList.addAll(items);
                    }
                }
                if (isExpanded != null) {
                    ((BaseExpandNode) element).setExpanded(isExpanded);
                }
            } else {
                List<T> childNode = element.getChildNode();
                if (childNode != null && !childNode.isEmpty()) {
                    List<T> items = flatData(childNode, isExpanded);
                    newList.addAll(items);
                }
            }

            if (element instanceof NodeFooterImp) {
                NodeFooterImp footerImp = (NodeFooterImp) element;
                if (footerImp.getFooterNode() != null) {
                    newList.add(footerImp.getFooterNode());
                }
            }
        }
        return newList;
    }

    private int collapse(int position, boolean isChangeChildCollapse, boolean animate, boolean notify) {
        T node = this.data.get(position);

        if (node instanceof BaseExpandNode && ((BaseExpandNode) node).isExpanded()) {
            int adapterPosition = position + getHeaderLayoutCount();

            ((BaseExpandNode) node).setExpanded(false);
            if (node.getChildNode() == null || node.getChildNode().isEmpty()) {
                if (notify) {
                    notifyItemChanged(adapterPosition);
                }
                return 0;
            }
            List<T> items = flatData(node.getChildNode(), isChangeChildCollapse);
            int size = items.size();
            this.data.removeAll(items);
            if (notify) {
                if (animate) {
                    notifyItemChanged(adapterPosition);
                    notifyItemRangeRemoved(adapterPosition + 1, size);
                } else {
                    notifyDataSetChanged();
                }
            }
            return size;
        }
        return 0;
    }

    private int expand(int position, boolean isChangeChildExpand, boolean animate, boolean notify) {
        T node = this.data.get(position);

        if (node instanceof BaseExpandNode && !((BaseExpandNode) node).isExpanded()) {
            int adapterPosition = position + getHeaderLayoutCount();

            ((BaseExpandNode) node).setExpanded(true);
            if (node.getChildNode() == null || node.getChildNode().isEmpty()) {
                if (notify) {
                    notifyItemChanged(adapterPosition);
                }
                return 0;
            }
            List<T> items = flatData(node.getChildNode(), isChangeChildExpand);
            int size = items.size();
            this.data.addAll(position + 1, items);
            if (notify) {
                if (animate) {
                    notifyItemChanged(adapterPosition);
                    notifyItemRangeInserted(adapterPosition + 1, size);
                } else {
                    notifyDataSetChanged();
                }
            }
            return size;
        }
        return 0;
    }

    public int collapse(int position, boolean animate, boolean notify) {
        return collapse(position, false, animate, notify);
    }

    public int expand(int position, boolean animate, boolean notify) {
        return expand(position, false, animate, notify);
    }

    public int expandOrCollapse(int position, boolean animate, boolean notify) {
        T node = this.data.get(position);
        if (node instanceof BaseExpandNode) {
            return (node.isExpanded()) ? collapse(position, false, animate, notify) : expand(position, false, animate, notify);
        }
        return 0;
    }

    public int expandAndChild(int position, boolean animate, boolean notify) {
        return expand(position, true, animate, notify);
    }

    public int collapseAndChild(int position, boolean animate, boolean notify) {
        return collapse(position, true, animate, notify);
    }

    public void expandAndCollapseOther(int position, boolean isExpandedChild, boolean isCollapseChild, boolean animate, boolean notify) {

        int expandCount = expand(position, isExpandedChild, animate, notify);
        if (expandCount == 0) {
            return;
        }

        int parentPosition = findParentNode(position);
        int firstPosition = (parentPosition == -1) ? 0 : parentPosition + 1;

        int newPosition = position;

        int beforeAllSize = position - firstPosition;

        if (beforeAllSize > 0) {
            for (int i = firstPosition; i < newPosition; i++) {
                int collapseSize = collapse(i, isCollapseChild, animate, notify);
                newPosition -= collapseSize;
            }
        }

        int lastPosition = (parentPosition == -1) ? data.size() - 1 : (data.get(parentPosition).getChildNode() != null ? data.get(parentPosition).getChildNode().size() + expandCount + position + 1 : position + expandCount + 1);

        if ((newPosition + expandCount) < lastPosition) {
            for (int i = newPosition + expandCount + 1; i <= lastPosition; i++) {
                int collapseSize = collapse(i, isCollapseChild, animate, notify);
                lastPosition -= collapseSize;
            }
        }
    }

    public int findParentNode(T node) {
        int pos = this.data.indexOf(node);
        if (pos == -1 || pos == 0) {
            return -1;
        }

        for (int i = pos - 1; i >= 0; i--) {
            T tempNode = this.data.get(i);
            if (tempNode.getChildNode() != null && tempNode.getChildNode().contains(node)) {
                return i;
            }
        }
        return -1;
    }

    public int findParentNode(int position) {
        if (position == 0) {
            return -1;
        }
        T node = this.data.get(position);
        for (int i = position - 1; i >= 0; i--) {
            T tempNode = this.data.get(i);
            if (tempNode.getChildNode() != null && tempNode.getChildNode().contains(node)) {
                return i;
            }
        }
        return -1;
    }
}