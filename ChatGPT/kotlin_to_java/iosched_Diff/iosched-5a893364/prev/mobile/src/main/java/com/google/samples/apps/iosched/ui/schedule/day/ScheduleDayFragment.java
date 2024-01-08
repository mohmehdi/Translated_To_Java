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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_schedule_day, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        viewModel = activityViewModelProvider(viewModelFactory);
        adapter = new ScheduleDayAdapter(viewModel, tagViewPool);
        recyclerView = view.findViewById(R.id.recyclerview);
        recyclerView.setAdapter(adapter);
        recyclerView.setRecycledViewPool(sessionViewPool);
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        layoutManager.setRecycleChildrenOnDetach(true);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final ScheduleDayFragment self = this;
        viewModel.getSessionsForDay(conferenceDay).observe(this, new Observer<List<Session>>() {
            @Override
            public void onChanged(@Nullable List<Session> list) {
                adapter.submitList(list != null ? list : Collections.emptyList());

                recyclerView.clearDecorations();
                if (list != null && !list.isEmpty()) {
                    recyclerView.addItemDecoration(
                            new ScheduleTimeHeadersDecoration(recyclerView.getContext(), list.map(new Function<Session, Object>() {
                                @Override
                                public Object apply(Session session) {
                                    return session;
                                }
                            }))
                    );
                }
            }
        });

        viewModel.getSnackBarMessage().observe(this, new Observer<SnackbarMessage>() {
            @Override
            public void onChanged(@Nullable SnackbarMessage message) {
                if (message != null && message.getContentIfNotHandled() != null) {
                    SnackbarMessage snackbarMessage = message.getContentIfNotHandled();
                    View coordinatorLayout = getActivity().findViewById(R.id.coordinator_layout);
                    int duration = snackbarMessage.isLongDuration() ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_SHORT;
                    Snackbar snackbar = Snackbar.make(coordinatorLayout, snackbarMessage.getMessageId(), duration);
                    if (snackbarMessage.getActionId() != null) {
                        snackbar.setAction(snackbarMessage.getActionId(), new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                snackbar.dismiss();
                            }
                        });
                    }
                    snackbar.setActionTextColor(ContextCompat.getColor(getContext(), R.color.teal));
                    snackbar.show();
                }
            }
        });

        viewModel.getErrorMessage().observe(this, new Observer<ErrorMessage>() {
            @Override
            public void onChanged(@Nullable ErrorMessage message) {
                if (message != null && message.getContentIfNotHandled() != null) {
                    Toast.makeText(self.getContext(), message.getContentIfNotHandled(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}