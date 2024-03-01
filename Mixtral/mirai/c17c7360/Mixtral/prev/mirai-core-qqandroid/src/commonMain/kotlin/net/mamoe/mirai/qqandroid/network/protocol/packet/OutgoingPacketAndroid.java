package net.mamoe.mirai.qqandroid.network.protocol.packet;

import kotlinx.io.core.BytePacketBuilder;
import kotlinx.io.core.ByteReadPacket;
import kotlinx.io.core.buildPacket;
import net.mamoe.mirai.qqandroid.network.QQAndroidClient;
import net.mamoe.mirai.utils.MiraiInternalAPI;
import net.mamoe.mirai.utils.cryptor.DecrypterByteArray;
import net.mamoe.mirai.utils.cryptor.encryptAndWrite;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@SuppressWarnings("unused")
public class OutgoingPacket {
    private final String name;
    public final String commandName;
    public final int sequenceId;
    public final ByteReadPacket delegate;

    public OutgoingPacket(String name, String commandName, int sequenceId, ByteReadPacket delegate) {
        this.name = (name != null) ? name : commandName;
        this.commandName = commandName;
        this.sequenceId = sequenceId;
        this.delegate = delegate;
    }


    public static final byte[] KEY_16_ZEROS = new byte[16];
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
}

@SuppressWarnings("unused")
public class OutgoingPacketFactory {

    @MiraiInternalAPI
    public OutgoingPacket buildOutgingPacket(
            QQAndroidClient client,
            String name,
            String commandName,
            byte[] key,
            BodyBuilder bodyBuilder
    ) {
        int sequenceId = client.nextSsoSequenceId();

        BytePacketBuilder packetBuilder = buildPacket();

        packetBuilder.writeIntLVPacket(lengthOffset -> {
            packetBuilder.writeInt(0x0B);
            packetBuilder.writeByte(1);
            packetBuilder.writeInt(sequenceId);
            packetBuilder.writeByte(0);
            String uinString = String.valueOf(client.getUin());
            packetBuilder.writeInt(uinString.length() + 4);
            packetBuilder.writeStringUtf8(uinString);
            encryptAndWrite(packetBuilder, key, sequenceId, bodyBuilder);
            return null;
        });

        return new OutgoingPacket(name, commandName, sequenceId, packetBuilder.build());
    }

    @MiraiInternalAPI
    public OutgoingPacket buildLoginOutgoingPacket(
            QQAndroidClient client,
            byte bodyType,
            byte[] extraData,
            String name,
            String commandName,
            byte[] key,
            BodyBuilder bodyBuilder
    ) {
        int sequenceId = client.nextSsoSequenceId();

        BytePacketBuilder packetBuilder = buildPacket();

        packetBuilder.writeIntLVPacket(lengthOffset -> {
            packetBuilder.writeInt(0x00_00_00_0A);
            packetBuilder.writeByte(bodyType);
            if (extraData != null) {
                packetBuilder.writeInt(extraData.length + 4);
                packetBuilder.writeFully(extraData);
            } else {
                packetBuilder.writeInt(4);
            }
            packetBuilder.writeByte(0x00);

            String uinString = String.valueOf(client.getUin());
            packetBuilder.writeInt(uinString.length() + 4);
            packetBuilder.writeStringUtf8(uinString);

            encryptAndWrite(packetBuilder, key, sequenceId, bodyBuilder);
            return null;
        });

        return new OutgoingPacket(name, commandName, sequenceId, packetBuilder.build());
    }



    public ByteReadPacket buildSessionOutgoingPacket(
            String uinAccount,
            DecrypterByteArray sessionKey,
            PacketFactory.BodyBuilder bodyBuilder
    ) {
        BytePacketBuilder packetBuilder = buildPacket();

        packetBuilder.writeIntLVPacket(lengthOffset -> {
            packetBuilder.writeInt(0x00_00_00_0B);
            packetBuilder.writeByte(0x01);

            packetBuilder.writeByte(0);

            packetBuilder.writeInt(uinAccount.length() + 4);
            packetBuilder.writeStringUtf8(uinAccount);

            sessionKey.encryptAndWrite(packetBuilder, bodyBuilder);
            return null;
        });

        return packetBuilder.build();
    }

    @SuppressWarnings("SameParameterValue")
    public void writeOicqRequestPacket(
            QQAndroidClient client,
            EncryptMethod encryptMethod,
            int commandId,
            PacketFactory.BodyBuilder bodyBuilder
    ) {
        BytePacketBuilder packetBuilder = buildPacket();

        packetBuilder.writeByte(0x02);
        packetBuilder.writeShort((short) (27 + 2 + bodyBuilder.build(packetBuilder, client).remaining));
        packetBuilder.writeShort(client.getProtocolVersion());
        packetBuilder.writeShort((short) commandId);
        packetBuilder.writeShort(1);
        packetBuilder.writeQQ(client.getUin());
        packetBuilder.writeByte(3);
        packetBuilder.writeByte(encryptMethod.getId());
        packetBuilder.writeByte(0);
        packetBuilder.writeInt(2);
        packetBuilder.writeInt(client.getAppClientVersion());
        packetBuilder.writeInt(0);

        packetBuilder.writePacket(bodyBuilder.build(packetBuilder, client));

        packetBuilder.writeByte(0x03);
    }

    public void writeSsoPacket(QQAndroidClient client, long subAppId, String commandName, ByteReadPacket extraData, int sequenceId, PacketBody body) {
        writeIntLVPacket(ar -> {
            ar.writeInt(sequenceId);
            ar.writeInt((int) subAppId);
            ar.writeInt((int) subAppId);
            ar.writeHex("01 00 00 00 00 00 00 00 00 00 01 00");
            if (extraData == BRP_STUB) {
                ar.writeInt(0x04);
            } else {
                ar.writeInt(extraData.remaining() + 4);
                ar.writePacket(extraData);
            }
            writeStringUtf8(ar, commandName);
            ar.writeInt(4 + 4);
            ar.writeInt(45112203);
            writeStringUtf8(ar, client.device.getImei());
            ar.writeInt(4);
            writeShortFully(ar, (short) (client.ksid.size() + 2));
            ar.writeFully(client.ksid.getBytes(StandardCharsets.UTF_8));
            ar.writeInt(4);
        }, body);
    }
}
