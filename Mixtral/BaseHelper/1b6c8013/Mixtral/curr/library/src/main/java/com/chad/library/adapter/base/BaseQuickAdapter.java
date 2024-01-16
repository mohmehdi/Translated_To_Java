package com.chad.library.adapter.base;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class BaseQuickAdapter<T, VH extends RecyclerView.ViewHolder>
  extends BrvahAdapter<T, VH> {

  private List<T> items;
  private int mLastPosition = -1;

  public BaseQuickAdapter(List<T> items) {
    this.items = items != null ? items : Collections.emptyList();
  }

  public void setList(List<T> list) {
    if (list == this.items) {
      return;
    }

    this.items = list != null ? list : Collections.emptyList();
    mLastPosition = -1;
    notifyDataSetChanged();
  }

  public void set(@IntRange(from = 0) int position, T data) {
    if (position >= this.items.size()) {
      return;
    }

    if (items instanceof MutableList) {
      ((MutableList<T>) items).set(position, data);
      notifyItemChanged(position);
    }
  }

  public void add(@IntRange(from = 0) int position, T data) {
    if (items instanceof MutableList) {
      ((MutableList<T>) items).add(position, data);
      notifyItemInserted(position);
    }
  }

  public void add(T data) {
    if (items instanceof MutableList) {
      ((MutableList<T>) items).add(data);
      notifyItemInserted(items.size());
    }
  }

  public void add(@IntRange(from = 0) int position, Collection<T> newData) {
    if (items instanceof MutableList) {
      ((MutableList<T>) items).addAll(position, newData);
      notifyItemRangeInserted(position, newData.size());
    }
  }

  public void addAll(Collection<T> newData) {
    if (items instanceof MutableList) {
      int oldSize = items.size();
      ((MutableList<T>) items).addAll(newData);
      notifyItemRangeInserted(oldSize, newData.size());
    }
  }

  public void removeAt(@IntRange(from = 0) int position) {
    if (position >= items.size()) {
      return;
    }

    if (items instanceof MutableList) {
      ((MutableList<T>) items).removeAt(position);
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
