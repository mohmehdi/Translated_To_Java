package com.chad.library.adapter.base;

import androidx.recyclerview.widget.RecyclerView;

public abstract class BaseSingleItemAdapter<T, VH extends RecyclerView.ViewHolder> extends BaseQuickAdapter<Object, VH> {

    private T mItem;

    protected abstract void onBindViewHolder(VH holder, T item);

    public void onBindViewHolder(VH holder, T item, List<Object> payloads) {
        onBindViewHolder(holder, item);
    }

    @Override
    public void onBindViewHolder(VH holder, int position, Object item) {
        onBindViewHolder(holder, mItem);
    }

    @Override
    public void onBindViewHolder(VH holder, int position, Object item, List<Object> payloads) {
        onBindViewHolder(holder, mItem, payloads);
    }

    @Override
    public int getItemCount(List<Object> items) {
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
    public void submitList(List<Object> list) {
        throw new RuntimeException("Please use setItem()");
    }

    @Override
    public void add(Object data) {
        throw new RuntimeException("Please use setItem()");
    }

    @Override
    public void add(int position, Object data) {
        throw new RuntimeException("Please use setItem()");
    }

    @Override
    public void addAll(Collection<Object> collection) {
        throw new RuntimeException("Please use setItem()");
    }

    @Override
    public void addAll(int position, Collection<Object> collection) {
        throw new RuntimeException("Please use setItem()");
    }

    @Override
    public void remove(Object data) {
        throw new RuntimeException("Please use setItem()");
    }

    @Override
    public void removeAt(int position) {
        throw new RuntimeException("Please use setItem()");
    }

    @Override
    public void set(int position, Object data) {
        throw new RuntimeException("Please use setItem()");
    }
}