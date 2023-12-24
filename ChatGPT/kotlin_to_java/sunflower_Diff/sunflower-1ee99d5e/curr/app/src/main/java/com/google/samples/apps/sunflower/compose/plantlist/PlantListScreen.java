
package com.google.samples.apps.sunflower.compose.plantlist;

import androidx.compose.foundation.layout.PaddingValues;
import androidx.compose.foundation.lazy.GridCells;
import androidx.compose.foundation.lazy.LazyVerticalGrid;
import androidx.compose.foundation.lazy.LazyVerticalGridKt;
import androidx.compose.foundation.lazy.LazyVerticalGridKt__LazyVerticalGrid;
import androidx.compose.foundation.lazy.LazyVerticalGridKt__LazyVerticalGridKt;
import androidx.compose.foundation.lazy.LazyVerticalGridKt__LazyVerticalGridKt$items$1;
import androidx.compose.foundation.lazy.LazyVerticalGridKt__LazyVerticalGridKt$items$1$1;
import androidx.compose.runtime.Composable;
import androidx.compose.runtime.getValue;
import androidx.compose.runtime.livedata.observeAsState;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.platform.testTag;
import androidx.compose.ui.res.dimensionResource;
import androidx.compose.ui.tooling.preview.Preview;
import androidx.compose.ui.tooling.preview.PreviewParameter;
import androidx.compose.ui.tooling.preview.PreviewParameterProvider;
import androidx.lifecycle.viewmodel.compose.viewModel;
import com.google.samples.apps.sunflower.R;
import com.google.samples.apps.sunflower.data.Plant;
import com.google.samples.apps.sunflower.viewmodels.PlantListViewModel;
import java.util.List;
import kotlin.collections.CollectionsKt;

@Composable
public final class PlantListScreen {
    @Composable
    public static final void PlantListScreen(PlantListViewModel viewModel, Function1<? super Plant, Unit> onPlantClick) {
        List<Plant> plants = (List)CollectionsKt.emptyList();
        plants = (List)viewModel.getPlants().observeAsState((Object)plants).getValue();
        PlantListScreen(plants, onPlantClick);
    }

    @Composable
    public static final void PlantListScreen(List<Plant> plants, Function1<? super Plant, Unit> onPlantClick) {
        LazyVerticalGrid columns = GridCells.fixed(2);
        Modifier modifier = Modifier.testTag("plant_list");
        PaddingValues contentPadding = new PaddingValues(dimensionResource(R.dimen.card_side_margin), dimensionResource(R.dimen.header_margin));
        LazyVerticalGridKt.lazyVerticalGrid$default(columns, modifier, contentPadding, (Function2)(new LazyVerticalGridKt__LazyVerticalGridKt$items$1(plants, onPlantClick)), 4, (Object)null);
    }

    @Preview
    @Composable
    private static final void PlantListScreenPreview(@PreviewParameter(PlantListPreviewParamProvider.class) List<Plant> plants) {
        PlantListScreen(plants);
    }

    private static final class PlantListPreviewParamProvider implements PreviewParameterProvider<List<Plant>> {
        public static final PlantListScreen.PlantListPreviewParamProvider INSTANCE;

        public List<List<Plant>> getValues() {
            return CollectionsKt.listOf(CollectionsKt.emptyList(), CollectionsKt.listOf(new Plant("1", "Apple", "Apple", 1), new Plant("2", "Banana", "Banana", 2), new Plant("3", "Carrot", "Carrot", 3), new Plant("4", "Dill", "Dill", 3)));
        }

        private PlantListPreviewParamProvider() {
        }

        static {
            PlantListPreviewParamProvider var0 = new PlantListPreviewParamProvider();
            INSTANCE = var0;
        }
    }
}
