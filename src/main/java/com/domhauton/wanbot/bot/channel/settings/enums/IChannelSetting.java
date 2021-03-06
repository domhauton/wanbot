package com.domhauton.wanbot.bot.channel.settings.enums;

/**
 * Created by Dominic Hauton on 26/07/2016.
 *
 * Interface for all ChannelSettingEnums
 */
public interface IChannelSetting<T> {
  T getDefault();

  Class<T> getGenericInterfaceType();
}
