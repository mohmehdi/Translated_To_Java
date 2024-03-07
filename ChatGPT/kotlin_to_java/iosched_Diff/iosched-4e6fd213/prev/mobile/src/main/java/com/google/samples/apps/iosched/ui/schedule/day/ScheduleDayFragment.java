package com.google.samples.apps.iosched.ui.schedule.day;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.core.view.doOnNextLayout;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.databinding.FragmentScheduleDayBinding;
import com.google.samples.apps.iosched.shared.result.EventObserver;
import com.google.samples.apps.iosched.shared.util.activityViewModelProvider;
import com.google.samples.apps.iosched.shared.util.lazyFast;
import com.google.samples.apps.iosched.ui.schedule.ScheduleViewModel;
import com.google.samples.apps.iosched.ui.schedule.SessionTimeData;
import com.google.samples.apps.iosched.util.clearDecorations;
import com.google.samples.apps.iosched.util.executeAfter;
import dagger.android.support.DaggerFragment;
import javax.inject.Inject;
import javax.inject.Named;

public class ScheduleDayFragment extends DaggerFragment {

    private static final String TAG = "ScheduleDayFragment";
    private static final String ARG_CONFERENCE_DAY = "arg.CONFERENCE_DAY";

    public static ScheduleDayFragment newInstance(int day) {
        Bundle args = new Bundle();
        args.putInt(ARG_CONFERENCE_DAY, day);
        ScheduleDayFragment fragment = new ScheduleDayFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Inject
    ViewModelProvider.Factory viewModelFactory;

    private ScheduleViewModel viewModel;
    private FragmentScheduleDayBinding binding;

    @Inject
    @Named("sessionViewPool")
    RecycledViewPool sessionViewPool;

    @Inject
    @Named("tagViewPool")
    RecycledViewPool tagViewPool;

    private final int conferenceDay = lazyFast(() -> {
        Bundle args = getArguments();
        if (args == null) {
            throw new IllegalStateException("Missing arguments!");
        }
        return args.getInt(ARG_CONFERENCE_DAY);
    });

    private ScheduleDayAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        viewModel = activityViewModelProvider(viewModelFactory);
        binding = FragmentScheduleDayBinding.inflate(inflater, container, false);
        binding.setLifecycleOwner(this);
        binding.setViewModel(viewModel);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        adapter = new ScheduleDayAdapter(
                viewModel,
                tagViewPool,
                viewModel.showReservations,
                viewModel.timeZoneId,
                this
        );

        binding.recyclerview.setAdapter(adapter);
        binding.recyclerview.setRecycledViewPool(sessionViewPool);
        ((LinearLayoutManager) binding.recyclerview.getLayoutManager()).setRecycleChildrenOnDetach(true);
        ((DefaultItemAnimator) binding.recyclerview.getItemAnimator()).setSupportsChangeAnimations(false);
        ((DefaultItemAnimator) binding.recyclerview.getItemAnimator()).setAddDuration(160L);
        ((DefaultItemAnimator) binding.recyclerview.getItemAnimator()).setMoveDuration(160L);
        ((DefaultItemAnimator) binding.recyclerview.getItemAnimator()).setChangeDuration(160L);
        ((DefaultItemAnimator) binding.recyclerview.getItemAnimator()).setRemoveDuration(120L);

        viewModel.getCurrentEvent().observe(this, eventLocation -> {
            if (eventLocation != null &&
                    !viewModel.getUserHasInteracted() &&
                    eventLocation.getDay() == conferenceDay &&
                    eventLocation.getSessionIndex() != -1
            ) {
                binding.recyclerview.post(() -> {
                    ((LinearLayoutManager) binding.recyclerview.getLayoutManager()).scrollToPositionWithOffset(
                            eventLocation.getSessionIndex(),
                            getResources().getDimensionPixelSize(R.dimen.margin_normal)
                    );
                });
            }
        });
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final Activity activity = requireActivity();
        viewModel.getSessionTimeDataForDay(conferenceDay).observe(activity, sessionTimeData -> {
            if (sessionTimeData == null) {
                return;
            }
            initializeList(sessionTimeData);
        });

        viewModel.getErrorMessage().observe(this, errorMsg -> {
            Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
        });
    }

    private void initializeList(SessionTimeData sessionTimeData) {
        List list = sessionTimeData.getList();
        if (list == null) {
            return;
        }
        String timeZoneId = sessionTimeData.getTimeZoneId();
        if (timeZoneId == null) {
            return;
        }
        adapter.submitList(list);

        binding.recyclerview.doOnNextLayout(() -> {
            clearDecorations();
            if (!list.isEmpty()) {
                addItemDecoration(
                        new ScheduleTimeHeadersDecoration(
                                getContext(), list.stream().map(it -> it.getSession()).collect(Collectors.toList()), timeZoneId
                        )
                );
            }
        });

        binding.executeAfter(() -> {
            isEmpty = list.isEmpty();
        });
    }
}