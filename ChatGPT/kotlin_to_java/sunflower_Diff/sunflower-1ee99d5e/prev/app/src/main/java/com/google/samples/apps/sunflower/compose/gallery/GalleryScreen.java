
package com.google.samples.apps.sunflower.compose.gallery;

import androidx.compose.foundation.layout.PaddingValues;
import androidx.compose.foundation.layout.padding;
import androidx.compose.foundation.layout.statusBarsPadding;
import androidx.compose.foundation.lazy.GridCells;
import androidx.compose.foundation.lazy.LazyVerticalGrid;
import androidx.compose.material.Icon;
import androidx.compose.material.IconButton;
import androidx.compose.material.Scaffold;
import androidx.compose.material.Text;
import androidx.compose.material.TopAppBar;
import androidx.compose.material.icons.Icons;
import androidx.compose.material.icons.filled.ArrowBack;
import androidx.compose.runtime.Composable;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.res.dimensionResource;
import androidx.compose.ui.res.stringResource;
import androidx.compose.ui.tooling.preview.Preview;
import androidx.compose.ui.tooling.preview.PreviewParameter;
import androidx.compose.ui.tooling.preview.PreviewParameterProvider;
import androidx.lifecycle.viewmodel.compose.viewModel;
import androidx.paging.PagingData;
import androidx.paging.compose.LazyPagingItems;
import androidx.paging.compose.collectAsLazyPagingItems;
import com.google.samples.apps.sunflower.R;
import com.google.samples.apps.sunflower.compose.plantlist.PhotoListItem;
import com.google.samples.apps.sunflower.data.UnsplashPhoto;
import com.google.samples.apps.sunflower.data.UnsplashPhotoUrls;
import com.google.samples.apps.sunflower.data.UnsplashUser;
import com.google.samples.apps.sunflower.viewmodels.GalleryViewModel;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.flowOf;

@Composable
public class GalleryScreen {
    public static void GalleryScreen(
        GalleryViewModel viewModel,
        Function<UnsplashPhoto, Unit> onPhotoClick,
        Function<Unit, Unit> onUpClick
    ) {
        GalleryScreen(
            viewModel.getPlantPictures(),
            onPhotoClick,
            onUpClick
        );
    }

    private static void GalleryScreen(
        Flow<PagingData<UnsplashPhoto>> plantPictures,
        Function<UnsplashPhoto, Unit> onPhotoClick,
        Function<Unit, Unit> onUpClick
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(stringResource(id = R.string.gallery_title));
                    },
                    Modifier.statusBarsPadding(),
                    navigationIcon = {
                        IconButton(onClick = { onUpClick.invoke() }) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = null
                            );
                        }
                    },
                );
            },
        ) { padding ->
            LazyPagingItems<UnsplashPhoto> pagingItems = plantPictures.collectAsLazyPagingItems();
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues.all(dimensionResource(id = R.dimen.card_side_margin))
            ) {
                items(
                    count = pagingItems.getItemCount(),
                ) { index ->
                    UnsplashPhoto photo = pagingItems.get(index);
                    if (photo != null) {
                        PhotoListItem(photo, () -> onPhotoClick.invoke(photo));
                    }
                }
            }
        }
    }

    @Preview
    @Composable
    private static void GalleryScreenPreview(
        @PreviewParameter(GalleryScreenPreviewParamProvider.class) Flow<PagingData<UnsplashPhoto>> plantPictures
    ) {
        GalleryScreen(plantPictures);
    }

    private static class GalleryScreenPreviewParamProvider implements PreviewParameterProvider<Flow<PagingData<UnsplashPhoto>>> {
        @Override
        public Sequence<Flow<PagingData<UnsplashPhoto>>> getValues() {
            return sequenceOf(
                flowOf(
                    PagingData.from(
                        listOf(
                            new UnsplashPhoto(
                                "1",
                                new UnsplashPhotoUrls("https:"),
                                new UnsplashUser("John Smith", "johnsmith")
                            ),
                            new UnsplashPhoto(
                                "2",
                                new UnsplashPhotoUrls("https:"),
                                new UnsplashUser("Sally Smith", "sallysmith")
                            )
                        )
                    )
                )
            );
        }
    }
}
