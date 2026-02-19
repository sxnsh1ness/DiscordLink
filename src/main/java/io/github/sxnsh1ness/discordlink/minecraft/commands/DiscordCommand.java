package io.github.sxnsh1ness.discordlink.minecraft.commands;

import io.github.sxnsh1ness.discordlink.DiscordLink;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class DiscordCommand implements CommandExecutor, TabCompleter {

    private final DiscordLink plugin;

    public DiscordCommand(DiscordLink plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("errors.player-only"));
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "link" -> handleLink(player);
            case "unlink" -> handleUnlink(player, args);
            case "info", "status" -> handleInfo(player);
            case "help" -> showHelp(player);
            case "2fa" -> handleTwoFA(player, args);
            default -> player.sendMessage(plugin.getConfigManager().getMessage("errors.unknown-command"));
        }

        return true;
    }

    private void handleLink(Player player) {
        if (!player.hasPermission("discordlink.link")) {
            player.sendMessage(plugin.getConfigManager().getMessage("errors.no-permission"));
            return;
        }

        if (!plugin.getDiscordBot().isReady()) {
            player.sendMessage(plugin.getConfigManager().getMessage("errors.discord-offline"));
            return;
        }

        if (plugin.getLinkManager().isLinked(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("link.already-linked"));
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String code = plugin.getLinkManager().generateLinkCode(player.getUniqueId(), player.getName());
                player.sendMessage(plugin.getConfigManager().getMessage("link.code-generated",
                        Map.of(
                                "code", code,
                                "seconds", "600"
                        )));
            } catch (SQLException e) {
                player.sendMessage("§cAn error occurred. Please try again.");
            }
        });
    }

    private void handleUnlink(Player player, String[] args) {
        if (!player.hasPermission("discordlink.unlink")) {
            player.sendMessage(plugin.getConfigManager().getMessage("errors.no-permission"));
            return;
        }

        if (!plugin.getConfigManager().isUnlinkAllowed()) {
            player.sendMessage(plugin.getConfigManager().getMessage("unlink.disabled"));
            return;
        }

        if (!plugin.getLinkManager().isLinked(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getMessage("unlink.not-linked"));
            return;
        }

        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            player.sendMessage(plugin.getConfigManager().getMessage("unlink.confirm"));
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = plugin.getLinkManager().unlink(player.getUniqueId());
            if (success) {
                player.sendMessage(plugin.getConfigManager().getMessage("unlink.success"));
            } else {
                player.sendMessage("§cFailed to unlink. Please try again.");
            }
        });
    }

    private void handleInfo(Player player) {
        if (!player.hasPermission("discordlink.info")) {
            player.sendMessage(plugin.getConfigManager().getMessage("errors.no-permission"));
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean linked = plugin.getLinkManager().isLinked(player.getUniqueId());
            if (!linked) {
                player.sendMessage(plugin.getConfigManager().getMessage("info.not-linked"));
            } else {
                String tag = plugin.getLinkManager().getDiscordTag(player.getUniqueId());
                String id = plugin.getLinkManager().getDiscordId(player.getUniqueId());
                player.sendMessage(plugin.getConfigManager().getMessage("info.linked",
                        Map.of("tag", tag != null ? tag : "Unknown", "id", id != null ? id : "Unknown")));
            }
        });
    }

    private void handleTwoFA(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + "§cUsage: /discord 2fa <code>");
            return;
        }

        if (!plugin.getTwoFAManager().hasPendingSession(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + "§cNo active 2FA session.");
            return;
        }

        String code = args[1].trim();
        var result = plugin.getTwoFAManager().verify(player.getUniqueId(), code);

        switch (result) {
            case SUCCESS -> player.sendMessage(plugin.getConfigManager().getMessage("2fa.success"));
            case WRONG_CODE -> player.sendMessage(plugin.getConfigManager().getMessage("2fa.failed"));
            case EXPIRED -> player.sendMessage(plugin.getConfigManager().getMessage("2fa.expired"));
            case NO_SESSION -> player.sendMessage(plugin.getConfigManager().getPrefix() + "§cNo active 2FA session.");
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(plugin.getConfigManager().getMessage("help.header"));
        player.sendMessage(plugin.getConfigManager().getMessage("help.link"));
        player.sendMessage(plugin.getConfigManager().getMessage("help.unlink"));
        player.sendMessage(plugin.getConfigManager().getMessage("help.info"));
        player.sendMessage(plugin.getConfigManager().getMessage("help.footer"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("link", "unlink", "info", "2fa", "help");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("unlink")) {
            return List.of("confirm");
        }
        return List.of();
    }
}
