
package net.mamoe.mirai.qqandroid.network.protocol.packet;

import kotlinx.io.core.*;
import kotlinx.io.pool.IoBuffer;
import kotlinx.io.pool.IoBufferPool;
import net.mamoe.mirai.data.Packet;
import net.mamoe.mirai.qqandroid.QQAndroidBot;
import net.mamoe.mirai.qqandroid.io.serialization.decodeFromByteArray;
import net.mamoe.mirai.qqandroid.network.protocol.packet.chat.receive.MessageSvc;
import net.mamoe.mirai.qqandroid.network.protocol.packet.chat.receive.OnlinePush;
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.LoginPacket;
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.StatSvc;
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.data.RequestPacket;
import net.mamoe.mirai.utils.DefaultLogger;
import net.mamoe.mirai.utils.MiraiLogger;
import net.mamoe.mirai.utils.cryptor.adjustToPublicKey;
import net.mamoe.mirai.utils.cryptor.decryptBy;
import net.mamoe.mirai.utils.io.IOExceptionKt;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings("unused")
public abstract class PacketFactory<TPacket extends Packet> {

    private final String commandName;

    public PacketFactory(String commandName) {
        this.commandName = commandName;
    }

    public abstract TPacket decode(ByteReadPacket packet, QQAndroidBot bot) throws IOExceptionKt;

}

public final class KnownPacketFactories {

    private static final List<PacketFactory<? extends Packet>> packetFactories = new CopyOnWriteArrayList<>();

    static {
        packetFactories.add(new LoginPacket());
        packetFactories.add(new StatSvc.Register());
        packetFactories.add(new OnlinePush.PbPushGroupMsg());
        packetFactories.add(new MessageSvc.PushNotify());
    }

    public static PacketFactory<? extends Packet> findPacketFactory(String commandName) {
        return packetFactories.stream()
                .filter(packetFactory -> Objects.equals(packetFactory.commandName, commandName))
                .findFirst()
                .orElse(null);
    }

    public static void parseIncomingPacket(QQAndroidBot bot, Input rawInput, PacketConsumer consumer) throws IOExceptionKt {
        byte[] inputBytes = rawInput.readBytes();
        String hexString = ByteBuffer.wrap(inputBytes).asCharBuffer(StandardCharsets.UTF_8).toString();
        DefaultLogger.verbose("开始处理包: " + hexString);
        ByteBuffer inputBuffer = ByteBuffer.wrap(inputBytes);
        ByteReadPacket inputPacket = new ByteBufferReadPacket(inputBuffer);
        int flag1 = inputPacket.readInt();
        DefaultLogger.verbose("flag1(0A/0B) = " + Integer.toHexString(flag1));
        byte flag2 = inputPacket.readByte();
        int flag2Int = Byte.toUnsignedInt(flag2);
        DefaultLogger.verbose("包类型(flag2) = " + flag2Int + ". (可能是 OicqRequest 或 Uni)");
        byte flag3 = inputPacket.readByte();
        if (flag3 != 0) {
            throw new IOExceptionKt("Illegal flag3. Expected 0, got " + flag3);
        }
        inputPacket.readString(inputPacket.readInt() - 4);
        IoBuffer dataBuffer = IoBufferPool.useInstance(inputBuffer.slice());
        int dataSize = inputPacket.readInt();
        DefaultLogger.verbose("数据包大小 = " + dataSize);
        if (flag2Int == 2) {
            byte[] decryptedData = dataBuffer.decryptBy(DECRYPTER_16_ZERO, dataSize);
            DefaultLogger.verbose("成功使用 16 zero 解密");
            ByteBuffer decryptedBuffer = ByteBuffer.wrap(decryptedData);
            ByteReadPacket decryptedPacket = new ByteBufferReadPacket(decryptedBuffer);
            parseSsoFrame(bot, decryptedPacket);
        } else {
            byte[] decryptedData = dataBuffer.decryptBy(bot.getClient().getWLoginSigInfo().getD2Key(), dataSize);
            DefaultLogger.verbose("成功使用 d2Key 解密");
            ByteBuffer decryptedBuffer = ByteBuffer.wrap(decryptedData);
            ByteReadPacket decryptedPacket = new ByteBufferReadPacket(decryptedBuffer);
            parseUniFrame(bot, decryptedPacket);
        }
    }

