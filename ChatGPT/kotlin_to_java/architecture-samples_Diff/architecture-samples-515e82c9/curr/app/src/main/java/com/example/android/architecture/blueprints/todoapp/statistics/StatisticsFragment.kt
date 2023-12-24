
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
import com.example.android.architecture.blueprints.todoapp.util.getVmFactory;
import com.example.android.architecture.blueprints.todoapp.util.setupRefreshLayout;

public class StatisticsFragment extends Fragment {

    private StatisticsFragBinding viewDataBinding;

    private final StatisticsViewModel statisticsViewModel by viewModels<StatisticsViewModel> { getVmFactory() };

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
        viewDataBinding.setViewmodel(statisticsViewModel);
        viewDataBinding.setLifecycleOwner(this.getViewLifecycleOwner());
        this.setupRefreshLayout(viewDataBinding.getRefreshLayout());
    }
}
