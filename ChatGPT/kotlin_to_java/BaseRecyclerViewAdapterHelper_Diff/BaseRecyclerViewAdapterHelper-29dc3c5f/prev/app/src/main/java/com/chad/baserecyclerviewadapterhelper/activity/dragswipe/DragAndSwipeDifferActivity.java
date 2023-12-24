
package com.chad.baserecyclerviewadapterhelper.activity.dragswipe;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.baserecyclerviewadapterhelper.R;
import com.chad.baserecyclerviewadapterhelper.adapter.DiffDragAndSwipeAdapter;
import com.chad.baserecyclerviewadapterhelper.base.BaseActivity;
import com.chad.baserecyclerviewadapterhelper.data.DataServer;
import com.chad.baserecyclerviewadapterhelper.utils.VibratorUtils;

import com.chad.library.adapter.base.dragswipe.listener.DragAndSwipeDataCallback;
import com.chad.library.adapter.base.dragswipe.QuickDragAndSwipe;
import com.chad.library.adapter.base.dragswipe.listener.OnItemDragListener;
import com.chad.library.adapter.base.dragswipe.listener.OnItemSwipeListener;
import com.chad.library.adapter.base.viewholder.QuickViewHolder;

public class DragAndSwipeDifferActivity extends BaseActivity {
    private RecyclerView mRecyclerView;
    private DiffDragAndSwipeAdapter mAdapter = new DiffDragAndSwipeAdapter();
    private QuickDragAndSwipe quickDragAndSwipe = new QuickDragAndSwipe()
            .setDragMoveFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN)
            .setSwipeMoveFlags(ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_universal_recycler);
        setBackBtn();
        setTitle("Diff Drag Swipe Use");
        findView();
        initRv();
        initDrag();
    }

    private void findView() {
        mRecyclerView = findViewById(R.id.rv);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void initRv() {
        mRecyclerView.setAdapter(mAdapter);
        mAdapter.submitList(DataServer.getDiffUtilDemoEntities());
    }

    private void initDrag() {
        OnItemDragListener listener = new OnItemDragListener() {
            @Override
            public void onItemDragStart(RecyclerView.ViewHolder viewHolder, int pos) {
                VibratorUtils.vibrate(getApplicationContext());
                Log.d(TAG, "drag start");
                QuickViewHolder holder = (QuickViewHolder) viewHolder;
                int startColor = Color.WHITE;
                int endColor = Color.rgb(245, 245, 245);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ValueAnimator v = ValueAnimator.ofArgb(startColor, endColor);
                    v.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            holder.itemView.setBackgroundColor((int) animation.getAnimatedValue());
                        }
                    });
                    v.setDuration(300);
                    v.start();
                }
            }

            @Override
            public void onItemDragMoving(RecyclerView.ViewHolder source, int from, RecyclerView.ViewHolder target, int to) {
                Log.d(TAG, "move from: " + source.getBindingAdapterPosition() + " to: " + target.getBindingAdapterPosition());
            }

            @Override
            public void onItemDragEnd(RecyclerView.ViewHolder viewHolder, int pos) {
                Log.d(TAG, "drag end");
                QuickViewHolder holder = (QuickViewHolder) viewHolder;
                int startColor = Color.rgb(245, 245, 245);
                int endColor = Color.WHITE;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ValueAnimator v = ValueAnimator.ofArgb(startColor, endColor);
                    v.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            holder.itemView.setBackgroundColor((int) animation.getAnimatedValue());
                        }
                    });
                    v.setDuration(300);
                    v.start();
                }
            }
        };

        OnItemSwipeListener swipeListener = new OnItemSwipeListener() {
            @Override
            public void onItemSwipeStart(RecyclerView.ViewHolder viewHolder, int pos) {
                Log.d(TAG, "onItemSwipeStart");
            }

            @Override
            public void onItemSwipeEnd(RecyclerView.ViewHolder viewHolder, int pos) {
                Log.d(TAG, "onItemSwipeEnd");
            }

            @Override
            public void onItemSwiped(RecyclerView.ViewHolder viewHolder, int pos) {
                Log.d(TAG, "onItemSwiped");
            }

            @Override
            public void onItemSwipeMoving(Canvas canvas, RecyclerView.ViewHolder viewHolder, float dX, float dY, boolean isCurrentlyActive) {
                Log.d(TAG, "onItemSwipeMoving");
            }
        };

        quickDragAndSwipe.attachToRecyclerView(mRecyclerView)
                .setDataCallback(new DragAndSwipeDataCallback() {
                    @Override
                    public void dataSwap(int fromPosition, int toPosition) {
                        mAdapter.swap(fromPosition, toPosition);
                    }

                    @Override
                    public void dataRemoveAt(int position) {
                        mAdapter.removeAt(position);
                    }
                })
                .setItemDragListener(listener)
                .setItemSwipeListener(swipeListener);
    }
}
