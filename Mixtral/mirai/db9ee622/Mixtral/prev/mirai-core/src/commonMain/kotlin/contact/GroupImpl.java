package net.mamoe.mirai.internal.contact;

import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;

import java.io.InputStream;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@SuppressWarnings({
  "unused",
  "RedundantSuppression"
})
public class GroupImpl implements Group {
  public static void checkIsInstance(Group group) {
    if (!(group instanceof GroupImpl)) {
      throw new IllegalStateException("group is not an instanceof GroupImpl!! DO NOT interlace two or more protocol implementations!!");
    }
  }

  private final QQAndroidBot bot;
  private final CoroutineContext coroutineContext;
  private final long id;
  private final GroupInfo groupInfo;
  private final Sequence < MemberInfo > members;
  private volatile String _name;
  private volatile String _announcement;
  private volatile boolean _allowMemberInvite;
  private volatile boolean _confessTalk;
  private volatile boolean _muteAll;
  private volatile boolean _autoApprove;
  private volatile boolean _anonymousChat;
  private NormalMember owner;
  private NormalMember botAsMember;

  public GroupImpl(QQAndroidBot bot, CoroutineContext coroutineContext, long id, GroupInfo groupInfo, Sequence < MemberInfo > members) {
    this.bot = bot;
    this.coroutineContext = coroutineContext != null ? coroutineContext : EmptyCoroutineContext.INSTANCE;
    this.id = id;
    this.groupInfo = groupInfo;
    this.members = members;
    this._name = groupInfo.getName();
    this._announcement = groupInfo.getMemo();
    this._allowMemberInvite = groupInfo.isAllowMemberInvite();
    this._confessTalk = groupInfo.isConfessTalk();
    this._muteAll = groupInfo.isMuteAll();
    this._autoApprove = groupInfo.isAutoApprove();
    this._anonymousChat = groupInfo.isAnonymousChatEnabled();
  }

  public Image uploadImage(ExternalImage image) {
    try {
      if (image.getInput() instanceof net.mamoe.mirai.utils.internal.DeferredReusableInput) {
        ((net.mamoe.mirai.utils.internal.DeferredReusableInput) image.getInput()).init(bot.getConfiguration().getFileCacheStrategy());
      }
      if (broadcastEvent(new BeforeImageUploadEvent(this, image)).isCancelled()) {
        throw new EventCancelledException("cancelled by BeforeImageUploadEvent.ToGroup");
      }
      bot.getNetwork().run(new Runnable() {
        @Override
        public void run() {
          Response response = new GroupPicUp(bot.getClient(),
              bot.getId(),
              getId(),
              image.md5,
              (int) image.getInput().size())
            .sendAndExpect();

          if (response instanceof Response.Failed) {
            Response.Failed failed = (Response.Failed) response;
            broadcastEvent(new ImageUploadEvent.Failed(GroupImpl.this, image, failed.resultCode, failed.message));
            if (failed.message.equals("over file size max")) throw new OverFileSizeMaxException();
            throw new IOException("upload group image failed with reason " + failed.message);
          } else if (response instanceof Response.FileExists) {
            Response.FileExists fileExists = (Response.FileExists) response;
            int resourceId = image.calculateImageResourceId();
            OfflineGroupImage offlineGroupImage = new OfflineGroupImage(imageId = resourceId);
            broadcastEvent(new ImageUploadEvent.Succeed(GroupImpl.this, image, offlineGroupImage));
            returnOfflineGroupImage(offlineGroupImage);
          } else if (response instanceof Response.RequireUpload) {
            Response.RequireUpload requireUpload = (Response.RequireUpload) response;
            HighwayHelper.uploadImageToServers(
              bot,
              requireUpload.uploadIpList.stream().map(ip -> new Pair < > (ip, requireUpload.uploadPortList.get(requireUpload.uploadIpList.indexOf(ip))))
              .toArray(Pair[]::new),
              requireUpload.uKey,
              image.getInput(),
              "group image",
              2
            );
            int resourceId = image.calculateImageResourceId();
            OfflineGroupImage offlineGroupImage = new OfflineGroupImage(imageId = resourceId);
            broadcastEvent(new ImageUploadEvent.Succeed(GroupImpl.this, image, offlineGroupImage));
            returnOfflineGroupImage(offlineGroupImage);
          }
        }
      });
    } catch (IOException e) {
      MiraiLogger.warning(GroupImpl.class, "Failed to upload image: " + e.getMessage());
    } finally {
      if (image.getInput() != null) {
        try {
          image.getInput().release();
        } catch (IOException e) {
          MiraiLogger.warning(GroupImpl.class, "Failed to release input stream: " + e.getMessage());
        }
      }
    }
    return null;
  }

