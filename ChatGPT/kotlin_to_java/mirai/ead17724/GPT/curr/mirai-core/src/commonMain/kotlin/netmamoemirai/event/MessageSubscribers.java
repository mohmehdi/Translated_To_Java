package net.mamoe.mirai.event;

import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.QQ;
import net.mamoe.mirai.event.events.BotEvent;
import net.mamoe.mirai.event.events.FriendMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.message.*;
import net.mamoe.mirai.utils.ExternalImage;
import net.mamoe.mirai.utils.Image;
import net.mamoe.mirai.utils.ImageId;

import kotlin.jvm.JvmName;

public class MessageSubscribers {

    @MessageDsl
    public static void subscribeMessages(@NotNull MessageSubscribersBuilder<SenderAndMessage<?>> listeners) {
        listeners.setListener(listener -> {
            // Subscribe to BotEvent and delegate handling to the appropriate event
            subscribeAlways(BotEvent.class, event -> {
                if (event instanceof FriendMessageEvent) {
                    listener.invoke(new FriendSenderAndMessage(((FriendMessageEvent) event).getSender(), ((FriendMessageEvent) event).getMessage()));
                } else if (event instanceof GroupMessageEvent) {
                    listener.invoke(new GroupSenderAndMessage(((GroupMessageEvent) event).getGroup(), ((GroupMessageEvent) event).getSender(), ((GroupMessageEvent) event).getMessage()));
                }
            });
        });
    }

    @MessageDsl
    public static void subscribeGroupMessages(@NotNull MessageSubscribersBuilder<GroupSenderAndMessage> listeners) {
        listeners.setListener(listener -> {
            // Subscribe to GroupMessageEvent
            subscribeAlways(GroupMessageEvent.class, event -> {
                listener.invoke(new GroupSenderAndMessage(((GroupMessageEvent) event).getGroup(), ((GroupMessageEvent) event).getSender(), ((GroupMessageEvent) event).getMessage()));
            });
        });
    }

    @MessageDsl
    public static void subscribeFriendMessages(@NotNull MessageSubscribersBuilder<FriendSenderAndMessage> listeners) {
        listeners.setListener(listener -> {
            // Subscribe to FriendMessageEvent
            subscribeAlways(FriendMessageEvent.class, event -> {
                listener.invoke(new FriendSenderAndMessage(((FriendMessageEvent) event).getSender(), ((FriendMessageEvent) event).getMessage()));
            });
        });
    }

    @MessageDsl
    public static void subscribeMessages(@NotNull Bot bot, @NotNull MessageSubscribersBuilder<SenderAndMessage<?>> listeners) {
        listeners.setListener(listener -> {
            // Subscribe to BotEvent and delegate handling to the appropriate event
            bot.subscribeAlways(BotEvent.class, event -> {
                if (event instanceof FriendMessageEvent) {
                    listener.invoke(new FriendSenderAndMessage(((FriendMessageEvent) event).getSender(), ((FriendMessageEvent) event).getMessage()));
                } else if (event instanceof GroupMessageEvent) {
                    listener.invoke(new GroupSenderAndMessage(((GroupMessageEvent) event).getGroup(), ((GroupMessageEvent) event).getSender(), ((GroupMessageEvent) event).getMessage()));
                }
            });
        });
    }

    @MessageDsl
    public static void subscribeGroupMessages(@NotNull Bot bot, @NotNull MessageSubscribersBuilder<GroupSenderAndMessage> listeners) {
        listeners.setListener(listener -> {
            // Subscribe to GroupMessageEvent
            bot.subscribeAlways(GroupMessageEvent.class, event -> {
                listener.invoke(new GroupSenderAndMessage(((GroupMessageEvent) event).getGroup(), ((GroupMessageEvent) event).getSender(), ((GroupMessageEvent) event).getMessage()));
            });
        });
    }

    @MessageDsl
    public static void subscribeFriendMessages(@NotNull Bot bot, @NotNull MessageSubscribersBuilder<FriendSenderAndMessage> listeners) {
        listeners.setListener(listener -> {
            // Subscribe to FriendMessageEvent
            bot.subscribeAlways(FriendMessageEvent.class, event -> {
                listener.invoke(new FriendSenderAndMessage(((FriendMessageEvent) event).getSender(), ((FriendMessageEvent) event).getMessage()));
            });
        });
    }

}

public abstract class SenderAndMessage<S extends Contact> {

