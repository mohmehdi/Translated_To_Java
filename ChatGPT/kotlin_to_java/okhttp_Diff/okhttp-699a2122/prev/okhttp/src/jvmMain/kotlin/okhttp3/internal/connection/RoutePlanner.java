package okhttp3.internal.connection;

import java.io.IOException;
import okhttp3.Address;
import okhttp3.HttpUrl;

public interface RoutePlanner {
  Address address;

  boolean isCanceled();

  Plan plan() throws IOException;

  void trackFailure(IOException e);

  boolean hasFailure();

  boolean hasMoreRoutes();

  boolean sameHostAndPort(HttpUrl url);

  interface Plan {
    boolean isConnected;

    void connect() throws IOException;

    RealConnection handleSuccess();

    void cancel();
  }
}