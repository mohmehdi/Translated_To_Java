
package com.google.samples.apps.sunflower;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.viewModels;
import androidx.lifecycle.lifecycleScope;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.navArgs;
import com.google.samples.apps.sunflower.adapters.GalleryAdapter;
import com.google.samples.apps.sunflower.databinding.FragmentGalleryBinding;
import com.google.samples.apps.sunflower.viewmodels.GalleryViewModel;
import dagger.hilt.android.AndroidEntryPoint;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.flow.collectLatest;
import kotlinx.coroutines.launch;

@AndroidEntryPoint
public class GalleryFragment extends Fragment {

    private GalleryAdapter adapter = new GalleryAdapter();
    private GalleryFragmentArgs args by navArgs();
    private Job searchJob = null;
    private GalleryViewModel viewModel by viewModels();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FragmentGalleryBinding binding = FragmentGalleryBinding.inflate(inflater, container, false);
        if (getContext() == null) {
            return binding.getRoot();
        }

        binding.getPhotoList().setAdapter(adapter);
        search(args.getPlantName());

        binding.getToolbar().setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Navigation.findNavController(view).navigateUp();
            }
        });

        return binding.getRoot();
    }

    private void search(String query) {
        if (searchJob != null) {
            searchJob.cancel();
        }
        searchJob = lifecycleScope.launch {
            viewModel.searchPictures(query).collectLatest {
                adapter.submitData(it);
            }
        };
    }
}
