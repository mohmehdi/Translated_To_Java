
package net.mamoe.mirai.internal.contact;

import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.User;
import kotlin.coroutines.CoroutineContext;

internal abstract class AbstractUser extends AbstractContact implements User {
    private final long id;
    private final String nick;
    private final String remark;

    public AbstractUser(Bot bot, CoroutineContext coroutineContext, net.mamoe.mirai.data.FriendInfo friendInfo) {
        super(bot, coroutineContext);
        this.id = friendInfo.getUin();
        this.nick = friendInfo.getNick();
        this.remark = friendInfo.getRemark();
    }
}