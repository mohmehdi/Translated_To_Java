

package okhttp3.internal.connection;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ExchangeFinder {
    private final RoutePlanner routePlanner;

    public ExchangeFinder(RoutePlanner routePlanner) {
        this.routePlanner = routePlanner;
    }

    public RealConnection find() throws IOException {
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
                    List<IOException> suppressedList = Collections.singletonList(e);
                    firstException.addSuppressed(suppressedList);
                }
                if (!routePlanner.retryAfterFailure()) {
                    throw firstException;
                }
            }
        }
    }
}