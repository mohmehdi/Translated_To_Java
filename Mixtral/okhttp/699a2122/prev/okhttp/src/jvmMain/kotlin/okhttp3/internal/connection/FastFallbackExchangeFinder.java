

package okhttp3.internal.connection;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import okhttp3.internal.concurrent.Task;
import okhttp3.internal.concurrent.TaskRunner;
import okhttp3.internal.connection.RoutePlanner.Plan;
import okhttp3.internal.okHttpName;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class FastFallbackExchangeFinder {
  private final RoutePlanner routePlanner;
  private final TaskRunner taskRunner;
  private final long connectDelayMillis = 250;
  
  private final ArrayList<Plan> connectsInFlight = new ArrayList<>();
  
  private final LinkedBlockingDeque<ConnectResult> connectResults;
  
  private IOException firstException;
  
  private boolean morePlansExist = true;

  public FastFallbackExchangeFinder(RoutePlanner routePlanner, TaskRunner taskRunner) {
    this.routePlanner = routePlanner;
    this.taskRunner = taskRunner;
    this.connectResults = taskRunner.backend.decorate(new LinkedBlockingDeque<>());
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

      if (firstException != null) throw firstException;
    } finally {
      for (Plan plan : connectsInFlight) {
        plan.cancel();
      }
    }
    return null;
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

    if (plan.isConnected) {
      connectResults.put(new ConnectResult(plan, null));
      return;
    }

    String taskName = "$okHttpName connect " + routePlanner.address.url.redact();
    taskRunner.newQueue().schedule(new Task(taskName) {
      @Override
      public long runOnce() {
        try {
          plan.connect();
          connectResults.put(new ConnectResult(plan, null));
        } catch (Throwable e) {
          connectResults.put(new ConnectResult(plan, e));
        }
        return -1;
      }
    });
  }

  private RealConnection awaitConnection() {
    if (connectsInFlight.isEmpty()) return null;

    ConnectResult completed;
    try {
      completed = connectResults.poll(connectDelayMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      return null;
    }
    if (completed == null) return null;

    connectsInFlight.remove(completed.plan);

    Throwable exception = completed.throwable;
    if (exception instanceof IOException) {
      trackFailure((IOException) exception);
      return null;
    } else if (exception != null) {
      throw new ExecutionException(exception);
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
    final Plan plan;
    final Throwable throwable;

    ConnectResult(Plan plan, Throwable throwable) {
      this.plan = plan;
      this.throwable = throwable;
    }
  }
}