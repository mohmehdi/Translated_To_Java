

package okhttp3.internal.connection;

import java.io.IOException;
import java.util.List;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.http.RealInterceptorChain;
import okhttp3.internal.http.RealResponseBody;
import okhttp3.internal.http.StreamAllocation;
import okhttp3.internal.platform.Platform;
import okio.Buffer;
import okio.Sink;
import okio.Source;

public final class ConnectInterceptor implements Interceptor {
  @Override
  public Response intercept(Interceptor.Chain chain) throws IOException {
    RealInterceptorChain realChain = (RealInterceptorChain) chain;
    StreamAllocation streamAllocation = realChain.call().streamAllocation();
    streamAllocation.acquire();
    try {
      Response.Builder responseBuilder = realChain.newBuilder();
      Response response = responseBuilder.build();
      streamAllocation.callStart(response.request(), response.code());
      BufferedSource source = realChain.call().responseBody().source();
      source.request(Long.MAX_VALUE); // Buffer the entire body.
      Buffer buffer = source.buffer();
      RealResponseBody realResponseBody = new RealResponseBody(response.contentType(), buffer.size(), buffer);
      response = response.newBuilder()
          .body(realResponseBody)
          .build();
      return realChain.proceed(realChain.request(), response);
    } finally {
      streamAllocation.callEnd();
    }
  }
}