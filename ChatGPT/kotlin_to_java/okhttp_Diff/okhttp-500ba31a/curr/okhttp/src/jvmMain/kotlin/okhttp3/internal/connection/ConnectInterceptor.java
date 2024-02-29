package okhttp3.internal.connection;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;
import okhttp3.internal.http.RealInterceptorChain;

public class ConnectInterceptor implements Interceptor {
  @Override
  public Response intercept(Interceptor.Chain chain) throws IOException {
    RealInterceptorChain realChain = (RealInterceptorChain) chain;
    Exchange exchange = realChain.call.initExchange(realChain);
    RealInterceptorChain connectedChain = realChain.copy(exchange);
    return connectedChain.proceed(realChain.request);
  }
}