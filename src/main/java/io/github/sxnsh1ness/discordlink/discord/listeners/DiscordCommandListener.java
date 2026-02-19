package io.github.sxnsh1ness.discordlink.discord.listeners;

import io.github.sxnsh1ness.discordlink.DiscordLink;
import io.github.sxnsh1ness.discordlink.linking.LinkManager;
import io.github.sxnsh1ness.discordlink.util.Logger;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.UUID;

public class DiscordCommandListener extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String discordId = event.getUser().getId();
        String discordTag = event.getUser().getAsTag();

        switch (event.getName()) {

            case "link" -> handleLink(event, discordId, discordTag);
            case "unlink" -> handleUnlink(event, discordId);
            case "verify" -> handleVerify(event, discordId);
            case "2fa" -> handleTwoFA(event, discordId);
        }
    }

    private void handleLink(SlashCommandInteractionEvent event, String discordId, String discordTag) {
        String code = event.getOption("code").getAsString().toUpperCase().trim();

        Bukkit.getScheduler().runTaskAsynchronously(DiscordLink.getInstance(), () -> {
            LinkManager.LinkResult result = DiscordLink.getInstance().getLinkManager().completeLink(code, discordId, discordTag);

            String response = switch (result) {
                case SUCCESS -> "✅ Your account has been successfully linked!";
                case INVALID_CODE -> "❌ Invalid or expired code. Generate a new one with `/discord link` in-game.";
                case DISCORD_ALREADY_LINKED -> "❌ Your Discord account is already linked to a Minecraft account. Use `/unlink` to unlink.";
                case MINECRAFT_ALREADY_LINKED -> "❌ That Minecraft account is already linked to another Discord account.";
                case ERROR -> "⚠️ An error occurred. Please try again later.";
            };

            event.reply(response).setEphemeral(true).queue();
        });
    }

    private void handleUnlink(SlashCommandInteractionEvent event, String discordId) {
        if (!DiscordLink.getInstance().getConfigManager().isUnlinkAllowed()) {
            event.reply("❌ Unlinking is disabled on this server.").setEphemeral(true).queue();
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(DiscordLink.getInstance(), () -> {
            try {
                boolean wasLinked = DiscordLink.getInstance().getDatabase().isDiscordLinked(discordId);
                if (!wasLinked) {
                    event.reply("❌ Your Discord account is not linked to any Minecraft account.").setEphemeral(true).queue();
                    return;
                }

                UUID uuid = DiscordLink.getInstance().getDatabase().getMinecraftUUID(discordId);
                DiscordLink.getInstance().getLinkManager().unlinkByDiscordId(discordId);

                if (uuid != null) {
                    Bukkit.getScheduler().runTask(DiscordLink.getInstance(), () -> {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) {
                            player.sendMessage(DiscordLink.getInstance().getConfigManager().getMessage("unlink.success"));
                        }
                    });
                }

                event.reply("✅ Your account has been unlinked.").setEphemeral(true).queue();
            } catch (SQLException e) {
                Logger.severe("DB error during Discord unlink: " + e.getMessage());
                event.reply("⚠️ An error occurred. Please try again later.").setEphemeral(true).queue();
            }
        });
    }

    private void handleVerify(SlashCommandInteractionEvent event, String discordId) {
        Bukkit.getScheduler().runTaskAsynchronously(DiscordLink.getInstance(), () -> {
            try {
                if (!DiscordLink.getInstance().getDatabase().isDiscordLinked(discordId)) {
                    event.reply("❌ Your Discord account is not linked to any Minecraft account.\nLink it in-game using `/discord link`.").setEphemeral(true).queue();
                    return;
                }
                UUID uuid = DiscordLink.getInstance().getDatabase().getMinecraftUUID(discordId);
                String playerName = uuid != null ? Bukkit.getOfflinePlayer(uuid).getName() : "Unknown";
                event.reply("✅ You are linked to Minecraft account: **" + playerName + "**").setEphemeral(true).queue();
            } catch (SQLException e) {
                event.reply("⚠️ An error occurred.").setEphemeral(true).queue();
            }
        });
    }

    private void handleTwoFA(SlashCommandInteractionEvent event, String discordId) {
        Bukkit.getScheduler().runTaskAsynchronously(DiscordLink.getInstance(), () -> {
            try {
                UUID uuid = DiscordLink.getInstance().getDatabase().getMinecraftUUID(discordId);
                if (uuid == null) {
                    event.reply("❌ Your Discord is not linked to a Minecraft account.").setEphemeral(true).queue();
                    return;
                }

                if (!DiscordLink.getInstance().getTwoFAManager().hasPendingSession(uuid)) {
                    event.reply("❌ No active 2FA session found.").setEphemeral(true).queue();
                    return;
                }

                String code = event.getOption("code").getAsString().trim();
                var result = DiscordLink.getInstance().getTwoFAManager().verify(uuid, code);

                switch (result) {
                    case SUCCESS -> {
                        Bukkit.getScheduler().runTask(DiscordLink.getInstance(), () -> {
                            Player player = Bukkit.getPlayer(uuid);
                            if (player != null) {
                                player.sendMessage(DiscordLink.getInstance().getConfigManager().getMessage("2fa.success"));
                            }
                        });
                        event.reply("✅ 2FA verified successfully! You may now play.").setEphemeral(true).queue();
                    }
                    case WRONG_CODE -> event.reply("❌ Incorrect code. Please try again.").setEphemeral(true).queue();
                    case EXPIRED -> event.reply("❌ Code has expired. Please rejoin to get a new one.").setEphemeral(true).queue();
                    case NO_SESSION -> event.reply("❌ No active 2FA session.").setEphemeral(true).queue();
                }
            } catch (SQLException e) {
                event.reply("⚠️ An error occurred.").setEphemeral(true).queue();
            }
        });
    }
}
