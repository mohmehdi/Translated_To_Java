
package com.chad.library.adapter.base.viewholder;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.RecyclerView;

public class EmptyLayoutVH extends RecyclerView.ViewHolder {
    private FrameLayout emptyLayout;

    public EmptyLayoutVH(FrameLayout emptyLayout) {
        super(emptyLayout);
        this.emptyLayout = emptyLayout;
    }

    public void changeEmptyView(View view) {
        ViewParent emptyLayoutVp = view.getParent();
        if (emptyLayoutVp instanceof ViewGroup) {
            ((ViewGroup) emptyLayoutVp).removeView(view);
        }

        emptyLayout.removeAllViews();
        emptyLayout.addView(view);
    }
}
