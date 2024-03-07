package com.google.samples.apps.iosched.ui.schedule.day;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.core.view.ViewCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool;

import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.databinding.FragmentScheduleDayBinding;
import com.google.samples.apps.iosched.shared.result.EventObserver;
import com.google.samples.apps.iosched.shared.util.ExtensionsKt;
import com.google.samples.apps.iosched.shared.util.UtilKt;
import com.google.samples.apps.iosched.ui.schedule.ScheduleViewModel;
import com.google.samples.apps.iosched.ui.schedule.SessionTimeData;
import com.google.samples.apps.iosched.util.ExtensionsJ;
import com.google.samples.apps.iosched.util.ExtensionsJt;
import com.google.samples.apps.iosched.util.ExtensionsK;
import com.google.samples.apps.iosched.util.ExtensionsJy;

import javax.inject.Inject;
import javax.inject.Named;

public class ScheduleDayFragment extends DaggerFragment {

    public static final String TAG = "ScheduleDayFragment";
    private static final String ARG_CONFERENCE_DAY = "arg.CONFERENCE_DAY";

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

    private int conferenceDay;

    private ScheduleDayAdapter adapter;

    public static ScheduleDayFragment newInstance(int day) {
        Bundle args = new Bundle();
        args.putInt(ARG_CONFERENCE_DAY, day);
        ScheduleDayFragment fragment = new ScheduleDayFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        viewModel = ExtensionsKt.parentViewModelProvider(this, viewModelFactory);
        binding = FragmentScheduleDayBinding.inflate(inflater, container, false);
        binding.setLifecycleOwner(this);
        binding.setViewModel(this);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        adapter = new ScheduleDayAdapter(viewModel, tagViewPool, viewModel.getShowReservations(),
                viewModel.getTimeZoneId(), this);

        binding.recyclerview.setAdapter(adapter);
        binding.recyclerview.setRecycledViewPool(sessionViewPool);
        ExtensionsJ.setLayoutManager(binding.recyclerview, new LinearLayoutManager(requireContext()));
        ExtensionsJy.setRecycleChildrenOnDetach(binding.recyclerview, true);
        RecyclerView.ItemAnimator itemAnimator = (RecyclerView.ItemAnimator) binding.recyclerview.getItemAnimator();
        if (itemAnimator instanceof DefaultItemAnimator) {
            DefaultItemAnimator defaultItemAnimator = (DefaultItemAnimator) itemAnimator;
            defaultItemAnimator.setSupportsChangeAnimations(false);
            defaultItemAnimator.setAddDuration(160L);
            defaultItemAnimator.setMoveDuration(160L);
            defaultItemAnimator.setChangeDuration(160L);
            defaultItemAnimator.setRemoveDuration(120L);
        }

        viewModel.getCurrentEvent().observe(this, eventLocation -> {
            if (eventLocation != null && !viewModel.getUserHasInteracted() &&
                    eventLocation.getDay() == conferenceDay && eventLocation.getSessionIndex() != -1) {
                ViewCompat.postOnAnimation(binding.recyclerview, () -> {
                    ExtensionsJt.getLinearLayoutManager(binding.recyclerview)
                            .scrollToPositionWithOffset(eventLocation.getSessionIndex(),
                                    getResources().getDimensionPixelSize(R.dimen.margin_normal));
                });
            }
        });
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        viewModel.getSessionTimeDataForDay(conferenceDay).observe(requireActivity(), sessionTimeData -> {
            if (sessionTimeData == null) return;
            initializeList(sessionTimeData);
        });

        viewModel.getErrorMessage().observe(this, new EventObserver<>(errorMsg ->
                Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show()));
    }

    private void initializeList(SessionTimeData sessionTimeData) {
        if (sessionTimeData.getList() == null || sessionTimeData.getTimeZoneId() == null) return;

        adapter.submitList(sessionTimeData.getList());

        binding.recyclerview.doOnNextLayout(() -> {
            ExtensionsKt.clearDecorations(binding.recyclerview);
            if (!sessionTimeData.getList().isEmpty()) {
                ExtensionsKt.addItemDecoration(binding.recyclerview,
                        new ScheduleTimeHeadersDecoration(requireContext(),
                                UtilKt.map(sessionTimeData.getList(), it -> it.getSession()),
                                sessionTimeData.getTimeZoneId()));
            }
        });

        ExtensionsK.executeAfter(binding, () -> {
            ExtensionsJt.setIsEmpty(binding, sessionTimeData.getList().isEmpty());
        });
    }
}
