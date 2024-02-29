package okhttp3.internal.connection;

import java.io.IOException;
import java.net.Socket;
import okhttp3.Address;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Route;
import okhttp3.internal.canReuseConnectionFor;
import okhttp3.internal.closeQuietly;
import okhttp3.internal.http.ExchangeCodec;
import okhttp3.internal.http.RealInterceptorChain;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.StreamResetException;

public class ExchangeFinder {
    private OkHttpClient client;
    private Address address;
    private RealCall call;
    private RealInterceptorChain chain;
    private ConnectionPool connectionPool;
    private EventListener eventListener;
    private boolean doExtensiveHealthChecks;
    private RouteSelector.Selection routeSelection;
    private RouteSelector routeSelector;
    private int refusedStreamCount;
    private int connectionShutdownCount;
    private int otherFailureCount;
    private Route nextRouteToTry;

    public ExchangeFinder(OkHttpClient client, Address address, RealCall call, RealInterceptorChain chain) {
        this.client = client;
        this.address = address;
        this.call = call;
        this.chain = chain;
        this.connectionPool = client.connectionPool.delegate;
        this.eventListener = call.eventListener;
        this.doExtensiveHealthChecks = !chain.request.method.equals("GET");
    }

    public ExchangeCodec find() throws IOException {
        IOException firstException = null;
        while (true) {
            if (call.isCanceled()) throw new IOException("Canceled");

            try {
                Plan plan = plan();
                if (plan instanceof ReusePlan) {
                    return ((ReusePlan) plan).connection.newCodec(client, chain);
                } else if (plan instanceof ConnectPlan) {
                    ConnectPlan connectPlan = (ConnectPlan) plan;
                    connectPlan.connect();
                    IOException failure = connectPlan.failure;
                    if (failure != null) throw failure;
                    return connectPlan.handleSuccess().newCodec(client, chain);
                }
            } catch (IOException e) {
                trackFailure(e);
                if (firstException == null) {
                    firstException = e;
                } else {
                    firstException.addSuppressed(e);
                }
                if (!retryAfterFailure()) {
                    throw firstException;
                }
            }
        }
    }

    private Plan plan() throws IOException {
        ReusePlan reuseCallConnection = planReuseCallConnection();
        if (reuseCallConnection != null) return reuseCallConnection;

        refusedStreamCount = 0;
        connectionShutdownCount = 0;
        otherFailureCount = 0;

        ReusePlan pooled1 = planReusePooledConnection();
        if (pooled1 != null) return pooled1;

        ConnectPlan connect = planConnect();

        ReusePlan pooled2 = planReusePooledConnection(connect.connection, connect.routes);
        if (pooled2 != null) return pooled2;

        return connect;
    }

    private ReusePlan planReuseCallConnection() {
        RealConnection candidate = call.connection;
        if (candidate == null) return null;

        boolean healthy = candidate.isHealthy(doExtensiveHealthChecks);
        Socket toClose = null;
        synchronized (candidate) {
            if (!healthy) {
                candidate.noNewExchanges = true;
                call.releaseConnectionNoEvents();
            } else if (candidate.noNewExchanges || !sameHostAndPort(candidate.route().address.url)) {
                call.releaseConnectionNoEvents();
            } else {
                return new ReusePlan(candidate);
            }
        }

        if (call.connection != null) {
            assert toClose == null;
            return new ReusePlan(candidate);
        }

        closeQuietly(toClose);
        eventListener.connectionReleased(call, candidate);
        return null;
    }

    private ConnectPlan planConnect() throws IOException {
        Route localNextRouteToTry = nextRouteToTry;
        if (localNextRouteToTry != null) {
            nextRouteToTry = null;
            return new ConnectPlan(localNextRouteToTry);
        }

        RouteSelector.Selection existingRouteSelection = routeSelection;
        if (existingRouteSelection != null && existingRouteSelection.hasNext()) {
            return new ConnectPlan(existingRouteSelection.next());
        }

        RouteSelector newRouteSelector = routeSelector;
        if (newRouteSelector == null) {
            newRouteSelector = new RouteSelector(address, call.client.routeDatabase, call, eventListener);
            routeSelector = newRouteSelector;
        }

        if (!newRouteSelector.hasNext()) throw new IOException("exhausted all routes");
        RouteSelector.Selection newRouteSelection = newRouteSelector.next();
        routeSelection = newRouteSelection;

        if (call.isCanceled()) throw new IOException("Canceled");

        return new ConnectPlan(newRouteSelection.next(), newRouteSelection.routes);
    }

