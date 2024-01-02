package com.example.picasso.paparazzi;

import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import app.cash.paparazzi.Paparazzi;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.Request;
import com.squareup.picasso3.RequestHandler;
import com.squareup.picasso3.layoutlib.LayoutlibExecutorService;
import org.junit.Rule;
import org.junit.Test;
import kotlinx.coroutines.Dispatchers;

public class PicassoPaparazziTest {
  @Rule
  public Paparazzi paparazzi = new Paparazzi();

  @Test
  public void loadsUrlIntoImageView() {
    Picasso picasso = new Picasso.Builder(paparazzi.getContext())
      .callFactory(() -> { throw new AssertionError(); })
      .executor(new LayoutlibExecutorService())
      .dispatcher(Dispatchers.Main)
      .addRequestHandler(new FakeRequestHandler())
      .build();

    paparazzi.snapshot(
      new ImageView(paparazzi.getContext()) {{
        setScaleType(ScaleType.CENTER);
        picasso.load("fake:")
          .resize(200, 200)
          .centerInside()
          .onlyScaleDown()
          .into(this);
      }}
    );
  }

  public static class FakeRequestHandler extends RequestHandler {
    @Override
    public boolean canHandleRequest(Request data) {
      return "fake".equals(data.uri.getScheme());
    }

    @Override
    public void load(Picasso picasso, Request request, Callback callback) {
      String imagePath = request.uri.getLastPathSegment();
      callback.onSuccess(new Result.Bitmap(loadBitmap(imagePath), LoadedFrom.MEMORY));
    }

    private android.graphics.Bitmap loadBitmap(String imagePath) {
      java.io.InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(imagePath);
      return BitmapFactory.decodeStream(resourceAsStream);
    }
  }
}