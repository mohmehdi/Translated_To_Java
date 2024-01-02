package com.chad.library.adapter.base;

import android.animation.Animator;
import android.content.Context;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.chad.library.adapter.base.animation.AlphaInAnimation;
import com.chad.library.adapter.base.animation.ItemAnimator;
import com.chad.library.adapter.base.animation.ScaleInAnimation;
import com.chad.library.adapter.base.animation.SlideInBottomAnimation;
import com.chad.library.adapter.base.animation.SlideInLeftAnimation;
import com.chad.library.adapter.base.animation.SlideInRightAnimation;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseQuickAdapter<T, VH extends RecyclerView.ViewHolder>
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private int mLastPosition = -1;
    private OnItemClickListener<T> mOnItemClickListener;
    private OnItemLongClickListener<T> mOnItemLongClickListener;
    private final SparseArray<OnItemChildClickListener<T>> mOnItemChildClickArray = new SparseArray<>(3);
    private final SparseArray<OnItemChildLongClickListener<T>> mOnItemChildLongClickArray = new SparseArray<>(3);
    private List<OnViewAttachStateChangeListener> onViewAttachStateChangeListeners;

    private RecyclerView _recyclerView;

    protected List<T> items = new ArrayList<>();

    private static final int EMPTY_VIEW = -1;
    private static final int EMPTY_PAYLOAD = 0;

    private boolean isEmptyViewEnable = false;
    private View emptyView;

    private boolean animationEnable = false;
    private boolean isAnimationFirstOnly = true;
    private ItemAnimator itemAnimation;

    public RecyclerView getRecyclerView() {
        return checkNotNull(_recyclerView, "Please get it after onAttachedToRecyclerView()");
    }

    public Context getContext() {
        return getRecyclerView().getContext();
    }

    public boolean isEmptyViewEnabled() {
        return isEmptyViewEnable;
    }

    public void setEmptyViewEnabled(boolean value) {
        boolean oldDisplayEmptyLayout = displayEmptyView();

        isEmptyViewEnable = value;

        boolean newDisplayEmptyLayout = displayEmptyView();

        if (oldDisplayEmptyLayout && !newDisplayEmptyLayout) {
            notifyItemRemoved(0);
        } else if (newDisplayEmptyLayout && !oldDisplayEmptyLayout) {
            notifyItemInserted(0);
        } else if (oldDisplayEmptyLayout && newDisplayEmptyLayout) {
            notifyItemChanged(0, EMPTY_PAYLOAD);
        }
    }

    public View getEmptyView() {
        return emptyView;
    }

    public void setEmptyView(View value) {
        boolean oldDisplayEmptyLayout = displayEmptyView();

        emptyView = value;

        boolean newDisplayEmptyLayout = displayEmptyView();

        if (oldDisplayEmptyLayout && !newDisplayEmptyLayout) {
            notifyItemRemoved(0);
        } else if (newDisplayEmptyLayout && !oldDisplayEmptyLayout) {
            notifyItemInserted(0);
        } else if (oldDisplayEmptyLayout && newDisplayEmptyLayout) {
            notifyItemChanged(0, EMPTY_PAYLOAD);
        }
    }

    public boolean isAnimationEnabled() {
        return animationEnable;
    }

    public void setAnimationEnabled(boolean value) {
        animationEnable = value;
    }

    public boolean isAnimationFirstOnly() {
        return isAnimationFirstOnly;
    }

    public void setAnimationFirstOnly(boolean value) {
        isAnimationFirstOnly = value;
    }

    public ItemAnimator getItemAnimation() {
        return itemAnimation;
    }

    public void setItemAnimation(ItemAnimator value) {
        animationEnable = true;
        itemAnimation = value;
    }

    protected abstract VH onCreateViewHolder(Context context, ViewGroup parent, int viewType);

    protected abstract void onBindViewHolder(VH holder, int position, T item);

    protected void onBindViewHolder(VH holder, int position, T item, List<Object> payloads) {
        onBindViewHolder(holder, position, item);
    }

    protected int getItemCount(List<T> items) {
        return items.size();
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
        return displayEmptyView() ? EMPTY_VIEW : getItemViewType(position, items);
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
    @CallSuper
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
    @CallSuper
    public void onViewDetachedFromWindow(RecyclerView.ViewHolder holder) {
        if (onViewAttachStateChangeListeners != null) {
            for (OnViewAttachStateChangeListener listener : onViewAttachStateChangeListeners) {
                listener.onViewDetachedFromWindow(holder);
            }
        }
    }

    @Override
    @CallSuper
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        _recyclerView = recyclerView;
    }

    @Override
    @CallSuper
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        _recyclerView = null;
    }

    protected void bindViewClickListener(VH viewHolder, int viewType) {
        if (mOnItemClickListener != null) {
            viewHolder.itemView.setOnClickListener(v -> {
                int position = viewHolder.getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(v, position);
                }
            });
        }

        if (mOnItemLongClickListener != null) {
            viewHolder.itemView.setOnLongClickListener(v -> {
                int position = viewHolder.getBindingAdapterPosition();
                return position != RecyclerView.NO_POSITION && onItemLongClick(v, position);
            });
        }

        for (int i = 0; i < mOnItemChildClickArray.size(); i++) {
            int id = mOnItemChildClickArray.keyAt(i);

            View childView = viewHolder.itemView.findViewById(id);
            if (childView != null) {
                int finalI = i;
                childView.setOnClickListener(v -> {
                    int position = viewHolder.getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        onItemChildClick(v, position);
                    }
                });
            }
        }

        for (int i = 0; i < mOnItemChildLongClickArray.size(); i++) {
            int id = mOnItemChildLongClickArray.keyAt(i);

            View childView = viewHolder.itemView.findViewById(id);
            if (childView != null) {
                int finalI = i;
                childView.setOnLongClickListener(v -> {
                    int position = viewHolder.getBindingAdapterPosition();
                    return position != RecyclerView.NO_POSITION && onItemChildLongClick(v, position);
                });
            }
        }
    }

    protected void onItemClick(View v, int position) {
        if (mOnItemClickListener != null) {
            mOnItemClickListener.onItemClick(this, v, position);
        }
    }

    protected boolean onItemLongClick(View v, int position) {
        return mOnItemLongClickListener != null && mOnItemLongClickListener.onItemLongClick(this, v, position);
    }

    protected void onItemChildClick(View v, int position) {
        if (mOnItemChildClickArray.get(v.getId()) != null) {
            mOnItemChildClickArray.get(v.getId()).onItemChildClick(this, v, position);
        }
    }

    protected boolean onItemChildLongClick(View v, int position) {
        return mOnItemChildLongClickArray.get(v.getId()) != null
                && mOnItemChildLongClickArray.get(v.getId()).onItemChildLongClick(this, v, position);
    }

    protected void asStaggeredGridFullSpan(RecyclerView.ViewHolder holder) {
        ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
        if (layoutParams instanceof StaggeredGridLayoutManager.LayoutParams) {
            ((StaggeredGridLayoutManager.LayoutParams) layoutParams).setFullSpan(true);
        }
    }

    protected boolean isFullSpanItem(int itemType) {
        return itemType == EMPTY_VIEW;
    }

    public T getItem(@IntRange(from = 0) int position) {
        return items.size() > position ? items.get(position) : null;
    }

    public int getItemPosition(T item) {
        return items.indexOf(item);
    }

    public void setEmptyViewLayout(Context context, @LayoutRes int layoutResId) {
        emptyView = LayoutInflater.from(context).inflate(layoutResId, new FrameLayout(context), false);
    }

    public boolean displayEmptyView(List<T> list) {
        return emptyView != null && isEmptyViewEnable && list.isEmpty();
    }

    private void runAnimator(RecyclerView.ViewHolder holder) {
        if (animationEnable) {
            if (!isAnimationFirstOnly || holder.getLayoutPosition() > mLastPosition) {
                ItemAnimator animation = itemAnimation != null ? itemAnimation : new AlphaInAnimation();
                startItemAnimator(animation.animator(holder.itemView), holder);
                mLastPosition = holder.getLayoutPosition();
            }
        }
    }

    protected void startItemAnimator(Animator anim, RecyclerView.ViewHolder holder) {
        anim.start();
    }

    public void setItemAnimation(AnimationType animationType) {
        switch (animationType) {
            case AlphaIn:
                itemAnimation = new AlphaInAnimation();
                break;
            case ScaleIn:
                itemAnimation = new ScaleInAnimation();
                break;
            case SlideInBottom:
                itemAnimation = new SlideInBottomAnimation();
                break;
            case SlideInLeft:
                itemAnimation = new SlideInLeftAnimation();
                break;
            case SlideInRight:
                itemAnimation = new SlideInRightAnimation();
                break;
        }
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

    public void set(int position, T data) {
        if (position >= items.size()) {
            throw new IndexOutOfBoundsException("position: " + position + ". size:" + items.size());
        }
        items.set(position, data);
        notifyItemChanged(position);
    }

    public void add(int position, T data) {
        if (position > items.size() || position < 0) {
            throw new IndexOutOfBoundsException("position: " + position + ". size:" + items.size());
        }

        if (displayEmptyView()) {
            notifyItemRemoved(0);
        }
        items.add(position, data);
        notifyItemInserted(position);
    }

    public void add(@NonNull T data) {
        if (displayEmptyView()) {
            notifyItemRemoved(0);
        }
        items.add(data);
        notifyItemInserted(items.size() - 1);
    }

    public void addAll(int position, Collection<T> newCollection) {
        if (position > items.size() || position < 0) {
            throw new IndexOutOfBoundsException("position: " + position + ". size:" + items.size());
        }

        if (displayEmptyView()) {
            notifyItemRemoved(0);
        }
        items.addAll(position, newCollection);
        notifyItemRangeInserted(position, newCollection.size());
    }
    public void addAll(@NonNull Collection<T> newCollection) {
        if (displayEmptyView()) {
            notifyItemRemoved(0);
        }

        int oldSize = items.size();
        mutableItems.addAll(newCollection);
        notifyItemRangeInserted(oldSize, newCollection.size());
    }

    public void removeAt(@IntRange(from = 0) int position) {
        if (position >= items.size()) {
            throw new IndexOutOfBoundsException("position: " + position + ". size:" + items.size());
        }
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
        if (fromPosition >= 0 && fromPosition < size && toPosition >= 0 && toPosition < size) {
            Collections.swap(items, fromPosition, toPosition);
            notifyItemMoved(fromPosition, toPosition);
        }
    }

    private List<T> mutableItems() {
        if (items instanceof ArrayList) {
            return (ArrayList<T>) items;
        } else if (items instanceof List) {
            return (List<T>) items;
        } else {
            List<T> mutableList = new ArrayList<>(items);
            items = mutableList;
            return mutableList;
        }
    }

    public void setOnItemClickListener(OnItemClickListener<T> listener) {
        this.mOnItemClickListener = listener;
    }

    public OnItemClickListener<T> getOnItemClickListener() {
        return mOnItemClickListener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener<T> listener) {
        this.mOnItemLongClickListener = listener;
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
        if (onViewAttachStateChangeListeners != null) {
            onViewAttachStateChangeListeners.clear();
        }
    }

    public enum AnimationType {
        AlphaIn, ScaleIn, SlideInBottom, SlideInLeft, SlideInRight
    }

    public interface OnViewAttachStateChangeListener {
        void onViewAttachedToWindow(RecyclerView.ViewHolder holder);
        void onViewDetachedFromWindow(RecyclerView.ViewHolder holder);
    }
}

interface OnItemClickListener<T> {
    void onItemClick(BaseQuickAdapter<T, ?> adapter, View view, int position);
}

interface OnItemLongClickListener<T> {
    boolean onItemLongClick(BaseQuickAdapter<T, ?> adapter, View view, int position);
}

interface OnItemChildClickListener<T> {
    void onItemChildClick(BaseQuickAdapter<T, ?> adapter, View view, int position);
}

interface OnItemChildLongClickListener<T> {
    boolean onItemChildLongClick(BaseQuickAdapter<T, ?> adapter, View view, int position);
}