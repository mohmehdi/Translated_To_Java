

package net.mamoe.mirai.internal.contact;

import kotlin.coroutines.CoroutineContext;
import kotlin.contracts.contract;

import java.io.InputStream;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@SuppressWarnings("unused")
@FileAnn(suppress = {
  "INAPPLICABLE_JVM_NAME",
  "DEPRECATION_ERROR",
  "INVISIBLE_MEMBER",
  "INVISIBLE_REFERENCE"
})
@OptIn(LowLevelApi.class)
public class GroupImpl implements Group {
  public static void checkIsInstance(Group group) {
    contract();
    if (!(group instanceof GroupImpl)) {
      throw new IllegalStateException("group is not an instanceof GroupImpl!! DO NOT interlace two or more protocol implementations!!");
    }
  }

  private final QQAndroidBot bot;
  private final CoroutineContext coroutineContext;
  private final long id;
  private final GroupInfo groupInfo;
  private final Sequence < MemberInfo > members;

  private long uin;
  private NormalMember owner;
  private NormalMember botAsMember;
  private MemberPermission botPermission;
  private int botMuteRemaining;
  private String _name;
  private String _announcement;
  private boolean _allowMemberInvite;
  private boolean _confessTalk;
  private boolean _muteAll;
  private boolean _autoApprove;
  private boolean _anonymousChat;

  public GroupImpl(QQAndroidBot bot, CoroutineContext coroutineContext, long id, GroupInfo groupInfo, Sequence < MemberInfo > members) {
    this.bot = bot;
    this.coroutineContext = coroutineContext;
    this.id = id;
    this.groupInfo = groupInfo;
    this.members = members;

    uin = groupInfo.getUin();
    _name = groupInfo.getName();
    _announcement = groupInfo.getMemo();
    _allowMemberInvite = groupInfo.isAllowMemberInvite();
    _confessTalk = groupInfo.isConfessTalk();
    _muteAll = groupInfo.isMuteAll();
    _autoApprove = groupInfo.isAutoApprove();
    _anonymousChat = groupInfo.isAllowAnonymousChat();
  }

  @Nullable
  @Override
  public NormalMember get(Long id) {
    if (id.equals(bot.getId())) {
      return botAsMember;
    }
    return members.stream().filter(member -> member.getId().equals(id)).findFirst().orElse(null);
  }

  @Override
  public boolean contains(Long id) {
    return id.equals(bot.getId()) || members.stream().anyMatch(member -> member.getId().equals(id));
  }

  @NotNull
  @Override
  public MessageReceipt < Group > sendMessage(@NotNull Message message) throws ExecutionException, InterruptedException {
    require(message.isContentNotEmpty(), "message is empty");
    check(!isBotMuted, new BotIsBeingMutedException(this));

    MessageReceipt < Group > receipt = sendMessageImpl(message, false);
    logMessageSent(message);
    return receipt;
  }

