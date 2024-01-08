package com.google.samples.apps.iosched.ui.schedule.day;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProvider;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.material.snackbar.SnackbarMessage;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay;
import com.google.samples.apps.iosched.shared.util.activityViewModelProvider;
import com.google.samples.apps.iosched.shared.util.getEnum;
import com.google.samples.apps.iosched.shared.util.lazyFast;
import com.google.samples.apps.iosched.shared.util.putEnum;
import com.google.samples.apps.iosched.ui.SnackbarMessage;
import com.google.samples.apps.iosched.ui.schedule.ScheduleViewModel;
import com.google.samples.apps.iosched.util.clearDecorations;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.android.support.DaggerFragment;

public class ScheduleDayFragment extends DaggerFragment {

    private static final String TAG = "ScheduleDayFragment";
    private static final String ARG_CONFERENCE_DAY = "arg.CONFERENCE_DAY";

    @Inject
    ViewModelProvider.Factory viewModelFactory;

    private ScheduleViewModel viewModel;

    @Inject
    @Named("sessionViewPool")
    RecycledViewPool sessionViewPool;

    @Inject
    @Named("tagViewPool")
    RecycledViewPool tagViewPool;

    private ConferenceDay conferenceDay;

    private ScheduleDayAdapter adapter;

    private RecyclerView recyclerView;

    public static ScheduleDayFragment newInstance(ConferenceDay day) {
        Bundle args = new Bundle();
        args.putEnum(ARG_CONFERENCE_DAY, day);
        ScheduleDayFragment fragment = new ScheduleDayFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_schedule_day, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        viewModel = activityViewModelProvider(viewModelFactory);
        adapter = new ScheduleDayAdapter(
                viewModel,
                tagViewPool,
                viewModel.observeLoggedInUser(),
                viewModel.observeRegisteredUser(),
                this);
        recyclerView = view.findViewById(R.id.recyclerview);
        recyclerView.setAdapter(adapter);
        recyclerView.setRecycledViewPool(sessionViewPool);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setRecycleChildrenOnDetach(true);
        recyclerView.setLayoutManager(layoutManager);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final ScheduleDayFragment fragment = this;
        viewModel.getSessionsForDay(conferenceDay).observe(getViewLifecycleOwner(), new Observer<List<Session>>() {
            @Override
            public void onChanged(@Nullable List<Session> list) {
                adapter.submitList(list != null ? list : Collections.emptyList());

                recyclerView.clearDecorations();
                if (list != null && !list.isEmpty()) {
                    recyclerView.addItemDecoration(
                            new ScheduleTimeHeadersDecoration(getContext(), list.map(new Function<Session, Session>() {
                                @Override
                                public Session apply(Session session) {
                                    return session;
                                }
                            })));
                }
            }
        });

        viewModel.getSnackBarMessage().observe(getViewLifecycleOwner(), new Observer<SnackbarMessage>() {
            @Override
            public void onChanged(@Nullable SnackbarMessage snackbarMessage) {
                if (snackbarMessage != null) {
                    Snackbar.make(fragment.requireActivity().findViewById(R.id.coordinator_layout),
                            snackbarMessage.getMessageId(),
                            snackbarMessage.getLongDuration() ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_SHORT)
                            .setAction(snackbarMessage.getActionId(), new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    snackbar.dismiss();
                                }
                            })
                            .setActionTextColor(ContextCompat.getColor(getContext(), R.color.teal))
                            .show();
                }
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), new Observer<Event<String>>() {
            @Override
            public void onChanged(@Nullable Event<String> event) {
                if (event != null) {
                    Toast.makeText(getContext(), event.getContentIfNotHandled(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}