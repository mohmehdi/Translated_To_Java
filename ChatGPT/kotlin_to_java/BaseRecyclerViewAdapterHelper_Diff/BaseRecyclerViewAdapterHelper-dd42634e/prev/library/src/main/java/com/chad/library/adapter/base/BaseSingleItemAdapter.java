package com.chad.library.adapter.base;

import androidx.recyclerview.widget.RecyclerView;

public abstract class BaseSingleItemAdapter<T, VH extends RecyclerView.ViewHolder> extends BaseQuickAdapter<T, VH> {

    private T mItem = null;

    protected abstract void onBindViewHolder(VH holder, T item);

    @Override
    public void onBindViewHolder(VH holder, int position, T item) {
        onBindViewHolder(holder, mItem);
    }

    @Override
    public void onBindViewHolder(VH holder, int position, T item, java.util.List<Object> payloads) {
        onBindViewHolder(holder, item, payloads);
    }

    @Override
    public int getItemCount(java.util.List<T> items) {
        return 1;
    }

    public void setItem(T t, Object payload) {
        mItem = t;
        notifyItemChanged(0, payload);
    }

    public T getItem() {
        return mItem;
    }

    public void setItem(T value) {
        mItem = value;
        notifyItemChanged(0);
    }

    @Override
    public void submitList(java.util.List<T> list) {
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
    public void addAll(java.util.Collection<T> collection) {
        throw new RuntimeException("Please use setItem()");
    }

    @Override
    public void addAll(int position, java.util.Collection<T> collection) {
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
