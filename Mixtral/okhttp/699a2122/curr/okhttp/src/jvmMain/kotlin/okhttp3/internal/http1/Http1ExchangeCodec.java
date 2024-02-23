

package okhttp3.internal.http1;

import java.io.EOFException;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.concurrent.TimeUnit;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.EmptyHeaders;
import okhttp3.internal.http.ExchangeCodec;
import okhttp3.internal.http.RequestLine;
import okhttp3.internal.http.StatusLine;
import okhttp3.internal.http.StatusLine.Companion;
import okhttp3.internal.http.promisesBody;
import okhttp3.internal.http.receiveHeaders;
import okhttp3.internal.skipAll;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ForwardingTimeout;
import okio.Sink;
import okio.Source;
import okio.Timeout;

public class Http1ExchangeCodec implements ExchangeCodec {
  private static final long NO_CHUNK_YET = -1;

  private static final int STATE_IDLE = 0;
  private static final int STATE_OPEN_REQUEST_BODY = 1;
  private static final int STATE_WRITING_REQUEST_BODY = 2;
  private static final int STATE_READ_RESPONSE_HEADERS = 3;
  private static final int STATE_OPEN_RESPONSE_BODY = 4;
  private static final int STATE_READING_RESPONSE_BODY = 5;
  private static final int STATE_CLOSED = 6;

  private OkHttpClient client;
  private ExchangeCodec.Carrier carrier;
  private BufferedSource source;
  private BufferedSink sink;
  private int state = STATE_IDLE;
  private HeadersReader headersReader;

  public Http1ExchangeCodec(OkHttpClient client, ExchangeCodec.Carrier carrier, BufferedSource source, BufferedSink sink) {
    this.client = client;
    this.carrier = carrier;
    this.source = source;
    this.sink = sink;
    this.headersReader = new HeadersReader(source);
  }

  @Override
  public Sink createRequestBody(Request request, long contentLength) throws IOException {
    if (request.body() != null && request.body().isDuplex()) {
      throw new ProtocolException("Duplex connections are not supported for HTTP/1");
    }

    if (request.isChunked()) {
      return newChunkedSink();
    } else if (contentLength != -1) {
      return newKnownLengthSink(contentLength);
    } else {
      throw new IllegalStateException(
          "Cannot stream a request body without chunked encoding or a known content length!");
    }
  }

  @Override
  public void cancel() {
    carrier.cancel();
  }

  @Override
  public void writeRequestHeaders(Request request) throws IOException {
    RequestLine requestLine = RequestLine.get(request, carrier.route().proxy().type());
    writeRequest(request.headers(), requestLine);
  }

  @Override
  public long reportedContentLength(Response response) {
    if (!response.promisesBody()) {
      return 0;
    }
    if (response.isChunked()) {
      return -1;
    } else {
      return response.headers().contentLength();
    }
  }

  @Override
  public Source openResponseBodySource(Response response) {
    if (!response.promisesBody()) {
      return newFixedLengthSource(0);
    }
    if (response.isChunked()) {
      return newChunkedSource(response.request().url());
    } else {
      long contentLength = response.headers().contentLength();
      if (contentLength != -1) {
        return newFixedLengthSource(contentLength);
      } else {
        return newUnknownLengthSource();
      }
    }
  }

  @Override
  public Headers trailers() {
    if (state != STATE_CLOSED) {
      throw new IllegalStateException("too early; can't read the trailers yet");
    }
    return trailers != null ? trailers : EmptyHeaders.INSTANCE;
  }

  @Override
  public void flushRequest() {
    sink.flush();
  }

  @Override
  public void finishRequest() {
    sink.flush();
  }

  public void writeRequest(Headers headers, String requestLine) throws IOException {
    if (state != STATE_IDLE) {
      throw new IllegalStateException("state: " + state);
    }
    sink.writeUtf8(requestLine).writeUtf8("\r\n");
    for (int i = 0; i < headers.size(); i++) {
      sink.writeUtf8(headers.name(i))
          .writeUtf8(": ")
          .writeUtf8(headers.value(i))
          .writeUtf8("\r\n");
    }
    sink.writeUtf8("\r\n");
    state = STATE_OPEN_REQUEST_BODY;
  }

