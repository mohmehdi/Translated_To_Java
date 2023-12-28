package com.squareup.picasso3;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.net.Uri;

import com.google.common.truth.Truth;
import com.squareup.picasso3.MediaStoreRequestHandler.Companion.getPicassoKind;
import com.squareup.picasso3.MediaStoreRequestHandler.PicassoKind;
import com.squareup.picasso3.RequestHandler.Callback;
import com.squareup.picasso3.Shadows.ShadowImageThumbnails;
import com.squareup.picasso3.Shadows.ShadowVideoThumbnails;
import com.squareup.picasso3.TestUtils;
import com.squareup.picasso3.TestUtils.MEDIA_STORE_CONTENT_1_URL;
import com.squareup.picasso3.TestUtils.MEDIA_STORE_CONTENT_KEY_1;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowVideoThumbnails.class, ShadowImageThumbnails.class})
public class MediaStoreRequestHandlerTest {
  @Mock
  private Context context;
  @Mock
  private Picasso picasso;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void decodesVideoThumbnailWithVideoMimeType() {
    Bitmap bitmap = TestUtils.makeBitmap();
    Request request = new Request.Builder(
      MEDIA_STORE_CONTENT_1_URL,
      0,
      Config.ARGB_8888
    )
      .stableKey(MEDIA_STORE_CONTENT_KEY_1)
      .resize(100, 100)
      .build();
    Action action = TestUtils.mockAction(request);
    MediaStoreRequestHandler requestHandler = create("video/");
    requestHandler.load(picasso, action.request, new Callback() {
      @Override
      public void onSuccess(RequestHandler.Result result) {
        assertBitmapsEqual(((RequestHandler.Result.Bitmap) result).bitmap, bitmap);
      }

      @Override
      public void onError(Throwable t) {
        Assert.fail(t.getMessage());
      }
    });
  }

  @Test
  public void decodesImageThumbnailWithImageMimeType() {
    Bitmap bitmap = TestUtils.makeBitmap(20, 20);
    Request request = new Request.Builder(
      MEDIA_STORE_CONTENT_1_URL,
      0,
      Config.ARGB_8888
    )
      .stableKey(MEDIA_STORE_CONTENT_KEY_1)
      .resize(100, 100)
      .build();
    Action action = TestUtils.mockAction(request);
    MediaStoreRequestHandler requestHandler = create("image/png");
    requestHandler.load(picasso, action.request, new Callback() {
      @Override
      public void onSuccess(RequestHandler.Result result) {
        assertBitmapsEqual(((RequestHandler.Result.Bitmap) result).bitmap, bitmap);
      }

      @Override
      public void onError(Throwable t) {
        Assert.fail(t.getMessage());
      }
    });
  }

  @Test
  public void getPicassoKindMicro() {
    Truth.assertThat(getPicassoKind(96, 96)).isEqualTo(PicassoKind.MICRO);
    Truth.assertThat(getPicassoKind(95, 95)).isEqualTo(PicassoKind.MICRO);
  }

  @Test
  public void getPicassoKindMini() {
    Truth.assertThat(getPicassoKind(512, 384)).isEqualTo(PicassoKind.MINI);
    Truth.assertThat(getPicassoKind(100, 100)).isEqualTo(PicassoKind.MINI);
  }

  @Test
  public void getPicassoKindFull() {
    Truth.assertThat(getPicassoKind(513, 385)).isEqualTo(PicassoKind.FULL);
    Truth.assertThat(getPicassoKind(1000, 1000)).isEqualTo(PicassoKind.FULL);
    Truth.assertThat(getPicassoKind(1000, 384)).isEqualTo(PicassoKind.FULL);
    Truth.assertThat(getPicassoKind(1000, 96)).isEqualTo(PicassoKind.FULL);
    Truth.assertThat(getPicassoKind(96, 1000)).isEqualTo(PicassoKind.FULL);
  }

  private MediaStoreRequestHandler create(String mimeType) {
    ContentResolver contentResolver = Mockito.mock(ContentResolver.class);
    Mockito.when(contentResolver.getType(Mockito.any(Uri.class))).thenReturn(mimeType);
    return create(contentResolver);
  }

  private MediaStoreRequestHandler create(ContentResolver contentResolver) {
    Mockito.when(context.getContentResolver()).thenReturn(contentResolver);
    return new MediaStoreRequestHandler(context);
  }

  private void assertBitmapsEqual(Bitmap a, Bitmap b) {
    if (a.getHeight() != b.getHeight()) {
      Assert.fail();
    }
    if (a.getWidth() != b.getWidth()) {
      Assert.fail();
    }

    if (Shadows.shadowOf(a).getDescription() != Shadows.shadowOf(b).getDescription()) {
      Assert.fail();
    }
  }
}