package io.github.sxnsh1ness.discordlink.discord.listeners;

import io.github.sxnsh1ness.discordlink.DiscordLink;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;

public class DiscordChatListener extends ListenerAdapter {

    private final DiscordLink plugin;

    public DiscordChatListener(DiscordLink plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;

        if (!plugin.getConfigManager().isChatEnabled()) return;
        if (!plugin.getConfigManager().isRelayToMinecraft()) return;

        String chatChannelId = plugin.getConfigManager().getChatChannelId();
        if (chatChannelId.isEmpty()) return;
        if (!event.getChannel().getId().equals(chatChannelId)) return;

        String username = event.getMember() != null
                ? event.getMember().getEffectiveName()
                : event.getAuthor().getName();
        String tag = event.getAuthor().getAsTag();
        String message = sanitizeMessage(event.getMessage());

        if (message.isEmpty()) return;

        String format = plugin.getConfigManager().getDiscordToMinecraftFormat()
                .replace("%username%", username)
                .replace("%tag%", tag)
                .replace("%message%", message)
                .replace("&", "§");
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(format);
            }
        });
    }

    private String sanitizeMessage(Message message) {
        String content = message.getContentDisplay();
        content = content.replace("§", "");

        if (content.length() > 256) {
            content = content.substring(0, 253) + "...";
        }

        return content.trim();
    }
}
