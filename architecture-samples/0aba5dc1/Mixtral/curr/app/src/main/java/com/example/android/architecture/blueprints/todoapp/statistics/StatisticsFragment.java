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
import com.example.android.architecture.blueprints.todoapp.statistics.StatisticsViewModel;
import com.example.android.architecture.blueprints.todoapp.util.GetViewModelFactory;
import com.example.android.architecture.blueprints.todoapp.util.SetupRefreshLayout;

public class StatisticsFragment extends Fragment {

  private StatisticsFragBinding viewDataBinding;

  private final StatisticsViewModel viewModel = new ViewModelsProvider<>(
    this,
    new GetViewModelFactory<>()
  )
    .get(StatisticsViewModel.class);

  @Override
  public View onCreateView(
    LayoutInflater inflater,
    ViewGroup container,
    Bundle savedInstanceState
  ) {
    viewDataBinding =
      DataBindingUtil.inflate(
        inflater,
        R.layout.statistics_frag,
        container,
        false
      );
    return viewDataBinding.getRoot();
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    viewDataBinding.setViewmodel(viewModel);
    viewDataBinding.setLifecycleOwner(getViewLifecycleOwner());
    new SetupRefreshLayout()
      .setupRefreshLayout(viewDataBinding.getRefreshLayout());
    viewModel.start();
  }
}
