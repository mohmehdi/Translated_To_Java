
package net.mamoe.mirai.event;

import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.QQ;
import net.mamoe.mirai.event.events.BotEvent;
import net.mamoe.mirai.event.events.FriendMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.message.data.*;
import net.mamoe.mirai.utils.ExternalImage;
import net.mamoe.mirai.utils.Image;
import net.mamoe.mirai.utils.ImageId;
import kotlin.jvm.JvmName;

import java.util.function.Consumer;
public interface MessageSubscribers {

     public static void subscribeMessages(MessageListener listener) {
        MessageSubscribersBuilder<SenderAndMessage<Object>> builder = new MessageSubscribersBuilder<>(senderAndMessage -> {
        });
        builder.subscribeAlways(event -> {
            if (event instanceof FriendMessageEvent) {
                listener.listen(new FriendSenderAndMessage(((FriendMessageEvent) event).getSender(), ((FriendMessageEvent) event).getMessage()));
            } else if (event instanceof GroupMessageEvent) {
                listener.listen(new GroupSenderAndMessage(((GroupMessageEvent) event).getGroup(), ((GroupMessageEvent) event).getSender(), ((GroupMessageEvent) event).getMessage()));
            }
        });
        builder.startListening();
    }

    public static void subscribeGroupMessages(MessageListener listener) {
        MessageSubscribersBuilder<GroupSenderAndMessage> builder = new MessageSubscribersBuilder<>(groupSenderAndMessage -> {
        });
        builder.subscribeAlways(event -> {
            listener.listen(new GroupSenderAndMessage(
                    ((GroupMessageEvent) event).getGroup(),
                    ((GroupMessageEvent) event).getSender(),
                    ((GroupMessageEvent) event).getMessage()));
        });
        builder.startListening();
    }

    public static void subscribeFriendMessages(MessageListener listener) {
        MessageSubscribersBuilder<FriendSenderAndMessage> builder = new MessageSubscribersBuilder<>(friendSenderAndMessage -> {
        });
        builder.subscribeAlways(event -> {
            listener.listen(new FriendSenderAndMessage(
                    ((FriendMessageEvent) event).getSender(),
                    ((FriendMessageEvent) event).getMessage()));
        });
        builder.startListening();
    }
    
    @MessageListenerDsl
    default void subscribeMessages(Bot bot, MessageSubscribersBuilder<SenderAndMessage<?>> builder) {
        MessageSubscribersBuilder<SenderAndMessage<?>> messageSubscribersBuilder = new MessageSubscribersBuilder<SenderAndMessage<?>>() {
            @Override
            public void subscribeAlways(Consumer<SenderAndMessage<?>> consumer) {
                MessageSubscribers.super.subscribeAlways(senderAndMessage -> {
                    if (senderAndMessage instanceof FriendMessageEvent) {
                        consumer.accept(new FriendSenderAndMessage<>((FriendMessageEvent) senderAndMessage));
                    } else if (senderAndMessage instanceof GroupMessageEvent) {
                        consumer.accept(new GroupSenderAndMessage<>((GroupMessageEvent) senderAndMessage));
                    }
                });
            }
        };
        builder.accept(messageSubscribersBuilder);
    }

    @MessageListenerDsl
    default void subscribeGroupMessages(Bot bot, MessageSubscribersBuilder builder) {
        MessageSubscribersBuilder<SenderAndMessage<?>> messageSubscribersBuilder = new MessageSubscribersBuilder<SenderAndMessage<?>>() {
            @Override
            public void subscribeAlways(Consumer<SenderAndMessage<?>> consumer) {
                MessageSubscribers.super.subscribeAlways(senderAndMessage -> consumer.accept(new GroupSenderAndMessage<>(senderAndMessage)));
            }
        };
        builder.accept(messageSubscribersBuilder);
    }

    @MessageListenerDsl
    default void subscribeFriendMessages(Bot bot, MessageSubscribersBuilder builder) {
        MessageSubscribersBuilder<SenderAndMessage<?>> messageSubscribersBuilder = new MessageSubscribersBuilder<SenderAndMessage<?>>() {
            @Override
            public void subscribeAlways(Consumer<SenderAndMessage<?>> consumer) {
                MessageSubscribers.super.subscribeAlways(senderAndMessage -> consumer.accept(new FriendSenderAndMessage<>(senderAndMessage)));
            }
        };
        builder.accept(messageSubscribersBuilder);
    }
}
public abstract class SenderAndMessage<S extends Contact> {
    protected final QQ sender;
    protected final S subject;
    protected final MessageChain message;

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
        reply(PlainText.PlainText(plain));
    }

    public void send(ExternalImage image) {
        image.sendTo(subject);
    }

    public Image upload(ExternalImage image) {
        return image.upload(subject);
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
    public GroupSenderAndMessage(Group group, QQ sender, MessageChain message) {
        super(sender, group, message);
    }
}

