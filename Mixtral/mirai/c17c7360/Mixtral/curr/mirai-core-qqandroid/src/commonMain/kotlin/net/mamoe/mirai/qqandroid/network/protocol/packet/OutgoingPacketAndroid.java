package net.mamoe.mirai.qqandroid.network.protocol.packet;

import kotlin.io.BytePacketBuilder;
import kotlin.io.ByteReadPacket;
import kotlin.io.ExperimentalIOException;
import kotlin.io.IOException;
import kotlin.io.encoder.encodeToByteArray;
import net.mamoe.mirai.qqandroid.network.QQAndroidClient;
import net.mamoe.mirai.qqandroid.network.handler.EncryptMethod;
import net.mamoe.mirai.qqandroid.network.handler.PacketFactory;
import net.mamoe.mirai.utils.MiraiInternalAPI;
import net.mamoe.mirai.utils.MiraiLogger;
import net.mamoe.mirai.utils.io.IOUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@ExperimentalIOException
public class OutgoingPacket {
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

    public static final byte[] KEY_16_ZEROS = new byte[16];
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];];
}

@ExperimentalIOException
@MiraiInternalAPI
public class OutgoingPacketFactory {


    public OutgoingPacket buildOutgoingPacket(
            QQAndroidClient client,
            byte bodyType, String name,
            String commandName, byte[] key,
            BodyBuilder body
    ) {
        int sequenceId = client.nextSsoSequenceId();

        BytePacketBuilder builder = new BytePacketBuilder();
        builder.writeIntLVPacket(4, () -> {
            builder.writeInt(0x0B);
            builder.writeByte(bodyType);
            builder.writeInt(sequenceId);
            builder.writeByte(0);
            String uinString = String.valueOf(client.getUin());
            builder.writeInt(uinString.length() + 4);
            builder.writeStringUtf8(uinString);
            encryptAndWrite(key, () -> body.accept(builder, sequenceId));
        });

        return new OutgoingPacket(name, commandName, sequenceId, builder.build());
    }

    public OutgoingPacket buildOutgoingUniPacket(
            QQAndroidClient client,
            byte bodyType, String name,
            String commandName, byte[] key,
            ExtraDataBuilder extraData,
            BodyBuilder body
    ) {
        int sequenceId = client.nextSsoSequenceId();

        BytePacketBuilder builder = new BytePacketBuilder();
        builder.writeIntLVPacket(4, () -> {
            builder.writeInt(0x0B);
            builder.writeByte(bodyType);
            builder.writeInt(sequenceId);
            builder.writeByte(0);
            String uinString = String.valueOf(client.getUin());
            builder.writeInt(uinString.length() + 4);
            builder.writeStringUtf8(uinString);
            encryptAndWrite(key, () -> body.accept(builder, sequenceId));
        });

        return new OutgoingPacket(name, commandName, sequenceId, builder.build());
    }

  
    public void writeUniPacket(
            BytePacketBuilder bytePacketBuilder,
            String commandName,
            ByteReadPacket extraData,
            PacketFactory.BodyBuilder body) {
        int packetLength = 0;

        bytePacketBuilder.writeIntLVPacket(packetLength);

        packetLength += commandName.length();
        bytePacketBuilder.writeInt(packetLength + 4);
        bytePacketBuilder.writeStringUtf8(commandName);

        packetLength = 4 + 4;
        bytePacketBuilder.writeInt(45112203);

        if (extraData == null || extraData.remaining() == 0) {
            bytePacketBuilder.writeInt(4);
        } else {
            packetLength += extraData.remaining();
            bytePacketBuilder.writeInt(packetLength + 4);
            bytePacketBuilder.writePacket(extraData);
        }

        bytePacketBuilder.writeIntLVPacket(packetLength, body);
    }

       public OutgoingPacket buildLoginOutgoingPacket(QQAndroidClient client, byte bodyType, byte[] extraData,
                                                   String name, String commandName, byte[] key,
                                                   BiConsumer<BytePacketBuilder, Integer> body) {
        int sequenceId = client.nextSsoSequenceId();

        BytePacketBuilder builder = new BytePacketBuilder();
        builder.writeIntLVPacket(it -> it + 4, () -> {
            builder.writeInt(0x00_00_00_0A);
            builder.writeByte(bodyType);
            if (extraData != null) {
                builder.writeInt(extraData.length + 4);
                builder.writeBytes(extraData);
            }
            builder.writeByte(0x00);

            String uin = String.valueOf(client.getUin());
            builder.writeInt(uin.length() + 4);
            builder.writeString(uin, StandardCharsets.UTF_8);

            EncryptUtils.encryptAndWrite(key, (encryptedData, offset, length) -> {
                body.accept(builder, sequenceId);
            }, builder.toByteArray(), 0, builder.size());
        });

        return new OutgoingPacket(name, commandName, sequenceId, builder);
    }

    private static final ByteReadPacket BRP_STUB = new ByteReadPacket(EMPTY_BYTE_ARRAY);

    public void writeSsoPacket(QQAndroidClient client, long subAppId, String commandName, ByteReadPacket extraData, int sequenceId, Body body) {
        writeIntLVPacket(
                (lengthOffset) -> {
                    writeInt(sequenceId);
                    writeInt((int) subAppId);
                    writeInt((int) subAppId);
                    writeHex("01 00 00 00 00 00 00 00 00 00 01 00");
                    if (extraData == BRPStub.INSTANCE) {
                        writeInt(0x04);
                    } else {
                        writeInt((extraData.remaining() + 4) & 0xFFFFFFFF);
                        writePacket(extraData);
                    }
                    int commandNameLength = commandName.length();
                    writeInt(commandNameLength + 4);
                    writeStringUtf8(commandName);
                    writeInt(4 + 4);
                    writeInt(45112203);
                    String imei = client.getDevice().getImei();
                    writeInt(imei.length() + 4);
                    writeStringUtf8(imei);
                    writeInt(4);
                    byte[] ksid = client.getKsid();
                    writeShort((short) ((ksid.length + 2) & 0xFFFF));
                    writeFully(ksid);
                    writeInt(4);
                },
                (builder) -> {
                    body.invoke(builder);
                }
        );
    }

    @ExperimentalUnsignedTypes
    @MiraiInternalAPI
    public void writeOicqRequestPacket(
            BytePacketBuilder bytePacketBuilder,
            QQAndroidClient client,
            EncryptMethod encryptMethod,
            int commandId,
            PacketFactory.BodyBuilder body) {
        byte[] bodyBytes = encryptMethod.makeBody(client, body);

        bytePacketBuilder.writeByte(0x02);
        bytePacketBuilder.writeShort((short) (27 + 2 + bodyBytes.length));
        bytePacketBuilder.writeShort(client.getProtocolVersion());
        bytePacketBuilder.writeShort((short) commandId);
        bytePacketBuilder.writeShort(1);
        bytePacketBuilder.writeQQ(client.getUin());
        bytePacketBuilder.writeByte((byte) 3);
        bytePacketBuilder.writeByte(encryptMethod.getId());
        bytePacketBuilder.writeByte(0);
        bytePacketBuilder.writeInt(2);
        bytePacketBuilder.writeInt(client.getAppClientVersion());
        bytePacketBuilder.writeInt(0);

        bytePacketBuilder.writePacket(BytePacketBuilder.buildPacket(bodyBytes));

        bytePacketBuilder.writeByte(0x03);
    }
}