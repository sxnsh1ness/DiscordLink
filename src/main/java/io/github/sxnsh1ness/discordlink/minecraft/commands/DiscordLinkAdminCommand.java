package io.github.sxnsh1ness.discordlink.minecraft.commands;

import io.github.sxnsh1ness.discordlink.DiscordLink;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jspecify.annotations.NonNull;

import java.sql.SQLException;
import java.util.*;

public class DiscordLinkAdminCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (!sender.hasPermission("discordlink.admin")) {
            sender.sendMessage(DiscordLink.getInstance().getConfigManager().getMessage("errors.no-permission"));
            return true;
        }

        if (args.length == 0) {
            showAdminHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                DiscordLink.getInstance().reload();
                sender.sendMessage(DiscordLink.getInstance().getConfigManager().getMessage("admin.reloaded"));
            }
            case "status" -> showStatus(sender);
            case "forceunlink" -> handleForceUnlink(sender, args);
            case "info" -> handlePlayerInfo(sender, args);
            case "syncroles" -> handleSyncRoles(sender, args);
            default -> showAdminHelp(sender);
        }

        return true;
    }

    private void showStatus(CommandSender sender) {
        sender.sendMessage("§9=== DiscordLink Status ===");
        sender.sendMessage("§7Bot connected: " + (DiscordLink.getInstance().getDiscordBot().isReady() ? "§a✓" : "§c✗"));
        sender.sendMessage("§7Guild: " + (DiscordLink.getInstance().getDiscordBot().getGuild() != null
                ? "§a" + DiscordLink.getInstance().getDiscordBot().getGuild().getName() : "§cNot found"));
        sender.sendMessage("§7Chat enabled: " + (DiscordLink.getInstance().getConfigManager().isChatEnabled() ? "§a✓" : "§c✗"));
        sender.sendMessage("§7 2FA enabled: " + (DiscordLink.getInstance().getConfigManager().is2FAEnabled() ? "§a✓" : "§c✗"));
        sender.sendMessage("§7Link required: " + (DiscordLink.getInstance().getConfigManager().isLinkRequired() ? "§a✓" : "§c✗"));
        sender.sendMessage("§7Role sync: " + (DiscordLink.getInstance().getConfigManager().isRoleSyncEnabled() ? "§a✓" : "§c✗"));
    }

    private void handleForceUnlink(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /discordlink forceunlink <player>");
            return;
        }
        String playerName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(playerName);
        if (target == null) {
            sender.sendMessage(DiscordLink.getInstance().getConfigManager().getMessage("admin.player-not-found"));
            return;
        }

        DiscordLink.getInstance().getServer().getScheduler().runTaskAsynchronously(DiscordLink.getInstance(), () -> {
            boolean success = DiscordLink.getInstance().getLinkManager().unlink(target.getUniqueId());
            if (success) {
                sender.sendMessage(DiscordLink.getInstance().getConfigManager().getMessage("admin.force-unlinked",
                        Map.of("player", playerName)));
            } else {
                sender.sendMessage("§cPlayer is not linked.");
            }
        });
    }

    private void handlePlayerInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /discordlink info <player>");
            return;
        }

        String playerName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(playerName);
        if (target == null) {
            sender.sendMessage(DiscordLink.getInstance().getConfigManager().getMessage("admin.player-not-found"));
            return;
        }

        DiscordLink.getInstance().getServer().getScheduler().runTaskAsynchronously(DiscordLink.getInstance(), () -> {
            try {
                boolean linked = DiscordLink.getInstance().getDatabase().isLinked(target.getUniqueId());
                sender.sendMessage("§9=== " + playerName + " ===");
                sender.sendMessage("§7Linked: " + (linked ? "§aYes" : "§cNo"));
                if (linked) {
                    String discordId = DiscordLink.getInstance().getDatabase().getDiscordId(target.getUniqueId());
                    String discordTag = DiscordLink.getInstance().getDatabase().getDiscordTag(target.getUniqueId());
                    sender.sendMessage("§7Discord: §e" + discordTag + " §7(" + discordId + ")");
                }
            } catch (SQLException e) {
                sender.sendMessage("§cDatabase error: " + e.getMessage());
            }
        });
    }

    private void handleSyncRoles(CommandSender sender, String[] args) {
        if (!DiscordLink.getInstance().getConfigManager().isRoleSyncEnabled()) {
            sender.sendMessage("§cRole sync is disabled in config.yml");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /discordlink syncroles <player>");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) {
            sender.sendMessage(DiscordLink.getInstance().getConfigManager().getMessage("admin.player-not-found"));
            return;
        }

        DiscordLink.getInstance().getServer().getScheduler().runTaskAsynchronously(DiscordLink.getInstance(), () -> {
            String discordId = DiscordLink.getInstance().getLinkManager().getDiscordId(target.getUniqueId());
            if (discordId == null) {
                sender.sendMessage("§c" + args[1] + " is not linked.");
                return;
            }
            DiscordLink.getInstance().getLinkManager().syncRoles(target.getUniqueId(), discordId);
            sender.sendMessage("§aRole sync triggered for " + args[1]);
        });
    }

    private void showAdminHelp(CommandSender sender) {
        sender.sendMessage("§9=== DiscordLink Admin ===");
        sender.sendMessage("§e/dl reload §7- Reload configuration");
        sender.sendMessage("§e/dl status §7- Show plugin status");
        sender.sendMessage("§e/dl info <player> §7- Show player link info");
        sender.sendMessage("§e/dl forceunlink <player> §7- Force unlink a player");
        sender.sendMessage("§e/dl syncroles <player> §7- Sync roles for a player");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, @NonNull Command cmd, @NonNull String alias, String @NonNull [] args) {
        if (!sender.hasPermission("discordlink.admin")) return List.of();
        if (args.length == 1) {
            return Arrays.asList("reload", "status", "info", "forceunlink", "syncroles");
        }
        if (args.length == 2) {
            List<String> players = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> players.add(p.getName()));
            return players;
        }
        return List.of();
    }
}