  @Override
  public Response.Builder readResponseHeaders(boolean expectContinue) throws IOException {
    if (state != STATE_OPEN_REQUEST_BODY && state != STATE_WRITING_REQUEST_BODY && state != STATE_READ_RESPONSE_HEADERS) {
      throw new IllegalStateException("state: " + state);
    }

    try {
      StatusLine statusLine = StatusLine.parse(headersReader.readLine());

      Response.Builder responseBuilder = new Response.Builder()
          .protocol(statusLine.protocol())
          .code(statusLine.code())
          .message(statusLine.message())
          .headers(headersReader.readHeaders());

      return when {
        expectContinue && statusLine.code() == Companion.HTTP_CONTINUE -> {
          null;
        }
        statusLine.code() == Companion.HTTP_CONTINUE -> {
          state = STATE_READ_RESPONSE_HEADERS;
          responseBuilder;
        }
        else -> {
          state = STATE_OPEN_RESPONSE_BODY;
          responseBuilder;
        }
      };
    } catch (EOFException e) {
      String address = carrier.route().address().url().redact();
      throw new IOException("unexpected end of stream on " + address, e);
    }
  }

  private Sink newChunkedSink() {
    if (state != STATE_OPEN_REQUEST_BODY) {
      throw new IllegalStateException("state: " + state);
    }
    state = STATE_WRITING_REQUEST_BODY;
    return new ChunkedSink();
  }

private Sink newKnownLengthSink() {
    if (state != STATE_OPEN_REQUEST_BODY) {
        throw new IllegalStateException("state: " + state);
    }
    state = STATE_WRITING_REQUEST_BODY;
    return new KnownLengthSink();
}


  private Source newFixedLengthSource(long length) {
    if (state != STATE_OPEN_RESPONSE_BODY) {
      throw new IllegalStateException("state: " + state);
    }
    state = STATE_READING_RESPONSE_BODY;
    return new FixedLengthSource(length);
  }

  private Source newChunkedSource(HttpUrl url) {
    if (state != STATE_OPEN_RESPONSE_BODY) {
      throw new IllegalStateException("state: " + state);
    }
    state = STATE_READING_RESPONSE_BODY;
    return new ChunkedSource(url);
  }

  private Source newUnknownLengthSource() {
    if (state != STATE_OPEN_RESPONSE_BODY) {
      throw new IllegalStateException("state: " + state);
    }
    state = STATE_READING_RESPONSE_BODY;
    carrier.noNewExchanges();
    return new UnknownLengthSource();
  }

  private void detachTimeout(ForwardingTimeout timeout) {
    Timeout oldDelegate = timeout.delegate();
    timeout.setDelegate(Timeout.NONE);
    oldDelegate.clearDeadline();
    oldDelegate.clearTimeout();
  }

  public void skipConnectBody(Response response) throws IOException {
    long contentLength = response.headers().contentLength();
    if (contentLength == -1) return;
    Source body = newFixedLengthSource(contentLength);
    body.skipAll(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
    body.close();
  }

  private class KnownLengthSink extends Sink {
    private final ForwardingTimeout timeout;
    private boolean closed;

    public KnownLengthSink(long contentLength) {
      this.timeout = new ForwardingTimeout(sink.timeout());
      this.closed = false;
    }

    @Override
    public Timeout timeout() {
      return timeout;
    }

    @Override
    public void write(Buffer source, long byteCount) throws IOException {
      if (closed) throw new IllegalStateException("closed");
      sink.write(source, byteCount);
    }

    @Override
    public void flush() throws IOException {
      if (closed) return;
      sink.flush();
    }

    @Override
    public void close() throws IOException {
      if (closed) return;
      closed = true;
      detachTimeout(timeout);
      state = STATE_READ_RESPONSE_HEADERS;
    }
  }

  private class ChunkedSink extends Sink {
    private final ForwardingTimeout timeout;
    private boolean closed;

    public ChunkedSink() {
      this.timeout = new ForwardingTimeout(sink.timeout());
      this.closed = false;
    }

    @Override
    public Timeout timeout() {
      return timeout;
    }

    @Override
    public void write(Buffer source, long byteCount) throws IOException {
      if (closed) throw new IllegalStateException("closed");
      if (byteCount == 0) return;

      sink.writeHexadecimalUnsignedLong(byteCount);
      sink.writeUtf8("\r\n");
      sink.write(source, byteCount);
      sink.writeUtf8("\r\n");
    }

    @Override
    public void flush() throws IOException {
      if (closed) return;
      sink.flush();
    }

    @Override
    public void close() throws IOException {
      if (closed) return;
      closed = true;
      sink.writeUtf8("0\r\n\r\n");
      detachTimeout(timeout);
      state = STATE_READ_RESPONSE_HEADERS;
    }
  }

  private abstract class AbstractSource extends Source {
    protected final ForwardingTimeout timeout;
    protected boolean closed;

    public AbstractSource() {
      this.timeout = new ForwardingTimeout(source.timeout());
      this.closed = false;
    }

    @Override
    public Timeout timeout() {
      return timeout;
    }

    @Override
    public long read(Buffer sink, long byteCount) throws IOException {
      if (closed) throw new IllegalStateException("closed");
      if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);

      try {
        return source.read(sink, byteCount);
      } catch (IOException e) {
        carrier.noNewExchanges();
        responseBodyComplete();
        throw e;
      }
    }

    protected void responseBodyComplete() {
      if (state == STATE_CLOSED) return;
      if (state != STATE_READING_RESPONSE_BODY) throw new IllegalStateException("state: " + state);

      detachTimeout(timeout);

      state = STATE_CLOSED;
    }
  }

