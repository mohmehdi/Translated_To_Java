package com.google.samples.apps.iosched.ui.schedule;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.updateLayoutParams;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.databinding.FragmentScheduleBinding;
import com.google.samples.apps.iosched.model.SessionId;
import com.google.samples.apps.iosched.shared.analytics.AnalyticsHelper;
import com.google.samples.apps.iosched.shared.result.EventObserver;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDays;
import com.google.samples.apps.iosched.shared.util.viewModelProvider;
import com.google.samples.apps.iosched.ui.MainNavigationFragment;
import com.google.samples.apps.iosched.ui.messages.SnackbarMessageManager;
import com.google.samples.apps.iosched.ui.prefs.SnackbarPreferenceViewModel;
import com.google.samples.apps.iosched.ui.schedule.day.ScheduleDayFragment;
import com.google.samples.apps.iosched.ui.sessiondetail.SessionDetailActivity;
import com.google.samples.apps.iosched.ui.setUpSnackbar;
import com.google.samples.apps.iosched.ui.signin.NotificationsPreferenceDialogFragment;
import com.google.samples.apps.iosched.ui.signin.SignInDialogFragment;
import com.google.samples.apps.iosched.ui.signin.SignOutDialogFragment;
import com.google.samples.apps.iosched.util.fabVisibility;
import com.google.samples.apps.iosched.widget.BottomSheetBehavior;
import com.google.samples.apps.iosched.widget.FadingSnackbar;

import javax.inject.Inject;

public class ScheduleFragment extends DaggerFragment implements MainNavigationFragment {

    private static final int COUNT = ConferenceDays.size;
    private static final String DIALOG_NEED_TO_SIGN_IN = "dialog_need_to_sign_in";
    private static final String DIALOG_CONFIRM_SIGN_OUT = "dialog_confirm_sign_out";
    private static final String DIALOG_SCHEDULE_HINTS = "dialog_schedule_hints";

    @Inject
    AnalyticsHelper analyticsHelper;

    @Inject
    ViewModelProvider.Factory viewModelFactory;

    private ScheduleViewModel scheduleViewModel;
    private CoordinatorLayout coordinatorLayout;

    @Inject
    SnackbarMessageManager snackbarMessageManager;

    private FloatingActionButton filtersFab;
    private ViewPager viewPager;
    private BottomSheetBehavior<?> bottomSheetBehavior;
    private FadingSnackbar snackbar;

    private List<Integer> labelsForDays;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        scheduleViewModel = viewModelProvider(viewModelFactory);
        FragmentScheduleBinding binding = FragmentScheduleBinding.inflate(inflater, container, false);
        binding.setLifecycleOwner(this);
        binding.setViewModel(this.scheduleViewModel);

        coordinatorLayout = binding.coordinatorLayout;
        filtersFab = binding.filterFab;
        snackbar = binding.snackbar;
        viewPager = binding.viewpager;

        SnackbarPreferenceViewModel snackbarPrefViewModel = viewModelProvider(viewModelFactory);
        setUpSnackbar(
                scheduleViewModel.getSnackBarMessage(),
                snackbar,
                snackbarMessageManager,
                () -> snackbarPrefViewModel.onStopClicked()
        );

        scheduleViewModel.getNavigateToSessionAction().observe(this, new EventObserver<>(this::openSessionDetail));
        scheduleViewModel.getNavigateToSignInDialogAction().observe(this, new EventObserver<>(this::openSignInDialog));
        scheduleViewModel.getNavigateToSignOutDialogAction().observe(this, new EventObserver<>(this::openSignOutDialog));
        scheduleViewModel.getScheduleUiHintsShown().observe(this, new EventObserver<>(this::openScheduleUiHintsDialog));
        scheduleViewModel.getShouldShowNotificationsPrefAction().observe(this, new EventObserver<>(this::openNotificationsPreferenceDialog));
        scheduleViewModel.getHasAnyFilters().observe(this, this::updateFiltersUi);

        if (savedInstanceState == null) {
            scheduleViewModel.setUserHasInteracted(false);
        }

