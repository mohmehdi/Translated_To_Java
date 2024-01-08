package com.google.samples.apps.iosched.ui.schedule;

import android.arch.lifecycle.ViewModelProvider;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.databinding.FragmentScheduleBinding;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import com.google.samples.apps.iosched.shared.util.ViewModelUtil;
import com.google.samples.apps.iosched.ui.schedule.agenda.ScheduleAgendaFragment;

import javax.inject.Inject;

public class ScheduleFragment extends DaggerFragment {

    private static final String TAG = ScheduleFragment.class.getSimpleName();
    private static final int COUNT = ConferenceDay.values().length + 1;
    private static final int AGENDA_POSITION = COUNT - 1;

    @Inject
    ViewModelProvider.Factory viewModelFactory;

    private ScheduleViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        viewModel = ViewModelUtil.getViewModel(this, viewModelFactory);
        FragmentScheduleBinding binding = DataBindingUtil.inflate(inflater, R.layout.fragment_schedule, container, false);

        binding.setViewModel(viewModel);
        binding.setLifecycleOwner(this);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewpager.setOffscreenPageLimit(COUNT - 1);
        viewpager.setAdapter(new ScheduleAdapter(getChildFragmentManager()));
        tabs.setupWithViewPager(viewpager);
    }

    private class ScheduleAdapter extends FragmentPagerAdapter {

        ScheduleAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return COUNT;
        }

        @Override
        public Fragment getItem(int position) {
            if (position == AGENDA_POSITION) {
                return new ScheduleAgendaFragment();
            } else {
                return ScheduleDayFragment.newInstance(ConferenceDay.values()[position]);
            }
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (position == AGENDA_POSITION) {
                return getString(R.string.agenda);
            } else {
                return ConferenceDay.values()[position].formatMonthDay();
            }
        }
    }
}