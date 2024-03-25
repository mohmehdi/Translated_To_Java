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

public class MessageSubscribers {

    @MessageListenerDsl
    public static void subscribeMessages(MessageSubscribersBuilder<SenderAndMessage<?>> listeners) {
        MessageSubscribersBuilder<SenderAndMessage<?>> builder = new MessageSubscribersBuilder<>(listener -> {
            EventHandlers.subscribeAlways(BotEvent.class, it -> {
                if (it instanceof FriendMessageEvent) {
                    listener.invoke(new FriendSenderAndMessage(((FriendMessageEvent) it).getSender(), ((FriendMessageEvent) it).getMessage()));
                } else if (it instanceof GroupMessageEvent) {
                    listener.invoke(new GroupSenderAndMessage(((GroupMessageEvent) it).getGroup(), ((GroupMessageEvent) it).getSender(), ((GroupMessageEvent) it).getMessage()));
                }
            });
        });
        builder.apply(listeners);
    }

    @MessageListenerDsl
    public static void subscribeGroupMessages(MessageSubscribersBuilder<GroupSenderAndMessage> listeners) {
        MessageSubscribersBuilder<GroupSenderAndMessage> builder = new MessageSubscribersBuilder<>(listener -> {
            EventHandlers.subscribeAlways(GroupMessageEvent.class, it -> {
                listener.invoke(new GroupSenderAndMessage(((GroupMessageEvent) it).getGroup(), ((GroupMessageEvent) it).getSender(), ((GroupMessageEvent) it).getMessage()));
            });
        });
        builder.apply(listeners);
    }

    @MessageListenerDsl
    public static void subscribeFriendMessages(MessageSubscribersBuilder<FriendSenderAndMessage> listeners) {
        MessageSubscribersBuilder<FriendSenderAndMessage> builder = new MessageSubscribersBuilder<>(listener -> {
            EventHandlers.subscribeAlways(FriendMessageEvent.class, it -> {
                listener.invoke(new FriendSenderAndMessage(((FriendMessageEvent) it).getSender(), ((FriendMessageEvent) it).getMessage()));
            });
        });
        builder.apply(listeners);
    }

    @MessageListenerDsl
    public static void subscribeMessages(Bot bot, MessageSubscribersBuilder<SenderAndMessage<?>> listeners) {
        MessageSubscribersBuilder<SenderAndMessage<?>> builder = new MessageSubscribersBuilder<>(listener -> {
            bot.subscribeAlways(BotEvent.class, it -> {
                if (it instanceof FriendMessageEvent) {
                    listener.invoke(new FriendSenderAndMessage(((FriendMessageEvent) it).getSender(), ((FriendMessageEvent) it).getMessage()));
                } else if (it instanceof GroupMessageEvent) {
                    listener.invoke(new GroupSenderAndMessage(((GroupMessageEvent) it).getGroup(), ((GroupMessageEvent) it).getSender(), ((GroupMessageEvent) it).getMessage()));
                }
            });
        });
        builder.apply(listeners);
    }

    @MessageListenerDsl
    public static void subscribeGroupMessages(Bot bot, MessageSubscribersBuilder<GroupSenderAndMessage> listeners) {
        MessageSubscribersBuilder<GroupSenderAndMessage> builder = new MessageSubscribersBuilder<>(listener -> {
            bot.subscribeAlways(GroupMessageEvent.class, it -> {
                listener.invoke(new GroupSenderAndMessage(((GroupMessageEvent) it).getGroup(), ((GroupMessageEvent) it).getSender(), ((GroupMessageEvent) it).getMessage()));
            });
        });
        builder.apply(listeners);
    }

    @MessageListenerDsl
    public static void subscribeFriendMessages(Bot bot, MessageSubscribersBuilder<FriendSenderAndMessage> listeners) {
        MessageSubscribersBuilder<FriendSenderAndMessage> builder = new MessageSubscribersBuilder<>(listener -> {
            bot.subscribeAlways(FriendMessageEvent.class, it -> {
                listener.invoke(new FriendSenderAndMessage(((FriendMessageEvent) it).getSender(), ((FriendMessageEvent) it).getMessage()));
            });
        });
        builder.apply(listeners);
    }

