package com.chad.library.adapter.base;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public abstract class BaseSingleItemAdapter<T, VH extends RecyclerView.ViewHolder> extends BaseQuickAdapter<Any, VH> {
    private T mItem;

    public BaseSingleItemAdapter(T mItem) {
        this.mItem = mItem;
    }

    protected abstract void onBindViewHolder(VH holder, T item);

    public void onBindViewHolder(VH holder, T item, List<Object> payloads) {
        onBindViewHolder(holder, item);
    }

    @Override
    public void onBindViewHolder(VH holder, int position, Any item) {
        onBindViewHolder(holder, mItem);
    }

    @Override
    public void onBindViewHolder(VH holder, int position, Any item, List<Object> payloads) {
        onBindViewHolder(holder, mItem, payloads);
    }

    @Override
    public int getItemCount(List<Any> items) {
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
    public void submitList(List<Any> list) {
        throw new RuntimeException("Please use setItem()");
    }

    @Override
    public void add(Any data) {
        throw new RuntimeException("Please use setItem()");
    }

    @Override
    public void add(int position, Any data) {
        throw new RuntimeException("Please use setItem()");
    }

    @Override
    public void addAll(Collection<Any> collection) {
        throw new RuntimeException("Please use setItem()");
    }

    @Override
    public void addAll(int position, Collection<Any> collection) {
        throw new RuntimeException("Please use setItem()");
    }

    @Override
    public void remove(Any data) {
        throw new RuntimeException("Please use setItem()");
    }

    @Override
    public void removeAt(int position) {
        throw new RuntimeException("Please use setItem()");
    }

    @Override
    public void set(int position, Any data) {
        throw new RuntimeException("Please use setItem()");
    }
}