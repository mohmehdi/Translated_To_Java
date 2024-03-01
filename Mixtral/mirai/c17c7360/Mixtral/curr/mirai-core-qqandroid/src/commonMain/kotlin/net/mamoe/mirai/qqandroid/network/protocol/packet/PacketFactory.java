

package net.mamoe.mirai.qqandroid.network.protocol.packet;

import kotlinx.io.core.*;
import kotlinx.io.pool.IoBuffer;
import net.mamoe.mirai.data.Packet;
import net.mamoe.mirai.event.Subscribable;
import net.mamoe.mirai.qqandroid.QQAndroidBot;
import net.mamoe.mirai.qqandroid.io.serialization.LoadAs;
import net.mamoe.mirai.qqandroid.network.protocol.packet.chat.receive.MessageSvc;
import net.mamoe.mirai.qqandroid.network.protocol.packet.chat.receive.OnlinePush;
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.LoginPacket;
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.StatSvc;
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.data.RequestPacket;
import net.mamoe.mirai.utils.DefaultLogger;
import net.mamoe.mirai.utils.MiraiLogger;
import net.mamoe.mirai.utils.cryptor.AdjustToPublicKey;
import net.mamoe.mirai.utils.cryptor.DecryptBy;
import net.mamoe.mirai.utils.io.*;
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

    public abstract TPacket decode(ByteReadPacket byteReadPacket, QQAndroidBot qqAndroidBot) throws Exception;

    public void handle(TPacket tPacket, QQAndroidBot qqAndroidBot) throws Exception {
    }

}

public class KnownPacketFactories {
    private static final MiraiLogger PacketLogger = DefaultLogger.create("Packet");
    private static final List<PacketFactory<? extends Packet>> packetFactories = new CopyOnWriteArrayList<>();

    static {
        packetFactories.add(new LoginPacket());
        packetFactories.add(new StatSvc.Register());
        packetFactories.add(new OnlinePush.PbPushGroupMsg());
        packetFactories.add(new MessageSvc.PushNotify());
    }

