package mockwebserver3.junit5.internal;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import mockwebserver3.MockWebServer;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

public class MockWebServerExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback, AfterEachCallback, ParameterResolver {
    private Resource getResource(ExtensionContext context) {
        ExtensionContext.Store store = context.getStore(MockWebServerExtension.namespace);
        Resource result = (Resource) store.get(context.getUniqueId());
        if (result == null) {
            result = new Resource();
            store.put(context.getUniqueId(), result);
        }
        return result;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == MockWebServer.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return getResource(extensionContext).newServer();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        getResource(context).startAll();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        getResource(context).startAll();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        getResource(context).shutdownAll();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        getResource(context).shutdownAll();
    }

    private static class Resource {
        private List<MockWebServer> servers;
        private boolean started;

        public Resource() {
            servers = new ArrayList<>();
            started = false;
        }

        public MockWebServer newServer() {
            MockWebServer result = new MockWebServer();
            if (started) {
                try {
                    result.start();
                } catch (IOException e) {
                    Logger.getLogger(MockWebServerExtension.class.getName()).log(Level.WARNING, "Failed to start MockWebServer", e);
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
                    Logger.getLogger(MockWebServerExtension.class.getName()).log(Level.WARNING, "Failed to start MockWebServer", e);
                }
            }
        }

        public void shutdownAll() {
            for (MockWebServer server : servers) {
                server.shutdown();
            }
        }
    }

    @IgnoreJRERequirement
    private static Logger logger = Logger.getLogger(MockWebServerExtension.class.getName());
    private static ExtensionContext.Namespace namespace = ExtensionContext.Namespace.create(MockWebServerExtension.class);
}