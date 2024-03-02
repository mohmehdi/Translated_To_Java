
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
  
  Carrier carrier;

  
  void createRequestBody(Request request, long contentLength) throws IOException;

  
  void writeRequestHeaders(Request request) throws IOException;

  
  void flushRequest() throws IOException;

  
  void finishRequest() throws IOException;

  
  Response.Builder readResponseHeaders(boolean expectContinue) throws IOException;

  long reportedContentLength(Response response) throws IOException;

  Source openResponseBodySource(Response response) throws IOException;

  
  Headers trailers() throws IOException;

  
  void cancel();

  
  interface Carrier {
    Route route;
    void trackFailure(RealCall call, IOException e);
    void noNewExchanges();
    void cancel();
  }

  static final int DISCARD_STREAM_TIMEOUT_MILLIS = 100;
}