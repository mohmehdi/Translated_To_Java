package com.example.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import androidx.activity.ComponentActivity;
import androidx.activity.compose.SetContent;
import androidx.compose.animation.core.animateFloatAsState;
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
import androidx.compose.foundation.lazy.grid.GridCells;
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid;
import androidx.compose.foundation.shape.RoundedCornerShape;
import androidx.compose.material.ContentScale;
import androidx.compose.material.MaterialTheme;
import androidx.compose.runtime.MutableState;
import androidx.compose.runtime.mutableStateOf;
import androidx.compose.runtime.remember;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.draw.clip;
import androidx.compose.ui.graphics.ColorFilter;
import androidx.compose.ui.graphics.DefaultAlpha;
import androidx.compose.ui.graphics.ImageBitmap;
import androidx.compose.ui.graphics.asImageBitmap;
import androidx.compose.ui.layout.ContentScale;
import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.platform.LocalContext;
import androidx.compose.ui.res.painterResource;
import androidx.compose.ui.tooling.preview.Preview;
import androidx.compose.ui.unit.dp;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.Request;
import com.squareup.picasso3.RequestHandler;
import com.squareup.picasso3.Transformation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SampleComposeActivity extends ComponentActivity {

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
        Content(urls);
    };

    setContentView(composeView);
}



  public static void Content(List<String> urls, Picasso picasso) {
    MutableState<ContentScale> contentScale = remember { mutableStateOf(ContentScale.Inside) };
    MutableState<Alignment> alignment = remember { mutableStateOf(Alignment.Center) };

    Column column = new Column();

    ImageGrid imageGrid = new ImageGrid(
      modifier = new Modifier().weight(1f),
      urls = urls,
      contentScale = contentScale.getValue(),
      alignment = alignment.getValue(),
      picasso = picasso
    );

    Options options = new Options(
      modifier = new Modifier()
        .background(Color.DarkGray)
        .padding(vertical = new Dp(4)),
      onContentScaleSelected = (ContentScale contentScaleValue) -> contentScale.setValue(contentScaleValue),
      onAlignmentSelected = (Alignment alignmentValue) -> alignment.setValue(alignmentValue)
    );

    column.add(imageGrid);
    column.add(options);
  }




