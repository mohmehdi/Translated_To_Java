
package okhttp3.internal.io;

import okhttp3.TestUtil;
import okio.Buffer;
import okio.ForwardingSink;
import okio.ForwardingSource;
import okio.Sink;
import okio.Source;
import org.junit.jupiter.api.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

public class InMemoryFileSystem implements FileSystem, TestRule, AfterEachCallback {
    private final Map<File, Buffer> files = new HashMap<>();
    private final IdentityHashMap<Source, File> openSources = new IdentityHashMap<>();
    private final IdentityHashMap<Sink, File> openSinks = new IdentityHashMap<>();

    @Override
    public void afterEach(ExtensionContext context) {
        ensureResourcesClosed();
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                base.evaluate();
                ensureResourcesClosed();
            }
        };
    }

    public void ensureResourcesClosed() {
        List<String> openResources = new ArrayList<>();
        for (File file : openSources.values()) {
            openResources.add("Source for " + file.getAbsolutePath());
        }
        for (File file : openSinks.values()) {
            openResources.add("Sink for " + file.getAbsolutePath());
        }
        Objects.requireNonNull(openResources);
        if (!openResources.isEmpty()) {
            throw new IllegalStateException("Resources acquired but not closed:\n * " +
                    String.join("\n * ", openResources));
        }
    }

    @Override
    public Source source(File file) throws FileNotFoundException {
        Buffer result = files.get(file);
        if (result == null) {
            throw new FileNotFoundException();
        }
        Source source = result.clone();
        openSources.put(source, file);
        return new ForwardingSource(source) {
            @Override
            public void close() throws IOException {
                openSources.remove(source);
                super.close();
            }
        };
    }

    public void sink(File file) throws FileNotFoundException {
        sink(file, false);
    }
    @Override
    public Sink appendingSink(File file) throws FileNotFoundException {
        return sink(file, true);
    }
    @Override
    public Sink sink(File file, boolean appending) throws FileNotFoundException {
        Buffer result = null;
        if (appending) {
            result = files.get(file);
        }
        if (result == null) {
            result = new Buffer();
        }
        files.put(file, result);
        Sink sink = result;
        openSinks.put(sink, file);
        return new ForwardingSink(sink) {
            @Override
            public void close() throws IOException {
                openSinks.remove(sink);
                super.close();
            }
        };
    }

    @Override
    public void delete(File file) throws IOException {
        files.remove(file);
    }

    @Override
    public boolean exists(File file) {
        return files.containsKey(file);
    }

    @Override
    public long size(File file) {
        Buffer buffer = files.get(file);
        return buffer != null ? buffer.size() : 0;
    }

    @Override
    public void rename(File from, File to) throws IOException {
        Objects.requireNonNull(files.put(to, files.remove(from)));
    }

    @Override
    public void deleteContents(File directory) throws IOException {
        Iterator<File> i = files.keySet().iterator();
        while (i.hasNext()) {
            File file = i.next();
            if (TestUtil.isDescendentOf(directory, file)) {
                i.remove();
            }
        }
    }

    @Override
    public String toString() {
        return "InMemoryFileSystem";
    }
}