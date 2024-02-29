package okhttp3.internal.connection;

import java.io.IOException;
import java.net.ProtocolException;
import java.net.SocketException;
import okhttp3.EventListener;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.http.ExchangeCodec;
import okhttp3.internal.http.RealResponseBody;
import okhttp3.internal.ws.RealWebSocket;
import okio.Buffer;
import okio.ForwardingSink;
import okio.ForwardingSource;
import okio.Sink;
import okio.Source;

public class Exchange {
  
  private final RealCall call;
  private final EventListener eventListener;
  private final RoutePlanner finder;
  private final ExchangeCodec codec;
  
  private boolean isDuplex = false;
  private boolean hasFailure = false;
  
  private final RealConnection connection;
  
  public Exchange(RealCall call, EventListener eventListener, RoutePlanner finder, ExchangeCodec codec) {
    this.call = call;
    this.eventListener = eventListener;
    this.finder = finder;
    this.codec = codec;
    this.connection = codec.connection;
  }
  
  public boolean isCoalescedConnection() {
    return finder.address.url.host != connection.route().address.url.host;
  }
  
  public void writeRequestHeaders(Request request) throws IOException {
    try {
      eventListener.requestHeadersStart(call);
      codec.writeRequestHeaders(request);
      eventListener.requestHeadersEnd(call, request);
    } catch (IOException e) {
      eventListener.requestFailed(call, e);
      trackFailure(e);
      throw e;
    }
  }
  
  public Sink createRequestBody(Request request, boolean duplex) throws IOException {
    this.isDuplex = duplex;
    long contentLength = request.body().contentLength();
    eventListener.requestBodyStart(call);
    Sink rawRequestBody = codec.createRequestBody(request, contentLength);
    return new RequestBodySink(rawRequestBody, contentLength);
  }
  
  public void flushRequest() throws IOException {
    try {
      codec.flushRequest();
    } catch (IOException e) {
      eventListener.requestFailed(call, e);
      trackFailure(e);
      throw e;
    }
  }
  
  public void finishRequest() throws IOException {
    try {
      codec.finishRequest();
    } catch (IOException e) {
      eventListener.requestFailed(call, e);
      trackFailure(e);
      throw e;
    }
  }
  
  public void responseHeadersStart() {
    eventListener.responseHeadersStart(call);
  }
  
  public Response.Builder readResponseHeaders(boolean expectContinue) throws IOException {
    try {
      Response.Builder result = codec.readResponseHeaders(expectContinue);
      if (result != null) {
        result.initExchange(this);
      }
      return result;
    } catch (IOException e) {
      eventListener.responseFailed(call, e);
      trackFailure(e);
      throw e;
    }
  }
  
  public void responseHeadersEnd(Response response) {
    eventListener.responseHeadersEnd(call, response);
  }
  
  public ResponseBody openResponseBody(Response response) throws IOException {
    try {
      String contentType = response.header("Content-Type");
      long contentLength = codec.reportedContentLength(response);
      Source rawSource = codec.openResponseBodySource(response);
      Source source = new ResponseBodySource(rawSource, contentLength);
      return new RealResponseBody(contentType, contentLength, source.buffer());
    } catch (IOException e) {
      eventListener.responseFailed(call, e);
      trackFailure(e);
      throw e;
    }
  }
  
  public Headers trailers() throws IOException {
    return codec.trailers();
  }
  
  public RealWebSocket.Streams newWebSocketStreams() throws SocketException {
    call.timeoutEarlyExit();
    return codec.connection.newWebSocketStreams(this);
  }
  
  public void webSocketUpgradeFailed() {
    bodyComplete(-1L, true, true, null);
  }
  
  public void noNewExchangesOnConnection() {
    codec.connection.noNewExchanges();
  }
  
  public void cancel() {
    codec.cancel();
  }
  
  public void detachWithViolence() {
    codec.cancel();
    call.messageDone(this, true, true, null);
  }
  