        scheduleViewModel.getCurrentEvent().observe(this, eventLocation -> {
            if (!scheduleViewModel.getUserHasInteracted()) {
                if (eventLocation != null) {
                    binding.viewpager.post(() -> binding.viewpager.setCurrentItem(eventLocation.getDay()));
                } else {
                    logAnalyticsPageView(binding.viewpager.getCurrentItem());
                }
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        viewPager.setOffscreenPageLimit(COUNT - 1);

        View appbar = view.findViewById(R.id.appbar);
        TabLayout tabs = view.findViewById(R.id.tabs);
        tabs.setupWithViewPager(viewPager);

        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                logAnalyticsPageView(position);
            }
        });

        bottomSheetBehavior = BottomSheetBehavior.from(view.findViewById(R.id.filter_sheet));
        filtersFab.setOnClickListener(v -> bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED));
        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(View bottomSheet, int newState) {
                int a11yState = (newState == BottomSheetBehavior.STATE_EXPANDED) ?
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS :
                        View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
                viewPager.setImportantForAccessibility(a11yState);
                appbar.setImportantForAccessibility(a11yState);
            }
        });

        scheduleViewModel.getLabelsForDays().observe(this, labels -> {
            if (labels != null) {
                if (!labels.equals(labelsForDays)) {
                    viewPager.setAdapter(new ScheduleAdapter(getChildFragmentManager(), labels));
                    labelsForDays = labels;
                }
            }
        });
    }

    private void updateFiltersUi(boolean hasAnyFilters) {
        boolean showFab = !hasAnyFilters;

        fabVisibility(filtersFab, showFab);

        snackbar.updateLayoutParams<CoordinatorLayout.LayoutParams>(param -> {
            int bottomMargin = getResources().getDimensionPixelSize(showFab ?
                    R.dimen.snackbar_margin_bottom_fab :
                    R.dimen.bottom_sheet_peek_height);
            param.bottomMargin = bottomMargin;
        });

        bottomSheetBehavior.setHideable(showFab);
        bottomSheetBehavior.setSkipCollapsed(showFab);

        if (showFab && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }
    }

    @Override
    public boolean onBackPressed() {
        if (bottomSheetBehavior != null && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            if (bottomSheetBehavior.isHideable() && bottomSheetBehavior.isSkipCollapsed()) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
            return true;
        }
        return super.onBackPressed();
    }

    @Override
    public void onUserInteraction() {
        if (scheduleViewModel != null) {
            scheduleViewModel.setUserHasInteracted(true);
        }
    }

    private void openSessionDetail(SessionId id) {
        startActivity(SessionDetailActivity.starterIntent(requireContext(), id));
    }

    private void openSignInDialog() {
        SignInDialogFragment dialog = new SignInDialogFragment();
        dialog.show(requireActivity().getSupportFragmentManager(), DIALOG_NEED_TO_SIGN_IN);
    }

    private void openSignOutDialog() {
        SignOutDialogFragment dialog = new SignOutDialogFragment();
        dialog.show(requireActivity().getSupportFragmentManager(), DIALOG_CONFIRM_SIGN_OUT);
    }

    private void openScheduleUiHintsDialog() {
        ScheduleUiHintsDialogFragment dialog = new ScheduleUiHintsDialogFragment();
        dialog.show(requireActivity().getSupportFragmentManager(), DIALOG_SCHEDULE_HINTS);
    }

    private void openNotificationsPreferenceDialog() {
        NotificationsPreferenceDialogFragment dialog = new NotificationsPreferenceDialogFragment();
        dialog.show(requireActivity().getSupportFragmentManager(), DIALOG_NOTIFICATIONS_PREFERENCE);
    }

    @Override
    public void onStart() {
        super.onStart();
        scheduleViewModel.initializeTimeZone();
    }

    private void logAnalyticsPageView(int position) {
        analyticsHelper.sendScreenView("Schedule - Day " + (position + 1), requireActivity());
    }

    public class ScheduleAdapter extends FragmentPagerAdapter {

        private final List<Integer> labelsForDays;

        public ScheduleAdapter(FragmentManager fm, List<Integer> labelsForDays) {
            super(fm);
            this.labelsForDays = labelsForDays;
        }

        @Override
        public int getCount() {
            return COUNT;
        }

        @Override
        public Fragment getItem(int position) {
            return ScheduleDayFragment.newInstance(position);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return getString(labelsForDays.get(position));
        }
    }
}