    public static void invoke(MessageListener<?> listener, SenderAndMessage<?> t) {
        listener.invoke(t, t.getMessage().stringValue());
    }

    public static String invoke(StringReplier<?> replier, SenderAndMessage<?> t) {
        return replier.invoke(t, t.getMessage().stringValue());
    }

    public static Message invoke(MessageReplier<?> replier, SenderAndMessage<?> t) {
        return replier.invoke(t, t.getMessage().stringValue());
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
        reply(message.toChain());
    }

    public void reply(String plain) {
        reply(new PlainText(plain));
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

class FriendSenderAndMessage extends SenderAndMessage<QQ> {

    public FriendSenderAndMessage(QQ sender, MessageChain message) {
        super(sender, sender, message);
    }
}

class GroupSenderAndMessage extends SenderAndMessage<Group> {

    private final Group group;

    public GroupSenderAndMessage(Group group, QQ sender, MessageChain message) {
        super(sender, group, message);
        this.group = group;
    }
}
public interface MessageListener<T> {
    CompletionStage<Void> invoke(T t, String message);
}

public interface MessageReplier<T> {
    CompletionStage<Message> invoke(T t, String message);
}

public interface StringReplier<T> {
    CompletionStage<String> invoke(T t, String message);
}

public class MessageSubscribersBuilder<T extends SenderAndMessage<?>> {

    private final MessageListener<T> handlerConsumer;

    public MessageSubscribersBuilder(MessageListener<T> handlerConsumer) {
        this.handlerConsumer = handlerConsumer;
    }

    public void case(String equals, boolean trim, MessageListener<T> listener) {
        content(s -> equals.equals(trim ? s.trim() : s), listener);
    }

    public void contains(String value, MessageListener<T> listener) {
        content(s -> s.contains(value), listener);
    }

    public void startsWith(String prefix, boolean removePrefix, MessageListener<T> listener) {
        content(s -> s.startsWith(prefix), () -> {
            if (removePrefix) {
                listener.invoke(this, this.message.stringValue.substringAfter(prefix));
            } else {
                listener.invoke(this);
            }
        });
    }

    public void endsWith(String start, MessageListener<T> listener) {
        content(s -> s.endsWith(start), listener);
    }

    public void sentBy(long qqId, MessageListener<T> listener) {
        content(s -> sender.getId() == qqId, listener);
    }

    public void sentFrom(long id, MessageListener<T> listener) {
        content(s -> this instanceof GroupSenderAndMessage && group.getId() == id, listener);
    }

    public <M extends Message> void has(MessageListener<T> listener) {
        handlerConsumer.invoke(message.any(M.class) ? this : null);
    }

    public void content(MessageListener<T> filter, MessageListener<T> listener) {
        handlerConsumer.invoke(filter.invoke(message.stringValue) ? this : null);
    }

    public void caseReply(String replier) {
        case(replier, true, s -> this.reply(replier));
    }

    public void caseReply(StringReplier<T> replier) {
        case(replier, true, s -> this.reply(replier.invoke(this)));
    }

    public void containsReply(String replier) {
        content(s -> s.contains(replier), s -> this.reply(replier));
    }

    public void containsReply(StringReplier<T> replier) {
        content(s -> s.contains(replier), replier::invoke);
    }

    public void startsWithReply(StringReplier<T> replier) {
        content(s -> s.startsWith(replier), replier::invoke);
    }

    public void endswithReply(StringReplier<T> replier) {
        content(s -> s.endsWith(replier), replier::invoke);
    }

    public void reply(String reply) {
        case(reply, s -> this.reply(reply));
    }

    public void reply(StringReplier<T> reply) {
        case(reply, s -> this.reply(reply.invoke(this)));
    }
}

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MessageListenerDsl {
}