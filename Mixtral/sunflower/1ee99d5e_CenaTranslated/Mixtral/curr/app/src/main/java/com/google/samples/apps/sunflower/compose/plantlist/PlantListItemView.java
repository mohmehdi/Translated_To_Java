package com.google.samples.apps.sunflower.compose.plantlist;

import android.content.res.Configuration;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.compose.foundation.Image;
import androidx.compose.foundation.layout.Column;
import androidx.compose.foundation.layout.fillMaxWidth;
import androidx.compose.foundation.layout.height;
import androidx.compose.foundation.layout.padding;
import androidx.compose.material.Card;
import androidx.compose.material.MaterialTheme;
import androidx.compose.material.Text;
import androidx.compose.runtime.Composable;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.layout.ContentScale;
import androidx.compose.ui.res.dimensionResource;
import androidx.compose.ui.res.stringResource;
import androidx.compose.ui.tooling.preview.Preview;
import com.bumptech.glide.integration.compose.GlideImage;
import com.google.samples.apps.sunflower.R;
import com.google.samples.apps.sunflower.data.Plant;
import com.google.samples.apps.sunflower.data.UnsplashPhoto;

@Composable
public void PlantListItem(Plant plant, Runnable onClick) {
    ImageListItem("plant", plant.getName(), plant.getImageUrl(), onClick);
}

@Composable
public void PhotoListItem(UnsplashPhoto photo, Runnable onClick) {
    ImageListItem("photo", photo.getUser().getName(), photo.getUrls().getSmall(), onClick);
}

@Composable
public void ImageListItem(String name, String imageUrl, @DrawableRes int imageRes, Runnable onClick) {
    Card(
        onClick,
        dimensionResource(R.dimen.card_elevation),
        MaterialTheme.shapes.card,
        Modifier
        .padding(horizontal = dimensionResource(R.dimen.card_side_margin))
        .padding(bottom = dimensionResource(R.dimen.card_bottom_margin))
    );

    Column(Modifier.fillMaxWidth());

    GlideImage(
        imageUrl,
        stringResource(R.string.a11y_plant_item_image),
        Modifier
        .fillMaxWidth()
        .height(dimensionResource(R.dimen.plant_item_image_height)),
        ContentScale.Crop
    );

    Text(
        name,
        TextAlign.Center,
        1,
        Modifier
        .fillMaxWidth()
        .padding(vertical = dimensionResource(R.dimen.margin_normal))
        .wrapContentWidth(Alignment.CenterHorizontally)
    );
}
