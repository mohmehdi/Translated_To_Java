
package com.chad.baserecyclerviewadapterhelper.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import com.chad.baserecyclerviewadapterhelper.R;

public class HeaderAdapter extends RecyclerView.Adapter<HeaderAdapter.VH> {
    private View.OnClickListener click;

    public HeaderAdapter(View.OnClickListener click) {
        this.click = click;
    }

    public static class VH extends RecyclerView.ViewHolder {
        public VH(View view) {
            super(view);
        }
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.head_view, parent, false);
        VH vh = new VH(view);
        vh.itemView.setOnClickListener(click);
        return vh;
    }

    @Override
    public void onBindViewHolder(VH holder, int position) {
    }

    @Override
    public int getItemCount() {
        return 1;
    }
}
