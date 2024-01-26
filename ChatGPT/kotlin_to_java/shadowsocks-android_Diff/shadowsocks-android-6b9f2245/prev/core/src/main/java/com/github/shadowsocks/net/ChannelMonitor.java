package com.github.shadowsocks.net;

import com.github.shadowsocks.utils.printLog;
import kotlinx.coroutines.*;
import kotlinx.coroutines.channels.Channel;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;

public class ChannelMonitor extends Thread {
    private static class Registration {
        public SelectableChannel channel;
        public int ops;
        public Function1<SelectionKey, Unit> listener;
        public CompletableDeferred<SelectionKey> result;

        public Registration(SelectableChannel channel, int ops, Function1<SelectionKey, Unit> listener) {
            this.channel = channel;
            this.ops = ops;
            this.listener = listener;
            this.result = new CompletableDeferred<>();
        }
    }

    private Selector selector;
    private Pipe registrationPipe;
    private Channel<Registration> pendingRegistrations;
    private volatile boolean running;

    private void registerInternal(SelectableChannel channel, int ops, Function1<SelectionKey, Unit> block) throws ClosedChannelException {
        channel.register(selector, ops, block);
    }

    public ChannelMonitor() throws IOException {
        super("ChannelMonitor");
        selector = Selector.open();
        registrationPipe = Pipe.open();
        pendingRegistrations = Channel.UNLIMITED;
        running = true;

        registrationPipe.source().configureBlocking(false);
        registerInternal(registrationPipe.source(), SelectionKey.OP_READ, new Function1<SelectionKey, Unit>() {
            @Override
            public void invoke(SelectionKey key) {
                ByteBuffer junk = ByteBuffer.allocateDirect(1);
                while (read(junk) > 0) {
                    Registration registration = pendingRegistrations.poll();
                    try {
                        registration.result.complete(registerInternal(registration.channel, registration.ops, registration.listener));
                    } catch (ClosedChannelException e) {
                        registration.result.completeExceptionally(e);
                    }
                    junk.clear();
                }
            }
        });

        start();
    }

    public SelectionKey register(SelectableChannel channel, int ops, Function1<SelectionKey, Unit> block) throws IOException, InterruptedException {
        Registration registration = new Registration(channel, ops, block);
        pendingRegistrations.send(registration);
        ByteBuffer junk = ByteBuffer.allocateDirect(1);
        while (running) {
            switch (registrationPipe.sink().write(junk)) {
                case 0:
                    kotlinx.coroutines.yield();
                    break;
                case 1:
                    break;
                default:
                    throw new IOException("Failed to register in the channel");
            }
        }
        if (!running) {
            throw new ClosedChannelException();
        }
        return registration.result.await();
    }

    public CompletableDeferred<SelectionKey> wait(SelectableChannel channel, int ops) throws IOException, InterruptedException {
        CompletableDeferred<SelectionKey> deferred = new CompletableDeferred<>();
        register(channel, ops, new Function1<SelectionKey, Unit>() {
            @Override
            public void invoke(SelectionKey key) {
                if (key.isValid()) {
                    key.interestOps(0);
                }
                deferred.complete(key);
            }
        });
        return deferred;
    }

    @Override
    public void run() {
        while (running) {
            int num;
            try {
                num = selector.select();
            } catch (IOException e) {
                printLog(e);
                continue;
            }
            if (num <= 0) {
                continue;
            }
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                ((Function1<SelectionKey, Unit>) key.attachment()).invoke(key);
            }
        }
    }

    public void close(CoroutineScope scope) {
        running = false;
        selector.wakeup();
        scope.launch(Dispatchers.IO, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                join();
                for (SelectionKey key : selector.keys()) {
                    try {
                        key.channel().close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    selector.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }
        });
    }
}