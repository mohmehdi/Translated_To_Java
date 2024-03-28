
package net.mamoe.mirai.internal.contact;

import kotlinx.coroutines.launch;
import net.mamoe.mirai.LowLevelApi;
import net.mamoe.mirai.Mirai;
import net.mamoe.mirai.contact.*;
import net.mamoe.mirai.data.GroupInfo;
import net.mamoe.mirai.data.MemberInfo;
import net.mamoe.mirai.event.broadcast;
import net.mamoe.mirai.event.events.*;
import net.mamoe.mirai.internal.MiraiImpl;
import net.mamoe.mirai.internal.QQAndroidBot;
import net.mamoe.mirai.internal.message.MessageSourceToGroupImpl;
import net.mamoe.mirai.internal.message.OfflineGroupImage;
import net.mamoe.mirai.internal.message.ensureSequenceIdAvailable;
import net.mamoe.mirai.internal.message.firstIsInstanceOrNull;
import net.mamoe.mirai.internal.network.highway.HighwayHelper;
import net.mamoe.mirai.internal.network.protocol.packet.chat.TroopManagement;
import net.mamoe.mirai.internal.network.protocol.packet.chat.image.ImgStore;
import net.mamoe.mirai.internal.network.protocol.packet.chat.receive.MessageSvcPbSendMsg;
import net.mamoe.mirai.internal.network.protocol.packet.chat.receive.createToGroup;
import net.mamoe.mirai.internal.network.protocol.packet.chat.voice.PttStore;
import net.mamoe.mirai.internal.network.protocol.packet.list.ProfileService;
import net.mamoe.mirai.internal.utils.GroupPkgMsgParsingCache;
import net.mamoe.mirai.internal.utils.MiraiPlatformUtils;
import net.mamoe.mirai.internal.utils.estimateLength;
import net.mamoe.mirai.internal.utils.toUHexString;
import net.mamoe.mirai.message.MessageReceipt;
import net.mamoe.mirai.message.data.*;
import net.mamoe.mirai.utils.*;

import java.io.InputStream;
import java.util.concurrent.ConcurrentLinkedQueue;
import kotlin.contracts.contract;
import kotlin.coroutines.CoroutineContext;
import kotlin.time.ExperimentalTime;

public class GroupImpl extends AbstractContact implements Group {
    private final long id;
    private final long uin;
    private String _name;
    private String _announcement;
    private boolean _allowMemberInvite;
    private boolean _confessTalk;
    private boolean _muteAll;
    private boolean _autoApprove;
    private boolean _anonymousChat;
    private NormalMember owner;
    private NormalMember botAsMember;
    private final ContactList<NormalMember> members;
    private final QQAndroidBot bot;

    public GroupImpl(QQAndroidBot bot, CoroutineContext coroutineContext, long id, GroupInfo groupInfo, Sequence<MemberInfo> members) {
        this.bot = bot;
        this.id = id;
        this.uin = groupInfo.getUin();
        this.members = new ContactList<>(members.mapNotNull(it -> {
            if (it.getUin() == bot.getId()) {
                botAsMember = newMember(it).cast();
                if (it.getPermission() == MemberPermission.OWNER) {
                    owner = botAsMember;
                }
                return null;
            } else {
                NormalMember member = newMember(it).cast();
                if (member.getPermission() == MemberPermission.OWNER) {
                    owner = member;
                }
                return member;
            }
        }).mapTo(new ConcurrentLinkedQueue<>(), it -> it));
        this._name = groupInfo.getName();
        this._announcement = groupInfo.getMemo();
        this._allowMemberInvite = groupInfo.isAllowMemberInvite();
        this._confessTalk = groupInfo.isConfessTalk();
        this._muteAll = groupInfo.isMuteAll();
        this._autoApprove = groupInfo.isAutoApprove();
        this._anonymousChat = groupInfo.isAllowAnonymousChat();
    }
    public boolean quit() throws Exception {
    if (botPermission == MemberPermission.OWNER) {
        throw new Exception("An owner cannot quit from an owning group");
    }

    if (!bot.groups.delegate.remove(this)) {
        return false;
    }

    ProfileService.GroupMngReq.GroupMngReqResponse response = new ProfileService.GroupMngReq(bot.client, this.id).sendAndExpect();
    if (response.errorCode != 0) {
        bot.groups.delegate.add(this);
        throw new Exception("Group.quit failed: " + response);
    }

    BotLeaveEvent.Active.broadcast(this);
    return true;
}

