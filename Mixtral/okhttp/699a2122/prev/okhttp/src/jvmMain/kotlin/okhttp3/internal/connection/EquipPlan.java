package okhttp3.internal.connection;

import java.io.IOException;
import java.net.ProtocolException;
import java.net.UnknownServiceException;
import java.security.cert.CertificateException;
import java.util.Objects;
import java.util.List;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import okhttp3.ConnectionSpec;
import okhttp3.Request;
import okio.IOException;

public final class EquipPlan {
  private final int attempt;
  private final Request tunnelRequest;
  private final int connectionSpecIndex;
  private final boolean isTlsFallback;

  public EquipPlan(int attempt, Request tunnelRequest, int connectionSpecIndex, boolean isTlsFallback) {
    this.attempt = attempt;
    this.tunnelRequest = tunnelRequest;
    this.connectionSpecIndex = connectionSpecIndex;
    this.isTlsFallback = isTlsFallback;
  }

  public EquipPlan(int attempt, Request tunnelRequest) {
    this(attempt, tunnelRequest, -1, false);
  }

  public EquipPlan(int attempt) {
    this(attempt, null, -1, false);
  }

  public EquipPlan() {
    this(0, null, -1, false);
  }

  public EquipPlan withCurrentOrInitialConnectionSpec(List<ConnectionSpec> connectionSpecs, SSLSocket sslSocket) throws IOException {
    if (connectionSpecIndex != -1) return this;
    return nextConnectionSpec(connectionSpecs, sslSocket)
        != null
        ?: throw new UnknownServiceException(
            "Unable to find acceptable protocols." +
                " isFallback=" + isTlsFallback +
                ", modes=" + connectionSpecs +
                ", supported protocols=" + java.util.Arrays.toString(sslSocket.getEnabledProtocols()));
  }

  public EquipPlan nextConnectionSpec(List<ConnectionSpec> connectionSpecs, SSLSocket sslSocket) {
    for (int i = connectionSpecIndex + 1; i < connectionSpecs.size(); i++) {
      ConnectionSpec connectionSpec = connectionSpecs.get(i);
      if (connectionSpec.isCompatible(sslSocket)) {
        return new EquipPlan(attempt, tunnelRequest, i, true);
      }
    }
    return null;
  }



  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + attempt;
    result = 31 * result + (tunnelRequest != null ? tunnelRequest.hashCode() : 0);
    result = 31 * result + connectionSpecIndex;
    result = 31 * result + (isTlsFallback ? 0 : 1);
    return result;
  }
}

boolean retryTlsHandshake(IOException e) {
  if (e instanceof ProtocolException) {
    return false;
  }
  if (e instanceof InterruptedIOException) {
    return false;
  }
  if (e instanceof SSLHandshakeException
      && e.getCause() instanceof CertificateException) {
    return false;
  }
  if (e instanceof SSLPeerUnverifiedException) {
    return false;
  }
  if (e instanceof SSLException) {
    return true;
  }
  return false;
}