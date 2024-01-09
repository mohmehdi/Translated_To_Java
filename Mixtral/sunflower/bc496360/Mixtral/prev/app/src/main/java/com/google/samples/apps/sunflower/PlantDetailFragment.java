package com.google.samples.apps.sunflower;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import com.google.android.material.composethemeadapter.MdcTheme;

public class PlantDetailFragment extends Fragment {

    private PlantDetailFragmentArgs args;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ComposeView composeView = new ComposeView(requireContext());
        args = PlantDetailFragmentArgs.fromBundle(getArguments());

        composeView.setContent {
            MdcTheme {
                PlantDetailsScreen(
                        args.getPlantId(),
                        view -> Navigation.findNavController(view).navigateUp(),
                        textToShare -> createShareIntent(textToShare)
                );
            }
        };

        return composeView;
    }

    @SuppressWarnings("deprecation")
    private void createShareIntent(String shareText) {
        Intent shareIntent = ShareCompat.IntentBuilder.from(requireActivity())
                .setText(shareText)
                .setType("text/plain")
                .createChooserIntent()
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        startActivity(shareIntent);
    }
}