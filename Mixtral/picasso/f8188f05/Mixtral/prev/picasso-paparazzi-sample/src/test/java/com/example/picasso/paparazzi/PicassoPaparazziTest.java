package com.example.picasso.paparazzi;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import app.cash.paparazzi.Paparazzi;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Picasso.Builder;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.Request;
import com.squareup.picasso3.RequestHandler;
import com.squareup.picasso3.layoutlib.LayoutlibExecutorService;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class PicassoPaparazziTest {
  @Rule public ActivityScenarioRule<PaparazziActivity> paparazzi =
      new ActivityScenarioRule<>(PaparazziActivity.class);

  private Picasso picasso;



  @Test
  public void loadsUrlIntoImageView() {
    ImageView imageView = new ImageView(paparazzi.getScenario().getContext());
    imageView.setScaleType(ScaleType.CENTER);
    Request request =
        new Request("fake:", null, null, 0, 0, ImageView.ScaleType.CENTER_INSIDE, null, null);
    picasso.load(request).resize(200, 200).centerInside().onlyScaleDown().into(imageView);
    paparazzi.capture().close();
  }

  private class FakeRequestHandler extends RequestHandler {
    @Override
    public boolean canHandleRequest(Request data) {
      return "fake".equals(data.uri.getScheme());
    }

    @Override
    public Result load(Picasso picasso, Request request, Callback callback) throws IOException {
      String imagePath = request.uri.getLastPathSegment();
      Bitmap bitmap = loadBitmap(imagePath);
      return new Result.Bitmap(bitmap, LoadedFrom.MEMORY);
    }

    private Bitmap loadBitmap(String imagePath) {
      InputStream resourceAsStream =
          getClass().getClassLoader().getResourceAsStream(imagePath);
      return BitmapFactory.decodeStream(resourceAsStream);
    }
  }
}