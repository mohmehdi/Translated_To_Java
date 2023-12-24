
package com.google.samples.apps.sunflower;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.samples.apps.sunflower.adapters.MY_GARDEN_PAGE_INDEX;
import com.google.samples.apps.sunflower.adapters.PLANT_LIST_PAGE_INDEX;
import com.google.samples.apps.sunflower.adapters.SunflowerPagerAdapter;
import com.google.samples.apps.sunflower.databinding.FragmentViewPagerBinding;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class HomeViewPagerFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FragmentViewPagerBinding binding = FragmentViewPagerBinding.inflate(inflater, container, false);
        TabLayout tabLayout = binding.tabs;
        ViewPager2 viewPager = binding.viewPager;

        viewPager.setAdapter(new SunflowerPagerAdapter(this));

        TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setIcon(getTabIcon(position));
            tab.setText(getTabTitle(position));
        }).attach();

        ((AppCompatActivity) getActivity()).setSupportActionBar(binding.toolbar);

        return binding.getRoot();
    }

    private int getTabIcon(int position) {
        switch (position) {
            case MY_GARDEN_PAGE_INDEX:
                return R.drawable.garden_tab_selector;
            case PLANT_LIST_PAGE_INDEX:
                return R.drawable.plant_list_tab_selector;
            default:
                throw new IndexOutOfBoundsException();
        }
    }

    private String getTabTitle(int position) {
        switch (position) {
            case MY_GARDEN_PAGE_INDEX:
                return getString(R.string.my_garden_title);
            case PLANT_LIST_PAGE_INDEX:
                return getString(R.string.plant_list_title);
            default:
                return null;
        }
    }
}
