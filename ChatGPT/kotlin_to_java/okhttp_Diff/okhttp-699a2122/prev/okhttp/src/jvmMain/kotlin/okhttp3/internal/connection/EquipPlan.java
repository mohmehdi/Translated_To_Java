package okhttp3.internal.connection;

import java.io.InterruptedIOException;
import java.net.ProtocolException;
import java.net.UnknownServiceException;
import java.security.cert.CertificateException;
import java.util.Objects;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import okhttp3.ConnectionSpec;
import okhttp3.Request;
import okio.IOException;

internal class EquipPlan {
    public final int attempt;
    public final Request tunnelRequest;
    public final int connectionSpecIndex;
    public final boolean isTlsFallback;

    public EquipPlan(int attempt, Request tunnelRequest, int connectionSpecIndex, boolean isTlsFallback) {
        this.attempt = attempt;
        this.tunnelRequest = tunnelRequest;
        this.connectionSpecIndex = connectionSpecIndex;
        this.isTlsFallback = isTlsFallback;
    }

    public EquipPlan withCurrentOrInitialConnectionSpec(List<ConnectionSpec> connectionSpecs, SSLSocket sslSocket) throws IOException {
        if (connectionSpecIndex != -1) return this;
        EquipPlan nextPlan = nextConnectionSpec(connectionSpecs, sslSocket);
        if (nextPlan != null) {
            return nextPlan;
        } else {
            throw new UnknownServiceException(
                "Unable to find acceptable protocols." +
                " isFallback=" + isTlsFallback +
                ", modes=" + connectionSpecs +
                ", supported protocols=" + Arrays.toString(sslSocket.getEnabledProtocols())
            );
        }
    }

    public EquipPlan nextConnectionSpec(List<ConnectionSpec> connectionSpecs, SSLSocket sslSocket) {
        for (int i = connectionSpecIndex + 1; i < connectionSpecs.size(); i++) {
            if (connectionSpecs.get(i).isCompatible(sslSocket)) {
                return new EquipPlan(i, (connectionSpecIndex != -1), isTlsFallback);
            }
        }
        return null;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + attempt;
        result = 31 * result + Objects.hashCode(tunnelRequest);
        result = 31 * result + connectionSpecIndex;
        result = 31 * result + (isTlsFallback ? 0 : 1);
        return result;
    }

    public boolean retryTlsHandshake(IOException e) {
        if (e instanceof ProtocolException) {
            return false;
        } else if (e instanceof InterruptedIOException) {
            return false;
        } else if (e instanceof SSLHandshakeException && e.getCause() instanceof CertificateException) {
            return false;
        } else if (e instanceof SSLPeerUnverifiedException) {
            return false;
        } else if (e instanceof SSLException) {
            return true;
        } else {
            return false;
        }
    }
}
