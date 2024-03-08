package com.google.samples.apps.iosched.ui.agenda;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.databinding.BindingAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import com.google.samples.apps.iosched.databinding.FragmentAgendaBinding;
import com.google.samples.apps.iosched.model.Block;
import com.google.samples.apps.iosched.shared.util.viewModelProvider;
import com.google.samples.apps.iosched.ui.MainNavigationFragment;
import com.google.samples.apps.iosched.util.clearDecorations;
import dagger.android.support.DaggerFragment;
import org.threeten.bp.ZoneId;
import javax.inject.Inject;

public class AgendaFragment extends DaggerFragment implements MainNavigationFragment {

    @Inject
    ViewModelProvider.Factory viewModelFactory;
    private AgendaViewModel viewModel;
    private FragmentAgendaBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAgendaBinding.inflate(inflater, container, false);
        binding.setLifecycleOwner(this);
        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        viewModel = viewModelProvider(viewModelFactory);
        binding.setViewModel(viewModel);
    }
}

@BindingAdapter(value = {"agendaItems", "timeZoneId"})
public static void agendaItems(RecyclerView recyclerView, List<Block> list, ZoneId timeZoneId) {
    if (recyclerView.getAdapter() == null) {
        recyclerView.setAdapter(new AgendaAdapter());
    }
    AgendaAdapter adapter = (AgendaAdapter) recyclerView.getAdapter();
    adapter.submitList(list != null ? list : Collections.emptyList());
    adapter.setTimeZoneId(timeZoneId != null ? timeZoneId : ZoneId.systemDefault());

    recyclerView.clearDecorations();
    if (list != null && !list.isEmpty()) {
        recyclerView.addItemDecoration(new AgendaHeadersDecoration(recyclerView.getContext(), list));
    }
}