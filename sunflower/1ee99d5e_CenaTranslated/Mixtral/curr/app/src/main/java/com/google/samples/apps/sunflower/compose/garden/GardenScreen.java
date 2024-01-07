package com.google.samples.apps.sunflower.compose.garden;

import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.activity.OnBackPressedDispatcherOwner;
import androidx.activity.compose.BackHandler;
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner;
import androidx.activity.compose.ReportDrawn;
import androidx.activity.compose.ReportDrawnWhen;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.Composable;
import androidx.compose.foundation.Image;
import androidx.compose.foundation.LayoutModifier;
import androidx.compose.foundation.lazy.grid.GridCells;
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid;
import androidx.compose.foundation.lazy.grid.items;
import androidx.compose.foundation.shape.RoundedCornerShape;
import androidx.compose.material.Card;
import androidx.compose.material.ContentAlpha;
import androidx.compose.material.MaterialTheme;
import androidx.compose.material.Surface;
import androidx.compose.material.Text;
import androidx.compose.runtime.Composable;
import androidx.compose.runtime.DisposableEffect;
import androidx.compose.runtime.collectAsState;
import androidx.compose.runtime.getValue;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.draw.ContentScale;
import androidx.compose.ui.graphics.Color;
import androidx.compose.ui.layout.ContentScale;
import androidx.compose.ui.platform.LocalContext;
import androidx.compose.ui.res.dimensionResource;
import androidx.compose.ui.res.pluralStringResource;
import androidx.compose.ui.res.stringResource;
import androidx.compose.ui.text.font.FontWeight;
import androidx.compose.ui.tooling.preview.Preview;
import androidx.compose.ui.unit.dp;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.compose.viewModel;
import com.google.accompanist.themeadapter.material.MdcTheme;
import com.google.samples.apps.sunflower.R;
import com.google.samples.apps.sunflower.compose.SunflowerImage;
import com.google.samples.apps.sunflower.data.GardenPlanting;
import com.google.samples.apps.sunflower.data.Plant;
import com.google.samples.apps.sunflower.data.PlantAndGardenPlantings;
import com.google.samples.apps.sunflower.viewmodels.GardenPlantingListViewModel;
import com.google.samples.apps.sunflower.viewmodels.PlantAndGardenPlantingsViewModel;
import com.google.samples.apps.sunflower.viewmodels.PlantAndGardenPlantingsViewModelFactory;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Composable
public fun GardenScreen(
    @NonNull viewModel: GardenPlantingListViewModel,
    @NonNull onAddPlantClick: () - > Unit,
    @NonNull onPlantClick: (PlantAndGardenPlantings) - > Unit
) {
    List gardenPlants = viewModel.getPlantAndGardenPlantings().collectAsState(
        Collections.emptyList()).getValue();
    GardenScreen(gardenPlants, onAddPlantClick, onPlantClick);
}

@Composable
public fun GardenScreen(
    @NonNull gardenPlants: List,
    @Nullable onAddPlantClick: () - > Unit = null,
    @Nullable onPlantClick: (PlantAndGardenPlantings) - > Unit = null
) {
    if (gardenPlants.isEmpty()) {
        EmptyGarden(onAddPlantClick);
    } else {
        GardenList(gardenPlants, onPlantClick);
    }
}

@Composable
private void GardenList(
    @NonNull List gardenPlants,
    @NonNull onPlantClick: (PlantAndGardenPlantings) - > Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            horizontal = dimensionResource(id = R.dimen.card_side_margin),
            vertical = dimensionResource(id = R.dimen.margin_normal)
        ),
        state = rememberLazyGridState()
    ) {
        items(
            items = gardenPlants,
            key = {
                it.plant.plantId
            }
        ) {
            GardenListItem(plant = it, onPlantClick = onPlantClick);
        }
    }
}

