package okhttp3.internal.connection;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClientTestRule;
import okhttp3.TestValueFactory;
import okhttp3.TlsVersion;
import okhttp3.tls.internal.TlsUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import static org.assertj.core.api.Assertions.assertThat;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.List;

@ExtendWith(OkHttpClientTestRule.class)
class RetryConnectionTest {
  private TestValueFactory factory = new TestValueFactory();
  private SSLSocketFactory handshakeCertificates = TlsUtil.localhost();
  private SSLHandshakeException retryableException = new SSLHandshakeException("Simulated handshake exception");

  @RegisterExtension
  OkHttpClientTestRule clientTestRule = new OkHttpClientTestRule();

  private okhttp3.OkHttpClient client = clientTestRule.newClient();

  @AfterEach
  void tearDown() {
    factory.close();
  }

  @Test
  void nonRetryableIOException() {
    IOException exception = new IOException("Non-handshake exception");
    assertThat(retryTlsHandshake(exception)).isFalse();
  }

  @Test
  void nonRetryableSSLHandshakeException() {
    SSLHandshakeException exception = new SSLHandshakeException("Certificate handshake exception") {
      @Override
      public Throwable initCause(Throwable cause) {
        if (cause != null) {
          initCause();
          cause.initCause(new CertificateException());
        }
        return this;
      }
    };
    assertThat(retryTlsHandshake(exception)).isFalse();
  }

  @Test
  void retryableSSLHandshakeException() {
    assertThat(retryTlsHandshake(retryableException)).isTrue();
  }

  @Test
  void someFallbacksSupported() {
    ConnectionSpec.Builder sslV3 = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.SSL_3_0)
      .build();
    RoutePlanner routePlanner = factory.newRoutePlanner(client);
    Route route = factory.newRoute();
    List connectionSpecs = Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, sslV3);
    SSLSocket socket = createSocketWithEnabledProtocols(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0);

    ConnectionSpecAttempt attempt0 = routePlanner.planConnectToRoute(route)
      .planWithCurrentOrInitialConnectionSpec(connectionSpecs, socket);
    assertThat(attempt0.isTlsFallback()).isFalse();
    connectionSpecs.get(attempt0.connectionSpecIndex()).apply(socket, attempt0.isTlsFallback());
    assertEnabledProtocols(socket, TlsVersion.TLS_1_2);
    ConnectionSpecAttempt attempt1 = attempt0.nextConnectionSpec(connectionSpecs, socket);
    assertThat(attempt1).isNotNull();
    assertThat(attempt1.isTlsFallback()).isTrue();
    socket.close();

    socket = createSocketWithEnabledProtocols(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0);
    connectionSpecs.get(attempt1.connectionSpecIndex()).apply(socket, attempt1.isTlsFallback());
    assertEnabledProtocols(socket, TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0);
    ConnectionSpecAttempt attempt2 = attempt1.nextConnectionSpec(connectionSpecs, socket);
    assertThat(attempt2).isNull();
    socket.close();
  }

  private SSLSocket createSocketWithEnabledProtocols(TlsVersion...tlsVersions) {
    SSLSocket sslSocket = (handshakeCertificates.createSocket() instanceof SSLSocket) ? (SSLSocket) handshakeCertificates.createSocket() : null;
    if (sslSocket != null) {
      sslSocket.setEnabledProtocols(javaNames(tlsVersions));
    }
    return sslSocket;
  }

  private void assertEnabledProtocols(SSLSocket socket, TlsVersion...required) {
    String[] requiredNames = new String[required.length];
    for (int i = 0; i < required.length; i++) {
      requiredNames[i] = required[i].javaName();
    }
    assertThat(socket.getEnabledProtocols())
      .containsExactlyInAnyOrder(requiredNames);
  }

  private String[] javaNames(TlsVersion...tlsVersions) {
    String[] names = new String[tlsVersions.length];
    for (int i = 0; i < tlsVersions.length; i++) {
      names[i] = tlsVersions[i].javaName();
    }
    return names;
  }
}