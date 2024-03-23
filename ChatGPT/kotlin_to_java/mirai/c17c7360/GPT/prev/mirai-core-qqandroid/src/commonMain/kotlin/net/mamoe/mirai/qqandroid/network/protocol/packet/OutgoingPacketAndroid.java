
//--------------------Class--------------------
//-------------------Functions-----------------
//-------------------Extra---------------------
//---------------------------------------------
package net.mamoe.mirai.qqandroid.network.protocol.packet;

import kotlin.ExperimentalUnsignedTypes;
import kotlin.io.core.BytePacketBuilder;
import kotlin.io.core.ByteReadPacket;
import kotlin.io.core.buildPacket;
import kotlin.io.core.writeFully;
import net.mamoe.mirai.qqandroid.network.QQAndroidClient;
import net.mamoe.mirai.utils.MiraiInternalAPI;
import net.mamoe.mirai.utils.cryptor.DecrypterByteArray;
import net.mamoe.mirai.utils.cryptor.encryptAndWrite;
import net.mamoe.mirai.utils.io.encryptAndWrite;
import net.mamoe.mirai.utils.io.writeHex;
import net.mamoe.mirai.utils.io.writeIntLVPacket;
import net.mamoe.mirai.utils.io.writeQQ;

@ExperimentalUnsignedTypes
internal class OutgoingPacket {
    private final String name;
    private final String commandName;
    private final int sequenceId;
    private final ByteReadPacket delegate;

    public OutgoingPacket(String name, String commandName, int sequenceId, ByteReadPacket delegate) {
        this.name = name;
        this.commandName = commandName;
        this.sequenceId = sequenceId;
        this.delegate = delegate;
    }
}

internal class PacketFactory {
    private static final byte[] KEY_16_ZEROS = new byte[16];
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    @MiraiInternalAPI
    public OutgoingPacket buildOutgingPacket(QQAndroidClient client, String name, String commandName, byte[] key, BodyBuilder body) {
        int sequenceId = client.nextSsoSequenceId();

        return new OutgoingPacket(name, commandName, sequenceId, buildPacket(builder -> {
            builder.writeIntLVPacket(it -> it + 4, packetBuilder -> {
                packetBuilder.writeInt(0x0B);
                packetBuilder.writeByte((byte) 1);
                packetBuilder.writeInt(sequenceId);
                packetBuilder.writeByte((byte) 0);
                String uin = client.getUin().toString();
                packetBuilder.writeInt(uin.length() + 4);
                packetBuilder.writeStringUtf8(uin);
                encryptAndWrite(key, packetBuilder, () -> body.build(sequenceId));
            });
        }));
    }

    @MiraiInternalAPI
    public OutgoingPacket buildLoginOutgoingPacket(QQAndroidClient client, byte bodyType, byte[] extraData, String name, String commandName, byte[] key, BodyBuilder body) {
        int sequenceId = client.nextSsoSequenceId();

        return new OutgoingPacket(name, commandName, sequenceId, buildPacket(builder -> {
            builder.writeIntLVPacket(it -> it + 4, packetBuilder -> {
                packetBuilder.writeInt(0x00_00_00_0A);
                packetBuilder.writeByte(bodyType);
                packetBuilder.writeInt(extraData.length + 4);
                packetBuilder.writeFully(extraData);
                packetBuilder.writeByte((byte) 0);
                String uin = client.getUin().toString();
                packetBuilder.writeInt(uin.length() + 4);
                packetBuilder.writeStringUtf8(uin);
                encryptAndWrite(key, packetBuilder, () -> body.build(sequenceId));
            });
        }));
    }

    private static final ByteReadPacket BRP_STUB = new ByteReadPacket(EMPTY_BYTE_ARRAY);

    @MiraiInternalAPI
    public void writeSsoPacket(BytePacketBuilder builder, QQAndroidClient client, long subAppId, String commandName, ByteReadPacket extraData, int sequenceId, BodyBuilder body) {
        builder.writeIntLVPacket(it -> it + 4, packetBuilder -> {
            packetBuilder.writeInt(sequenceId);
            packetBuilder.writeInt((int) subAppId);
            packetBuilder.writeInt((int) subAppId);
            packetBuilder.writeHex("01 00 00 00 00 00 00 00 00 00 01 00");
            if (extraData == BRP_STUB) {
                packetBuilder.writeInt(0x04);
            } else {
                packetBuilder.writeInt(extraData.remaining() + 4);
                packetBuilder.writePacket(extraData);
            }
            packetBuilder.writeInt(commandName.length() + 4);
            packetBuilder.writeStringUtf8(commandName);
            packetBuilder.writeInt(4 + 4);
            packetBuilder.writeInt(45112203);

            String imei = client.getDevice().getImei();
            packetBuilder.writeInt(imei.length() + 4);
            packetBuilder.writeStringUtf8(imei);
            packetBuilder.writeInt(4);
            byte[] ksid = client.getKsid();
            packetBuilder.writeShort((short) (ksid.length + 2));
            packetBuilder.writeFully(ksid);
            packetBuilder.writeInt(4);
        });

        builder.writeIntLVPacket(it -> it + 4, body::build);
    }

    public ByteReadPacket buildSessionOutgoingPacket(String uinAccount, DecrypterByteArray sessionKey, BodyBuilder body) {
        return buildPacket(builder -> {
            builder.writeIntLVPacket(it -> it + 4, packetBuilder -> {
                packetBuilder.writeInt(0x00_00_00_0B);
                packetBuilder.writeByte((byte) 0x01);
                packetBuilder.writeByte((byte) 0);
                packetBuilder.writeInt(uinAccount.length() + 4);
                packetBuilder.writeStringUtf8(uinAccount);
                encryptAndWrite(sessionKey, packetBuilder, body::build);
            });
        });
    }

    @ExperimentalUnsignedTypes
    @MiraiInternalAPI
    public void writeOicqRequestPacket(BytePacketBuilder builder, QQAndroidClient client, EncryptMethod encryptMethod, int commandId, BodyBuilder bodyBlock) {
        ByteReadPacket body = encryptMethod.makeBody(client, bodyBlock);

        builder.writeByte((byte) 0x02);
        builder.writeShort((short) (27 + 2 + body.remaining()));
        builder.writeShort(client.getProtocolVersion());
        builder.writeShort((short) commandId);
        builder.writeShort((short) 1);
        builder.writeQQ(client.getUin());
        builder.writeByte((byte) 3);
        builder.writeByte(encryptMethod.getId());
        builder.writeByte((byte) 0);
        builder.writeInt(2);
        builder.writeInt(client.getAppClientVersion());
        builder.writeInt(0);

        builder.writePacket(body);

        builder.writeByte((byte) 0x03);
    }
}
