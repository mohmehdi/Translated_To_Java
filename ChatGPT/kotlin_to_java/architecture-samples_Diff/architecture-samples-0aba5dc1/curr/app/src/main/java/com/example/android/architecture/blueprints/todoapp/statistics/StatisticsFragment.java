
package com.example.android.architecture.blueprints.todoapp.statistics;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.viewModels;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.databinding.StatisticsFragBinding;
import com.example.android.architecture.blueprints.todoapp.util.ViewModelFactory;
import com.example.android.architecture.blueprints.todoapp.util.setupRefreshLayout;

public class StatisticsFragment extends Fragment {

    private StatisticsFragBinding viewDataBinding;

    private final StatisticsViewModel viewModel;

    public StatisticsFragment() {
        this.viewModel = new ViewModelProvider(this, new ViewModelFactory()).get(StatisticsViewModel.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        viewDataBinding = DataBindingUtil.inflate(
                inflater, R.layout.statistics_frag, container,
                false
        );
        return viewDataBinding.getRoot();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        viewDataBinding.setViewmodel(viewModel);
        viewDataBinding.setLifecycleOwner(this.getViewLifecycleOwner());
        this.setupRefreshLayout(viewDataBinding.getRefreshLayout());
        viewModel.start();
    }
}
