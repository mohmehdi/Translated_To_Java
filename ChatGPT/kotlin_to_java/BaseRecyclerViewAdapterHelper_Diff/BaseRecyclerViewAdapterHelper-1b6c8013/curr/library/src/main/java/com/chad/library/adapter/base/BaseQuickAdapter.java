
package com.chad.library.adapter.base;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collection;
import java.util.List;

public abstract class BaseQuickAdapter<T, VH extends RecyclerView.ViewHolder> extends BrvahAdapter<T, VH> {

    public BaseQuickAdapter(List<T> items) {
        super(items);
    }

    public void setList(List<T> list) {
        if (list == this.items) {
            return;
        }

        this.items = list != null ? list : new ArrayList<>();
        mLastPosition = -1;
        notifyDataSetChanged();
    }

    public void set(@IntRange(from = 0) int position, T data) {
        if (position >= this.items.size()) {
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

    public void addAll(@NonNull Collection<T> newData) {
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
}
