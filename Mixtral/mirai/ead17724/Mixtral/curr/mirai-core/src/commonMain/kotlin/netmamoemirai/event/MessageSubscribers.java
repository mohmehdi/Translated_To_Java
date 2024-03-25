package net.mamoe.mirai.event;

import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.contact.QQ;
import net.mamoe.mirai.event.events.BotEvent;
import net.mamoe.mirai.event.events.FriendMessageEvent;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.message.data.*;
import net.mamoe.mirai.message.codecs.ExternalImage;
import net.mamoe.mirai.utils.Image;
import net.mamoe.mirai.utils.ImageId;
import net.mamoe.mirai.utils.UploadFailureException;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;


public class MessageSubscribers {

    @MessageDsl
    public static void subscribeMessages(MessageDsl listeners) {
        MessageSubscribersBuilder<SenderAndMessage<?>> builder = new MessageSubscribersBuilder<>((t) -> {
        });
        builder.subscribeAlways((SenderAndMessage<?> senderAndMessage) -> {
            if (senderAndMessage instanceof FriendMessageEvent) {
                listeners.subscribe(builder.subscribeAlways(friendSenderAndMessage -> {
                    friendSenderAndMessage.accept(new FriendSenderAndMessage<>(((FriendMessageEvent) senderAndMessage).sender, ((FriendMessageEvent) senderAndMessage).message));
                }));
            } else if (senderAndMessage instanceof GroupMessageEvent) {
                listeners.subscribe(builder.subscribeAlways(groupSenderAndMessage -> {
                    groupSenderAndMessage.accept(new GroupSenderAndMessage<>(((GroupMessageEvent) senderAndMessage).group, ((GroupMessageEvent) senderAndMessage).sender, ((GroupMessageEvent) senderAndMessage).message));
                }));
            }
        });
    }

    @MessageDsl
    public static void subscribeGroupMessages(MessageDsl listeners) {
        MessageSubscribersBuilder<GroupSenderAndMessage> builder = new MessageSubscribersBuilder<>(groupSenderAndMessage -> {
        });
        builder.subscribeAlways(groupSenderAndMessage -> {
            listeners.subscribe(builder.subscribeAlways(it -> {
                it.accept(groupSenderAndMessage);
            }));
        });
    }

    @MessageDsl
    public static void subscribeFriendMessages(MessageDsl listeners) {
        MessageSubscribersBuilder<FriendSenderAndMessage> builder = new MessageSubscribersBuilder<>(friendSenderAndMessage -> {
        });
        builder.subscribeAlways(friendSenderAndMessage -> {
            listeners.subscribe(builder.subscribeAlways(it -> {
                it.accept(friendSenderAndMessage);
            }));
        });
    }

    @SuppressWarnings("unchecked")
    public static <T extends SenderAndMessage<M>, M extends MessageSource> void subscribeMessages(Bot bot, MessageSubscribersBuilder<T> listeners) {
        subscribeAlways(bot, event -> {
            if (event instanceof FriendMessageEvent) {
                FriendMessageEvent friendMessageEvent = (FriendMessageEvent) event;
                listeners.subscribe((T) new FriendSenderAndMessage<>(friendMessageEvent.getSender(), friendMessageEvent.getMessage()), friendMessageEvent.getMessage().getString());
            } else if (event instanceof GroupMessageEvent) {
                GroupMessageEvent groupMessageEvent = (GroupMessageEvent) event;
                listeners.subscribe((T) new GroupSenderAndMessage<>(groupMessageEvent.getGroup(), groupMessageEvent.getSender(), groupMessageEvent.getMessage()), groupMessageEvent.getMessage().getString());
            }
        });
    }

    @SuppressWarnings("unchecked")
    public static <T extends SenderAndMessage<M>, M extends MessageSource> void subscribeGroupMessages(Bot bot, MessageSubscribersBuilder<T> listeners) {
        subscribeAlways(bot, event -> {
            if (event instanceof GroupMessageEvent) {
                GroupMessageEvent groupMessageEvent = (GroupMessageEvent) event;
                listeners.subscribe((T) new GroupSenderAndMessage<>(groupMessageEvent.getGroup(), groupMessageEvent.getSender(), groupMessageEvent.getMessage()), groupMessageEvent.getMessage().getString());
            }
        });
    }

