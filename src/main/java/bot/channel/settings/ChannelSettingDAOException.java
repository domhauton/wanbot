package bot.channel.settings;

import bot.channel.ChannelOperationException;

/**
 * Created by Dominic Hauton on 26/06/2016.
 *
 * Basic Exception for the DAO
 */
public class ChannelSettingDAOException extends ChannelOperationException {

  public ChannelSettingDAOException(String message) {
    super(message);
  }
}
