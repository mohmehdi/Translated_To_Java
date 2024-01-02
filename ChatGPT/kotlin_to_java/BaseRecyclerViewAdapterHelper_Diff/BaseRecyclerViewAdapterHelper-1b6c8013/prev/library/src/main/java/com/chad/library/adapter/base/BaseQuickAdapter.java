package com.chad.library.adapter.base;

import android.animation.Animator;
import android.content.Context;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.chad.library.adapter.base.animation.BaseAnimation;
import com.chad.library.adapter.base.listener.OnItemChildClickListener;
import com.chad.library.adapter.base.listener.OnItemChildLongClickListener;
import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.chad.library.adapter.base.listener.OnItemLongClickListener;
import com.chad.library.adapter.base.listener.OnViewAttachStateChangeListener;
import com.chad.library.adapter.base.module.BaseDraggableModule;
import com.chad.library.adapter.base.module.DraggableModule;
import com.chad.library.adapter.base.viewholder.EmptyLayoutVH;
import com.chad.library.adapter.base.viewholder.QuickViewHolder;

import java.util.List;

public abstract class BaseQuickAdapter<T, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int EMPTY_VIEW = 0x10000555;

    public List<T> items = emptyList();

    public boolean isUseEmpty = true;

    public boolean animationEnable = false;

    public boolean isAnimationFirstOnly = true;

    public BaseAnimation adapterAnimation = null;

    private BaseDraggableModule mDraggableModule = null;

    private FrameLayout mEmptyLayout;

    private int mLastPosition = -1;

    private OnItemClickListener<T> mOnItemClickListener;
    private OnItemLongClickListener<T> mOnItemLongClickListener;

    private SparseArray<OnItemChildClickListener<T>> mOnItemChildClickArray = new SparseArray<>(3);
    private SparseArray<OnItemChildLongClickListener<T>> mOnItemChildLongClickArray = new SparseArray<>(3);

    private RecyclerView recyclerViewOrNull = null;

    private List<OnViewAttachStateChangeListener> onViewAttachStateChangeListeners;

    public BaseQuickAdapter(List<T> items) {
        this.items = items;
        checkModule();
    }

    private void checkModule() {
        if (this instanceof DraggableModule) {
            mDraggableModule = ((DraggableModule) this).addDraggableModule(this);
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == EMPTY_VIEW) {
            ViewParent emptyLayoutVp = mEmptyLayout.getParent();
            if (emptyLayoutVp instanceof ViewGroup) {
                ((ViewGroup) emptyLayoutVp).removeView(mEmptyLayout);
            }
            return new EmptyLayoutVH(mEmptyLayout);
        }
        return onCreateViewHolder(parent.getContext(), parent, viewType);
    }

    @Override
    public int getItemCount() {
        return hasEmptyView() ? 1 : getItemCount(items);
    }

    @Override
    public int getItemViewType(int position) {
        return hasEmptyView() ? EMPTY_VIEW : getItemViewType(position, items);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof EmptyLayoutVH) {
            return;
        }
        onBindViewHolder((VH) holder, position, getItem(position));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
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

    public T getItem(@IntRange(from = 0) int position) {
        return items.get(position);
    }

    public T getItemOrNull(@IntRange(from = 0) int position) {
        return items.size() > position ? items.get(position) : null;
    }

    public int getItemPosition(T item) {
        return item != null && !items.isEmpty() ? items.indexOf(item) : -1;
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
                int position = viewHolder.getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    childView.setOnClickListener(v -> onItemChildClick(v, position));
                }
            }
        }
        for (int i = 0; i < mOnItemChildLongClickArray.size(); i++) {
            int id = mOnItemChildLongClickArray.keyAt(i);
            View childView = viewHolder.itemView.findViewById(id);
            if (childView != null) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    childView.setOnLongClickListener(v -> onItemChildLongClick(v, position));
                }
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

    protected int getItemCount(List<T> items) {
        return items.size();
    }

    protected int getItemViewType(int position, List<T> list) {
        return 0;
    }

    protected abstract VH onCreateViewHolder(Context context, ViewGroup parent, int viewType);

    protected abstract void onBindViewHolder(VH holder, int position, T item);

    protected void onBindViewHolder(VH holder, int position, T item, List<Object> payloads) {
    }

    public boolean isFullSpanItem(int itemType) {
        return itemType == EMPTY_VIEW;
    }

    public boolean isEmptyViewHolder(RecyclerView.ViewHolder viewHolder) {
        return viewHolder instanceof EmptyLayoutVH;
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

    public void setEmptyView(View emptyView) {
        int oldItemCount = getItemCount();
        boolean insert = false;
        if (mEmptyLayout == null) {
            mEmptyLayout = new FrameLayout(emptyView.getContext());
            ViewGroup.LayoutParams layoutParams = emptyView.getLayoutParams();
            if (layoutParams != null) {
                mEmptyLayout.setLayoutParams(new ViewGroup.LayoutParams(layoutParams.width, layoutParams.height));
            } else {
                mEmptyLayout.setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            }
            insert = true;
        } else {
            ViewGroup.LayoutParams layoutParams = emptyView.getLayoutParams();
            if (layoutParams != null) {
                ViewGroup.LayoutParams lp = mEmptyLayout.getLayoutParams();
                lp.width = layoutParams.width;
                lp.height = layoutParams.height;
                mEmptyLayout.setLayoutParams(lp);
            }
        }

        mEmptyLayout.removeAllViews();
        mEmptyLayout.addView(emptyView);
        isUseEmpty = true;
        if (insert && hasEmptyView()) {
            int position = 0;
            if (getItemCount() > oldItemCount) {
                notifyItemInserted(position);
            } else {
                notifyDataSetChanged();
            }
        }
    }

    public void setEmptyView(int layoutResId) {
        RecyclerView recyclerView = recyclerViewOrNull;
        if (recyclerView != null) {
            View view = LayoutInflater.from(recyclerView.getContext()).inflate(layoutResId, recyclerView, false);
            setEmptyView(view);
        }
    }

    public void removeEmptyView() {
        if (mEmptyLayout != null) {
            mEmptyLayout.removeAllViews();
        }
    }

    public boolean hasEmptyView() {
        return mEmptyLayout != null && mEmptyLayout.getChildCount() > 0 && isUseEmpty && items.isEmpty();
    }

    public FrameLayout getEmptyLayout() {
        return mEmptyLayout != null ? mEmptyLayout : null;
    }
    private void addAnimation(RecyclerView.ViewHolder holder) {
        if (animationEnable) {
            if (!isAnimationFirstOnly || holder.getLayoutPosition() > mLastPosition) {
                BaseAnimation animation = adapterAnimation != null ? adapterAnimation : new AlphaInAnimation();
                for (Animator animator : animation.animators(holder.itemView)) {
                    startAnim(animator, holder.getLayoutPosition());
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
        adapterAnimation = switch (animationType) {
            case AlphaIn -> new AlphaInAnimation();
            case ScaleIn -> new ScaleInAnimation();
            case SlideInBottom -> new SlideInBottomAnimation();
            case SlideInLeft -> new SlideInLeftAnimation();
            case SlideInRight -> new SlideInRightAnimation();
        };
    }

    public void setNewInstance(List<T> list) {
        if (list == items) {
            return;
        }

        items = list != null ? list : new ArrayList<>();
        mLastPosition = -1;
        notifyDataSetChanged();
    }

    public void replaceList(Collection<T> newItems) {
        // Implementation omitted
    }

    public void setList(List<T> list) {
        if (list == items) {
            return;
        }

        items = list != null ? list : new ArrayList<>();
        mLastPosition = -1;
        notifyDataSetChanged();
    }

    public void setData(@IntRange(from = 0) int position, T data) {
        if (position >= items.size()) {
            return;
        }

        if (items instanceof List) {
            ((List<T>) items).set(position, data);
            notifyItemChanged(position);
        }
    }

    public void add(@IntRange(from = 0) int position, T data) {
        if (items instanceof List) {
            ((List<T>) items).add(position, data);
            notifyItemInserted(position);
        }
    }

    public void add(@NonNull T data) {
        if (items instanceof List) {
            ((List<T>) items).add(data);
            notifyItemInserted(items.size());
        }
    }

    public void add(@IntRange(from = 0) int position, Collection<T> newData) {
        if (items instanceof List) {
            ((List<T>) items).addAll(position, newData);
            notifyItemRangeInserted(position, newData.size());
        }
    }

    public void addData(@NonNull Collection<T> newData) {
        if (items instanceof List) {
            int oldSize = items.size();
            ((List<T>) items).addAll(newData);
            notifyItemRangeInserted(oldSize, newData.size());
        }
    }

    public void removeAt(@IntRange(from = 0) int position) {
        if (position >= items.size()) {
            return;
        }

        if (items instanceof List) {
            ((List<T>) items).remove(position);
            notifyItemRemoved(position);
        }
    }

    public void remove(T data) {
        int index = items.indexOf(data);
        if (index == -1) {
            return;
        }
        removeAt(index);
    }

    protected void compatibilityDataSizeChanged(int size) {
        if (items.size() == size) {
            notifyDataSetChanged();
        }
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
}