    @SuppressWarnings("unchecked")
    public static <T extends SenderAndMessage<M>, M extends MessageSource> void subscribeFriendMessages(Bot bot, MessageSubscribersBuilder<T> listeners) {
        subscribeAlways(bot, event -> {
            if (event instanceof FriendMessageEvent) {
                FriendMessageEvent friendMessageEvent = (FriendMessageEvent) event;
                listeners.subscribe((T) new FriendSenderAndMessage<>(friendMessageEvent.getSender(), friendMessageEvent.getMessage()), friendMessageEvent.getMessage().getString());
            }
        });
    }

}
public class SenderAndMessage<S extends Contact> {
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

    public void send(ExternalImage image) {
        sendTo(image, subject);
    }

    public Image upload(ExternalImage image) {
        return upload(image, subject);
    }

    public void send(Image image) {
        sendTo(image, subject);
    }

    public void send(ImageId imageId) {
        sendTo(imageId, subject);
    }

    public void send(Message message) {
        sendTo(message, subject);
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
public interface MessageReplier<T> extends Function<T, Message> {

}

public interface StringReplier<T> extends Function<T, String> {

}

public class MessageSubscribersBuilder<T extends SenderAndMessage<S>, S extends Contact> {
    private final Subscriber subscriber;


    public MessageSubscribersBuilder(Consumer<BiConsumer<MessageDsl, MessageDsl>> subscriber) {
        this.subscriber = subscriber;
    }
    
    public MessageSubscribersBuilder<T> case(String equals, boolean trim, boolean ignoreCase, Consumer<T> onEvent) {
        return content(s -> equals.equals(trim ? s.trim() : s), ignoreCase, onEvent);
    }

    public MessageSubscribersBuilder<T> contains(String sub, Consumer<T> onEvent) {
        return content(s -> s.contains(sub), onEvent);
    }

    public MessageSubscribersBuilder<T> startsWith(String prefix, boolean removePrefix, Consumer<T> onEvent) {
        return content(s -> s.startsWith(prefix), (message, onSubscribe) -> {
            if (removePrefix) {
                onSubscribe.accept(() -> onEvent.accept(message.substringAfter(prefix))));
            } else {
                onSubscribe.accept(() -> onEvent.accept(message));
            }
        });
    

    public MessageSubscribersBuilder<T> endsWith(String suffix, Consumer<T> onEvent) {
        return content(s -> s.endsWith(suffix), onEvent);
    }

    public MessageSubscribersBuilder<T> sentBy(Number qqId, Consumer<T> onEvent) {
        return content(message -> message.sender().id() == qqId, onEvent);
    }

    public MessageSubscribersBuilder<T> sentFrom(Number id, Consumer<T> onEvent) {
        return content(message -> {
            if (message instanceof GroupSenderAndMessage) {
                return ((GroupSenderAndMessage) message).group().id() == id;
            }
            return false;
        }, onEvent);
    }

    public <M extends Message> void has(OnEvent<M> onEvent) {
        EventBus.getDefault().register(this);
    }

    public void content(StringFilter<T> filter, StringReply<T> onEvent) {
        subscriber.subscribe(new SubscriberCallback<T>() {
            @Override
            public void onEvent(T context, String message) {
                if (filter.filter(context, message)) {
                    onEvent.onReply(context, message);
                }
            }
        });
    }

        public CaseReply<T> caseReply(String string) {
            return new CaseReply<>(case(this.string, true, s -> s.reply(string)));
        }

        public CaseReply<T> caseReply(Function<String, T> stringReplier) {
            return new CaseReply<>(case(this.string, true, s -> s.reply(stringReplier.apply(s))));
        }

        public CaseReply<T> containsReply(String string) {
            return new CaseReply<>(case(this.string, false, s -> s.content(it -> it.contains(this.string)).reply(string)));
        }

        public CaseReply<T> containsReply(Function<String, T> stringReplier) {
            return new CaseReply<>(case(this.string, false, s -> s.content(it -> it.contains(this.string)).reply(stringReplier.apply(s))));
        }

        public CaseReply<T> startsWithReply(Function<String, T> stringReplier) {
            return new CaseReply<>(case(this.string, false, s -> s.content(it -> it.startsWith(this.string)).reply(stringReplier.apply(s))));
        }

        public CaseReply<T> endswithReply(Function<String, T> stringReplier) {
            return new CaseReply<>(case(this.string, false, s -> s.content(it -> it.endsWith(this.string)).reply(stringReplier.apply(s))));
        }

        public CaseReply<T> reply(String string) {
            return new CaseReply<>(case(this.string, false, s -> s.reply(string)));
        }

        public CaseReply<T> reply(Function<String, T> stringReplier) {
            return new CaseReply<>(case(this.string, false, s -> s.reply(stringReplier.apply(s))));
        }

}


@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
public @interface MessageDsl {
}
