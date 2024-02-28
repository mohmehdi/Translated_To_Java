package com.google.samples.apps.iosched.ui.schedule;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TabLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.FadingSnackbar;
import com.google.android.material.snackbar.Snackbar;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.SharedViewModel;
import com.google.samples.apps.iosched.SharedViewModel.ScheduleUiHintsShownEvent;
import com.google.samples.apps.iosched.SharedViewModel.SessionActionEvent;
import com.google.samples.apps.iosched.SharedViewModel.SignInDialogEvent;
import com.google.samples.apps.iosched.SharedViewModel.SignOutDialogEvent;
import com.google.samples.apps.iosched.SignInDialogFragment;
import com.google.samples.apps.iosched.SignOutDialogFragment;
import com.google.samples.apps.iosched.SnackbarPreferenceViewModel;
import com.google.samples.apps.iosched.SnackbarPreferenceViewModel.SnackbarMessageActionEvent;
import com.google.samples.apps.iosched.TimeUtils.ConferenceDays;
import com.google.samples.apps.iosched.analytics.AnalyticsHelper;
import com.google.samples.apps.iosched.databinding.FragmentScheduleBinding;
import com.google.samples.apps.iosched.model.SessionId;
import com.google.samples.apps.iosched.shared.result.EventObserver;
import com.google.samples.apps.iosched.shared.util.TimeUtils;
import com.google.samples.apps.iosched.ui.schedule.day.ScheduleDayFragment;
import com.google.samples.apps.iosched.ui.sessiondetail.SessionDetailActivity;
import com.google.samples.apps.iosched.ui.signin.NotificationsPreferenceDialogFragment;
import com.google.samples.apps.iosched.ui.signin.NotificationsPreferenceDialogFragment.NotificationsPreferenceDialogFragmentArgs;
import com.google.samples.apps.iosched.ui.signin.SignInDialogFragment;
import com.google.samples.apps.iosched.ui.signin.SignOutDialogFragment;
import com.google.samples.apps.iosched.ui.setUpSnackbar;
import com.google.samples.apps.iosched.widget.BottomSheetBehavior;
import com.google.samples.apps.iosched.widget.BottomSheetBehavior.BottomSheetCallback;
import java.util.Arrays;
import java.util.List;

public class ScheduleFragment extends DaggerFragment implements MainNavigationFragment {

    private static final int COUNT = ConferenceDays.size();
    private static final String DIALOG_NEED_TO_SIGN_IN = "dialog_need_to_sign_in";
    private static final String DIALOG_CONFIRM_SIGN_OUT = "dialog_confirm_sign_out";
    private static final String DIALOG_SCHEDULE_HINTS = "dialog_schedule_hints";

    @Inject
    AnalyticsHelper analyticsHelper;

    @Inject
    ViewModelProvider.Factory viewModelFactory;

    private SharedViewModel scheduleViewModel;
    private CoordinatorLayout coordinatorLayout;
    private SnackbarMessageActionEvent snackbarMessageActionEvent;
    private SnackbarPreferenceViewModel snackbarPreferenceViewModel;
    private FloatingActionButton filtersFab;
    private ViewPager viewPager;
    private BottomSheetBehavior bottomSheetBehavior;
    private FadingSnackbar snackbar;
    private List<Integer> labelsForDays;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        scheduleViewModel = new ViewModelProvider(this, viewModelFactory).get(SharedViewModel.class);
        FragmentScheduleBinding binding = FragmentScheduleBinding.inflate(inflater, container, false);
        binding.setLifecycleOwner(this);
        binding.setViewModel(scheduleViewModel);

        coordinatorLayout = binding.coordinatorLayout;
        filtersFab = binding.filterFab;
        snackbar = binding.snackbar;
        viewPager = binding.viewpager;

        snackbarPreferenceViewModel = new ViewModelProvider(this, viewModelFactory).get(SnackbarPreferenceViewModel.class);
        setUpSnackbar(scheduleViewModel.getSnackBarMessage(), snackbar, snackbarMessageActionEvent,
                snackbarPreferenceViewModel,
                view -> {
                    snackbarPreferenceViewModel.onStopClicked();
                });

        scheduleViewModel.getSessionActionEvent().observe(this, new EventObserver<SessionActionEvent>() {
            @Override
            public void onEventUnhandled(SessionActionEvent sessionActionEvent) {
                openSessionDetail(sessionActionEvent.getSessionId());
            }
        });

