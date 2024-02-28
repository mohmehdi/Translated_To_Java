

package com.google.samples.apps.iosched.ui.agenda;

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
import com.google.samples.apps.iosched.databinding.FragmentAgendaBinding;
import com.google.samples.apps.iosched.model.Block;
import com.google.samples.apps.iosched.shared.util.viewModelProvider;
import com.google.samples.apps.iosched.ui.MainNavigationFragment;
import com.google.samples.apps.iosched.ui.agenda.AgendaAdapter;
import com.google.samples.apps.iosched.ui.agenda.AgendaHeadersDecoration;
import java.time.ZoneId;
import java.util.List;
import javax.inject.Inject;

public class AgendaFragment extends DaggerFragment implements MainNavigationFragment {

    @Inject
    ViewModelProvider.Factory viewModelFactory;
    private AgendaViewModel viewModel;
    private FragmentAgendaBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_agenda, container, false);
        binding.setLifecycleOwner((LifecycleOwner) this);
        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        viewModel = viewModelProvider(viewModelFactory);
        binding.setViewModel(viewModel);
    }

    @BindingAdapter(value = {"agendaItems", "timeZoneId"}, requireAll = false)
    public static void agendaItems(RecyclerView recyclerView, List<Block> list, ZoneId timeZoneId) {
        if (recyclerView.getAdapter() == null) {
            recyclerView.setAdapter(new AgendaAdapter());
        }
        AgendaAdapter adapter = (AgendaAdapter) recyclerView.getAdapter();
        if (list != null) {
            adapter.submitList(list);
        } else {
            adapter.submitList(Collections.emptyList());
        }
        adapter.setTimeZoneId(timeZoneId != null ? timeZoneId : ZoneId.systemDefault());

        recyclerView.clearDecorations();
        if (list != null && !list.isEmpty()) {
            recyclerView.addItemDecoration(new AgendaHeadersDecoration(recyclerView.getContext(), list));
        }
    }
}