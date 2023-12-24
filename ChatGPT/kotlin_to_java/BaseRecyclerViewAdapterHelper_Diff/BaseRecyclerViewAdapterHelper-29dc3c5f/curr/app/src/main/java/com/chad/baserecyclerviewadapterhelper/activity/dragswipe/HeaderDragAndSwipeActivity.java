
package com.chad.baserecyclerviewadapterhelper.activity.dragswipe;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.baserecyclerviewadapterhelper.R;
import com.chad.baserecyclerviewadapterhelper.activity.dragswipe.adapter.HeaderDragAndSwipeAdapter;
import com.chad.baserecyclerviewadapterhelper.activity.home.adapter.HomeTopHeaderAdapter;
import com.chad.baserecyclerviewadapterhelper.base.BaseActivity;
import com.chad.baserecyclerviewadapterhelper.utils.VibratorUtils;

import com.chad.library.adapter.base.QuickAdapterHelper;
import com.chad.library.adapter.base.dragswipe.DragSwipeUtils;
import com.chad.library.adapter.base.loadstate.LoadState;
import com.chad.library.adapter.base.loadstate.trailing.TrailingLoadStateAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import java.util.List;

import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.delay;
import kotlinx.coroutines.launch;
import kotlinx.coroutines.withContext;

public class HeaderDragAndSwipeActivity extends BaseActivity {
    private static final int PAGE_SIZE = 20;
    private PageInfo pageInfo = new PageInfo();
    private HeaderDragAndSwipe headerDragAndSwipe = new HeaderDragAndSwipe()
            .setDragMoveFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN)
            .setSwipeMoveFlags(ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
    private HeaderDragAndSwipeAdapter mAdapter = new HeaderDragAndSwipeAdapter();
    private QuickAdapterHelper helper;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_header_drag_and_swipe);
        setBackBtn();
        setTitle("Head Drag And Swipe");
        RecyclerView mRVDragAndSwipe = findViewById(R.id.mRVDragAndSwipe);
        mRVDragAndSwipe.setLayoutManager(new LinearLayoutManager(this));
        helper = new QuickAdapterHelper.Builder(mAdapter)
                .setTrailingLoadStateAdapter(new TrailingLoadStateAdapter.OnTrailingListener() {
                    @Override
                    public void onLoad() {
                        loadMore();
                    }

                    @Override
                    public void onFailRetry() {

                    }

                    @Override
                    public boolean isAllowLoading() {
                        return true;
                    }
                })
                .build().addHeader(new HomeTopHeaderAdapter());

        DragSwipeUtils.attachToRecyclerView(headerDragAndSwipe, mRVDragAndSwipe)
                .setDataCallback(mAdapter)
                .setItemDragListener(new ItemDragListener() {
                    @Override
                    public void onItemDragStart(RecyclerView.ViewHolder viewHolder, int pos) {
                        Log.d(TAG, "drag start");
                        VibratorUtils.vibrate(getApplicationContext());
                        BaseViewHolder holder = (BaseViewHolder) viewHolder;

                        int startColor = Color.WHITE;
                        int endColor = Color.rgb(245, 245, 245);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            ValueAnimator.ofArgb(startColor, endColor).apply {
                                addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                    @Override
                                    public void onAnimationUpdate(ValueAnimator animation) {
                                        holder.itemView.setBackgroundColor((int) animation.getAnimatedValue());
                                    }
                                });
                                setDuration(300);
                                start();
                            }
                        }
                    }

                    @Override
                    public void onItemDragMoving(RecyclerView.ViewHolder source, int from, RecyclerView.ViewHolder target, int to) {
                        Log.d(TAG, "move from: " + from + "  to:  " + to);
                    }

                    @Override
                    public void onItemDragEnd(RecyclerView.ViewHolder viewHolder, int pos) {
                        Log.d(TAG, "drag end");
                        BaseViewHolder holder = (BaseViewHolder) viewHolder;

                        int startColor = Color.rgb(245, 245, 245);
                        int endColor = Color.WHITE;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            ValueAnimator.ofArgb(startColor, endColor).apply {
                                addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                    @Override
                                    public void onAnimationUpdate(ValueAnimator animation) {
                                        holder.itemView.setBackgroundColor((int) animation.getAnimatedValue());
                                    }
                                });
                                setDuration(300);
                                start();
                            }
                        }
                    }
                })
                .setItemSwipeListener(new ItemSwipeListener() {
                    @Override
                    public void onItemSwipeStart(RecyclerView.ViewHolder viewHolder, int pos) {
                        Log.d(TAG, "onItemSwipeStart");
                    }

                    @Override
                    public void onItemSwipeMoving(Canvas canvas, RecyclerView.ViewHolder viewHolder, float dX, float dY, boolean isCurrentlyActive) {
                        Log.d(TAG, "onItemSwipeMoving");
                    }

                    @Override
                    public void onItemSwiped(RecyclerView.ViewHolder viewHolder, int pos) {
                        Log.d(TAG, "onItemSwiped");
                    }

                    @Override
                    public void onItemSwipeEnd(RecyclerView.ViewHolder viewHolder, int pos) {
                        Log.d(TAG, "onItemSwipeEnd");
                    }
                });
        mRVDragAndSwipe.setAdapter(helper.getAdapter());
        loadMore();
    }

    private void loadMore() {
        Request request = new Request(pageInfo.page, new RequestCallBack() {
            @Override
            public void success(List<String> data) {
                if (pageInfo.page == 0) {
                    mAdapter.submitList(data);
                } else {
                    mAdapter.addAll(data);
                }
                helper.getTrailingLoadStateAdapter().checkDisableLoadMoreIfNotFullPage();
                helper.setTrailingLoadState(LoadState.NotLoading.INSTANCE);

                pageInfo.nextPage();
            }

            @Override
            public void fail(Exception e) {

            }

            @Override
            public void end() {
                helper.setTrailingLoadState(LoadState.NotLoading.INSTANCE);
            }
        });
        request.loadMore();
    }

    private static class PageInfo {
        private int page = 0;

        public void nextPage() {
            page++;
        }

        public void reset() {
            page = 0;
        }

        public boolean isFirstPage() {
            return page == 0;
        }
    }

    private static class Request {
        private int mPage;
        private RequestCallBack mCallBack;

        public Request(int page, RequestCallBack callBack) {
            mPage = page;
            mCallBack = callBack;
        }

        public void loadMore() {
            GlobalScope.launch(Dispatchers.IO) {
                if (mPage != 0) {
                    delay(1500);
                }
                withContext(Dispatchers.Main) {
                    int size = PAGE_SIZE;
                    if (mPage == 3) {
                        mCallBack.end();
                    } else {
                        int starIndex = mPage * size;
                        mCallBack.success(generateData(starIndex, size));
                    }
                }
            }
        }

        private List<String> generateData(int starIndex, int size) {
            List<String> data = new ArrayList<>(size);
            int endIndex = starIndex + size;
            for (int i = starIndex; i < endIndex; i++) {
                data.add("item " + i);
            }
            return data;
        }
    }

    private interface RequestCallBack {
        void success(List<String> data);

        void fail(Exception e);

        void end();
    }
}
