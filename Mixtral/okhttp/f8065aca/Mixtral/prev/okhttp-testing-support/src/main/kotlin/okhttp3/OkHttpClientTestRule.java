
package okhttp3;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.EventListener.Factory;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.internal.Internal;
import okhttp3.internal.Util;
import okhttp3.internal.connection.RealConnection;
import okhttp3.internal.connection.StreamAllocation;
import okhttp3.internal.http2.Http2;
import okhttp3.internal.http2.Http2Connection;
import okhttp3.internal.http2.Http2Connection.Builder;
import okhttp3.internal.http2.Http2Layer;
import okhttp3.internal.platform.Platform;
import okhttp3.internal.platform.Platform.ExceptionCatcher;
import okhttp3.internal.platform.Platform.UserLog;
import okhttp3.internal.platform.PlatformJdk14;
import okhttp3.internal.platform.PlatformJdkFix;
import okhttp3.internal.platform.PlatformTest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.testing.Flaky;
import okhttp3.testing.MockSchdeduler;
import okhttp3.testing.PlatformJdk14WithFixedScheduler;
import okhttp3.testing.PlatformTestWithFixedScheduler;
import okhttp3.testing.RecordingExecutor;
import okhttp3.testing.RecordingExecutor.Task;
import okhttp3.testing.TestRule;
import okhttp3.testing.TestRules;
import okhttp3.tls.HandshakeCertificates;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ByteString;
import okio.Okio;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.Mockito;

public class OkHttpClientTestRule implements TestRule {
  private final List<String> clientEventsList = new CopyOnWriteArrayList<>();
  private OkHttpClient testClient;
  private Throwable uncaughtException;
  private Logger logger;
  private String testName;

  private boolean recordEvents = true;
  private boolean recordTaskRunner = false;
  private boolean recordFrames = false;
  private boolean recordSslDebug = false;

  private final Handler testLogHandler = new Handler() {
    @Override
    public void publish(LogRecord record) {
      final String name = record.getLoggerName();
      final boolean recorded = switch (name) {
        case TaskRunner.class.getName() -> recordTaskRunner;
        case Http2.class.getName() -> recordFrames;
        case "javax.net.ssl" -> recordSslDebug;
        default -> false;
      };

      if (recorded) {
        synchronized (clientEventsList) {
          clientEventsList.add(record.getMessage());

          if (name.equals("javax.net.ssl")) {
            final Object[] parameters = record.getParameters();

            if (parameters != null) {
              clientEventsList.add(parameters[0].toString());
            }
          }
        }
      }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
    }
  }.apply {
    setLevel(Level.FINEST);
  };

  private void applyLogger(Consumer<Logger> fn) {
    Logger.getLogger(OkHttpClient.class.getPackage().getName()).fn();
    Logger.getLogger(OkHttpClient.class.getName()).fn();
    Logger.getLogger(Http2.class.getName()).fn();
    Logger.getLogger(TaskRunner.class.getName()).fn();
    Logger.getLogger("javax.net.ssl").fn();
  }

  public EventListener.Factory wrap(EventListener eventListener) {
    return new EventListener.Factory() {

    };
  }

  public EventListener.Factory wrap(EventListener.Factory eventListenerFactory) {
    return new EventListener.Factory() {

    };
  }

  public OkHttpClient newClient() {
    OkHttpClient client = testClient;
    if (client == null) {
      client = new OkHttpClient.Builder()
          .dns(SINGLE_INET_ADDRESS_DNS)
          .eventListenerFactory(
              new EventListener.Factory() {

              })
          .build();
      testClient = client;
    }
    return client;
  }

  public OkHttpClient.Builder newClientBuilder() {
    return newClient().newBuilder();
  }

  public void addEvent(String event) {
    if (recordEvents) {
      logger.info(event);

      synchronized (clientEventsList) {
        clientEventsList.add(event);
      }
    }
  }

  public void initUncaughtException(Throwable throwable) {
    if (uncaughtException == null) {
      uncaughtException = throwable;
    }
  }

  public void ensureAllConnectionsReleased() {
    testClient.connectionPool().evictAll();

    int connectionCount = testClient.connectionPool().connectionCount();
    if (connectionCount > 0) {
      System.out.println("Delaying to avoid flakes");
      try {
        Thread.sleep(500L);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      System.out.println("After delay: " + testClient.connectionPool().connectionCount());
    }

    Assert.assertEquals(0, connectionCount);
  }

  public void ensureAllTaskQueuesIdle() {
    long entryTime = System.nanoTime();

    for (TaskRunner.Queue queue : TaskRunner.INSTANCE.activeQueues()) {
      long waitTime = (entryTime + 1_000_000_000L - System.nanoTime());
      if (!queue.idleLatch().await(waitTime, TimeUnit.NANOSECONDS)) {
        TaskRunner.INSTANCE.cancelAll();
        Assert.fail("Queue still active after 1000 ms");
      }
    }
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() {
        testName = description.getMethodName();

        Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> initUncaughtException(e));

        boolean taskQueuesWereIdle = TaskRunner.INSTANCE.activeQueues().isEmpty();
        Throwable failure = null;
        try {
          applyLogger(logger -> {
            logger.addHandler(testLogHandler);
            logger.setLevel(Level.FINEST);
            logger.setUseParentHandlers(false);
          });

          base.evaluate();
          if (uncaughtException != null) {
            throw new AssertionError("uncaught exception thrown during test", uncaughtException);
          }
          logEventsIfFlaky(description);
        } catch (Throwable t) {
          failure = t;
          logEvents();
          throw t;
        } finally {
          LogManager.getLogManager().reset();

          Thread.setDefaultUncaughtExceptionHandler(defaultUncaughtExceptionHandler);
          try {
            ensureAllConnectionsReleased();
            releaseClient();
          } catch (AssertionError ae) {
            if (failure != null) {
              failure.addSuppressed(ae);
            } else {
              failure = ae;
            }
          }

          try {
            if (taskQueuesWereIdle) {
              ensureAllTaskQueuesIdle();
            }
          } catch (AssertionError ae) {
            if (failure != null) {
              failure.addSuppressed(ae);
            } else {
              failure = ae;
            }
          }

          if (failure != null) {
            throw failure;
          }
        }
      }

      private void releaseClient() {
        testClient.dispatcher().executorService().shutdown();
      }
    };
  }

  private void logEventsIfFlaky(Description description) {
    if (isTestFlaky(description)) {
      logEvents();
    }
  }

  private boolean isTestFlaky(Description description) {
    return Arrays.stream(description.getAnnotations())
        .anyMatch(annotation -> annotation.annotationType() == Flaky.class) ||
        Arrays.stream(description.getTestClass().getAnnotations())
            .anyMatch(annotation -> annotation.annotationType() == Flaky.class);
  }

  private void logEvents() {
    synchronized (clientEventsList) {
      System.out.println(MessageFormat.format(
          "{0} Events ({1})",
          testName,
          clientEventsList.size()));

      for (String e : clientEventsList) {
        System.out.println(e);
      }
    }
  }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
      List<InetAddress> addresses = Dns.SYSTEM.lookup(hostname);
      return Arrays.asList(addresses.get(0));
    }
}