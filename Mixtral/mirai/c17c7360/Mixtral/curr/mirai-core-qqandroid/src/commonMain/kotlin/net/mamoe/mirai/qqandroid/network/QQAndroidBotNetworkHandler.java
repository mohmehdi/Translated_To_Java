
package net.mamoe.mirai.qqandroid.network;

import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.intrinsics.*;
import kotlinx.coroutines.*;
import kotlinx.io.core.*;
import kotlinx.io.pool.ObjectPool;
import net.mamoe.mirai.data.Packet;
import net.mamoe.mirai.event.BroadcastControllable;
import net.mamoe.mirai.event.Cancellable;
import net.mamoe.mirai.event.Subscribable;
import net.mamoe.mirai.event.broadcast;
import net.mamoe.mirai.network.BotNetworkHandler;
import net.mamoe.mirai.qqandroid.QQAndroidBot;
import net.mamoe.mirai.qqandroid.event.PacketReceivedEvent;
import net.mamoe.mirai.qqandroid.network.protocol.packet.KnownPacketFactories;
import net.mamoe.mirai.qqandroid.network.protocol.packet.OutgoingPacket;
import net.mamoe.mirai.qqandroid.network.protocol.packet.PacketFactory;
import net.mamoe.mirai.qqandroid.network.protocol.packet.PacketFactory$PacketFactoryDslMarker;
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.LoginPacket;
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.LoginPacket.LoginPacketResponse;
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.StatSvc;
import net.mamoe.mirai.utils.*;
import net.mamoe.mirai.utils.io.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QQAndroidBotNetworkHandler implements BotNetworkHandler {
    private QQAndroidBot bot;
    private CompletableJob supervisor;
    private PlatformSocket channel;
    private ByteReadPacket cachedPacket;
    private long expectingRemainingLength;
    private ExecutorService PacketReceiveDispatcher;
    private ExecutorService PacketProcessDispatcher;
    private ConcurrentLinkedQueue<PacketListener> packetListeners;

    public QQAndroidBotNetworkHandler(QQAndroidBot bot) {
        this.bot = bot;
        this.supervisor = new SupervisorJob(bot.coroutineContext[Job]);
        this.PacketReceiveDispatcher = Executors.newSingleThreadExecutor();
        this.PacketProcessDispatcher = Executors.newSingleThreadExecutor();
        this.packetListeners = new ConcurrentLinkedQueue<>();
    }


  

    @Override
    public void login() {
        this.channel = new PlatformSocket();
        this.channel.connect("113.96.13.208", 8080);
        new GlobalScope(this.PacketReceiveDispatcher).launch(new CoroutineName("Incoming Packet Receiver")) {

        };

        bot.logger.info("Trying login");
        LoginPacket.LoginPacketResponse response;
        while (true) {
            response = LoginPacket.SubCommand9.INSTANCE.sendAndExpect(bot.client);
            if (response instanceof UnsafeLogin) {
                bot.logger.info("Login unsuccessful, device auth is needed");
                bot.logger.info("登陆失败, 原因为非常用设备登陆");
                bot.logger.info("Open the following URL in QQ browser and complete the verification");
                bot.logger.info("将下面这个链接在QQ浏览器中打开并完成认证后尝试再次登陆");
                bot.logger.info(response.url);
                return;
            } else if (response instanceof Captcha) {
                if (response instanceof Captcha.Picture) {
                    bot.logger.info("需要图片验证码");
                    String result = bot.configuration.loginSolver.onSolvePicCaptcha(bot, response.data);
                    if (result == null || result.length() != 4) {
                        result = "ABCD";
                    }
                    bot.logger.info("提交验证码");
                    response = LoginPacket.SubCommand2.INSTANCE.sendAndExpect(bot.client, response.sign, result);
                    continue;
                } else if (response instanceof Captcha.Slider) {
                    bot.logger.info("需要滑动验证码");
                    // TODO: Implement slider captcha
                }
            } else if (response instanceof Error) {
                throw new RuntimeException(response.toString());
            } else if (response instanceof SMSVerifyCodeNeeded) {
                String result = bot.configuration.loginSolver.onGetPhoneNumber();
                response = LoginPacket.SubCommand7.INSTANCE.sendAndExpect(bot.client, response.t174, response.t402, result);
                continue;
            } else if (response instanceof Success) {
                bot.logger.info("Login successful");
                break;
            }
        }

        System.out.println("d2key=" + bot.client.wLoginSigInfo.d2Key.toUHexString());
        StatSvc.Register.INSTANCE.sendAndExpect(bot.client);
    }

    private void processReceive() {
        while (channel.isOpen()) {
            try {
                ByteReadPacket rawInput = channel.read();
                if (rawInput == null) {
                    return;
                }
                PacketReceiveDispatcher.submit(() -> processPacket(rawInput));
            } catch (ClosedChannelException e) {
                dispose(e);
                return;
            } catch (ReadPacketInternalException e) {
                bot.logger.error("Socket channel read failed: " + e.getMessage());
                continue;
            } catch (CancellationException e) {
                return;
            } catch (Throwable e) {
                bot.logger.error("Caught unexpected exceptions", e);
                continue;
            }
        }
    }

    private void processPacket(ByteReadPacket input) {
        if (input.remaining() == 0) {
            return;
        }

        if (cachedPacket == null) {
            int length = input.readInt() - 4;
            if (input.remaining() == length) {
                parsePacketAsync(input);
                return;
            }

            while (input.remaining() > length) {
                parsePacketAsync(input.readIoBuffer(length));

                length = input.readInt() - 4;
            }

            if (input.remaining() != 0) {
                expectingRemainingLength = length - input.remaining();
                cachedPacket = input;
            } else {
                cachedPacket = null;
            }
        } else {
            if (input.remaining() >= expectingRemainingLength) {
                parsePacketAsync(buildPacket(cachedPacket, input, expectingRemainingLength));
                cachedPacket = null;

                if (input.remaining() != 0) {
                    processPacket(input);
                }
            } else {
                expectingRemainingLength -= input.remaining();
                cachedPacket = buildPacket(cachedPacket, input);
            }
        }
    }

  

    public Job parsePacketAsync(IoBuffer input, IoBuffer.Pool pool) {
        return this.launch(PacketProcessDispatcher.class, new Function0<Job>() {
            @Override
            public Job invoke() {
                IoBuffer inputClone = input.clone();
                try {
                    parsePacket(inputClone);
                    return Job.COMPLETE;
                } catch (Exception e) {
                    return Job.FAILED;
                } finally {
                    inputClone.discard();
                    if (pool != null) {
                        inputClone.release(pool);
                    }
                }
            }
        });
    }

    private void parsePacketAsync(Input input) {
        if (input instanceof IoBuffer) {
            parsePacketAsync((IoBuffer) input);
        } else {
            GlobalScope.launch(PacketProcessDispatcher + new CoroutineName("Incoming Packet handler")) {
                try {
                    parsePacket(input);
                } finally {
                    input.dispose();
                }
            };
        }
    }

    private void parsePacket(Input input) {
        try {
            KnownPacketFactories.parseIncomingPacket(bot, input, (packetFactory, packet, commandName, sequenceId) -> {
                for (PacketListener listener : packetListeners) {
                    if (listener.filter(commandName, sequenceId) && packetListeners.remove(listener)) {
                        listener.complete(packet);
                    }
                }

                if (PacketReceivedEvent.INSTANCE.broadcast(packet).cancelled) {
                    return;
                }

                if (packet instanceof Subscribable) {
                    if (packet instanceof BroadcastControllable) {
                        if (((BroadcastControllable) packet).shouldBroadcast()) {
                            ((Subscribable) packet).broadcast();
                        }
                    } else {
                        ((Subscribable) packet).broadcast();
                    }

                    if (packet instanceof Cancellable && ((Cancellable) packet).cancelled()) {
                        return;
                    }
                }

                packetFactory.run(packet.handle(bot));

                bot.logger.info(packet);
            });
        } finally {
            System.out.println();
            System.out.println();
        }
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
    @Override
    public void awaitDisconnection() throws InterruptedException {
        supervisor.getJob().join();
    }
    @Override
    public void dispose(Throwable cause) {
        System.out.println("Closed");
        super.dispose(cause);
    }

    private final CoroutineContext coroutineContext = ...; // Initialize the context

}