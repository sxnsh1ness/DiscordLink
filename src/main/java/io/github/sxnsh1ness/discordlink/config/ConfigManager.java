package io.github.sxnsh1ness.discordlink.config;

import io.github.sxnsh1ness.discordlink.DiscordLink;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private FileConfiguration config;
    private FileConfiguration messages;

    public ConfigManager() {
        reload();
    }

    public void reload() {
        DiscordLink.getInstance().reloadConfig();
        this.config = DiscordLink.getInstance().getConfig();

        File messagesFile = new File(DiscordLink.getInstance().getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public String getToken() { return config.getString("discord.token"); }
    public String getGuildId() { return config.getString("discord.guild-id"); }

    public boolean isLinkRequired() { return config.getBoolean("link.required", false); }
    public boolean isUnlinkAllowed() { return config.getBoolean("link.allow-unlink", true); }
    public String getLinkedRoleId() { return config.getString("link.linked-role-id", ""); }
    public boolean isSyncNickname() { return config.getBoolean("link.sync-nickname", true); }
    public String getLinkKickMessage() { return config.getString("link.kick-message", "&cYou must link your Discord account!"); }

    public boolean is2FAEnabled() { return config.getBoolean("2fa.enabled", false); }
    public int get2FAExpireSeconds() { return config.getInt("2fa.code-expire-seconds", 120); }
    public String get2FAKickMessage() { return config.getString("2fa.kick-message", "&c2FA code expired."); }

    public boolean isChatEnabled() { return config.getBoolean("chat.enabled", true); }
    public String getChatChannelId() { return config.getString("chat.channel-id", ""); }
    public String getWebhookUrl() { return config.getString("chat.webhook-url", ""); }
    public String getDiscordToMinecraftFormat() { return config.getString("chat.discord-to-minecraft"); }
    public String getMinecraftToDiscordFormat() { return config.getString("chat.minecraft-to-discord"); }
    public boolean isRelayToDiscord() { return config.getBoolean("chat.relay-to-discord", true); }
    public boolean isRelayToMinecraft() { return config.getBoolean("chat.relay-to-minecraft", true); }

    public boolean isEventsEnabled() { return config.getBoolean("events.enabled", true); }
    public String getEventsChannelId() { return config.getString("events.channel-id", ""); }
    public boolean isJoinEnabled() { return config.getBoolean("events.join.enabled", true); }
    public String getJoinMessage() { return config.getString("events.join.message"); }
    public boolean isQuitEnabled() { return config.getBoolean("events.quit.enabled", true); }
    public String getQuitMessage() { return config.getString("events.quit.message"); }
    public boolean isDeathEnabled() { return config.getBoolean("events.death.enabled", true); }
    public String getDeathMessage() { return config.getString("events.death.message"); }
    public boolean isFirstJoinEnabled() { return config.getBoolean("events.first-join.enabled", true); }
    public String getFirstJoinMessage() { return config.getString("events.first-join.message"); }
    public boolean isAdvancementEnabled() { return config.getBoolean("events.advancement.enabled", true); }
    public String getAdvancementMessage() { return config.getString("events.advancement.message"); }

    public boolean isConsoleEnabled() { return config.getBoolean("console.enabled", false); }
    public String getConsoleChannelId() { return config.getString("console.channel-id", ""); }
    public boolean isConsoleCommandsAllowed() { return config.getBoolean("console.allow-commands", false); }
    public List<String> getConsoleCommandRoleIds() { return config.getStringList("console.command-role-ids"); }

    public boolean isStatusEnabled() { return config.getBoolean("status.enabled", true); }
    public int getStatusUpdateInterval() { return config.getInt("status.update-interval", 30); }
    public String getStatusType() { return config.getString("status.type", "WATCHING"); }
    public String getStatusMessage() { return config.getString("status.message", "%online%/%max% players"); }

    public boolean isRoleSyncEnabled() { return config.getBoolean("role-sync.enabled", false); }
    public Map<String, String> getRoleSyncMappings() {
        Map<String, String> map = new HashMap<>();
        if (config.isConfigurationSection("role-sync.mappings")) {
            for (String key : config.getConfigurationSection("role-sync.mappings").getKeys(false)) {
                map.put(key, config.getString("role-sync.mappings." + key));
            }
        }
        return map;
    }

    public boolean isBanSyncMcToDiscord() { return config.getBoolean("ban-sync.minecraft-to-discord", false); }
    public boolean isBanSyncDiscordToMc() { return config.getBoolean("ban-sync.discord-to-minecraft", false); }

    public String getPrefix() { return color(config.getString("prefix", "&8[&9Discord&8] &r")); }
    public boolean isDebug() { return config.getBoolean("debug", false); }

    public String getMessage(String path) {
        String msg = messages.getString(path, "&cMissing message: " + path);
        return color(msg);
    }

    public String getMessage(String path, Map<String, String> replacements) {
        String msg = getMessage(path);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return msg;
    }

    private String color(String text) {
        if (text == null) return "";
        return text.replace("&", "§");
    }
}
