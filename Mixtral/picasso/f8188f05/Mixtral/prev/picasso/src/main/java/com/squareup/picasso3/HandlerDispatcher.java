package com.squareup.picasso3;

import android.content.Context;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Pair;
import com.squareup.picasso3.Picasso.Priority;
import com.squareup.picasso3.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class HandlerDispatcher extends Dispatcher {
  private final DispatcherThread dispatcherThread;
  private final Handler handler;
  private final Handler mainHandler;

  public HandlerDispatcher(Context context, ExecutorService service, Handler mainThreadHandler, PlatformLruCache cache) {
    super(context, service, mainThreadHandler, cache);

    dispatcherThread = new DispatcherThread();
    dispatcherThread.start();
    Looper dispatcherThreadLooper = dispatcherThread.getLooper();
    Utils.flushStackLocalLeaks(dispatcherThreadLooper);
    handler = new DispatcherHandler(dispatcherThreadLooper, this);
    mainHandler = new MainDispatcherHandler(mainThreadHandler.getLooper(), this);
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
    handler.sendMessage(
      handler.obtainMessage(
        AIRPLANE_MODE_CHANGE,
        airplaneMode ? AIRPLANE_MODE_ON : AIRPLANE_MODE_OFF,
        0
      )
    );
  }

  @Override
  public void dispatchCompleteMain(BitmapHunter hunter) {
    Message message = mainHandler.obtainMessage(HUNTER_COMPLETE, hunter);
    if (hunter.getPriority() == Priority.HIGH) {
      mainHandler.sendMessageAtFrontOfQueue(message);
    } else {
      mainHandler.sendMessage(message);
    }
  }

  @Override
  public void dispatchBatchResumeMain(List<Action> batch) {
    mainHandler.sendMessage(mainHandler.obtainMessage(REQUEST_BATCH_RESUME, batch));
  }

  private static class DispatcherHandler extends Handler {
    private final WeakReference<HandlerDispatcher> dispatcherWeakReference;

    public DispatcherHandler(Looper looper, HandlerDispatcher dispatcher) {
      super(looper);

      dispatcherWeakReference = new WeakReference<>(dispatcher);
    }

    @Override
    public void handleMessage(Message msg) {
      HandlerDispatcher dispatcher = dispatcherWeakReference.get();
      if (dispatcher == null) {
        return;
      }

      switch (msg.what) {
        case REQUEST_SUBMIT: {
          Action action = (Action) msg.obj;
          dispatcher.performSubmit(action);
          break;
        }
        case REQUEST_CANCEL: {
          Action action = (Action) msg.obj;
          dispatcher.performCancel(action);
          break;
        }
        case TAG_PAUSE: {
          Object tag = msg.obj;
          dispatcher.performPauseTag(tag);
          break;
        }
        case TAG_RESUME: {
          Object tag = msg.obj;
          dispatcher.performResumeTag(tag);
          break;
        }
        case HUNTER_COMPLETE: {
          BitmapHunter hunter = (BitmapHunter) msg.obj;
          dispatcher.performComplete(hunter);
          break;
        }
        case HUNTER_RETRY: {
          BitmapHunter hunter = (BitmapHunter) msg.obj;
          dispatcher.performRetry(hunter);
          break;
        }
        case HUNTER_DECODE_FAILED: {
          BitmapHunter hunter = (BitmapHunter) msg.obj;
          dispatcher.performError(hunter);
          break;
        }
        case NETWORK_STATE_CHANGE: {
          NetworkInfo info = (NetworkInfo) msg.obj;
          dispatcher.performNetworkStateChange(info);
          break;
        }
        case AIRPLANE_MODE_CHANGE: {
          boolean airplaneMode = msg.arg1 == AIRPLANE_MODE_ON;
          dispatcher.performAirplaneModeChange(airplaneMode);
          break;
        }
        default:
          throw new AssertionError("Unknown handler message received: " + msg.what);
      }
    }
  }

  private static class MainDispatcherHandler extends Handler {
    private final WeakReference<Dispatcher> dispatcherWeakReference;

    public MainDispatcherHandler(Looper looper, Dispatcher dispatcher) {
      super(looper);

      dispatcherWeakReference = new WeakReference<>(dispatcher);
    }

    @Override
    public void handleMessage(Message msg) {
      Dispatcher dispatcher = dispatcherWeakReference.get();
      if (dispatcher == null) {
        return;
      }

      switch (msg.what) {
        case HUNTER_COMPLETE: {
          BitmapHunter hunter = (BitmapHunter) msg.obj;
          dispatcher.performCompleteMain(hunter);
          break;
        }
        case REQUEST_BATCH_RESUME: {
          List<Action> batch = (List<Action>) msg.obj;
          dispatcher.performBatchResumeMain(batch);
          break;
        }
        default:
          throw new AssertionError("Unknown handler message received: " + msg.what);
      }
    }
  }

  private static class DispatcherThread extends HandlerThread {
    public DispatcherThread() {
      super(Utils.THREAD_PREFIX + DISPATCHER_THREAD_NAME, Process.THREAD_PRIORITY_BACKGROUND);
    }
  }

  private static final long RETRY_DELAY = 500;
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