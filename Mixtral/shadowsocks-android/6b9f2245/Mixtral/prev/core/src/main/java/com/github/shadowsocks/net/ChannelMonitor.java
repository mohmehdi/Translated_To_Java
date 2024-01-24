package com.github.shadowsocks.net;

import com.github.shadowsocks.utils.printLog;
import kotlinx.coroutines.*;
import kotlinx.coroutines.channels.Channel;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;

class ChannelMonitor extends Thread("ChannelMonitor") {
    private class Registration {
        final SelectableChannel channel;
        final int ops;
        final SelectionKeyListener listener;
        CompletableDeferred<SelectionKey> result;

        Registration(SelectableChannel channel, int ops, SelectionKeyListener listener) {
            this.channel = channel;
            this.ops = ops;
            this.listener = listener;
        }
    }

    private final Selector selector = Selector.open();
    private final Pipe registrationPipe = Pipe.open();
    private final Channel<Registration> pendingRegistrations = Channel.create(Channel.UNLIMITED);
    private volatile boolean running = true;

    private SelectionKey registerInternal(SelectableChannel channel, int ops, SelectionKeyListener listener) throws ClosedChannelException {
        return channel.register(selector, ops, listener);
    }

    public ChannelMonitor() {
        Pipe.SourceChannel source = registrationPipe.source();
        source.configureBlocking(false);
        source.register(selector, SelectionKey.OP_READ, key -> {
            ByteBuffer junk = ByteBuffer.allocateDirect(1);
            while (source.read(junk) > 0) {
                Registration registration = pendingRegistrations.take();
                try {
                    registration.result = CompletableDeferred.create(registerInternal(registration.channel, registration.ops, registration.listener));
                } catch (ClosedChannelException e) {
                    registration.result = CompletableDeferred.createExceptionally(e);
                }
                junk.clear();
            }
        });
        start();
    }

    public SelectionKey register(SelectableChannel channel, int ops, SelectionKeyListener listener) throws IOException {
        Registration registration = new Registration(channel, ops, listener);
        pendingRegistrations.send(registration);
        ByteBuffer junk = ByteBuffer.allocateDirect(1);
        while (running) {
            switch (registrationPipe.sink().write(junk)) {
                case 0:
                    continue;
                case 1:
                    break;
                default:
                    throw new IOException("Failed to register in the channel");
            }
        }
        if (!running) throw new ClosedChannelException();
        return registration.result.get();
    }

    public CompletableDeferred<SelectionKey> wait(SelectableChannel channel, int ops) {
        CompletableDeferred<SelectionKey> deferred = CompletableDeferred.create();
        register(channel, ops, key -> {
            if (key.isValid()) key.interestOps(0); // stop listening
            deferred.complete(key);
        });
        return deferred;
    }

    @Override
    public void run() {
        while (running) {
            try {
                selector.select();
            } catch (IOException e) {
                printLog(e);
                continue;
            }
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                ((SelectionKeyListener) key.attachment()).process(key);
            }
        }
    }

    public void close(CoroutineScope scope) {
        running = false;
        selector.wakeup();
        scope.launch(Dispatchers.IO, new Continuation<Unit>() {
            @Override
            public Unit resume(Object o) {
                join();
                selector.keys().forEach(key -> {
                    try {
                        key.channel().close();
                    } catch (IOException e) {
                        printLog(e);
                    }
                });
                try {
                    selector.close();
                } catch (IOException e) {
                    printLog(e);
                }
                return null;
            }

            @Override
            public Object resumeWithException(Throwable throwable) {
                return null;
            }
        });
    }
}