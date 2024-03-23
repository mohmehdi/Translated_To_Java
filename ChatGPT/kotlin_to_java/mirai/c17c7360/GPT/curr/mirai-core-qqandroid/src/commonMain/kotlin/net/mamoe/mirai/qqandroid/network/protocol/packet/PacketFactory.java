package net.mamoe.mirai.qqandroid.network.protocol.packet;

import kotlinx.coroutines.ExperimentalCoroutinesApi;
import kotlinx.coroutines.channels.Channel.Factory;
import kotlinx.coroutines.channels.SendChannel;
import kotlinx.coroutines.channels.SendChannel.DefaultImpls;
import kotlinx.coroutines.channels.SendChannelKt;
import kotlinx.coroutines.channels.SendChannelKt__Channels_commonKt;
import kotlinx.coroutines.selects.SelectBuilder;
import kotlinx.coroutines.selects.SelectBuilderImpl;
import kotlinx.coroutines.selects.SelectClause1;
import kotlinx.coroutines.selects.SelectInstance;
import kotlinx.coroutines.selects.SelectInstanceImpl;
// import kotlinx.io.ByteReadPacket;
import kotlinx.io.ByteArrayPool;
import kotlinx.io.core.BytePacketBuilder;
import kotlinx.io.core.IoBuffer;
import kotlinx.io.core.buildPacket;
import kotlinx.io.core.readBytes;
import kotlinx.io.core.readTextExactBytes;
import kotlinx.io.core.writeText;
import kotlinx.serialization.KSerializer;
import kotlinx.serialization.SerializationException;
import net.mamoe.mirai.qqandroid.QQAndroidBot;
import net.mamoe.mirai.qqandroid.io.serialization.loadAs;
import net.mamoe.mirai.qqandroid.network.protocol.packet.chat.receive.MessageSvc;
import net.mamoe.mirai.qqandroid.network.protocol.packet.chat.receive.OnlinePush;
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.LoginPacket;
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.StatSvc;
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.data.RequestPacket;
import net.mamoe.mirai.utils.DefaultLogger;
import net.mamoe.mirai.utils.MiraiLogger;
import net.mamoe.mirai.utils.cryptor.CryptorKt;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
// import java.util.ArrayList;
import java.util.List;

import static kotlin.experimental.ExperimentalTypeInference.INSTANCE;

@ExperimentalUnsignedTypes
abstract class PacketFactory<TPacket extends Packet> {
    final String commandName;

    PacketFactory(String commandName) {
        this.commandName = commandName;
    }

    abstract TPacket decode(@NotNull QQAndroidBot bot, @NotNull ByteReadPacket packet) throws IOException;

    void handle(@NotNull QQAndroidBot bot, @NotNull TPacket packet) throws IOException {}
    
    internal <I extends IoBuffer, R> R withUse(I ioBuffer, IoBlock<I, R> block) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE);
    }
    try {
        return block.invoke(ioBuffer);
    } finally {
        ioBuffer.release(IoBuffer.Pool.INSTANCE);
    }
}
}

@ExperimentalCoroutinesApi
final class KnownPacketFactories extends ArrayList<PacketFactory<?>> {
    static final byte[] DECRYPTER_16_ZERO = new byte[16];
    static final MiraiLogger PacketLogger = new DefaultLogger("Packet");

    static PacketFactory<?> findPacketFactory(String commandName) {
        for (PacketFactory<?> factory : INSTANCE) {
            if (factory.commandName.equals(commandName)) {
                return factory;
            }
        }
        return null;
    }

