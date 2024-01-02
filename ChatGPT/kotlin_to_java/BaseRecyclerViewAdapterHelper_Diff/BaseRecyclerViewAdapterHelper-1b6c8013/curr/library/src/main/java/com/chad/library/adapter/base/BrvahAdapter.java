package com.chad.library.adapter.base;

import android.animation.Animator;
import android.content.Context;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.chad.library.adapter.base.animation.*;
import com.chad.library.adapter.base.module.BaseDraggableModule;
import com.chad.library.adapter.base.viewholder.EmptyLayoutVH;
import com.chad.library.adapter.base.viewholder.QuickViewHolder;

import java.util.ArrayList;
import java.util.List;

public abstract class BrvahAdapter<T, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public List<T> items = new ArrayList<>();
    public BaseAnimation adapterAnimation = null;
    private BaseDraggableModule mDraggableModule = null;
    private int mLastPosition = -1;
    private OnItemClickListener<T> mOnItemClickListener = null;
    private OnItemLongClickListener<T> mOnItemLongClickListener = null;
    private SparseArray<OnItemChildClickListener<T>> mOnItemChildClickArray = new SparseArray<>(3);
    private SparseArray<OnItemChildLongClickListener<T>> mOnItemChildLongClickArray = new SparseArray<>(3);
    private RecyclerView recyclerViewOrNull = null;
    private List<OnViewAttachStateChangeListener> onViewAttachStateChangeListeners = null;

    public BrvahAdapter() {
        checkModule();
    }

    private void checkModule() {
        // Do nothing in the Kotlin code
    }

    protected int getItemViewType(int position, List<T> list) {
        return 0;
    }

    protected abstract VH onCreateViewHolder(Context context, ViewGroup parent, int viewType);

    protected void onBindViewHolder(VH holder, int position, T item, List<Object> payloads) {
        // Do nothing in the Kotlin code
    }

    protected int getItemCount(List<T> items) {
        return items.size();
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

        return onCreateViewHolder(parent.getContext(), parent, viewType);
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
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
            return;
        }

        if (holder instanceof EmptyLayoutVH) {
            ((EmptyLayoutVH) holder).changeEmptyView(emptyView);
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
            setStaggeredGridFullSpan(holder);
        } else {
            addAnimation(holder);
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
        recyclerViewOrNull = recyclerView;

        if (mDraggableModule != null) {
            mDraggableModule.attachToRecyclerView(recyclerView);
        }
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        recyclerViewOrNull = null;
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
        OnItemChildClickListener<T> listener = mOnItemChildClickArray.get(v.getId());
        if (listener != null) {
            listener.onItemChildClick(this, v, position);
        }
    }

    protected boolean onItemChildLongClick(View v, int position) {
        OnItemChildLongClickListener<T> listener = mOnItemChildLongClickArray.get(v.getId());
        return listener != null && listener.onItemChildLongClick(this, v, position);
    }

    public T getItem(@IntRange(from = 0) int position) {
        return items.get(position);
    }

    public int getItemPosition(T item) {
        return item != null && !items.isEmpty() ? items.indexOf(item) : -1;
    }

    protected boolean isFullSpanItem(int itemType) {
        return itemType == EMPTY_VIEW;
    }

    protected void setStaggeredGridFullSpan(RecyclerView.ViewHolder holder) {
        ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
        if (layoutParams instanceof StaggeredGridLayoutManager.LayoutParams) {
            ((StaggeredGridLayoutManager.LayoutParams) layoutParams).setFullSpan(true);
        }
    }

    public View getViewByPosition(int position, @IdRes int viewId) {
        RecyclerView recyclerView = recyclerViewOrNull;
        if (recyclerView == null) {
            return null;
        }

        QuickViewHolder viewHolder = (QuickViewHolder) recyclerView.findViewHolderForLayoutPosition(position);
        return viewHolder != null ? viewHolder.getViewOrNull(viewId) : null;
    }

    public View emptyView = null;
    private boolean isUseEmpty = false;

    public void setEmptyViewLayout(@LayoutRes int layoutResId) {
        if (recyclerViewOrNull != null) {
            View view = LayoutInflater.from(recyclerViewOrNull.getContext()).inflate(layoutResId, recyclerViewOrNull, false);
            emptyView = view;
        }
    }

    private boolean displayEmptyView() {
        return emptyView != null && isUseEmpty && items.isEmpty();
    }

    private void addAnimation(RecyclerView.ViewHolder holder) {
        if (animationEnable) {
            if (!isAnimationFirstOnly || holder.getLayoutPosition() > mLastPosition) {
                BaseAnimation animation = adapterAnimation != null ? adapterAnimation : new AlphaInAnimation();
                for (Animator anim : animation.animators(holder.itemView)) {
                    startAnim(anim, holder.getLayoutPosition());
                }
                mLastPosition = holder.getLayoutPosition();
            }
        }
    }

    protected void startAnim(Animator anim, int index) {
        anim.start();
    }

    public enum AnimationType {
        AlphaIn, ScaleIn, SlideInBottom, SlideInLeft, SlideInRight
    }

    public void setAnimationWithDefault(AnimationType animationType) {
        switch (animationType) {
            case AlphaIn:
                adapterAnimation = new AlphaInAnimation();
                break;
            case ScaleIn:
                adapterAnimation = new ScaleInAnimation();
                break;
            case SlideInBottom:
                adapterAnimation = new SlideInBottomAnimation();
                break;
            case SlideInLeft:
                adapterAnimation = new SlideInLeftAnimation();
                break;
            case SlideInRight:
                adapterAnimation = new SlideInRightAnimation();
                break;
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

    public BrvahAdapter<T, VH> addOnItemChildClickListener(@IdRes int id, OnItemChildClickListener<T> listener) {
        mOnItemChildClickArray.put(id, listener);
        return this;
    }

    public BrvahAdapter<T, VH> removeOnItemChildClickListener(@IdRes int id) {
        mOnItemChildClickArray.remove(id);
        return this;
    }

    public BrvahAdapter<T, VH> addOnItemChildLongClickListener(@IdRes int id, OnItemChildLongClickListener<T> listener) {
        mOnItemChildLongClickArray.put(id, listener);
        return this;
    }

    public BrvahAdapter<T, VH> removeOnItemChildLongClickListener(@IdRes int id) {
        mOnItemChildLongClickArray.remove(id);
        return this;
    }

    public BrvahAdapter<T, VH> addOnViewAttachStateChangeListener(OnViewAttachStateChangeListener listener) {
        if (onViewAttachStateChangeListeners == null) {
            onViewAttachStateChangeListeners = new ArrayList<>();
        }
        onViewAttachStateChangeListeners.add(listener);
        return this;
    }

    public BrvahAdapter<T, VH> removeOnViewAttachStateChangeListener(OnViewAttachStateChangeListener listener) {
        if (onViewAttachStateChangeListeners != null) {
            onViewAttachStateChangeListeners.remove(listener);
        }
        return this;
    }

    public BrvahAdapter<T, VH> clearOnViewAttachStateChangeListener() {
        if (onViewAttachStateChangeListeners != null) {
            onViewAttachStateChangeListeners.clear();
        }
        return this;
    }

    public interface OnItemClickListener<T> {
        void onItemClick(BrvahAdapter<T, ?> adapter, View view, int position);
    }

    public interface OnItemLongClickListener<T> {
        boolean onItemLongClick(BrvahAdapter<T, ?> adapter, View view, int position);
    }

    public interface OnItemChildClickListener<T> {
        void onItemChildClick(BrvahAdapter<T, ?> adapter, View view, int position);
    }

    public interface OnItemChildLongClickListener<T> {
        boolean onItemChildLongClick(BrvahAdapter<T, ?> adapter, View view, int position);
    }

    public interface OnViewAttachStateChangeListener {
        void onViewAttachedToWindow(RecyclerView.ViewHolder holder);

        void onViewDetachedFromWindow(RecyclerView.ViewHolder holder);
    }

    public static final int EMPTY_VIEW = 0x10000555;
}