    public static PacketFactory<? extends Packet> findPacketFactory(String commandName) {
        return packetFactories.stream().filter(packetFactory -> Objects.equals(packetFactory.commandName, commandName)).findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Packet> void parseIncomingPacket(QQAndroidBot qqAndroidBot, Input input, PacketConsumer<T> consumer) throws Exception {
        IoBuffer rawInput = input.readBytes();
        PacketLogger.verbose("开始处理包: " + Hex.bytesToHex(rawInput.toArray()));

        ByteReadPacket byteReadPacket = rawInput.toReadPacket();
        int flag1 = byteReadPacket.readInt();

        PacketLogger.verbose("flag1(0A/0B) = " + Integer.toHexString(flag1 & 0xFF));

        int flag2 = byteReadPacket.readByte() & 0xFF;
        PacketLogger.verbose("包类型(flag2) = " + flag2 + ". (可能是 OicqRequest 或 Uni)");

        int flag3 = byteReadPacket.readByte() & 0xFF;
        Objects.requireNonNull(qqAndroidBot);
        if (flag3 != 0) {
            throw new IllegalStateException("Illegal flag3. Expected 0, got " + flag3);
        }

        byteReadPacket.readString(byteReadPacket.readInt() - 4);

        IoBufferPool ioBufferPool = IoBufferPool.DEFAULT;
        IoBuffer data = ioBufferPool.allocate(byteReadPacket.remaining());
        byteReadPacket.readFully(data);

        byte[] decryptedData;
        if (flag2 == 2) {
            PacketLogger.verbose("SSO, 尝试使用 16 zero 解密.");
            decryptedData = DecryptBy.decryptBy(DECRYPTER_16_ZERO, data.toArray(), data.remaining());
            PacketLogger.verbose("成功使用 16 zero 解密");
        } else {
            PacketLogger.verbose("Uni, 尝试使用 d2Key 解密.");
            decryptedData = DecryptBy.decryptBy(qqAndroidBot.client.wLoginSigInfo.d2Key, data.toArray(), data.remaining());
            PacketLogger.verbose("成功使用 d2Key 解密");
        }

        byteReadPacket = IoBuffer.wrap(decryptedData).toReadPacket();

        switch (flag1) {
            case 0x0A:
            case 0x0B:
                parseSsoFrame(qqAndroidBot, byteReadPacket);
                break;
            default:
                throw new IllegalStateException("unknown flag1: " + Integer.toHexString(flag1 & 0xFF));
        }
    }

    private static void parseUniFrame(QQAndroidBot qqAndroidBot, ByteReadPacket byteReadPacket) throws Exception {
        int sequenceId = byteReadPacket.readInt();
        IoBuffer body = byteReadPacket.readIoBuffer(byteReadPacket.readInt() - 4);

        byteReadPacket.discardExact(4);

        PacketLogger.verbose("收到 UniPacket 的 body=" + Hex.bytesToHex(body.toArray()));
    }

    public static class IncomingPacket {
        private final PacketFactory<? extends Packet> packetFactory;
        private final int sequenceId;
        private final ByteReadPacket data;

        public IncomingPacket(PacketFactory<? extends Packet> packetFactory, int sequenceId, ByteReadPacket data) {
            this.packetFactory = packetFactory;
            this.sequenceId = sequenceId;
            this.data = data;
        }

    }

    private static void parseSsoFrame(QQAndroidBot qqAndroidBot, ByteReadPacket byteReadPacket) throws Exception {
        String commandName = "";
        int ssoSequenceId = 0;

        IoBuffer inner = byteReadPacket.readIoBuffer(byteReadPacket.readInt() - 4);
        ssoSequenceId = inner.readInt();
        PacketLogger.verbose("sequenceId = " + ssoSequenceId);
        inner.discardExact(4);

        byte[] extraData = new byte[inner.remaining()];
        inner.get(extraData);
        PacketLogger.verbose("sso(inner)extraData = " + Hex.bytesToHex(extraData));

        commandName = byteReadPacket.readString(byteReadPacket.readInt() - 4, StandardCharsets.UTF_8);
        IoBuffer unknown = byteReadPacket.readIoBuffer(byteReadPacket.readInt() - 4);
        if (unknown.readRemaining() != 0x02B05B8B) {
            DebugLogger.debug("got new unknown: " + Hex.bytesToHex(unknown.toArray()));
        }

        byteReadPacket.discardExact(4);

        PacketFactory<? extends Packet> packetFactory = KnownPacketFactories.findPacketFactory(commandName);

        qqAndroidBot.logger.verbose(commandName);
        if (packetFactory == null) {
            qqAndroidBot.logger.warning("找不到包 PacketFactory");
            PacketLogger.verbose("传递给 PacketFactory 的数据 = " + Hex.bytesToHex(byteReadPacket.toArray()));
        }
    }

    private static <T extends Packet> void parseOicqResponse(ByteReadPacket byteReadPacket, QQAndroidBot qqAndroidBot, PacketFactory<T> packetFactory, int ssoSequenceId, PacketConsumer<T> consumer) throws Exception {
        int qq = byteReadPacket.readUInt().toLong();
        int encryptionMethod = byteReadPacket.readUShort().toInt();

        byteReadPacket.discardExact(1);

        IoBuffer data;
        T packet;
        switch (encryptionMethod) {
            case 4:
                byte[] decryptedData = DecryptBy.decryptBy(qqAndroidBot.client.ecdh.keyPair.initialShareKey, byteReadPacket.readRemaining());
                decryptedData = DecryptBy.decryptBy(qqAndroidBot.client.ecdh.calculateShareKeyByPeerPublicKey(ReadUShortLVByteArray.readUShortLVByteArray(byteReadPacket).adjustToPublicKey()), decryptedData);
                data = IoBuffer.wrap(decryptedData);
                packet = packetFactory.decode(data.toReadPacket(), qqAndroidBot);
                break;
            case 0:
                if (qqAndroidBot.client.loginState == 0) {
                    byte[] dataBytes = new byte[byteReadPacket.readRemaining()];
                    byteReadPacket.readFully(dataBytes);
                    decryptedData = DecryptBy.decryptBy(qqAndroidBot.client.ecdh.keyPair.initialShareKey, dataBytes);
                    decryptedData = DecryptBy.decryptBy(qqAndroidBot.client.randomKey, decryptedData);
                    data = IoBuffer.wrap(decryptedData);
                } else {
                    data = IoBuffer.wrap(DecryptBy.decryptBy(qqAndroidBot.client.randomKey, byteReadPacket.readRemaining()));
                }
                packet = packetFactory.decode(data.toReadPacket(), qqAndroidBot);
                break;
            default:
                throw new IllegalStateException("Illegal encryption method. expected 0 or 4, got " + encryptionMethod);
        }

        consumer.accept(packetFactory, packet, packetFactory.commandName, ssoSequenceId);
    }

    private static <T extends Packet> void parseUniResponse(ByteReadPacket byteReadPacket, QQAndroidBot qqAndroidBot, PacketFactory<T> packetFactory, int ssoSequenceId, PacketConsumer<T> consumer) throws Exception {
        RequestPacket uni = LoadAs.loadAs(byteReadPacket.readBytes(byteReadPacket.readInt() - 4), RequestPacket.serializer());
        PacketLogger.verbose(uni.toString());
    }
}

@FunctionalInterface
public interface PacketConsumer<T extends Packet> {
    void accept(PacketFactory<T> packetFactory, T packet, String commandName, int ssoSequenceId) throws Exception;
}