
package okhttp3.internal.connection;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import okhttp3.ConnectionSpec;
import okhttp3.TlsVersion;
import okhttp3.tls.internal.TlsUtil;
import static okhttp3.tls.internal.TlsUtil.localhost;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class EquipPlanTest {
    private final X509Certificate[] handshakeCertificates = localhost();
    private final SSLHandshakeException retryableException = new SSLHandshakeException(
            "Simulated handshake exception"
    );

    @Test
    void nonRetryableIOException() {
        IOException exception = new IOException("Non-handshake exception");
        assertThat(retryTlsHandshake(exception)).isFalse();
    }

    @Test
    void nonRetryableSSLHandshakeException() {
        SSLHandshakeException exception = new SSLHandshakeException("Certificate handshake exception") {
            {
                initCause(new CertificateException());
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
        ConnectionSpec.Builder sslV3Builder = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS);
        sslV3Builder.tlsVersions(TlsVersion.SSL_3_0);
        ConnectionSpec sslV3 = sslV3Builder.build();
        EquipPlan equipPlan = new EquipPlan(connectionSpecIndex = -1);
        List<ConnectionSpec> connectionSpecs = Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, sslV3);
        SSLSocket socket = createSocketWithEnabledProtocols(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0);

        ConnectionSpec.Builder currentSpecBuilder = equipPlan.withCurrentOrInitialConnectionSpec(connectionSpecs, socket);
        boolean isTlsFallback = currentSpecBuilder.isTlsFallback;
        int connectionSpecIndex = currentSpecBuilder.connectionSpecIndex;
        ConnectionSpec currentSpec = connectionSpecs.get(connectionSpecIndex);
        currentSpec.configure(socket, isTlsFallback);
        assertEnabledProtocols(socket, TlsVersion.TLS_1_2);

        ConnectionSpec nextSpec = equipPlan.nextConnectionSpec(connectionSpecs, socket, currentSpec, isTlsFallback);
        assertThat(nextSpec).isNotNull();
        isTlsFallback = nextSpec.isTlsFallback;
        connectionSpecIndex = nextSpec.connectionSpecIndex;
        currentSpec = connectionSpecs.get(connectionSpecIndex);
        currentSpec.configure(socket, isTlsFallback);
        assertEnabledProtocols(socket, TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0);

        nextSpec = equipPlan.nextConnectionSpec(connectionSpecs, socket, currentSpec, isTlsFallback);
        assertThat(nextSpec).isNull();
        socket.close();
    }

    private SSLSocket createSocketWithEnabledProtocols(TlsVersion... tlsVersions) {
        SSLSocket sslSocket = (handshakeCertificates.get(0).getSocketFactory().createSocket());
        String[] enabledProtocols = new String[tlsVersions.length];
        for (int i = 0; i < tlsVersions.length; i++) {
            enabledProtocols[i] = tlsVersions[i].javaName();
        }
        sslSocket.setEnabledProtocols(enabledProtocols);
        return sslSocket;
    }

    private void assertEnabledProtocols(SSLSocket socket, TlsVersion... required) {
        String[] enabledProtocols = socket.getEnabledProtocols();
        List<String> enabledProtocolsList = Arrays.asList(enabledProtocols);
        List<String> requiredList = Arrays.asList(required);
        assertThat(enabledProtocolsList).containsAll(requiredList);
    }

    private String[] javaNames(TlsVersion... tlsVersions) {
    String[] javaNames = new String[tlsVersions.length];
    for (int i = 0; i < tlsVersions.length; i++) {
        javaNames[i] = tlsVersions[i].javaName();
    }
    return javaNames;
}
}