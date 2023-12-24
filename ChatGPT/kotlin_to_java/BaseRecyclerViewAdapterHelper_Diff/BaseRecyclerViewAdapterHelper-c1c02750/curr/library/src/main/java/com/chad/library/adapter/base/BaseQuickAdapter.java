
package com.chad.library.adapter.base;

import android.animation.Animator;
import android.content.Context;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.chad.library.adapter.base.animation.AlphaInAnimation;
import com.chad.library.adapter.base.animation.AnimationType;
import com.chad.library.adapter.base.animation.ItemAnimator;
import com.chad.library.adapter.base.animation.ScaleInAnimation;
import com.chad.library.adapter.base.animation.SlideInBottomAnimation;
import com.chad.library.adapter.base.animation.SlideInLeftAnimation;
import com.chad.library.adapter.base.animation.SlideInRightAnimation;
import com.chad.library.adapter.base.module.BaseDraggableModule;
import com.chad.library.adapter.base.viewholder.EmptyLayoutVH;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class BaseQuickAdapter<T, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private int mLastPosition = -1;
    private OnItemClickListener<T> mOnItemClickListener;
    private OnItemLongClickListener<T> mOnItemLongClickListener;
    private SparseArray<OnItemChildClickListener<T>> mOnItemChildClickArray = new SparseArray<>(3);
    private SparseArray<OnItemChildLongClickListener<T>> mOnItemChildLongClickArray = new SparseArray<>(3);
    private List<OnViewAttachStateChangeListener> onViewAttachStateChangeListeners;

    private RecyclerView _recyclerView;

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

    private boolean isEmptyViewEnable = false;

    public boolean isEmptyViewEnable() {
        return isEmptyViewEnable;
    }

    public void setEmptyViewEnable(boolean isEmptyViewEnable) {
        boolean oldDisplayEmptyLayout = displayEmptyView();

        this.isEmptyViewEnable = isEmptyViewEnable;

        boolean newDisplayEmptyLayout = displayEmptyView();

        if (oldDisplayEmptyLayout && !newDisplayEmptyLayout) {
            notifyItemRemoved(0);
        } else if (newDisplayEmptyLayout && !oldDisplayEmptyLayout) {
            notifyItemInserted(0);
        } else if (oldDisplayEmptyLayout && newDisplayEmptyLayout) {
            notifyItemChanged(0, EMPTY_PAYLOAD);
        }
    }

    private View emptyView;

    public View getEmptyView() {
        return emptyView;
    }

    public void setEmptyView(View emptyView) {
        boolean oldDisplayEmptyLayout = displayEmptyView();

        this.emptyView = emptyView;

        boolean newDisplayEmptyLayout = displayEmptyView();

        if (oldDisplayEmptyLayout && !newDisplayEmptyLayout) {
            notifyItemRemoved(