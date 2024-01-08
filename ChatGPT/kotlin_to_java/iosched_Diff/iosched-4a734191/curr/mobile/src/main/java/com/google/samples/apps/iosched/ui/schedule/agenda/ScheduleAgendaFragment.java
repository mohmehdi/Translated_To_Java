package com.google.samples.apps.iosched.ui.schedule.agenda;

import android.arch.lifecycle.ViewModelProvider;
import android.databinding.BindingAdapter;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.samples.apps.iosched.databinding.FragmentScheduleAgendaBinding;
import com.google.samples.apps.iosched.shared.model.Block;
import com.google.samples.apps.iosched.shared.util.activityViewModelProvider;
import com.google.samples.apps.iosched.ui.schedule.ScheduleViewModel;
import com.google.samples.apps.iosched.util.clearDecorations;

import javax.inject.Inject;

public class ScheduleAgendaFragment extends DaggerFragment {

    @Inject
    ViewModelProvider.Factory viewModelFactory;
    private ScheduleViewModel viewModel;
    private FragmentScheduleAgendaBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentScheduleAgendaBinding.inflate(inflater, container, false);
        binding.setLifecycleOwner(this);
        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        viewModel = activityViewModelProvider(viewModelFactory);
        binding.setViewModel(viewModel);
    }
}

public class ScheduleAgendaFragment {

    @BindingAdapter("agendaItems")
    public static void agendaItems(RecyclerView recyclerView, List<Block> list) {
        if (recyclerView.getAdapter() == null) {
            recyclerView.setAdapter(new ScheduleAgendaAdapter());
        }
        ((ScheduleAgendaAdapter) recyclerView.getAdapter()).submitList(list != null ? list : Collections.emptyList());

        recyclerView.clearDecorations();
        if (list != null && !list.isEmpty()) {
            recyclerView.addItemDecoration(new ScheduleAgendaHeadersDecoration(recyclerView.getContext(), list));
        }
    }
}