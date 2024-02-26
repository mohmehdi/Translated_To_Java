

package okhttp3.internal.connection;

import java.io.IOException;
import okhttp3.Address;
import okhttp3.HttpUrl;
import okhttp3.internal.connection.RoutePlanner.Plan;

public interface RoutePlanner {
  public Address address;

  public boolean isCanceled();

  public Plan plan() throws IOException;

  public void trackFailure(IOException e);

  public boolean hasFailure();

  public boolean hasMoreRoutes();

  public boolean sameHostAndPort(HttpUrl url);

  public interface Plan {
    public boolean isConnected;

    public void connect() throws IOException;

    public RealConnection handleSuccess();

    public void cancel();
  }
}

