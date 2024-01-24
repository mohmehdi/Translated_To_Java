package com.github.shadowsocks.net;

import com.github.shadowsocks.utils.Log;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.intrinsics.*;
import kotlin.coroutines.intrinsics.CoroutineSingletons;
import kotlin.ResultKt;
import kotlin.Unit;
import kotlinx.coroutines.channels.Channel;
import kotlinx.coroutines.channels.ProducerScope;
import kotlinx.coroutines.channels.ChannelIterator;
import kotlinx.coroutines.selects.SelectClause0;
import kotlinx.coroutines.selects.SelectClause1;
import kotlinx.coroutines.selects.SelectInstructions;
import kotlinx.coroutines.selects.SelectImpl;
import kotlinx.coroutines.selects.WhileSelectClause0;
import kotlinx.coroutines.selects.WhileSelectClause1;
import java.io.ByteBuffer;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SelectableChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelectionKey;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;

public class ChannelMonitor extends Thread {
    private static class Registration {
        private final SelectableChannel channel;
        private final int ops;
        private final SelectionKey.AbstractSelectionKey listener;

        public Registration(SelectableChannel channel, int ops, SelectionKey.AbstractSelectionKey listener) {
            this.channel = channel;
            this.ops = ops;
            this.listener = listener;
        }
    }

    private final Selector selector = SelectorProvider.provider().openSelector();
    private final Pipe registrationPipe = Pipe.open();
    private final Channel<Registration> pendingRegistrations = Channel.CONCAT(16).open();
    private final Channel<Unit> closeChannel = Channel.RENDEZVOUS.open();
    private volatile boolean running = true;

    private SelectionKey registerInternal(SelectableChannel channel, int ops, SelectionKey.AbstractSelectionKey listener) throws IOException {
        return channel.register(selector, ops, listener);
    }

    public ChannelMonitor() {
        registrationPipe.source().configureBlocking(false);
        registrationPipe.source().register(selector, SelectionKey.OP_READ, key -> {
            ByteBuffer junk = ByteBuffer.allocateDirect(1);
            while (registrationPipe.source().read(junk) > 0) {
                Registration registration = pendingRegistrations.receive();
                try {
                    registration.result.complete(registerInternal(registration.channel, registration.ops, registration.listener));
                } catch (ClosedChannelException e) {
                    registration.result.completeExceptionally(e);
                }
                junk.clear();
            }
            return null;
        });
        start();
    }

    public SelectionKey register(SelectableChannel channel, int ops, SelectionKey.AbstractSelectionKey block) {
        Registration registration = new Registration(channel, ops, block);
        pendingRegistrations.send(registration);
        ByteBuffer junk = ByteBuffer.allocateDirect(1);
        while (running) {
            int write = registrationPipe.sink().write(junk);
            if (write == 1) {
                break;
            } else if (write == 0) {
                Thread.yield();
            } else {
                throw new IOException("Failed to register in the channel");
            }
        }
        if (!running) {
            throw new ClosedChannelException();
        }
        return registration.result.getCompletionExceptionOrNull();
    }

    public CompletableDeferred<SelectionKey> wait(SelectableChannel channel, int ops) {
        CompletableDeferred<SelectionKey> deferred = new CompletableDeferred<>();
        register(channel, ops, key -> {
            if (key.isValid()) {
                key.interestOps(0); // stop listening
            }
            deferred.complete(key);
            return null;
        });
        return deferred;
    }

    @Override
    public void run() {
        while (running) {
            try {
                selector.select();
            } catch (IOException e) {
                Log.printLog(e);
                continue;
            }
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                ((SelectionKey.AbstractSelectionKey) key.attachment()).invoke(key);
            }
        }
        closeChannel.sendBlocking(Unit.INSTANCE);
    }

    public void close(Continuation<? super Unit> completion) {
        running = false;
        selector.wakeup();
        SelectImpl.select(new SelectClause0() {
            @Override
            public void invoke() {
                closeChannel.receive();
                Iterator<SelectionKey> keys = selector.keys().iterator();
                while (keys.hasNext()) {
                    keys.next().channel().close();
                }
                selector.close();
            }
        }, new WhileSelectClause1<Unit, Object>(completion) {
            @Override
            public Object invoke(Unit unit, Object result) {
                return running;
            }
        }, new SelectInstructions(completion));
    }
}