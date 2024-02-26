

package okhttp3.internal.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Response.Builder;
import okhttp3.internal.connection.RealConnection;
import okio.Buffer;
import okio.Sink;
import okio.Source;

public interface ExchangeCodec {

  RealConnection connection();

  void writeRequestHeaders(Request request) throws IOException;

  void flushRequest() throws IOException;

  void finishRequest() throws IOException;

  Builder readResponseHeaders(boolean expectContinue) throws IOException;

  long reportedContentLength(Response response) throws IOException;

  Source openResponseBodySource(Response response) throws IOException;

  Headers trailers() throws IOException;

  void cancel();

  static final int DISCARD_STREAM_TIMEOUT_MILLIS = 100;

  Sink createRequestBody(Request request, long contentLength) throws IOException;
}