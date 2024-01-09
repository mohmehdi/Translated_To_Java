package com.google.samples.apps.sunflower.compose.gallery;

import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.ComponentActivity;
import androidx.activity.viewModels;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.foundation.layout.PaddingValues;
import androidx.compose.foundation.layout.statusBarsPadding;
import androidx.compose.foundation.lazy.grid.GridCells;
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid;
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
import androidx.lifecycle.Observer;
import androidx.lifecycle.viewmodel.compose.ViewModel;
import androidx.lifecycle.viewmodel.compose.ViewModelKt;
import androidx.paging.PagingData;
import androidx.paging.PagingDataAdapter;
import androidx.paging.PagingSource;
import androidx.paging.PagingState;
import androidx.paging.compose.LazyPagingItems;
import androidx.paging.compose.collectAsLazyPagingItems;
import com.google.samples.apps.sunflower.R;
import com.google.samples.apps.sunflower.data.UnsplashPhoto;
import com.google.samples.apps.sunflower.data.UnsplashPhotoUrls;
import com.google.samples.apps.sunflower.data.UnsplashUser;
import com.google.samples.apps.sunflower.viewmodels.GalleryViewModel;
import java.util.List;
import java.util.concurrent.Flow;

public class GalleryScreenActivity extends ComponentActivity {

    private GalleryViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = ViewModelKt.getViewModel(this, GalleryViewModel.class);
        setContent {
            GalleryScreen(viewModel.getPlantPictures(), this::onPhotoClick, this::onUpClick);
        }
    }

    private void onPhotoClick(UnsplashPhoto photo) {
        // Handle photo click
    }

    private void onUpClick() {
        finish();
    }

    @Composable
    private void GalleryScreen(
        Flow < PagingData < UnsplashPhoto >> plantPictures,
        OnPhotoClick onPhotoClick,
        OnUpClick onUpClick) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(stringResource(id = R.string.gallery_title))
                    },
                    Modifier.statusBarsPadding(),
                    navigationIcon = {
                        IconButton(onClick = () - > onUpClick.onUpClick()) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                null
                            );
                        }
                    }
                );
            }
        ) {
            padding - >
                LazyPagingItems < UnsplashPhoto > pagingItems = plantPictures.collectAsLazyPagingItems();
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.padding(padding),
                contentPadding = new PaddingValues(
                    all = dimensionResource(id = R.dimen.card_side_margin))
            ) {
                // TODO update this implementation once paging Compose supports LazyGridScope
                // See: https://issuetracker.google.com/issues/178087310
                for (int i = 0; i < pagingItems.getItemCount(); i++) {
                    int index = i;
                    UnsplashPhoto photo = pagingItems.peek(i);
                    if (photo == null) {
                        return @for;
                    }
                    PhotoListItem(photo, () - > onPhotoClick.onPhotoClick(photo));
                }
            };
        }
    }

    @Preview
    @Composable
    private void GalleryScreenPreview(
        @PreviewParameter(GalleryScreenPreviewParamProvider.class) Flow < PagingData < UnsplashPhoto >> plantPictures) {
        GalleryScreen(plantPictures, this::onPhotoClick, this::onUpClick);
    }

    private static class GalleryScreenPreviewParamProvider implements PreviewParameterProvider < Flow < PagingData < UnsplashPhoto >>> {

        @NonNull
        @Override
        public List < Flow < PagingData < UnsplashPhoto >>> getValues() {
            return List.of(
                new PagingData.from(
                    List.of(
                        new UnsplashPhoto(
                            "1",
                            new UnsplashPhotoUrls("https://images.unsplash.com/photo-1417325384643-aac51acc9e5d?q=75&fm=jpg&w=400&fit=max"),
                            new UnsplashUser("John Smith", "johnsmith")
                        ),
                        new UnsplashPhoto(
                            "2",
                            new UnsplashPhotoUrls("https://images.unsplash.com/photo-1417325384643-aac51acc9e5d?q=75&fm=jpg&w=400&fit=max"),
                            new UnsplashUser("Sally Smith", "sallysmith")
                        )
                    )
                )
            );
        }
    }

    public interface OnPhotoClick {
        void onPhotoClick(UnsplashPhoto photo);
    }

    public interface OnUpClick {
        void onUpClick();
    }
}