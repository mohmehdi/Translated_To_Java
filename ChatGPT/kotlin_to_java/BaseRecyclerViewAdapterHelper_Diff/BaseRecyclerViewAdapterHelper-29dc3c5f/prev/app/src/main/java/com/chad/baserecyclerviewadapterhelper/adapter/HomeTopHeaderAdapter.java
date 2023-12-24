
package com.chad.baserecyclerviewadapterhelper.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import com.chad.baserecyclerviewadapterhelper.R;
import com.chad.library.adapter.base.fullspan.FullSpanAdapterType;

public class HomeTopHeaderAdapter extends RecyclerView.Adapter<HomeTopHeaderAdapter.VH> implements FullSpanAdapterType {

    private static final int HEAD_VIEWTYPE = 0x10000556;

    static class VH extends RecyclerView.ViewHolder {
        VH(View view) {
            super(view);
        }
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.top_view, parent, false));
    }

    @Override
    public int getItemViewType(int position) {
        return HEAD_VIEWTYPE;
    }

    @Override
    public void onBindViewHolder(VH holder, int position) {
    }

    @Override
    public int getItemCount() {
        return 1;
    }
}
