

package com.google.samples.apps.iosched.ui.schedule.agenda;

import android.arch.lifecycle.ViewModelProvider;
import android.databinding.BindingAdapter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.google.samples.apps.iosched.databinding.FragmentScheduleAgendaBinding;
import com.google.samples.apps.iosched.shared.model.Block;
import com.google.samples.apps.iosched.shared.util.ActivityViewModelProvider;
import com.google.samples.apps.iosched.ui.schedule.ScheduleViewModel;
import com.google.samples.apps.iosched.util.RecyclerViewDecorations;
import dagger.android.support.DaggerFragment;
import javax.inject.Inject;

public class ScheduleAgendaFragment extends DaggerFragment {

    @Inject
    ViewModelProvider.Factory viewModelFactory;
    private ScheduleViewModel viewModel;
    private FragmentScheduleAgendaBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentScheduleAgendaBinding.inflate(inflater, container, false);
        binding.setLifecycleOwner(this);
        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        viewModel = ActivityViewModelProvider.getInstance(requireActivity(), viewModelFactory).get(ScheduleViewModel.class);
        binding.setViewModel(viewModel);
    }

    @BindingAdapter("agendaItems")
    public static void agendaItems(RecyclerView recyclerView, List<Block> list) {
        if (recyclerView.getAdapter() == null) {
            recyclerView.setAdapter(new ScheduleAgendaAdapter());
        }
        ((ScheduleAgendaAdapter) recyclerView.getAdapter()).submitList(list != null ? list : java.util.Collections.emptyList());

        recyclerView.clearDecorations();
        if (list != null && !list.isEmpty()) {
            recyclerView.addItemDecoration(new ScheduleAgendaHeadersDecoration(recyclerView.getContext(), list));
        }
    }
}