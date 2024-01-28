package com.squareup.picasso3;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.Config;
import static org.junit.Assert.assertThat;
import static org.robolectric.Shadows.shadowOf;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBitmapFactory;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {
  ShadowBitmapFactory.class
})
class BitmapUtilsTest {

  @Test
  public void bitmapConfig() {
    for (Config config: Config.values()) {
      Request data = Request.builder(URI_1).config(config).build();
      Request copy = Request.builder(data).build();

      assertThat(createBitmapOptions(data)).isNotNull();
      assertThat(shadowOf(BitmapFactory.decodeResource(null, R.drawable.test))
        .getBitmap().getConfig()).isSameInstanceAs(config);
      assertThat(createBitmapOptions(copy)).isNotNull();
      assertThat(shadowOf(BitmapFactory.decodeResource(null, R.drawable.test))
        .getBitmap().getConfig()).isSameInstanceAs(config);
    }
  }

  @Test
  public void requiresComputeInSampleSize() {
    assertThat(requiresInSampleSize(null)).isFalse();

    BitmapFactory.Options defaultOptions = new BitmapFactory.Options();
    assertThat(requiresInSampleSize(defaultOptions)).isFalse();

    BitmapFactory.Options justBounds = new BitmapFactory.Options();
    justBounds.inJustDecodeBounds = true;
    assertThat(requiresInSampleSize(justBounds)).isTrue();
  }

  @Test
  public void calculateInSampleSizeNoResize() {
    BitmapFactory.Options options = new BitmapFactory.Options();
    Request data = Request.builder(URI_1).build();
    calculateInSampleSize(100, 100, 150, 150, options, data);
    assertThat(options.inSampleSize).isEqualTo(1);
  }

  @Test
  public void calculateInSampleSizeResize() {
    BitmapFactory.Options options = new BitmapFactory.Options();
    Request data = Request.builder(URI_1).build();
    calculateInSampleSize(100, 100, 200, 200, options, data);
    assertThat(options.inSampleSize).isEqualTo(2);
  }

  @Test
  public void calculateInSampleSizeResizeCenterInside() {
    BitmapFactory.Options options = new BitmapFactory.Options();
    Request data = Request.builder(URI_1).centerInside().resize(100, 100).build();
    calculateInSampleSize(data.targetWidth, data.targetHeight, 400, 200, options, data);
    assertThat(options.inSampleSize).isEqualTo(4);
  }

  @Test
  public void calculateInSampleSizeKeepAspectRatioWithWidth() {
    BitmapFactory.Options options = new BitmapFactory.Options();
    Request data = Request.builder(URI_1).resize(400, 0).build();
    calculateInSampleSize(data.targetWidth, data.targetHeight, 800, 200, options, data);
    assertThat(options.inSampleSize).isEqualTo(2);
  }

  @Test
  public void calculateInSampleSizeKeepAspectRatioWithHeight() {
    BitmapFactory.Options options = new BitmapFactory.Options();
    Request data = Request.builder(URI_1).resize(0, 100).build();
    calculateInSampleSize(data.targetWidth, data.targetHeight, 800, 200, options, data);
    assertThat(options.inSampleSize).isEqualTo(2);
  }

  @Test
  public void nullBitmapOptionsIfNoResizingOrPurgeable() {
    Request noResize = Request.builder(URI_1).build();
    BitmapFactory.Options noResizeOptions = createBitmapOptions(noResize);
    assertThat(noResizeOptions).isNull();
  }

  @Test
  public void inJustDecodeBoundsIfResizing() {
    Request requiresResize = Request.builder(URI_1).resize(20, 15).build();
    BitmapFactory.Options resizeOptions = createBitmapOptions(requiresResize);
    assertThat(resizeOptions).isNotNull();
    assertThat(resizeOptions.inJustDecodeBounds).isTrue();
    assertThat(resizeOptions.inPurgeable).isFalse();
    assertThat(resizeOptions.inInputShareable).isFalse();
  }

  @Test
  public void inPurgeableIfInPurgeable() {
    Request request = Request.builder(URI_1).purgeable().build();
    BitmapFactory.Options options = createBitmapOptions(request);
    assertThat(options).isNotNull();
    assertThat(options.inPurgeable).isTrue();
    assertThat(options.inInputShareable).isTrue();
    assertThat(options.inJustDecodeBounds).isFalse();
  }

  @Test
  public void createWithConfigAndNotInJustDecodeBoundsOrInPurgeable() {
    Request config = Request.builder(URI_1).config(Config.RGB_565).build();
    BitmapFactory.Options configOptions = createBitmapOptions(config);
    assertThat(configOptions).isNotNull();
    assertThat(configOptions.inJustDecodeBounds).isFalse();
    assertThat(configOptions.inPurgeable).isFalse();
    assertThat(configOptions.inInputShareable).isFalse();
  }
}