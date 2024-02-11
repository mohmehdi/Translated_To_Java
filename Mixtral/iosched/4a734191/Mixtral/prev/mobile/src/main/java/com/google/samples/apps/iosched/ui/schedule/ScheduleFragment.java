

package com.google.samples.apps.iosched.ui.schedule;

import android.arch.lifecycle.ViewModelProvider;
import android.arch.lifecycle.ViewModelStoreOwner;
import android.content.Context;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.databinding.FragmentScheduleBinding;
import com.google.samples.apps.iosched.shared.util.TimeUtils;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import com.google.samples.apps.iosched.ui.schedule.ScheduleFragment.ScheduleAdapter;
import javax.inject.Inject;
import javax.inject.Provider;

public class ScheduleFragment extends DaggerFragment {

    public static final String TAG = ScheduleFragment.class.getSimpleName();
    public static final int COUNT = ConferenceDay.values().length;

    @Inject
    Provider<ViewModelProvider.Factory> viewModelFactoryProvider;

    private ScheduleViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider((ViewModelStoreOwner) requireActivity(),
                viewModelFactoryProvider.get()).get(ScheduleViewModel.class);

        FragmentScheduleBinding binding = DataBindingUtil.inflate(inflater,
                R.layout.fragment_schedule, container, false);
        binding.setViewModel(viewModel);
        binding.setLifecycleOwner(this);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ViewPager viewpager = view.findViewById(R.id.viewpager);
        viewpager.setOffscreenPageLimit(COUNT - 1);
        viewpager.setAdapter(new ScheduleAdapter(getChildFragmentManager()));
        TabLayout tabs = view.findViewById(R.id.tabs);
        tabs.setupWithViewPager(viewpager);
    }

    static class ScheduleAdapter extends FragmentPagerAdapter {

        ScheduleAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return COUNT;
        }

        @Override
        public Fragment getItem(int position) {
            return ScheduleDayFragment.newInstance(ConferenceDay.values()[position]);
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return ConferenceDay.values()[position].formatMonthDay();
        }
    }
}