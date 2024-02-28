

package com.google.samples.apps.iosched.ui.schedule;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.lifecycle.Observer;
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
import com.google.samples.apps.iosched.shared.viewmodel.ViewModelProvider;
import com.google.samples.apps.iosched.shared.viewmodel.ViewModelProvider.Factory;
import com.google.samples.apps.iosched.ui.MainNavigationFragment;
import com.google.samples.apps.iosched.ui.MessagesSnackbarManager;
import com.google.samples.apps.iosched.ui.PrefsSnackbarPreferenceViewModel;
import com.google.samples.apps.iosched.ui.ScheduleAgendaFragment;
import com.google.samples.apps.iosched.ui.ScheduleDayFragment;
import com.google.samples.apps.iosched.ui.SessionDetailActivity;
import com.google.samples.apps.iosched.ui.signin.NotificationsPreferenceDialogFragment;
import com.google.samples.apps.iosched.ui.signin.NotificationsPreferenceDialogFragment.Companion;
import com.google.samples.apps.iosched.ui.signin.SignInDialogFragment;
import com.google.samples.apps.iosched.ui.signin.SignOutDialogFragment;
import com.google.samples.apps.iosched.ui.setUpSnackbar;
import com.google.samples.apps.iosched.widget.BottomSheetBehavior;
import com.google.samples.apps.iosched.widget.BottomSheetBehavior.BottomSheetCallback;
import com.google.samples.apps.iosched.widget.BottomSheetBehavior.State;
import com.google.samples.apps.iosched.widget.FadingSnackbar;
import java.util.List;
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
    Factory viewModelFactory;

    private ScheduleViewModel scheduleViewModel;
    private CoordinatorLayout coordinatorLayout;
    private MessagesSnackbarManager snackbarMessageManager;
    private FloatingActionButton filtersFab;
    private ViewPager viewPager;
    private BottomSheetBehavior bottomSheetBehavior;
    private FadingSnackbar snackbar;
    private List<Integer> labelsForDays;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ScheduleViewModelFactory factory = new ScheduleViewModelFactory(requireActivity().getApplication(), analyticsHelper);
        scheduleViewModel = ViewModelProviders.of(this, factory).get(ScheduleViewModel.class);
        FragmentScheduleBinding binding = FragmentScheduleBinding.inflate(inflater, container, false);
        binding.setLifecycleOwner(this);
        binding.setViewModel(scheduleViewModel);

        coordinatorLayout = binding.coordinatorLayout;
        filtersFab = binding.filterFab;
        snackbar = binding.snackbar;
        viewPager = binding.viewpager;

        PrefsSnackbarPreferenceViewModel snackbarPrefViewModel = ViewModelProviders.of(this, viewModelFactory).get(PrefsSnackbarPreferenceViewModel.class);
        setUpSnackbar(scheduleViewModel.snackBarMessage, snackbar, snackbarMessageManager,
                view -> snackbarPrefViewModel.onStopClicked());

        scheduleViewModel.navigateToSessionAction.observe(this, new EventObserver() {

        });

        scheduleViewModel.navigateToSignInDialogAction.observe(this, new EventObserver() {

        });

        scheduleViewModel.navigateToSignOutDialogAction.observe(this, new EventObserver() {

        });

        scheduleViewModel.scheduleUiHintsShown.observe(this, new EventObserver() {

        });

        scheduleViewModel.shouldShowNotificationsPrefAction.observe(this, new EventObserver() {

        });

        scheduleViewModel.transientUiState.observe(this, new Observer<TransientUiState>() {

        });

        if (savedInstanceState == null) {
            scheduleViewModel.userHasInteracted = false;
        }

        scheduleViewModel.currentEvent.observe(this, new Observer<EventLocation>() {
           
        });

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
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

        View filterSheet = view.findViewById(R.id.filter_sheet);
        bottomSheetBehavior = BottomSheetBehavior.from(filterSheet);
        filtersFab.setOnClickListener(new View.OnClickListener() {

        });

        bottomSheetBehavior.setBottomSheetCallback(new BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                int a11yState = newState == State.EXPANDED ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
                viewPager.setImportantForAccessibility(a11yState);
                appbar.setImportantForAccessibility(a11yState);
            }


        });

        scheduleViewModel.labelsForDays.observe(this, new Observer<List<Integer>>() {

        });

        if (savedInstanceState == null) {
            scheduleViewModel.setIsAgendaPage(false);
        }

        ViewCompat.setOnApplyWindowInsetsListener(view, new OnApplyWindowInsetsListener() {

        });

        view.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {

        });
    }

    private void updateFiltersUi(TransientUiState uiState) {
        boolean showFab = !uiState.isAgendaPage && !uiState.hasAnyFilters;
        int bottomMargin = showFab ? getResources().getDimensionPixelSize(R.dimen.snackbar_margin_bottom_fab) : getResources().getDimensionPixelSize(R.dimen.bottom_sheet_peek_height);

        snackbar.getView().setLayoutParams(new CoordinatorLayout.LayoutParams(CoordinatorLayout.LayoutParams.MATCH_PARENT, CoordinatorLayout.LayoutParams.WRAP_CONTENT));
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) snackbar.getView().getLayoutParams();
        params.setMargins(0, 0, 0, bottomMargin);
        snackbar.getView().setLayoutParams(params);

        bottomSheetBehavior.setHideable(uiState.isAgendaPage || !uiState.hasAnyFilters);
        bottomSheetBehavior.setSkipCollapsed(uiState.isAgendaPage || !uiState.hasAnyFilters);
        if (uiState.isAgendaPage || !uiState.hasAnyFilters) {
            bottomSheetBehavior.setState(State.HIDDEN);
        }

        fabVisibility(filtersFab, showFab);
    }

    @Override
    public boolean onBackPressed() {
        if (bottomSheetBehavior != null && bottomSheetBehavior.getState() == State.EXPANDED) {
            if (bottomSheetBehavior.isHideable() && bottomSheetBehavior.getSkipCollapsed()) {
                bottomSheetBehavior.setState(State.HIDDEN);
            } else {
                bottomSheetBehavior.setState(State.COLLAPSED);
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
        dialog.show(requireActivity().getSupportFragmentManager(), Companion.DIALOG_NOTIFICATIONS_PREFERENCE);
    }

    @Override
    public void onStart() {
        super.onStart();
        scheduleViewModel.initializeTimeZone();
    }

    private void logAnalyticsPageView(int position) {
        String page = position == AGENDA_POSITION ? "agenda" : "Day " + (position + 1);
        analyticsHelper.sendScreenView("Schedule - " + page, (AppCompatActivity) requireActivity());
    }

    class ScheduleAdapter extends FragmentPagerAdapter {

        private List<Integer> labelsForDays;

        public ScheduleAdapter(@NonNull FragmentManager fm, List<Integer> labelsForDays) {
            super(fm);
            this.labelsForDays = labelsForDays;
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return position == AGENDA_POSITION ? new ScheduleAgendaFragment() : ScheduleDayFragment.newInstance(position);
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return position == AGENDA_POSITION ? getString(R.string.agenda) : getString(labelsForDays.get(position));
        }

        @Override
        public int getCount() {
            return COUNT;
        }
    }
}