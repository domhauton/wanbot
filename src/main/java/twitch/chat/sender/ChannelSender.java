package twitch.chat.sender;

import com.google.inject.name.Named;

import org.joda.time.Period;
import org.joda.time.PeriodType;

import javax.inject.Inject;

import irc.ChatHandshake;
import twitch.chat.data.OutboundTwitchMessage;
import twitch.chat.exceptions.TwitchChatException;

/**
 * Created by Dominic Hauton on 13/04/2016.
 *
 * Used to send messages to a given TwitchChannel
 */
class ChannelSender extends MessageSender {

  private final static Period messageDelay = new Period(400, PeriodType.millis());
  private String channelName;
  private String twitchChatServerName;
  private Integer twitchChatServerPort;

  @Inject
  public ChannelSender(
      @Named("twitch.username") String twitchUsername,
      @Named("twitch.oauth.token") String oAuthToken,
      @Named("twitch.irc.public.twitchChannel") String channelName,
      @Named("twitch.irc.public.server") String twitchChatServerName,
      @Named("twitch.irc.public.port") Integer twitchChatServerPort,
      @Named("twitch.irc.public.eventCountPerWindow") Integer maxEventCountPerWindow,
      @Named("twitch.irc.public.eventCountWindowSize") Integer windowSizeSeconds) {
    super(twitchUsername, oAuthToken, new AsyncEventBuffer(maxEventCountPerWindow, windowSizeSeconds));
    this.channelName = channelName;
    this.twitchChatServerName = twitchChatServerName;
    this.twitchChatServerPort = twitchChatServerPort;
  }

  public void connect() throws TwitchChatException {
    super.connect(channelName, twitchChatServerName, twitchChatServerPort);
    sendChatHandshake(ChatHandshake.COMMANDS);
    setMessageDelay(messageDelay);
  }

  public void sendChannelMessage(OutboundTwitchMessage outboundTwitchMessage) {
    sendMessageAsync(outboundTwitchMessage);
  }
}
