

package okhttp3.internal.http;

import java.io.IOException;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.internal.connection.RealCall;
import okio.Sink;
import okio.Source;

public interface ExchangeCodec {
  
  Carrier carrier();

  
  @Throws(IOException.class)
  Sink createRequestBody(Request request, long contentLength) throws IOException;

  
  @Throws(IOException.class)
  void writeRequestHeaders(Request request) throws IOException;

  
  @Throws(IOException.class)
  void flushRequest() throws IOException;

  
  @Throws(IOException.class)
  void finishRequest() throws IOException;

  
  @Throws(IOException.class)
  Response.Builder readResponseHeaders(boolean expectContinue) throws IOException;

  @Throws(IOException.class)
  long reportedContentLength(Response response) throws IOException;

  @Throws(IOException.class)
  Source openResponseBodySource(Response response) throws IOException;

  
  Headers trailers() throws IOException;

  
  void cancel();

  
  public static class Carrier {
    public Route route() {
      return null;
    }

    public void trackFailure(RealCall call, IOException e) {
    }

    public void noNewExchanges() {
    }

    public void cancel() {
    }
  }

  public static final int DISCARD_STREAM_TIMEOUT_MILLIS = 100;
}