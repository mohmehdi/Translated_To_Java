package net.mamoe.mirai.qqandroid.network.protocol.packet;

import kotlin.Experimental;
import kotlin.UByte;
import kotlin.UShort;
import kotlin.io.ByteReadPacket;
import kotlin.io.ByteReadPacketKt;
import kotlin.io.BytePacketBuilder;
import kotlin.io.BytePacketBuilderKt;
import kotlin.io.CloseableKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.Charsets;

import java.util.Arrays;

import static net.mamoe.mirai.qqandroid.network.protocol.packet.Constants.*;

@Experimental
public final class OutgoingPacket {
    private final String name;
    private final String commandName;
    private final int sequenceId;
    private final ByteReadPacket delegate;

    public OutgoingPacket(String name, String commandName, int sequenceId, ByteReadPacket delegate) {
        this.name = name != null ? name : commandName;
        this.commandName = commandName;
        this.sequenceId = sequenceId;
        this.delegate = delegate;
    }
}

@Experimental
public final class PacketFactory {
    public static final byte[] KEY_16_ZEROS = new byte[16];
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    public static OutgoingPacket buildOutgoingPacket(QQAndroidClient client, byte bodyType, String name, String commandName, byte[] key, int sequenceId, BytePacketBuilder.Block body) {
        int var10000 = client.nextSsoSequenceId();
        return new OutgoingPacket(name, commandName, var10000, BytePacketBuilderKt.buildPacket(null, null, (BytePacketBuilder.InitialCapacity) null, (BytePacketBuilder.CapacityIncrement) null, (BytePacketBuilder.OnFailure) null, (BytePacketBuilder.() -> Unit) (new BytePacketBuilder(0, 0, (BytePacketBuilder.InitialCapacity) null, (BytePacketBuilder.CapacityIncrement) null, (BytePacketBuilder.OnFailure) null, bodyType, sequenceId, (BytePacketBuilder) null, 28))));
    }

    public static OutgoingPacket buildOutgoingUniPacket(QQAndroidClient client, byte bodyType, String name, String commandName, byte[] key, ByteReadPacket extraData, int sequenceId, BytePacketBuilder.Block body) {
        int var10000 = client.nextSsoSequenceId();
        return new OutgoingPacket(name, commandName, var10000, BytePacketBuilderKt.buildPacket(null, null, (BytePacketBuilder.InitialCapacity) null, (BytePacketBuilder.CapacityIncrement) null, (BytePacketBuilder.OnFailure) null, (BytePacketBuilder.() -> Unit) (new BytePacketBuilder(0, 0, (BytePacketBuilder.InitialCapacity) null, (BytePacketBuilder.CapacityIncrement) null, (BytePacketBuilder.OnFailure) null, bodyType, sequenceId, (BytePacketBuilder) null, 29))));
    }

    public static OutgoingPacket buildLoginOutgoingPacket(QQAndroidClient client, byte bodyType, byte[] extraData, String name, String commandName, byte[] key, int sequenceId, BytePacketBuilder.Block body) {
        int var10000 = client.nextSsoSequenceId();
        return new OutgoingPacket(name, commandName, var10000, BytePacketBuilderKt.buildPacket(null, null, (BytePacketBuilder.InitialCapacity) null, (BytePacketBuilder.CapacityIncrement) null, (BytePacketBuilder.OnFailure) null, (BytePacketBuilder.() -> Unit) (new BytePacketBuilder(0, 0, (BytePacketBuilder.InitialCapacity) null, (BytePacketBuilder.CapacityIncrement) null, (BytePacketBuilder.OnFailure) null, bodyType, sequenceId, (BytePacketBuilder) null, 26))));
    }

    public static void writeUniPacket(BytePacketBuilder $receiver, String commandName, ByteReadPacket extraData, BytePacketBuilder.Block body) {
        BytePacketBuilderKt.writeIntLVPacket($receiver, (BytePacketBuilder.Length) null, (BytePacketBuilder.() -> Unit) (new BytePacketBuilder(0, 0, (BytePacketBuilder.InitialCapacity) null, (BytePacketBuilder.CapacityIncrement) null, (BytePacketBuilder.OnFailure) null, (byte) 1, 0, (BytePacketBuilder) null, 26)));
    }

    public static void writeSsoPacket(BytePacketBuilder $receiver, QQAndroidClient client, long subAppId, String commandName, ByteReadPacket extraData, int sequenceId, BytePacketBuilder.Block body) {
        BytePacketBuilderKt.writeIntLVPacket($receiver, (BytePacketBuilder.Length) null, (BytePacketBuilder.() -> Unit) (new BytePacketBuilder(0, 0, (BytePacketBuilder.InitialCapacity) null, (BytePacketBuilder.CapacityIncrement) null, (BytePacketBuilder.OnFailure) null, (byte) 1, 0, (BytePacketBuilder) null, 30)));
    }

    public static void writeOicqRequestPacket(BytePacketBuilder $receiver, QQAndroidClient client, EncryptMethod encryptMethod, int commandId, BytePacketBuilder.Block bodyBlock) {
        ByteReadPacket body = encryptMethod.makeBody(client, bodyBlock);
        $receiver.writeByte((byte) 2);
        $receiver.writeShort((short) (27 + 2 + body.remaining()));
        $receiver.writeShort(client.getProtocolVersion());
        $receiver.writeShort((short) commandId);
        $receiver.writeShort((short) 1);
        $receiver.writeQQ(client.getUin());
        $receiver.writeByte((byte) 3);
        $receiver.writeByte(encryptMethod.getId());
        $receiver.writeByte((byte) 0);
        $receiver.writeInt(2);
        $receiver.writeInt(client.getAppClientVersion());
        $receiver.writeInt(0);
        $receiver.writePacket(body);
        $receiver.writeByte((byte) 3);
    }
}