@Composable
private void GardenListItem(
    @NonNull PlantAndGardenPlantings plant,
    @NonNull onPlantClick: (PlantAndGardenPlantings) - > Unit
) {
    PlantAndGardenPlantingsViewModel vm = new PlantAndGardenPlantingsViewModel(plant);

    Modifier.padding(
            start = dimensionResource(id = R.dimen.card_side_margin),
            end = dimensionResource(id = R.dimen.card_side_margin),
            bottom = dimensionResource(id = R.dimen.card_bottom_margin)
        ),
        elevation = dimensionResource(id = R.dimen.card_elevation),
        shape = MaterialTheme.shapes.card,
);
Column(Modifier.fillMaxWidth()) {
    SunflowerImage(
        model = vm.getImageUrl(),
        contentDescription = plant.plant.description,
        Modifier
        .fillMaxWidth()
        .height(dimensionResource(id = R.dimen.plant_item_image_height)),
        contentScale = ContentScale.Crop,
    );

    Text(
        text = vm.getPlantName(),
        Modifier
        .padding(vertical = dimensionResource(id = R.dimen.margin_normal))
        .align(Alignment.CenterHorizontally),
        style = MaterialTheme.typography.subtitle1,
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
        .padding(top = dimensionResource(id = R.dimen.margin_normal)),
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
        .padding(bottom = dimensionResource(id = R.dimen.margin_normal)),
        style = MaterialTheme.typography.subtitle2
    );
}
}

@Composable
private void EmptyGarden(@Nullable onAddPlantClick: () - > Unit) {

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
            onClick = onAddPlantClick,
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.onPrimary),
            shape = RoundedCornerShape(
                topStart = 0. dp,
                topEnd = dimensionResource(id = R.dimen.button_corner_radius),
                bottomStart = dimensionResource(id = R.dimen.button_corner_radius),
                bottomEnd = 0. dp,
            ),
        ); {
            Text(
                color = MaterialTheme.colors.primary,
                text = stringResource(id = R.string.add_plant)
            );
        }
    }
}

@Preview
@Composable
private void GardenScreenPreview(
    @NonNull PlantAndGardenPlantings...gardenPlants
) {
    MdcTheme {
        GardenScreen(gardenPlants);
    }
}

public class PlantAndGardenPlantingsViewModel extends ViewModel {
    private final PlantAndGardenPlantings plantAndGardenPlantings;

    public PlantAndGardenPlantingsViewModel(
        @NonNull PlantAndGardenPlantings plantAndGardenPlantings
    ) {
        this.plantAndGardenPlantings = plantAndGardenPlantings;
    }

    public String getImageUrl() {
        return plantAndGardenPlantings.plant.imageUrl;
    }

    public String getPlantName() {
        return plantAndGardenPlantings.plant.name;
    }

    public String getPlantDateString() {
        Calendar calendar = plantAndGardenPlantings.gardenPlantings.get(0).plantDate;
        return String.format(
            Locale.getDefault(),
            "%02d/%02d/%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        );
    }

    public String getWaterDateString() {
        Calendar calendar = plantAndGardenPlantings.gardenPlantings.get(0).lastWateringDate;
        return String.format(
            Locale.getDefault(),
            "%02d/%02d/%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        );
    }

    public int getWateringInterval() {
        return plantAndGardenPlantings.plant.wateringInterval;
    }
}

public class PlantAndGardenPlantingsViewModelFactory implements ViewModelProvider.Factory {
    private final PlantAndGardenPlantings plantAndGardenPlantings;

    public PlantAndGardenPlantingsViewModelFactory(
        @NonNull PlantAndGardenPlantings plantAndGardenPlantings
    ) {
        this.plantAndGardenPlantings = plantAndGardenPlantings;
    }

    @NonNull
    @Override
    public T create(@NonNull Class modelClass) {
        if (modelClass.isAssignableFrom(PlantAndGardenPlantingsViewModel.class)) {
            return (T) new PlantAndGardenPlantingsViewModel(plantAndGardenPlantings);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}

public class GardenScreenPreviewParamProvider implements PreviewParameterProvider < List > {
    @NonNull
    @Override
    public List provideValues() {
        return List.of(
            PlantAndGardenPlantings.builder()
            .plant(
                Plant.builder()
                .plantId("1")
                .name("Apple")
                .description("An apple.")
                .growZoneNumber(1)
                .wateringInterval(2)
                .imageUrl("https://")
                .build()
            )
            .gardenPlantings(
                List.of(
                    GardenPlanting.builder()
                    .plantId("1")
                    .plantDate(Calendar.getInstance())
                    .lastWateringDate(Calendar.getInstance())
                    .build()
                )
            )
            .build()
        );
    }
}