  @Contract(pure = true)
  public boolean checkIsGroupImpl() {
    return GroupImpl.checkIsInstance(this);
  }

  public static Member newAnonymous(String name, String id) {
    return new Member(new MemberInfo() {
      {
        this.nameCard = name;
        this.permission = MemberPermission.MEMBER;
        this.specialTitle = "匿名";
        this.muteTimestamp = 0;
        this.uin = 80000000L;
        this.nick = name;
        this.remark = "匿名";
      }

    });
  }

  @Override
  public boolean quit() {
    if (botPermission != MemberPermission.OWNER) {
      if (!bot.groups.delegate.remove(this)) {
        return false;
      }
      return true;
    }
    return false;
  }

  @Override
  public Member newMember(MemberInfo memberInfo) {
    if (memberInfo.getAnonymousId() != null) {
      return new AnonymousMemberImpl(
        this, coroutineContext,
        memberInfo, memberInfo.getAnonymousId()
      );
    }
    return new MemberImpl(
      (FriendImpl) Mirai._lowLevelNewFriend(bot, memberInfo),
      this,
      coroutineContext,
      memberInfo
    );
  }

  @Override
  public Member get(long id) {
    if (id == bot.getId()) {
      return botAsMember;
    }
    return members.stream().filter(member -> member.getId() == id).findFirst().map(member -> (NormalMember) member).orElse(null);
  }

  @Override
  public boolean contains(long id) {
    return bot.getId() == id || get(id) != null;
  }

  @Override
  public @NotNull MessageReceipt < Group > sendMessage(@NotNull Message message) {
    require(message.content().length() > 0, "message is empty");
    if (isBotMuted) {
      throw new BotIsBeingMutedException(this);
    }

    return sendMessageImpl(message, false).also(() -> logMessageSent(message));
  }

