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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import com.chad.library.adapter.base.animation.BaseAnimation;
import com.chad.library.adapter.base.animation.AlphaInAnimation;
import com.chad.library.adapter.base.animation.ScaleInAnimation;
import com.chad.library.adapter.base.animation.SlideInBottomAnimation;
import com.chad.library.adapter.base.animation.SlideInLeftAnimation;
import com.chad.library.adapter.base.animation.SlideInRightAnimation;
import com.chad.library.adapter.base.module.BaseDraggableModule;
import com.chad.library.adapter.base.viewholder.EmptyLayoutVH;
import com.chad.library.adapter.base.viewholder.QuickViewHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class BrvahAdapter<T, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<T> items;
    private boolean isUseEmpty;
    private boolean animationEnable;
    private boolean isAnimationFirstOnly;
    private BaseAnimation adapterAnimation;
    private BaseDraggableModule mDraggableModule;
    private int mLastPosition = -1;
    private OnItemClickListener<T> mOnItemClickListener;
    private OnItemLongClickListener<T> mOnItemLongClickListener;
    private SparseArray<OnItemChildClickListener<T>> mOnItemChildClickArray;
    private SparseArray<OnItemChildLongClickListener<T>> mOnItemChildLongClickArray;
    private RecyclerView recyclerViewOrNull;
    private MutableList<OnViewAttachStateChangeListener> onViewAttachStateChangeListeners;
    private View emptyView;

    public BrvahAdapter(@NonNull List<T> items) {
        this.items = items;
        checkModule();
    }

    public BrvahAdapter() {
        this.items = new ArrayList<>();
        checkModule();
    }

    private void checkModule() {
    }

    protected abstract int getItemViewType(int position, @NonNull List<T> list);

    protected abstract VH onCreateViewHolder(@NonNull Context context, @NonNull ViewGroup parent, int viewType);

    protected abstract void onBindViewHolder(@NonNull VH holder, int position, @NonNull T item);

    protected void onBindViewHolder(@NonNull VH holder, int position, @NonNull T item, @NonNull List<Object> payloads) {
    }

    protected int getItemCount(@NonNull List<T> items) {
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

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == EMPTY_VIEW) {
            return new EmptyLayoutVH(new FrameLayout(parent.getContext()));
        }

        VH viewHolder = onCreateViewHolder(parent.getContext(), parent, viewType);
        bindViewClickListener(viewHolder, viewType);

        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof EmptyLayoutVH) {
            ((EmptyLayoutVH) holder).changeEmptyView(emptyView);
            return;
        }

        onBindViewHolder((VH) holder, position, getItem(position));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder((VH) holder, position);
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
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
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
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        if (onViewAttachStateChangeListeners != null) {
            for (OnViewAttachStateChangeListener listener : onViewAttachStateChangeListeners) {
                listener.onViewDetachedFromWindow(holder);
            }
        }
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        recyclerViewOrNull = recyclerView;

        if (mDraggableModule != null) {
            mDraggableModule.attachToRecyclerView(recyclerView);
        }
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        recyclerViewOrNull = null;
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

            View childView = viewHolder.itemView.findViewById(id);
            if (childView != null) {
                childView.setOnClickListener(v -> {
                    int position = viewHolder.getBindingAdapterPosition();
                    if (position == RecyclerView.NO_POSITION) {
                        return;
                    }
                    onItemChildClick(v, position);
                });
            }
        }

        for (int i = 0; i < mOnItemChildLongClickArray.size(); i++) {
            int id = mOnItemChildLongClickArray.keyAt(i);

            View childView = viewHolder.itemView.findViewById(id);
            if (childView != null) {
                childView.setOnLongClickListener(v -> {
                    int position = viewHolder.getBindingAdapterPosition();
                    if (position == RecyclerView.NO_POSITION) {
                        return false;
                    }
                    return onItemChildLongClick(v, position);
                });
            }
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

    @NonNull
    public T getItem(@IntRange(from = 0) int position) {
        return items.get(position);
    }

    public int getItemPosition(@Nullable T item) {
        return items.indexOf(item);
    }

    public boolean isFullSpanItem(int itemType) {
        return itemType == EMPTY_VIEW;
    }

    public boolean isEmptyViewHolder(@NonNull RecyclerView.ViewHolder holder) {
        return holder instanceof EmptyLayoutVH;
    }

    protected void setStaggeredGridFullSpan(@NonNull RecyclerView.ViewHolder holder) {
        View view = holder.itemView;
        RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) view.getLayoutParams();
        if (layoutParams instanceof StaggeredGridLayoutManager.LayoutParams) {
            layoutParams.setFullSpan(true);
        }
    }

    @Nullable
    public View getViewByPosition(int position, @IdRes int viewId) {
        RecyclerView recyclerView = recyclerViewOrNull;
        if (recyclerView == null) {
            return null;
        }
        RecyclerView.ViewHolder viewHolder = recyclerView.findViewHolderForLayoutPosition(position);
        if (viewHolder == null || !(viewHolder instanceof QuickViewHolder)) {
            return null;
        }
        return ((QuickViewHolder) viewHolder).getViewOrNull(viewId);
    }

    public void setEmptyView(@Nullable View emptyView) {
        boolean oldDisplayEmptyLayout = displayEmptyView();
        this.emptyView = emptyView;
        isUseEmpty = true;

        boolean newDisplayEmptyLayout = displayEmptyView();

        if (oldDisplayEmptyLayout && !newDisplayEmptyLayout) {
            notifyItemRemoved(0);
        } else if (newDisplayEmptyLayout && !oldDisplayEmptyLayout) {
            notifyItemInserted(0);
        } else if (oldDisplayEmptyLayout && newDisplayEmptyLayout) {
            notifyItemChanged(0, "");
        }
    }

    public void setEmptyViewLayout(@LayoutRes int layoutResId) {
        RecyclerView recyclerView = recyclerViewOrNull;
        if (recyclerView == null) {
            return;
        }
        View view = LayoutInflater.from(recyclerView.getContext()).inflate(layoutResId, recyclerView, false);
        setEmptyView(view);
    }

    public boolean displayEmptyView() {
        if (emptyView == null) {
            return false;
        }
        if (!isUseEmpty) {
            return false;
        }
        return items.isEmpty();
    }

    public void addAnimation(@NonNull RecyclerView.ViewHolder holder) {
        if (animationEnable) {
            if (!isAnimationFirstOnly || holder.getLayoutPosition() > mLastPosition) {
                BaseAnimation animation = adapterAnimation != null ? adapterAnimation : new AlphaInAnimation();
                List<Animator> animators = animation.animators(holder.itemView);
                for (Animator anim : animators) {
                    startAnim(anim, holder.getLayoutPosition());
                }
                mLastPosition = holder.getLayoutPosition();
            }
        }
    }

    protected void startAnim(@NonNull Animator anim, int index) {
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

    public void setOnItemClickListener(@Nullable OnItemClickListener<T> listener) {
        this.mOnItemClickListener = listener;
    }

    @Nullable
    public OnItemClickListener<T> getOnItemClickListener() {
        return mOnItemClickListener;
    }

    public void setOnItemLongClickListener(@Nullable OnItemLongClickListener<T> listener) {
        this.mOnItemLongClickListener = listener;
    }

    @Nullable
    public OnItemLongClickListener<T> getOnItemLongClickListener() {
        return mOnItemLongClickListener;
    }

    public void addOnItemChildClickListener(@IdRes int id, @NonNull OnItemChildClickListener<T> listener) {
        mOnItemChildClickArray.put(id, listener);
    }

    public void removeOnItemChildClickListener(@IdRes int id) {
        mOnItemChildClickArray.remove(id);
    }

    public void addOnItemChildLongClickListener(@IdRes int id, @NonNull OnItemChildLongClickListener<T> listener) {
        mOnItemChildLongClickArray.put(id, listener);
    }

    public void removeOnItemChildLongClickListener(@IdRes int id) {
        mOnItemChildLongClickArray.remove(id);
    }

    public void addOnViewAttachStateChangeListener(@NonNull OnViewAttachStateChangeListener listener) {
        if (onViewAttachStateChangeListeners == null) {
            onViewAttachStateChangeListeners = new ArrayList<>();
        }
        onViewAttachStateChangeListeners.add(listener);
    }

    public void removeOnViewAttachStateChangeListener(@NonNull OnViewAttachStateChangeListener listener) {
        if (onViewAttachStateChangeListeners != null) {
            onViewAttachStateChangeListeners.remove(listener);
        }
    }

    public void clearOnViewAttachStateChangeListener() {
        if (onViewAttachStateChangeListeners != null) {
            onViewAttachStateChangeListeners.clear();
        }
    }

    public interface OnItemClickListener<T> {
        void onItemClick(BrvahAdapter<T, VH> adapter, View view, int position);
    }

    public interface OnItemLongClickListener<T> {
        boolean onItemLongClick(BrvahAdapter<T, VH> adapter, View view, int position);
    }

    public interface OnItemChildClickListener<T> {
        void onItemChildClick(BrvahAdapter<T, VH> adapter, View view, int position);
    }

    public interface OnItemChildLongClickListener<T> {
        boolean onItemChildLongClick(BrvahAdapter<T, VH> adapter, View view, int position);
    }

    public interface OnViewAttachStateChangeListener {

        void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder);

        void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder);
    }

    public static final int EMPTY_VIEW = 0x10000555;
}