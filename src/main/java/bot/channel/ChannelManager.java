package bot.channel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.Duration;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import bot.channel.blacklist.BlacklistEntry;
import bot.channel.blacklist.BlacklistManager;
import bot.channel.blacklist.BlacklistOperationException;
import bot.channel.blacklist.BlacklistType;
import bot.channel.message.ImmutableTwitchMessageList;
import bot.channel.message.MessageManager;
import bot.channel.message.TwitchMessage;
import bot.channel.permissions.PermissionException;
import bot.channel.permissions.PermissionsManager;
import bot.channel.permissions.UserPermission;
import bot.channel.settings.ChannelSettingDAOHashMapImpl;
import bot.channel.settings.ChannelSettingDao;
import bot.channel.settings.enums.ChannelSettingInteger;
import bot.channel.settings.enums.ChannelSettingString;
import bot.channel.timeouts.TimeoutManager;
import bot.channel.timeouts.TimeoutReason;

/**
 * Created by Dominic Hauton on 12/03/2016.
 *
 * Stores information about the user channel.
 */
public class ChannelManager {
  private static final Logger log = LogManager.getLogger();
  private final String channelName;
  private final PermissionsManager permissionsManager;
  private final MessageManager messageManager;
  private final TimeoutManager timeoutManager;
  private final BlacklistManager blacklistManager;
  private final ChannelSettingDao channelSettingDao;

  public ChannelManager(String channelName) {
    this(channelName,
        new PermissionsManager(),
        new MessageManager(),
        new TimeoutManager(),
        new BlacklistManager(),
        new ChannelSettingDAOHashMapImpl());
  }

  ChannelManager(
      String channelName,
      PermissionsManager permissionsManager,
      MessageManager messageManager,
      TimeoutManager timeoutManager,
      BlacklistManager blacklistManager,
      ChannelSettingDao channelSettingDao) {
    this.channelName = channelName;
    this.permissionsManager = permissionsManager;
    this.messageManager = messageManager;
    this.timeoutManager = timeoutManager;
    this.blacklistManager = blacklistManager;
    this.channelSettingDao = channelSettingDao;
  }

  /**
   * Checks if the given user has permission for the requested action per
   *
   * @return true if user has permission for the action
   */
  boolean checkPermission(TwitchUser user, UserPermission requiredPermission) throws ChannelOperationException {
    return getPermission(user).authorizedForActionOfPermissionLevel(requiredPermission);
  }

  public UserPermission getPermission(TwitchUser twitchUser) throws ChannelOperationException {
    try {
      return permissionsManager.getUser(twitchUser);
    } catch (PermissionException e) {
      String defaultPermissionString = channelSettingDao.getSettingOrDefault(channelName, ChannelSettingString
          .DEFAULT_PERMISSION);
      try {
        return UserPermission.valueOf(defaultPermissionString);
      } catch (IllegalArgumentException e2) {
        throw new ChannelOperationException("Failed to cast default permission to valid permission");
      }
    }
  }

  public void setPermission(TwitchUser twitchUser, UserPermission newPermission) {
    log.info("Setting permission {} for user {}", newPermission::toString, twitchUser::toString);
    permissionsManager.changeUserPermission(twitchUser, newPermission);
  }

  /**
   * Adds a message to the channel message manager.
   *
   * @return true if message passed blacklists.
   * @throws ChannelOperationException insertion failed. Reason unknown.
   */
  public boolean addChannelMessage(TwitchMessage message) throws ChannelOperationException {
    if (messageManager.addMessage(message)) {
      // n.b. Inverted boolean!
      return !blacklistManager.isMessageBlacklisted(message.getMessage());
    } else {
      throw new ChannelOperationException("Failed to insert message into channel. Reason Unknown.");
    }
  }

  public ImmutableTwitchMessageList getMessageSnapshot() {
    return messageManager.getChannelSnapshot();
  }

  public ImmutableTwitchMessageList getMessageSnapshot(TwitchUser username) {
    return messageManager.getUserSnapshot(username);
  }

  Duration getUserTimeout(TwitchUser twitchUser) {
    return timeoutManager.getUserTimeout(twitchUser.getUsername());
  }

  public Duration addUserTimeout(String twitchUser, TimeoutReason timeoutReason) {
    log.info("Adding a timeout {} for user {}", timeoutReason::toString, twitchUser::toString);
    return timeoutManager.addUserTimeout(twitchUser, timeoutReason);
  }

  public Collection<TwitchMessage> blacklistItem(String input, BlacklistType blacklistType) throws
      ChannelOperationException {
    Integer messageLookBehind = channelSettingDao.getSettingOrDefault(channelName, ChannelSettingInteger
        .CHANNEL_RETROSPECTIVE_LOOKBACK);
    return blacklistItem(input, blacklistType, messageLookBehind);
  }

  /**
   * @return List of messages breaching new item.
   */
  public Collection<TwitchMessage> blacklistItem(
      String input,
      BlacklistType blacklistType,
      int messageLookBehind) throws ChannelOperationException {
    log.info("Adding item {} to channel {} blacklist as {} with {} look behind", input, channelName,
        blacklistType, messageLookBehind);
    ImmutableTwitchMessageList messageList = getMessageSnapshot();
    if (messageLookBehind <= 0) {
      blacklistManager.addToBlacklist(input, blacklistType);
      return Collections.emptyList();
    } else {
      Collection<TwitchMessage> trimmedMessageList = messageList
          .stream()
          .limit(messageLookBehind)
          .collect(Collectors.toList());
      BlacklistEntry blacklistEntry = blacklistManager.addToBlacklist(input, blacklistType);
      return trimmedMessageList
          .stream()
          .filter(message -> blacklistEntry.matches(message.getMessage()))
          .collect(Collectors.toList());
    }
  }

  /**
   * Remove exact blacklist entry
   *
   * @return Blacklist entry that has been removed
   * @throws ChannelOperationException if Blacklist entry request was not found.
   */
  public BlacklistEntry removeBlacklistItem(String input, BlacklistType blacklistType) throws ChannelOperationException {
    try {
      return blacklistManager.removeFromBlacklist(input, blacklistType);
    } catch (BlacklistOperationException e) {
      throw new ChannelOperationException("Failed to remove blacklist entry " + input + " of type " +
          blacklistType.toString());
    }
  }

  /**
   * Fuzzy removal of blacklist entry. Will first search exact, then any matching entry
   *
   * @param input contents of blacklist message
   * @return All blacklist entries that have been removed.
   */
  Collection<BlacklistEntry> removeBlacklistItem(String input) {
    return blacklistManager.removeFromBlacklist(input);
  }

  public String getChannelName() {
    return channelName;
  }
}