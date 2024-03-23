package net.mamoe.mirai.qqandroid.network.protocol.packet;

import net.mamoe.mirai.data.Packet;
import net.mamoe.mirai.qqandroid.QQAndroidBot;
import net.mamoe.mirai.qqandroid.io.serialization.RequestPacket;
import net.mamoe.mirai.qqandroid.network.protocol.packet.chat.receive.MessageSvc;
import net.mamoe.mirai.qqandroid.network.protocol.packet.chat.receive.OnlinePush;
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.LoginPacket;
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.StatSvc;
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.data.RequestPacket;
import net.mamoe.mirai.utils.DefaultLogger;
import net.mamoe.mirai.utils.MiraiLogger;
import net.mamoe.mirai.utils.cryptor.CryptorKt;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static kotlin.io.ByteStreamsKt.readBytes;
import static kotlin.io.ByteStreamsKt.readFully;

@UseExperimental(ExperimentalUnsignedTypes.class)
abstract class PacketFactory < TPacket extends Packet > {
  protected final String commandName;

  PacketFactory(String commandName) {
    this.commandName = commandName;
  }

  abstract TPacket decode(QQAndroidBot bot, ByteReadPacket packet);
}

class KnownPacketFactories {
  private static final byte[] DECRYPTER_16_ZERO = new byte[16];

  private static final MiraiLogger PacketLogger = new DefaultLogger("Packet");

  private static final List < PacketFactory < ? >> knownPacketFactories = new ArrayList < > ();

  static {
    knownPacketFactories.add(LoginPacket.INSTANCE);
    knownPacketFactories.add(StatSvc.Register.INSTANCE);
    knownPacketFactories.add(OnlinePush.PbPushGroupMsg.INSTANCE);
    knownPacketFactories.add(MessageSvc.PushNotify.INSTANCE);
  }

  static PacketFactory < ? > findPacketFactory(String commandName) {
    for (PacketFactory < ? > factory : knownPacketFactories) {
      if (factory.commandName.equals(commandName)) {
        return factory;
      }
    }
    return null;
  }

  static void parseIncomingPacket(QQAndroidBot bot, Input rawInput, PacketConsumer consumer) {
    byte[] bytes = rawInput.readBytes();
    PacketLogger.verbose("开始处理包: " + CryptorKt.toUHexString(bytes));
    ByteReadPacket byteReadPacket = new ByteReadPacket(bytes);
    int remaining = byteReadPacket.remaining();
    assert remaining < Integer.MAX_VALUE;
    int flag1 = byteReadPacket.readInt();

    PacketLogger.verbose("flag1(0A/0B) = " + CryptorKt.toUHexString((byte) flag1));
    int flag2 = byteReadPacket.readByte();
    PacketLogger.verbose("包类型(flag2) = " + flag2 + ". (可能是 " + (flag2 == 2 ? "OicqRequest" : "Uni") + ")");

    int flag3 = byteReadPacket.readByte();
    assert flag3 == 0: "Illegal flag3. Expected 0, got " + flag3;

    readFully(byteReadPacket, byteReadPacket.readInt() - 4);

    ByteArrayPool.useInstance(data -> {
      int size = byteReadPacket.readAvailable(data);
      try {
        if (flag2 == 2) {
          PacketLogger.verbose("SSO, 尝试使用 16 zero 解密.");
          CryptorKt.decryptBy(DECRYPTER_16_ZERO, data, size);
          PacketLogger.verbose("成功使用 16 zero 解密");
        } else {
          PacketLogger.verbose("Uni, 尝试使用 d2Key 解密.");
          CryptorKt.decryptBy(bot.client.wLoginSigInfo.d2Key, data, size);
          PacketLogger.verbose("成功使用 d2Key 解密");
        }
      } catch (Exception e) {
        PacketLogger.verbose("失败, 尝试其他各种key");
        bot.client.tryDecryptOrNull(data, size, it -> it);
      }
      ByteReadPacket decryptedData = new ByteReadPacket(data, size);
      int flag1Final = flag1;
      parseSsoFrame(bot, decryptedData, flag1Final, consumer);
    });
  }

  private static < R > R inline(Supplier < R > block) {
    return block.get();
  }
  private static IncomingPacket parseUniFrame(QQAndroidBot bot, ByteReadPacket rawInput) {
    int sequenceId;
    ByteReadPacket packetData;

    // Processing sequenceId
    sequenceId = rawInput.readIoBuffer(rawInput.readInt() - 4).withUse(buffer -> buffer.readInt());

    // Reading packet body and printing debug message
    packetData = rawInput.readIoBuffer(rawInput.readInt() - 4).withUse(buffer -> {
      System.out.println("收到 UniPacket 的 body=" + CryptorKt.toUHexString(buffer.readBytes()));
      return null;
    });

    // return new IncomingPacket(null, 0, null);// Placeholder return, actual implementation is missing
    return null; 
  }

