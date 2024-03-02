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
import okhttp3.internal.EMPTY_HEADERS;
import okhttp3.internal.connection.RealConnection;
import okhttp3.internal.discard;
import okhttp3.internal.headersContentLength;
import okhttp3.internal.http.ExchangeCodec;
import okhttp3.internal.http.RequestLine;
import okhttp3.internal.http.StatusLine;

import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ForwardingTimeout;
import okio.Sink;
import okio.Source;
import okio.Timeout;

public final class Http1ExchangeCodec implements ExchangeCodec {
    private static final long NO_CHUNK_YET = -1L;

    private static final int STATE_IDLE = 0;
    private static final int STATE_OPEN_REQUEST_BODY = 1;
    private static final int STATE_WRITING_REQUEST_BODY = 2;
    private static final int STATE_READ_RESPONSE_HEADERS = 3;
    private static final int STATE_OPEN_RESPONSE_BODY = 4;
    private static final int STATE_READING_RESPONSE_BODY = 5;
    private static final int STATE_CLOSED = 6;

    private final OkHttpClient client;
    private final RealConnection connection;
    private final BufferedSource source;
    private final BufferedSink sink;
    private int state = STATE_IDLE;
    private final HeadersReader headersReader = new HeadersReader(source);
    private Headers trailers;

    public Http1ExchangeCodec(OkHttpClient client, RealConnection connection, BufferedSource source, BufferedSink sink) {
        this.client = client;
        this.connection = connection;
        this.source = source;
        this.sink = sink;
    }

    private boolean isChunked(Response response) {
        return "chunked".equalsIgnoreCase(response.header("Transfer-Encoding"));
    }

    private boolean isChunked(Request request) {
        return "chunked".equalsIgnoreCase(request.header("Transfer-Encoding"));
    }

    public boolean isClosed() {
        return state == STATE_CLOSED;
    }

    @Override
    public Sink createRequestBody(Request request, long contentLength) {
        if (request.body() != null && request.body().isDuplex()) {
            throw new ProtocolException("Duplex connections are not supported for HTTP/1");
        }
        return request.isChunked() ? newChunkedSink() :
                (contentLength != -1L ? newKnownLengthSink() :
                        new IllegalStateException("Cannot stream a request body without chunked encoding or a known content length!"));
    }

    @Override
    public void cancel() {
        connection.cancel();
    }

    @Override
    public void writeRequestHeaders(Request request) {
        String requestLine = RequestLine.get(request, connection.route().proxy().type());
        writeRequest(request.headers(), requestLine);
    }

    @Override
    public long reportedContentLength(Response response) {
        if (!response.promisesBody()) {
            return 0L;
        } else if (isChunked(response)) {
            return -1L;
        } else {
            return response.headersContentLength();
        }
    }

    @Override
    public Source openResponseBodySource(Response response) {
        if (!response.promisesBody()) {
            return newFixedLengthSource(0);
        } else if (isChunked(response)) {
            return newChunkedSource(response.request().url());
        } else {
            long contentLength = response.headersContentLength();
            return (contentLength != -1L) ? newFixedLengthSource(contentLength) : newUnknownLengthSource();
        }
    }

    @Override
    public Headers trailers() {
        if (state != STATE_CLOSED) {
            throw new IllegalStateException("too early; can't read the trailers yet");
        }
        return trailers != null ? trailers : EMPTY_HEADERS;
    }

    @Override
    public void flushRequest() throws IOException {
        sink.flush();
    }

    @Override
    public void finishRequest() throws IOException {
        sink.flush();
    }

