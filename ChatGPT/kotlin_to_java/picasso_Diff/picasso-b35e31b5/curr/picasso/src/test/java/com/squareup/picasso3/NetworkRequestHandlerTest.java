package com.squareup.picasso3;

import com.google.common.truth.Truth;
import com.squareup.picasso3.RequestHandler.Result;
import com.squareup.picasso3.TestUtils.EventRecorder;
import com.squareup.picasso3.TestUtils.PremadeCall;
import com.squareup.picasso3.TestUtils.URI_1;
import com.squareup.picasso3.TestUtils.URI_KEY_1;
import com.squareup.picasso3.TestUtils.mockNetworkInfo;
import com.squareup.picasso3.TestUtils.mockPicasso;
import okhttp3.CacheControl;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
public class NetworkRequestHandlerTest {
  private LinkedBlockingDeque<Response> responses = new LinkedBlockingDeque<>();
  private LinkedBlockingDeque<okhttp3.Request> requests = new LinkedBlockingDeque<>();

  @Mock
  private Dispatcher dispatcher;
  private Picasso picasso;
  private NetworkRequestHandler networkHandler;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    picasso = TestUtils.mockPicasso(RuntimeEnvironment.application);
    networkHandler = new NetworkRequestHandler(request -> {
      requests.add(request);
      try {
        return new PremadeCall(request, responses.takeFirst());
      } catch (InterruptedException e) {
        throw new AssertionError(e);
      }
    });
  }

  @Test
  public void doesNotForceLocalCacheOnlyWithAirplaneModeOffAndRetryCount() throws Exception {
    responses.add(responseOf(ResponseBody.create(null, new byte[10])));
    Action action = TestUtils.mockAction(picasso, URI_KEY_1, URI_1);
    CountDownLatch latch = new CountDownLatch(1);
    networkHandler.load(picasso, action.request, new RequestHandler.Callback() {
      @Override
      public void onSuccess(Result result) {
        try {
          Assert.assertTrue(requests.takeFirst().cacheControl().toString().isEmpty());
          latch.countDown();
        } catch (InterruptedException e) {
          throw new AssertionError(e);
        }
      }

      @Override
      public void onError(Throwable t) {
        throw new AssertionError(t);
      }
    });
    Assert.assertTrue(latch.await(10, TimeUnit.SECONDS));
  }

  @Test
  public void withZeroRetryCountForcesLocalCacheOnly() throws Exception {
    responses.add(responseOf(ResponseBody.create(null, new byte[10])));
    Action action = TestUtils.mockAction(picasso, URI_KEY_1, URI_1);
    PlatformLruCache cache = new PlatformLruCache(0);
    BitmapHunter hunter = new BitmapHunter(picasso, dispatcher, cache, action, networkHandler);
    hunter.retryCount = 0;
    hunter.hunt();
    Assert.assertEquals(CacheControl.FORCE_CACHE.toString(), requests.takeFirst().cacheControl().toString());
  }

  @Test
  public void shouldRetryTwiceWithAirplaneModeOffAndNoNetworkInfo() {
    Action action = TestUtils.mockAction(picasso, URI_KEY_1, URI_1);
    PlatformLruCache cache = new PlatformLruCache(0);
    BitmapHunter hunter = new BitmapHunter(picasso, dispatcher, cache, action, networkHandler);
    Assert.assertTrue(hunter.shouldRetry(false, null));
    Assert.assertTrue(hunter.shouldRetry(false, null));
    Assert.assertFalse(hunter.shouldRetry(false, null));
  }

  @Test
  public void shouldRetryWithUnknownNetworkInfo() {
    Assert.assertTrue(networkHandler.shouldRetry(false, null));
    Assert.assertTrue(networkHandler.shouldRetry(true, null));
  }

  @Test
  public void shouldRetryWithConnectedNetworkInfo() {
    NetworkInfo info = mockNetworkInfo();
    Mockito.when(info.isConnected()).thenReturn(true);
    Assert.assertTrue(networkHandler.shouldRetry(false, info));
    Assert.assertTrue(networkHandler.shouldRetry(true, info));
  }

  @Test
  public void shouldNotRetryWithDisconnectedNetworkInfo() {
    NetworkInfo info = mockNetworkInfo();
    Mockito.when(info.isConnectedOrConnecting()).thenReturn(false);
    Assert.assertFalse(networkHandler.shouldRetry(false, info));
    Assert.assertFalse(networkHandler.shouldRetry(true, info));
  }

  @Test
  public void noCacheAndKnownContentLengthDispatchToStats() throws Exception {
    EventRecorder eventRecorder = new EventRecorder();
    Picasso picasso = picasso.newBuilder().addEventListener(eventRecorder).build();
    int knownContentLengthSize = 10;
    responses.add(responseOf(ResponseBody.create(null, new byte[knownContentLengthSize])));
    Action action = TestUtils.mockAction(picasso, URI_KEY_1, URI_1);
    CountDownLatch latch = new CountDownLatch(1);
    networkHandler.load(picasso, action.request, new RequestHandler.Callback() {
      @Override
      public void onSuccess(Result result) {
        Truth.assertThat(eventRecorder.downloadSize).isEqualTo(knownContentLengthSize);
        latch.countDown();
      }

      @Override
      public void onError(Throwable t) {
        throw new AssertionError(t);
      }
    });
    Assert.assertTrue(latch.await(10, TimeUnit.SECONDS));
  }

  @Test
  public void unknownContentLengthFromDiskThrows() throws Exception {
    EventRecorder eventRecorder = new EventRecorder();
    Picasso picasso = picasso.newBuilder().addEventListener(eventRecorder).build();
    AtomicBoolean closed = new AtomicBoolean();
    ResponseBody body = new ResponseBody() {
      @Override
      public MediaType contentType() {
        return null;
      }

      @Override
      public long contentLength() {
        return 0;
      }

      @Override
      public BufferedSource source() {
        return new Buffer();
      }

      @Override
      public void close() {
        closed.set(true);
        super.close();
      }
    };
    responses.add(responseOf(body)
      .newBuilder()
      .cacheResponse(responseOf(null))
      .build());
    Action action = TestUtils.mockAction(picasso, URI_KEY_1, URI_1);
    CountDownLatch latch = new CountDownLatch(1);
    networkHandler.load(picasso, action.request, new RequestHandler.Callback() {
      @Override
      public void onSuccess(Result result) {
        throw new AssertionError();
      }

      @Override
      public void onError(Throwable t) {
        Truth.assertThat(eventRecorder.downloadSize).isEqualTo(0);
        Assert.assertTrue(closed.get());
        latch.countDown();
      }
    });
    Assert.assertTrue(latch.await(10, TimeUnit.SECONDS));
  }

  @Test
  public void cachedResponseDoesNotDispatchToStats() {
    EventRecorder eventRecorder = new EventRecorder();
    Picasso picasso = picasso.newBuilder().addEventListener(eventRecorder).build();
    responses.add(responseOf(ResponseBody.create(null, new byte[10]))
      .newBuilder()
      .cacheResponse(responseOf(null))
      .build());
    Action action = TestUtils.mockAction(picasso, URI_KEY_1, URI_1);
    CountDownLatch latch = new CountDownLatch(1);
    networkHandler.load(picasso, action.request, new RequestHandler.Callback() {
      @Override
      public void onSuccess(Result result) {
        Truth.assertThat(eventRecorder.downloadSize).isEqualTo(0);
        latch.countDown();
      }

      @Override
      public void onError(Throwable t) {
        throw new AssertionError(t);
      }
    });
    Assert.assertTrue(latch.await(10, TimeUnit.SECONDS));
  }

  @Test
  public void shouldHandleSchemeInsensitiveCase() {
    String[] schemes = {"http", "https", "HTTP", "HTTPS", "HTtP"};
    for (String scheme : schemes) {
      URI uri = URI_1.buildUpon().scheme(scheme).build();
      Assert.assertTrue(networkHandler.canHandleRequest(TestUtils.mockRequest(uri)));
    }
  }

  private Response responseOf(ResponseBody body) {
    return new Response.Builder()
      .code(200)
      .protocol(Protocol.HTTP_1_1)
      .request(new okhttp3.Request.Builder().url("http://example.com").build())
      .message("OK")
      .body(body)
      .build();
  }
}