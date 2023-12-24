
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
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavArgs;
import androidx.navigation.fragment.NavArgsLazy;
import com.google.android.material.composethemeadapter.MdcTheme;
import com.google.samples.apps.sunflower.compose.plantdetail.PlantDetailsScreen;

public class PlantDetailFragment extends Fragment {

    private final NavArgsLazy<PlantDetailFragmentArgs> args = NavArgsLazy.from(this, getArguments());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ComposeView composeView = new ComposeView(requireContext());
        composeView.setId(View.generateViewId());

        composeView.setContentView(() -> {
            MdcTheme.INSTANCE.getTheme().applyTo(composeView);
            return new PlantDetailsScreen(
                    args.getValue().getPlantId(),
                    () -> Navigation.findNavController(composeView).navigateUp(),
                    this::createShareIntent
            );
        });

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
