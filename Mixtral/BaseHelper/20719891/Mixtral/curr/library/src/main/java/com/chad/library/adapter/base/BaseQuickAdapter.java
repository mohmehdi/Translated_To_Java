package com.chad.library.adapter.base;

import android.animation.Animator;
import android.content.Context;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.*;
import androidx.annotation.IntRange;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class BaseQuickAdapter<T, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<T> items;
    private int mLastPosition = -1;
    private OnItemClickListener<T> mOnItemClickListener;
    private OnItemLongClickListener<T> mOnItemLongClickListener;
    private SparseArray<OnItemChildClickListener<T>> mOnItemChildClickArray;
    private SparseArray<OnItemChildLongClickListener<T>> mOnItemChildLongClickArray;
    private List<OnViewAttachStateChangeListener> onViewAttachStateChangeListeners;

    private RecyclerView _recyclerView;

    public RecyclerView getRecyclerView() {
        checkNotNull(_recyclerView);
        return _recyclerView;
    }

    public Context getContext() {
        return getRecyclerView().getContext();
    }

    public BaseQuickAdapter(List<T> items) {
        this.items = items;
    }

    protected void asStaggeredGridFullSpan(RecyclerView.ViewHolder holder) {
        RecyclerView.LayoutParams layoutParams = holder.itemView.getLayoutParams();
        if (layoutParams instanceof StaggeredGridLayoutManager.LayoutParams) {
            StaggeredGridLayoutManager.LayoutParams staggeredLayoutParams = (StaggeredGridLayoutManager.LayoutParams) layoutParams;
            staggeredLayoutParams.setFullSpan(true);
        }
    }


    public boolean isFullSpanItem(int itemType) {
        return itemType == EMPTY_VIEW;
    }

    public T getItem(@IntRange(from = 0) int position) {
        return items.get(position);
    }

    public int getItemPosition(T item) {
        return items.indexOf(item);
    }

    public void setEmptyViewLayout(Context context, @LayoutRes int layoutResId) {
        emptyView = LayoutInflater.from(context).inflate(layoutResId, new FrameLayout(context), false);
    }

    public boolean displayEmptyView(List<T> list) {
        if (emptyView == null || !isEmptyViewEnable) return false;
        return list.isEmpty();
    }

    private void runAnimator(RecyclerView.ViewHolder holder) {
        if (animationEnable) {
            if (!isAnimationFirstOnly || holder.getLayoutPosition() > mLastPosition) {
                ItemAnimator animation = itemAnimation != null ? itemAnimation : new AlphaInAnimation();
                animation.animator(holder.itemView).apply {
                    startItemAnimator(this, holder);
                };
                mLastPosition = holder.getLayoutPosition();
            }
        }
    }

    protected abstract void startItemAnimator(Animator anim, RecyclerView.ViewHolder holder);

    public void setItemAnimation(AnimationType animationType) {
        itemAnimation = switch (animationType) {
            case AnimationType.AlphaIn -> new AlphaInAnimation();
            case AnimationType.ScaleIn -> new ScaleInAnimation();
            case AnimationType.SlideInBottom -> new SlideInBottomAnimation();
            case AnimationType.SlideInLeft -> new SlideInLeftAnimation();
            case AnimationType.SlideInRight -> new SlideInRightAnimation();
        };
    }

    public void submitList(List<T> list) {
        if (list == items) return;

        mLastPosition = -1;

        List<T> newList = list != null ? list : new ArrayList<>();

        boolean oldDisplayEmptyLayout = displayEmptyView();
        boolean newDisplayEmptyLayout = displayEmptyView(newList);

        if (oldDisplayEmptyLayout && !newDisplayEmptyLayout) {
            items = newList;
            notifyItemRemoved(0);
            notifyItemRangeInserted(0, newList.size());
        } else if (newDisplayEmptyLayout && !oldDisplayEmptyLayout) {
            notifyItemRangeRemoved(0, items.size());
            items = newList;
            notifyItemInserted(0);
        } else if (oldDisplayEmptyLayout && newDisplayEmptyLayout) {
            items = newList;
            notifyItemChanged(0, EMPTY_PAYLOAD);
        } else {
            items = newList;
            notifyDataSetChanged();
        }
    }

    public void set(@IntRange(from = 0) int position, T data) {
        if (position >= items.size()) {
            throw new IndexOutOfBoundsException("position: " + position + ". size:" + items.size());
        }
        List<T> mutableItems = getMutableItems();
        mutableItems.set(position, data);
        notifyItemChanged(position);
    }

    public void add(@IntRange(from = 0) int position, T data) {
        if (position > items.size() || position < 0) {
            throw new IndexOutOfBoundsException("position: " + position + ". size:" + items.size());
        }

        if (displayEmptyView()) {
            notifyItemRemoved(0);
        }
        List<T> mutableItems = getMutableItems();
        mutableItems.add(position, data);
        notifyItemInserted(position);
    }

    public void add(T data) {
        if (displayEmptyView()) {
            notifyItemRemoved(0);
        }
        List<T> mutableItems = getMutableItems();
        mutableItems.add(data);
        notifyItemInserted(items.size() - 1);
    }

    public void addAll(@IntRange(from = 0) int position, Collection<T> newCollection) {
        if (position > items.size() || position < 0) {
            throw new IndexOutOfBoundsException("position: " + position + ". size:" + items.size());
        }

        if (displayEmptyView()) {
            notifyItemRemoved(0);
        }
        List<T> mutableItems = getMutableItems();
        mutableItems.addAll(position, newCollection);
        notifyItemRangeInserted(position, newCollection.size());
    }

    public void addAll(Collection<T> newCollection) {
        if (displayEmptyView()) {
            notifyItemRemoved(0);
        }

        int oldSize = items.size();
        List<T> mutableItems = getMutableItems();
        mutableItems.addAll(newCollection);
        notifyItemRangeInserted(oldSize, newCollection.size());
    }

    public void removeAt(@IntRange(from = 0) int position) {
        if (position >= items.size()) {
            throw new IndexOutOfBoundsException("position: " + position + ". size:" + items.size());
        }
        List<T> mutableItems = getMutableItems();
        mutableItems.remove(position);
        notifyItemRemoved(position);

        if (displayEmptyView()) {
            notifyItemInserted(0);
        }
    }

    public void remove(T data) {
        int index = items.indexOf(data);
        if (index == -1) return;
        removeAt(index);
    }

    public void swap(int fromPosition, int toPosition) {
        int size = items.size();
        if (fromPosition in 0 until size && toPosition in 0 until size) {
            Collections.swap(items, fromPosition, toPosition);
            notifyItemMoved(fromPosition, toPosition);
        }
    }

    private List<T> getMutableItems() {
        if (items instanceof ArrayList) {
            return (ArrayList<T>) items;
        } else if (items instanceof MutableList) {
            return (MutableList<T>) items;
        } else {
            List<T> mutableItems = new ArrayList<>(items);
            items = mutableItems;
            return mutableItems;
        }
    }





    public boolean isEmptyViewEnable() {
        return isEmptyViewEnable;
    }

    public void setEmptyViewEnable(boolean emptyViewEnable) {
        isEmptyViewEnable = emptyViewEnable;
    }

    public View getEmptyView() {
        return emptyView;
    }

    public void setEmptyView(View emptyView) {
        this.emptyView = emptyView;
    }

    public boolean isAnimationEnable() {
        return animationEnable;
    }

    public void setAnimationEnable(boolean animationEnable) {
        this.animationEnable = animationEnable;
    }

    public boolean isAnimationFirstOnly() {
        return isAnimationFirstOnly;
    }

    public void setAnimationFirstOnly(boolean animationFirstOnly) {
        isAnimationFirstOnly = animationFirstOnly;
    }

    public ItemAnimator getItemAnimation() {
        return itemAnimation;
    }

    public void setItemAnimation(ItemAnimator itemAnimation) {
        animationEnable = true;
        this.itemAnimation = itemAnimation;
    }

    protected abstract VH onCreateViewHolder(Context context, ViewGroup parent, int viewType);

    protected abstract void onBindViewHolder(VH holder, int position, T item);

    protected void onBindViewHolder(VH holder, int position, T item, List<Object> payloads) {
        onBindViewHolder(holder, position, item);
    }

    protected int getItemCount(List<T> list) {
        return list.size();
    }

    protected int getItemViewType(int position, List<T> list) {
        return 0;
    }

    @Override
    public int getItemCount() {
        return displayEmptyView() ? 1 : getItemCount(items);
    }

    @Override
    public int getItemViewType(int position) {
        if (displayEmptyView()) {
            return EMPTY_VIEW;
        }
        return getItemViewType(position, items);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == EMPTY_VIEW) {
            return new EmptyLayoutVH(new FrameLayout(parent.getContext()));
        }

        VH viewHolder = onCreateViewHolder(parent.getContext(), parent, viewType);
        bindViewClickListener(viewHolder, viewType);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof EmptyLayoutVH) {
            ((EmptyLayoutVH) holder).changeEmptyView(emptyView);
            return;
        }

        onBindViewHolder((VH) holder, position, getItem(position));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, List<Object> payloads) {
        if (holder instanceof EmptyLayoutVH) {
            ((EmptyLayoutVH) holder).changeEmptyView(emptyView);
            return;
        }

        if (payloads.isEmpty()) {
            onBindViewHolder((VH) holder, position, getItem(position));
            return;
        }

        onBindViewHolder((VH) holder, position, getItem(position), payloads);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);

        if (isFullSpanItem(getItemViewType(holder.getBindingAdapterPosition()))) {
            holder.asStaggeredGridFullSpan();
        } else {
            runAnimator(holder);
        }

        if (onViewAttachStateChangeListeners != null) {
            for (OnViewAttachStateChangeListener listener : onViewAttachStateChangeListeners) {
                listener.onViewAttachedToWindow(holder);
            }
        }
    }

    @Override
    public void onViewDetachedFromWindow(RecyclerView.ViewHolder holder) {
        if (onViewAttachStateChangeListeners != null) {
            for (OnViewAttachStateChangeListener listener : onViewAttachStateChangeListeners) {
                listener.onViewDetachedFromWindow(holder);
            }
        }
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        _recyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        _recyclerView = null;
    }

    protected void bindViewClickListener(VH viewHolder, int viewType) {
        if (mOnItemClickListener != null) {
            viewHolder.itemView.setOnClickListener(v -> {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                onItemClick(v, position);
            });
        }
        if (mOnItemLongClickListener != null) {
            viewHolder.itemView.setOnLongClickListener(v -> {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return false;
                }
                return onItemLongClick(v, position);
            });
        }

        for (int i = 0; i < mOnItemChildClickArray.size(); i++) {
            int id = mOnItemChildClickArray.keyAt(i);

            viewHolder.itemView.findViewById(id).setOnClickListener(v -> {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                onItemChildClick(v, position);
            });
        }

        for (int i = 0; i < mOnItemChildLongClickArray.size(); i++) {
            int id = mOnItemChildLongClickArray.keyAt(i);

            viewHolder.itemView.findViewById(id).setOnLongClickListener(v -> {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return false;
                }
                return onItemChildLongClick(v, position);
            });
        }
    }

    protected void onItemClick(View v, int position) {
        mOnItemClickListener.onItemClick(this, v, position);
    }

    protected boolean onItemLongClick(View v, int position) {
        return mOnItemLongClickListener.onItemLongClick(this, v, position);
    }

    protected void onItemChildClick(View v, int position) {
        mOnItemChildClickArray.get(v.getId()).onItemChildClick(this, v, position);
    }

    protected boolean onItemChildLongClick(View v, int position) {
        return mOnItemChildLongClickArray.get(v.getId()).onItemChildLongClick(this, v, position);
    }

    public void setOnItemClickListener(OnItemClickListener<T> listener) {
        mOnItemClickListener = listener;
    }

    public OnItemClickListener<T> getOnItemClickListener() {
        return mOnItemClickListener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener<T> listener) {
        mOnItemLongClickListener = listener;
    }

    public OnItemLongClickListener<T> getOnItemLongClickListener() {
        return mOnItemLongClickListener;
    }

    public void addOnItemChildClickListener(@IdRes int id, OnItemChildClickListener<T> listener) {
        mOnItemChildClickArray.put(id, listener);
    }

    public void removeOnItemChildClickListener(@IdRes int id) {
        mOnItemChildClickArray.remove(id);
    }

    public void addOnItemChildLongClickListener(@IdRes int id, OnItemChildLongClickListener<T> listener) {
        mOnItemChildLongClickArray.put(id, listener);
    }

    public void removeOnItemChildLongClickListener(@IdRes int id) {
        mOnItemChildLongClickArray.remove(id);
    }

    public void addOnViewAttachStateChangeListener(OnViewAttachStateChangeListener listener) {
        if (onViewAttachStateChangeListeners == null) {
            onViewAttachStateChangeListeners = new ArrayList<>();
        }
        onViewAttachStateChangeListeners.add(listener);
    }

    public void removeOnViewAttachStateChangeListener(OnViewAttachStateChangeListener listener) {
        onViewAttachStateChangeListeners.remove(listener);
    }

    public void clearOnViewAttachStateChangeListener() {
        onViewAttachStateChangeListeners.clear();
    }

    enum AnimationType {
        AlphaIn, ScaleIn, SlideInBottom, SlideInLeft, SlideInRight
    }

    public interface OnViewAttachStateChangeListener {

        void onViewAttachedToWindow(RecyclerView.ViewHolder holder);

        void onViewDetachedFromWindow(RecyclerView.ViewHolder holder);
    }

    public static final int EMPTY_VIEW = 0x10000555;

    public static final int EMPTY_PAYLOAD = 0;
}

public typealias OnItemClickListener<T> = (adapter, view, position) -> Object;

public typealias OnItemLongClickListener<T> = (adapter, view, position) -> boolean;

public typealias OnItemChildClickListener<T> = (adapter, view, position) -> Object;

public typealias OnItemChildLongClickListener<T> = (adapter, view, position) -> boolean;
