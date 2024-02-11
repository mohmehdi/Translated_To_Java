

package com.google.samples.apps.iosched.ui.schedule;

import android.arch.lifecycle.ViewModelProvider;
import android.arch.lifecycle.ViewModelStoreOwner;
import android.content.res.Resources;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.databinding.FragmentScheduleBinding;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import com.google.samples.apps.iosched.ui.schedule.agenda.ScheduleAgendaFragment;
import com.google.samples.apps.iosched.ui.schedule.day.ScheduleDayFragment;
import com.google.samples.apps.iosched.ui.schedule.day.ScheduleDayFragmentArgs;
import dagger.android.support.DaggerFragment;
import javax.inject.Inject;

public class ScheduleFragment extends DaggerFragment {

    public static final String TAG = ScheduleFragment.class.getSimpleName();
    public static final int COUNT = ConferenceDay.values().length + 1;
    public static final int AGENDA_POSITION = COUNT - 1;

    @Inject
    ViewModelProvider.Factory viewModelFactory;

    private ScheduleViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        viewModel = ViewModelProviders.of((ViewModelStoreOwner) requireActivity(), viewModelFactory)
                .get(ScheduleViewModel.class);

        FragmentScheduleBinding binding = DataBindingUtil.inflate(inflater, R.layout.fragment_schedule, container, false);
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

    public static class ScheduleAdapter extends FragmentPagerAdapter {

        public ScheduleAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return COUNT;
        }

        @Override
        public Fragment getItem(int position) {
            if (position == AGENDA_POSITION) {
                return ScheduleAgendaFragment.newInstance();
            } else {
                Resources resources = ScheduleFragment.this.getResources();
                ConferenceDay conferenceDay = ConferenceDay.values()[position];
                Bundle bundle = new Bundle();
                bundle.putSerializable("conferenceDay", conferenceDay);
                ScheduleDayFragment scheduleDayFragment = new ScheduleDayFragment();
                scheduleDayFragment.setArguments(bundle);
                return scheduleDayFragment;
            }
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            if (position == AGENDA_POSITION) {
                return ScheduleFragment.this.getString(R.string.agenda);
            } else {
                ConferenceDay conferenceDay = ConferenceDay.values()[position];
                return conferenceDay.formatMonthDay();
            }
        }
    }
}