    private static void parseSsoFrame(QQAndroidBot bot, ByteReadPacket input) throws IOExceptionKt {
        int ssoSequenceId;
        String commandName;
        IoBuffer extraDataBuffer = IoBufferPool.useInstance(input.readIoBuffer(input.readInt() - 4));
        ssoSequenceId = input.readInt();
        DefaultLogger.verbose("sequenceId = " + ssoSequenceId);
        input.discardExact(4);
        byte[] extraData = new byte[extraDataBuffer.remaining()];
        extraDataBuffer.get(extraData);
        DefaultLogger.verbose("sso(inner)extraData = " + ByteBuffer.wrap(extraData).asCharBuffer(StandardCharsets.UTF_8).toString());
        commandName = input.readString(input.readInt() - 4);
        input.discardExact(4);
        byte[] unknown = new byte[input.readInt() - 4];
        input.get(unknown);
        if (ByteBuffer.wrap(unknown).asCharBuffer(StandardCharsets.UTF_8).toString().toInt() != 0x2B05B8B) {
            DefaultLogger.debug("got new unknown: " + ByteBuffer.wrap(unknown).asCharBuffer(StandardCharsets.UTF_8).toString());
        }
        input.discardExact(4);
        PacketFactory<? extends Packet> packetFactory = findPacketFactory(commandName);
        DefaultLogger.verbose(commandName);
        if (packetFactory == null) {
            DefaultLogger.warning("找不到包 PacketFactory");
            DefaultLogger.verbose("传递给 PacketFactory 的数据 = " + input.readBytes().toUHexString());
        }
    }

    private void parseOicqResponse(ByteReadPacket this, QQAndroidBot bot, PacketFactory packetFactory, int ssoSequenceId, PacketConsumer consumer) {
    long qq;
    this.readIoBuffer(this.readInt() - 4).use(new Consumer() {
        @Override
        public void accept(IoBuffer ioBuffer) {
        check(ioBuffer.get() & 0xFF == 2);
        ioBuffer.skip(2); // discardExact(2)
        ioBuffer.skip(2); // discardExact(2)
        ioBuffer.order(ByteOrder.LITTLE_ENDIAN);
        short a = ioBuffer.getShort();
        short b = ioBuffer.getShort();
        qq = (long) ioBuffer.getUnsignedShort() & 0xFFFF L;
        int encryptionMethod = ioBuffer.getUnsignedShort() & 0xFFFF;

        ioBuffer.skip(1); // discardExact(1)
        IoBuffer data;
        if (encryptionMethod == 4) {
            byte[] decryptedData = this.decryptBy(bot.client.ecdh.keyPair.initialShareKey, ioBuffer.remaining() - 1);

            ECDH.KeyPair keyPair = bot.client.ecdh.keyPair;
            byte[] peerPublicKey = bot.client.ecdh.calculateShareKeyByPeerPublicKey(keyPair.getPublicKey().adjustToPublicKey());
            decryptedData = this.decryptBy(peerPublicKey, decryptedData);

            data = packetFactory.decode(bot, new ByteReadPacket(decryptedData));
        } else if (encryptionMethod == 0) {
            byte[] dataBytes;
            if (bot.client.loginState == 0) {
            ByteArrayBuffer byteArrayBuffer = ByteArrayPool.useInstance();
            int size = ioBuffer.remaining() - 1;
            dataBytes = new byte[size];
            ioBuffer.get(dataBytes);

            dataBytes = runCatching(() -> this.decryptBy(bot.client.ecdh.keyPair.initialShareKey, dataBytes)).getOrElse(() -> this.decryptBy(bot.client.randomKey, dataBytes));
            } else {
            dataBytes = this.decryptBy(bot.client.randomKey, 0, ioBuffer.remaining() - 1);
            }
            data = packetFactory.decode(bot, new ByteReadPacket(dataBytes));
        } else {
            throw new IllegalStateException("Illegal encryption method. expected 0 or 4, got " + encryptionMethod);
        }

        consumer.accept(data, packetFactory.commandName, ssoSequenceId);
        }
    });
    }

 public void parseUniResponse(QQAndroidBot bot, PacketFactory packetFactory, int ssoSequenceId, PacketConsumer consumer) {
        try {
            BufferedSource source = byteReadChannel.getSource();
            byte[] requestPacketBytes = source.readByteArray(source.readInt() - 4);
            RequestPacket uni = RequestPacket.serializer.decodeFromByteArray(requestPacketBytes);
            PacketLogger.verbose(uni.toString());
        } catch (IOException e) {
            // handle exception
        }
    }

    private static void parseUniFrame(QQAndroidBot bot, ByteReadPacket input) throws IOExceptionKt {
        int sequenceId;
        IoBuffer bodyBuffer = IoBufferPool.useInstance(input.readIoBuffer(input.readInt() - 4));
        sequenceId = input.readInt();
        DefaultLogger.verbose("sequenceId = " + sequenceId);
        bodyBuffer.discardExact(4);
        DefaultLogger.verbose("收到 UniPacket 的 body=" + bodyBuffer.readBytes().toUHexString());
    }

    private static final byte[] DECRYPTER_16_ZERO = new byte[16];

    public interface PacketConsumer {
        void accept(Packet packet, String commandName, int ssoSequenceId) throws IOExceptionKt;
    }
    public class IncomingPacket {
    private final PacketFactory<?> packetFactory;
    private final int sequenceId;
    private final ByteReadPacket data;

        public IncomingPacket(PacketFactory<?> packetFactory, int sequenceId, ByteReadPacket data) {
            this.packetFactory = packetFactory;
            this.sequenceId = sequenceId;
            this.data = data;
        }
    }

    private static final MiraiLogger PacketLogger = DefaultLogger.create("Packet");
}