  private void trackFailure(IOException e) {
    hasFailure = true;
    finder.trackFailure(e);
    codec.connection.trackFailure(call, e);
  }
  
  public <E extends IOException> E bodyComplete(long bytesRead, boolean responseDone, boolean requestDone, E e) {
    if (e != null) {
      trackFailure(e);
    }
    if (requestDone) {
      if (e != null) {
        eventListener.requestFailed(call, e);
      } else {
        eventListener.requestBodyEnd(call, bytesRead);
      }
    }
    if (responseDone) {
      if (e != null) {
        eventListener.responseFailed(call, e);
      } else {
        eventListener.responseBodyEnd(call, bytesRead);
      }
    }
    return call.messageDone(this, requestDone, responseDone, e);
  }
  
  public void noRequestBody() {
    call.messageDone(this, true, false, null);
  }
  
  private class RequestBodySink extends ForwardingSink {
    
    private final long contentLength;
    private boolean completed = false;
    private long bytesReceived = 0L;
    private boolean closed = false;
    
    public RequestBodySink(Sink delegate, long contentLength) {
      super(delegate);
      this.contentLength = contentLength;
    }
    
    @Override
    public void write(Buffer source, long byteCount) throws IOException {
      if (closed) throw new IOException("closed");
      if (contentLength != -1L && bytesReceived + byteCount > contentLength) {
        throw new ProtocolException("expected " + contentLength + " bytes but received " + (bytesReceived + byteCount));
      }
      try {
        super.write(source, byteCount);
        this.bytesReceived += byteCount;
      } catch (IOException e) {
        throw complete(e);
      }
    }
    
    @Override
    public void flush() throws IOException {
      try {
        super.flush();
      } catch (IOException e) {
        throw complete(e);
      }
    }
    
    @Override
    public void close() throws IOException {
      if (closed) return;
      closed = true;
      if (contentLength != -1L && bytesReceived != contentLength) {
        throw new ProtocolException("unexpected end of stream");
      }
      try {
        super.close();
        complete(null);
      } catch (IOException e) {
        throw complete(e);
      }
    }
    
    private <E extends IOException> E complete(E e) {
      if (completed) return e;
      completed = true;
      return bodyComplete(bytesReceived, false, true, e);
    }
  }
  
  private class ResponseBodySource extends ForwardingSource {
    
    private final long contentLength;
    private long bytesReceived = 0L;
    private boolean invokeStartEvent = true;
    private boolean completed = false;
    private boolean closed = false;
    
    public ResponseBodySource(Source delegate, long contentLength) {
      super(delegate);
      this.contentLength = contentLength;
      if (contentLength == 0L) {
        complete(null);
      }
    }
    
    @Override
    public long read(Buffer sink, long byteCount) throws IOException {
      if (closed) throw new IOException("closed");
      try {
        long read = delegate.read(sink, byteCount);
        
        if (invokeStartEvent) {
          invokeStartEvent = false;
          eventListener.responseBodyStart(call);
        }
        
        if (read == -1L) {
          complete(null);
          return -1L;
        }
        
        long newBytesReceived = bytesReceived + read;
        if (contentLength != -1L && newBytesReceived > contentLength) {
          throw new ProtocolException("expected " + contentLength + " bytes but received " + newBytesReceived);
        }
        
        bytesReceived = newBytesReceived;
        if (newBytesReceived == contentLength) {
          complete(null);
        }
        
        return read;
      } catch (IOException e) {
        throw complete(e);
      }
    }
    
    @Override
    public void close() throws IOException {
      if (closed) return;
      closed = true;
      try {
        super.close();
        complete(null);
      } catch (IOException e) {
        throw complete(e);
      }
    }
    
    public <E extends IOException> E complete(E e) {
      if (completed) return e;
      completed = true;
      
      if (e == null && invokeStartEvent) {
        invokeStartEvent = false;
        eventListener.responseBodyStart(call);
      }
      return bodyComplete(bytesReceived, true, false, e);
    }
  }
}