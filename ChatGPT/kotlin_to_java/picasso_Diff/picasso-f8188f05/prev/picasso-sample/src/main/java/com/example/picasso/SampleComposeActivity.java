package com.example.picasso;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.os.Bundle;
import androidx.compose.foundation.Image;
import androidx.compose.foundation.background;
import androidx.compose.foundation.clickable;
import androidx.compose.foundation.layout.Arrangement;
import androidx.compose.foundation.layout.Box;
import androidx.compose.foundation.layout.Column;
import androidx.compose.foundation.layout.Row;
import androidx.compose.foundation.layout.Spacer;
import androidx.compose.foundation.layout.aspectRatio;
import androidx.compose.foundation.layout.fillMaxWidth;
import androidx.compose.foundation.layout.height;
import androidx.compose.foundation.layout.padding;
import androidx.compose.foundation.lazy.GridCells;
import androidx.compose.foundation.lazy.LazyVerticalGrid;
import androidx.compose.foundation.shape.RoundedCornerShape;
import androidx.compose.foundation.text.BasicText;
import androidx.compose.runtime.Composable;
import androidx.compose.runtime.getValue;
import androidx.compose.runtime.mutableStateOf;
import androidx.compose.runtime.remember;
import androidx.compose.runtime.setValue;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.draw.clip;
import androidx.compose.ui.graphics.Color;
import androidx.compose.ui.graphics.painter.Painter;
import androidx.compose.ui.layout.ContentScale;
import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.platform.LocalContext;
import androidx.compose.ui.tooling.preview.Preview;
import androidx.compose.ui.unit.IntSize;
import androidx.compose.ui.unit.dp;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.Request;
import com.squareup.picasso3.RequestHandler;
import com.squareup.picasso3.compose.rememberPainter;
import com.squareup.picasso3.layoutlib.LayoutlibExecutorService;
import java.util.List;
import java.util.Map;

public class SampleComposeActivity extends PicassoSampleActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ComposeView composeView = new ComposeView(this);

    List<String> urls = Data.URLS;
    urls.addAll(Data.URLS);
    urls.addAll(Data.URLS);

    composeView.setContent(() -> Content(urls));

    setContentView(composeView);
  }
}

@Composable
public static void Content(List<String> urls) {
  var contentScale by remember(() -> mutableStateOf(ContentScale.Inside));
  var alignment by remember(() -> mutableStateOf(Alignment.Center));

  Column {
    ImageGrid(
      Modifier.weight(1F),
      urls,
      contentScale,
      alignment
    );

    Options(
      Modifier
        .background(Color.DarkGray)
        .padding(vertical = 4.dp),
      contentScale -> { contentScale = contentScale; },
      alignment -> { alignment = alignment; }
    );
  }
}

@Composable
public static void ImageGrid(
  Modifier modifier,
  List<String> urls,
  ContentScale contentScale,
  Alignment alignment
) {
  LazyVerticalGrid(
    GridCells.Adaptive(150.dp),
    modifier
  ) {
    items(urls.size(), index -> {
      String url = urls.get(index);
      Image(
        rememberPainter(url, picasso -> {
          Request.Builder builder = new Request.Builder(url);
          builder.placeholder(R.drawable.placeholder);
          builder.error(R.drawable.error);
          return builder.build();
        }),
        null,
        contentScale,
        alignment,
        Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
      );
    });
  }
}

