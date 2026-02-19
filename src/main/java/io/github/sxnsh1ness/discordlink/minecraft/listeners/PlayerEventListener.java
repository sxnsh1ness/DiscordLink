package io.github.sxnsh1ness.discordlink.minecraft.listeners;

import io.github.sxnsh1ness.discordlink.DiscordLink;
import net.kyori.adventure.text.Component;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerEventListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (DiscordLink.getInstance().getConfigManager().isLinkRequired()
                && !player.hasPermission("discordlink.bypass.link")
                && !DiscordLink.getInstance().getLinkManager().isLinked(player.getUniqueId())) {

            DiscordLink.getInstance().getServer().getScheduler().runTaskLater(DiscordLink.getInstance(), () -> {
                player.kickPlayer(DiscordLink.getInstance().getConfigManager().getLinkKickMessage().replace("&", "§"));
            }, 10L);
            return;
        }

        if (DiscordLink.getInstance().getConfigManager().is2FAEnabled()
                && !player.hasPermission("discordlink.bypass.2fa")
                && DiscordLink.getInstance().getLinkManager().isLinked(player.getUniqueId())) {

            String discordId = DiscordLink.getInstance().getLinkManager().getDiscordId(player.getUniqueId());
            String discordTag = DiscordLink.getInstance().getLinkManager().getDiscordTag(player.getUniqueId());
            if (discordId != null) {
                DiscordLink.getInstance().getTwoFAManager().startSession(player.getUniqueId(), discordId, discordTag);
                player.sendMessage(DiscordLink.getInstance().getConfigManager().getMessage("2fa.code-sent",
                        java.util.Map.of(
                                "tag", discordTag != null ? discordTag : "Discord",
                                "seconds", String.valueOf(DiscordLink.getInstance().getConfigManager().get2FAExpireSeconds())
                        )));
            }
        }

        if (!DiscordLink.getInstance().getConfigManager().isEventsEnabled()) return;
        String channelId = DiscordLink.getInstance().getConfigManager().getEventsChannelId();

        if (player.hasPlayedBefore()) {
            if (DiscordLink.getInstance().getConfigManager().isJoinEnabled()) {
                String msg = DiscordLink.getInstance().getConfigManager().getJoinMessage()
                        .replace("%username%", player.getName());
                DiscordLink.getInstance().getDiscordBot().sendMessage(channelId, msg);
            }
        } else {
            if (DiscordLink.getInstance().getConfigManager().isFirstJoinEnabled()) {
                String msg = DiscordLink.getInstance().getConfigManager().getFirstJoinMessage()
                        .replace("%username%", player.getName());
                DiscordLink.getInstance().getDiscordBot().sendMessage(channelId, msg);
            }
        }

        if (DiscordLink.getInstance().getConfigManager().isRoleSyncEnabled()
                && DiscordLink.getInstance().getLinkManager().isLinked(player.getUniqueId())) {
            String discordId = DiscordLink.getInstance().getLinkManager().getDiscordId(player.getUniqueId());
            if (discordId != null) {
                DiscordLink.getInstance().getServer().getScheduler().runTaskLaterAsynchronously(DiscordLink.getInstance(), () ->
                        DiscordLink.getInstance().getLinkManager().syncRoles(player.getUniqueId(), discordId), 40L);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!DiscordLink.getInstance().getConfigManager().isEventsEnabled()) return;
        if (!DiscordLink.getInstance().getConfigManager().isQuitEnabled()) return;

        String channelId = DiscordLink.getInstance().getConfigManager().getEventsChannelId();
        String msg = DiscordLink.getInstance().getConfigManager().getQuitMessage()
                .replace("%username%", event.getPlayer().getName());
        DiscordLink.getInstance().getDiscordBot().sendMessage(channelId, msg);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!DiscordLink.getInstance().getConfigManager().isEventsEnabled()) return;
        if (!DiscordLink.getInstance().getConfigManager().isDeathEnabled()) return;

        String deathMessage = event.deathMessage() != null
                ? stripColors(event.getDeathMessage())
                : event.getEntity().getName() + " died";

        String channelId = DiscordLink.getInstance().getConfigManager().getEventsChannelId();
        String msg = DiscordLink.getInstance().getConfigManager().getDeathMessage()
                .replace("%death_message%", escapeMarkdown(deathMessage))
                .replace("%username%", event.getEntity().getName());
        DiscordLink.getInstance().getDiscordBot().sendMessage(channelId, msg);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!DiscordLink.getInstance().getConfigManager().isEventsEnabled()) return;
        if (!DiscordLink.getInstance().getConfigManager().isAdvancementEnabled()) return;

        String key = event.getAdvancement().getKey().getKey();
        if (key.startsWith("recipes/")) return;

        String title = getAdvancementTitle(event.getAdvancement());
        if (title == null) return;

        String channelId = DiscordLink.getInstance().getConfigManager().getEventsChannelId();
        String msg = DiscordLink.getInstance().getConfigManager().getAdvancementMessage()
                .replace("%username%", event.getPlayer().getName())
                .replace("%advancement%", title);
        DiscordLink.getInstance().getDiscordBot().sendMessage(channelId, msg);
    }

    private String getAdvancementTitle(Advancement advancement) {
        try {
            var method = advancement.getClass().getMethod("getDisplay");
            var display = method.invoke(advancement);
            if (display == null) return null;
            var titleMethod = display.getClass().getMethod("getTitle");
            Object title = titleMethod.invoke(display);
            return title != null ? title.toString() : advancement.getKey().getKey();
        } catch (Exception e) {
            return advancement.getKey().getKey();
        }
    }

    private String stripColors(String text) {
        return text.replaceAll("§[0-9a-fklmnorA-FKLMNOR]", "");
    }

    private String escapeMarkdown(String text) {
        return text.replace("*", "\\*").replace("_", "\\_").replace("`", "\\`");
    }
}