    private final QQ sender;
    private final S subject;
    private final MessageChain message;

    public SenderAndMessage(QQ sender, S subject, MessageChain message) {
        this.sender = sender;
        this.subject = subject;
        this.message = message;
    }

    public void reply(MessageChain message) {
        subject.sendMessage(message);
    }

    public void reply(Message message) {
        subject.sendMessage(message.toChain());
    }

    public void reply(String plain) {
        subject.sendMessage(plain.toMessage());
    }

    public void send(ExternalImage externalImage) {
        externalImage.sendTo(subject);
    }

    public Image upload(ExternalImage externalImage) {
        return externalImage.upload(subject);
    }

    public void send(Image image) {
        image.sendTo(subject);
    }

    public void send(ImageId imageId) {
        imageId.sendTo(subject);
    }

    public void send(Message message) {
        message.sendTo(subject);
    }
}

public class FriendSenderAndMessage extends SenderAndMessage<QQ> {

    public FriendSenderAndMessage(QQ sender, MessageChain message) {
        super(sender, sender, message);
    }
}

public class GroupSenderAndMessage extends SenderAndMessage<Group> {

    private final Group group;

    public GroupSenderAndMessage(Group group, QQ sender, MessageChain message) {
        super(sender, group, message);
        this.group = group;
    }
}

@FunctionalInterface
public interface MessageReplier<T> {
    CompletableFuture<Message> invoke(T t, String message) throws Exception;
}

@FunctionalInterface
public interface StringReplier<T> {
    CompletableFuture<String> invoke(T t, String message) throws Exception;
}

public class MessageSubscribersBuilder<T extends SenderAndMessage<?>> {

    private final MessageDslSubscriber<T> subscriber;

    public MessageSubscribersBuilder(MessageDslSubscriber<T> subscriber) {
        this.subscriber = subscriber;
    }

    public void case(String equals, boolean trim, boolean ignoreCase, MessageDslAction<T> onEvent) {
        content(s -> equals.equals(trim ? s.trim() : s, ignoreCase), onEvent);
    }

    public void contains(String sub, MessageDslAction<T> onEvent) {
        content(s -> s.contains(sub), onEvent);
    }

    public void startsWith(String prefix, boolean removePrefix, MessageDslAction<T> onEvent) {
        content(s -> s.startsWith(prefix), () -> {
            if (removePrefix) onEvent.invoke(this.message.stringValue.substringAfter(prefix));
            else onEvent.invoke(this);
        });
    }

    public void endsWith(String suffix, MessageDslAction<T> onEvent) {
        content(s -> s.endsWith(suffix), onEvent);
    }

    public CompletableFuture<Void> sentBy(long qqId, MessageHandler<T> onEvent) throws Exception {
        return content(sender -> sender.getId() == qqId, onEvent);
    }
    
    public void sentBy(long qqId, MessageDslAction<T> onEvent) {
        content(s -> sender.getId() == qqId, onEvent);
    }

    public void sentFrom(long id, MessageDslAction<T> onEvent) {
        content(s -> this instanceof GroupSenderAndMessage && group.getId() == id, onEvent);
    }

    public void has(MessageDslAction<T> onEvent) {
        subscriber.invoke(() -> message.any());
    }

    public void content(MessageDslFilter<T> filter, MessageDslAction<T> onEvent) {
        subscriber.invoke(() -> filter.filter(message.stringValue), onEvent);
    }

    public void caseReply(String replier) {
        case(replier, true, () -> this.reply(replier));
    }

    public void caseReply(StringReplier<T> replier) {
        case(replier, true, () -> this.reply(replier.invoke(this)));
    }

    public void containsReply(String replier) {
        content(s -> s.contains(replier), () -> this.reply(replier));
    }

    public void containsReply(StringReplier<T> replier) {
        content(s -> s.contains(replier), () -> replier.invoke(this));
    }

    public void startsWithReply(StringReplier<T> replier) {
        content(s -> s.startsWith(replier), () -> replier.invoke(this));
    }

    public void endswithReply(StringReplier<T> replier) {
        content(s -> s.endsWith(replier), () -> replier.invoke(this));
    }

    public void reply(String reply) {
        case(reply, () -> this.reply(reply));
    }

    public void reply(StringReplier<T> reply) {
        case(reply, () -> this.reply(reply.invoke(this)));
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface MessageDsl {
}