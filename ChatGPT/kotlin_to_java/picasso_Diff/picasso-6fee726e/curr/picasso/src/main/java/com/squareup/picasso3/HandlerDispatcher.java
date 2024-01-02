package com.squareup.picasso3;

import android.content.Context;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import java.util.concurrent.ExecutorService;

public class HandlerDispatcher extends Dispatcher {
    private DispatcherThread dispatcherThread;
    private Handler handler;

    public HandlerDispatcher(Context context, ExecutorService service, Handler mainThreadHandler, PlatformLruCache cache) {
        super(context, service, mainThreadHandler, cache);
        dispatcherThread = new DispatcherThread();
        dispatcherThread.start();
        Looper dispatcherThreadLooper = dispatcherThread.getLooper();
        Utils.flushStackLocalLeaks(dispatcherThreadLooper);
        handler = new DispatcherHandler(dispatcherThreadLooper, this);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        dispatcherThread.quit();
    }

    @Override
    public void dispatchSubmit(Action action) {
        handler.sendMessage(handler.obtainMessage(REQUEST_SUBMIT, action));
    }

    @Override
    public void dispatchCancel(Action action) {
        handler.sendMessage(handler.obtainMessage(REQUEST_CANCEL, action));
    }

    @Override
    public void dispatchPauseTag(Object tag) {
        handler.sendMessage(handler.obtainMessage(TAG_PAUSE, tag));
    }

    @Override
    public void dispatchResumeTag(Object tag) {
        handler.sendMessage(handler.obtainMessage(TAG_RESUME, tag));
    }

    @Override
    public void dispatchComplete(BitmapHunter hunter) {
        handler.sendMessage(handler.obtainMessage(HUNTER_COMPLETE, hunter));
    }

    @Override
    public void dispatchRetry(BitmapHunter hunter) {
        handler.sendMessageDelayed(handler.obtainMessage(HUNTER_RETRY, hunter), RETRY_DELAY);
    }

    @Override
    public void dispatchFailed(BitmapHunter hunter) {
        handler.sendMessage(handler.obtainMessage(HUNTER_DECODE_FAILED, hunter));
    }

    @Override
    public void dispatchNetworkStateChange(NetworkInfo info) {
        handler.sendMessage(handler.obtainMessage(NETWORK_STATE_CHANGE, info));
    }

    @Override
    public void dispatchAirplaneModeChange(boolean airplaneMode) {
        handler.sendMessage(handler.obtainMessage(AIRPLANE_MODE_CHANGE, airplaneMode ? AIRPLANE_MODE_ON : AIRPLANE_MODE_OFF, 0));
    }

    private static class DispatcherHandler extends Handler {
        private final Dispatcher dispatcher;

        public DispatcherHandler(Looper looper, Dispatcher dispatcher) {
            super(looper);
            this.dispatcher = dispatcher;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REQUEST_SUBMIT:
                    Action submitAction = (Action) msg.obj;
                    dispatcher.performSubmit(submitAction);
                    break;
                case REQUEST_CANCEL:
                    Action cancelAction = (Action) msg.obj;
                    dispatcher.performCancel(cancelAction);
                    break;
                case TAG_PAUSE:
                    Object pauseTag = msg.obj;
                    dispatcher.performPauseTag(pauseTag);
                    break;
                case TAG_RESUME:
                    Object resumeTag = msg.obj;
                    dispatcher.performResumeTag(resumeTag);
                    break;
                case HUNTER_COMPLETE:
                    BitmapHunter completeHunter = (BitmapHunter) msg.obj;
                    dispatcher.performComplete(completeHunter);
                    break;
                case HUNTER_RETRY:
                    BitmapHunter retryHunter = (BitmapHunter) msg.obj;
                    dispatcher.performRetry(retryHunter);
                    break;
                case HUNTER_DECODE_FAILED:
                    BitmapHunter failedHunter = (BitmapHunter) msg.obj;
                    dispatcher.performError(failedHunter);
                    break;
                case NETWORK_STATE_CHANGE:
                    NetworkInfo networkInfo = (NetworkInfo) msg.obj;
                    dispatcher.performNetworkStateChange(networkInfo);
                    break;
                case AIRPLANE_MODE_CHANGE:
                    boolean airplaneMode = msg.arg1 == AIRPLANE_MODE_ON;
                    dispatcher.performAirplaneModeChange(airplaneMode);
                    break;
                default:
                    Picasso.HANDLER.post(() -> {
                        throw new AssertionError("Unknown handler message received: " + msg.what);
                    });
                    break;
            }
        }
    }

    private static class DispatcherThread extends HandlerThread {
        public DispatcherThread() {
            super(Utils.THREAD_PREFIX + DISPATCHER_THREAD_NAME, Process.THREAD_PRIORITY_BACKGROUND);
        }
    }

    private static final long RETRY_DELAY = 500L;
    private static final int AIRPLANE_MODE_ON = 1;
    private static final int AIRPLANE_MODE_OFF = 0;
    private static final int REQUEST_SUBMIT = 1;
    private static final int REQUEST_CANCEL = 2;
    private static final int HUNTER_RETRY = 5;
    private static final int HUNTER_DECODE_FAILED = 6;
    private static final int AIRPLANE_MODE_CHANGE = 10;
    private static final int TAG_PAUSE = 11;
    private static final int TAG_RESUME = 12;
    private static final String DISPATCHER_THREAD_NAME = "Dispatcher";
}