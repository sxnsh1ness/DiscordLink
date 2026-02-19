package io.github.sxnsh1ness.discordlink.minecraft.listeners;

import io.github.sxnsh1ness.discordlink.DiscordLink;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerEventListener implements Listener {

    private final DiscordLink plugin;

    public PlayerEventListener(DiscordLink plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (plugin.getConfigManager().isLinkRequired()
                && !player.hasPermission("discordlink.bypass.link")
                && !plugin.getLinkManager().isLinked(player.getUniqueId())) {

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.kickPlayer(plugin.getConfigManager().getLinkKickMessage().replace("&", "§"));
            }, 10L);
            return;
        }

        if (plugin.getConfigManager().is2FAEnabled()
                && !player.hasPermission("discordlink.bypass.2fa")
                && plugin.getLinkManager().isLinked(player.getUniqueId())) {

            String discordId = plugin.getLinkManager().getDiscordId(player.getUniqueId());
            String discordTag = plugin.getLinkManager().getDiscordTag(player.getUniqueId());
            if (discordId != null) {
                plugin.getTwoFAManager().startSession(player.getUniqueId(), discordId, discordTag);
                player.sendMessage(plugin.getConfigManager().getMessage("2fa.code-sent",
                        java.util.Map.of(
                                "tag", discordTag != null ? discordTag : "Discord",
                                "seconds", String.valueOf(plugin.getConfigManager().get2FAExpireSeconds())
                        )));
            }
        }

        if (!plugin.getConfigManager().isEventsEnabled()) return;
        String channelId = plugin.getConfigManager().getEventsChannelId();

        if (player.hasPlayedBefore()) {
            if (plugin.getConfigManager().isJoinEnabled()) {
                String msg = plugin.getConfigManager().getJoinMessage()
                        .replace("%username%", player.getName());
                plugin.getDiscordBot().sendMessage(channelId, msg);
            }
        } else {
            if (plugin.getConfigManager().isFirstJoinEnabled()) {
                String msg = plugin.getConfigManager().getFirstJoinMessage()
                        .replace("%username%", player.getName());
                plugin.getDiscordBot().sendMessage(channelId, msg);
            }
        }

        if (plugin.getConfigManager().isRoleSyncEnabled()
                && plugin.getLinkManager().isLinked(player.getUniqueId())) {
            String discordId = plugin.getLinkManager().getDiscordId(player.getUniqueId());
            if (discordId != null) {
                plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () ->
                        plugin.getLinkManager().syncRoles(player.getUniqueId(), discordId), 40L);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.getConfigManager().isEventsEnabled()) return;
        if (!plugin.getConfigManager().isQuitEnabled()) return;

        String channelId = plugin.getConfigManager().getEventsChannelId();
        String msg = plugin.getConfigManager().getQuitMessage()
                .replace("%username%", event.getPlayer().getName());
        plugin.getDiscordBot().sendMessage(channelId, msg);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfigManager().isEventsEnabled()) return;
        if (!plugin.getConfigManager().isDeathEnabled()) return;

        String deathMessage = event.deathMessage() != null
                ? stripColors(event.getDeathMessage())
                : event.getEntity().getName() + " died";

        String channelId = plugin.getConfigManager().getEventsChannelId();
        String msg = plugin.getConfigManager().getDeathMessage()
                .replace("%death_message%", escapeMarkdown(deathMessage))
                .replace("%username%", event.getEntity().getName());
        plugin.getDiscordBot().sendMessage(channelId, msg);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!plugin.getConfigManager().isEventsEnabled()) return;
        if (!plugin.getConfigManager().isAdvancementEnabled()) return;

        String key = event.getAdvancement().getKey().getKey();
        if (key.startsWith("recipes/")) return;

        String title = getAdvancementTitle(event.getAdvancement());
        if (title == null) return;

        String channelId = plugin.getConfigManager().getEventsChannelId();
        String msg = plugin.getConfigManager().getAdvancementMessage()
                .replace("%username%", event.getPlayer().getName())
                .replace("%advancement%", title);
        plugin.getDiscordBot().sendMessage(channelId, msg);
    }

    private String getAdvancementTitle(org.bukkit.advancement.Advancement advancement) {
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
