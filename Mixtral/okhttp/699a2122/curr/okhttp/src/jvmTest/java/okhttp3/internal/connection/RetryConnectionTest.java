package okhttp3.internal.connection;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClientTestRule;
import okhttp3.TestValueFactory;
import okhttp3.TlsVersion;
import okhttp3.tls.internal.TlsUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RetryConnectionTest {
    private final TestValueFactory factory = new TestValueFactory();
    private final X509Certificate[] handshakeCertificates = TlsUtil.localhost();
    private final SSLHandshakeException retryableException = new SSLHandshakeException("Simulated handshake exception");

    @org.junit.jupiter.api.extension.RegisterExtension
    public final OkHttpClientTestRule clientTestRule = new OkHttpClientTestRule();

    private OkHttpClient client;

    @AfterEach
    public void tearDown() {
        factory.close();
    }

    @Test
    public void nonRetryableIOException() {
        final IOException exception = new IOException("Non-handshake exception");
        Assertions.assertFalse(retryTlsHandshake(exception));
    }

    @Test
    public void nonRetryableSSLHandshakeException() {
        final SSLHandshakeException exception = new SSLHandshakeException("Certificate handshake exception") {
            {
                initCause(new CertificateException());
            }
        };
        Assertions.assertFalse(retryTlsHandshake(exception));
    }

    @Test
    public void retryableSSLHandshakeException() {
        Assertions.assertTrue(retryTlsHandshake(retryableException));
    }

    @Test
    public void someFallbacksSupported() {
        final ConnectionSpec.Builder sslV3Builder = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.SSL_3_0);
        final ConnectionSpec sslV3 = sslV3Builder.build();
        final RoutePlanner routePlanner = factory.newRoutePlanner(client);
        final Route route = factory.newRoute();
        final List<ConnectionSpec> connectionSpecs = Arrays.asList(ConnectionSpec.MODERN_TLS,
                ConnectionSpec.COMPATIBLE_TLS, sslV3);
        final String[] enabledSocketTlsVersions = {
                "TLSv1.2",
                "TLSv1.1",
                "TLSv1.0"
        };
        final SSLSocket socket = createSocketWithEnabledProtocols(enabledSocketTlsVersions);

        // MODERN_TLS is used here.
        final ConnectionSpec.Selection negotiate0 = routePlanner.planConnectToRoute(route)
                .planWithCurrentOrInitialConnectionSpec(connectionSpecs, socket);
        Assertions.assertFalse(negotiate0.isTlsFallback());
        connectionSpecs.get(negotiate0.connectionSpecIndex()).apply(socket, negotiate0.isTlsFallback());
        assertEnabledProtocols(socket, "TLSv1.2");
        final ConnectionSpec.Selection negotiate1 = negotiate0.nextConnectionSpec(connectionSpecs, socket);
        Assertions.assertNotNull(negotiate1);
        Assertions.assertTrue(negotiate1.isTlsFallback());
        socket.close();

        // COMPATIBLE_TLS is used here.
        final SSLSocket socket2 = createSocketWithEnabledProtocols(enabledSocketTlsVersions);
        connectionSpecs.get(negotiate1.connectionSpecIndex()).apply(socket2, negotiate1.isTlsFallback());
        assertEnabledProtocols(socket2, "TLSv1.2", "TLSv1.1", "TLSv1.0");
        final ConnectionSpec.Selection negotiate2 = negotiate1.nextConnectionSpec(connectionSpecs, socket2);
        Assertions.assertNull(negotiate2);
        socket2.close();

        // sslV3 is not used because SSLv3 is not enabled on the socket.
    }

    private SSLSocket createSocketWithEnabledProtocols(final String... tlsVersions) {
        final SSLSocket socket = handshakeCertificates.sslSocketFactory().createSocket();
        Objects.requireNonNull(socket);
        socket.setEnabledProtocols(tlsVersions);
        return socket;
    }

    private void assertEnabledProtocols(final SSLSocket socket, final String... required) {
        Assertions.assertArrayEquals(required, javaNames(socket.getEnabledProtocols()));
    }

    private String[] javaNames(final String[] tlsVersions) {
        return Arrays.stream(tlsVersions)
                .map(s -> s.replaceAll("TLS", ""))
                .map(String::toUpperCase)
                .toArray(String[]::new);
    }
}