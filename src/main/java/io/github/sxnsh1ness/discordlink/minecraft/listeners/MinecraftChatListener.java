package io.github.sxnsh1ness.discordlink.minecraft.listeners;

import io.github.sxnsh1ness.discordlink.DiscordLink;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class MinecraftChatListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!DiscordLink.getInstance().getConfigManager().isChatEnabled()) return;
        if (!DiscordLink.getInstance().getConfigManager().isRelayToDiscord()) return;

        String chatChannelId = DiscordLink.getInstance().getConfigManager().getChatChannelId();
        if (chatChannelId.isEmpty()) return;

        String player = event.getPlayer().getName();
        String message = stripColors(event.getMessage());

        String webhookUrl = DiscordLink.getInstance().getConfigManager().getWebhookUrl();
        if (webhookUrl != null && !webhookUrl.isEmpty()) {
            String avatarUrl = "https://cravatar.eu/helmavatar/" + player + "/64.png";
            DiscordLink.getInstance().getDiscordBot().sendWebhook(player, avatarUrl, message);
        } else {
            String format = DiscordLink.getInstance().getConfigManager().getMinecraftToDiscordFormat()
                    .replace("%username%", escapeMarkdown(player))
                    .replace("%message%", escapeMarkdown(message))
                    .replace("%world%", event.getPlayer().getWorld().getName());
            DiscordLink.getInstance().getDiscordBot().sendMessage(chatChannelId, format);
        }
    }

    private String stripColors(String text) {
        return text.replaceAll("§[0-9a-fklmnorA-FKLMNOR]", "");
    }

    private String escapeMarkdown(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("~", "\\~")
                .replace("`", "\\`")
                .replace(">", "\\>");
    }
}
