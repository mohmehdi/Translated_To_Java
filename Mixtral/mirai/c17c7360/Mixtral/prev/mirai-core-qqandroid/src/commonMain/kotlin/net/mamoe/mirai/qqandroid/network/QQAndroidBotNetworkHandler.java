
package net.mamoe.mirai.qqandroid.network;

import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.intrinsics.SupervisorJob;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class QQAndroidBotNetworkHandler implements BotNetworkHandler {
    private QQAndroidBot bot;
    private CompletableJob supervisor;
    private Socket channel;
    private ExecutorService PacketReceiveDispatcher;
    private ExecutorService PacketProcessDispatcher;
    private ByteReadPacket cachedPacket;
    private long expectingRemainingLength;
    private ConcurrentLinkedQueue<PacketListener> packetListeners;
    private ReentrantLock lock;

    public QQAndroidBotNetworkHandler(QQAndroidBot bot) {
        this.bot = bot;
        this.supervisor = new SupervisorJob(bot.coroutineContext.getJob());
        this.PacketReceiveDispatcher = Executors.newSingleThreadExecutor();
        this.PacketProcessDispatcher = Executors.newSingleThreadExecutor();
        this.packetListeners = new ConcurrentLinkedQueue<>();
        this.lock = new ReentrantLock();
    }



       @Override
        public void login() {
            channel = new PlatformSocket();
            channel.connect("113.96.13.208", 8080);

            new Thread(() -> {
                try {
                    processReceive();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, "Incoming Packet Receiver").start();

            bot.logger.info("Trying login");
            LoginPacket.LoginPacketResponse response;
            while (true) {
                response = sendAndExpect(new LoginPacket.SubCommand9(bot.client));
                if (response instanceof UnsafeLogin) {
                    bot.logger.info("Login unsuccessful, device auth is needed");
                    bot.logger.info("登陆失败, 原因为非常用设备登陆");
                    bot.logger.info("Open the following URL in QQ browser and complete the verification");
                    bot.logger.info("将下面这个链接在QQ浏览器中打开并完成认证后尝试再次登陆");
                    bot.logger.info(((UnsafeLogin) response).url);
                    return;
                } else if (response instanceof Captcha) {
                    if (response instanceof Captcha.Picture) {
                        bot.logger.info("需要图片验证码");
                        String result = bot.configuration.loginSolver.onSolvePicCaptcha(bot, ((Captcha.Picture) response).data);
                        if (result == null || result.length() != 4) {
                            result = "ABCD";
                        }
                        bot.logger.info("提交验证码");
                        response = sendAndExpect(new LoginPacket.SubCommand2(bot.client, ((Captcha.Picture) response).sign, result));
                        continue;
                    } else if (response instanceof Captcha.Slider) {
                        bot.logger.info("需要滑动验证码");
                        // TODO("滑动验证码")
                    }
                } else if (response instanceof Error) {
                    error(response.toString());
                    return;
                } else if (response instanceof SMSVerifyCodeNeeded) {
                    String result = bot.configuration.loginSolver.onGetPhoneNumber();
                    response = sendAndExpect(new LoginPacket.SubCommand7(
                            bot.client,
                            ((SMSVerifyCodeNeeded) response).t174,
                            ((SMSVerifyCodeNeeded) response).t402,
                            result
                    ));
                    continue;
                } else if (response instanceof Success) {
                    bot.logger.info("Login successful");
                    break;
                }
            }

            System.out.println("d2key=" + bot.client.wLoginSigInfo.d2Key.toUHexString());
            sendAndExpect(StatSvc.Register.create(bot.client));
        }

    private void processReceive() throws IOException {
        while (channel.isConnected()) {
            ReadableByteChannel rawInput = channel.readChannel();
            if (rawInput != null) {
                PacketReceiveDispatcher.submit(() -> {
                    try {
                        processPacket(rawInput);
                    } catch (Throwable e) {
                        bot.getLogger().error("Caught unexpected exceptions", e);
                    }
                });
            }
        }
    }

    @UseExperimental(ExperimentalCoroutinesApi.class)
    internal void processPacket(ByteReadChannel rawInput) {
        Buffer input = rawInput.debugPrint("Received");
        if (input.remaining() == 0L) {
            return;
        }

        Buffer cachedPacket = null;
        int length;
        if (cachedPacket == null) {
            length = input.readInt() - 4;
            if (input.remaining() == length) {
                parsePacketAsync(input);
                return;
            }

            while (input.remaining() > length) {
                parsePacketAsync(input.read(length));

                length = input.readInt() - 4;
            }

            if (input.remaining() != 0L) {
                int expectingRemainingLength = length - input.remaining();
                cachedPacket = input;
            } else {
                cachedPacket = null;
            }
        } else {
            if (input.remaining() >= expectingRemainingLength) {
                parsePacketAsync(buildPacket(
                    packet -> {
                        writePacket(cachedPacket, packet);
                        writePacket(input, expectingRemainingLength, packet);
                    }
                ));
                cachedPacket = null;

                if (input.remaining() != 0L) {
                    processPacket(input);
                }
            } else {
                expectingRemainingLength -= input.remaining();
                cachedPacket = buildPacket(
                    packet -> {
                        writePacket(cachedPacket, packet);
                        writePacket(input, packet);
                    }
                );
            }
        }
    }


      public void parsePacket(Input input) {
        try {
            KnownPacketFactories.parseIncomingPacket(bot, input, (packetFactory, packet, commandName, sequenceId) -> {
                for (PacketListener listener : packetListeners) {
                    if (listener.filter(commandName, sequenceId) && packetListeners.remove(listener)) {
                        listener.complete(packet);
                    }
                }

                if (PacketReceivedEvent.broadcast(packet).isCancelled()) {
                    return;
                }

                if (packet instanceof Subscribable) {
                    if (packet instanceof BroadcastControllable) {
                        if (((BroadcastControllable) packet).shouldBroadcast()) {
                            ((BroadcastControllable) packet).broadcast();
                        }
                    } else {
                        packet.broadcast();
                    }

                    if (packet instanceof Cancellable && ((Cancellable) {
                        if (((Cancellable) packet).isCancelled()) {
                            return;
                        }
                    }
                }

                packetFactory.run(packet -> packet.handle(bot));

                bot.logger.info(packet.toString());
            });
            } finally {
                System.out.println();
                System.out.println();
            }
        }
    }
    }


    @Override
    public Job parsePacketAsync(IoBuffer input, IoBuffer.Pool pool) {
        return PacketProcessDispatcher.submit(() -> {
            try {
                parsePacket(input);
            } finally {
                input.discard();
                input.release(pool);
            }
        });
    }

    @Override
    public Job parsePacketAsync(Input input) {
        if (input instanceof IoBuffer) {
            throw new IllegalArgumentException("input cannot be IoBuffer");
        }
        return PacketProcessDispatcher.submit(() -> {
            try {
                parsePacket(input);
            } finally {
                input.close();
            }
        });
    }

    @Override
    public void dispose(Throwable cause) {
        System.out.println("Closed");
        super.dispose(cause);
    }

        @Override
    public void awaitDisconnection() throws InterruptedException {
        supervisor.getJob().join();
    }

    public static class PacketListener implements CompletableDeferred<Packet> {
        private final String commandName;
        private final int sequenceId;

        public PacketListener(String commandName, int sequenceId) {
            this.commandName = commandName;
            this.sequenceId = sequenceId;
        }

        public boolean filter(String commandName, int sequenceId) {
            return this.commandName.equals(commandName) && this.sequenceId == sequenceId;
        }
    }
}