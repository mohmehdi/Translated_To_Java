package com.google.samples.apps.sunflower;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.foundation.layout.Arrangement;
import androidx.compose.foundation.layout.Column;
import androidx.compose.foundation.layout.fillMaxSize;
import androidx.compose.material.Button;
import androidx.compose.material.MaterialTheme;
import androidx.compose.material.Surface;
import androidx.compose.material.Text;
import androidx.compose.runtime.Composable;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.tooling.preview.Preview;
import androidx.compose.ui.unit.sp;
import androidx.core.app.ShareCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.Objects;

@AndroidEntryPoint
public class PlantDetailFragment extends Fragment {

private PlantDetailViewModel plantDetailViewModel;

@NonNull
@Override
public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
@Nullable Bundle savedInstanceState) {
ComposeView composeView = new ComposeView(requireContext());
plantDetailViewModel = new ViewModelProvider(this).get(PlantDetailViewModel.class);
composeView.setContent {
MdcTheme {
Column(
modifier = Modifier.fillMaxSize(),
verticalArrangement = Arrangement.Center,
horizontalAlignment = Alignment.CenterHorizontally
) {
Text(
text = plantDetailViewModel.getPlant().getName(),
fontSize = 24.sp
)
Button(
onClick = {
navigateToGallery();
}
) {
Text("Gallery")
}
Button(
onClick = {
createShareIntent();
}
) {
Text("Share")
}
}
}
};
return composeView;
}

private void navigateToGallery() {
Objects.requireNonNull(plantDetailViewModel.getPlant()).ifPresent(plant -> {
String name = plant.getName();
PlantDetailFragmentDirections.ActionPlantDetailFragmentToGalleryFragment action =
PlantDetailFragmentDirections.actionPlantDetailFragmentToGalleryFragment(name);
NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
navController.navigate(action);
});
}

@Suppress("DEPRECATION")
private void createShareIntent() {
String shareText = Objects.requireNonNull(plantDetailViewModel.getPlant()).map(plant -> {
if (plant == null) {
return "";
} else {
return getString(R.string.share_text_plant, plant.getName());
}
}).orElse("");
Intent shareIntent = ShareCompat.IntentBuilder.from((FragmentActivity) requireActivity())
.setText(shareText)
.setType("text/plain")
.createChooserIntent()
.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
startActivity(shareIntent);
}
}