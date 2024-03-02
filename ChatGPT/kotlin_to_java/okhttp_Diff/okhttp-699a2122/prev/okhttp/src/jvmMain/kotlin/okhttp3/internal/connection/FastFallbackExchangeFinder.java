package okhttp3.internal.connection;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import okhttp3.internal.concurrent.Task;
import okhttp3.internal.concurrent.TaskRunner;
import okhttp3.internal.connection.RoutePlanner.Plan;
import okhttp3.internal.okHttpName;

public class FastFallbackExchangeFinder {
  private RoutePlanner routePlanner;
  private TaskRunner taskRunner;
  private long connectDelayMillis = 250L;
  private List<Plan> connectsInFlight;
  private LinkedBlockingDeque<ConnectResult> connectResults;
  private IOException firstException;
  private boolean morePlansExist;

  public FastFallbackExchangeFinder(RoutePlanner routePlanner, TaskRunner taskRunner) {
    this.routePlanner = routePlanner;
    this.taskRunner = taskRunner;
    this.connectsInFlight = new ArrayList<>();
    this.connectResults = taskRunner.backend.decorate(new LinkedBlockingDeque<>());
    this.firstException = null;
    this.morePlansExist = true;
  }

  public RealConnection find() {
    try {
      while (morePlansExist || !connectsInFlight.isEmpty()) {
        if (routePlanner.isCanceled()) throw new IOException("Canceled");

        launchConnect();

        RealConnection connection = awaitConnection();
        if (connection != null) return connection;

        morePlansExist = morePlansExist && routePlanner.hasMoreRoutes();
      }

      throw firstException;
    } finally {
      for (Plan plan : connectsInFlight) {
        plan.cancel();
      }
    }
  }

  private void launchConnect() {
    if (!morePlansExist) return;

    Plan plan;
    try {
      plan = routePlanner.plan();
    } catch (IOException e) {
      trackFailure(e);
      return;
    }

    connectsInFlight.add(plan);

    if (plan.isConnected()) {
      connectResults.put(new ConnectResult(plan, null));
      return;
    }

    String taskName = okHttpName + " connect " + routePlanner.address.url.redact();
    taskRunner.newQueue().schedule(new Task(taskName) {
      @Override
      public long runOnce() {
        try {
          plan.connect();
          connectResults.put(new ConnectResult(plan, null));
        } catch (Throwable e) {
          connectResults.put(new ConnectResult(plan, e));
        }
        return -1L;
      }
    });
  }

  private RealConnection awaitConnection() {
    if (connectsInFlight.isEmpty()) return null;

    ConnectResult completed = connectResults.poll(connectDelayMillis, TimeUnit.MILLISECONDS);
    if (completed == null) return null;

    connectsInFlight.remove(completed.plan);

    Throwable exception = completed.throwable;
    if (exception instanceof IOException) {
      trackFailure((IOException) exception);
      return null;
    } else if (exception != null) {
      throw new RuntimeException(exception);
    }

    return completed.plan.handleSuccess();
  }

  private void trackFailure(IOException exception) {
    routePlanner.trackFailure(exception);

    if (firstException == null) {
      firstException = exception;
    } else {
      firstException.addSuppressed(exception);
    }
  }

  private static class ConnectResult {
    private Plan plan;
    private Throwable throwable;

    public ConnectResult(Plan plan, Throwable throwable) {
      this.plan = plan;
      this.throwable = throwable;
    }
  }
}