  private @NotNull MessageReceipt < Group > sendMessageImpl(@NotNull Message message, boolean isForward) {
    if (message instanceof MessageChain messageChain) {
      if (messageChain.anyMatch(ForwardMessage.class::isInstance)) {
        ForwardMessage forwardMessage = (ForwardMessage) messageChain.stream()
          .filter(ForwardMessage.class::isInstance)
          .findFirst()
          .orElseThrow(() -> new RuntimeException("ForwardMessage must be standalone"));
        return sendMessageImpl(forwardMessage, true);
      }
    } else if (message instanceof ForwardMessage forwardMessage) {
      if (forwardMessage.nodeList().size() > 200) {
        throw new MessageTooLargeException(
          this, forwardMessage, forwardMessage,
          "ForwardMessage allows up to 200 nodes, but found " + forwardMessage.nodeList().size());
      }

      return Mirai.lowLevelSendGroupLongOrForwardMessage(bot, this.getId(), forwardMessage.nodeList(), false, forwardMessage);
    } else {
      MessageChain msg;
      if (!(message instanceof LongMessage) && !(message instanceof ForwardMessageInternal)) {
        MessageGroupPreSendEvent groupMessagePreSendEvent = new GroupMessagePreSendEvent(this, message);
        groupMessagePreSendEvent.broadcast();
        if (groupMessagePreSendEvent.isCancelled()) {
          throw new EventCancelledException("cancelled by GroupMessagePreSendEvent");
        }

        List < Message > messageChainList = groupMessagePreSendEvent.getMessage().asMessageChain();
        int length = estimateLength(messageChainList, this, 703);
        int imageCount = (int) messageChainList.stream().filter(Image.class::isInstance).count();

        if (length > 5000 || imageCount > 50) {
          throw new MessageTooLargeException(
            this, message, messageChainList,
            "message(" + messageChainList.stream().limit(10).map(Object::toString).collect(Collectors.joining("", "", "")) + ") is too large. Allow up to 50 images or 5000 chars");
        }

        if (length > 702 || imageCount > 2) {
          List < ForwardMessage.Node > nodeList = new ArrayList < > ();
          nodeList.add(new ForwardMessage.Node(
            bot.getId(),
            (int) TimeUnit.SECONDS.toSeconds(Duration.ofNow().toSeconds()),
            messageChainList,
            bot.getNick()));

          return Mirai.lowLevelSendGroupLongOrForwardMessage(
            bot,
            this.getId(),
            nodeList,
            true, null);
        }
        msg = messageChainList;
      } else {
        msg = message.asMessageChain();
      }

      QuoteReply quoteReply = (QuoteReply) msg.firstOrNull(QuoteReply.class);
      if (quoteReply != null) {
        quoteReply.source().ensureSequenceIdAvailable();
      }

      MessageSourceToGroupImpl source;
      MessageReceipt < Group > result;
      try {
        result = bot.getNetwork().runCatching(() -> {
            source = new MessageSourceToGroupImpl();
            MessageSvcPbSendMsg.createToGroup(
              bot.getClient(),
              this,
              msg,
              isForward) {
              source = it;
            }.sendAndExpect(MessageSvcPbSendMsg.Response.class).let(response -> {
                if (!(response instanceof MessageSvcPbSendMsg.Response.SUCCESS))) {
                throw new RuntimeException("Send group message failed: " + response);
              }
            }
            });

          source.ensureSequenceIdAvailable(); new MessageReceipt < > (source, this);
        }.getOrElse(exception -> {
        throw new RuntimeException(exception);
      });
    } catch (InterruptedException | ExecutionException | RuntimeException | IOException e) {
      throw new RuntimeException(e);
    }

    GroupMessagePostSendEvent groupMessagePostSendEvent = new GroupMessagePostSendEvent(this, msg, null, result);
    groupMessagePostSendEvent.broadcast();

    return result;
  }

  @Override
  public @NotNull Voice uploadVoice(@NotNull InputStream input) {
    byte[] content = new byte[input.available()];
    try {
      input.read(content);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    if (content.length > 1048576) {
      throw new OverFileSizeMaxException();
    }

    String md5 = getMD5(content);
    int codec = getCodec(content);

    try {
      PttStore.GroupPttUp.Response.RequireUpload response = PttStore.GroupPttUp(
        bot.getClient(),
        bot.getId(),
        id,
        md5,
        content.length,
        codec).sendAndExpect();

      List < String > uploadIpList = response.uploadIpList();
      List < Integer > uploadPortList = response.uploadPortList();
      List < String > uploadUrlList = new ArrayList < > ();

      for (int i = 0; i < uploadIpList.size(); i++) {
        uploadUrlList.add(uploadIpList.get(i) + ":" + uploadPortList.get(i));
      }

      byte[] uploadedContent = HighwayHelper.uploadPttToServers(
        bot,
        uploadUrlList,
        content,
        md5,
        response.uKey,
        response.fileKey,
        codec,
        "group voice"
      );

      return new Voice(md5 + ".amr", md5, content.length, codec, "");
    } catch (IOException | ExecutionException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String toString() {
    return "Group{" +
      "id=" + id +
      '}';
  }
}