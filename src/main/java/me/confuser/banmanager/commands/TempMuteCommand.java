package me.confuser.banmanager.commands;

import java.sql.SQLException;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.confuser.banmanager.BanManager;
import me.confuser.banmanager.configs.TimeLimitType;
import me.confuser.banmanager.data.PlayerMuteData;
import me.confuser.banmanager.data.PlayerData;
import me.confuser.banmanager.util.DateUtils;
import me.confuser.banmanager.util.UUIDUtils;
import me.confuser.bukkitutil.Message;
import me.confuser.bukkitutil.commands.BukkitCommand;

public class TempMuteCommand extends BukkitCommand<BanManager> {

      public TempMuteCommand() {
            super("tempmute");
      }

      @Override
      public boolean onCommand(final CommandSender sender, Command command, String commandName, String[] args) {
            if (args.length < 3) {
                  return false;
            }

            if (args[0].toLowerCase().equals(sender.getName().toLowerCase())) {
                  sender.sendMessage(Message.getString("noSelf"));
                  return true;
            }

            // Check if UUID vs name
            final String playerName = args[0];
            final boolean isUUID = playerName.length() > 16;
            boolean isMuted = false;

            if (isUUID) {
                  isMuted = plugin.getPlayerMuteStorage().isMuted(UUID.fromString(playerName));
            } else {
                  isMuted = plugin.getPlayerMuteStorage().isMuted(playerName);
            }

            if (isMuted) {
                  Message message = Message.get("alreadyMuted");
                  message.set("player", playerName);

                  sender.sendMessage(message.toString());
                  return true;
            }

            long expiresCheck;

            try {
                  expiresCheck = DateUtils.parseDateDiff(args[1], true);
            } catch (Exception e1) {
                  sender.sendMessage(Message.get("invalidTime").toString());
                  return true;
            }
            
            if (plugin.getConfiguration().getTimeLimits().isPastLimit(sender, TimeLimitType.PLAYER_MUTE, expiresCheck)) {
            	Message.get("timeLimitError").sendTo(sender);
            	return true;
            }

            final long expires = expiresCheck;
            final String reason = StringUtils.join(args, " ", 2, args.length);

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {

                  @Override
                  public void run() {
                        PlayerData player;

                        if (isUUID) {
                              try {
                                    player = plugin.getPlayerStorage().queryForId(UUIDUtils.toBytes(UUID.fromString(playerName)));
                              } catch (SQLException e) {
                                    sender.sendMessage(Message.get("errorOccurred").toString());
                                    e.printStackTrace();
                                    return;
                              }
                        } else {
                              player = plugin.getPlayerStorage().retrieve(playerName, true);
                        }

                        if (player == null) {
                              sender.sendMessage(Message.get("playerNotFound").set("player", playerName).toString());
                              return;
                        }

                        PlayerData actor;

                        if (sender instanceof Player) {
                              actor = plugin.getPlayerStorage().getOnline((Player) sender);
                        } else {
                              actor = plugin.getPlayerStorage().getConsole();
                        }

                        PlayerMuteData mute = new PlayerMuteData(player, actor, reason, expires);
                        boolean created;

                        try {
                              created = plugin.getPlayerMuteStorage().mute(mute);
                        } catch (SQLException e) {
                              sender.sendMessage(Message.get("errorOccurred").toString());
                              e.printStackTrace();
                              return;
                        }

                        if (!created) {
                              return;
                        }

                        if (plugin.getPlayerStorage().isOnline(player.getUUID())) {
                              Player bukkitPlayer = plugin.getServer().getPlayer(player.getUUID());

                              Message muteMessage = Message.get("tempMuted")
                                      .set("displayName", bukkitPlayer.getDisplayName())
                                      .set("player", player.getName())
                                      .set("reason", mute.getReason())
                                      .set("actor", actor.getName())
                                      .set("expires", DateUtils.getDifferenceFormat(mute.getExpires()));

                              bukkitPlayer.sendMessage(muteMessage.toString());
                        }

                  }

            });

            return true;
      }

}
