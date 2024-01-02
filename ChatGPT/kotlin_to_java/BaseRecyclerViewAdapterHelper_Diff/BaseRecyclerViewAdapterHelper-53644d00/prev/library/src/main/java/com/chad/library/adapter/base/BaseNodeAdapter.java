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
        super.setNewData(flatData(data != null ? data : new ArrayList<>()));
    }

    @Override
    public void addData(int position, BaseNode data) {
        addData(position, new ArrayList<>(Collections.singletonList(data)));
    }

    @Override
    public void addData(BaseNode data) {
        addData(new ArrayList<>(Collections.singletonList(data)));
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
        List<BaseNode> newFlatData = flatData(new ArrayList<>(Collections.singletonList(data)));
        this.getData().addAll(index, newFlatData);
        notifyItemRangeChanged(index + getHeaderLayoutCount(), Math.max(removeCount, newFlatData.size));
    }

    @Override
    public void replaceData(Collection<BaseNode> newData) {
        if (newData != this.getData()) {
            super.replaceData(flatData(newData));
        }
    }

    @Override
    public void setDiffNewData(List<BaseNode> newData) {
        if (hasEmptyView()) {
            setNewData(newData);
            return;
        }
        super.setDiffNewData(flatData(newData != null ? newData : new ArrayList<>()));
    }

    @Override
    public void setDiffNewData(DiffUtil.DiffResult diffResult, List<BaseNode> newData) {
        if (hasEmptyView()) {
            setNewData(newData);
            return;
        }
        super.setDiffNewData(diffResult, flatData(newData != null ? newData : new ArrayList<>()));
    }

    private int removeAt(int position) {
        if (position >= getData().size()) {
            return 0;
        }

        int removeCount = 0;
        BaseNode node = getData().get(position);

        if (node.getChildNode() != null && !node.getChildNode().isEmpty()) {
            if (node instanceof BaseExpandNode) {
                if (((BaseExpandNode) node).isExpanded()) {
                    List<BaseNode> items = flatData(((BaseExpandNode) node).getChildNode());
                    getData().removeAll(items);
                    removeCount = items.size();
                }
            } else {
                List<BaseNode> items = flatData(node.getChildNode());
                getData().removeAll(items);
                removeCount = items.size();
            }
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

        if (node.getChildNode() != null && !node.getChildNode().isEmpty()) {
            if (node instanceof BaseExpandNode) {
                if (((BaseExpandNode) node).isExpanded()) {
                    List<BaseNode> items = flatData(((BaseExpandNode) node).getChildNode());
                    getData().removeAll(items);
                    removeCount = items.size();
                }
            } else {
                List<BaseNode> items = flatData(node.getChildNode());
                getData().removeAll(items);
                removeCount = items.size();
            }
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
        List<BaseNode> childNodes = parentNode.getChildNode();
        if (childNodes != null) {
            if (parentNode instanceof BaseExpandNode && !((BaseExpandNode) parentNode).isExpanded()) {
                childNodes.remove(childNode);
                return;
            }
            remove(childNode);

            childNodes.remove(childNode);
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
        List<BaseNode> childNode = parentNode.getChildNode();
        if (childNode != null) {
            if (parentNode instanceof BaseExpandNode && !((BaseExpandNode) parentNode).isExpanded()) {
                childNode.clear();
                childNode.addAll(newData);
                return;
            }

            int parentIndex = getData().indexOf(parentNode);
            int removeCount = removeChildAt(parentIndex);
            notifyItemRangeRemoved(parentIndex + 1 + getHeaderLayoutCount(), removeCount);

            childNode.clear();
            childNode.addAll(newData);

            List<BaseNode> newFlatData = flatData(newData);
            getData().addAll(parentIndex + 1, newFlatData);

            notifyItemRangeInserted(parentIndex + 1 + getHeaderLayoutCount(), newFlatData.size);
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
                if (isExpanded == null || ((BaseExpandNode) element).isExpanded()) {
                    List<BaseNode> items = flatData(((BaseExpandNode) element).getChildNode(), isExpanded);
                    newList.addAll(items);
                }
                if (isExpanded != null) {
                    ((BaseExpandNode) element).setExpanded(isExpanded);
                }
            } else {
                List<BaseNode> items = flatData(element.getChildNode(), isExpanded);
                newList.addAll(items);
            }

            if (element instanceof NodeFooterImp) {
                BaseNode footerNode = ((NodeFooterImp) element).getFooterNode();
                if (footerNode != null) {
                    newList.add(footerNode);
                }
            }
        }
        return newList;
    }

    private int collapse(@IntRange(from = 0) int position, boolean isChangeChildCollapse,
                         boolean animate, boolean notify) {
        BaseNode node = getData().get(position);

        if (node instanceof BaseExpandNode && ((BaseExpandNode) node).isExpanded()) {
            int adapterPosition = position + getHeaderLayoutCount();

            ((BaseExpandNode) node).setExpanded(false);
            if (node.getChildNode() == null || node.getChildNode().isEmpty()) {
                notifyItemChanged(adapterPosition);
                return 0;
            }
            List<BaseNode> items = flatData(((BaseExpandNode) node).getChildNode(), isChangeChildCollapse ? false : null);
            int size = items.size();
            getData().removeAll(items);
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
    private int expand(@IntRange(from = 0) int position,
                       boolean isChangeChildExpand,
                       boolean animate,
                       boolean notify) {
        BaseNode node = getData().get(position);

        if (node instanceof BaseExpandNode && !((BaseExpandNode) node).isExpanded()) {
            int adapterPosition = position + getHeaderLayoutCount();

            ((BaseExpandNode) node).setExpanded(true);
            if (((BaseExpandNode) node).getChildNode() == null || ((BaseExpandNode) node).getChildNode().isEmpty()) {
                notifyItemChanged(adapterPosition);
                return 0;
            }
            List<BaseNode> items = flatData(((BaseExpandNode) node).getChildNode(), isChangeChildExpand ? true : null);
            int size = items.size();
            getData().addAll(position + 1, items);
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

    @Override
    public int collapse(@IntRange(from = 0) int position, boolean animate, boolean notify) {
        return collapse(position, false, animate, notify);
    }

    @Override
    public int expand(@IntRange(from = 0) int position, boolean animate, boolean notify) {
        return expand(position, false, animate, notify);
    }

    public int expandOrCollapse(@IntRange(from = 0) int position, boolean animate, boolean notify) {
        BaseNode node = getData().get(position);
        if (node instanceof BaseExpandNode) {
            return ((BaseExpandNode) node).isExpanded() ?
                    collapse(position, false, animate, notify) :
                    expand(position, false, animate, notify);
        }
        return 0;
    }

    public int expandAndChild(@IntRange(from = 0) int position, boolean animate, boolean notify) {
        return expand(position, true, animate, notify);
    }

    public int collapseAndChild(@IntRange(from = 0) int position, boolean animate, boolean notify) {
        return collapse(position, true, animate, notify);
    }

    public void expandAndCollapseOther(@IntRange(from = 0) int position,
                                       boolean isExpandedChild,
                                       boolean isCollapseChild,
                                       boolean animate,
                                       boolean notify) {
        int expandCount = expand(position, isExpandedChild, animate, notify);
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
                int collapseSize = collapse(i, isCollapseChild, animate, notify);
                i++;
                newPosition -= collapseSize;
            } while (i < newPosition);
        }

        int lastPosition = (parentPosition == -1) ?
                getData().size() - 1 :
                parentPosition + ((BaseExpandNode) getData().get(parentPosition)).getChildNode().size() + expandCount;

        if ((newPosition + expandCount) < lastPosition) {
            int i = newPosition + expandCount + 1;
            while (i <= lastPosition) {
                int collapseSize = collapse(i, isCollapseChild, animate, notify);
                i++;
                lastPosition -= collapseSize;
            }
        }
    }

    public int findParentNode(BaseNode node) {
        int pos = getData().indexOf(node);
        if (pos == -1 || pos == 0) {
            return -1;
        }

        for (int i = pos - 1; i >= 0; i--) {
            BaseNode tempNode = getData().get(i);
            if (tempNode.getChildNode() != null && tempNode.getChildNode().contains(node)) {
                return i;
            }
        }
        return -1;
    }

    public int findParentNode(@IntRange(from = 0) int position) {
        if (position == 0) {
            return -1;
        }
        BaseNode node = getData().get(position);
        for (int i = position - 1; i >= 0; i--) {
            BaseNode tempNode = getData().get(i);
            if (tempNode.getChildNode() != null && tempNode.getChildNode().contains(node)) {
                return i;
            }
        }
        return -1;
    }
}
