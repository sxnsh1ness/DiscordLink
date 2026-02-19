package io.github.sxnsh1ness.discordlink.discord.listeners;

import io.github.sxnsh1ness.discordlink.DiscordLink;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;

public class DiscordChatListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;

        if (!DiscordLink.getInstance().getConfigManager().isChatEnabled()) return;
        if (!DiscordLink.getInstance().getConfigManager().isRelayToMinecraft()) return;

        String chatChannelId = DiscordLink.getInstance().getConfigManager().getChatChannelId();
        if (chatChannelId.isEmpty()) return;
        if (!event.getChannel().getId().equals(chatChannelId)) return;

        String username = event.getMember() != null
                ? event.getMember().getEffectiveName()
                : event.getAuthor().getName();
        String tag = event.getAuthor().getAsTag();
        String message = sanitizeMessage(event.getMessage());

        if (message.isEmpty()) return;

        String format = DiscordLink.getInstance().getConfigManager().getDiscordToMinecraftFormat()
                .replace("%username%", username)
                .replace("%tag%", tag)
                .replace("%message%", message)
                .replace("&", "§");
        Bukkit.getScheduler().runTask(DiscordLink.getInstance(), () -> {
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