    private ReusePlan planReusePooledConnection(RealConnection connectionToReplace, List<Route> routes) {
        ConnectionPool.AcquirePooledConnectionResult result = connectionPool.callAcquirePooledConnection(
                doExtensiveHealthChecks, address, call, routes, connectionToReplace != null && !connectionToReplace.isNew);
        if (result == null) return null;

        if (connectionToReplace != null) {
            nextRouteToTry = connectionToReplace.route();
            if (!connectionToReplace.isNew) {
                connectionToReplace.socket().closeQuietly();
            }
        }

        eventListener.connectionAcquired(call, result.connection);
        return new ReusePlan(result.connection);
    }

    private void trackFailure(IOException e) {
        if (e instanceof StreamResetException && ((StreamResetException) e).errorCode == ErrorCode.REFUSED_STREAM) {
            refusedStreamCount++;
        } else if (e instanceof ConnectionShutdownException) {
            connectionShutdownCount++;
        } else {
            otherFailureCount++;
        }
    }

    private boolean retryAfterFailure() {
        if (refusedStreamCount == 0 && connectionShutdownCount == 0 && otherFailureCount == 0) {
            return false;
        }

        if (nextRouteToTry != null) {
            return true;
        }

        Route retryRoute = retryRoute();
        if (retryRoute != null) {
            nextRouteToTry = retryRoute;
            return true;
        }

        if (routeSelection != null && routeSelection.hasNext()) return true;

        RouteSelector localRouteSelector = routeSelector;
        if (localRouteSelector != null) {
            return localRouteSelector.hasNext();
        }

        return true;
    }

    private Route retryRoute() {
        if (refusedStreamCount > 1 || connectionShutdownCount > 1 || otherFailureCount > 0) {
            return null;
        }

        RealConnection connection = call.connection;
        if (connection == null) return null;

        synchronized (connection) {
            if (connection.routeFailureCount != 0) return null;
            if (!connection.noNewExchanges) return null;
            if (!connection.route().address.url.canReuseConnectionFor(address.url)) return null;
            return connection.route();
        }
    }

    private boolean sameHostAndPort(HttpUrl url) {
        HttpUrl routeUrl = address.url;
        return url.port == routeUrl.port && url.host.equals(routeUrl.host);
    }

    private static class Plan {
    }

    private static class ReusePlan extends Plan {
        private RealConnection connection;

        public ReusePlan(RealConnection connection) {
            this.connection = connection;
        }
    }

    private class ConnectPlan extends Plan {
        private Route route;
        private List<Route> routes;
        private RealConnection connection;
        private boolean complete;
        private IOException failure;

        public ConnectPlan(Route route, List<Route> routes) {
            this.route = route;
            this.routes = routes;
            this.connection = new RealConnection(client.taskRunner, connectionPool, route);
        }

        public void connect() {
            call.connectionsToCancel.add(connection);
            try {
                connection.connect(chain.connectTimeoutMillis, chain.readTimeoutMillis, chain.writeTimeoutMillis,
                        client.pingIntervalMillis, client.retryOnConnectionFailure, call, eventListener);
            } catch (IOException e) {
                failure = e;
            } finally {
                call.connectionsToCancel.remove(connection);
                complete = true;
            }
        }

        public RealConnection handleSuccess() {
            call.client.routeDatabase.connected(connection.route());

            ReusePlan pooled3 = planReusePooledConnection(connection, routes);
            if (pooled3 != null) return pooled3.connection;

            synchronized (connection) {
                connectionPool.put(connection);
                call.acquireConnectionNoEvents(connection);
            }

            eventListener.connectionAcquired(call, connection);
            return connection;
        }
    }
}