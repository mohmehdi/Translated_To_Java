package okhttp3.internal.connection;

import java.io.IOException;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import okhttp3.ConnectionSpec;
import okhttp3.TlsVersion;
import okhttp3.tls.internal.TlsUtil.localhost;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class EquipPlanTest {
  private final HandshakeCertificates handshakeCertificates = localhost();
  private final SSLHandshakeException retryableException = new SSLHandshakeException(
    "Simulated handshake exception"
  );

  @Test
  public void nonRetryableIOException() {
    IOException exception = new IOException("Non-handshake exception");
    Assertions.assertThat(retryTlsHandshake(exception)).isFalse();
  }

  @Test
  public void nonRetryableSSLHandshakeException() {
    SSLHandshakeException exception = new SSLHandshakeException("Certificate handshake exception");
    exception.initCause(new CertificateException());
    Assertions.assertThat(retryTlsHandshake(exception)).isFalse();
  }

  @Test
  public void retryableSSLHandshakeException() {
    Assertions.assertThat(retryTlsHandshake(retryableException)).isTrue();
  }

  @Test
  public void someFallbacksSupported() {
    ConnectionSpec sslV3 = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.SSL_3_0)
      .build();
    EquipPlan equipPlan = new EquipPlan(-1);
    List<ConnectionSpec> connectionSpecs = Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, sslV3);
    TlsVersion[] enabledSocketTlsVersions = {
      TlsVersion.TLS_1_2,
      TlsVersion.TLS_1_1,
      TlsVersion.TLS_1_0
    };
    SSLSocket socket = createSocketWithEnabledProtocols(enabledSocketTlsVersions);

    
    Attempt attempt0 = equipPlan.withCurrentOrInitialConnectionSpec(connectionSpecs, socket);
    Assertions.assertThat(attempt0.isTlsFallback).isFalse();
    connectionSpecs.get(attempt0.connectionSpecIndex).apply(socket, attempt0.isTlsFallback);
    assertEnabledProtocols(socket, TlsVersion.TLS_1_2);
    Attempt attempt1 = attempt0.nextConnectionSpec(connectionSpecs, socket);
    Assertions.assertThat(attempt1).isNotNull();
    Assertions.assertThat(attempt1.isTlsFallback).isTrue();
    socket.close();

    
    socket = createSocketWithEnabledProtocols(enabledSocketTlsVersions);
    connectionSpecs.get(attempt1.connectionSpecIndex).apply(socket, attempt1.isTlsFallback);
    assertEnabledProtocols(socket, TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0);
    Attempt attempt2 = attempt1.nextConnectionSpec(connectionSpecs, socket);
    Assertions.assertThat(attempt2).isNull();
    socket.close();

    
  }

  private SSLSocket createSocketWithEnabledProtocols(TlsVersion... tlsVersions) {
    SSLSocket sslSocket = (SSLSocket) handshakeCertificates.sslSocketFactory().createSocket();
    sslSocket.setEnabledProtocols(javaNames(tlsVersions));
    return sslSocket;
  }

  private void assertEnabledProtocols(SSLSocket socket, TlsVersion... required) {
    Assertions.assertThat(socket.getEnabledProtocols())
      .containsExactlyInAnyOrder(javaNames(required));
  }

  private String[] javaNames(TlsVersion... tlsVersions) {
    List<String> names = new ArrayList<>();
    for (TlsVersion tlsVersion : tlsVersions) {
      names.add(tlsVersion.javaName());
    }
    return names.toArray(new String[0]);
  }
}