@Composable
public static void Options(
  Modifier modifier,
  Function1<ContentScale, Unit> onContentScaleSelected,
  Function1<Alignment, Unit> onAlignmentSelected
) {
  var contentScaleKey by remember(() -> mutableStateOf("Inside"));
  var alignmentKey by remember(() -> mutableStateOf("Center"));
  Column(modifier) {
    List<Map.Entry<String, ContentScale>> contentScales = new ArrayList<>(CONTENT_SCALES.entrySet());
    List<List<Map.Entry<String, ContentScale>>> contentScalesChunks = ListUtils.partition(contentScales, 4);
    for (List<Map.Entry<String, ContentScale>> entries : contentScalesChunks) {
      Row(
        Modifier
          .padding(2.dp)
          .fillMaxWidth(),
        Arrangement.SpaceEvenly,
        () -> {
          for (Map.Entry<String, ContentScale> entry : entries) {
            String key = entry.getKey();
            ContentScale value = entry.getValue();
            OptionText(
              Modifier.weight(1F),
              key,
              contentScaleKey.equals(key),
              () -> {
                contentScaleKey = key;
                onContentScaleSelected.invoke(value);
              }
            );
          }
        }
      );
    }

    Spacer(Modifier.height(8.dp));

    List<Map.Entry<String, Alignment>> alignments = new ArrayList<>(ALIGNMENTS.entrySet());
    List<List<Map.Entry<String, Alignment>>> alignmentsChunks = ListUtils.partition(alignments, 3);
    for (List<Map.Entry<String, Alignment>> entries : alignmentsChunks) {
      Row(
        Modifier
          .padding(2.dp)
          .fillMaxWidth(),
        Arrangement.SpaceEvenly,
        () -> {
          for (Map.Entry<String, Alignment> entry : entries) {
            String key = entry.getKey();
            Alignment value = entry.getValue();
            OptionText(
              Modifier.weight(1F),
              key,
              alignmentKey.equals(key),
              () -> {
                alignmentKey = key;
                onAlignmentSelected.invoke(value);
              }
            );
          }
        }
      );
    }
  }
}

@Composable
private static void OptionText(Modifier modifier, String key, boolean selected, Function0<Unit> onClick) {
  Box(modifier) {
    BasicText(
      key,
      Modifier
        .align(Alignment.Center)
        .clip(RoundedCornerShape(8.dp))
        .clickable(onClick)
        .background(selected ? Color.Blue : Color.White)
        .padding(8.dp, 4.dp)
    );
  }
}

private static final Map<String, ContentScale> CONTENT_SCALES = Map.ofEntries(
  Map.entry("Crop", ContentScale.Crop),
  Map.entry("Fit", ContentScale.Fit),
  Map.entry("Inside", ContentScale.Inside),
  Map.entry("Fill Width", ContentScale.FillWidth),
  Map.entry("Fill Height", ContentScale.FillHeight),
  Map.entry("Fill Bounds", ContentScale.FillBounds),
  Map.entry("None", ContentScale.None)
);

private static final Map<String, Alignment> ALIGNMENTS = Map.ofEntries(
  Map.entry("TopStart", Alignment.TopStart),
  Map.entry("TopCenter", Alignment.TopCenter),
  Map.entry("TopEnd", Alignment.TopEnd),
  Map.entry("CenterStart", Alignment.CenterStart),
  Map.entry("Center", Alignment.Center),
  Map.entry("CenterEnd", Alignment.CenterEnd),
  Map.entry("BottomStart", Alignment.BottomStart),
  Map.entry("BottomCenter", Alignment.BottomCenter),
  Map.entry("BottomEnd", Alignment.BottomEnd)
);

@Preview
@Composable
private static void ContentPreview() {
  Map<String, IntSize> images = Map.ofEntries(
    Map.entry(Color.Blue.toArgb(), new IntSize(200, 100)),
    Map.entry(Color.Red.toArgb(), new IntSize(100, 200)),
    Map.entry(Color.Green.toArgb(), new IntSize(100, 100)),
    Map.entry(Color.Yellow.toArgb(), new IntSize(300, 100)),
    Map.entry(Color.Black.toArgb(), new IntSize(100, 300)),
    Map.entry(Color.LightGray.toArgb(), new IntSize(400, 100)),
    Map.entry(Color.Cyan.toArgb(), new IntSize(100, 100)),
    Map.entry(Color.White.toArgb(), new IntSize(100, 400))
  );

  Context context = LocalContext.current;
  Content(
    new ArrayList<>(images.keySet()),
    () -> {
      Picasso.Builder builder = new Picasso.Builder(context);
      builder.callFactory(() -> {
        throw new AssertionError();
      });
      builder.executor(new LayoutlibExecutorService());
      builder.addRequestHandler(
        new RequestHandler() {
          @Override
          public boolean canHandleRequest(Request data) {
            String url = data.uri().toString();
            return images.containsKey(url);
          }

          @Override
          public void load(Picasso picasso, Request request, Callback callback) {
            String url = request.uri().toString();
            Pair<Integer, IntSize> pair = images.get(url);
            int color = pair.getFirst();
            IntSize size = pair.getSecond();
            Bitmap bitmap = Bitmap.createBitmap(size.getWidth(), size.getHeight(), Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(color);
            callback.onSuccess(new Result.Bitmap(bitmap, LoadedFrom.MEMORY));
          }
        }
      );
      return builder.build();
    }
  );
}