        scheduleViewModel.getSignInDialogEvent().observe(this, new EventObserver<SignInDialogEvent>() {
            @Override
            public void onEventUnhandled(SignInDialogEvent signInDialogEvent) {
                openSignInDialog();
            }
        });

        scheduleViewModel.getSignOutDialogEvent().observe(this, new EventObserver<SignOutDialogEvent>() {
            @Override
            public void onEventUnhandled(SignOutDialogEvent signOutDialogEvent) {
                openSignOutDialog();
            }
        });

        scheduleViewModel.getScheduleUiHintsShownEvent().observe(this, new EventObserver<ScheduleUiHintsShownEvent>() {
            @Override
            public void onEventUnhandled(ScheduleUiHintsShownEvent scheduleUiHintsShownEvent) {
                if (!scheduleUiHintsShownEvent.isShown()) {
                    openScheduleUiHintsDialog();
                }
            }
        });

        scheduleViewModel.getShouldShowNotificationsPrefAction().observe(this, new EventObserver<Boolean>() {
            @Override
            public void onEventUnhandled(Boolean aBoolean) {
                if (aBoolean) {
                    openNotificationsPreferenceDialog();
                }
            }
        });

        scheduleViewModel.getHasAnyFilters().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean hasAnyFilters) {
                updateFiltersUi(hasAnyFilters);
            }
        });

        if (savedInstanceState == null) {
            scheduleViewModel.setUserHasInteracted(false);
        }

        scheduleViewModel.getCurrentEvent().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String eventLocation) {
                if (!scheduleViewModel.getUserHasInteracted()) {
                    if (eventLocation != null) {
                        viewPager.post(new Runnable() {
                            @Override
                            public void run() {
                                viewPager.setCurrentItem(TimeUtils.getDayIndexFromLocation(eventLocation), false);
                            }
                        });
                    } else {
                        logAnalyticsPageView(viewPager.getCurrentItem());
                    }
                }
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewPager.setOffscreenPageLimit(COUNT - 1);

        TabLayout tabs = view.findViewById(R.id.tabs);
        tabs.setupWithViewPager(viewPager);

        viewPager.addOnPageChangeListener(new SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                logAnalyticsPageView(position);
            }
        });

        bottomSheetBehavior = BottomSheetBehavior.from(view.findViewById(R.id.filter_sheet));
        filtersFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        bottomSheetBehavior.setBottomSheetCallback(new BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                int a11yState = newState == BottomSheetBehavior.STATE_EXPANDED ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
                viewPager.setImportantForAccessibility(a11yState);
                ((AppCompatActivity) requireActivity()).getSupportActionBar().setImportantForAccessibility(a11yState);
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            }
        });

        scheduleViewModel.getLabelsForDays().observe(this, new Observer<List<Integer>>() {
            @Override
            public void onChanged(List<Integer> labels) {
                if (labels != labelsForDays) {
                    viewPager.setAdapter(new ScheduleAdapter(getChildFragmentManager(), labels));
                    labelsForDays = labels;
                }
            }
        });

        viewPager.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                viewPager.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                updateFiltersUi(scheduleViewModel.getHasAnyFilters().getValue());
            }
        });
    }

    private void updateFiltersUi(boolean hasAnyFilters) {
        boolean showFab = !hasAnyFilters;

        fabVisibility(filtersFab, showFab);

        snackbar.getView().setBackgroundColor(ContextCompat.getColor(requireContext(), showFab ? R.color.colorPrimary : R.color.colorSurface));

        bottomSheetBehavior.setHideable(showFab);
        bottomSheetBehavior.setSkipCollapsed(showFab);
        if (showFab && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }
    }

    @Override
    public boolean onBackPressed() {
        if (bottomSheetBehavior != null && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            if (bottomSheetBehavior.isHideable() && bottomSheetBehavior.getSkipCollapsed()) {
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
        super.onUserInteraction();
        if (scheduleViewModel != null) {
            scheduleViewModel.setUserHasInteracted(true);
        }
    }

    private void openSessionDetail(SessionId sessionId) {
        startActivity(SessionDetailActivity.starterIntent(requireContext(), sessionId));
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

    private class ScheduleAdapter extends FragmentPagerAdapter {

        private List<Integer> labelsForDays;

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

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return getString(labelsForDays.get(position));
        }
    }
}