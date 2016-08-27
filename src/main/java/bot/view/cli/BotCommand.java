package bot.view.cli;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import bot.channel.ChannelManager;
import bot.channel.ChannelOperationException;
import bot.channel.TwitchUser;
import bot.channel.permissions.UserPermission;
import twitch.chat.data.OutboundTwitchMessage;


/**
 * Created by Dominic Hauton on 06/05/2016.
 * <p>
 * Converts an input String into a command.
 */
public class BotCommand {
  private final static String commandPrefix = "!bot ";
  private final static char flagPrefix = '-';

  private final TwitchUser twitchUser;
  private final ChannelManager channelManager;
  private BotCommandType botCommandType;
  private ImmutableSet<Character> flags;
  private ImmutableList<String> args;

  public BotCommand(String inputMessage, TwitchUser twitchUser, ChannelManager channelManager) {
    this.twitchUser = twitchUser;
    this.channelManager = channelManager;
    String command = inputMessage.replaceFirst(commandPrefix, "");
    parseCommandMessage(command);
  }

  /**
   * Splits a command on whitespaces. Preserves whitespace in quotes. Trims excess whitespace.
   * Supports quote escape within quotes.
   *
   * @return List of split commands
   */
  static List<String> splitCommand(String inputString) {
    List<String> matchList = new LinkedList<>();
    LinkedList<Character> charList = inputString.chars()
        .mapToObj(i -> (char) i)
        .collect(Collectors.toCollection(LinkedList::new));

    // Finite-State Automaton for parsing.

    CommandSplitterState state = CommandSplitterState.BeginningChunk;
    LinkedList<Character> chunkBuffer = new LinkedList<>();

    for (Character currentChar : charList) {
      switch (state) {
        case BeginningChunk:
          switch (currentChar) {
            case '"':
              state = CommandSplitterState.ParsingQuote;
              break;
            case ' ':
              break;
            default:
              state = CommandSplitterState.ParsingWord;
              chunkBuffer.add(currentChar);
          }
          break;
        case ParsingWord:
          switch (currentChar) {
            case ' ':
              state = CommandSplitterState.BeginningChunk;
              String newWord = chunkBuffer.stream().map(Object::toString).collect(Collectors.joining());
              matchList.add(newWord);
              chunkBuffer = new LinkedList<>();
              break;
            default:
              chunkBuffer.add(currentChar);
          }
          break;
        case ParsingQuote:
          switch (currentChar) {
            case '"':
              state = CommandSplitterState.BeginningChunk;
              String newWord = chunkBuffer.stream().map(Object::toString).collect(Collectors.joining());
              matchList.add(newWord);
              chunkBuffer = new LinkedList<>();
              break;
            case '\\':
              state = CommandSplitterState.EscapeChar;
              break;
            default:
              chunkBuffer.add(currentChar);
          }
          break;
        case EscapeChar:
          switch (currentChar) {
            case '"': // Intentional fall through
            case '\\':
              state = CommandSplitterState.ParsingQuote;
              chunkBuffer.add(currentChar);
              break;
            default:
              state = CommandSplitterState.ParsingQuote;
              chunkBuffer.add('\\');
              chunkBuffer.add(currentChar);
          }
      }
    }

    if (state != CommandSplitterState.BeginningChunk) {
      String newWord = chunkBuffer.stream().map(Object::toString).collect(Collectors.joining());
      matchList.add(newWord);
    }
    return matchList;
  }

  public static boolean isValidCommand(String rawInputMessage) {
    return rawInputMessage.startsWith(commandPrefix);
  }

  /**
   * Called during constructor
   */
  void parseCommandMessage(String rawBotCommand) {
    List<String> splitCommands = splitCommand(rawBotCommand);
    if (splitCommands.isEmpty()) {
      botCommandType = BotCommandType.UNKNOWN;
      flags = ImmutableSet.of();
      args = ImmutableList.of();
    } else {
      String commandName = splitCommands.get(0);
      botCommandType = BotCommandType.getCommand(commandName);
      flags = splitCommands.stream()
          .filter(command -> command.startsWith(String.valueOf(flagPrefix)))
          .map(flags -> flags.substring(1))
          .map(String::toLowerCase)
          .map(CharSequence::chars)
          .flatMap(intStream -> intStream.mapToObj(i -> (char) i))
          .collect(Collectors.collectingAndThen(Collectors.toSet(), ImmutableSet::copyOf));
      args = splitCommands.stream()
          .filter(command -> !command.startsWith(String.valueOf(flagPrefix)))
          .collect(Collectors.collectingAndThen(Collectors.toList(), ImmutableList::copyOf));
    }
  }

  public Collection<OutboundTwitchMessage> parseCommand() throws BotCommandException {
    UserPermission userPermission;
    try {
      userPermission = channelManager.getPermission(twitchUser);
    } catch (ChannelOperationException e) {
      userPermission = UserPermission.ChannelUser;
    }
    if (userPermission.authorizedForActionOfPermissionLevel(botCommandType.requiredUserPermissionLevel())) {
      return botCommandType.getCommandExecutor().executeCommand(flags, args, channelManager);
    } else {
      throw new BotCommandException("Insufficient permissions to run command.");
    }
  }

  private enum CommandSplitterState {
    BeginningChunk, ParsingWord, ParsingQuote, EscapeChar
  }
}
