
package com.example.android.architecture.blueprints.todoapp.statistics;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.databinding.StatisticsFragBinding;
import com.example.android.architecture.blueprints.todoapp.util.ViewModelUtil;

public class StatisticsFragment extends Fragment {

    private StatisticsFragBinding viewDataBinding;
    private StatisticsViewModel statisticsViewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        viewDataBinding = DataBindingUtil.inflate(inflater, R.layout.statistics_frag, container,
                false);
        return viewDataBinding.getRoot();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        statisticsViewModel = ViewModelUtil.obtainViewModel(getActivity(), StatisticsViewModel.class);
        viewDataBinding.setStats(statisticsViewModel);
        viewDataBinding.setLifecycleOwner(this.getViewLifecycleOwner());
    }

    @Override
    public void onResume() {
        super.onResume();
        statisticsViewModel.start();
    }
}
