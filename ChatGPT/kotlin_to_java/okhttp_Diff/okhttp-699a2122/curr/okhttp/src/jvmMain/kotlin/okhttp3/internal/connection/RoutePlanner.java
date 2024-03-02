package okhttp3.internal.connection;

import java.io.IOException;
import okhttp3.Address;
import okhttp3.HttpUrl;

public interface RoutePlanner {
  Address address;

  boolean isCanceled() throws IOException;

  Plan plan() throws IOException;

  void trackFailure(IOException e);

  boolean hasFailure();

  boolean hasMoreRoutes();

  boolean sameHostAndPort(HttpUrl url);

  interface Plan {
    boolean isConnected();

    ConnectResult connect();

    RealConnection handleSuccess();

    void cancel();
  }

  class ConnectResult {
    private final Plan plan;
    private final Plan nextPlan;
    private final Throwable throwable;

    public ConnectResult(Plan plan, Plan nextPlan, Throwable throwable) {
      this.plan = plan;
      this.nextPlan = nextPlan;
      this.throwable = throwable;
    }

    public ConnectResult(Plan plan) {
      this(plan, null, null);
    }
  }
}