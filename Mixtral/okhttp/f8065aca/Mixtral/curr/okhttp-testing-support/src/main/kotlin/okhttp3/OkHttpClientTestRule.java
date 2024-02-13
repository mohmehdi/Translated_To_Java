
package okhttp3;

import java.net.InetAddress;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import okhttp3.internal.concurrent.TaskRunner;
import okhttp3.internal.http2.Http2;
import okhttp3.testing.Flaky;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestRule;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.runners.model.Statement;

import static java.util.logging.Level.FINEST;

public class OkHttpClientTestRule implements TestRule, BeforeEachCallback, AfterEachCallback {
  private List<String> clientEventsList = new ArrayList<>();
  private OkHttpClient testClient;
  private Throwable uncaughtException;
  private Logger logger;
  private String testName;
  private Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler;
  private boolean taskQueuesWereIdle;

  public boolean recordEvents = true;
  public boolean recordTaskRunner = false;
  public boolean recordFrames = false;
  public boolean recordSslDebug = false;

  private final Handler testLogHandler = new Handler() {
    @Override
    public void publish(LogRecord record) {
      String name = record.getLoggerName();
      boolean recorded = false;
      if (name.equals(TaskRunner.class.getName())) {
        recorded = recordTaskRunner;
      } else if (name.equals(Http2.class.getName())) {
        recorded = recordFrames;
      } else if (name.equals("javax.net.ssl")) {
        recorded = recordSslDebug;
      }

      if (recorded) {
        synchronized (clientEventsList) {
          clientEventsList.add(record.getMessage());

          if (name.equals("javax.net.ssl")) {
            Object[] parameters = record.getParameters();
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
    public void close() {
    }
  };

  private void applyLogger(Consumer<Logger> fn) {
    Logger.getLogger(OkHttpClient.class.getPackage().getName()).fn();
    Logger.getLogger(OkHttpClient.class.getName()).fn();
    Logger.getLogger(Http2.class.getName()).fn();
    Logger.getLogger(TaskRunner.class.getName()).fn();
    Logger.getLogger("javax.net.ssl").fn();
  }

  public EventListener wrap(EventListener eventListener) {
    return EventListener.Factory.create(call -> new ClientRuleEventListener(eventListener, this::addEvent));
  }

  public EventListener.Factory wrap(EventListener.Factory eventListenerFactory) {
    return call -> new ClientRuleEventListener(eventListenerFactory.create(call), this::addEvent);
  }

  public OkHttpClient newClient() {
    OkHttpClient client = testClient;
    if (client == null) {
      client = new OkHttpClient.Builder()
          .dns(SINGLE_INET_ADDRESS_DNS)
          .eventListenerFactory(
              EventListener.Factory.create(call -> new ClientRuleEventListener(logger, this::addEvent)))
          .build();
      testClient = client;
    }
    return client;
  }

  public OkHttpClient.Builder newClientBuilder() {
    return newClient().newBuilder();
  }

  public synchronized void addEvent(String event) {
    if (recordEvents) {
      logger.info(event);

      synchronized (clientEventsList) {
        clientEventsList.add(event);
      }
    }
  }

  public synchronized void initUncaughtException(Throwable throwable) {
    if (uncaughtException == null) {
      uncaughtException = throwable;
    }
  }

  public void ensureAllConnectionsReleased() {
    if (testClient != null) {
      ConnectionPool connectionPool = testClient.connectionPool();

      connectionPool.evictAll();
      int connectionCount = connectionPool.connectionCount();
      if (connectionCount > 0) {
        System.out.println("Delaying to avoid flakes");
        try {
          Thread.sleep(500L);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        System.out.println("After delay: " + connectionPool.connectionCount());
      }

      Assert.assertEquals(0, connectionPool.connectionCount());
    }
  }

  private void ensureAllTaskQueuesIdle() {
    long entryTime = System.nanoTime();

    for (TaskRunner.Queue queue : TaskRunner.INSTANCE.activeQueues()) {
      long waitTime = (entryTime + 1_000_000_000L - System.nanoTime());
      if (!queue.idleLatch().await(waitTime, TimeUnit.NANOSECONDS)) {
        TaskRunner.INSTANCE.cancelAll();
        throw new RuntimeException("Queue still active after 1000 ms");
      }
    }
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    testName = context.getDisplayName();

    beforeEach();
  }

  private void beforeEach() {
    defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((t, e) -> initUncaughtException(e));

    taskQueuesWereIdle = TaskRunner.INSTANCE.activeQueues().isEmpty();

    applyLogger(logger -> {
      logger.addHandler(testLogHandler);
      logger.setLevel(FINEST);
      logger.setUseParentHandlers(false);
    });
  }

  @Override
  public void afterEach(ExtensionContext context) {
    Throwable failure = context.getExecutionException().orElse(null);
    Throwable newFailure = afterEach(context.isFlaky(), failure);
    if (failure == null && newFailure != null) throw newFailure;
  }

  private Throwable afterEach(boolean isFlaky, Throwable failure) {
    if (uncaughtException != null) {
      return new AssertionError("uncaught exception thrown during test", uncaughtException);
    }

    if (isFlaky) {
      logEvents();
    }

    LogManager.getLogManager().reset();

    Throwable result = failure;
    Thread.setDefaultUncaughtExceptionHandler(defaultUncaughtExceptionHandler);
    try {
      ensureAllConnectionsReleased();
      releaseClient();
    } catch (AssertionError ae) {
      result = new AssertionError(result, ae);
    }

    try {
      if (taskQueuesWereIdle) {
        ensureAllTaskQueuesIdle();
      }
    } catch (AssertionError ae) {
      result = new AssertionError(result, ae);
    }

    return result;
  }

  private void releaseClient() {
    testClient.dispatcher().executorService().shutdown();
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() {
        testName = description.getMethodName();
        beforeEach();

        Throwable failure = null;
        try {
          base.evaluate();
        } catch (Throwable t) {
          failure = t;
          logEvents();
        }

        Throwable newFailure = afterEach(description.isFlaky(), failure);
        if (newFailure != null) throw newFailure;
      }
    };
  }

  public boolean isFlaky(Description description) {
    return description.getAnnotations().stream()
        .anyMatch(annotation -> annotation.annotationType().equals(Flaky.class));
  }

  public boolean isFlaky(ExtensionContext context) {
    return context.getTestMethod().map(method -> method.isAnnotationPresent(Flaky.class))
        .orElseGet(() -> context.getTestClass().map(clazz -> clazz.isAnnotationPresent(Flaky.class))
            .orElse(false));
  }

  @Synchronized
  private void logEvents() {
    synchronized (clientEventsList) {
      System.out.printf("%s Events (%d)%n", testName, clientEventsList.size());

      for (String e : clientEventsList) {
        System.out.println(e);
      }
    }
  }

    @Override
    public List<InetAddress> lookup(String hostname) {
      List<InetAddress> addresses = Dns.SYSTEM.lookup(hostname);
      return Arrays.asList(addresses.get(0));
    }

  public static Throwable plus(Throwable a, Throwable b) {
    Objects.requireNonNull(a);
    Objects.requireNonNull(b);
    a.addSuppressed(b);
    return a;
  }
}