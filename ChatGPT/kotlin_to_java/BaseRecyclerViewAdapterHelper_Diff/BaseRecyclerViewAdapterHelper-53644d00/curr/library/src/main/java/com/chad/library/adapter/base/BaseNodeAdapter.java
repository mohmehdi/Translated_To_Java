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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public abstract class BaseNodeAdapter extends BaseProviderMultiAdapter<BaseNode> {

    private final HashSet<Integer> fullSpanNodeTypeSet = new HashSet<>();

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
        super.setNewData(flatData(data != null ? data : emptyList()));
    }

    @Override
    public void addData(int position, BaseNode data) {
        addData(position, Collections.singletonList(data));
    }

    @Override
    public void addData(BaseNode data) {
        addData(Collections.singletonList(data));
    }

    @Override
    public void addData(int position, Collection<BaseNode> newData) {
        List<BaseNode> nodes = flatData(newData);
        super.addData(position, nodes);
    }

    @Override
    public void addData(Collection<BaseNode> newData) {
        List<BaseNode> nodes = flatData(newData);
        super.addData(nodes);
    }

    @Override
    public void remove(int position) {
        int removeCount = removeAt(position);
        notifyItemRangeRemoved(position + getHeaderLayoutCount(), removeCount);
        compatibilityDataSizeChanged(0);
    }

    @Override
    public void setData(int index, BaseNode data) {
        int removeCount = removeAt(index);

        List<BaseNode> newFlatData = flatData(Collections.singletonList(data));
        getData().addAll(index, newFlatData);

        if (removeCount == newFlatData.size()) {
            notifyItemRangeChanged(index + getHeaderLayoutCount(), removeCount);
        } else {
            notifyItemRangeRemoved(index + getHeaderLayoutCount(), removeCount);
            notifyItemRangeInserted(index + getHeaderLayoutCount(), newFlatData.size());
        }
    }

    @Override
    public void replaceData(Collection<BaseNode> newData) {
        if (!newData.equals(getData())) {
            super.replaceData(flatData(newData));
        }
    }

    @Override
    public void setDiffNewData(List<BaseNode> newData) {
        if (hasEmptyView()) {
            setNewData(newData);
            return;
        }
        super.setDiffNewData(flatData(newData != null ? newData : emptyList()));
    }

    @Override
    public void setDiffNewData(DiffUtil.DiffResult diffResult, List<BaseNode> newData) {
        if (hasEmptyView()) {
            setNewData(newData);
            return;
        }
        super.setDiffNewData(diffResult, flatData(newData != null ? newData : emptyList()));
    }

    private int removeAt(int position) {
        if (position >= getData().size()) {
            return 0;
        }

        int removeCount = 0;
        BaseNode node = getData().get(position);

        if (node != null && !node.getChildNode().isEmpty()) {
            List<BaseNode> items = flatData(node.getChildNode());
            getData().removeAll(items);
            removeCount = items.size();
        }

        getData().remove(position);
        removeCount += 1;

        if (node instanceof NodeFooterImp && ((NodeFooterImp) node).getFooterNode() != null) {
            getData().remove(position);
            removeCount += 1;
        }
        return removeCount;
    }

    private int removeChildAt(int position) {
        if (position >= getData().size()) {
            return 0;
        }

        int removeCount = 0;
        BaseNode node = getData().get(position);

        if (node != null && !node.getChildNode().isEmpty()) {
            List<BaseNode> items = flatData(node.getChildNode());
            getData().removeAll(items);
            removeCount = items.size();
        }
        return removeCount;
    }

    public void nodeAddData(BaseNode parentNode, BaseNode data) {
        List<BaseNode> childNode = parentNode.getChildNode();
        if (childNode != null) {
            childNode.add(data);
            int childIndex = childNode.size();

            if (parentNode instanceof BaseExpandNode && !((BaseExpandNode) parentNode).isExpanded()) {
                return;
            }

            int parentIndex = getData().indexOf(parentNode);
            addData(parentIndex + childIndex, data);
        }
    }

    public void nodeAddData(BaseNode parentNode, int childIndex, BaseNode data) {
        List<BaseNode> childNode = parentNode.getChildNode();
        if (childNode != null) {
            childNode.add(childIndex, data);

            if (parentNode instanceof BaseExpandNode && !((BaseExpandNode) parentNode).isExpanded()) {
                return;
            }

            int parentIndex = getData().indexOf(parentNode);
            int pos = parentIndex + 1 + childIndex;
            addData(pos, data);
        }
    }

    public void nodeAddData(BaseNode parentNode, int childIndex, Collection<BaseNode> newData) {
        List<BaseNode> childNode = parentNode.getChildNode();
        if (childNode != null) {
            childNode.addAll(childIndex, newData);

            if (parentNode instanceof BaseExpandNode && !((BaseExpandNode) parentNode).isExpanded()) {
                return;
            }
            int parentIndex = getData().indexOf(parentNode);
            int pos = parentIndex + 1 + childIndex;
            addData(pos, newData);
        }
    }

    public void nodeRemoveData(BaseNode parentNode, int childIndex) {
        List<BaseNode> childNode = parentNode.getChildNode();
        if (childNode != null) {
            if (childIndex >= childNode.size()) {
                return;
            }

            if (parentNode instanceof BaseExpandNode && !((BaseExpandNode) parentNode).isExpanded()) {
                childNode.remove(childIndex);
                return;
            }

            int parentIndex = getData().indexOf(parentNode);
            int pos = parentIndex + 1 + childIndex;
            remove(pos);

            childNode.remove(childIndex);
        }
    }

    public void nodeRemoveData(BaseNode parentNode, BaseNode childNode) {
        List<BaseNode> childNodeList = parentNode.getChildNode();
        if (childNodeList != null) {
            if (parentNode instanceof BaseExpandNode && !((BaseExpandNode) parentNode).isExpanded()) {
                childNodeList.remove(childNode);
                return;
            }
            remove(childNode);

            childNodeList.remove(childNode);
        }
    }

    public void nodeSetData(BaseNode parentNode, int childIndex, BaseNode data) {
        List<BaseNode> childNode = parentNode.getChildNode();
        if (childNode != null) {
            if (childIndex >= childNode.size()) {
                return;
            }

            if (parentNode instanceof BaseExpandNode && !((BaseExpandNode) parentNode).isExpanded()) {
                childNode.set(childIndex, data);
                return;
            }

            int parentIndex = getData().indexOf(parentNode);
            int pos = parentIndex + 1 + childIndex;
            setData(pos, data);

            childNode.set(childIndex, data);
        }
    }
        public void nodeReplaceChildData(BaseNode parentNode, Collection<BaseNode> newData) {
        parentNode.childNode = null;
        parentNode.childNode = new ArrayList<>(newData);
        if (parentNode instanceof BaseExpandNode && !((BaseExpandNode) parentNode).isExpanded) {
            return;
        }

        int parentIndex = this.getData().indexOf(parentNode);
        int removeCount = removeChildAt(parentIndex);

        List<BaseNode> newFlatData = flatData(newData);
        this.getData().addAll(parentIndex + 1, newFlatData);

        int positionStart = parentIndex + 1 + getHeaderLayoutCount();
        if (removeCount == newFlatData.size()) {
            notifyItemRangeChanged(positionStart, removeCount);
        } else {
            notifyItemRangeRemoved(positionStart, removeCount);
            notifyItemRangeInserted(positionStart, newFlatData.size());
        }
    }

    private List<BaseNode> flatData(Collection<BaseNode> list) {
        return flatData(list, null);
    }

    private List<BaseNode> flatData(Collection<BaseNode> list, Boolean isExpanded) {
        List<BaseNode> newList = new ArrayList<>();

        for (BaseNode element : list) {
            newList.add(element);

            if (element instanceof BaseExpandNode) {
                if (isExpanded == null || isExpanded || ((BaseExpandNode) element).isExpanded) {
                    List<BaseNode> items = flatData(((BaseExpandNode) element).childNode, isExpanded);
                    newList.addAll(items);
                }
                if (isExpanded != null) {
                    ((BaseExpandNode) element).isExpanded = isExpanded;
                }
            } else {
                List<BaseNode> items = flatData(element.childNode, isExpanded);
                newList.addAll(items);
            }

            if (element instanceof NodeFooterImp) {
                BaseNode footerNode = ((NodeFooterImp) element).footerNode;
                if (footerNode != null) {
                    newList.add(footerNode);
                }
            }
        }
        return newList;
    }

    private int collapse(@IntRange(from = 0) int position, boolean isChangeChildCollapse,
                         boolean animate, boolean notify, Object parentPayload) {
        BaseNode node = this.getData().get(position);

        if (node instanceof BaseExpandNode && ((BaseExpandNode) node).isExpanded) {
            int adapterPosition = position + getHeaderLayoutCount();

            ((BaseExpandNode) node).isExpanded = false;
            if (node.childNode == null || node.childNode.isEmpty()) {
                notifyItemChanged(adapterPosition, parentPayload);
                return 0;
            }

            List<BaseNode> items = flatData(node.childNode, isChangeChildCollapse ? false : null);
            int size = items.size();
            this.getData().removeAll(items);
            if (notify) {
                if (animate) {
                    notifyItemChanged(adapterPosition, parentPayload);
                    notifyItemRangeRemoved(adapterPosition + 1, size);
                } else {
                    notifyDataSetChanged();
                }
            }
            return size;
        }
        return 0;
    }

    private int expand(@IntRange(from = 0) int position, boolean isChangeChildExpand,
                       boolean animate, boolean notify, Object parentPayload) {
        BaseNode node = this.getData().get(position);

        if (node instanceof BaseExpandNode && !((BaseExpandNode) node).isExpanded) {
            int adapterPosition = position + getHeaderLayoutCount();

            ((BaseExpandNode) node).isExpanded = true;
            if (node.childNode == null || node.childNode.isEmpty()) {
                notifyItemChanged(adapterPosition, parentPayload);
                return 0;
            }

            List<BaseNode> items = flatData(node.childNode, isChangeChildExpand ? true : null);
            int size = items.size();
            this.getData().addAll(position + 1, items);
            if (notify) {
                if (animate) {
                    notifyItemChanged(adapterPosition, parentPayload);
                    notifyItemRangeInserted(adapterPosition + 1, size);
                } else {
                    notifyDataSetChanged();
                }
            }
            return size;
        }
        return 0;
    }

    public int collapse(@IntRange(from = 0) int position, boolean animate, boolean notify, Object parentPayload) {
        return collapse(position, false, animate, notify, parentPayload);
    }

    public int expand(@IntRange(from = 0) int position, boolean animate, boolean notify, Object parentPayload) {
        return expand(position, false, animate, notify, parentPayload);
    }

    public int expandOrCollapse(@IntRange(from = 0) int position, boolean animate, boolean notify, Object parentPayload) {
        BaseNode node = this.getData().get(position);
        if (node instanceof BaseExpandNode) {
            return ((BaseExpandNode) node).isExpanded ?
                    collapse(position, false, animate, notify, parentPayload) :
                    expand(position, false, animate, notify, parentPayload);
        }
        return 0;
    }

    public int expandAndChild(@IntRange(from = 0) int position, boolean animate, boolean notify, Object parentPayload) {
        return expand(position, true, animate, notify, parentPayload);
    }

    public int collapseAndChild(@IntRange(from = 0) int position, boolean animate, boolean notify, Object parentPayload) {
        return collapse(position, true, animate, notify, parentPayload);
    }

    public void expandAndCollapseOther(@IntRange(from = 0) int position, boolean isExpandedChild,
                                       boolean isCollapseChild, boolean animate, boolean notify,
                                       Object expandPayload, Object collapsePayload) {
        int expandCount = expand(position, isExpandedChild, animate, notify, expandPayload);
        if (expandCount == 0) {
            return;
        }

        int parentPosition = findParentNode(position);

        int firstPosition = (parentPosition == -1) ? 0 : parentPosition + 1;

        int newPosition = position;

        int beforeAllSize = position - firstPosition;

        if (beforeAllSize > 0) {
            int i = firstPosition;
            do {
                int collapseSize = collapse(i, isCollapseChild, animate, notify, collapsePayload);
                i++;
                newPosition -= collapseSize;
            } while (i < newPosition);
        }

        int lastPosition = (parentPosition == -1) ?
                this.getData().size() - 1 :
                parentPosition + (this.getData().get(parentPosition).childNode != null ?
                        this.getData().get(parentPosition).childNode.size() : 0) + expandCount;

        if ((newPosition + expandCount) < lastPosition) {
            int i = newPosition + expandCount + 1;
            while (i <= lastPosition) {
                int collapseSize = collapse(i, isCollapseChild, animate, notify, collapsePayload);
                i++;
                lastPosition -= collapseSize;
            }
        }
    }

    public int findParentNode(BaseNode node) {
        int pos = this.getData().indexOf(node);
        if (pos == -1 || pos == 0) {
            return -1;
        }

        for (int i = pos - 1; i >= 0; i--) {
            BaseNode tempNode = this.getData().get(i);
            if (tempNode.childNode != null && tempNode.childNode.contains(node)) {
                return i;
            }
        }
        return -1;
    }

    public int findParentNode(@IntRange(from = 0) int position) {
        if (position == 0) {
            return -1;
        }
        BaseNode node = this.getData().get(position);
        for (int i = position - 1; i >= 0; i--) {
            BaseNode tempNode = this.getData().get(i);
            if (tempNode.childNode != null && tempNode.childNode.contains(node)) {
                return i;
            }
        }
        return -1;
    }
}
