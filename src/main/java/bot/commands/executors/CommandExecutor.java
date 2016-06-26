package bot.commands.executors;

import bot.commands.BotCommandException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import twitch.channel.ChannelManager;
import twitch.chat.data.OutboundTwitchMessage;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Created by Dominic Hauton on 29/05/2016.
 *
 *
 */
interface CommandExecutor {
    Collection<OutboundTwitchMessage> executeCommand(ImmutableSet<Character> flags,
                                                     ImmutableList<String> args,
                                                     ChannelManager channelManager) throws BotCommandException;
}