  @NotNull
  private MessageReceipt < Group > sendMessageImpl(@NotNull Message message, boolean isForward) throws ExecutionException, InterruptedException {
    if (message instanceof MessageChain) {
      MessageChain messageChain = (MessageChain) message;
      if (messageChain.anyMatch(node -> node instanceof ForwardMessage)) {
        ForwardMessage forwardMessage = (ForwardMessage) messageChain.stream().filter(node -> node instanceof ForwardMessage).findFirst().orElse(null);
        if (forwardMessage == null) {
          throw new RuntimeException("ForwardMessage must be standalone");
        }
        return sendMessageImpl(forwardMessage, true);
      }
    } else if (message instanceof ForwardMessage) {
      ForwardMessage forwardMessage = (ForwardMessage) message;
      if (forwardMessage.getNodeList().size() >= 200) {
        throw new MessageTooLargeException(
          this, message, forwardMessage,
          "ForwardMessage allows up to 200 nodes, but found " + forwardMessage.getNodeList().size()
        );
      }

      return MiraiImpl.lowLevelSendGroupLongOrForwardMessage(bot, this.getId(), forwardMessage.getNodeList(), false, forwardMessage);
    }

    MessageChain msg;
    if (!(message instanceof LongMessage) && !(message instanceof ForwardMessageInternal)) {
      GroupMessagePreSendEvent preSendEvent = new GroupMessagePreSendEvent(this, message);
      preSendEvent.broadcast();
      if (preSendEvent.isCancelled()) {
        throw new EventCancelledException("cancelled by GroupMessagePreSendEvent");
      }

      MessageChain chain = preSendEvent.getMessage().asMessageChain();
      int length = estimateLength(chain, this, 703);
      int imageCnt = (int) chain.stream().filter(node -> node instanceof Image).count();

      if (length > 5000 || imageCnt > 50) {
        throw new MessageTooLargeException(
          this, message, chain,
          "message(" + chain.joinToString("", limit = 10) + ") is too large. Allow up to 50 images or 5000 chars"
        );
      }

      if (length > 702 || imageCnt > 2) {
        return MiraiImpl.lowLevelSendGroupLongOrForwardMessage(
          bot,
          this.getId(),
          Arrays.asList(
            new ForwardMessage.Node(
              bot.getId(),
              (int) currentTimeSeconds(),
              chain,
              bot.getNick()
            )
          ),
          true, null
        );
      }
      msg = chain;
    } else {
      msg = message.asMessageChain();
    }

    QuoteReply quoteReply = msg.firstIsInstanceOrNull(QuoteReply.class);
    if (quoteReply != null) {
      quoteReply.getSource().ensureSequenceIdAvailable();
    }

    MessageReceipt < Group > result = bot.getNetwork().runCatching(() -> {
      MessageSourceToGroupImpl source;
      MessageSvcPbSendMsg.createToGroup(
        bot.getClient(),
        this,
        msg,
        isForward
      ) {
        source = it;
      }.sendAndExpect(MessageSvcPbSendMsg.Response.class).let(response -> {
        if (!(response instanceof MessageSvcPbSendMsg.Response.SUCCESS)) {
          throw new RuntimeException("Send group message failed: " + response);
        }
      });

      try {
        source.ensureSequenceIdAvailable();
      } catch (Exception e) {
        bot.getNetwork().getLogger().warning("Timeout awaiting sequenceId for group message(" +
          message.contentToString().take(10) + "). Some features may not work properly");
        bot.getNetwork().getLogger().warning(e);
      }

      return new MessageReceipt < > (source, this);
    });

    result.fold(
      success -> {
        GroupMessagePostSendEvent postSendEvent = new GroupMessagePostSendEvent(this, msg, null, success);
        postSendEvent.broadcast();
        return success;
      },
      failure -> {
        GroupMessagePostSendEvent postSendEvent = new GroupMessagePostSendEvent(this, msg, failure, null);
        postSendEvent.broadcast();
        throw failure;
      }
    );

    return result.get();
  }

  @Override
  public Image uploadImage(ExternalImage image) throws EventCancelledException, OverFileSizeMaxException, NetworkException, LoginInfoFailureException {
    if (image.getInput() instanceof net.mamoe.mirai.utils.internal.DeferredReusableInput) {
      ((net.mamoe.mirai.utils.internal.DeferredReusableInput < ExternalResource.Input > ) image.getInput()).init(bot.getConfiguration().getFileCacheStrategy());
    }
    if (BeforeImageUploadEvent.broadcast(this, image).isCancelled()) {
      throw new EventCancelledException("cancelled by BeforeImageUploadEvent.ToGroup");
    }

    ImageUploadEvent.Succeed succeedEvent = null;
    ImageUploadEvent.Failed failedEvent = null;

    try {
      NetworkSession network = bot.getNetwork().getSession();
      NetworkSession.SessionSession session = (NetworkSession.SessionSession) network;

      byte[] md5 = MiraiPlatformUtils.md5(image.getInput().readBytes());
      long size = image.getInput().size();

      ImgStore.GroupPicUp.Response.Response response = ImgStore.GroupPicUp.sendAndExpect(
        session,
        uin -> bot.getId(),
        groupCode -> id,
        md5,
        size
      );

      switch (response) {
      case ImgStore.GroupPicUp.Response.Failed failed:
        ImageUploadEvent.Failed failedEvent = new ImageUploadEvent.Failed(this, image, failed.getResultCode(), failed.getMessage());
        BeforeImageUploadEvent.broadcast(this, image).ifCancelled(() -> {
          throw new EventCancelledException("cancelled by BeforeImageUploadEvent.ToGroup");
        });
        if (failed.getMessage().equals("over file size max")) {
          throw new OverFileSizeMaxException();
        }
        failed.printStackTrace();
        return null;

      case ImgStore.GroupPicUp.Response.FileExists fileExists:
        OfflineGroupImage offlineGroupImage = new OfflineGroupImage(
          image.calculateImageResourceId()
        ).also(() -> ImageUploadEvent.Succeed(this, image, offlineGroupImage).broadcast());
        return offlineGroupImage;

      case ImgStore.GroupPicUp.Response.RequireUpload requireUpload:
        List < UByte > uploadIpList = requireUpload.getUploadIpList();
        List < UByte > uploadPortList = requireUpload.getUploadPortList();
        String uKey = requireUpload.getUKey();
        HighwayHelper.uploadImageToServers(
          bot,
          (List < Pair < UByte, UByte >> ) requireUpload.getUploadIpList().zip(uploadPortList),
          uKey,
          image.getInput(),
          "group image",
          commandId -> 2
        );
        OfflineGroupImage offlineGroupImage = new OfflineGroupImage(
          image.calculateImageResourceId()
        ).also(() -> ImageUploadEvent.Succeed(this, image, offlineGroupImage).broadcast());
        return offlineGroupImage;
      }
    } finally {
      image.getInput().release();
    }
    return null;
  }

