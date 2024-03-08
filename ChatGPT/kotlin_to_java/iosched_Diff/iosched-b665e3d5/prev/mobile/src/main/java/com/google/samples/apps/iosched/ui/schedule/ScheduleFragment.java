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
import androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener;
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
import com.google.samples.apps.iosched.ui.schedule.agenda.ScheduleAgendaFragment;
import com.google.samples.apps.iosched.ui.schedule.day.ScheduleDayFragment;
import com.google.samples.apps.iosched.ui.sessiondetail.SessionDetailActivity;
import com.google.samples.apps.iosched.ui.setUpSnackbar;
import com.google.samples.apps.iosched.ui.signin.NotificationsPreferenceDialogFragment;
import com.google.samples.apps.iosched.ui.signin.SignInDialogFragment;
import com.google.samples.apps.iosched.ui.signin.SignOutDialogFragment;
import com.google.samples.apps.iosched.util.fabVisibility;
import com.google.samples.apps.iosched.widget.BottomSheetBehavior;
import com.google.samples.apps.iosched.widget.BottomSheetBehavior.BottomSheetCallback;
import com.google.samples.apps.iosched.widget.FadingSnackbar;
import dagger.android.support.DaggerFragment;
import javax.inject.Inject;

public class ScheduleFragment extends DaggerFragment implements MainNavigationFragment {

    private static final int COUNT = ConferenceDays.size() + 1;
    private static final int AGENDA_POSITION = COUNT - 1;
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        scheduleViewModel = viewModelProvider(viewModelFactory);
        FragmentScheduleBinding binding = FragmentScheduleBinding.inflate(inflater, container, false);
        binding.setLifecycleOwner(this);
        binding.setViewModel(this.scheduleViewModel);

        coordinatorLayout = binding.coordinatorLayout;
        filtersFab = binding.filterFab;
        snackbar = binding.snackbar;
        viewPager = binding.viewpager;

        SnackbarPreferenceViewModel snackbarPrefViewModel = viewModelProvider(viewModelFactory);
        setUpSnackbar(scheduleViewModel.snackBarMessage, snackbar, snackbarMessageManager,
            () -> {
                snackbarPrefViewModel.onStopClicked();
            });

        scheduleViewModel.navigateToSessionAction.observe(this, sessionId -> {
            openSessionDetail(sessionId);
        });

        scheduleViewModel.navigateToSignInDialogAction.observe(this, event -> {
            openSignInDialog();
        });

        scheduleViewModel.navigateToSignOutDialogAction.observe(this, event -> {
            openSignOutDialog();
        });

        scheduleViewModel.scheduleUiHintsShown.observe(this, event -> {
            if (!event) {
                openScheduleUiHintsDialog();
            }
        });

        scheduleViewModel.shouldShowNotificationsPrefAction.observe(this, event -> {
            if (event) {
                openNotificationsPreferenceDialog();
            }
        });

        scheduleViewModel.transientUiState.observe(this, uiState -> {
            updateFiltersUi(uiState != null ? uiState : return);
        });

        if (savedInstanceState == null) {
            scheduleViewModel.userHasInteracted = false;
        }

        scheduleViewModel.currentEvent.observe(this, eventLocation -> {
            if (!scheduleViewModel.userHasInteracted) {
                if (eventLocation != null) {
                    viewPager.post(() -> {
                        viewPager.setCurrentItem(eventLocation.day);
                    });
                } else {
                    logAnalyticsPageView(viewPager.getCurrentItem());
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

        viewPager.addOnPageChangeListener(new SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                scheduleViewModel.setIsAgendaPage(position == AGENDA_POSITION);
                logAnalyticsPageView(position);
            }
        });

        bottomSheetBehavior = BottomSheetBehavior.from(view.findViewById(R.id.filter_sheet));
        filtersFab.setOnClickListener(v -> {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetCallback() {
            @Override
            public void onStateChanged(View bottomSheet, int newState) {
                int a11yState = newState == STATE_EXPANDED ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
                viewPager.setImportantForAccessibility(a11yState);
                appbar.setImportantForAccessibility(a11yState);
            }
        });

        scheduleViewModel.labelsForDays.observe(this, labels -> {
            if (labels != null) {
                if (labelsForDays == null || !labels.equals(labelsForDays)) {
                    viewPager.setAdapter(new ScheduleAdapter(getChildFragmentManager(), labels));
                    labelsForDays = labels;
                }
            }
        });

        if (savedInstanceState == null) {
            scheduleViewModel.setIsAgendaPage(false);
        }
    }

    private void updateFiltersUi(TransientUiState uiState) {
        boolean showFab = !uiState.isAgendaPage && !uiState.hasAnyFilters;
        boolean hideable = uiState.isAgendaPage || !uiState.hasAnyFilters;

        fabVisibility(filtersFab, showFab);

        snackbar.updateLayoutParams(params -> {
            params.bottomMargin = getResources().getDimensionPixelSize(showFab ? R.dimen.snackbar_margin_bottom_fab : R.dimen.bottom_sheet_peek_height);
        });

        bottomSheetBehavior.setHideable(hideable);
        bottomSheetBehavior.setSkipCollapsed(!uiState.hasAnyFilters);
        if (hideable && bottomSheetBehavior.getState() == STATE_COLLAPSED) {
            bottomSheetBehavior.setState(STATE_HIDDEN);
        }
    }

    @Override
    public boolean onBackPressed() {
        if (bottomSheetBehavior != null && bottomSheetBehavior.getState() == STATE_EXPANDED) {
            if (bottomSheetBehavior.isHideable() && bottomSheetBehavior.skipCollapsed()) {
                bottomSheetBehavior.setState(STATE_HIDDEN);
            } else {
                bottomSheetBehavior.setState(STATE_COLLAPSED);
            }
            return true;
        }
        return super.onBackPressed();
    }

    @Override
    public void onUserInteraction() {
        if (scheduleViewModel != null) {
            scheduleViewModel.userHasInteracted = true;
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
        String page = position == AGENDA_POSITION ? "agenda" : "Day " + (position + 1);
        analyticsHelper.sendScreenView("Schedule - " + page, requireActivity());
    }

    class ScheduleAdapter extends FragmentPagerAdapter {

        private final List<Integer> labelsForDays;

        ScheduleAdapter(FragmentManager fm, List<Integer> labelsForDays) {
            super(fm);
            this.labelsForDays = labelsForDays;
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
                return ScheduleDayFragment.newInstance(position);
            }
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (position == AGENDA_POSITION) {
                return getString(R.string.agenda);
            } else {
                return getString(labelsForDays.get(position));
            }
        }
    }
}