
package net.mamoe.mirai.internal.contact;

import kotlinx.coroutines.Job;
import kotlinx.coroutines.SupervisorJob;
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

internal class GroupImpl implements Group {
    private final CoroutineContext coroutineContext;
    private final long id;
    private final GroupInfo groupInfo;
    private final Sequence<MemberInfo> members;
    private final QQAndroidBot bot;
    private NormalMember owner;
    private NormalMember botAsMember;
    private final GroupSettings settings;
    private final String _name;
    private String _announcement;
    private boolean _allowMemberInvite;
    private boolean _confessTalk;
    private boolean _muteAll;
    private boolean _autoApprove;
    private boolean _anonymousChat;
    private final ConcurrentLinkedQueue<NormalMember> membersList;

    public GroupImpl(QQAndroidBot bot, CoroutineContext coroutineContext, long id, GroupInfo groupInfo, Sequence<MemberInfo> members) {
        this.bot = bot;
        this.coroutineContext = coroutineContext + new SupervisorJob(coroutineContext[Job]);
        this.id = id;
        this.groupInfo = groupInfo;
        this.members = members;
        this.membersList = new ConcurrentLinkedQueue<>();
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

    


    @Override
    public boolean quit() {
        check(botPermission != MemberPermission.OWNER, "An owner cannot quit from a owning group");

        if (!bot.groups.delegate.remove(this)) {
            return false;
        }
        bot.network.run(() -> {
            ProfileService.GroupMngReq.GroupMngReqResponse response = new ProfileService.GroupMngReq(bot.getClient(), this.id).sendAndExpect();
            check(response.errorCode == 0, "Group.quit failed: " + response);
        });
        new BotLeaveEvent.Active(this).broadcast();
        return true;
    }

    @Override
    public Member newMember(MemberInfo memberInfo) {
        if (memberInfo.getAnonymousId() != null) {
            return new AnonymousMemberImpl(this, this.coroutineContext, memberInfo, memberInfo.getAnonymousId());
        }
        return new MemberImpl((FriendImpl) Mirai._lowLevelNewFriend(bot, memberInfo), this, this.coroutineContext, memberInfo);
    }

    public Member newAnonymous(String name, String id) {
        return newMember(new MemberInfo() {
            
        });
    }

    @Override
    public NormalMember get(long id) {
        if (id == bot.id) {
            return botAsMember;
        }
        return membersList.stream().filter(member -> member.getId() == id).findFirst().orElse(null);
    }

    @Override
    public boolean contains(long id) {
        return bot.id == id || membersList.stream().anyMatch(member -> member.getId() == id);
    }

    @Override
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
            check(((ForwardMessage) message).getNodeList().size() < 200, "ForwardMessage allows up to 200 nodes, but found " + ((ForwardMessage) message).getNodeList().size());

            return MiraiImpl.lowLevelSendGroupLongOrForwardMessage(bot, this.id, ((ForwardMessage) message).getNodeList(), false, (ForwardMessage) message);
        }

        MessageChain msg;
        if (!(message instanceof LongMessage) && !(message instanceof ForwardMessageInternal)) {
            MessageChain chain = kotlin.runCatching(() -> new GroupMessagePreSendEvent(this, message).broadcast()).onSuccess(() -> {
                check(!it.isCancelled(), new EventCancelledException("cancelled by GroupMessagePreSendEvent"));
            }).getOrElse(() -> {
                throw new EventCancelledException("exception thrown when broadcasting GroupMessagePreSendEvent", it);
            }).getMessage().asMessageChain();

            int length = chain.estimateLength(this, 703);
            int imageCnt = 0;

            if (length > 5000 || chain.count(Image.class) > 50) {
                throw new MessageTooLargeException(this, message, chain, "message(" + chain.joinToString("", 10) + ") is too large. Allow up to 50 images or 5000 chars");
            }

            if (length > 702 || imageCnt > 2) {
                return MiraiImpl.lowLevelSendGroupLongOrForwardMessage(bot, this.id, List.of(new ForwardMessage.Node(bot.id, currentTimeSeconds().toInt(), chain, bot.nick)), true, null);
            }
            msg = chain;
        } else {
            msg = message.asMessageChain();
        }

