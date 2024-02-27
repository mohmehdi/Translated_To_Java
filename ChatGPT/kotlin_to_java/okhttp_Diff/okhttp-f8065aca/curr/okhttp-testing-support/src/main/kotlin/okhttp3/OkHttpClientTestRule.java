package okhttp3;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import okhttp3.internal.concurrent.TaskRunner;
import okhttp3.internal.http2.Http2;
import okhttp3.testing.Flaky;
import org.junit.Assert;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class OkHttpClientTestRule implements TestRule, BeforeEachCallback, AfterEachCallback {
    private final List<String> clientEventsList = new ArrayList<>();
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

                    if (record.getLoggerName().equals("javax.net.ssl")) {
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

    private void applyLogger(Logger logger) {
        logger.getLogger(OkHttpClient.class.getPackage().getName()).setLevel(Level.FINEST);
        logger.getLogger(OkHttpClient.class.getName()).setLevel(Level.FINEST);
        logger.getLogger(Http2.class.getName()).setLevel(Level.FINEST);
        logger.getLogger(TaskRunner.class.getName()).setLevel(Level.FINEST);
        logger.getLogger("javax.net.ssl").setLevel(Level.FINEST);
    }

    public EventListener.Factory wrap(EventListener eventListener) {
        return call -> new ClientRuleEventListener(eventListener, this::addEvent);
    }

    public EventListener.Factory wrap(EventListener.Factory eventListenerFactory) {
        return call -> new ClientRuleEventListener(eventListenerFactory.create(call), this::addEvent);
    }

    public OkHttpClient newClient() {
        OkHttpClient client = testClient;
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .dns(SINGLE_INET_ADDRESS_DNS)
                    .eventListenerFactory(call -> new ClientRuleEventListener(this::addEvent))
                    .build();
            testClient = client;
        }
        return client;
    }

    public OkHttpClient.Builder newClientBuilder() {
        return newClient().newBuilder();
    }

    private synchronized void addEvent(String event) {
        if (recordEvents) {
            logger.info(event);

            synchronized (clientEventsList) {
                clientEventsList.add(event);
            }
        }
    }

    private synchronized void initUncaughtException(Throwable throwable) {
        if (uncaughtException == null) {
            uncaughtException = throwable;
        }
    }

    public void ensureAllConnectionsReleased() {
        OkHttpClient client = testClient;
        if (client != null) {
            ConnectionPool connectionPool = client.connectionPool();

            connectionPool.evictAll();
            if (connectionPool.connectionCount() > 0) {
                System.out.println("Delaying to avoid flakes");
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("After delay: " + connectionPool.connectionCount());
            }

            Assert.assertEquals(0, connectionPool.connectionCount());
        }
    }

    private void ensureAllTaskQueuesIdle() {
        long entryTime = System.nanoTime();

        for (TaskRunner.Queue queue : TaskRunner.INSTANCE.activeQueues()) {
            long waitTime = entryTime + 1_000_000_000L - System.nanoTime();
            try {
                if (!queue.idleLatch().await(waitTime, TimeUnit.NANOSECONDS)) {
                    TaskRunner.INSTANCE.cancelAll();
                    Assert.fail("Queue still active after 1000 ms");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
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
        Thread.setDefaultUncaughtExceptionHandler((t, throwable) -> initUncaughtException(throwable));

        taskQueuesWereIdle = TaskRunner.INSTANCE.activeQueues().isEmpty();

        applyLogger(Logger.getLogger(""));

        logger = Logger.getLogger(OkHttpClient.class.getPackage().getName());
        logger.addHandler(testLogHandler);
        logger.setLevel(Level.FINEST);
        logger.setUseParentHandlers(false);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Throwable failure = context.getExecutionException().orElse(null);
        Throwable newFailure = afterEach(context.isFlaky(), failure);
        if (failure == null && newFailure != null) {
            throw newFailure;
        }
    }

    private Throwable afterEach(boolean isFlaky, Throwable failure) {
        if (uncaughtException != null) {
            return failure != null ? failure + new AssertionError("uncaught exception thrown during test", uncaughtException) : new AssertionError("uncaught exception thrown during test", uncaughtException);
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
            result = result != null ? result + ae : ae;
        }

        try {
            if (taskQueuesWereIdle) {
                ensureAllTaskQueuesIdle();
            }
        } catch (AssertionError ae) {
            result = result != null ? result + ae : ae;
        }

        return result;
    }

    private void releaseClient() {
        OkHttpClient client = testClient;
        if (client != null) {
            client.dispatcher().executorService().shutdown();
        }
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
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
                if (newFailure != null) {
                    throw newFailure;
                }
            }
        };
    }

    private boolean isFlaky(Description description) {
        return description.getAnnotations().stream().anyMatch(annotation -> annotation.annotationType() == Flaky.class) ||
                description.getTestClass().getAnnotations().stream().anyMatch(annotation -> annotation.annotationType() == Flaky.class);
    }

    private boolean isFlaky(ExtensionContext context) {
        return context.getTestMethod().map(method -> method.isAnnotationPresent(Flaky.class)).orElse(false) ||
                context.getTestClass().map(clazz -> clazz.isAnnotationPresent(Flaky.class)).orElse(false);
    }

    private void logEvents() {
        synchronized (clientEventsList) {
            System.out.println(testName + " Events (" + clientEventsList.size() + ")");

            for (String e : clientEventsList) {
                System.out.println(e);
            }
        }
    }

    private static final Dns SINGLE_INET_ADDRESS_DNS = new Dns() {
        @Override
        public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            List<InetAddress> addresses = Dns.SYSTEM.lookup(hostname);
            return Collections.singletonList(addresses.get(0));
        }
    };

    private Throwable plus(Throwable throwable1, Throwable throwable2) {
        if (throwable1 != null) {
            throwable1.addSuppressed(throwable2);
            return throwable1;
        }
        return throwable2;
    }
}