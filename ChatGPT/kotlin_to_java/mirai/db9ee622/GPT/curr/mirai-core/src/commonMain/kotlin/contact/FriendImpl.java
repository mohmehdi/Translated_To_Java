package net.mamoe.mirai.internal.contact;

import kotlinx.atomicfu.AtomicInt;
import net.mamoe.mirai.LowLevelApi;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.data.FriendInfo;
import net.mamoe.mirai.event.broadcast;
import net.mamoe.mirai.event.events.BeforeImageUploadEvent;
import net.mamoe.mirai.event.events.EventCancelledException;
import net.mamoe.mirai.event.events.ImageUploadEvent;
import net.mamoe.mirai.internal.QQAndroidBot;
import net.mamoe.mirai.internal.network.highway.postImage;
import net.mamoe.mirai.internal.network.highway.sizeToString;
import net.mamoe.mirai.internal.network.protocol.data.proto.Cmd0x352;
import net.mamoe.mirai.internal.network.protocol.packet.chat.image.LongConn;
import net.mamoe.mirai.internal.utils.MiraiPlatformUtils;
import net.mamoe.mirai.internal.utils.toUHexString;
import net.mamoe.mirai.message.MessageReceipt;
import net.mamoe.mirai.message.data.Image;
import net.mamoe.mirai.message.data.Message;
import net.mamoe.mirai.message.data.isContentNotEmpty;
import net.mamoe.mirai.utils.ExternalImage;
import net.mamoe.mirai.utils.verbose;
import kotlin.contracts.ExperimentalContracts;
import kotlin.contracts.contract;
import kotlin.coroutines.CoroutineContext;
import kotlin.math.roundToInt;
import kotlin.time.measureTime;

import java.util.concurrent.atomic.AtomicInteger;

@LowLevelApi
@SuppressWarnings({"EXPERIMENTAL_API_USAGE", "DEPRECATION_ERROR", "NOTHING_TO_INLINE", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE"})
class FriendInfoImpl implements FriendInfo {
    private final net.mamoe.mirai.internal.network.protocol.data.jce.FriendInfo jceFriendInfo;
    private String nick;
    private String remark;

    public FriendInfoImpl(net.mamoe.mirai.internal.network.protocol.data.jce.FriendInfo jceFriendInfo) {
        this.jceFriendInfo = jceFriendInfo;
        this.nick = jceFriendInfo.nick;
        this.remark = jceFriendInfo.remark;
    }

    @ExperimentalContracts
    public FriendInfo checkIsInfoImpl() {
        contract {
            returns() implies (this instanceof FriendInfoImpl);
        }
        if (!(this instanceof FriendInfoImpl)) {
            throw new IllegalStateException("A Friend instance is not instance of FriendImpl. Don't interlace two protocol implementations together!");
        }
        return this;
    }
    public FriendImpl checkIsFriendImpl() {
        contract {
            returns() implies (Intrinsics.areEqual(this@Friend, FriendImpl.class));
        }
        if (!(this instanceof FriendImpl)) {
            throw new IllegalStateException("A Friend instance is not an instance of FriendImpl. Don't interlace two protocol implementations together!");
        }
        return (FriendImpl) this;
    }
}

@LowLevelApi
class FriendImpl extends AbstractUser implements Friend {
    private final AtomicInt lastMessageSequence = new AtomicInt(-1);
    private final FriendInfo friendInfo;

    public FriendImpl(QQAndroidBot bot, CoroutineContext coroutineContext, FriendInfo friendInfo) {
        super(bot, coroutineContext, friendInfo);
        this.friendInfo = friendInfo;
    }

    @Override
    public MessageReceipt<Friend> sendMessage(Message message) {
        if (!message.isContentNotEmpty()) {
            throw new IllegalArgumentException("message is empty");
        }
        MessageReceipt<Friend> receipt = sendMessageImpl(message, it -> new MessageReceipt<>(it, this), it -> new MessageReceipt<>(it, this));
        logMessageSent(message);
        return receipt;
    }

    @Override
    public String toString() {
        return "Friend(" + id + ")";
    }

    @Override
    public Image uploadImage(ExternalImage image) {
        try {
            if (image.input instanceof net.mamoe.mirai.utils.internal.DeferredReusableInput) {
                image.input.init(bot.configuration.fileCacheStrategy);
            }
            if (BeforeImageUploadEvent.broadcast(new BeforeImageUploadEvent(this, image)).isCancelled()) {
                throw new EventCancelledException("cancelled by BeforeImageUploadEvent.ToGroup");
            }
            LongConn.OffPicUp.Response response = bot.network.run(() -> {
                return LongConn.OffPicUp.sendAndExpect(bot.client, new Cmd0x352.TryUpImgReq(bot.id.toInt(), id.toInt(), 0, image.md5, image.input.size.toInt(), image.md5.toUHexString("") + "." + image.formatName, 1));
            });

            if (response instanceof LongConn.OffPicUp.Response.FileExists) {
                net.mamoe.mirai.internal.message.OfflineFriendImage offlineFriendImage = new net.mamoe.mirai.internal.message.OfflineFriendImage(((LongConn.OffPicUp.Response.FileExists) response).resourceId);
                ImageUploadEvent.Succeed(new FriendImpl(this, image, offlineFriendImage)).broadcast();
                return offlineFriendImage;
            } else if (response instanceof LongConn.OffPicUp.Response.RequireUpload) {
                bot.network.logger.verbose("[Http] Uploading friend image, size=" + image.input.size.sizeToString());
                long time = measureTime(() -> {
                    return MiraiPlatformUtils.Http.postImage("0x6ff0070", bot.id, null, image.input, response.uKey.toUHexString(""));
                });
                bot.network.logger.verbose("[Http] Uploading friend image: succeed at " + ((double) image.input.size.toDouble() / 1024 / time.inSeconds).roundToInt() + " KiB/s");
                net.mamoe.mirai.internal.message.OfflineFriendImage offlineFriendImage = new net.mamoe.mirai.internal.message.OfflineFriendImage(((LongConn.OffPicUp.Response.RequireUpload) response).resourceId);
                ImageUploadEvent.Succeed(new FriendImpl(this, image, offlineFriendImage)).broadcast();
                return offlineFriendImage;
            } else if (response instanceof LongConn.OffPicUp.Response.Failed) {
                ImageUploadEvent.Failed(new FriendImpl(this, image, -1, ((LongConn.OffPicUp.Response.Failed) response).message)).broadcast();
                throw new RuntimeException(((LongConn.OffPicUp.Response.Failed) response).message);
            }
        } finally {
            image.input.release();
        }
        return null;
    }
}