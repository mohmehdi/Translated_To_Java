package com.squareup.picasso3;

import android.content.Context;
import android.net.NetworkInfo;
import android.os.Handler;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.CoroutineName;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.ExperimentalCoroutinesApi;
import kotlinx.coroutines.SupervisorJob;
import kotlinx.coroutines.cancel;
import kotlinx.coroutines.channels.Channel;
import kotlinx.coroutines.delay;
import kotlinx.coroutines.isActive;
import kotlinx.coroutines.launch;

@OptIn(ExperimentalCoroutinesApi.class)
public class InternalCoroutineDispatcher extends BaseDispatcher {
    private CoroutineScope scope;
    private Channel<Runnable> channel;

    public InternalCoroutineDispatcher(Context context, Handler mainThreadHandler, PlatformLruCache cache, CoroutineDispatcher mainDispatcher, CoroutineDispatcher backgroundDispatcher) {
        super(context, mainThreadHandler, cache);
        this.mainDispatcher = mainDispatcher;
        this.backgroundDispatcher = backgroundDispatcher;
        this.scope = new CoroutineScope(new SupervisorJob() + backgroundDispatcher);
        this.channel = new Channel<>(Channel.UNLIMITED);

        scope.launch(() -> {
            while (!channel.isClosedForReceive()) {
                channel.receive().run();
            }
        });
    }

    @Override
    public void shutdown() {
        super.shutdown();
        channel.close();
        scope.cancel();
    }

    @Override
    public void dispatchSubmit(Action action) {
        channel.trySend(() -> performSubmit(action));
    }

    @Override
    public void dispatchCancel(Action action) {
        channel.trySend(() -> performCancel(action));
    }

    @Override
    public void dispatchPauseTag(Object tag) {
        channel.trySend(() -> performPauseTag(tag));
    }

    @Override
    public void dispatchResumeTag(Object tag) {
        channel.trySend(() -> performResumeTag(tag));
    }

    @Override
    public void dispatchComplete(BitmapHunter hunter) {
        channel.trySend(() -> performComplete(hunter));
    }

    @Override
    public void dispatchRetry(BitmapHunter hunter) {
        scope.launch(() -> {
            delay(RETRY_DELAY);
            channel.send(() -> performRetry(hunter));
        });
    }

    @Override
    public void dispatchFailed(BitmapHunter hunter) {
        channel.trySend(() -> performError(hunter));
    }

    @Override
    public void dispatchNetworkStateChange(NetworkInfo info) {
        channel.trySend(() -> performNetworkStateChange(info));
    }

    @Override
    public void dispatchAirplaneModeChange(boolean airplaneMode) {
        channel.trySend(() -> performAirplaneModeChange(airplaneMode));
    }

    @Override
    public void dispatchCompleteMain(BitmapHunter hunter) {
        scope.launch(mainDispatcher, () -> {
            performCompleteMain(hunter);
        });
    }

    @Override
    public void dispatchBatchResumeMain(List<Action> batch) {
        scope.launch(mainDispatcher, () -> {
            performBatchResumeMain(batch);
        });
    }

    @Override
    public void dispatchSubmit(BitmapHunter hunter) {
        hunter.job = scope.launch(new CoroutineName(hunter.getName()), () -> {
            hunter.run();
        });
    }

    @Override
    public boolean isShutdown() {
        return !scope.isActive();
    }
}