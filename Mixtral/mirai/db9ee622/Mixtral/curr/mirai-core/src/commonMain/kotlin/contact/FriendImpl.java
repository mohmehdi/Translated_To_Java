
package net.mamoe.mirai.internal.contact;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.jvm.JvmField;
import kotlin.jvm.internal.Intrinsics;
import kotlinx.atomicfu.AtomicInt;
import net.mamoe.mirai.LowLevelApi;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.data.FriendInfo;
import net.mamoe.mirai.event.EventCancelledException;
import net.mamoe.mirai.event.events.BeforeImageUploadEvent;
import net.mamoe.mirai.event.events.ImageUploadEvent;
import net.mamoe.mirai.internal.QQAndroidBot;
import net.mamoe.mirai.internal.network.highway.HighwayTransport;
import net.mamoe.mirai.internal.network.highway.PostImageResponse;
import net.mamoe.mirai.internal.network.highway.SizeToString;
import net.mamoe.mirai.internal.network.protocol.data.jce.FriendInfo as JceFriendInfo;
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

import java.io.InputStream;

public class FriendInfoImpl implements FriendInfo {
    @JvmField
    private final JceFriendInfo jceFriendInfo;

    public FriendInfoImpl(JceFriendInfo jceFriendInfo) {
        this.jceFriendInfo = jceFriendInfo;
    }

   
}

public class FriendImpl extends AbstractUser implements Friend {
    @JvmField
    private final AtomicInt lastMessageSequence;

    public FriendImpl(QQAndroidBot bot, CoroutineContext coroutineContext, FriendInfo friendInfo) {
        super(bot, coroutineContext, friendInfo);
        this.lastMessageSequence = new AtomicInt(-1);
    }
    @Contract(pure = true)
    public FriendInfoImpl checkIsInfoImpl() {
        if (this instanceof FriendInfoImpl) {
            return (FriendInfoImpl) this;
        } else {
            throw new IllegalStateException("A Friend instance is not instance of FriendImpl. Don't interlace two protocol implementations together!");
        }
    }
    @Contract(pure = true)
    public FriendImpl checkIsFriendImpl() {
        if (this instanceof FriendImpl) {
            return (FriendImpl) this;
        } else {
            throw new IllegalStateException("A Friend instance is not instance of FriendImpl. Don't interlace two protocol implementations together!");
        }
    }

    @Override
    public String toString() {
        return "Friend(" + getId() + ")";
    }

    @Override
    public MessageReceipt<Friend> sendMessage(Message message) {
        Intrinsics.checkNotNullExpressionValue(message, "message");
        Intrinsics.checkNotNull(message, "message");
        if (!message.isContentNotEmpty()) {
            throw new IllegalArgumentException("message is empty");
        }
        return sendMessageImpl(message, friendReceiptConstructor -> new MessageReceipt(friendReceiptConstructor, this), tReceiptConstructor -> new MessageReceipt(tReceiptConstructor, this)).also(receipt -> {
            logMessageSent(message);
        });
    }

    @Override
    public Image uploadImage(ExternalImage image) throws EventCancelledException {
        if (image.getInput() instanceof net.mamoe.mirai.utils.internal.DeferredReusableInput deferredReusableInput) {
            deferredReusableInput.init(getBot().getConfiguration().getFileCacheStrategy());
        }
        BeforeImageUploadEvent beforeImageUploadEvent = new BeforeImageUploadEvent(this, image);
        beforeImageUploadEvent.broadcast();
        if (beforeImageUploadEvent.isCancelled()) {
            throw new EventCancelledException("cancelled by BeforeImageUploadEvent.ToGroup");
        }
        PostImageResponse response = getBot().getNetwork().run(() -> {
            LongConn.OffPicUp offPicUp = new LongConn.OffPicUp(getBot().getClient(), new Cmd0x352.TryUpImgReq(
                    srcUin.intValue(),
                    getId().intValue(),
                    0,
                    image.getMd5(),
                    (int) image.getInput().getSize(),
                    image.getMd5().toUHexString("") + "." + image.getFormatName(),
                    1
            ));
            return offPicUp.sendAndExpect(LongConn.OffPicUp.Response.class);
        });

        switch (response.getResultCode()) {
            case 0:
                net.mamoe.mirai.internal.message.OfflineFriendImage offlineFriendImage = new net.mamoe.mirai.internal.message.OfflineFriendImage(response.getResourceId());
                ImageUploadEvent.Succeed succeed = new ImageUploadEvent.Succeed(this, image, offlineFriendImage);
                succeed.broadcast();
                return offlineFriendImage;
            case 1:
                HighwayTransport.Http http = getBot().getNetwork().getHttp();
                String uKeyHex = response.getUKey().toUHexString("");
                PostImageResponse.ImageUploadTask imageUploadTask = http.postImage("0x6ff0070", getBot().getId(), null, image.getInput(), uKeyHex);
                imageUploadTask.await();
                PostImageResponse.ImageUploadTask.Response response1 = imageUploadTask.getResponse();
                if (response1.getResultCode() == 0) {
                    net.mamoe.mirai.internal.message.OfflineFriendImage offlineFriendImage1 = new net.mamoe.mirai.internal.message.OfflineFriendImage(response1.getResourceId());
                    ImageUploadEvent.Succeed succeed1 = new ImageUploadEvent.Succeed(this, image, offlineFriendImage1);
                    succeed1.broadcast();
                    return offlineFriendImage1;
                } else {
                    ImageUploadEvent.Failed failed = new ImageUploadEvent.Failed(this, image, -1, response1.getErrorMessage());
                    failed.broadcast();
                    throw new RuntimeException(response1.getErrorMessage());
                }
            default:
                ImageUploadEvent.Failed failed1 = new ImageUploadEvent.Failed(this, image, -1, response.getErrorMessage());
                failed1.broadcast();
                throw new RuntimeException(response.getErrorMessage());
        }
    }
}