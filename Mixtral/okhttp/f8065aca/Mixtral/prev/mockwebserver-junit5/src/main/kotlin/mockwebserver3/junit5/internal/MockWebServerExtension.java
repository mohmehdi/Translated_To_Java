
package mockwebserver3.junit5.internal;

import mockwebserver3.MockWebServer;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import org.junit.jupiter.api.extension.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

public class MockWebServerExtension implements BeforeEachCallback, ParameterResolver {

    private static class Resource implements ExtensionContext.Store.CloseableResource {
        private final List<MockWebServer> servers = new ArrayList<>();
        private boolean started;

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

        @Override
        public void close() throws IOException {
            for (MockWebServer server : servers) {
                server.shutdown();
            }
        }
    }

    @Override
    @IgnoreJRERequirement
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == MockWebServer.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return extensionContext.getStore(GLOBAL).getOrComputeIfAbsent(Resource.class, key -> {
            Resource resource = new Resource();
            extensionContext.getStore(GLOBAL).put(key, resource);
            return resource;
        }).newServer();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        extensionContext.getStore(GLOBAL).get(Resource.class, Object.class).startAll();
    }

    private static final Logger logger = Logger.getLogger(MockWebServerExtension.class.getName());
}