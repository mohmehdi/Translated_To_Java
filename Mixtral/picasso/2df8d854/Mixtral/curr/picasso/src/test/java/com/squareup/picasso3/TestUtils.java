package com.squareup.picasso3;

import android.app.Notification;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.Drawable;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.RemoteViews;
import com.squareup.picasso3.BitmapHunterTest.TestableBitmapHunter;
import com.squareup.picasso3.Picasso.LoadedFrom;
import com.squareup.picasso3.Picasso.Priority;
import com.squareup.picasso3.Picasso.RequestTransformer;
import com.squareup.picasso3.RequestHandler.Result;
import com.squareup.picasso3.RequestHandler.Result.Bitmap as PicassoBitmap;
import okhttp3.Call;
import okhttp3.Response;
import okio.Timeout;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestUtils {
  public static final Uri URI_1 = Uri.parse("http://example.com/1.png");
  public static final Uri URI_2 = Uri.parse("http://example.com/2.png");
  public static final String STABLE_1 = "stableExampleKey1";
  public static final Request SIMPLE_REQUEST = new Request.Builder(URI_1).build();
  public static final String URI_KEY_1 = SIMPLE_REQUEST.key;
  public static final String URI_KEY_2 = new Request.Builder(URI_2).build().key;
  public static final String STABLE_URI_KEY_1 = new Request.Builder(URI_1).stableKey(STABLE_1).build().key;
  private static final File FILE_1 = new File("C:\\windows\\system32\\logo.exe");
  public static final String FILE_KEY_1 = new Request.Builder(Uri.fromFile(FILE_1)).build().key;
  public static final Uri FILE_1_URL = Uri.parse("file:///" + FILE_1.getPath());
  public static final Uri FILE_1_URL_NO_AUTHORITY = Uri.parse("file:/" + FILE_1.getParent());
  public static final Uri MEDIA_STORE_CONTENT_1_URL = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath("1").build();
  public static final Uri MEDIA_STORE_CONTENT_2_URL = MediaStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath("1").build();
  public static final String MEDIA_STORE_CONTENT_KEY_1 = new Request.Builder(MEDIA_STORE_CONTENT_1_URL).build().key;
  public static final String MEDIA_STORE_CONTENT_KEY_2 = new Request.Builder(MEDIA_STORE_CONTENT_2_URL).build().key;
  public static final Uri CONTENT_1_URL = Uri.parse("content://zip/zap/zoop.jpg");
  public static final String CONTENT_KEY_1 = new Request.Builder(CONTENT_1_URL).build().key;
  public static final Uri CONTACT_URI_1 = Contacts.CONTENT_URI.buildUpon().appendPath("1234").build();
  public static final String CONTACT_KEY_1 = new Request.Builder(CONTACT_URI_1).build().key;
  public static final Uri CONTACT_PHOTO_URI_1 =
    Contacts.CONTENT_URI.buildUpon().appendPath("1234").appendPath(ContactsContract.Contacts.Photo.CONTENT_DIRECTORY).build();
  public static final String CONTACT_PHOTO_KEY_1 = new Request.Builder(CONTACT_PHOTO_URI_1).build().key;
  public static final int RESOURCE_ID_1 = 1;
  public static final String RESOURCE_ID_KEY_1 = new Request.Builder(RESOURCE_ID_1).build().key;
  public static final Uri ASSET_URI_1 = Uri.parse("file:///android_asset/foo/bar.png");
  public static final String ASSET_KEY_1 = new Request.Builder(ASSET_URI_1).build().key;
  private static final String RESOURCE_PACKAGE = "com.squareup.picasso3";
  private static final String RESOURCE_TYPE = "drawable";
  private static final String RESOURCE_NAME = "foo";
  public static final Uri RESOURCE_ID_URI = Uri.Builder()
    .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
    .authority(RESOURCE_PACKAGE)
    .appendPath(Integer.toString(RESOURCE_ID_1))
    .build();
  public static final String RESOURCE_ID_URI_KEY = new Request.Builder(RESOURCE_ID_URI).build().key;
  public static final Uri RESOURCE_TYPE_URI = Uri.Builder()
    .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
    .authority(RESOURCE_PACKAGE)
    .appendPath(RESOURCE_TYPE)
    .appendPath(RESOURCE_NAME)
    .build();
  public static final String RESOURCE_TYPE_URI_KEY = new Request.Builder(RESOURCE_TYPE_URI).build().key;
  public static final Uri CUSTOM_URI = Uri.parse("foo://bar");
  public static final String CUSTOM_URI_KEY = new Request.Builder(CUSTOM_URI).build().key;
  public static final String BITMAP_RESOURCE_VALUE = "foo.png";
  public static final String XML_RESOURCE_VALUE = "foo.xml";
  private static final Config DEFAULT_CONFIG = Config.ARGB_8888;
  private static final int DEFAULT_CACHE_SIZE = 123;
  public static final String CUSTOM_HEADER_NAME = "Cache-Control";
  public static final String CUSTOM_HEADER_VALUE = "no-cache";

  @Mock
  private Context context;

  @Mock
  private PackageManager pm;

  @Mock
  private Resources res;

  @Captor
  private ArgumentCaptor<TypedValue> typedValueCaptor;

  @Mock
  private Callback callback;

  @Mock
  private NetworkInfo networkInfo;

  @Mock
  private ExecutorService executorService;

  @Mock
  private Dispatcher dispatcher;

  @Mock
  private PlatformLruCache platformLruCache;

  @Mock
  private RequestHandler requestHandler;

  @Mock
  private RequestCreator requestCreator;

  @Mock
  private Result result;

  @Mock
  private PicassoBitmap bitmapResult;

  @Mock
  private ImageView imageView;

  @Mock
  private ViewTreeObserver viewTreeObserver;

  @Mock
  private IBinder windowToken;

  @Mock
  private RemoteViews remoteViews;

  @Mock
  private Notification notification;

  @Mock
  private Drawable drawable;

  @Mock
  private Bitmap bitmap;

  @Mock
  private BitmapTarget bitmapTarget;

  @Mock
  private DrawableTarget drawableTarget;

  private TestUtils() {
    MockitoAnnotations.initMocks(this);
  }

  public static Context mockPackageResourceContext() {
    Context context = mock(Context.class);
    PackageManager pm = mock(PackageManager.class);
    Resources res = mock(Resources.class);

    when(context.getPackageManager()).thenReturn(pm);
    try {
      when(pm.getResourcesForApplication(RESOURCE_PACKAGE)).thenReturn(res);
    } catch (NameNotFoundException e) {
      throw new RuntimeException(e);
    }
    when(res.getIdentifier(RESOURCE_NAME, RESOURCE_TYPE, RESOURCE_PACKAGE)).thenReturn(RESOURCE_ID_1);
    return context;
  }

  public static Resources mockResources(String resValueString) {
    Resources resources = mock(Resources.class);
    doAnswer(invocation -> {
      Object[] args = invocation.getArguments();
      TypedValue value = (TypedValue) args[1];
      value.string = resValueString;
      return null;
    }).when(resources).getValue(anyInt(), any(TypedValue.class), anyBoolean());

    return resources;
  }

  public static Request mockRequest(Uri uri) {
    return new Request.Builder(uri).build();
  }

  public static FakeAction mockAction(
    Picasso picasso,
    String key,
    Uri uri,
    Object target,
    int resourceId,
    Priority priority,
    String tag,
    Map<String, String> headers
  ) {
    Request.Builder builder = new Request.Builder(uri, resourceId, DEFAULT_CONFIG).stableKey(key);
    if (priority != null) {
      builder.priority(priority);
    }
    if (tag != null) {
      builder.tag(tag);
    }
    headers.forEach((k, v) -> builder.addHeader(k, v));
    Request request = builder.build();
    return mockAction(picasso, request, target);
  }

  public static FakeAction mockAction(Picasso picasso, Request request, Object target) {
    return new FakeAction(picasso, request, target);
  }

  public static ImageView mockImageViewTarget() {
    return mock(ImageView.class);
  }

  public static RemoteViews mockRemoteViews() {
    return mock(RemoteViews.class);
  }

  public static Notification mockNotification() {
    return mock(Notification.class);
  }

  public static ImageView mockFitImageViewTarget(boolean alive) {
    ViewTreeObserver observer = mock(ViewTreeObserver.class);
    when(observer.isAlive()).thenReturn(alive);
    ImageView mock = mock(ImageView.class);
    when(mock.getWindowToken()).thenReturn(windowToken);
    when(mock.getViewTreeObserver()).thenReturn(observer);
    return mock;
  }

  public static BitmapTarget mockBitmapTarget() {
    return mock(BitmapTarget.class);
  }

  public static DrawableTarget mockDrawableTarget() {
    return mock(DrawableTarget.class);
  }

  public static Callback mockCallback() {
    return mock(Callback.class);
  }

  public static DeferredRequestCreator mockDeferredRequestCreator(
    RequestCreator creator,
    ImageView target
  ) {
    ViewTreeObserver observer = mock(ViewTreeObserver.class);
    when(target.getViewTreeObserver()).thenReturn(observer);
    return new DeferredRequestCreator(creator, target, null);
  }

  public static RequestCreator mockRequestCreator(Picasso picasso) {
    return new RequestCreator(picasso, null, 0);
  }

  public static NetworkInfo mockNetworkInfo(boolean isConnected) {
    NetworkInfo mock = mock(NetworkInfo.class);
    when(mock.isConnected()).thenReturn(isConnected);
    when(mock.isConnectedOrConnecting()).thenReturn(isConnected);
    return mock;
  }

  public static BitmapHunter mockHunter(
    Picasso picasso,
    Result result,
    Action action,
    Exception e,
    boolean shouldRetry,
    boolean supportsReplay,
    Dispatcher dispatcher
  ) {
    return new TestableBitmapHunter(
      picasso,
      dispatcher,
      platformLruCache,
      action,
      bitmapResult,
      e,
      shouldRetry,
      supportsReplay
    );
  }

  public static Picasso mockPicasso(Context context) {
    Picasso picasso = mockPicasso(context, requestHandler);
    when(picasso.isRunning()).thenReturn(true);
    return picasso;
  }

  public static Picasso mockPicasso(Context context, RequestHandler requestHandler) {
    return new Picasso.Builder(context)
      .callFactory(UNUSED_CALL_FACTORY)
      .withCacheSize(0)
      .addRequestHandler(requestHandler)
      .build();
  }

  public static Bitmap makeBitmap(int width, int height) {
    return Bitmap.createBitmap(width, height, Config.ALPHA_8);
  }

  public static DrawableLoader makeLoaderWithDrawable(Drawable drawable) {
    return new DrawableLoader(() -> drawable);
  }

  public static class FakeAction extends Action {
    private final Picasso picasso;
    private final Request request;
    private final Object target;
    private Result completedResult;
    private Exception errorException;

    public FakeAction(Picasso picasso, Request request, Object target) {
      super(picasso, request);
      this.picasso = picasso;
      this.request = request;
      this.target = target;
    }

    @Override
    public void complete(Result result) {
      completedResult = result;
    }

    @Override
    public void error(Exception e) {
      errorException = e;
    }

    @Override
    public Object getTarget() {
      return target;
    }
  }

  public static final Call.Factory UNUSED_CALL_FACTORY =
    call -> { throw new AssertionError(); };

  public static final RequestHandler NOOP_REQUEST_HANDLER =
    new RequestHandler() {
      @Override
      public boolean canHandleRequest(Request data) {
        return false;
      }

      @Override
      public void load(Picasso picasso, Request request, Callback callback) {
      }
    };

  public static final RequestTransformer NOOP_TRANSFORMER =
    requestBuilder -> new Request.Builder(0).build();

  public static final Picasso.Listener NOOP_LISTENER = (picasso, uri, exception) -> {};

  public static final List<RequestTransformer> NO_TRANSFORMERS = Collections.emptyList();

  public static final List<RequestHandler> NO_HANDLERS = Collections.emptyList();

  public static final List<EventListener> NO_EVENT_LISTENERS = Collections.emptyList();

  public static Picasso defaultPicasso(
    Context context,
    boolean hasRequestHandlers,
    boolean hasTransformers
  ) {
    Picasso.Builder builder = new Picasso.Builder(context);

    if (hasRequestHandlers) {
      builder.addRequestHandler(NOOP_REQUEST_HANDLER);
    }
    if (hasTransformers) {
      builder.addRequestTransformer(NOOP_TRANSFORMER);
    }
    return builder
      .callFactory(UNUSED_CALL_FACTORY)
      .defaultBitmapConfig(DEFAULT_CONFIG)
      .executor(new PicassoExecutorService())
      .indicatorsEnabled(true)
      .listener(NOOP_LISTENER)
      .loggingEnabled(true)
      .withCacheSize(DEFAULT_CACHE_SIZE)
      .build();
  }

  public static class EventRecorder implements EventListener {
    private int maxCacheSize = 0;
    private int cacheSize = 0;
    private int cacheHits = 0;
    private int cacheMisses = 0;
    private long downloadSize;
    private Bitmap decodedBitmap;
    private Bitmap transformedBitmap;
    private boolean closed;

    @Override
    public void cacheMaxSize(int maxSize) {
      maxCacheSize = maxSize;
    }

    @Override
    public void cacheSize(int size) {
      cacheSize = size;
    }

    @Override
    public void cacheHit() {
      cacheHits++;
    }

    @Override
    public void cacheMiss() {
      cacheMisses++;
    }

    @Override
    public void downloadFinished(long size) {
      downloadSize = size;
    }

    @Override
    public void bitmapDecoded(Bitmap bitmap) {
      decodedBitmap = bitmap;
    }

    @Override
    public void bitmapTransformed(Bitmap bitmap) {
      transformedBitmap = bitmap;
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  public static class PremadeCall implements Call {
    private final okhttp3.Request request;
    private final Response response;

    public PremadeCall(okhttp3.Request request, Response response) {
      this.request = request;
      this.response = response;
    }

    @Override
    public okhttp3.Request request() {
      return request;
    }

    @Override
    public Response execute() {
      return response;
    }

    @Override
    public void enqueue(Callback responseCallback) {
      try {
        responseCallback.onResponse(this, response);
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }

    @Override
    public void cancel() {
      throw new AssertionError();
    }

    @Override
    public boolean isExecuted() {
      throw new AssertionError();
    }

    @Override
    public boolean isCanceled() {
      throw new AssertionError();
    }

    @Override
    public Call clone() {
      throw new AssertionError();
    }

    @Override
    public Timeout timeout() {
      throw new AssertionError();
    }
  }

  public static class TestDelegatingService implements ExecutorService {
    private final ExecutorService delegate;
    private int submissions;

    public TestDelegatingService(ExecutorService delegate) {
      this.delegate = delegate;
    }

    @Override
    public void shutdown() {
      delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
      throw new AssertionError("Not implemented.");
    }

    @Override
    public boolean isShutdown() {
      return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
      throw new AssertionError("Not implemented.");
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
      throw new AssertionError("Not implemented.");
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
      throw new AssertionError("Not implemented.");
    }

    @Override
    public Future<?> submit(Runnable task) {
      submissions++;
      return delegate.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<Callable<T>> tasks) throws InterruptedException {
      throw new AssertionError("Not implemented.");
    }

    @Override
    public <T> List<Future<T>> invokeAll(
      Collection<Callable<T>> tasks,
      long timeout,
      TimeUnit unit
    ) throws InterruptedException {
      throw new AssertionError("Not implemented.");
    }

    @Override
    public <T> T invokeAny(Collection<Callable<T>> tasks) throws InterruptedException, ExecutionException {
      throw new AssertionError("Not implemented.");
    }

    @Override
    public <T> T invokeAny(
      Collection<Callable<T>> tasks,
      long timeout,
      TimeUnit unit
    ) throws InterruptedException, ExecutionException, TimeoutException {
      throw new AssertionError("Not implemented.");
    }

    @Override
    public void execute(Runnable command) {
      delegate.execute(command);
    }
  }

  public static <T> T any(Class<T> type) {
    return Mockito.any(type);
  }

  public static <T extends Object> T eq(T value) {
    return Mockito.eq(value);
  }

  public static <T> ArgumentCaptor<T> argumentCaptor() {
    return ArgumentCaptor.forClass(T.class);
  }



    public class KArgumentCaptor<T> {
        private final ArgumentCaptor<T> captor;

        public KArgumentCaptor(ArgumentCaptor<T> captor) {
            this.captor = captor;
        }

        public T getValue() {
            return captor.getValue();
        }

        public T capture() {
            return captor.capture();
        }
    }
}