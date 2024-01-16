package com.chad.library.adapter.base;

import android.animation.Animator;
import android.content.Context;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import java.util.List;

public abstract class BaseQuickAdapter<T, VH extends RecyclerView.ViewHolder>
  extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  public static final int EMPTY_VIEW = 0x10000555;

  private boolean isUseEmpty;
  private boolean animationEnable;
  private boolean isAnimationFirstOnly;
  private BaseAnimation adapterAnimation;
  private BaseDraggableModule mDraggableModule;
  private FrameLayout mEmptyLayout;
  private int mLastPosition = -1;
  private OnItemClickListener<T> mOnItemClickListener;
  private OnItemLongClickListener<T> mOnItemLongClickListener;
  private SparseArray<OnItemChildClickListener<T>> mOnItemChildClickArray;
  private SparseArray<OnItemChildLongClickListener<T>> mOnItemChildLongClickArray;

  private RecyclerView recyclerViewOrNull;
  private List<OnViewAttachStateChangeListener> onViewAttachStateChangeListeners;

  public BaseQuickAdapter(List<T> items) {
    this.items = items;
    checkModule();
  }

  private void checkModule() {
    if (this instanceof DraggableModule) {
      mDraggableModule =
        ((DraggableModule<T, VH>) this).addDraggableModule(this);
    }
  }

  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(
    ViewGroup parent,
    int viewType
  ) {
    if (viewType == EMPTY_VIEW) {
      ViewParent emptyLayoutVp = mEmptyLayout.getParent();
      if (emptyLayoutVp instanceof ViewGroup) {
        ((ViewGroup) emptyLayoutVp).removeView(mEmptyLayout);
      }

      return new EmptyLayoutVH(mEmptyLayout);
    }

    return onCreateViewHolder(parent.getContext(), parent, viewType)
      .apply(viewHolder -> {
        bindViewClickListener(viewHolder, viewType);
      });
  }

  @Override
  public int getItemCount() {
    return hasEmptyView() ? 1 : getItemCount(items);
  }

  @Override
  public int getItemViewType(int position) {
    if (hasEmptyView()) {
      return EMPTY_VIEW;
    }

    return getItemViewType(position, items);
  }

  @Override
  public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
    if (holder instanceof EmptyLayoutVH) {
      return;
    }

    onBindViewHolder(holder, position, getItem(position));
  }

  @Override
  public void onBindViewHolder(
    RecyclerView.ViewHolder holder,
    int position,
    List<Object> payloads
  ) {
    if (payloads.isEmpty()) {
      onBindViewHolder(holder, position);
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

  public T getItem(int position) {
    return items.get(position);
  }

  public T getItemOrNull(int position) {
    return items.get(position);
  }

  public int getItemPosition(T item) {
    return items.indexOf(item);
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
    return mOnItemChildLongClickArray
      .get(v.getId())
      .onItemChildLongClick(this, v, position);
  }

  protected int getItemCount(List<T> items) {
    return items.size();
  }

  protected int getItemViewType(int position, List<T> list) {
    return 0;
  }

  protected abstract VH onCreateViewHolder(
    Context context,
    ViewGroup parent,
    int viewType
  );

  protected abstract void onBindViewHolder(VH holder, int position, T item);

  protected void onBindViewHolder(
    VH holder,
    int position,
    T item,
    List<Object> payloads
  ) {}

  public boolean isFullSpanItem(int itemType) {
    return itemType == EMPTY_VIEW;
  }

  public boolean isEmptyViewHolder(RecyclerView.ViewHolder holder) {
    return holder instanceof EmptyLayoutVH;
  }

  protected void setStaggeredGridFullSpan(RecyclerView.ViewHolder holder) {
    ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
    if (layoutParams instanceof StaggeredGridLayoutManager.LayoutParams) {
      ((StaggeredGridLayoutManager.LayoutParams) layoutParams).setFullSpan(
          true
        );
    }
  }

  public View getViewByPosition(int position, @IdRes int viewId) {
    RecyclerView recyclerView = recyclerViewOrNull;
    RecyclerView.ViewHolder viewHolder = recyclerView != null
      ? recyclerView.findViewHolderForLayoutPosition(position)
      : null;
    if (viewHolder == null || viewHolder.getItemViewType() != position) {
      return null;
    }
    return viewHolder.getViewOrNull(viewId);
  }

  public void setEmptyView(View emptyView) {
    int oldItemCount = getItemCount();
    boolean insert = false;
    if (!this::mEmptyLayout.isInitialized) {
      mEmptyLayout = new FrameLayout(emptyView.getContext());

      mEmptyLayout.setLayoutParams(emptyView.getLayoutParams());

      insert = true;
    } else {
      mEmptyLayout.setLayoutParams(emptyView.getLayoutParams());
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
    recyclerViewOrNull.let(recyclerView -> {
      View view = LayoutInflater
        .from(recyclerView.getContext())
        .inflate(layoutResId, recyclerView, false);
      setEmptyView(view);
    });
  }

  public void removeEmptyView() {
    if (this::mEmptyLayout.isInitialized) {
      mEmptyLayout.removeAllViews();
    }
  }

  public boolean hasEmptyView() {
    if (
      !this::mEmptyLayout.isInitialized || mEmptyLayout.getChildCount() == 0
    ) {
      return false;
    }
    if (!isUseEmpty) {
      return false;
    }
    return items.isEmpty();
  }

  public FrameLayout getEmptyLayout() {
    return mEmptyLayout;
  }

  private void addAnimation(RecyclerView.ViewHolder holder) {
    if (animationEnable) {
      if (!isAnimationFirstOnly || holder.getLayoutPosition() > mLastPosition) {
        BaseAnimation animation = adapterAnimation != null
          ? adapterAnimation
          : new AlphaInAnimation();
        List<Animator> animators = animation.animators(holder.itemView);
        for (Animator anim : animators) {
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
    AlphaIn,
    ScaleIn,
    SlideInBottom,
    SlideInLeft,
    SlideInRight,
  }

  public void setAnimationWithDefault(AnimationType animationType) {
    adapterAnimation =
      switch (animationType) {
        case AlphaIn -> new AlphaInAnimation();
        case ScaleIn -> new ScaleInAnimation();
        case SlideInBottom -> new SlideInBottomAnimation();
        case SlideInLeft -> new SlideInLeftAnimation();
        case SlideInRight -> new SlideInRightAnimation();
      };
  }

  public void setNewInstance(List<T> list) {
    if (list == this.items) {
      return;
    }

    this.items = list;
    mLastPosition = -1;
    notifyDataSetChanged();
  }

  public void replaceList(Collection<T> newItems) {}

  public void setList(List<T> list) {
    if (list == this.items) {
      return;
    }

    this.items = list;
    mLastPosition = -1;
    notifyDataSetChanged();
  }

  public void setData(int position, T data) {
    if (position >= this.items.size()) {
      return;
    }

    if (items instanceof List) {
      ((List<T>) items).set(position, data);
      notifyItemChanged(position);
    }
  }

  public void add(int position, T data) {
    if (items instanceof List) {
      ((List<T>) items).add(position, data);
      notifyItemInserted(position);
    }
  }

  public void add(T data) {
    if (items instanceof List) {
      ((List<T>) items).add(data);
      notifyItemInserted(items.size());
    }
  }

  public void add(int position, Collection<T> newData) {
    if (items instanceof List) {
      ((List<T>) items).addAll(position, newData);
      notifyItemRangeInserted(position, newData.size());
    }
  }

  public void addData(Collection<T> newData) {
    if (items instanceof List) {
      int oldSize = items.size();
      ((List<T>) items).addAll(newData);
      notifyItemRangeInserted(oldSize, newData.size());
    }
  }

  public void removeAt(int position) {
    if (position >= items.size()) {
      return;
    }

    if (items instanceof List) {
      ((List<T>) items).removeAt(position);
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

  public void addOnItemChildClickListener(
    @IdRes int id,
    OnItemChildClickListener<T> listener
  ) {
    mOnItemChildClickArray.put(id, listener);
  }

  public void removeOnItemChildClickListener(@IdRes int id) {
    mOnItemChildClickArray.remove(id);
  }

  public void addOnItemChildLongClickListener(
    @IdRes int id,
    OnItemChildLongClickListener<T> listener
  ) {
    mOnItemChildLongClickArray.put(id, listener);
  }

  public void removeOnItemChildLongClickListener(@IdRes int id) {
    mOnItemChildLongClickArray.remove(id);
  }

  public void addOnViewAttachStateChangeListener(
    OnViewAttachStateChangeListener listener
  ) {
    if (onViewAttachStateChangeListeners == null) {
      onViewAttachStateChangeListeners = new ArrayList<>();
    }
    onViewAttachStateChangeListeners.add(listener);
  }

  public void removeOnViewAttachStateChangeListener(
    OnViewAttachStateChangeListener listener
  ) {
    onViewAttachStateChangeListeners.remove(listener);
  }

  public void clearOnViewAttachStateChangeListener() {
    onViewAttachStateChangeListeners.clear();
  }

  public interface OnItemClickListener<T> {
    void onItemClick(BaseQuickAdapter<T, VH> adapter, View view, int position);
  }

  public interface OnItemLongClickListener<T> {
    boolean onItemLongClick(
      BaseQuickAdapter<T, VH> adapter,
      View view,
      int position
    );
  }

  public interface OnItemChildClickListener<T> {
    void onItemChildClick(
      BaseQuickAdapter<T, VH> adapter,
      View view,
      int position
    );
  }

  public interface OnItemChildLongClickListener<T> {
    boolean onItemChildLongClick(
      BaseQuickAdapter<T, VH> adapter,
      View view,
      int position
    );
  }

  public interface OnViewAttachStateChangeListener {
    void onViewAttachedToWindow(RecyclerView.ViewHolder holder);

    void onViewDetachedFromWindow(RecyclerView.ViewHolder holder);
  }
}
