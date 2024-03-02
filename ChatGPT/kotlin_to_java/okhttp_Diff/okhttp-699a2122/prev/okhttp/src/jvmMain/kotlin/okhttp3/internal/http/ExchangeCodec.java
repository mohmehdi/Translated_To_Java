package okhttp3.internal.http;

import java.io.IOException;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.connection.RealConnection;
import okio.Sink;
import okio.Source;

public interface ExchangeCodec {

  RealConnection connection;

  Sink createRequestBody(Request request, long contentLength) throws IOException;

  void writeRequestHeaders(Request request) throws IOException;

  void flushRequest() throws IOException;

  void finishRequest() throws IOException;

  Response.Builder readResponseHeaders(boolean expectContinue) throws IOException;

  long reportedContentLength(Response response) throws IOException;

  Source openResponseBodySource(Response response) throws IOException;

  Headers trailers() throws IOException;

  void cancel();

  public static final int DISCARD_STREAM_TIMEOUT_MILLIS = 100;
  
}