    static void parseIncomingPacket(@NotNull QQAndroidBot bot, @NotNull ByteReadPacket rawInput, PacketConsumer<?> consumer) throws IOException {
        try {
            byte[] bytes = rawInput.readBytes().readArray();
            PacketLogger.verbose("开始处理包: " + bytes);
            ByteReadPacket packet = rawInput.readBytes().readArray().toReadPacket();
            if (packet.getRemaining() < Integer.MAX_VALUE) {
                int flag1 = packet.readInt();

                PacketLogger.verbose("flag1(0A/0B) = " + Byte.toUnsignedInt((byte) flag1));
                int flag2 = packet.readByte();
                PacketLogger.verbose("包类型(flag2) = " + flag2 + ". (可能是 " + (flag2 == 2 ? "OicqRequest" : "Uni") + ")");

                int flag3 = packet.readByte();
                if (flag3 != 0) {
                    throw new IOException("Illegal flag3. Expected 0, got " + flag3);
                }

                packet.readTextExactBytes(packet.readInt() - 4);

                byte[] data = new byte[packet.remaining()];
                packet.readFully(data);
                ByteArrayPool.useInstance(bytes, byteArray -> {
                    int size = packet.readAvailable(byteArray);
                    try {
                        ByteReadPacket decryptedData;
                        if (flag2 == 2) {
                            PacketLogger.verbose("SSO, 尝试使用 16 zero 解密.");
                            decryptedData = CryptorKt.decryptBy(bytes, DECRYPTER_16_ZERO, size);
                            PacketLogger.verbose("成功使用 16 zero 解密");
                        } else {
                            PacketLogger.verbose("Uni, 尝试使用 d2Key 解密.");
                            decryptedData = CryptorKt.decryptBy(bytes, bot.client.wLoginSigInfo.d2Key, size);
                            PacketLogger.verbose("成功使用 d2Key 解密");
                        }

                        if (decryptedData != null) {
                            IncomingPacket incomingPacket = parseSsoFrame(bot, decryptedData);
                            if (incomingPacket.packetFactory != null) {
                                if (incomingPacket.packetFactory instanceof PacketFactory) {
                                    PacketFactory<?> factory = (PacketFactory<?>) incomingPacket.packetFactory;
                                    T packet1 = factory.decode(bot, incomingPacket.data);
                                    consumer.invoke(factory, packet1, factory.commandName, incomingPacket.sequenceId);
                                }
                            }
                        }
                    } catch (Exception e) {
                        PacketLogger.verbose("失败, 尝试其他各种key");
                        bot.client.tryDecryptOrNull(bytes, size, decryptedBytes -> {
                            try {
                                return new ByteReadPacket(decryptedBytes);
                            } catch (IOException e1) {
                                return null;
                            }
                        });
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private IncomingPacket parseUniFrame(@NotNull QQAndroidBot bot, @NotNull ByteReadPacket rawInput) throws IOException {
    try {
        int sequenceId;
        byte[] bytes1 = rawInput.readBytes(rawInput.readInt() - 4).readArray();
        ByteArrayPool.useInstance(bytes1, byteArray -> {
            try {
                sequenceId = byteArray.readInt();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        byte[] bytes2 = rawInput.readBytes(rawInput.readInt() - 4).readArray();
        ByteArrayPool.useInstance(bytes2, byteArray -> {
            PacketLogger.verbose("收到 UniPacket 的 body=" + byteArray.readBytes().toUHexString());
        });

        // return new IncomingPacket(null, 0, null);
        return null;
    } catch (IOException e) {
        e.printStackTrace();
    }
    return null;
}

    static class IncomingPacket {
        final PacketFactory<?> packetFactory;
        final int sequenceId;
        final ByteReadPacket data;

        IncomingPacket(PacketFactory<?> packetFactory, int sequenceId, ByteReadPacket data) {
            this.packetFactory = packetFactory;
            this.sequenceId = sequenceId;
            this.data = data;
        }
    }

    static IncomingPacket parseSsoFrame(@NotNull QQAndroidBot bot, @NotNull ByteReadPacket input) throws IOException {
        String commandName;
        int ssoSequenceId;

        int sequenceId = input.readInt();
        PacketLogger.verbose("sequenceId = " + sequenceId);
        if (input.readInt() == 0) {
            byte[] extraData = input.readBytes(input.readInt() - 4);
            PacketLogger.verbose("sso(inner)extraData = " + extraData);

            commandName = input.readTextExactBytes(input.readInt() - 4);
            byte[] unknown = input.readBytes(input.readInt() - 4);
            if (unknown[0] != 2) {
                DebugLogger.debug("got new unknown: " + unknown);
            }

            if (input.readInt() == 0) {
                PacketFactory<?> packetFactory = findPacketFactory(commandName);
                bot.logger.verbose(commandName);
                if (packetFactory == null) {
                    bot.logger.warning("找不到包 PacketFactory");
                    PacketLogger.verbose("传递给 PacketFactory 的数据 = " + input.readBytes());
                }
                return new IncomingPacket(packetFactory, ssoSequenceId, input);
            }
        }
        return null;
    }

    private void parseUniResponse(@NotNull QQAndroidBot bot, @NotNull PacketFactory<?> packetFactory, int ssoSequenceId, @NotNull PacketConsumer<Packet> consumer) throws IOException {
    try {
        byte[] bytes = readBytes(readInt() - 4).readArray();
        RequestPacket uni = ByteArrayPool.useInstance(bytes, byteArray -> {
            try {
                return byteArray.loadAs(RequestPacket.Companion.getSerializer());
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
        PacketLogger.verbose(uni.toString());
    } catch (IOException e) {
        e.printStackTrace();
    }
}
}
