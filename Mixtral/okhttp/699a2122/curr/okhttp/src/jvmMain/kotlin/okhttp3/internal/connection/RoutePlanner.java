

package okhttp3.internal.connection;

import java.io.IOException;
import okhttp3.Address;
import okhttp3.HttpUrl;
import okhttp3.internal.connection.RoutePlanner.Plan;

public interface RoutePlanner {
  public Address address();

  public boolean isCanceled();

  public Plan plan() throws IOException;

  public void trackFailure(IOException e);

  public boolean hasFailure();

  public boolean hasMoreRoutes();

  public boolean sameHostAndPort(HttpUrl url);

  public static class Plan {
    public boolean isConnected;

    public ConnectResult connect() {
      return new ConnectResult(this);
    }

    public RealConnection handleSuccess() {
      return null; // Since RealConnection is an interface, we can't instantiate it here
    }

    public void cancel() {}
  }

  public static class ConnectResult {
    public final Plan plan;
    public final Plan nextPlan;
    public final Throwable throwable;

    public ConnectResult(Plan plan) {
      this(plan, null, null);
    }

    public ConnectResult(Plan plan, Plan nextPlan, Throwable throwable) {
      this.plan = plan;
      this.nextPlan = nextPlan;
      this.throwable = throwable;
    }
  }
}