package com.github.shadowsocks.net;

import com.github.shadowsocks.utils.printLog;
import kotlinx.coroutines.*;
import kotlinx.coroutines.channels.Channel;
import kotlinx.coroutines.channels.SendChannel;
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
    private Channel<Unit> closeChannel;
    private volatile boolean running;

    private void registerInternal(SelectableChannel channel, int ops, Function1<SelectionKey, Unit> block) throws ClosedChannelException {
        channel.register(selector, ops, block);
    }

    public ChannelMonitor() throws IOException {
        super("ChannelMonitor");
        selector = Selector.open();
        registrationPipe = Pipe.open();
        pendingRegistrations = Channel.UNLIMITED;
        closeChannel = Channel.Companion.unlimited();
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
            int writeResult = registrationPipe.sink().write(junk);
            if (writeResult == 0) {
                kotlinx.coroutines.yield();
            } else if (writeResult == 1) {
                break;
            } else {
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
        closeChannel.sendBlocking(Unit.INSTANCE);
    }

    public void close(CoroutineScope scope) {
        running = false;
        selector.wakeup();
        scope.launch(new Function1<CoroutineScope, Unit>() {
            @Override
            public Unit invoke(CoroutineScope scope) {
                closeChannel.receive();
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