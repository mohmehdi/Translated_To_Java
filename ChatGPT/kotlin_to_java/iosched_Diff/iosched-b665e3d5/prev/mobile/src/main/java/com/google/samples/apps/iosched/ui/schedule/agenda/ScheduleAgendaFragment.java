package com.google.samples.apps.iosched.ui.schedule.agenda;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.databinding.BindingAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import com.google.samples.apps.iosched.databinding.FragmentScheduleAgendaBinding;
import com.google.samples.apps.iosched.model.Block;
import com.google.samples.apps.iosched.shared.util.parentViewModelProvider;
import com.google.samples.apps.iosched.ui.schedule.ScheduleViewModel;
import com.google.samples.apps.iosched.util.clearDecorations;
import dagger.android.support.DaggerFragment;
import org.threeten.bp.ZoneId;
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
        
        viewModel = parentViewModelProvider(viewModelFactory);
        binding.setViewModel(viewModel);
    }
}

public class ScheduleAgendaFragment {

    @BindingAdapter(value = {"agendaItems", "timeZoneId"})
    public static void agendaItems(RecyclerView recyclerView, List<Block> list, ZoneId timeZoneId) {
        if (recyclerView.getAdapter() == null) {
            recyclerView.setAdapter(new ScheduleAgendaAdapter());
        }
        ScheduleAgendaAdapter adapter = (ScheduleAgendaAdapter) recyclerView.getAdapter();
        adapter.submitList(list != null ? list : Collections.emptyList());
        adapter.timeZoneId = timeZoneId != null ? timeZoneId : ZoneId.systemDefault();

        recyclerView.clearDecorations();
        if (list != null && !list.isEmpty()) {
            recyclerView.addItemDecoration(new ScheduleAgendaHeadersDecoration(recyclerView.getContext(), list));
        }
    }
}