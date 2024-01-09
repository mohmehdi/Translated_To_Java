package com.google.samples.apps.sunflower.compose.plantlist;

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.foundation.lazy.grid.GridCells;
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid;
import androidx.compose.foundation.lazy.grid.items;
import androidx.compose.runtime.Composable;
import androidx.compose.runtime.getValue;
import androidx.compose.runtime.livedata.observeAsState;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.platform.testTag;
import androidx.compose.ui.res.dimensionResource;
import androidx.compose.ui.tooling.preview.Preview;
import androidx.compose.ui.tooling.preview.PreviewParameter;
import androidx.compose.ui.unit.Dp;
import androidx.compose.ui.unit.dp;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.google.samples.apps.sunflower.R;
import com.google.samples.apps.sunflower.data.Plant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class PlantListScreen {

    @Composable
    public static void PlantListScreen(
        PlantListViewModel viewModel,
        PlantClickListener onPlantClick
    ) {
        List < Plant > plants = viewModel.getPlants().observeAsState(emptyList()).getValue();
        PlantListScreen(plants, onPlantClick);
    }

    @Composable
    public static void PlantListScreen(
        List < Plant > plants,
        PlantClickListener onPlantClick
    ) {
        LazyVerticalGrid grid = new LazyVerticalGrid(
            GridCells.Fixed(2),
            modifier - > modifier.testTag("plant_list"),
            new PaddingValues(
                horizontal - > dimensionResource(R.dimen.card_side_margin).toDp(),
                vertical - > dimensionResource(R.dimen.header_margin).toDp()
            )
        );

        grid.setItems(
            items - > plants,
            plant - > {
                PlantListItem item = new PlantListItem(plant, () - > onPlantClick.onPlantClick(plant));
                return item;
            }
        );
    }

    @Preview
    @Composable
    private static void PlantListScreenPreview(
        @PreviewParameter PlantListPreviewParamProvider plants
    ) {
        PlantListScreen(plants.getPlants());
    }

    public interface PlantClickListener {
        void onPlantClick(Plant plant);
    }

    public static class PlantListViewModel extends ViewModel {
        private final AtomicInteger plantId = new AtomicInteger(1);
        private final List < Plant > plants = List.of(
            new Plant("1", "Apple", "Apple", 1),
            new Plant("2", "Banana", "Banana", 2),
            new Plant("3", "Carrot", "Carrot", 3),
            new Plant("4", "Dill", "Dill", 3)
        );

        public MutableLiveData < List < Plant >> getPlants() {
            MutableLiveData < List < Plant >> plantsLiveData = new MutableLiveData < > ();
            plantsLiveData.setValue(plants);
            return plantsLiveData;
        }
    }

    public static class PlantListPreviewParamProvider implements PreviewParameterProvider < List < Plant >> {
        private static final List < List < Plant >> PLANTS = List.of(
            List.of(),
            List.of(
                new Plant("1", "Apple", "Apple", 1),
                new Plant("2", "Banana", "Banana", 2),
                new Plant("3", "Carrot", "Carrot", 3),
                new Plant("4", "Dill", "Dill", 3)
            )
        );

        @NonNull
        @Override
        public List < Plant > getValue(int index) {
            return PLANTS.get(index);
        }
    }
}