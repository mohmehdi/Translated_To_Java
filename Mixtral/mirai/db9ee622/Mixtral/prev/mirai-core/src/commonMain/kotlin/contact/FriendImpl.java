
//⚠!#!
//--------------------Class--------------------
//Classes missing in Java:

//-------------------Functions-----------------
//Functions missing in Java:
//- FriendInfo.checkIsInfoImpl()
//- Friend.checkIsFriendImpl()

//Functions extra in Java:
//+ FriendImpl(QQAndroidBot, CoroutineContext, long, FriendInfo)
//+ getCoroutineContext()
//+ getId()
//+ getFriendInfo()
//+ getNick()
//+ getRemark()

//-------------------Extra---------------------
//Bracket problem
//---------------------------------------------
package net.mamoe.mirai.internal.contact;

import kotlinx.atomicfu.AtomicInt;
import kotlinx.atomicfu.atomic;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.SupervisorJob;
import net.mamoe.mirai.LowLevelApi;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.data.FriendInfo;
import net.mamoe.mirai.event.EventCancelledException;
import net.mamoe.mirai.event.events.BeforeImageUploadEvent;
import net.mamoe.mirai.event.events.Event;
import net.mamoe.mirai.event.events.ImageUploadEvent;
import net.mamoe.mirai.internal.QQAndroidBot;
import net.mamoe.mirai.internal.network.highway.Http;
import net.mamoe.mirai.internal.network.highway.HttpResponse;
import net.mamoe.mirai.internal.network.highway.HttpResponseCode;
import net.mamoe.mirai.internal.network.highway.HttpUploadRequest;
import net.mamoe.mirai.internal.network.highway.HttpUploadResponse;
import net.mamoe.mirai.internal.network.highway.HttpUploader;
import net.mamoe.mirai.internal.network.highway.HttpUploaderFactory;
import net.mamoe.mirai.internal.network.highway.HttpUploaderImpl;
import net.mamoe.mirai.internal.network.highway.HttpUploaderManager;
import net.mamoe.mirai.internal.network.highway.HttpUploaderManagerImpl;
import net.mamoe.mirai.internal.network.highway.ImageUploadRequest;
import net.mamoe.mirai.internal.network.highway.ImageUploadResponse;
import net.mamoe.mirai.internal.network.highway.ImageUploader;
import net.mamoe.mirai.internal.network.highway.ImageUploaderFactory;
import net.mamoe.mirai.internal.network.highway.ImageUploaderManager;
import net.mamoe.mirai.internal.network.highway.ImageUploaderManagerImpl;
import net.mamoe.mirai.internal.network.highway.PostImageRequest;
import net.mamoe.mirai.internal.network.highway.PostImageResponse;
import net.mamoe.mirai.internal.network.highway.Proto;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.TryUpImgReq;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.TryUpImgResp;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadResp;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadResp.FileExists;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadResp.RequireUpload;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadResp.Failed;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadRespOrBuilder;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadResp.Builder;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadRespOrBuilder.FileExistsCase;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadRespOrBuilder.RequireUploadCase;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadRespOrBuilder.FailedCase;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadRespOrBuilder.FileExistsCase.ResourceId;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadRespOrBuilder.RequireUploadCase.UKey;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadRespOrBuilder.FailedCase.Message;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadRespOrBuilder.FileExistsCase.ResourceIdCase;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadRespOrBuilder.RequireUploadCase.UKeyCase;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadRespOrBuilder.FailedCase.MessageCase;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadRespOrBuilder.FileExistsCase.ResourceIdOrBuilder;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadRespOrBuilder.RequireUploadCase.UKeyOrBuilder;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadRespOrBuilder.FailedCase.MessageOrBuilder;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadRespOrBuilder.FileExistsCase.ResourceIdOrBuilder.ResourceIdFieldNumber;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadRespOrBuilder.RequireUploadCase.UKeyOrBuilder.UKeyFieldNumber;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadRespOrBuilder.FailedCase.MessageOrBuilder.MessageFieldNumber;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadRespOrBuilder.FileExistsCase.ResourceIdCase.ResourceId;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadRespOrBuilder.RequireUploadCase.UKeyCase.UKey;
import net.mamoe.mirai.internal.network.highway.ProtoCmd0x352.UploadRespOrBuilder.FailedCase.MessageCase.Message;
import net.mamoe.mirai.internal.network.protocol.data.proto.Cmd0x352;
import net.mamoe.mirai.internal.network.protocol.packet.chat.image.LongConn;
import net.mamoe.mirai.internal.utils.MiraiPlatformUtils;
import net.mamoe.mirai.internal.utils.toUHexString;
import net.mamoe.mirai.message.MessageReceipt;
import net.mamoe.mirai.message.data.Image;
import net.mamoe.mirai.message.data.Message;
import net.mamoe.mirai.message.data.MessageChain;
import net.mamoe.mirai.message.data.MessageSource;
import net.mamoe.mirai.message.data.MessageSources;
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.utils.ExternalImage;
import net.mamoe.mirai.utils.ImageUploaderManager;
import net.mamoe.mirai.utils.MemberPermission;
import net.mamoe.mirai.utils.MiraiLogger;
import net.mamoe.mirai.utils.MemberPermission.MemberPermissionValue;
import net.mamoe.mirai.utils.WeakReference;
public class FriendInfoImpl implements FriendInfo {

    private final FriendInfo jceFriendInfo;

    public FriendInfoImpl(FriendInfo jceFriendInfo) {
        this.jceFriendInfo = jceFriendInfo;
    }
}

public class FriendImpl implements Friend {
    private final QQAndroidBot bot;
    private final CoroutineContext coroutineContext;
    private final long id;
    private final FriendInfo friendInfo;
    private final AtomicInteger lastMessageSequence;

    public FriendImpl(QQAndroidBot bot, CoroutineContext coroutineContext, long id, FriendInfo friendInfo) {
        this.bot = bot;
        this.coroutineContext = coroutineContext;
        this.id = id;
        this.friendInfo = friendInfo;
        this.lastMessageSequence = new AtomicInteger(-1);
    }
    
    @Contract(" -> new")
    public FriendInfo checkIsInfoImpl() {
        if (this instanceof FriendInfoImpl) {
            return (FriendInfoImpl) this;
        } else {
            throw new IllegalStateException("A Friend instance is not instance of FriendImpl. Don't interlace two protocol implementations together!");
        }
    }

    @Contract(" -> new")
    public Friend checkIsFriendImpl() {
        if (this instanceof FriendImpl) {
            return (FriendImpl) this;
        } else {
            throw new IllegalStateException("A Friend instance is not instance of FriendImpl. Don't interlace two protocol implementations together!");
        }
    }

 



    @Override
    public MessageReceipt<Friend> sendMessage(Message message) {
        if (message.content().isEmpty()) {
            throw new IllegalArgumentException("message is empty");
        }
        return sendMessageImpl(
                message,
                friendReceiptConstructor -> new MessageReceipt<>(friendReceiptConstructor, this),
                tReceiptConstructor -> new MessageReceipt<>(tReceiptConstructor, this)
        ).also(receipt -> {
            logMessageSent(message);
        });
    }

    @Override
    public String toString() {
        return "Friend(" + id + ")";
    }

    @Override
    public Image uploadImage(ExternalImage image) {
        if (BeforeImageUploadEvent.INSTANCE.broadcast(new BeforeImageUploadEvent(this, image)).isCancelled()) {
            throw new EventCancelledException("cancelled by BeforeImageUploadEvent.ToGroup");
        }
    }
}