package com.chad.library.adapter.base;

import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public abstract class BaseSingleItemAdapter<T, VH extends RecyclerView.ViewHolder> extends BaseQuickAdapter<T, VH> {


private T mItem;

public BaseSingleItemAdapter(T mItem) {
    this(mItem, null);
}

public BaseSingleItemAdapter(T mItem, int layoutResId) {
    super(layoutResId, null);
    this.mItem = mItem;
}

protected abstract void onBindViewHolder(VH holder, T item);

public void onBindViewHolder(VH holder, T item, List<Object> payloads) {
    onBindViewHolder(holder, item);
}

@Override
public void onBindViewHolder(VH holder, int position, T item) {
    onBindViewHolder(holder, mItem);
}

@Override
public void onBindViewHolder(VH holder, int position, T item, List<Object> payloads) {
    onBindViewHolder(holder, item, payloads);
}

@Override
public int getItemCount(List<T> items) {
    return 1;
}

public void setItem(T t, Object payload) {
    mItem = t;
    notifyItemChanged(0, payload);
}

public T getItem() {
    return mItem;
}

public void setItem(T t) {
    mItem = t;
    notifyItemChanged(0);
}

@Override
public void submitList(List<T> list) {
    throw new RuntimeException("Please use setItem()");
}

@Override
public void add(T data) {
    throw new RuntimeException("Please use setItem()");
}

@Override
public void add(int position, T data) {
    throw new RuntimeException("Please use setItem()");
}

@Override
public void addAll(Collection<T> collection) {
    throw new RuntimeException("Please use setItem()");
}

@Override
public void addAll(int position, Collection<T> collection) {
    throw new RuntimeException("Please use setItem()");
}

@Override
public void remove(T data) {
    throw new RuntimeException("Please use setItem()");
}

@Override
public void removeAt(int position) {
    throw new RuntimeException("Please use setItem()");
}

@Override
public void set(int position, T data) {
    throw new RuntimeException("Please use setItem()");
}
}