  private class FixedLengthSource extends AbstractSource {
    private long bytesRemaining;

    public FixedLengthSource(long contentLength) {
      super();
      this.bytesRemaining = contentLength;
      if (bytesRemaining == 0) {
        responseBodyComplete();
      }
    }

    @Override
    public long read(Buffer sink, long byteCount) throws IOException {
      if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
      if (closed) throw new IllegalStateException("closed");
      if (bytesRemaining == 0) return -1;

      long read = super.read(sink, Math.min(bytesRemaining, byteCount));
      if (read == -1) {
        carrier.noNewExchanges(); 
        IOException e = new ProtocolException("unexpected end of stream");
        responseBodyComplete();
        throw e;
      }

      bytesRemaining -= read;
      if (bytesRemaining == 0) {
        responseBodyComplete();
      }
      return read;
    }

    @Override
    public void close() throws IOException {
      if (closed) return;

      if (bytesRemaining != 0 && !discard(ExchangeCodec.DISCARD_STREAM_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
        carrier.noNewExchanges(); 
        responseBodyComplete();
      }

      closed = true;
    }
  }

  private class ChunkedSource extends AbstractSource {
    private long bytesRemainingInChunk = NO_CHUNK_YET;
    private boolean hasMoreChunks;

    public ChunkedSource(HttpUrl url) {
      super();
      this.bytesRemainingInChunk = NO_CHUNK_YET;
      this.hasMoreChunks = true;
    }

    @Override
    public long read(Buffer sink, long byteCount) throws IOException {
      if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
      if (!hasMoreChunks) return -1;

      if (bytesRemainingInChunk == 0 || bytesRemainingInChunk == NO_CHUNK_YET) {
        readChunkSize();
        if (!hasMoreChunks) return -1;
      }

      long read = super.read(sink, Math.min(byteCount, bytesRemainingInChunk));
      if (read == -1) {
        carrier.noNewExchanges(); 
        IOException e = new ProtocolException("unexpected end of stream");
        responseBodyComplete();
        throw e;
      }
      bytesRemainingInChunk -= read;
      return read;
    }

    private void readChunkSize() throws IOException {
      if (bytesRemainingInChunk != NO_CHUNK_YET) {
        source.readUtf8LineStrict();
      }
      try {
        bytesRemainingInChunk = source.readHexadecimalUnsignedLong();
        String extensions = source.readUtf8LineStrict().trim();
        if (bytesRemainingInChunk < 0 || (!extensions.isEmpty() && !extensions.startsWith(";"))) {
          throw new ProtocolException("expected chunk size and optional extensions" +
              " but was \"" + bytesRemainingInChunk + extensions + '"');
        }
      } catch (NumberFormatException e) {
        throw new ProtocolException(e.getMessage());
      }

      if (bytesRemainingInChunk == 0) {
        hasMoreChunks = false;
        trailers = headersReader.readHeaders();
        client.cookieJar().receiveHeaders(url, trailers);
        responseBodyComplete();
      }
    }

    @Override
    public void close() throws IOException {
      if (closed) return;
      if (hasMoreChunks && !discard(ExchangeCodec.DISCARD_STREAM_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
        carrier.noNewExchanges(); 
        responseBodyComplete();
      }
      closed = true;
    }
  }

  private class UnknownLengthSource extends AbstractSource {
    private boolean inputExhausted;

    @Override
    public long read(Buffer sink, long byteCount) throws IOException {
      if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
      if (closed) throw new IllegalStateException("closed");
      if (inputExhausted) return -1;

      long read = super.read(sink, byteCount);
      if (read == -1) {
        inputExhausted = true;
        responseBodyComplete();
        return -1;
      }
      return read;
    }

    @Override
    public void close() throws IOException {
      if (closed) return;
      if (!inputExhausted) {
        responseBodyComplete();
      }
      closed = true;
    }
  }
}