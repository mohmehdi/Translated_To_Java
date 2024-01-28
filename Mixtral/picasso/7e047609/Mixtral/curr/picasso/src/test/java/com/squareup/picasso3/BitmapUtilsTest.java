package com.squareup.picasso3;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import static org.junit.Assert.assertThat;
import static org.robolectric.Shadows.shadowOf;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowBitmapFactory;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {
  ShadowBitmapFactory.class
})
class BitmapUtilsTest {

  @Test
  public void bitmapConfig() {
    for (Bitmap.Config config: Bitmap.Config.values()) {
      Request data = new Request.Builder(URI_1).config(config).build();
      Request copy = new Request.Builder(data).build();

      Options createBitmapOptionsData = createBitmapOptions(data);
      assertThat(createBitmapOptionsData.inPreferredConfig).isSameInstanceAs(config);

      Options createBitmapOptionsCopy = createBitmapOptions(copy);
      assertThat(createBitmapOptionsCopy.inPreferredConfig).isSameInstanceAs(config);
    }
  }

  @Test
  public void requiresComputeInSampleSize() {
    assertThat(requiresInSampleSize(null)).isFalse();

    Options defaultOptions = new Options();
    assertThat(requiresInSampleSize(defaultOptions)).isFalse();

    Options justBounds = new Options();
    justBounds.inJustDecodeBounds = true;
    assertThat(requiresInSampleSize(justBounds)).isTrue();
  }

  @Test
  public void calculateInSampleSizeNoResize() {
    Options options = new Options();
    Request data = new Request.Builder(URI_1).build();
    calculateInSampleSize(100, 100, 150, 150, options, data);
    assertThat(options.inSampleSize).isEqualTo(1);
  }

  @Test
  public void calculateInSampleSizeResize() {
    Options options = new Options();
    Request data = new Request.Builder(URI_1).build();
    calculateInSampleSize(100, 100, 200, 200, options, data);
    assertThat(options.inSampleSize).isEqualTo(2);
  }

  @Test
  public void calculateInSampleSizeResizeCenterInside() {
    Options options = new Options();
    Request data = new Request.Builder(URI_1).centerInside().resize(100, 100).build();
    calculateInSampleSize(data.targetWidth, data.targetHeight, 400, 200, options, data);
    assertThat(options.inSampleSize).isEqualTo(4);
  }

  @Test
  public void calculateInSampleSizeKeepAspectRatioWithWidth() {
    Options options = new Options();
    Request data = new Request.Builder(URI_1).resize(400, 0).build();
    calculateInSampleSize(data.targetWidth, data.targetHeight, 800, 200, options, data);
    assertThat(options.inSampleSize).isEqualTo(2);
  }

  @Test
  public void calculateInSampleSizeKeepAspectRatioWithHeight() {
    Options options = new Options();
    Request data = new Request.Builder(URI_1).resize(0, 100).build();
    calculateInSampleSize(data.targetWidth, data.targetHeight, 800, 200, options, data);
    assertThat(options.inSampleSize).isEqualTo(2);
  }

  @Test
  public void nullBitmapOptionsIfNoResizing() {
    Request noResize = new Request.Builder(URI_1).build();
    Options noResizeOptions = createBitmapOptions(noResize);
    assertThat(noResizeOptions).isNull();
  }

  @Test
  public void inJustDecodeBoundsIfResizing() {
    Request requiresResize = new Request.Builder(URI_1).resize(20, 15).build();
    Options resizeOptions = createBitmapOptions(requiresResize);
    assertThat(resizeOptions).isNotNull();
    assertThat(resizeOptions.inJustDecodeBounds).isTrue();
  }

  @Test
  public void createWithConfigAndNotInJustDecodeBounds() {
    Request config = new Request.Builder(URI_1).config(Bitmap.Config.RGB_565).build();
    Options configOptions = createBitmapOptions(config);
    assertThat(configOptions).isNotNull();
    assertThat(configOptions.inJustDecodeBounds).isFalse();
  }
}