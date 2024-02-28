

package com.google.samples.apps.iosched.ui.schedule.agenda;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.databinding.FragmentScheduleAgendaBinding;
import com.google.samples.apps.iosched.model.Block;
import com.google.samples.apps.iosched.shared.util.parentViewModelProvider;
import com.google.samples.apps.iosched.ui.schedule.ScheduleViewModel;
import com.google.samples.apps.iosched.util.clearDecorations;
import java.time.ZoneId;
import javax.inject.Inject;
import dagger.android.support.DaggerFragment;

public class ScheduleAgendaFragment extends DaggerFragment {

    @Inject
    ViewModelProvider.Factory viewModelFactory;
    private ScheduleViewModel viewModel;
    private FragmentScheduleAgendaBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_schedule_agenda, container, false);
        binding.setLifecycleOwner(this);
        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        viewModel = parentViewModelProvider(viewModelFactory);
        binding.setViewModel(viewModel);

        viewModel.getAgendaItems().observe(this, new Observer<List<Block>>() {

        });
    }
}

// BindingAdapter translated to a static method in Java
import androidx.databinding.BindingAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.time.ZoneId;

@BindingAdapter(value = {"agendaItems", "timeZoneId"}, requireAll = false)
public static void agendaItems(RecyclerView recyclerView, List<Block> list, ZoneId timeZoneId) {
    if (list != null && !list.isEmpty()) {
        if (recyclerView.getAdapter() == null) {
            recyclerView.setAdapter(new ScheduleAgendaAdapter());
        }
        ScheduleAgendaAdapter adapter = (ScheduleAgendaAdapter) recyclerView.getAdapter();
        adapter.submitList(list);
        adapter.setTimeZoneId(timeZoneId != null ? timeZoneId : ZoneId.systemDefault());

        recyclerView.clearDecorations();
        if (list != null && !list.isEmpty()) {
            recyclerView.addItemDecoration(new ScheduleAgendaHeadersDecoration(recyclerView.getContext(), list));
        }
    }
}