    private static class IncomingPacket {
    final PacketFactory < ? > packetFactory;
    final int sequenceId;
    final ByteReadPacket data;

    IncomingPacket(PacketFactory < ? > packetFactory, int sequenceId, ByteReadPacket data) {
      this.packetFactory = packetFactory;
      this.sequenceId = sequenceId;
      this.data = data;
    }
  }

  private static void parseSsoFrame(QQAndroidBot bot, ByteReadPacket input, int flag1, PacketConsumer consumer) {
    String commandName;
    int ssoSequenceId;
    ByteReadPacket packetData;

    ssoSequenceId = input.readInt();
    PacketLogger.verbose("sequenceId = " + ssoSequenceId);
    assert input.readInt() == 0;
    byte[] extraData = readBytes(input, input.readInt() - 4);
    PacketLogger.verbose("sso(inner)extraData = " + CryptorKt.toUHexString(extraData));

    commandName = input.readString(input.readInt() - 4);
    byte[] unknown = readBytes(input, input.readInt() - 4);
    if (unknown[0] != 0x02 || unknown[1] != (byte) 0xB0 || unknown[2] != (byte) 0x5B || unknown[3] != (byte) 0x8B) {
      DebugLogger.debug("got new unknown: " + CryptorKt.toUHexString(unknown));
    }

    assert input.readInt() == 0;

    PacketFactory < ? > packetFactory = findPacketFactory(commandName);
    bot.logger.verbose(commandName);
    if (packetFactory == null) {
      bot.logger.warning("找不到包 PacketFactory");
      PacketLogger.verbose("传递给 PacketFactory 的数据 = " + CryptorKt.toUHexString(input.readBytes()));
    }
    consumer.accept(new IncomingPacket(packetFactory, ssoSequenceId, input));
  }

  private static void parseOicqResponse(QQAndroidBot bot, ByteReadPacket packetData, PacketFactory < ? > packetFactory, int ssoSequenceId, PacketConsumer consumer) {
    long qq;
    assert packetData.readByte() == 2;
    readFully(packetData, 2);
    packetData.readUShort();
    packetData.readShort();
    qq = packetData.readUInt().toLong();
    int encryptionMethod = packetData.readUShort();
    packetData.discardExact(1);

    Packet packet;
    switch (encryptionMethod) {
    case 4:
      byte[] data = CryptorKt.decryptBy(bot.client.ecdh.keyPair.initialShareKey, packetData.readRemaining() - 1, packetData.readBytes(packetData.readRemaining() - 1));
      byte[] peerShareKey = bot.client.ecdh.calculateShareKeyByPeerPublicKey(packetData.readUShortLVByteArray().adjustToPublicKey());
      data = CryptorKt.decryptBy(peerShareKey, data);
      packet = packetFactory.decode(bot, new ByteReadPacket(data));
      break;
    case 0:
      byte[] decryptedData;
      if (bot.client.loginState == 0) {
        decryptedData = packetData.decryptBy(bot.client.ecdh.keyPair.initialShareKey, packetData.readRemaining() - 1, packetData.readBytes(packetData.readRemaining() - 1));
      } else {
        decryptedData = packetData.decryptBy(bot.client.randomKey, 0, packetData.readRemaining() - 1);
      }

      packet = packetFactory.decode(bot, new ByteReadPacket(decryptedData));
      break;
    default:
      throw new IllegalStateException("Illegal encryption method. expected 0 or 4, got " + encryptionMethod);
    }

    consumer.accept(packet, packetFactory.commandName, ssoSequenceId);
  }

  private static void parseUniResponse(QQAndroidBot bot, ByteReadPacket packetData, PacketFactory < ? > packetFactory, int ssoSequenceId, PacketConsumer consumer) {
    RequestPacket uni = packetData.readBytes(packetData.readInt() - 4).loadAs(RequestPacket.Companion.getSerializer());
    PacketLogger.verbose(uni.toString());
  }



  @NotNull
  static < R > R withUse(@NotNull IoBuffer buffer, @NotNull IoBuffer.() -> R block) {
    try {
      return block.invoke(buffer);
    } finally {
      buffer.release();
    }
  }

  interface PacketConsumer {
    void accept(Packet packet, String commandName, int ssoSequenceId);
  }
}