    public static void checkIsInstance(Group instance) {
        contract {
            returns() implies (instance instanceof GroupImpl);
        }
        check(instance instanceof GroupImpl, "group is not an instanceof GroupImpl!! DO NOT interlace two or more protocol implementations!!");
    }

    public void checkIsGroupImpl() {
        contract {
            returns() implies (this instanceof GroupImpl);
        }
        GroupImpl.checkIsInstance(this);
    }

    public NormalMember newMember(MemberInfo memberInfo) {
        if (memberInfo.getAnonymousId() != null) {
            return new AnonymousMemberImpl(this, this.coroutineContext, memberInfo, memberInfo.getAnonymousId());
        }
        return new MemberImpl((FriendImpl) Mirai._lowLevelNewFriend(bot, memberInfo), this, this.coroutineContext, memberInfo);
    }

    public Member newAnonymous(String name, String id) {
        return newMember(new MemberInfo() {
            
        });
    }

    public NormalMember get(long id) {
        if (id == bot.getId()) {
            return botAsMember;
        }
        return members.stream().filter(it -> it.getId() == id).findFirst().orElse(null);
    }

    public boolean contains(long id) {
        return bot.getId() == id || members.stream().anyMatch(it -> it.getId() == id);
    }

    public MessageReceipt<Group> sendMessage(Message message) {
        require(message.isContentNotEmpty(), "message is empty");
        check(!isBotMuted(), new BotIsBeingMutedException(this));
        return sendMessageImpl(message, false);
    }

    private MessageReceipt<Group> sendMessageImpl(Message message, boolean isForward) {
        if (message instanceof MessageChain) {
            if (((MessageChain) message).anyIsInstance(ForwardMessage.class)) {
                return sendMessageImpl(((MessageChain) message).singleOrNull(), true);
            }
        }
        if (message instanceof ForwardMessage) {
            if (((ForwardMessage) message).getNodeList().size() >= 200) {
                throw new MessageTooLargeException(this, message, message, "ForwardMessage allows up to 200 nodes, but found " + ((ForwardMessage) message).getNodeList().size());
            }
            return MiraiImpl.lowLevelSendGroupLongOrForwardMessage(bot, this.id, ((ForwardMessage) message).getNodeList(), false, (ForwardMessage) message);
        }
        MessageChain msg;
        if (!(message instanceof LongMessage) && !(message instanceof ForwardMessageInternal)) {
            MessageChain chain = kotlin.runCatching(() -> GroupMessagePreSendEvent(this, message).broadcast()).onSuccess(it -> {
                check(!it.isCancelled(), new EventCancelledException("cancelled by GroupMessagePreSendEvent"));
            }).getOrElse(() -> {
                throw new EventCancelledException("exception thrown when broadcasting GroupMessagePreSendEvent");
            }).getMessage().asMessageChain();
            int length = chain.estimateLength(this, 703);
            int imageCnt = chain.count(it -> it instanceof Image);
            if (length > 5000 || imageCnt > 50) {
                throw new MessageTooLargeException(this, message, chain, "message(" + chain.joinToString("", 10) + ") is too large. Allow up to 50 images or 5000 chars");
            }
            if (length > 702 || imageCnt > 2) {
                return MiraiImpl.lowLevelSendGroupLongOrForwardMessage(bot, this.id, List.of(new ForwardMessage.Node(bot.getId(), currentTimeSeconds().toInt(), chain, bot.getNick())), true, null);
            }
            msg = chain;
        } else {
            msg = message.asMessageChain();
        }
        msg.firstIsInstanceOrNull(QuoteReply.class).getSource().ensureSequenceIdAvailable();
        MessageSvcPbSendMsg.Response response = bot.network.run(() -> {
            MessageSourceToGroupImpl source;
            MessageSvcPbSendMsg.createToGroup(bot.getClient(), this, msg, isForward, it -> source = it).sendAndExpect(MessageSvcPbSendMsg.Response.class);
            source.ensureSequenceIdAvailable();
            return new MessageReceipt(source, this);
        });
        if (response instanceof MessageSvcPbSendMsg.Response.SUCCESS) {
            GroupMessagePostSendEvent event = new GroupMessagePostSendEvent(this, msg, null, response);
            event.broadcast();
        } else {
            GroupMessagePostSendEvent event = new GroupMessagePostSendEvent(this, msg, response, null);
            event.broadcast();
        }
        return response;
    }