  @MiraiExperimentalApi
  public Voice uploadVoice(InputStream input) throws OverFileSizeMaxException, NetworkException, LoginInfoFailureException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int len;

    while ((len = input.read(buffer)) != -1) {
      output.write(buffer, 0, len);
    }

    byte[] content = output.toByteArray();
    byte[] md5 = MiraiPlatformUtils.md5(content);
    int codec = getCodec(content, 0, 10);

    if (content.length > 1048576) {
      throw new OverFileSizeMaxException();
    }

    return bot.getNetwork().run(session -> {
      List < UByte > uploadIpList = requireUpload.getUploadIpList();
      List < UByte > uploadPortList = requireUpload.getUploadPortList();
      String uKey = requireUpload.getUKey();

      byte[] fileKey = null;
      long size = content.length;
      String fileMd5 = "";

      for (int i = 0; i < uploadIpList.size(); i++) {
        UByte uploadIp = uploadIpList.get(i);
        UByte uploadPort = uploadPortList.get(i);

        PttStore.GroupPttUp.Response.RequireUpload requireUpload = PttStore.GroupPttUp.sendAndExpectRequireUpload(
          session,
          bot.getId(),
          id,
          md5,
          size,
          codec
        );

        if (requireUpload != null) {
          fileMd5 = md5;
          fileKey = requireUpload.getFileKey();
          size = requireUpload.getFileSize();
          break;
        }
      }

      if (fileKey == null) {
        throw new IllegalStateException("Failed to get file key");
      }

      byte[] uploadData = new byte[size];
      System.arraycopy(content, 0, uploadData, 0, (int) size);

      byte[] uploadResult = HighwayHelper.uploadPttToServers(
        bot,
        uploadIpList,
        uploadPortList,
        fileKey,
        fileMd5,
        content,
        codec
      );

      return new Voice(
        md5.toUHexString(""),
        md5,
        size,
        codec,
        ""
      );
    });
  }

  @Contract(pure = true)
  public boolean checkIsGroupImpl() {
    return GroupImpl.checkIsInstance(this);
  }

  public boolean quit() {
    check(botPermission != MemberPermission.OWNER) {
      "An owner cannot quit from a owning group"
    };

    if (!bot.groups.delegate.remove(this)) {
      return false;
    }

    bot.network.run(() -> {
      ProfileService.GroupMngReq.GroupMngReqResponse response;
      try {
        response = ProfileService.GroupMngReq.newInstance(bot.client, this.id).sendAndExpect();
      } catch (TelegramApiException e) {
        throw new RuntimeException(e);
      }
      check(response.errorCode == 0) {
        "Group.quit failed: " + response
      };
    });

    BotLeaveEvent.Active.broadcast(this);
    return true;
  }

  public Member newMember(MemberInfo memberInfo) {
    String anId = null;
    if (memberInfo.anonymousId != null) {
      anId = memberInfo.anonymousId;
    }

    if (anId != null) {
      return new AnonymousMemberImpl(
        this, this.coroutineContext, memberInfo, anId);
    }

    FriendImpl friendImpl = (FriendImpl) Mirai._lowLevelNewFriend(bot, memberInfo);
    return new MemberImpl(friendImpl, this, this.coroutineContext, memberInfo);
  }

  internal Member newAnonymous(String name, String id) {
    return newMember(
      new MemberInfo() {

      }
    );
  }

  @Override
  public String toString() {
    return "Group(" + id + ")";
  }
}