    private void writeRequest(Headers headers, String requestLine) throws IOException {
        if (state != STATE_IDLE) {
            throw new IllegalStateException("state: " + state);
        }
        sink.writeUtf8(requestLine).writeUtf8("\r\n");
        for (int i = 0, size = headers.size(); i < size; i++) {
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
        if (state != STATE_OPEN_REQUEST_BODY &&
                state != STATE_WRITING_REQUEST_BODY &&
                state != STATE_READ_RESPONSE_HEADERS) {
            throw new IllegalStateException("state: " + state);
        }

        try {
            StatusLine statusLine = StatusLine.parse(headersReader.readLine());

            Response.Builder responseBuilder = new Response.Builder()
                    .protocol(statusLine.protocol)
                    .code(statusLine.code)
                    .message(statusLine.message)
                    .headers(headersReader.readHeaders());

            if (expectContinue && statusLine.code == HTTP_CONTINUE) {
                return null;
            } else if (statusLine.code == HTTP_CONTINUE) {
                state = STATE_READ_RESPONSE_HEADERS;
                return responseBuilder;
            } else {
                state = STATE_OPEN_RESPONSE_BODY;
                return responseBuilder;
            }
        } catch (EOFException e) {
            String address = connection.route().address().url().redact();
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
        connection.noNewExchanges();
        return new UnknownLengthSource();
    }

    private void detachTimeout(ForwardingTimeout timeout) {
        Timeout oldDelegate = timeout.delegate();
        timeout.setDelegate(Timeout.NONE);
        oldDelegate.clearDeadline();
        oldDelegate.clearTimeout();
    }

    private void skipConnectBody(Response response) {
        long contentLength = response.headersContentLength();
        if (contentLength == -1L) {
            return;
        }
        Source body = newFixedLengthSource(contentLength);
        body.skipAll(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
        body.close();
    }

    private final class KnownLengthSink implements Sink {
        private final ForwardingTimeout timeout = new ForwardingTimeout(sink.timeout());
        private boolean closed;

        @Override
        public Timeout timeout() {
            return timeout;
        }

        @Override
        public void write(Buffer source, long byteCount) throws IOException {
            if (closed) {
                throw new IllegalStateException("closed");
            }
            checkOffsetAndCount(source.size(), 0, byteCount);
            sink.write(source, byteCount);
        }

        @Override
        public void flush() throws IOException {
            if (closed) {
                return;
            }
            sink.flush();
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            detachTimeout(timeout);
            state = STATE_READ_RESPONSE_HEADERS;
        }
    }

    private final class ChunkedSink implements Sink {
        private final ForwardingTimeout timeout = new ForwardingTimeout(sink.timeout());
        private boolean closed;

        @Override
        public Timeout timeout() {
            return timeout;
        }

        @Override
        public void write(Buffer source, long byteCount) throws IOException {
            if (closed) {
                throw new IllegalStateException("closed");
            }
            if (byteCount == 0L) {
                return;
            }

            sink.writeHexadecimalUnsignedLong(byteCount);
            sink.writeUtf8("\r\n");
            sink.write(source, byteCount);
            sink.writeUtf8("\r\n");
        }

        @Override
        public synchronized void flush() throws IOException {
            if (closed) {
                return;
            }
            sink.flush();
        }

        @Override
        public synchronized void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            sink.writeUtf8("0\r\n\r\n");
            detachTimeout(timeout);
            state = STATE_READ_RESPONSE_HEADERS;
        }
    }

    private abstract class AbstractSource implements Source {
        protected final ForwardingTimeout timeout = new ForwardingTimeout(source.timeout());
        protected boolean closed;

        @Override
        public Timeout timeout() {
            return timeout;
        }

        @Override
        public long read(Buffer sink, long byteCount) throws IOException {
            try {
                long read = source.read(sink, byteCount);
                if (read == -1L) {
                    connection.noNewExchanges();
                    responseBodyComplete();
                }
                return read;
            } catch (IOException e) {
                connection.noNewExchanges();
                responseBodyComplete();
                throw e;
            }
        }

        void responseBodyComplete() {
            if (state == STATE_CLOSED) {
                return;
            }
            if (state != STATE_READING_RESPONSE_BODY) {
                throw new IllegalStateException("state: " + state);
            }

            detachTimeout(timeout);

            state = STATE_CLOSED;
        }
    }

    private final class FixedLengthSource extends AbstractSource {
        private long bytesRemaining;

        FixedLengthSource(long bytesRemaining) {
            this.bytesRemaining = bytesRemaining;
            if (bytesRemaining == 0L) {
                responseBodyComplete();
            }
        }

        @Override
        public long read(Buffer sink, long byteCount) throws IOException {
            checkOffsetAndCount(byteCount, 0, byteCount);
            if (closed) {
                return -1L;
            }
            if (bytesRemaining == 0L) {
                return -1L;
            }

            long read = super.read(sink, Math.min(bytesRemaining, byteCount));
            if (read == -1L) {
                connection.noNewExchanges();
                ProtocolException e = new ProtocolException("unexpected end of stream");
                responseBodyComplete();
                throw e;
            }

            bytesRemaining -= read;
            if (bytesRemaining == 0L) {
                responseBodyComplete();
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }

            if (bytesRemaining != 0L &&
                    !discard(ExchangeCodec.DISCARD_STREAM_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                connection.noNewExchanges();
                responseBodyComplete();
            }

            closed = true;
        }
    }

    private final class ChunkedSource extends AbstractSource {
        private static final int NO_CHUNK_YET = -1;

        private long bytesRemainingInChunk = NO_CHUNK_YET;
        private boolean hasMoreChunks = true;
        private final HttpUrl url;

        ChunkedSource(HttpUrl url) {
            this.url = url;
        }

        @Override
        public long read(Buffer sink, long byteCount) throws IOException {
            checkOffsetAndCount(byteCount, 0, byteCount);
            if (closed) {
                return -1L;
            }
            if (!hasMoreChunks) {
                return -1L;
            }

            if (bytesRemainingInChunk == 0L || bytesRemainingInChunk == NO_CHUNK_YET) {
                readChunkSize();
                if (!hasMoreChunks) {
                    return -1L;
                }
            }

            long read = super.read(sink, Math.min(byteCount, bytesRemainingInChunk));
            if (read == -1L) {
                connection.noNewExchanges();
                ProtocolException e = new ProtocolException("unexpected end of stream");
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
                if (bytesRemainingInChunk < 0L || (extensions.length() > 0 && !extensions.startsWith(";"))) {
                    throw new ProtocolException("expected chunk size and optional extensions" +
                            " but was \"" + bytesRemainingInChunk + extensions + "\"");
                }
            } catch (NumberFormatException e) {
                throw new ProtocolException(e.getMessage());
            }

            if (bytesRemainingInChunk == 0L) {
                hasMoreChunks = false;
                trailers = headersReader.readHeaders();
                client.cookieJar().receiveHeaders(url, trailers);
                responseBodyComplete();
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            if (hasMoreChunks &&
                    !discard(ExchangeCodec.DISCARD_STREAM_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                connection.noNewExchanges();
                responseBodyComplete();
            }
            closed = true;
        }
    }

    private final class UnknownLengthSource extends AbstractSource {
        private boolean inputExhausted;

        @Override
        public long read(Buffer sink, long byteCount) throws IOException {
            checkOffsetAndCount(byteCount, 0, byteCount);
            if (closed) {
                return -1L;
            }
            if (inputExhausted) {
                return -1L;
            }

            long read = super.read(sink, byteCount);
            if (read == -1L) {
                inputExhausted = true;
                responseBodyComplete();
                return -1L;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            if (!inputExhausted) {
                responseBodyComplete();
            }
            closed = true;
        }
    }
}