    @Deprecated
    @OptIn(ExperimentalTime.class)
    public Image uploadImage(ExternalImage image) {
        try {
            if (image.getInput() instanceof net.mamoe.mirai.utils.internal.DeferredReusableInput) {
                image.getInput().init(bot.getConfiguration().getFileCacheStrategy());
            }
            if (BeforeImageUploadEvent(this, image).broadcast().isCancelled()) {
                throw new EventCancelledException("cancelled by BeforeImageUploadEvent.ToGroup");
            }
            ImgStore.GroupPicUp.Response response = bot.network.run(() -> {
                ImgStore.GroupPicUp.Response result = ImgStore.GroupPicUp(bot.getClient(), bot.getId(), id, image.getMd5(), (int) image.getInput().size()).sendAndExpect();
                if (result instanceof ImgStore.GroupPicUp.Response.Failed) {
                    ImageUploadEvent.Failed(this, image, ((ImgStore.GroupPicUp.Response.Failed) result).getResultCode(), ((ImgStore.GroupPicUp.Response.Failed) result).getMessage()).broadcast();
                    if (((ImgStore.GroupPicUp.Response.Failed) result).getMessage().equals("over file size max")) {
                        throw new OverFileSizeMaxException();
                    }
                    throw new RuntimeException("upload group image failed with reason " + ((ImgStore.GroupPicUp.Response.Failed) result).getMessage());
                } else if (result instanceof ImgStore.GroupPicUp.Response.FileExists) {
                    String resourceId = image.calculateImageResourceId();
                    return new OfflineGroupImage(resourceId);
                } else if (result instanceof ImgStore.GroupPicUp.Response.RequireUpload) {
                    HighwayHelper.uploadImageToServers(bot, List.of(result.getUploadIpList().zip(result.getUploadPortList())), result.getUKey(), image.getInput(), "group image", 2);
                    String resourceId = image.calculateImageResourceId();
                    return new OfflineGroupImage(resourceId);
                }
                return null;
            });
            return response;
        } finally {
            image.getInput().release();
        }
    }

    @MiraiExperimentalApi
    public Voice uploadVoice(InputStream input) {
        byte[] content = new byte[input.available()];
        input.read(content);
        if (content.length > 1048576) {
            throw new OverFileSizeMaxException();
        }
        String md5 = MiraiPlatformUtils.md5(content);
        int codec = content.length >= 10 && new String(content, 0, 10).startsWith("2321414D52") ? 0 : content.length >= 10 && new String(content, 0, 10).startsWith("02232153494C4B5F5633") ? 1 : 0;
        PttStore.GroupPttUp.Response.RequireUpload response = bot.network.run(() -> {
            PttStore.GroupPttUp.Response.RequireUpload result = PttStore.GroupPttUp(bot.getClient(), bot.getId(), id, md5, content.length, codec).sendAndExpect();
            HighwayHelper.uploadPttToServers(bot, List.of(result.getUploadIpList().zip(result.getUploadPortList())), content, md5, result.getUKey(), result.getFileKey(), codec);
            return result;
        });
        return new Voice(md5 + ".amr", md5, content.length, codec, "");
    }

    @Override
    public String toString() {
        return "Group(" + id + ")";
    }

    private final GroupPkgMsgParsingCache groupPkgMsgParsingCache;
}