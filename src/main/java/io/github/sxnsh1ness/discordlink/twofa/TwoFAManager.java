package io.github.sxnsh1ness.discordlink.twofa;

import io.github.sxnsh1ness.discordlink.DiscordLink;
import io.github.sxnsh1ness.discordlink.sessions.TwoFASession;
import io.github.sxnsh1ness.discordlink.util.Logger;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TwoFAManager {

    private final Map<UUID, TwoFASession> pendingSessions = new HashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    public void startSession(UUID playerUUID, String discordId, String discordTag) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        int expireSeconds = DiscordLink.getInstance().getConfigManager().get2FAExpireSeconds();
        long expiresAt = System.currentTimeMillis() + (expireSeconds * 1000L);

        pendingSessions.put(playerUUID, new TwoFASession(code, expiresAt));

        DiscordLink.getInstance().getDiscordBot().sendDM(discordId,
                DiscordLink.getInstance().getConfigManager().getMessage("discord.2fa-dm",
                        Map.of(
                                "player", Bukkit.getPlayer(playerUUID) != null
                                        ? Bukkit.getPlayer(playerUUID).getName() : "Unknown",
                                "code", code,
                                "seconds", String.valueOf(expireSeconds)
                        )));

        Bukkit.getScheduler().runTaskLater(DiscordLink.getInstance(), () -> {
            TwoFASession session = pendingSessions.get(playerUUID);
            if (session != null && !session.isVerified()) {
                pendingSessions.remove(playerUUID);
                Player player = Bukkit.getPlayer(playerUUID);
                if (player != null) {
                    player.kickPlayer(
                            DiscordLink.getInstance().getConfigManager().get2FAKickMessage().replace("&", "§")
                    );
                }
            }
        }, expireSeconds * 20L);

        Logger.debug("Started 2FA session for " + playerUUID);
    }

    public VerifyResult verify(UUID playerUUID, String inputCode) {
        TwoFASession session = pendingSessions.get(playerUUID);
        if (session == null) return VerifyResult.NO_SESSION;
        if (System.currentTimeMillis() > session.getExpiresAt()) {
            pendingSessions.remove(playerUUID);
            return VerifyResult.EXPIRED;
        }
        if (!session.getCode().equals(inputCode)) {
            return VerifyResult.WRONG_CODE;
        }
        session.setVerified(true);
        pendingSessions.remove(playerUUID);
        return VerifyResult.SUCCESS;
    }

    public boolean hasPendingSession(UUID playerUUID) {
        return pendingSessions.containsKey(playerUUID);
    }

    public void clearSession(UUID playerUUID) {
        pendingSessions.remove(playerUUID);
    }

    public enum VerifyResult {
        SUCCESS, NO_SESSION, EXPIRED, WRONG_CODE
    }
}
