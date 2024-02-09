
package com.squareup.picasso3.pollexor;

import android.net.Uri;
import com.google.common.truth.Truth;
import com.squareup.picasso3.Request.Builder;
import com.squareup.pollexor.Thumbor;
import com.squareup.pollexor.ThumborUrlBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PollexorRequestTransformerTest {
  private final PollexorRequestTransformer transformer = new PollexorRequestTransformer(Thumbor.create(HOST));
  private final PollexorRequestTransformer secureTransformer = new PollexorRequestTransformer(Thumbor.create(HOST, KEY));
  private final PollexorRequestTransformer alwaysResizeTransformer = new PollexorRequestTransformer(
    Thumbor.create(HOST), true
  );
  private final PollexorRequestTransformer callbackTransformer = new PollexorRequestTransformer(
    Thumbor.create(HOST), it -> it.filter("custom")
  );

  @Test
  public void resourceIdRequestsAreNotTransformed() {
    Builder input = new Builder(12).build();
    Builder output = transformer.transformRequest(input);
    Truth.assertThat(output).isSameInstanceAs(input);
  }

  @Test
  public void resourceIdRequestsAreNotTransformedWhenAlwaysTransformIsTrue() {
    Builder input = new Builder(12).build();
    Builder output = alwaysResizeTransformer.transformRequest(input);
    Truth.assertThat(output).isSameInstanceAs(input);
  }

  @Test
  public void nonHttpRequestsAreNotTransformed() {
    Builder input = new Builder(IMAGE_URI).build();
    Builder output = transformer.transformRequest(input);
    Truth.assertThat(output).isSameInstanceAs(input);
  }

  @Test
  public void nonResizedRequestsAreNotTransformed() {
    Builder input = new Builder(IMAGE_URI).build();
    Builder output = transformer.transformRequest(input);
    Truth.assertThat(output).isSameInstanceAs(input);
  }

  @Test
  public void nonResizedRequestsAreTransformedWhenAlwaysTransformIsSet() {
    Builder input = new Builder(IMAGE_URI).build();
    Builder output = alwaysResizeTransformer.transformRequest(input);
    Truth.assertThat(output).isNotSameInstanceAs(input);
    Truth.assertThat(output.hasSize()).isFalse();

    String expected = Thumbor.create(HOST)
      .buildImage(IMAGE)
      .filter(ThumborUrlBuilder.format(WEBP))
      .toUrl();
    Truth.assertThat(output.uri.toString()).isEqualTo(expected);
  }

  @Test
  public void simpleResize() {
    Builder input = new Builder(IMAGE_URI).resize(50, 50).build();
    Builder output = transformer.transformRequest(input);
    Truth.assertThat(output).isNotSameInstanceAs(input);
    Truth.assertThat(output.hasSize()).isFalse();

    String expected = Thumbor.create(HOST)
      .buildImage(IMAGE)
      .resize(50, 50)
      .filter(ThumborUrlBuilder.format(WEBP))
      .toUrl();
    Truth.assertThat(output.uri.toString()).isEqualTo(expected);
  }

  @Test
  public void simpleResizeWithCenterCrop() {
    Builder input = new Builder(IMAGE_URI).resize(50, 50).centerCrop().build();
    Builder output = transformer.transformRequest(input);
    Truth.assertThat(output).isNotSameInstanceAs(input);
    Truth.assertThat(output.hasSize()).isFalse();
    Truth.assertThat(output.centerCrop).isFalse();

    String expected = Thumbor.create(HOST)
      .buildImage(IMAGE)
      .resize(50, 50)
      .filter(ThumborUrlBuilder.format(WEBP))
      .toUrl();
    Truth.assertThat(output.uri.toString()).isEqualTo(expected);
  }

  @Test
  public void simpleResizeWithCenterInside() {
    Builder input = new Builder(IMAGE_URI).resize(50, 50).centerInside().build();
    Builder output = transformer.transformRequest(input);
    Truth.assertThat(output).isNotSameInstanceAs(input);
    Truth.assertThat(output.hasSize()).isFalse();
    Truth.assertThat(output.centerInside).isFalse();

    String expected = Thumbor.create(HOST)
      .buildImage(IMAGE)
      .resize(50, 50)
      .filter(ThumborUrlBuilder.format(WEBP))
      .fitIn()
      .toUrl();
    Truth.assertThat(output.uri.toString()).isEqualTo(expected);
  }

  @Test
  public void simpleResizeWithEncryption() {
    Builder input = new Builder(IMAGE_URI).resize(50, 50).build();
    Builder output = secureTransformer.transformRequest(input);
    Truth.assertThat(output).isNotSameInstanceAs(input);
    Truth.assertThat(output.hasSize()).isFalse();

    String expected = Thumbor.create(HOST, KEY)
      .buildImage(IMAGE)
      .resize(50, 50)
      .filter(ThumborUrlBuilder.format(WEBP))
      .toUrl();
    Truth.assertThat(output.uri.toString()).isEqualTo(expected);
  }

  @Test
  public void simpleResizeWithCenterInsideAndEncryption() {
    Builder input = new Builder(IMAGE_URI).resize(50, 50).centerInside().build();
    Builder output = secureTransformer.transformRequest(input);
    Truth.assertThat(output).isNotSameInstanceAs(input);
    Truth.assertThat(output.hasSize()).isFalse();
    Truth.assertThat(output.centerInside).isFalse();

    String expected = Thumbor.create(HOST, KEY)
      .buildImage(IMAGE)
      .resize(50, 50)
      .filter(ThumborUrlBuilder.format(WEBP))
      .fitIn()
      .toUrl();
    Truth.assertThat(output.uri.toString()).isEqualTo(expected);
  }

  @Test
  public void configureCallback() {
    Builder input = new Builder(IMAGE_URI).resize(50, 50).build();
    Builder output = callbackTransformer.transformRequest(input);
    Truth.assertThat(output).isNotSameInstanceAs(input);
    Truth.assertThat(output.hasSize()).isFalse();
    String expected = Thumbor.create(HOST)
      .buildImage(IMAGE)
      .resize(50, 50)
      .filter("custom")
      .filter(ThumborUrlBuilder.format(WEBP))
      .toUrl();
    Truth.assertThat(output.uri.toString()).isEqualTo(expected);
  }

  private static final String HOST = "http:";
  private static final String KEY = "omgsecretpassword";
  private static final String IMAGE = "http:";
  private static final Uri IMAGE_URI = Uri.parse(IMAGE);
}