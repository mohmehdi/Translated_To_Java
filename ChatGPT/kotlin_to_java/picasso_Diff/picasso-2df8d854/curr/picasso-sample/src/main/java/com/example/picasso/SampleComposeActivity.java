package com.example.picasso;

import android.os.Bundle;
import androidx.compose.foundation.ExperimentalFoundationApi;
import androidx.compose.foundation.Image;
import androidx.compose.foundation.layout.aspectRatio;
import androidx.compose.foundation.layout.fillMaxWidth;
import androidx.compose.foundation.lazy.GridCells.Adaptive;
import androidx.compose.foundation.lazy.LazyVerticalGrid;
import androidx.compose.runtime.Composable;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.layout.ContentScale;
import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.unit.dp;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.compose.rememberPainter;

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

    composeView.setContent(() -> ImageGrid(urls));

    setContentView(composeView);
  }
}

@OptIn(ExperimentalFoundationApi.class)
@Composable
public void ImageGrid(
  Modifier modifier,
  List<String> urls,
  Picasso picasso
) {
  LazyVerticalGrid(
    cells = Adaptive(150.dp),
    modifier = modifier
  ) {
    items(urls.size(), (index) -> {
      String url = urls.get(index);
      Image(
        painter = picasso.rememberPainter(key = url, () -> {
          return picasso.load(url).placeholder(R.drawable.placeholder).error(R.drawable.error);
        }),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
      );
    });
  }
}