@Composable
public void ImageGrid(
        Modifier modifier,
        List<String> urls,
        ContentScale contentScale,
        Alignment alignment,
        Picasso picasso
) {
    LazyVerticalGrid(
            columns = Adaptive(150),
            modifier = modifier
    ) {
        items(urls.size()) {
            String url = urls.get(it);
            Image(
                    painter = picasso.rememberPainter(url) {
                        it.load(url).placeholder(R.drawable.placeholder).error(R.drawable.error);
                    },
                    contentDescription = null,
                    contentScale = contentScale,
                    alignment = alignment,
                    modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
            );
        }
    };
}






  public static void Options(
      Modifier modifier,
      OnContentScaleSelected onContentScaleSelected,
      OnAlignmentSelected onAlignmentSelected) {
    MutableState<String> contentScaleKey = mutableStateOf("Inside");
    MutableState<String> alignmentKey = mutableStateOf("Center");

    Column(modifier) {
      for (entries : CONTENT_SCALES.entrySet().stream().collect(Collectors.groupingBy(entry -> entry.getKey().hashCode() / 4 * 4)).entrySet()) {
        Row(
            Modifier
                .padding(2.dp)
                .fillMaxWidth(),
            Arrangement.SpaceEvenly
        ) {
          for (entry : entries.getValue()) {
            OptionText(
                Modifier.weight(1F),
                entry.getKey(),
                contentScaleKey.getValue().equals(entry.getKey()),
                () -> {
                  contentScaleKey.setValue(entry.getKey());
                  onContentScaleSelected.onContentScaleSelected(CONTENT_SCALES.get(entry.getKey()));
                }
            );
          }
        }
      }

      Spacer(Modifier.height(8.dp));

      for (entries : ALIGNMENTS.entrySet().stream().collect(Collectors.groupingBy(entry -> entry.getKey().hashCode() / 3 * 3)).entrySet()) {
        Row(
            Modifier
                .padding(2.dp)
                .fillMaxWidth(),
            Arrangement.SpaceEvenly
        ) {
          for (entry : entries.getValue()) {
            OptionText(
                Modifier.weight(1F),
                entry.getKey(),
                alignmentKey.getValue().equals(entry.getKey()),
                () -> {
                  alignmentKey.setValue(entry.getKey());
                  onAlignmentSelected.onAlignmentSelected(ALIGNMENTS.get(entry.getKey()));
                }
            );
          }
        }
      }
    }
  }

  public static void OptionText(
      Modifier modifier,
      String key,
      boolean selected,
      Runnable onClick) {
    Box(modifier) {
      ClickableText(
          text = key,
          modifier = Modifier
              .align(Alignment.Center)
              .clip(RoundedCornerShape(8.dp))
              .clickable(onClick = onClick)
              .background(if (selected) Color.Blue else Color.White)
              .padding(horizontal = 8.dp, vertical = 4.dp)
      );
    }
  }

  private static final Map<String, ContentScale> CONTENT_SCALES = new HashMap<>();
  static {
    CONTENT_SCALES.put("Crop", ContentScale.Crop);
    CONTENT_SCALES.put("Fit", ContentScale.Fit);
    CONTENT_SCALES.put("Inside", ContentScale.Inside);
    CONTENT_SCALES.put("Fill Width", ContentScale.FillWidth);
    CONTENT_SCALES.put("Fill Height", ContentScale.FillHeight);
    CONTENT_SCALES.put("Fill Bounds", ContentScale.FillBounds);
    CONTENT_SCALES.put("None", ContentScale.None);
  }

  private static final Map<String, Alignment> ALIGNMENTS = new HashMap<>();
  static {
    ALIGNMENTS.put("TopStart", Alignment.TopStart);
    ALIGNMENTS.put("TopCenter", Alignment.TopCenter);
    ALIGNMENTS.put("TopEnd", Alignment.TopEnd);
    ALIGNMENTS.put("CenterStart", Alignment.CenterStart);
    ALIGNMENTS.put("Center", Alignment.Center);
    ALIGNMENTS.put("CenterEnd", Alignment.CenterEnd);
    ALIGNMENTS.put("BottomStart", Alignment.BottomStart);
    ALIGNMENTS.put("BottomCenter", Alignment.BottomCenter);
    ALIGNMENTS.put("BottomEnd", Alignment.BottomEnd);
  }

  @Preview
  public static void ContentPreview() {
    Map<String, IntSize> images = new HashMap<>();
    images.put("https://cash.app/-3368620175227699201", new IntSize(200, 100));
    images.put("https://cash.app/-8803772021677772805", new IntSize(100, 200));
    images.put("https://cash.app/-5625030866535659954", new IntSize(100, 100));
    images.put("https://cash.app/-7082596801589955554", new IntSize(300, 100));
    images.put("https://cash.app/-6585620606508159650", new IntSize(100, 300));
    images.put("https://cash.app/-3705027240855705762", new IntSize(400, 100));
    images.put("https://cash.app/-7252052658335565203", new IntSize(100, 100));
    images.put("https://cash.app/-5963569067285756450", new IntSize(100, 400));

    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    Content(
        images.keySet().toList(),
        new Picasso.Builder(context)
            .build(),
        new PicassoRequestHandler() {
          @Override
          boolean canHandleRequest(Request request) {
            return images.containsKey(request.uri.toString());
          }

          @Override
          void load(Picasso picasso, Request request, Callback callback) {
            IntSize size = images.get(request.uri.toString());
            Bitmap bitmap = Bitmap.createBitmap(size.getWidth(), size.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.parseColor(request.uri.toString().substring(8, 16)));
            callback.onSuccess(new Result.Bitmap(bitmap, Result.Bitmap.Type.MEMORY));
          }
        }
    );
  }

}
