package com.github.shadowsocks.net;

import android.os.SystemClock;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.github.shadowsocks.Core;
import com.github.shadowsocks.acl.Acl;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.responseLength;
import kotlinx.coroutines.*;
import java.io.IOException;
import java.net.HttpURLConnection;
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

        public static final class Idle extends Status {
            @Override
            protected CharSequence getStatus() {
                return Core.app.getText(R.string.vpn_connected);
            }
        }

        public static final class Testing extends Status {
            @Override
            protected CharSequence getStatus() {
                return Core.app.getText(R.string.connection_test_testing);
            }
        }

        public static final class Success extends Status {
            private final long elapsed;

            public Success(long elapsed) {
                this.elapsed = elapsed;
            }

            @Override
            protected CharSequence getStatus() {
                return Core.app.getString(R.string.connection_test_available, elapsed);
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
                    return Core.app.getString(R.string.connection_test_error_status_code, code);
                }
            }

            public static final class IOFailure extends Error {
                private final IOException e;

                public IOFailure(IOException e) {
                    this.e = e;
                }

                @Override
                protected String getError() {
                    return Core.app.getString(R.string.connection_test_error, e.getMessage());
                }
            }
        }
    }

    private Pair<HttpURLConnection, Job> running;
    public MutableLiveData<Status> status = new MutableLiveData<Status>() {{
        setValue(new Status.Idle());
    }};

    public void testConnection() {
        cancelTest();
        status.setValue(new Status.Testing());
        URL url = new URL("https", Core.currentProfile.first.route == Acl.CHINALIST ? "www.qualcomm.cn" : "www.google.com", "/generate_204");
        HttpURLConnection conn = (HttpURLConnection) (DataStore.serviceMode != Key.modeVpn ? url.openConnection(DataStore.proxy) : url.openConnection());
        conn.setRequestProperty("Connection", "close");
        conn.setInstanceFollowRedirects(false);
        conn.setUseCaches(false);
        running = new Pair<>(conn, GlobalScope.launch(Dispatchers.Main, CoroutineStart.UNDISPATCHED, () -> {
            status.setValue(withContext(Dispatchers.IO, () -> {
                try {
                    long start = SystemClock.elapsedRealtime();
                    int code = conn.getResponseCode();
                    long elapsed = SystemClock.elapsedRealtime() - start;
                    if (code == 204 || (code == 200 && conn.responseLength() == 0L)) {
                        return new Status.Success(elapsed);
                    } else {
                        return new Status.Error.UnexpectedResponseCode(code);
                    }
                } catch (IOException e) {
                    return new Status.Error.IOFailure(e);
                } finally {
                    conn.disconnect();
                }
            }));
        }));
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
        status.setValue(new Status.Idle());
    }
}