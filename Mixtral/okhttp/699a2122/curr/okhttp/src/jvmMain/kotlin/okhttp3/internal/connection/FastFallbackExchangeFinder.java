

package okhttp3.internal.connection;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import okhttp3.internal.concurrent.Task;
import okhttp3.internal.concurrent.TaskRunner;
import okhttp3.internal.connection.RoutePlanner.ConnectResult;
import okhttp3.internal.connection.RoutePlanner.Plan;
import okhttp3.internal.okHttpName;

public class FastFallbackExchangeFinder {
  private final RoutePlanner routePlanner;
  private final TaskRunner taskRunner;
  private final long connectDelayMillis = 250;

  private CopyOnWriteArrayList<Plan> connectsInFlight = new CopyOnWriteArrayList<>();
  private LinkedBlockingDeque<ConnectResult> connectResults;
  private IOException firstException;
  private boolean morePlansExist;

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

    if (plan.isConnected()) {
      connectResults.put(new ConnectResult(plan));
      return;
    }

    String taskName = "$okHttpName connect " + routePlanner.address.url.redact();
    taskRunner.newQueue().schedule(new Task(taskName) {
      @Override
      public long runOnce() {
        ConnectResult connectResult;
        try {
          connectResult = connectAndDoRetries();
        } catch (Throwable e) {
          connectResult = new ConnectResult(plan, throwable = e);
        }
        connectResults.put(connectResult);
        return -1;
      }

      private ConnectResult connectAndDoRetries() throws IOException {
        Throwable firstException = null;
        Plan currentPlan = plan;
        while (true) {
          ConnectResult connectResult = currentPlan.connect();

          if (connectResult.throwable == null) {
            if (connectResult.nextPlan == null) return connectResult;
          } else {
            if (firstException == null) {
              firstException = connectResult.throwable;
            } else {
              firstException.addSuppressed(connectResult.throwable);
            }

            if (connectResult.nextPlan == null || !(connectResult.throwable instanceof IOException)) break;
          }

          connectsInFlight.add(connectResult.nextPlan);
          connectsInFlight.remove(currentPlan);
          currentPlan = connectResult.nextPlan;
        }

        return new ConnectResult(currentPlan, throwable = firstException);
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
      throw new AssertionError("Unexpected exception type: " + exception.getClass());
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
}