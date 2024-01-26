package com.github.shadowsocks.net;

import android.os.SystemClock;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.github.shadowsocks.Core;
import com.github.shadowsocks.Core.app;
import com.github.shadowsocks.acl.Acl;
import com.github.shadowsocks.core.R;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.responseLength;
import kotlinx.coroutines.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

public class HttpsTest extends ViewModel {
    public abstract static class Status {
        protected abstract CharSequence getStatus();
        public void retrieve(SetStatus setStatus, ErrorCallback errorCallback) {
            setStatus.setStatus(getStatus());
        }

        public interface SetStatus {
            void setStatus(CharSequence status);
        }

        public interface ErrorCallback {
            void onError(String error);
        }
    }

    public static final class Idle extends Status {
        @Override
        protected CharSequence getStatus() {
            return app.getText(R.string.vpn_connected);
        }
    }

    public static final class Testing extends Status {
        @Override
        protected CharSequence getStatus() {
            return app.getText(R.string.connection_test_testing);
        }
    }

    public static final class Success extends Status {
        private final long elapsed;

        public Success(long elapsed) {
            this.elapsed = elapsed;
        }

        @Override
        protected CharSequence getStatus() {
            return app.getString(R.string.connection_test_available, elapsed);
        }
    }

    public abstract static class Error extends Status {
        protected abstract String getError();
        private boolean shown = false;

        @Override
        public void retrieve(SetStatus setStatus, ErrorCallback errorCallback) {
            super.retrieve(setStatus, errorCallback);
            if (shown) return;
            shown = true;
            errorCallback.onError(getError());
        }

        public static final class UnexpectedResponseCode extends Error {
            private final int code;

            public UnexpectedResponseCode(int code) {
                this.code = code;
            }

            @Override
            protected String getError() {
                return app.getString(R.string.connection_test_error_status_code, code);
            }
        }

        public static final class IOFailure extends Error {
            private final IOException e;

            public IOFailure(IOException e) {
                this.e = e;
            }

            @Override
            protected String getError() {
                return app.getString(R.string.connection_test_error, e.getMessage());
            }
        }
    }

    private Pair<HttpURLConnection, Job> running = null;
    public MutableLiveData<Status> status = new MutableLiveData<Status>() {{
        setValue(new Idle());
    }};

    public void testConnection() {
        cancelTest();
        status.setValue(new Testing());
        URL url = new URL("https", Core.currentProfile != null && Core.currentProfile.first.route == Acl.CHINALIST ? "www.qualcomm.cn" : "www.google.com", "/generate_204");
        HttpURLConnection conn = (HttpURLConnection) (DataStore.serviceMode == Key.modeVpn ? url.openConnection() : url.openConnection(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", DataStore.portProxy))));
        conn.setRequestProperty("Connection", "close");
        conn.setInstanceFollowRedirects(false);
        conn.setUseCaches(false);
        running = new Pair<>(conn, GlobalScope.launch(Dispatchers.Main, CoroutineStart.UNDISPATCHED, new Function2<CoroutineScope, Continuation<? super Status>, Object>() {
            @Override
            public Object invoke(CoroutineScope coroutineScope, Continuation<? super Status> continuation) {
                return new Continuation<Object>() {
                    @Override
                    public CoroutineContext getContext() {
                        return null;
                    }

                    @Override
                    public void resumeWith(Object o) {

                    }
                };
            }
        }) {
            @Override
            public Object invoke(CoroutineScope coroutineScope, Continuation<? super Status> continuation) {
                return new Continuation<Object>() {
                    @Override
                    public CoroutineContext getContext() {
                        return null;
                    }

                    @Override
                    public void resumeWith(Object o) {

                    }
                };
            }
        });
    }

    private void cancelTest() {
        if (running != null) {
            Job job = running.getSecond();
            if (job != null) {
                job.cancel();
            }
            HttpURLConnection conn = running.getFirst();
            if (conn != null) {
                conn.disconnect();
            }
            running = null;
        }
    }

    public void invalidate() {
        cancelTest();
        status.setValue(new Idle());
    }
}