package io.github.sxnsh1ness.discordlink.discord;

import io.github.sxnsh1ness.discordlink.DiscordLink;
import io.github.sxnsh1ness.discordlink.discord.listeners.DiscordChatListener;
import io.github.sxnsh1ness.discordlink.discord.listeners.DiscordCommandListener;
import io.github.sxnsh1ness.discordlink.discord.listeners.DiscordConsoleListener;
import io.github.sxnsh1ness.discordlink.util.Logger;
import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumSet;

public class DiscordBot {

    private final DiscordLink plugin;
    @Getter
    private JDA jda;
    private BukkitTask statusTask;

    public DiscordBot(DiscordLink plugin) {
        this.plugin = plugin;
    }

    public boolean start() {
        try {
            jda = JDABuilder.createDefault(plugin.getConfigManager().getToken(),
                            EnumSet.of(
                                    GatewayIntent.GUILD_MESSAGES,
                                    GatewayIntent.GUILD_MEMBERS,
                                    GatewayIntent.MESSAGE_CONTENT,
                                    GatewayIntent.DIRECT_MESSAGES
                            ))
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .disableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS)
                    .addEventListeners(
                            new DiscordChatListener(plugin),
                            new DiscordCommandListener(plugin),
                            new DiscordConsoleListener(plugin)
                    )
                    .setStatus(OnlineStatus.ONLINE)
                    .build()
                    .awaitReady();

            Logger.info("Discord bot logged in as: " + jda.getSelfUser().getAsTag());

            registerSlashCommands();
            startStatusTask();

            return true;
        } catch (Exception e) {
            Logger.severe("Failed to start Discord bot: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void registerSlashCommands() {
        Guild guild = getGuild();
        if (guild == null) return;

        guild.updateCommands().addCommands(
                Commands.slash("link", "Link your Discord account to Minecraft")
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING,
                                "code", "Your 6-character link code from in-game", true),
                Commands.slash("unlink", "Unlink your Discord account from Minecraft"),
                Commands.slash("verify", "Check your link status"),
                Commands.slash("2fa", "Submit your 2FA code")
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING,
                                "code", "Your 6-digit 2FA code", true)
        ).queue(
                s -> Logger.info("Registered " + s.size() + " slash commands in guild."),
                e -> Logger.warn("Failed to register slash commands: " + e.getMessage())
        );
    }

    private void startStatusTask() {
        if (!plugin.getConfigManager().isStatusEnabled()) return;
        int interval = plugin.getConfigManager().getStatusUpdateInterval() * 20;

        statusTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            String message = plugin.getConfigManager().getStatusMessage()
                    .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                    .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()));

            Activity activity = switch (plugin.getConfigManager().getStatusType().toUpperCase()) {
                case "PLAYING" -> Activity.playing(message);
                case "LISTENING" -> Activity.listening(message);
                case "COMPETING" -> Activity.competing(message);
                default -> Activity.watching(message);
            };
            jda.getPresence().setActivity(activity);
        }, 0L, interval);
    }

    public void reload() {
        if (statusTask != null) {
            statusTask.cancel();
        }
        startStatusTask();
    }

    public void shutdown() {
        if (statusTask != null) statusTask.cancel();
        if (jda != null) {
            jda.getPresence().setStatus(OnlineStatus.OFFLINE);
            jda.shutdown();
        }
    }

    public void sendMessage(String channelId, String message) {
        if (jda == null || channelId == null || channelId.isEmpty()) return;
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            Logger.debug("Channel not found: " + channelId);
            return;
        }
        channel.sendMessage(message).queue(
                s -> {},
                e -> Logger.debug("Failed to send message: " + e.getMessage())
        );
    }

    public void sendEmbed(String channelId, MessageEmbed embed) {
        if (jda == null || channelId == null || channelId.isEmpty()) return;
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) return;
        channel.sendMessageEmbeds(embed).queue(
                s -> {},
                e -> Logger.debug("Failed to send embed: " + e.getMessage())
        );
    }

    public void sendDM(String discordId, String message) {
        if (jda == null) return;
        jda.retrieveUserById(discordId).queue(user ->
                        user.openPrivateChannel().queue(channel ->
                                channel.sendMessage(message).queue(
                                        s -> {},
                                        e -> Logger.debug("Failed to send DM: " + e.getMessage())
                                )),
                e -> Logger.debug("Could not find user " + discordId + ": " + e.getMessage())
        );
    }

    public void sendWebhook(String username, String avatarUrl, String message) {
        String webhookUrl = plugin.getConfigManager().getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            sendMessage(plugin.getConfigManager().getChatChannelId(), "**" + username + "**: " + message);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.net.URI uri = java.net.URI.create(webhookUrl);
                java.net.HttpURLConnection con = (java.net.HttpURLConnection) uri.toURL().openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setDoOutput(true);

                String escapedMsg = message.replace("\\", "\\\\").replace("\"", "\\\"");
                String escapedUser = username.replace("\\", "\\\\").replace("\"", "\\\"");
                String body = String.format(
                        "{\"username\":\"%s\",\"avatar_url\":\"%s\",\"content\":\"%s\"}",
                        escapedUser, avatarUrl != null ? avatarUrl : "", escapedMsg
                );

                con.getOutputStream().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                con.getInputStream().close();
                con.disconnect();
            } catch (Exception e) {
                Logger.debug("Webhook error: " + e.getMessage());
            }
        });
    }

    public Guild getGuild() {
        if (jda == null) return null;
        String guildId = plugin.getConfigManager().getGuildId();
        if (guildId == null || guildId.isEmpty()) return null;
        return jda.getGuildById(guildId);
    }

    public boolean isReady() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }
}
