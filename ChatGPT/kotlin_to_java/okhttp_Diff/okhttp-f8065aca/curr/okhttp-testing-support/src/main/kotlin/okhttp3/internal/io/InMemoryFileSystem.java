package okhttp3.internal.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.IdentityHashMap;
import okhttp3.TestUtil;
import okio.Buffer;
import okio.ForwardingSink;
import okio.ForwardingSource;
import okio.Sink;
import okio.Source;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class InMemoryFileSystem implements FileSystem, TestRule, AfterEachCallback {
  private final IdentityHashMap<Source, File> openSources = new IdentityHashMap<>();
  private final IdentityHashMap<Sink, File> openSinks = new IdentityHashMap<>();
  public final HashMap<File, Buffer> files = new HashMap<>();

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
      openResources.add("Source for " + file);
    }
    for (File file : openSinks.values()) {
      openResources.add("Sink for " + file);
    }
    if (!openResources.isEmpty()) {
      throw new IllegalStateException("Resources acquired but not closed:\n * " + TextUtils.join("\n * ", openResources));
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

  @Override
  public Sink sink(File file) throws FileNotFoundException {
    return sink(file, false);
  }

  @Override
  public Sink appendingSink(File file) throws FileNotFoundException {
    return sink(file, true);
  }

  private Sink sink(File file, boolean appending) throws FileNotFoundException {
    Buffer result = appending ? files.get(file) : null;
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
    return buffer != null ? buffer.size() : 0L;
  }

  @Override
  public void rename(File from, File to) throws IOException {
    Buffer buffer = files.remove(from);
    if (buffer == null) {
      throw new FileNotFoundException();
    }
    files.put(to, buffer);
  }

  @Override
  public void deleteContents(File directory) throws IOException {
    Iterator<File> iterator = files.keySet().iterator();
    while (iterator.hasNext()) {
      File file = iterator.next();
      if (TestUtil.isDescendentOf(file, directory)) {
        iterator.remove();
      }
    }
  }

  @Override
  public String toString() {
    return "InMemoryFileSystem";
  }
}