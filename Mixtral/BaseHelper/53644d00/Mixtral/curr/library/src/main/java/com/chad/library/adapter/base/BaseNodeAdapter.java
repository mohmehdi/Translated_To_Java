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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class BaseNodeAdapter extends BaseProviderMultiAdapter<BaseNode> {

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
        if (data == this.data) {
            return;
        }
        super.setNewData(flatData(data));
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
        this.data.addAll(index, newFlatData);

        if (removeCount == newFlatData.size()) {
            notifyItemRangeChanged(index + getHeaderLayoutCount(), removeCount);
        } else {
            notifyItemRangeRemoved(index + getHeaderLayoutCount(), removeCount);
            notifyItemRangeInserted(index + getHeaderLayoutCount(), newFlatData.size());

        }
    }

    @Override
    public void replaceData(Collection<BaseNode> newData) {
        if (newData != this.data) {
            super.replaceData(flatData(newData));
        }
    }

    @Override
    public void setDiffNewData(List<BaseNode> newData) {
        if (hasEmptyView()) {
            setNewData(newData);
            return;
        }
        super.setDiffNewData(flatData(newData));
    }

    @Override
    public void setDiffNewData(DiffUtil.DiffResult diffResult, List<BaseNode> newData) {
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

        BaseNode node = this.data.get(position);
        if (node.getChildNode() != null && !node.getChildNode().isEmpty()) {
            if (node instanceof BaseExpandNode) {
                if (((BaseExpandNode) node).isExpanded()) {
                    List<BaseNode> items = flatData(node.getChildNode());
                    this.data.removeAll(items);
                    removeCount = items.size();
                }
            } else {
                List<BaseNode> items = flatData(node.getChildNode());
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

        BaseNode node = this.data.get(position);
        if (node.getChildNode() != null && !node.getChildNode().isEmpty()) {
            if (node instanceof BaseExpandNode) {
                if (((BaseExpandNode) node).isExpanded()) {
                    List<BaseNode> items = flatData(node.getChildNode());
                    this.data.removeAll(items);
                    removeCount = items.size();
                }
            } else {
                List<BaseNode> items = flatData(node.getChildNode());
                this.data.removeAll(items);
                removeCount = items.size();
            }
        }
        return removeCount;
    }

    public void nodeAddData(BaseNode parentNode, BaseNode data) {
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

    public void nodeAddData(BaseNode parentNode, int childIndex, BaseNode data) {
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

    public void nodeAddData(BaseNode parentNode, int childIndex, Collection<BaseNode> newData) {
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

    public void nodeRemoveData(BaseNode parentNode, int childIndex) {
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

    public void nodeRemoveData(BaseNode parentNode, BaseNode childNode) {
        if (parentNode.getChildNode() != null) {
            if (parentNode instanceof BaseExpandNode && !((BaseExpandNode) parentNode).isExpanded()) {
                parentNode.getChildNode().remove(childNode);
                return;
            }
            remove(childNode);

            parentNode.getChildNode().remove(childNode);
        }
    }

    public void nodeSetData(BaseNode parentNode, int childIndex, BaseNode data) {
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

    public void nodeReplaceChildData(BaseNode parentNode, Collection<BaseNode> newData) {
        if (parentNode.getChildNode() != null) {
            if (parentNode instanceof BaseExpandNode && !((BaseExpandNode) parentNode).isExpanded()) {
                parentNode.getChildNode().clear();
                parentNode.getChildNode().addAll(newData);
                return;
            }

            int parentIndex = this.data.indexOf(parentNode);
            int removeCount = removeChildAt(parentIndex);

            parentNode.getChildNode().clear();
            parentNode.getChildNode().addAll(newData);

            List<BaseNode> newFlatData = flatData(newData);
            this.data.addAll(parentIndex + 1, newFlatData);

            int positionStart = parentIndex + 1 + getHeaderLayoutCount();
            if (removeCount == newFlatData.size()) {
                notifyItemRangeChanged(positionStart, removeCount);
            } else {
                notifyItemRangeRemoved(positionStart, removeCount);
                notifyItemRangeInserted(positionStart, newFlatData.size());
            }
        }
    }

    private List<BaseNode> flatData(Collection<BaseNode> list, Boolean isExpanded) {
        List<BaseNode> newList = new ArrayList<>();

        for (BaseNode element : list) {
            newList.add(element);

            if (element instanceof BaseExpandNode) {
                if (isExpanded == true || ((BaseExpandNode) element).isExpanded()) {
                    List<BaseNode> childNode = element.getChildNode();
                    if (childNode != null && !childNode.isEmpty()) {
                        List<BaseNode> items = flatData(childNode, isExpanded);
                        newList.addAll(items);
                    }
                }
                if (isExpanded != null) {
                    ((BaseExpandNode) element).setExpanded(isExpanded);
                }
            } else {
                List<BaseNode> childNode = element.getChildNode();
                if (childNode != null && !childNode.isEmpty()) {
                    List<BaseNode> items = flatData(childNode, isExpanded);
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

    private List<BaseNode> flatData(Collection<BaseNode> list) {
        return flatData(list, null);
    }

    private int collapse(int position, boolean isChangeChildCollapse, boolean animate, boolean notify, Object parentPayload) {
        BaseNode node = this.data.get(position);

        if (node instanceof BaseExpandNode && ((BaseExpandNode) node).isExpanded()) {
            int adapterPosition = position + getHeaderLayoutCount();

            ((BaseExpandNode) node).setExpanded(false);
            if (node.getChildNode() == null || node.getChildNode().isEmpty()) {
                if (notify) {
                    notifyItemChanged(adapterPosition, parentPayload);
                }
                return 0;
            }
            List<BaseNode> items = flatData(node.getChildNode(), isChangeChildCollapse);
            int size = items.size();
            this.data.removeAll(items);
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

    private int expand(int position, boolean isChangeChildExpand, boolean animate, boolean notify, Object parentPayload) {
        BaseNode node = this.data.get(position);

        if (node instanceof BaseExpandNode && !((BaseExpandNode) node).isExpanded()) {
            int adapterPosition = position + getHeaderLayoutCount();

            ((BaseExpandNode) node).setExpanded(true);
            if (node.getChildNode() == null || node.getChildNode().isEmpty()) {
                if (notify) {
                    notifyItemChanged(adapterPosition, parentPayload);
                }
                return 0;
            }
            List<BaseNode> items = flatData(node.getChildNode(), isChangeChildExpand);
            int size = items.size();
            this.data.addAll(position + 1, items);
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

    public int collapse(int position, boolean animate, boolean notify, Object parentPayload) {
        return collapse(position, false, animate, notify, parentPayload);
    }

    public int expand(int position, boolean animate, boolean notify, Object parentPayload) {
        return expand(position, false, animate, notify, parentPayload);
    }

    public int expandOrCollapse(int position, boolean animate, boolean notify, Object parentPayload) {
        BaseNode node = this.data.get(position);
        if (node instanceof BaseExpandNode) {
            return (node.isExpanded()) ? collapse(position, false, animate, notify, parentPayload) : expand(position, false, animate, notify, parentPayload);
        }
        return 0;
    }

    public int expandAndChild(int position, boolean animate, boolean notify, Object parentPayload) {
        return expand(position, true, animate, notify, parentPayload);
    }

    public int collapseAndChild(int position, boolean animate, boolean notify, Object parentPayload) {
        return collapse(position, true, animate, notify, parentPayload);
    }

    public void expandAndCollapseOther(int position, boolean isExpandedChild, boolean isCollapseChild, boolean animate, boolean notify, Object expandPayload, Object collapsePayload) {

        int expandCount = expand(position, isExpandedChild, animate, notify, expandPayload);
        if (expandCount == 0) {
            return;
        }

        int parentPosition = findParentNode(position);
        int firstPosition = (parentPosition == -1) ? 0 : parentPosition + 1;

        int newPosition = position;

        int beforeAllSize = position - firstPosition;

        if (beforeAllSize > 0) {
            for (int i = firstPosition; i < newPosition; i++) {
                int collapseSize = collapse(i, isCollapseChild, animate, notify, collapsePayload);
                newPosition -= collapseSize;
            }
        }

        int lastPosition = (parentPosition == -1) ? data.size() - 1 : (data.get(parentPosition).getChildNode() != null ? parentPosition + data.get(parentPosition).getChildNode().size() + expandCount : parentPosition + expandCount);

        if ((newPosition + expandCount) < lastPosition) {
            for (int i = newPosition + expandCount + 1; i <= lastPosition; i++) {
                int collapseSize = collapse(i, isCollapseChild, animate, notify, collapsePayload);
                lastPosition -= collapseSize;
            }

        }

    }

    public int findParentNode(BaseNode node) {
        int pos = this.data.indexOf(node);
        if (pos == -1 || pos == 0) {
            return -1;
        }

        for (int i = pos - 1; i >= 0; i--) {
            BaseNode tempNode = this.data.get(i);
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
        BaseNode node = this.data.get(position);
        for (int i = position - 1; i >= 0; i--) {
            BaseNode tempNode = this.data.get(i);
            if (tempNode.getChildNode() != null && tempNode.getChildNode().contains(node)) {
                return i;
            }
        }
        return -1;
    }
}