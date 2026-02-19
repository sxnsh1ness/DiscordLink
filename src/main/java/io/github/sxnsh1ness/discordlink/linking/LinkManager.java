package io.github.sxnsh1ness.discordlink.linking;

import io.github.sxnsh1ness.discordlink.DiscordLink;
import io.github.sxnsh1ness.discordlink.database.Database;
import io.github.sxnsh1ness.discordlink.util.Logger;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

public class LinkManager {

    private final DiscordLink plugin;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public LinkManager(DiscordLink plugin) {
        this.plugin = plugin;
    }

    public String generateLinkCode(UUID playerUUID, String playerName) throws SQLException {
        String code;
        int attempts = 0;
        do {
            code = generateCode();
            attempts++;
            if (attempts > 100) throw new SQLException("Could not generate unique code");
        } while (plugin.getDatabase().getCode(code) != null);

        long expireAt = System.currentTimeMillis() + (60_000L * 10);
        plugin.getDatabase().saveCode(code, playerUUID, playerName, expireAt);
        return code;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    public LinkResult completeLink(String code, String discordId, String discordTag) {
        try {
            if (plugin.getDatabase().isDiscordLinked(discordId)) {
                return LinkResult.DISCORD_ALREADY_LINKED;
            }

            Database.CodeData data = plugin.getDatabase().getCode(code);
            if (data == null) {
                return LinkResult.INVALID_CODE;
            }

            if (plugin.getDatabase().isLinked(data.uuid)) {
                plugin.getDatabase().deleteCode(code);
                return LinkResult.MINECRAFT_ALREADY_LINKED;
            }

            plugin.getDatabase().saveLink(data.uuid, discordId, discordTag);
            plugin.getDatabase().deleteCode(code);

            Bukkit.getScheduler().runTask(plugin, () -> {
                applyLinkedRole(discordId);
                applyNickname(discordId, data.username);
            });

            Player player = Bukkit.getPlayer(data.uuid);
            if (player != null) {
                player.sendMessage(plugin.getConfigManager().getMessage("link.success",
                        java.util.Map.of("tag", discordTag)));
            }

            if (plugin.getConfigManager().isRoleSyncEnabled()) {
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                        syncRoles(data.uuid, discordId), 20L);
            }

            Logger.info("Linked " + data.username + " (" + data.uuid + ") to Discord " + discordTag);
            return LinkResult.SUCCESS;

        } catch (SQLException e) {
            Logger.severe("Database error during link: " + e.getMessage());
            return LinkResult.ERROR;
        }
    }

    public boolean unlink(UUID uuid) {
        try {
            String discordId = plugin.getDatabase().getDiscordId(uuid);
            if (discordId == null) return false;

            plugin.getDatabase().removeLink(uuid);

            Bukkit.getScheduler().runTask(plugin, () -> {
                removeLinkedRole(discordId);
                removeNickname(discordId);
            });

            return true;
        } catch (SQLException e) {
            Logger.severe("Database error during unlink: " + e.getMessage());
            return false;
        }
    }

    public boolean unlinkByDiscordId(String discordId) {
        try {
            plugin.getDatabase().removeLinkByDiscordId(discordId);
            removeLinkedRole(discordId);
            return true;
        } catch (SQLException e) {
            Logger.severe("Database error during unlink: " + e.getMessage());
            return false;
        }
    }

    public void applyLinkedRole(String discordId) {
        String roleId = plugin.getConfigManager().getLinkedRoleId();
        if (roleId == null || roleId.isEmpty()) return;

        Guild guild = plugin.getDiscordBot().getGuild();
        if (guild == null) return;

        guild.retrieveMemberById(discordId).queue(member -> {
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                guild.addRoleToMember(member, role).queue(
                        s -> Logger.debug("Applied linked role to " + member.getEffectiveName()),
                        e -> Logger.warn("Failed to apply linked role: " + e.getMessage())
                );
            }
        }, e -> Logger.warn("Could not find Discord member " + discordId));
    }

    public void removeLinkedRole(String discordId) {
        String roleId = plugin.getConfigManager().getLinkedRoleId();
        if (roleId == null || roleId.isEmpty()) return;

        Guild guild = plugin.getDiscordBot().getGuild();
        if (guild == null) return;

        guild.retrieveMemberById(discordId).queue(member -> {
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                guild.removeRoleFromMember(member, role).queue();
            }
        }, e -> {});
    }

    public void applyNickname(String discordId, String minecraftName) {
        if (!plugin.getConfigManager().isSyncNickname()) return;

        Guild guild = plugin.getDiscordBot().getGuild();
        if (guild == null) return;

        guild.retrieveMemberById(discordId).queue(member -> {
            if (guild.getOwner() != null && guild.getOwner().getId().equals(discordId)) return;
            member.modifyNickname(minecraftName).queue(
                    s -> {},
                    e -> Logger.debug("Failed to set nickname for " + discordId + ": " + e.getMessage())
            );
        }, e -> {});
    }

    /** Remove the Discord nickname */
    public void removeNickname(String discordId) {
        if (!plugin.getConfigManager().isSyncNickname()) return;

        Guild guild = plugin.getDiscordBot().getGuild();
        if (guild == null) return;

        guild.retrieveMemberById(discordId).queue(member -> {
            if (guild.getOwner() != null && guild.getOwner().getId().equals(discordId)) return;
            member.modifyNickname(null).queue(s -> {}, e -> {});
        }, e -> {});
    }

    public void syncRoles(UUID playerUUID, String discordId) {
        if (!plugin.getConfigManager().isRoleSyncEnabled()) return;

        try {
            Guild guild = plugin.getDiscordBot().getGuild();
            if (guild == null) return;

            if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) return;

            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(playerUUID);
            if (user == null) return;

            String primaryGroup = user.getPrimaryGroup();
            Map<String, String> mappings = plugin.getConfigManager().getRoleSyncMappings();

            guild.retrieveMemberById(discordId).queue(member -> {
                for (String roleId : mappings.values()) {
                    Role role = guild.getRoleById(roleId);
                    if (role != null && member.getRoles().contains(role)) {
                        guild.removeRoleFromMember(member, role).queue();
                    }
                }
                String targetRoleId = mappings.get(primaryGroup);
                if (targetRoleId != null) {
                    Role role = guild.getRoleById(targetRoleId);
                    if (role != null) {
                        guild.addRoleToMember(member, role).queue(
                                s -> Logger.debug("Synced role " + primaryGroup + " for " + discordId),
                                e -> Logger.warn("Failed to sync role: " + e.getMessage())
                        );
                    }
                }
            }, e -> Logger.warn("Could not find Discord member for role sync: " + discordId));

        } catch (Exception e) {
            Logger.warn("Error during role sync: " + e.getMessage());
        }
    }

    public boolean isLinked(UUID uuid) {
        try {
            return plugin.getDatabase().isLinked(uuid);
        } catch (SQLException e) {
            Logger.severe("DB error checking link status: " + e.getMessage());
            return false;
        }
    }

    public String getDiscordId(UUID uuid) {
        try {
            return plugin.getDatabase().getDiscordId(uuid);
        } catch (SQLException e) {
            return null;
        }
    }

    public String getDiscordTag(UUID uuid) {
        try {
            return plugin.getDatabase().getDiscordTag(uuid);
        } catch (SQLException e) {
            return null;
        }
    }

    public enum LinkResult {
        SUCCESS,
        INVALID_CODE,
        DISCORD_ALREADY_LINKED,
        MINECRAFT_ALREADY_LINKED,
        ERROR
    }
}