        msg.firstIsInstanceOrNull(QuoteReply.class).getSource().ensureSequenceIdAvailable();

        MessageReceipt<Group> result = bot.network.runCatching(() -> {
            MessageSourceToGroupImpl source;
            MessageSvcPbSendMsg.createToGroup(bot.getClient(), this, msg, isForward, it -> source = it).sendAndExpect(MessageSvcPbSendMsg.Response.class).let(it -> {
                check(it instanceof MessageSvcPbSendMsg.Response.SUCCESS, "Send group message failed: " + it);
            });

            try {
                source.ensureSequenceIdAvailable();
            } catch (Exception e) {
                bot.network.logger.warning(() -> "Timeout awaiting sequenceId for group message(" + message.contentToString().take(10) + "). Some features may not work properly");
                bot.network.logger.warning(e);
            }

            return new MessageReceipt(source, this);
        });

        result.fold(
            onSuccess -> new GroupMessagePostSendEvent(this, msg, null, onSuccess).broadcast(),
            onFailure -> new GroupMessagePostSendEvent(this, msg, onFailure, null).broadcast()
        );

        return result.getOrThrow();
    }

    @Override
    public Image uploadImage(ExternalImage image) {
        try {
            if (image.getInput() instanceof net.mamoe.mirai.utils.internal.DeferredReusableInput) {
                image.getInput().init(bot.getConfiguration().getFileCacheStrategy());
            }
            if (new BeforeImageUploadEvent(this, image).broadcast().isCancelled()) {
                throw new EventCancelledException("cancelled by BeforeImageUploadEvent.ToGroup");
            }
            return bot.network.run(() -> {
                ImgStore.GroupPicUp.Response response = new ImgStore.GroupPicUp(bot.getClient(), bot.getId(), id, image.getMd5(), (int) image.getInput().size()).sendAndExpect();

                switch (response) {
                    case FAILED:
                        ImageUploadEvent.Failed(this, image, response.getResultCode(), response.getMessage()).broadcast();
                        if (response.getMessage().equals("over file size max")) throw new OverFileSizeMaxException();
                        throw new RuntimeException("upload group image failed with reason " + response.getMessage());
                    case FILE_EXISTS:
                        String resourceId = image.calculateImageResourceId();
                        return new OfflineGroupImage(resourceId);
                    case REQUIRE_UPLOAD:
                        HighwayHelper.uploadImageToServers(bot, response.getUploadIpList().zip(response.getUploadPortList()), response.getUKey(), image.getInput(), "group image", 2);
                        String resourceId = image.calculateImageResourceId();
                        return new OfflineGroupImage(resourceId);
                }
            });
        } finally {
            image.getInput().release();
        }
    }

    @Override
    public Voice uploadVoice(InputStream input) {
        byte[] content = new byte[input.available()];
        input.read(content);
        if (content.length > 1048576) {
            throw new OverFileSizeMaxException();
        }
        String md5 = MiraiPlatformUtils.md5(content);
        int codec = content.length >= 10 && content.toUHexString("").startsWith("2321414D52") ? 0 : content.length >= 10 && content.toUHexString("").startsWith("02232153494C4B5F5633") ? 1 : 0;
        return bot.network.run(() -> {
            PttStore.GroupPttUp.Response.RequireUpload response = new PttStore.GroupPttUp(bot.getClient(), bot.getId(), id, md5, content.length, codec).sendAndExpect();
            HighwayHelper.uploadPttToServers(bot, response.getUploadIpList().zip(response.getUploadPortList()), content, md5, response.getUKey(), response.getFileKey(), codec);
            return new Voice(md5.toUHexString("") + ".amr", md5, content.length, codec, "");
        });
    }

    @Override
    public String toString() {
        return "Group(" + id + ")";
    }

    private final GroupPkgMsgParsingCache groupPkgMsgParsingCache;
}