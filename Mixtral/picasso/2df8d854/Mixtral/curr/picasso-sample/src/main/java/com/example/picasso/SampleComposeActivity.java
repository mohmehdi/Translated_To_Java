package com.example.picasso;

import android.os.Bundle;
import androidx.activity.viewModels;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.foundation.ExperimentalFoundationApi;
import androidx.compose.foundation.Image;
import androidx.compose.foundation.layout.AspectRatio;
import androidx.compose.foundation.layout.FillMaxWidth;
import androidx.compose.foundation.lazy.LazyVerticalGrid;
import androidx.compose.foundation.lazy.gridcells.AdaptiveGridCells;
import androidx.compose.material.ContentScale;
import androidx.compose.runtime.Composable;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.unit.Dp;
import androidx.compose.ui.unit.dp;
import com.squareup.picasso3.Picasso;

public class SampleComposeActivity extends PicassoSampleActivity {
@Override
protected void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);
  ComposeView composeView = new ComposeView(this);

  List<String> urls = new ArrayList<>(Data.URLS);
  Collections.shuffle(urls);
  urls.addAll(new ArrayList<>(Data.URLS));
  Collections.shuffle(urls);
  urls.addAll(new ArrayList<>(Data.URLS));
  Collections.shuffle(urls);

  composeView.setContent {
    ImageGrid(urls)
  };

  setContentView(composeView);
}

@Composable
public void ImageGrid(Modifier modifier, List<String> urls) {
    Picasso picasso = PicassoInitializer.get();
    LazyVerticalGrid(
        cells = new Adaptive(150.dp),
        modifier = modifier
    ) {
        items(urls.size()) { index ->
            String url = urls.get(index);
            Image(
                painter = picasso.rememberPainter(url) {
                    it.load(url).placeholder(R.drawable.placeholder).error(R.drawable.error);
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
            );
        }
    };
}
}