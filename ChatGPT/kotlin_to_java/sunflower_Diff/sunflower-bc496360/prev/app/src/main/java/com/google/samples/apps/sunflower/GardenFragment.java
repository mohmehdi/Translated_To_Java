
package com.google.samples.apps.sunflower;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.viewModels;
import androidx.viewpager2.widget.ViewPager2;
import com.google.samples.apps.sunflower.adapters.GardenPlantingAdapter;
import com.google.samples.apps.sunflower.adapters.PLANT_LIST_PAGE_INDEX;
import com.google.samples.apps.sunflower.databinding.FragmentGardenBinding;
import com.google.samples.apps.sunflower.utilities.InjectorUtils;
import com.google.samples.apps.sunflower.viewmodels.GardenPlantingListViewModel;

public class GardenFragment extends Fragment {

    private FragmentGardenBinding binding;

    private final GardenPlantingListViewModel viewModel by viewModels {
        InjectorUtils.provideGardenPlantingListViewModelFactory(requireContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentGardenBinding.inflate(inflater, container, false);
        GardenPlantingAdapter adapter = new GardenPlantingAdapter();
        binding.gardenList.setAdapter(adapter);

        binding.addPlant.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToPlantListPage();
            }
        });

        subscribeUi(adapter, binding);
        return binding.getRoot();
    }

    private void subscribeUi(GardenPlantingAdapter adapter, FragmentGardenBinding binding) {
        viewModel.getPlantAndGardenPlantings().observe(getViewLifecycleOwner(), result -> {
            binding.setHasPlantings(!result.isNullOrEmpty());
            adapter.submitList(result);
        });
    }

    private void navigateToPlantListPage() {
        requireActivity().findViewById<ViewPager2>(R.id.view_pager).setCurrentItem(PLANT_LIST_PAGE_INDEX);
    }
}
