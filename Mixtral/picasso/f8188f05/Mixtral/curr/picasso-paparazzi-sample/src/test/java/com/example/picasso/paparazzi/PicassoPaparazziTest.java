package com.example.picasso.paparazzi;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import app.cash.paparazzi.Paparazzi;
import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.Picasso.Builder;
import com.squareup.picasso3.Request;
import com.squareup.picasso3.RequestHandler;
import com.squareup.picasso3.RequestHandler.Result;
import com.squareup.picasso3.Callback;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;

import kotlinx.coroutines.Dispatchers;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class PicassoPaparazziTest {
  @Rule public ActivityScenarioRule<MainActivity> activityRule =
      new ActivityScenarioRule<>(MainActivity.class);

  private Picasso picasso;



  @Test
  public void loadsUrlIntoImageView() {
    ImageView imageView = new ImageView(InstrumentationRegistry.getInstrumentation().getTargetContext());
    imageView.setScaleType(ScaleType.CENTER);

    Request request =
        new Request.Builder()
            .resize(200, 200)
            .centerInside()
            .onlyScaleDown()
            .build()
            .forRequest("fake:example.jpg");

    picasso.load(request).into(
        imageView,
        new Callback() {
          @Override
          public void onSuccess() {
            Paparazzi paparazzi = new Paparazzi(InstrumentationRegistry.getInstrumentation().getTargetContext());
            paparazzi.snapshot(imageView);
          }

          @Override
          public void onError(Exception e) {
            e.printStackTrace();
          }
        });
  }

  private static class FakeRequestHandler extends RequestHandler {
    @Override
    public boolean canHandleRequest(Request data) {
      return "fake".equals(data.uri.getScheme());
    }

    @Override
    public Result load(Picasso picasso, Request request, Callback callback) {
      String imagePath = request.uri.getLastPathSegment();
      Bitmap bitmap = loadBitmap(imagePath);
      if (bitmap == null) {
        return null;
      }
      return new Result.Bitmap(bitmap, Picasso.LoadedFrom.MEMORY);
    }

    private Bitmap loadBitmap(String imagePath) {
      try {
        InputStream resourceAsStream =
            getClass()
                .getClassLoader()
                .getResourceAsStream(
                    imagePath.startsWith("/")
                        ? imagePath.substring(1)
                        : imagePath);
        if (resourceAsStream == null) {
          return null;
        }
        return BitmapFactory.decodeStream(resourceAsStream);
      } catch (IOException e) {
        e.printStackTrace();
        return null;
      }
    }
  }
}