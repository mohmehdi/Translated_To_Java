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
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.chad.library.adapter.base.animation.AlphaInAnimation;
import com.chad.library.adapter.base.animation.AnimationType;
import com.chad.library.adapter.base.animation.ItemAnimator;
import com.chad.library.adapter.base.animation.ScaleInAnimation;
import com.chad.library.adapter.base.animation.SlideInBottomAnimation;
import com.chad.library.adapter.base.animation.SlideInLeftAnimation;
import com.chad.library.adapter.base.animation.SlideInRightAnimation;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseQuickAdapter<T, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<T> items = new ArrayList<>();
    private int mLastPosition = -1;
    private OnItemClickListener<T> mOnItemClickListener = null;
    private OnItemLongClickListener<T> mOnItemLongClickListener = null;
    private SparseArray<OnItemChildClickListener<T>> mOnItemChildClickArray = new SparseArray<>(3);
    private SparseArray<OnItemChildLongClickListener<T>> mOnItemChildLongClickArray = new SparseArray<>(3);
    private List<OnViewAttachStateChangeListener> onViewAttachStateChangeListeners = null;

    private RecyclerView _recyclerView = null;

    public RecyclerView getRecyclerView() {
        if (_recyclerView == null) {
            throw new IllegalStateException("Please get it after onAttachedToRecyclerView()");
        }
        return _recyclerView;
    }

    public Context getContext() {
        return getRecyclerView().getContext();
    }

    public boolean isEmptyViewHolder(RecyclerView.ViewHolder viewHolder) {
        return viewHolder instanceof EmptyLayoutVH;
    }

    private boolean displayEmptyView() {
        return isEmptyViewEnable && items.isEmpty();
    }

    private EmptyLayoutVH createEmptyViewHolder(ViewGroup parent) {
        FrameLayout frameLayout = new FrameLayout(parent.getContext());
        frameLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return new EmptyLayoutVH(frameLayout);
    }

    public boolean isEmptyViewEnable = false;

    public View emptyView = null;

    public boolean animationEnable = false;

    public boolean isAnimationFirstOnly = true;

    public ItemAnimator itemAnimation = null;

    @NonNull
    protected abstract VH onCreateViewHolder(Context context, ViewGroup parent, int viewType);

    protected abstract void onBindViewHolder(@NonNull VH holder, int position, @Nullable T item);

    protected void onBindViewHolder(@NonNull VH holder, int position, @Nullable T item, @NonNull List<Object> payloads) {
        onBindViewHolder(holder, position, item);
    }

    protected int getItemCount(@NonNull List<T> items) {
        return items.size();
    }

    protected int getItemViewType(int position, @NonNull List<T> list) {
        return 0;
    }

    @Override
    public final int getItemCount() {
        return displayEmptyView() ? 1 : getItemCount(items);
    }

    @Override
    public final int getItemViewType(int position) {
        return displayEmptyView() ? EMPTY_VIEW : getItemViewType(position, items);
    }

    @NonNull
    @Override
    public final RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == EMPTY_VIEW) {
            return createEmptyViewHolder(parent);
        }
        VH viewHolder = onCreateViewHolder(parent.getContext(), parent, viewType);
        bindViewClickListener(viewHolder, viewType);
        return viewHolder;
    }

    @Override
    public final void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof EmptyLayoutVH) {
            ((EmptyLayoutVH) holder).changeEmptyView(emptyView);
            return;
        }
        //noinspection unchecked
        onBindViewHolder((VH) holder, position, getItem(position));
    }

    @Override
    public final void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (holder instanceof EmptyLayoutVH) {
            ((EmptyLayoutVH) holder).changeEmptyView(emptyView);
            return;
        }

        if (payloads.isEmpty()) {
            //noinspection unchecked
            onBindViewHolder((VH) holder, position, getItem(position));
        } else {
            //noinspection unchecked
            onBindViewHolder((VH) holder, position, getItem(position), payloads);
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @CallSuper
    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
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

    @CallSuper
    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        if (onViewAttachStateChangeListeners != null) {
            for (OnViewAttachStateChangeListener listener : onViewAttachStateChangeListeners) {
                listener.onViewDetachedFromWindow(holder);
            }
        }
    }

    @CallSuper
    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        _recyclerView = recyclerView;
    }

    @CallSuper
    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        _recyclerView = null;
    }

    protected void bindViewClickListener(@NonNull VH viewHolder, int viewType) {
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
                        onItemChildClick(v, position, finalI);
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
                    return position != RecyclerView.NO_POSITION && onItemChildLongClick(v, position, finalI);
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

    protected void onItemChildClick(View v, int position, int childPosition) {
        OnItemChildClickListener<T> listener = mOnItemChildClickArray.get(v.getId());
        if (listener != null) {
            listener.onItemChildClick(this, v, position, childPosition);
        }
    }

    protected boolean onItemChildLongClick(View v, int position, int childPosition) {
        OnItemChildLongClickListener<T> listener = mOnItemChildLongClickArray.get(v.getId());
        return listener != null && listener.onItemChildLongClick(this, v, position, childPosition);
    }

    protected void setStaggeredGridFullSpan(RecyclerView.ViewHolder holder) {
        ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
        if (layoutParams instanceof StaggeredGridLayoutManager.LayoutParams) {
            ((StaggeredGridLayoutManager.LayoutParams) layoutParams).setFullSpan(true);
        }
    }

    protected boolean isFullSpanItem(int itemType) {
        return itemType == EMPTY_VIEW;
    }

    @Nullable
    protected T getItem(@IntRange(from = 0) int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    protected int getItemPosition(@Nullable T item) {
        return items.indexOf(item);
    }

    protected void setEmptyViewLayout(Context context, @LayoutRes int layoutResId) {
        emptyView = LayoutInflater.from(context).inflate(layoutResId, new FrameLayout(context), false);
    }

    protected boolean displayEmptyView(List<T> list) {
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

    protected void setItemAnimation(AnimationType animationType) {
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

    public void submitList(@Nullable List<T> list) {
        if (list == items) {
            return;
        }

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
        items.set(position, data);
        notifyItemChanged(position);
    }
   public void add(@IntRange(from = 0) int position, T data) {
        if (position > items.size() || position < 0) {
            throw new IndexOutOfBoundsException("position: " + position + ". size:" + items.size());
        }

        if (displayEmptyView()) {
            notifyItemRemoved(0);
        }
        mutableItems.add(position, data);
        notifyItemInserted(position);
    }

    public void add(@NonNull T data) {
        if (displayEmptyView()) {
            notifyItemRemoved(0);
        }
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
        mutableItems.addAll(position, newCollection);
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

    private List<T> getMutableItems() {
        if (items instanceof ArrayList) {
            return (ArrayList<T>) items;
        } else if (items instanceof List) {
            return (List<T>) items;
        } else {
            return new ArrayList<>(items);
        }
    }

    public void setOnItemClickListener(OnItemClickListener<T> listener) {
        this.mOnItemClickListener = listener;
    }

    @Nullable
    public OnItemClickListener<T> getOnItemClickListener() {
        return mOnItemClickListener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener<T> listener) {
        this.mOnItemLongClickListener = listener;
    }

    @Nullable
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
        if (onViewAttachStateChangeListeners != null) {
            onViewAttachStateChangeListeners.remove(listener);
        }
    }

    public void clearOnViewAttachStateChangeListener() {
        if (onViewAttachStateChangeListeners != null) {
            onViewAttachStateChangeListeners.clear();
        }
    }

    public enum AnimationType {
        AlphaIn, ScaleIn, SlideInBottom, SlideInLeft, SlideInRight
    }

    public interface OnItemClickListener<T> {
        void onItemClick(BaseQuickAdapter<T, ?> adapter, View view, int position);
    }

    public interface OnItemLongClickListener<T> {
        boolean onItemLongClick(BaseQuickAdapter<T, ?> adapter, View view, int position);
    }

    public interface OnItemChildClickListener<T> {
        void onItemChildClick(BaseQuickAdapter<T, ?> adapter, View view, int position);
    }

    public interface OnItemChildLongClickListener<T> {
        boolean onItemChildLongClick(BaseQuickAdapter<T, ?> adapter, View view, int position);
    }

    public interface OnViewAttachStateChangeListener {
        void onViewAttachedToWindow(RecyclerView.ViewHolder holder);

        void onViewDetachedFromWindow(RecyclerView.ViewHolder holder);
    }

    public static final int EMPTY_VIEW = 0x10000555;
    static final int EMPTY_PAYLOAD = 0;
}