
package com.google.samples.apps.sunflower.compose.garden;

import androidx.activity.compose.ReportDrawn;
import androidx.activity.compose.ReportDrawnWhen;
import androidx.compose.foundation.layout.Arrangement;
import androidx.compose.foundation.layout.Column;
import androidx.compose.foundation.layout.PaddingValues;
import androidx.compose.foundation.layout.fillMaxWidth;
import androidx.compose.foundation.layout.height;
import androidx.compose.foundation.layout.padding;
import androidx.compose.foundation.lazy.GridCells;
import androidx.compose.foundation.lazy.LazyVerticalGrid;
import androidx.compose.foundation.lazy.rememberLazyGridState;
import androidx.compose.foundation.shape.RoundedCornerShape;
import androidx.compose.material.Button;
import androidx.compose.material.ButtonDefaults;
import androidx.compose.material.Card;
import androidx.compose.material.ExperimentalMaterialApi;
import androidx.compose.material.MaterialTheme;
import androidx.compose.material.Text;
import androidx.compose.runtime.Composable;
import androidx.compose.runtime.collectAsState;
import androidx.compose.runtime.getValue;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.ExperimentalComposeUiApi;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.layout.ContentScale;
import androidx.compose.ui.res.dimensionResource;
import androidx.compose.ui.res.pluralStringResource;
import androidx.compose.ui.res.stringResource;
import androidx.compose.ui.text.font.FontWeight;
import androidx.compose.ui.tooling.preview.Preview;
import androidx.compose.ui.tooling.preview.PreviewParameter;
import androidx.compose.ui.tooling.preview.PreviewParameterProvider;
import androidx.compose.ui.unit.dp;
import com.google.samples.apps.sunflower.viewmodels.GardenPlantingListViewModel;
import androidx.lifecycle.viewmodel.compose.viewModel;
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi;
import com.google.accompanist.themeadapter.material.MdcTheme;
import com.google.samples.apps.sunflower.R;
import com.google.samples.apps.sunflower.compose.card;
import com.google.samples.apps.sunflower.compose.utils.SunflowerImage;
import com.google.samples.apps.sunflower.data.GardenPlanting;
import com.google.samples.apps.sunflower.data.Plant;
import com.google.samples.apps.sunflower.data.PlantAndGardenPlantings;
import com.google.samples.apps.sunflower.viewmodels.PlantAndGardenPlantingsViewModel;
import java.util.Calendar;
import java.util.List;

@Composable
public class GardenScreen {
    public static void GardenScreen(
        GardenPlantingListViewModel viewModel,
        Runnable onAddPlantClick,
        OnPlantClickListener onPlantClick
    ) {
        List<PlantAndGardenPlantings> gardenPlants = viewModel.getPlantAndGardenPlantings().collectAsState(initial = emptyList()).getValue();
        GardenScreen(gardenPlants, onAddPlantClick, onPlantClick);
    }

    private static void GardenScreen(
        List<PlantAndGardenPlantings> gardenPlants,
        Runnable onAddPlantClick,
        OnPlantClickListener onPlantClick
    ) {
        if (gardenPlants.isEmpty()) {
            EmptyGarden(onAddPlantClick);
        } else {
            GardenList(gardenPlants, onPlantClick);
        }
    }

