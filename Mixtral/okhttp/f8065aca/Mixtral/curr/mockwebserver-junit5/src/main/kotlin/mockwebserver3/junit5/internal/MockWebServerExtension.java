
package mockwebserver3.junit5.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import mockwebserver3.MockWebServer;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.api.extension.ExtensionContext.Store;

public class MockWebServerExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback, AfterEachCallback, ParameterResolver {
    private static final Logger logger = Logger.getLogger(MockWebServerExtension.class.getName());
    private static final ExtensionContext.Namespace namespace = ExtensionContext.Namespace.create(MockWebServerExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        context.getStore(namespace).getOrComputeIfAbsent("resource", key -> new Resource(), Resource.class).startAll();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        context.getStore(namespace).getOrComputeIfAbsent("resource", key -> new Resource(), Resource.class).startAll();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        context.getStore(namespace).get("resource", Resource.class).shutdownAll();
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        context.getStore(namespace).get("resource", Resource.class).shutdownAll();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == MockWebServer.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return context.getStore(namespace).getOrComputeIfAbsent("resource", key -> new Resource(), Resource.class).newServer();
    }

    private static class Resource {
        private List<MockWebServer> servers = new ArrayList<>();
        private boolean started = false;

        public MockWebServer newServer() {
            MockWebServer result = new MockWebServer();
            if (started) {
                result.start();
            }
            servers.add(result);
            return result;
        }

        public void startAll() {
            started = true;
            for (MockWebServer server : servers) {
                server.start();
            }
        }

        public void shutdownAll() {
            try {
                for (MockWebServer server : servers) {
                    server.shutdown();
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "MockWebServer shutdown failed", e);
            }
        }
    }
}