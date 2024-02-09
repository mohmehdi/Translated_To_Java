
package com.squareup.picasso3;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.Config;
import com.google.common.truth.Truth;
import com.squareup.picasso3.BitmapUtils;
import com.squareup.picasso3.Request;
import com.squareup.picasso3.TestUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class BitmapUtilsTest {

  @Test
  public void bitmapConfig() {
    for (Config config : Bitmap.Config.values()) {
      Request data = new Request.Builder(TestUtils.URI_1).config(config).build();
      Request copy = data.newBuilder().build();

      assertThat(BitmapUtils.createBitmapOptions(data).inPreferredConfig).isSameInstanceAs(config);
      assertThat(BitmapUtils.createBitmapOptions(copy).inPreferredConfig).isSameInstanceAs(config);
    }
  }

  @Test
  public void requiresComputeInSampleSize() {
    assertThat(BitmapUtils.requiresInSampleSize(null)).isFalse();

    BitmapFactory.Options defaultOptions = new BitmapFactory.Options();
    assertThat(BitmapUtils.requiresInSampleSize(defaultOptions)).isFalse();

    BitmapFactory.Options justBounds = new BitmapFactory.Options();
    justBounds.inJustDecodeBounds = true;
    assertThat(BitmapUtils.requiresInSampleSize(justBounds)).isTrue();
  }

  @Test
  public void calculateInSampleSizeNoResize() {
    BitmapFactory.Options options = new BitmapFactory.Options();
    Request data = new Request.Builder(TestUtils.URI_1).build();
    BitmapUtils.calculateInSampleSize(100, 100, 150, 150, options, data);
    assertThat(options.inSampleSize).isEqualTo(1);
  }

  @Test
  public void calculateInSampleSizeResize() {
    BitmapFactory.Options options = new BitmapFactory.Options();
    Request data = new Request.Builder(TestUtils.URI_1).build();
    BitmapUtils.calculateInSampleSize(100, 100, 200, 200, options, data);
    assertThat(options.inSampleSize).isEqualTo(2);
  }

  @Test
  public void calculateInSampleSizeResizeCenterInside() {
    BitmapFactory.Options options = new BitmapFactory.Options();
    Request data = new Request.Builder(TestUtils.URI_1).centerInside().resize(100, 100).build();
    BitmapUtils.calculateInSampleSize(data.targetWidth, data.targetHeight, 400, 200, options, data);
    assertThat(options.inSampleSize).isEqualTo(4);
  }

  @Test
  public void calculateInSampleSizeKeepAspectRatioWithWidth() {
    BitmapFactory.Options options = new BitmapFactory.Options();
    Request data = new Request.Builder(TestUtils.URI_1).resize(400, 0).build();
    BitmapUtils.calculateInSampleSize(data.targetWidth, data.targetHeight, 800, 200, options, data);
    assertThat(options.inSampleSize).isEqualTo(2);
  }

  @Test
  public void calculateInSampleSizeKeepAspectRatioWithHeight() {
    BitmapFactory.Options options = new BitmapFactory.Options();
    Request data = new Request.Builder(TestUtils.URI_1).resize(0, 100).build();
    BitmapUtils.calculateInSampleSize(data.targetWidth, data.targetHeight, 800, 200, options, data);
    assertThat(options.inSampleSize).isEqualTo(2);
  }

  @Test
  public void nullBitmapOptionsIfNoResizingOrPurgeable() {
    Request noResize = new Request.Builder(TestUtils.URI_1).build();
    BitmapFactory.Options noResizeOptions = BitmapUtils.createBitmapOptions(noResize);
    assertThat(noResizeOptions).isNull();
  }

  @Test
  public void inJustDecodeBoundsIfResizing() {
    Request requiresResize = new Request.Builder(TestUtils.URI_1).resize(20, 15).build();
    BitmapFactory.Options resizeOptions = BitmapUtils.createBitmapOptions(requiresResize);
    assertThat(resizeOptions).isNotNull();
    assertThat(resizeOptions.inJustDecodeBounds).isTrue();
    assertThat(resizeOptions.inPurgeable).isFalse();
    assertThat(resizeOptions.inInputShareable).isFalse();
  }

  @Test
  public void inPurgeableIfInPurgeable() {
    Request request = new Request.Builder(TestUtils.URI_1).purgeable().build();
    BitmapFactory.Options options = BitmapUtils.createBitmapOptions(request);
    assertThat(options).isNotNull();
    assertThat(options.inPurgeable).isTrue();
    assertThat(options.inInputShareable).isTrue();
    assertThat(options.inJustDecodeBounds).isFalse();
  }

  @Test
  public void createWithConfigAndNotInJustDecodeBoundsOrInPurgeable() {
    Request config = new Request.Builder(TestUtils.URI_1).config(Config.RGB_565).build();
    BitmapFactory.Options configOptions = BitmapUtils.createBitmapOptions(config);
    assertThat(configOptions).isNotNull();
    assertThat(configOptions.inJustDecodeBounds).isFalse();
    assertThat(configOptions.inPurgeable).isFalse();
    assertThat(configOptions.inInputShareable).isFalse();
  }
}