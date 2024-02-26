
package okhttp3.internal.connection;

import java.io.IOException;
import java.util.Objects;

class ExchangeFinder {
    private final RoutePlanner routePlanner;

    ExchangeFinder(RoutePlanner routePlanner) {
        this.routePlanner = routePlanner;
    }

    RealConnection find() throws IOException {
        IOException firstException = null;
        while (true) {
            if (routePlanner.isCanceled()) {
                throw new IOException("Canceled");
            }

            try {
                RoutePlan plan = routePlanner.plan();
                if (!plan.isConnected) {
                    plan.connect();
                }
                return plan.handleSuccess();
            } catch (IOException e) {
                routePlanner.trackFailure(e);

                if (firstException == null) {
                    firstException = e;
                } else {
                    firstException.addSuppressed(e);
                }
                if (!routePlanner.hasMoreRoutes()) {
                    throw Objects.requireNonNull(firstException);
                }
            }
        }
    }
}