    private static void GardenList(
        List<PlantAndGardenPlantings> gardenPlants,
        OnPlantClickListener onPlantClick
    ) {
        LazyVerticalGrid gridState = rememberLazyGridState();
        ReportDrawnWhen(() -> gridState.getLayoutInfo().getTotalItemsCount() > 0);
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = new PaddingValues(
                horizontal = dimensionResource(id = R.dimen.card_side_margin),
                vertical = dimensionResource(id = R.dimen.margin_normal)
            )
        ) {
            items(count = gardenPlants.size(), itemContent = (index) -> {
                PlantAndGardenPlantings plant = gardenPlants.get(index);
                GardenListItem(plant, onPlantClick);
            });
        }
    }

    @OptIn({ExperimentalMaterialApi.class, ExperimentalGlideComposeApi.class, ExperimentalComposeUiApi.class})
    private static void GardenListItem(
        PlantAndGardenPlantings plant,
        OnPlantClickListener onPlantClick
    ) {
        PlantAndGardenPlantingsViewModel vm = new PlantAndGardenPlantingsViewModel(plant);

        int cardSideMargin = dimensionResource(id = R.dimen.card_side_margin);
        int marginNormal = dimensionResource(id = R.dimen.margin_normal);

        Card(
            onClick = () -> onPlantClick.onPlantClick(plant),
            modifier = Modifier.padding(
                start = cardSideMargin,
                end = cardSideMargin,
                bottom = dimensionResource(id = R.dimen.card_bottom_margin)
            ),
            elevation = dimensionResource(id = R.dimen.card_elevation),
            shape = MaterialTheme.shapes.card
        ) {
            Column(Modifier.fillMaxWidth()) {
                SunflowerImage(
                    model = vm.getImageUrl(),
                    contentDescription = plant.getPlant().getDescription(),
                    Modifier
                        .fillMaxWidth()
                        .height(dimensionResource(id = R.dimen.plant_item_image_height)),
                    contentScale = ContentScale.Crop
                );

                Text(
                    text = vm.getPlantName(),
                    Modifier
                        .padding(vertical = marginNormal)
                        .align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.subtitle1
                );

                Text(
                    text = stringResource(id = R.string.plant_date_header),
                    Modifier.align(Alignment.CenterHorizontally),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primaryVariant,
                    style = MaterialTheme.typography.subtitle2
                );
                Text(
                    text = vm.getPlantDateString(),
                    Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.subtitle2
                );

                Text(
                    text = stringResource(id = R.string.watered_date_header),
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = marginNormal),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primaryVariant,
                    style = MaterialTheme.typography.subtitle2
                );
                Text(
                    text = vm.getWaterDateString(),
                    Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.subtitle2
                );
                Text(
                    text = pluralStringResource(
                        id = R.plurals.watering_next,
                        count = vm.getWateringInterval(),
                        vm.getWateringInterval()
                    ),
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = marginNormal),
                    style = MaterialTheme.typography.subtitle2
                );
            }
        }
    }

    private static void EmptyGarden(Runnable onAddPlantClick) {
        ReportDrawn();

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(id = R.string.garden_empty),
                style = MaterialTheme.typography.h5
            );
            Button(
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.onPrimary),
                shape = new RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = dimensionResource(id = R.dimen.button_corner_radius),
                    bottomStart = dimensionResource(id = R.dimen.button_corner_radius),
                    bottomEnd = 0.dp
                ),
                onClick = onAddPlantClick
            ) {
                Text(
                    color = MaterialTheme.colors.primary,
                    text = stringResource(id = R.string.add_plant)
                );
            }
        }
    }

    @Preview
    private static void GardenScreenPreview(
        @PreviewParameter(GardenScreenPreviewParamProvider.class) List<PlantAndGardenPlantings> gardenPlants
    ) {
        MdcTheme {
            GardenScreen(gardenPlants);
        }
    }

    private static class GardenScreenPreviewParamProvider implements PreviewParameterProvider<List<PlantAndGardenPlantings>> {
        @Override
        public Sequence<List<PlantAndGardenPlantings>> getValues() {
            return sequenceOf(
                emptyList(),
                listOf(
                    new PlantAndGardenPlantings(
                        new Plant(
                            "1",
                            "Apple",
                            "An apple.",
                            1,
                            2,
                            "https://example.com/apple.jpg"
                        ),
                        listOf(
                            new GardenPlanting(
                                "1",
                                Calendar.getInstance(),
                                Calendar.getInstance()
                            )
                        )
                    )
                )
            );
        }
    }

    public interface OnPlantClickListener {
        void onPlantClick(PlantAndGardenPlantings plant);
    }
}
