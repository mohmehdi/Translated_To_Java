
package net.mamoe.mirai.internal.contact;

import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.SupervisorJob;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.internal.QQAndroidBot;
import net.mamoe.mirai.utils.cast;
import net.mamoe.mirai.utils.getValue;
import net.mamoe.mirai.utils.unsafeWeakRef;

public abstract class AbstractContact implements Contact {
    private final CoroutineContext coroutineContext;
    private final QQAndroidBot bot;

    public AbstractContact(Bot bot, CoroutineContext coroutineContext) {
        this.coroutineContext = coroutineContext.plus(new SupervisorJob(coroutineContext.get(Job)));
        this.bot = bot.cast(QQAndroidBot.class).unsafeWeakRef();
    }
}