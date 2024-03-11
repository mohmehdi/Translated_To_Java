package com.google.samples.apps.iosched.ui.schedule;

import android.arch.lifecycle.ViewModelProvider;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.databinding.FragmentScheduleBinding;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import com.google.samples.apps.iosched.shared.util.activityViewModelProvider;
import com.google.samples.apps.iosched.ui.schedule.agenda.ScheduleAgendaFragment;

import javax.inject.Inject;

public class ScheduleFragment extends DaggerFragment {

    public static final String TAG = ScheduleFragment.class.getSimpleName();
    public static final int COUNT = ConferenceDay.values().length + 1;
    public static final int AGENDA_POSITION = COUNT - 1;

    @Inject
    ViewModelProvider.Factory viewModelFactory;

    private ScheduleViewModel viewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        viewModel = activityViewModelProvider(viewModelFactory);
        FragmentScheduleBinding binding = DataBindingUtil.inflate(
                inflater, R.layout.fragment_schedule, container, false);

        binding.setViewModel(viewModel);
        binding.setLifecycleOwner(this);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        viewpager.setOffscreenPageLimit(COUNT - 1);
        viewpager.setAdapter(new ScheduleAdapter(getChildFragmentManager()));
        tabs.setupWithViewPager(viewpager);
    }

    private class ScheduleAdapter extends FragmentPagerAdapter {

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