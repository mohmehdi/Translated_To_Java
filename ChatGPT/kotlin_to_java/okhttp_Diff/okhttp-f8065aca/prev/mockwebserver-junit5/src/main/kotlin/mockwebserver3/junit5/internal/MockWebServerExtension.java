package mockwebserver3.junit5.internal;

import mockwebserver3.MockWebServer;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MockWebServerExtension implements BeforeEachCallback, ParameterResolver {
    private Resource getResource(ExtensionContext extensionContext) {
        return extensionContext.getStore(ExtensionContext.Namespace.GLOBAL)
                .getOrComputeIfAbsent(Resource.class);
    }

    private class Resource implements ExtensionContext.Store.CloseableResource {
        private List<MockWebServer> servers;
        private boolean started;

        public Resource() {
            this.servers = new ArrayList<>();
            this.started = false;
        }

        public MockWebServer newServer() {
            MockWebServer result = new MockWebServer();
            if (started) {
                try {
                    result.start();
                } catch (IOException e) {
                    Logger.getLogger(MockWebServerExtension.class.getName())
                            .log(Level.WARNING, "Failed to start MockWebServer", e);
                }
            }
            servers.add(result);
            return result;
        }

        public void startAll() {
            started = true;
            for (MockWebServer server : servers) {
                try {
                    server.start();
                } catch (IOException e) {
                    Logger.getLogger(MockWebServerExtension.class.getName())
                            .log(Level.WARNING, "Failed to start MockWebServer", e);
                }
            }
        }

        @Override
        public void close() {
            for (MockWebServer server : servers) {
                server.shutdown();
            }
        }
    }

    @IgnoreJRERequirement
    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == MockWebServer.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return getResource(extensionContext).newServer();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        getResource(extensionContext).startAll();
    }
}