@FunctionalInterface
public interface MessageListener<T extends SenderAndMessage<?>> {
    void invoke(T senderAndMessage, String message);
}

@FunctionalInterface
public interface MessageReplier<T extends SenderAndMessage<?>> {
    Message reply(T senderAndMessage, String message);
}

@FunctionalInterface
public interface StringReplier<T extends SenderAndMessage<?>> {
    String reply(T senderAndMessage, String message);
}

public final class MessageSubscribersBuilder<T extends SenderAndMessage<?>> {
    private final Consumer<MessageListener<T>> handlerConsumer;

    public MessageSubscribersBuilder(Consumer<MessageListener<T>> handlerConsumer) {
        this.handlerConsumer = handlerConsumer;
    }

    public void case(String equals, boolean trim, MessageListener<T> listener) {
        content(message -> equals.equals(trim ? message.trim() : message), listener);
    }

    public void contains(String value, MessageListener<T> listener) {
        content(message -> message.contains(value), listener);
    }

    public void startsWith(String prefix, boolean removePrefix, MessageListener<T> listener) {
        content(message -> message.startsWith(prefix), message -> {
            if (removePrefix) {
                return listener.invoke(this, message.substring(prefix.length()));
            } else {
                return listener.invoke(this, message);
            }
        });
    }

    public void endsWith(String start, MessageListener<T> listener) {
        content(message -> message.endsWith(start), listener);
    }

    public void sentBy(int qqId, MessageListener<T> listener) {
        sentBy((long) qqId, listener);
    }

    public void sentBy(long qqId, MessageListener<T> listener) {
        content(senderAndMessage -> senderAndMessage.sender.getId() == qqId, listener);
    }

    public void sentFrom(long id, MessageListener<T> listener) {
        sentFrom(id, (senderAndMessage, message) -> {
            if (senderAndMessage instanceof GroupSenderAndMessage) {
                return ((GroupSenderAndMessage) senderAndMessage).getGroup().getId() == id;
            } else {
                return false;
            }
        });
    }

    public void sentFrom(int id, MessageListener<T> listener) {
        sentFrom((long) id, listener);
    }

    public <M extends Message> void has(MessageListener<T> listener) {
        handlerConsumer.accept(senderAndMessage -> {
            if (senderAndMessage.message.contains(M.class)) {
                listener.invoke(senderAndMessage, senderAndMessage.message.stringValue());
            }
        });
    }

    public void content(MessageListener<T> filter, MessageListener<T> listener) {
        handlerConsumer.accept(senderAndMessage -> {
            if (filter.invoke(senderAndMessage, senderAndMessage.message.stringValue())) {
                listener.invoke(senderAndMessage, senderAndMessage.message.stringValue());
            }
        });
    }

    public void caseReply(String equals, String replier) {
        caseReply(equals, true, message -> PlainText.PlainText(replier));
    }

    public void caseReply(String equals, StringReplier<T> replier) {
        caseReply(equals, true, replier);
    }

    public void containsReply(String contains, String replier) {
        containsReply(contains, message -> PlainText.PlainText(replier));
    }

    public void containsReply(String contains, StringReplier<T> replier) {
        containsReply(contains, replier);
    }

    public void startsWithReply(String startsWith, StringReplier<T> replier) {
        content(message -> message.startsWith(startsWith), message -> {
            Message reply = replier.reply(this, message.substring(startsWith.length()));
            return reply;
        });
    }

    public void endswithReply(String endsWith, StringReplier<T> replier) {
        content(message -> message.endsWith(endsWith), message -> {
            Message reply = replier.reply(this, message);
            return reply;
        });
    }

    public void reply(String equals, String replier) {
        caseReply(equals, replier);
    }

    public void reply(String equals, StringReplier<T> replier) {
        caseReply(equals, replier);
    }
}

@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FUNCTION})
@interface MessageListenerDsl {}