
package com.google.samples.apps.sunflower;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.app.ShareCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.viewModels;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.composethemeadapter.MdcTheme;
import com.google.samples.apps.sunflower.compose.plantdetail.PlantDetailsScreen;
import com.google.samples.apps.sunflower.viewmodels.PlantDetailViewModel;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PlantDetailFragment extends Fragment {

    private PlantDetailViewModel plantDetailViewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        plantDetailViewModel = new ViewModelProvider(this).get(PlantDetailViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ComposeView composeView = new ComposeView(requireContext());
        composeView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        composeView.setContent(() -> {
            MdcTheme.INSTANCE.getTheme().getComposeContent().invoke(() -> {
                PlantDetailsScreen plantDetailsScreen = new PlantDetailsScreen(
                        plantDetailViewModel,
                        () -> Navigation.findNavController(requireView()).navigateUp(),
                        () -> createShareIntent()
                );
                plantDetailsScreen.getContent();
            });
        });
        return composeView;
    }

    private void navigateToGallery() {
        if (plantDetailViewModel.getPlant().getValue() != null) {
            String plantName = plantDetailViewModel.getPlant().getValue().getName();
            NavHostFragment.findNavController(this).navigate(PlantDetailFragmentDirections.actionPlantDetailFragmentToGalleryFragment(plantName));
        }
    }

    @SuppressWarnings("deprecation")
    private void createShareIntent() {
        String shareText = plantDetailViewModel.getPlant().getValue() != null ?
                getString(R.string.share_text_plant, plantDetailViewModel.getPlant().getValue().getName()) : "";
        Intent shareIntent = ShareCompat.IntentBuilder.from(requireActivity())
                .setText(shareText)
                .setType("text/plain")
                .createChooserIntent()
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        startActivity(shareIntent);
    }
}
