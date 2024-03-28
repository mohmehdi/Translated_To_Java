
package net.mamoe.mirai.internal.contact;

import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.User;
import net.mamoe.mirai.data.FriendInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CoroutineContext;

public abstract class AbstractUser extends User implements AbstractContact {
    private final long id;
    private final String nick;
    private final String remark;

    public AbstractUser(@NotNull Bot bot, @NotNull CoroutineContext coroutineContext, @NotNull FriendInfo friendInfo) {
        super(bot, coroutineContext);
        this.id = friendInfo.getUin();
        this.nick = friendInfo.getNick();
        this.remark = friendInfo.getRemark();
    }

}