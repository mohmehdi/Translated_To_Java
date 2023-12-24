
package com.google.samples.apps.sunflower.compose.plantlist;

import androidx.compose.foundation.layout.Column;
import androidx.compose.foundation.layout.fillMaxWidth;
import androidx.compose.foundation.layout.height;
import androidx.compose.foundation.layout.padding;
import androidx.compose.foundation.layout.wrapContentWidth;
import androidx.compose.material.Card;
import androidx.compose.material.ExperimentalMaterialApi;
import androidx.compose.material.MaterialTheme;
import androidx.compose.material.Text;
import androidx.compose.runtime.Composable;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.layout.ContentScale;
import androidx.compose.ui.res.dimensionResource;
import androidx.compose.ui.res.stringResource;
import androidx.compose.ui.text.style.TextAlign;
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi;
import com.google.samples.apps.sunflower.R;
import com.google.samples.apps.sunflower.compose.card;
import com.google.samples.apps.sunflower.compose.utils.SunflowerImage;
import com.google.samples.apps.sunflower.data.Plant;
import com.google.samples.apps.sunflower.data.UnsplashPhoto;

@Composable
public class PlantListItem {
    public static void PlantListItem(Plant plant, Runnable onClick) {
        ImageListItem(plant.getName(), plant.getImageUrl(), onClick);
    }
}

@Composable
public class PhotoListItem {
    public static void PhotoListItem(UnsplashPhoto photo, Runnable onClick) {
        ImageListItem(photo.getUser().getName(), photo.getUrls().getSmall(), onClick);
    }
}

@OptIn(ExperimentalMaterialApi.class)
@Composable
public class ImageListItem {
    public static void ImageListItem(String name, String imageUrl, Runnable onClick) {
        Card card = new Card(onClick = onClick,
                elevation = dimensionResource(id = R.dimen.card_elevation),
                shape = MaterialTheme.shapes.card,
                modifier = Modifier
                        .padding(horizontal = dimensionResource(id = R.dimen.card_side_margin))
                        .padding(bottom = dimensionResource(id = R.dimen.card_bottom_margin))
        );
        Column column = new Column(Modifier.fillMaxWidth());
        SunflowerImage sunflowerImage = new SunflowerImage(
                model = imageUrl,
                contentDescription = stringResource(R.string.a11y_plant_item_image),
                Modifier
                        .fillMaxWidth()
                        .height(dimensionResource(id = R.dimen.plant_item_image_height)),
                contentScale = ContentScale.Crop
        );
        Text text = new Text(
                text = name,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = dimensionResource(id = R.dimen.margin_normal))
                        .wrapContentWidth(Alignment.CenterHorizontally)
        );
        column.addChild(sunflowerImage);
        column.addChild(text);
        card.addChild(column);
    }
}
