package com.chad.baserecyclerviewadapterhelper.activity.home;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.chad.baserecyclerviewadapterhelper.R;
import com.chad.baserecyclerviewadapterhelper.activity.animation.AnimationUseActivity;
import com.chad.baserecyclerviewadapterhelper.activity.databinding.DataBindingUseActivity;
import com.chad.baserecyclerviewadapterhelper.activity.differ.DifferActivity;
import com.chad.baserecyclerviewadapterhelper.activity.dragswipe.DragAndSwipeUseActivity;
import com.chad.baserecyclerviewadapterhelper.activity.emptyview.EmptyViewUseActivity;
import com.chad.baserecyclerviewadapterhelper.activity.headerfooter.HeaderAndFooterUseActivity;
import com.chad.baserecyclerviewadapterhelper.activity.home.adapter.HomeAdapter;
import com.chad.baserecyclerviewadapterhelper.activity.home.adapter.HomeTopHeaderAdapter;
import com.chad.baserecyclerviewadapterhelper.activity.itemclick.ItemClickActivity;
import com.chad.baserecyclerviewadapterhelper.activity.loadmore.AutoLoadMoreRefreshUseActivity;
import com.chad.baserecyclerviewadapterhelper.activity.loadmore.NoAutoAutoLoadMoreRefreshUseActivity;
import com.chad.baserecyclerviewadapterhelper.activity.upfetch.UpFetchUseActivity;
import com.chad.baserecyclerviewadapterhelper.databinding.ActivityHomeBinding;
import com.chad.baserecyclerviewadapterhelper.entity.HomeEntity;
import com.chad.library.adapter.base.QuickAdapterHelper;

import java.util.ArrayList;

public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;

    private HomeAdapter homeAdapter;

    private QuickAdapterHelper helper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        homeAdapter = new HomeAdapter(getHomeItemData());

        helper = new QuickAdapterHelper.Builder(homeAdapter)
                .build()
                .addHeader(new HomeTopHeaderAdapter());

        binding.getRecyclerView().setAdapter(helper.getAdapter());

        homeAdapter.setOnItemClickListener((adapter, view, position) -> {
            HomeEntity item = (HomeEntity) adapter.getItems().get(position);
            if (!item.isSection()) {
                startActivity(new Intent(HomeActivity.this, item.getActivity()));
            }
        });
    }

    private ArrayList<HomeEntity> getHomeItemData() {
        ArrayList<HomeEntity> homeItemData = new ArrayList<>();
        homeItemData.add(new HomeEntity("BaseQuickAdapter 基础功能"));
        homeItemData.add(new HomeEntity("Animation", AnimationUseActivity.class, R.mipmap.gv_animation));
        homeItemData.add(new HomeEntity("Header/Footer", HeaderAndFooterUseActivity.class, R.mipmap.gv_header_and_footer));
        homeItemData.add(new HomeEntity("EmptyView", EmptyViewUseActivity.class, R.mipmap.gv_empty));
        homeItemData.add(new HomeEntity("ItemClick", ItemClickActivity.class, R.mipmap.gv_item_click));
        homeItemData.add(new HomeEntity("DataBinding", DataBindingUseActivity.class, R.mipmap.gv_databinding));
        homeItemData.add(new HomeEntity("DiffUtil", DifferActivity.class, R.mipmap.gv_databinding));

        homeItemData.add(new HomeEntity("功能模块"));
        homeItemData.add(new HomeEntity("LoadMore(Auto)", AutoLoadMoreRefreshUseActivity.class, R.mipmap.gv_pulltorefresh));
        homeItemData.add(new HomeEntity("LoadMore", NoAutoAutoLoadMoreRefreshUseActivity.class, R.mipmap.gv_pulltorefresh));
        homeItemData.add(new HomeEntity("DragAndSwipe", DragAndSwipeUseActivity.class, R.mipmap.gv_drag_and_swipe));
        homeItemData.add(new HomeEntity("UpFetch", UpFetchUseActivity.class, R.drawable.gv_up_fetch));

        return homeItemData;
    }
}