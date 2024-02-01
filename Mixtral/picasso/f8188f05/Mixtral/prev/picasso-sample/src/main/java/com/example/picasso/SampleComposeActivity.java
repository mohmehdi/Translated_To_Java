package com.example.picasso;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
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
import androidx.compose.ui.graphics.toArgb;
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
import com.squareup.picasso3.layoutlib.LayoutlibExecutorService;

public class SampleComposeActivity extends AppCompatActivity {




@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ComposeView composeView = new ComposeView(this);

    List<String> urls = new ArrayList<>(Data.URLS);
    urls.addAll(new ArrayList<>(Data.URLS));
    urls.addAll(new ArrayList<>(Data.URLS));
    Collections.shuffle(urls);

    composeView.setContent {
        Content(urls, PicassoInitializer.get());
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
    columns = Adaptive(dimensionResource(150)),
    modifier = modifier
  ) {
    items(urls.size()) {
      String url = urls.get(it);
      Painter painter = picasso.load(url)
        .placeholder(R.drawable.placeholder)
        .error(R.drawable.error)
        .get();
      Image(
        painter = painter,
        contentDescription = null,
        contentScale = contentScale,
        alignment = alignment,
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
      )
    }
  }
}





public void Options(
            Modifier modifier,
            OnContentScaleSelected onContentScaleSelected,
            OnAlignmentSelected onAlignmentSelected
    ) {
        String contentScaleKey = "Inside";
        String alignmentKey = "Center";

        List<Map.Entry<String, ContentScale>> CONTENT_SCALES = ...; // initialize the map entries

        List<Map.Entry<String, Alignment>> ALIGNMENTS = ...; // initialize the map entries

        for (List<Map.Entry<String, ContentScale>> entries : CONTENT_SCALES.stream().collect(Collectors.groupingBy(x -> entries.indexOf(x) / 4)).values()) {
            Row row = new Row(
                    modifier.padding(2.dp).fillMaxWidth(),
                    Arrangement.SpaceEvenly
            );

            for (Map.Entry<String, ContentScale> entry : entries) {
                OptionText optionText = new OptionText(
                        modifier.weight(1F),
                        entry.getKey(),
                        contentScaleKey.equals(entry.getKey()),
                        () -> {
                            contentScaleKey = entry.getKey();
                            onContentScaleSelected.invoke(entry.getValue());
                        }
                );
                row.add(optionText);
            }
        }
    }








    @Composable
    private void OptionText(Modifier modifier, String key, boolean selected, Runnable onClick) {
    Box(modifier = modifier) {
        Text(
        text = key,
        modifier = Modifier
            .align(Alignment.Center)
            .clip(RoundedCornerShape.create(8.dp))
            .clickable(onClick = onClick)
            .background(if (selected) Color.Blue else Color.White)
            .padding(horizontal = 8.dp, vertical = 4.dp)
        );
    }
    }

    private final Map<String, ContentScale> CONTENT_SCALES = new HashMap<>();
    static {
    CONTENT_SCALES.put("Crop", ContentScale.CROP);
    CONTENT_SCALES.put("Fit", ContentScale.FIT);
    CONTENT_SCALES.put("Inside", ContentScale.INCLUDE);
    CONTENT_SCALES.put("Fill Width", ContentScale.FILL_WIDTH);
    CONTENT_SCALES.put("Fill Height", ContentScale.FILL_HEIGHT);
    CONTENT_SCALES.put("Fill Bounds", ContentScale.FILL);
    CONTENT_SCALES.put("None", ContentScale.NONE);
    }

    private final Map<String, Alignment> ALIGNMENTS = new HashMap<>();
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

    @Composable
    private void ContentPreview() {
    Map<Integer, Size> images = new HashMap<>();
    images.put(Color.BLUE.toArgb(), new Size(200, 100));
    images.put(Color.RED.toArgb(), new Size(100, 200));
    images.put(Color.GREEN.toArgb(), new Size(100, 100));
    images.put(Color.YELLOW.toArgb(), new Size(300, 100));
    images.put(Color.BLACK.toArgb(), new Size(100, 300));
    images.put(Color.LTGRAY.toArgb(), new Size(400, 100));
    images.put(Color.CYAN.toArgb(), new Size(100, 100));
    images.put(Color.WHITE.toArgb(), new Size(100, 400));

    LocalContext context = LocalContext.current;
    Content(
        urls = images.keySet().stream().toList(),
        picasso = remember {
        Picasso.Builder(context)
            .callFactory(new PicassoCallFactory() {
            @Override
            public Executor newExecutor(Context context) {
                return new LayoutlibExecutorService();
            }
            })
            .addRequestHandler(new RequestHandler() {
            @Override
            public boolean canHandleRequest(Request data) {
                return images.containsKey(data.uri.toString());
            }

            @Override
            public void load(Picasso picasso, Request request, Callback callback) {
                Map.Entry<Integer, Size> entry = images.entrySet().stream()
                .filter(e -> e.getKey().equals(request.uri.toString()))
                .findFirst()
                .orElseThrow();

                int color = entry.getKey();
                Size size = entry.getValue();
                Bitmap bitmap = Bitmap.createBitmap(size.getWidth(), size.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(color);

                callback.onSuccess(new Result.Bitmap(bitmap, Result.Bitmap.ResultType.MEMORY));
            }
            })
            .build()
        }
    );
    }
}