
package net.mamoe.mirai.internal.contact;

import kotlinx.atomicfu.AtomicInt;
import kotlinx.atomicfu.AtomicKt;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.SupervisorJob;
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
import net.mamoe.mirai.utils.getValue;
import net.mamoe.mirai.utils.unsafeWeakRef;
import net.mamoe.mirai.utils.verbose;
import kotlin.contracts.ExperimentalContracts;
import kotlin.contracts.contract;
import kotlin.coroutines.CoroutineContext;
import kotlin.math.MathKt;
import kotlin.time.measureTime;

internal class FriendInfoImpl {
    private final net.mamoe.mirai.internal.network.protocol.data.jce.FriendInfo jceFriendInfo;

    public FriendInfoImpl(net.mamoe.mirai.internal.network.protocol.data.jce.FriendInfo jceFriendInfo) {
        this.jceFriendInfo = jceFriendInfo;
    }

    public String getNick() {
        return jceFriendInfo.getNick();
    }

    public long getUin() {
        return jceFriendInfo.getFriendUin();
    }

    public String getRemark() {
        return jceFriendInfo.getRemark();
    }
}





internal class FriendImpl implements Friend {
    private final QQAndroidBot bot;
    private final CoroutineContext coroutineContext;
    private final long id;
    private final FriendInfo friendInfo;
    private final CoroutineContext coroutineContext;

    public FriendImpl(QQAndroidBot bot, CoroutineContext coroutineContext, long id, FriendInfo friendInfo) {
        this.bot = bot;
        this.coroutineContext = coroutineContext;
        this.id = id;
        this.friendInfo = friendInfo;
        this.coroutineContext = coroutineContext.plus(new SupervisorJob(coroutineContext.get(Job)));
    }

    private final AtomicInt lastMessageSequence = AtomicKt.atomic(-1);


    public MessageReceipt<Friend> sendMessage(Message message) {
        if (!MessageKt.isContentNotEmpty(message)) {
            throw new IllegalArgumentException("message is empty");
        }
        MessageReceipt<Friend> receipt = sendMessageImpl(message, Friend::new, MessageReceipt::new);
        logMessageSent(message);
        return receipt;
    }

    public String toString() {
        return "Friend(" + id + ")";
    }

    public Image uploadImage(ExternalImage image) {
        try {
            if (image.getInput() instanceof net.mamoe.mirai.utils.internal.DeferredReusableInput) {
                image.getInput().init(bot.getConfiguration().getFileCacheStrategy());
            }
            if (BeforeImageUploadEventKt.BeforeImageUploadEvent(this, image).broadcast().isCancelled()) {
                throw new EventCancelledException("cancelled by BeforeImageUploadEvent.ToGroup");
            }
            LongConn.OffPicUp.Response response = bot.getNetwork().run(() -> {
                return LongConn.OffPicUp(bot.getClient(), new Cmd0x352.TryUpImgReq(bot.getId().toInt(), id.toInt(), 0, image.getMd5(), image.getInput().getSize().toInt(), image.getMd5() + "." + image.getFormatName(), 1)).sendAndExpect(LongConn.OffPicUp.Response.class);
            });

            if (response instanceof LongConn.OffPicUp.Response.FileExists) {
                net.mamoe.mirai.internal.message.OfflineFriendImage offlineFriendImage = new net.mamoe.mirai.internal.message.OfflineFriendImage(((LongConn.OffPicUp.Response.FileExists) response).getResourceId());
                ImageUploadEvent.Succeed(this, image, offlineFriendImage).broadcast();
                return offlineFriendImage;
            } else if (response instanceof LongConn.OffPicUp.Response.RequireUpload) {
                bot.getNetwork().logger.verbose("[Http] Uploading friend image, size=" + image.getInput().getSize().sizeToString());
                double time = measureTime(() -> {
                    return MiraiPlatformUtils.Http.postImage("0x6ff0070", bot.getId(), null, image.getInput(), response.getUKey().toUHexString(""));
                });
                bot.getNetwork().logger.verbose("[Http] Uploading friend image: succeed at " + (image.getInput().getSize().toDouble() / 1024 / time.inSeconds()).roundToInt() + " KiB/s");
                net.mamoe.mirai.internal.message.OfflineFriendImage offlineFriendImage = new net.mamoe.mirai.internal.message.OfflineFriendImage(response.getResourceId());
                ImageUploadEvent.Succeed(this, image, offlineFriendImage).broadcast();
                return offlineFriendImage;
            } else if (response instanceof LongConn.OffPicUp.Response.Failed) {
                ImageUploadEvent.Failed(this, image, -1, ((LongConn.OffPicUp.Response.Failed) response).getMessage()).broadcast();
                throw new RuntimeException(((LongConn.OffPicUp.Response.Failed) response).getMessage());
            }
        } finally {
            image.getInput().release();
        }
    }
}