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
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class OkHttpClientTestRule implements TestRule {
    private List<String> clientEventsList = new ArrayList<>();
    private OkHttpClient testClient = null;
    private Throwable uncaughtException = null;
    private Logger logger = null;
    private String testName;

    private boolean recordEvents = true;
    private boolean recordTaskRunner = false;
    private boolean recordFrames = false;
    private boolean recordSslDebug = false;

    private Handler testLogHandler = new Handler() {
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
        return () -> new ClientRuleEventListener(eventListener, this::addEvent);
    }

    public EventListener.Factory wrap(EventListener.Factory eventListenerFactory) {
        return () -> new ClientRuleEventListener(eventListenerFactory.create(call), this::addEvent);
    }


    public OkHttpClient newClient() {
        OkHttpClient client = testClient;
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .dns(SINGLE_INET_ADDRESS_DNS)
                    .eventListenerFactory(wrap(new EventListener() {}))
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

        for (TaskRunner.TaskQueue queue : TaskRunner.INSTANCE.activeQueues()) {
            long waitTime = (entryTime + 1_000_000_000L - System.nanoTime());
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
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                testName = description.getMethodName();

                Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
                Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> initUncaughtException(throwable));
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
                if (testClient != null) {
                    testClient.dispatcher().executorService().shutdown();
                }
            }
        };
    }


    private void logEventsIfFlaky(Description description) {
        if (isTestFlaky(description)) {
            logEvents();
        }
    }

    private boolean isTestFlaky(Description description) {
        return description.getAnnotations().stream().anyMatch(annotation -> annotation.annotationType() == Flaky.class) ||
                description.getTestClass().getAnnotations().stream().anyMatch(annotation -> annotation.annotationType() == Flaky.class);
    }

    private synchronized void logEvents() {
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
}