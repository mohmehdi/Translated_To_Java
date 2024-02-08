package com.example.picasso;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

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
import androidx.compose.runtime.remember;
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
import com.squareup.picasso3.Request;
import com.squareup.picasso3.RequestHandler;
import com.squareup.picasso3.compose.rememberPainter;
import kotlinx.coroutines.Dispatchers;

public class SampleComposeActivity extends PicassoSampleActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ComposeView composeView = new ComposeView(this);
        composeView.setId(View.generateViewId());

        String[] urls = Data.URLS.clone();
        System.arraycopy(Data.URLS, 0, urls, Data.URLS.length, Data.URLS.length);
        System.arraycopy(Data.URLS, 0, urls, Data.URLS.length * 2, Data.URLS.length);

        composeView.setContent(() -> Content(urls));
        setContentView(composeView);
    }

    @Composable
    public static void Content(List<String> urls) {
        var contentScale = remember { mutableStateOf(ContentScale.Inside) };
        var alignment = remember { mutableStateOf(Alignment.Center) };

        Column {
            ImageGrid(
                modifier = Modifier.weight(1F),
                urls = urls,
                contentScale = contentScale.getValue(),
                alignment = alignment.getValue(),
                picasso = PicassoInitializer.INSTANCE.get()
            );

            Options(
                modifier = Modifier
                        .background(Color.DarkGray)
                        .padding(vertical = 4.dp),
                onContentScaleSelected = (it) -> contentScale.setValue(it),
                onAlignmentSelected = (it) -> alignment.setValue(it)
            );
        }
    }

    @Composable
    public static void ImageGrid(Modifier modifier, List<String> urls, ContentScale contentScale, Alignment alignment, Picasso picasso) {
        LazyVerticalGrid(
                cells = GridCells.Adaptive(150.dp),
                modifier = modifier
        ) {
            items(urls.length, (it) -> {
                String url = urls[it];
                Image(
                        painter = picasso.rememberPainter(key = url, (it) -> it.load(url).placeholder(R.drawable.placeholder).error(R.drawable.error)),
                        contentDescription = null,
                        contentScale = contentScale,
                        alignment = alignment,
                        modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                );
            });
        }
    }

    @Composable
    public static void Options(Modifier modifier, OnContentScaleSelected onContentScaleSelected, OnAlignmentSelected onAlignmentSelected) {
        var contentScaleKey = remember { mutableStateOf("Inside") };
        var alignmentKey = remember { mutableStateOf("Center") };

        Column(modifier = modifier) {
            for (Map.Entry<String, ContentScale> entry : CONTENT_SCALES.entrySet()) {
                OptionText(
                        modifier = Modifier.weight(1F),
                        key = entry.getKey(),
                        selected = contentScaleKey.getValue().equals(entry.getKey()),
                        onClick = () -> {
                            contentScaleKey.setValue(entry.getKey());
                            onContentScaleSelected.invoke(entry.getValue());
                        }
                );
            }

            Spacer(modifier = Modifier.height(8.dp));

            for (Map.Entry<String, Alignment> entry : ALIGNMENTS.entrySet()) {
                OptionText(
                        modifier = Modifier.weight(1F),
                        key = entry.getKey(),
                        selected = alignmentKey.getValue().equals(entry.getKey()),
                        onClick = () -> {
                            alignmentKey.setValue(entry.getKey());
                            onAlignmentSelected.invoke(entry.getValue());
                        }
                );
            }
        }
    }

    @Composable
    private static void OptionText(Modifier modifier, String key, boolean selected, Runnable onClick) {
        Box(modifier = modifier) {
            BasicText(
                    text = key,
                    modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onClick)
                            .background(selected ? Color.Blue : Color.White)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
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
        Map<String, Pair<Integer, IntSize>> images = Map.ofEntries(
                Map.entry("https://cash.app/-16777216.png", new Pair<>(Color.Blue.toArgb(), new IntSize(200, 100))),
                Map.entry("https://cash.app/-65536.png", new Pair<>(Color.Red.toArgb(), new IntSize(100, 200))),
                Map.entry("https://cash.app/-16711936.png", new Pair<>(Color.Green.toArgb(), new IntSize(100, 100))),
                Map.entry("https://cash.app/-256.png", new Pair<>(Color.Yellow.toArgb(), new IntSize(300, 100))),
                Map.entry("https://cash.app/-16777216.png", new Pair<>(Color.Black.toArgb(), new IntSize(100, 300))),
                Map.entry("https://cash.app/-4144960.png", new Pair<>(Color.LightGray.toArgb(), new IntSize(400, 100))),
                Map.entry("https://cash.app/-16711681.png", new Pair<>(Color.Cyan.toArgb(), new IntSize(100, 100))),
                Map.entry("https://cash.app/-1.png", new Pair<>(Color.White.toArgb(), new IntSize(100, 400)))
        );

        Context context = LocalContext.current;
        Content(
                urls = images.keySet().toArray(new String[0]),
                picasso = remember(() -> new Picasso.Builder(context)
                        .callFactory(() -> {
                            throw new AssertionError();
                        }) // Removes network
                        .dispatchers(
                                Dispatchers.getMain(),
                                Dispatchers.getDefault()
                        )
                        .addRequestHandler(
                                new RequestHandler() {
                                    @Override
                                    public boolean canHandleRequest(Request data) {
                                        return data.uri().toString().equals(images.get(data.uri().toString()).first().toString());
                                    }

                                    @Override
                                    public void load(Picasso picasso, Request request, Callback callback) {
                                        Pair<Integer, IntSize> pair = images.get(request.uri().toString());
                                        int color = pair.first();
                                        IntSize size = pair.second();
                                        Bitmap bitmap = Bitmap.createBitmap(size.getWidth(), size.getHeight(), Config.ARGB_8888);
                                        new Canvas(bitmap).drawColor(color);

                                        callback.onSuccess(Result.Bitmap(bitmap, MEMORY));
                                    }
                                }
                        )
                        .build()
                )
        );
    }
}