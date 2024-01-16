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
import com.chad.library.adapter.base.animation.AnimationType;
import com.chad.library.adapter.base.animation.BaseAnimation;
import com.chad.library.adapter.base.module.BaseDraggableModule;
import com.chad.library.adapter.base.viewholder.EmptyLayoutVH;

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
    private BaseDraggableModule mDraggableModule;
    private RecyclerView _recyclerView;

    public BaseQuickAdapter(List<T> items) {
        this.items = items;
    }

    public BaseQuickAdapter() {
        this(new ArrayList<T>());
    }
    protected void setStaggeredGridFullSpan(RecyclerView.ViewHolder holder) {
        ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
        if (layoutParams instanceof StaggeredGridLayoutManager.LayoutParams) {
            StaggeredGridLayoutManager.LayoutParams staggeredGridLayoutParams = (StaggeredGridLayoutManager.LayoutParams) layoutParams;
            staggeredGridLayoutParams.setFullSpan(true);
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
        switch (animationType) {
            case AnimationType.AlphaIn:
                itemAnimation = new AlphaInAnimation();
                break;
            case AnimationType.ScaleIn:
                itemAnimation = new ScaleInAnimation();
                break;
            case AnimationType.SlideInBottom:
                itemAnimation = new SlideInBottomAnimation();
                break;
            case AnimationType.SlideInLeft:
                itemAnimation = new SlideInLeftAnimation();
                break;
            case AnimationType.SlideInRight:
                itemAnimation = new SlideInRightAnimation();
                break;
        }
    }

    public void submitList(List<T> list) {
        if (list == items) return;

        mLastPosition = -1;

        List<T> newList = list != null ? list : Collections.emptyList();

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

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
        } else {
            onBindViewHolder(holder, position, payloads.get(0));
        }
    }

    protected abstract void onBindViewHolder(VH holder, int position, Object payload);

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

    private List<T> getMutableItems() {
        if (items instanceof MutableList) {
            return (MutableList<T>) items;
        } else {
            return new ArrayList<>(items);
        }
    }
    public RecyclerView getRecyclerView() {
        return _recyclerView;
    }

    public Context getContext() {
        return _recyclerView.getContext();
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
        this.animationEnable = true;
        this.itemAnimation = itemAnimation;
    }

    @Override
    public int getItemCount() {
        if (displayEmptyView()) {
            return 1;
        }
        return getItemCount(items);
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
        onBindViewHolder(holder, position, getItem(position));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, List<Object> payloads) {
        if (holder instanceof EmptyLayoutVH) {
            ((EmptyLayoutVH) holder).changeEmptyView(emptyView);
            return;
        }
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position, getItem(position));
            return;
        }
        onBindViewHolder(holder, position, getItem(position), payloads);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);

        if (isFullSpanItem(getItemViewType(holder.getBindingAdapterPosition()))) {
            setStaggeredGridFullSpan(holder);
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

        if (mDraggableModule != null) {
            mDraggableModule.attachToRecyclerView(recyclerView);
        }
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        _recyclerView = null;
    }

    protected abstract void onCreateViewHolder(
            Context context, ViewGroup parent, int viewType
    );

    protected abstract void onBindViewHolder(VH holder, int position, T item);

    protected abstract int getItemCount(List<T> items);

    protected abstract int getItemViewType(int position, List<T> list);

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

    public void addOnItemChildClickListener(@IntRes int id, OnItemChildClickListener<T> listener) {
        mOnItemChildClickArray.put(id, listener);
    }

    public void removeOnItemChildClickListener(@IntRes int id) {
        mOnItemChildClickArray.remove(id);
    }

    public void addOnItemChildLongClickListener(@IntRes int id, OnItemChildLongClickListener<T> listener) {
        mOnItemChildLongClickArray.put(id, listener);
    }

    public void removeOnItemChildLongClickListener(@IntRes int id) {
        mOnItemChildLongClickArray.remove(id);
    }

    public void addOnViewAttachStateChangeListener(OnViewAttachStateChangeListener listener) {
        if (onViewAttachStateChangeListeners == null) {
            onViewAttachStateChangeListeners = new ArrayList<>();
        }
        onViewAttachStateChangeListeners.add(listener);
    }

    public void removeOnViewAttachStateChangeListener(OnViewAttachStateChangeListener listener) {
        if (onViewAttachStateChangeListeners != null) {
            onViewAttachStateChangeListeners.remove(listener);
        }
    }

    public void clearOnViewAttachStateChangeListener() {
        if (onViewAttachStateChangeListeners != null) {
            onViewAttachStateChangeListeners.clear();
        }
    }

    enum class AnimationType {
    AlphaIn,
    ScaleIn,
    SlideInBottom,
    SlideInLeft,
    SlideInRight
    }

    public interface OnItemClickListener {
    void onItemClick(BaseQuickAdapter<T, ?> adapter, View view, int position);
    }

    public interface OnItemLongClickListener {
    boolean onItemLongClick(BaseQuickAdapter<T, ?> adapter, View view, int position);
    }

    public interface OnItemChildClickListener {
    void onItemChildClick(BaseQuickAdapter<T, ?> adapter, View view, int position);
    }

    public interface OnItemChildLongClickListener {
    boolean onItemChildLongClick(BaseQuickAdapter<T, ?> adapter, View view, int position);
    }

    public interface OnViewAttachStateChangeListener {
    void onViewAttachedToWindow(RecyclerView.ViewHolder holder);

    void onViewDetachedFromWindow(RecyclerView.ViewHolder holder);
    }

public static final int EMPTY_VIEW = 0x10000555;

public static final int EMPTY_PAYLOAD = 0;
}