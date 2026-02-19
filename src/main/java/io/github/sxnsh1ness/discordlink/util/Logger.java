package io.github.sxnsh1ness.discordlink.util;

import io.github.sxnsh1ness.discordlink.DiscordLink;
import org.bukkit.Bukkit;

public class Logger {

    private static final String PREFIX = "[DiscordLink] ";

    public static void info(String message) {
        Bukkit.getConsoleSender().sendMessage("§9" + PREFIX + "§r" + color(message));
    }

    public static void warn(String message) {
        Bukkit.getConsoleSender().sendMessage("§e" + PREFIX + "§e" + message);
    }

    public static void severe(String message) {
        Bukkit.getConsoleSender().sendMessage("§c" + PREFIX + "§c" + message);
    }

    public static void debug(String message) {
        DiscordLink plugin = DiscordLink.getInstance();
        if (plugin != null && plugin.getConfigManager() != null && plugin.getConfigManager().isDebug()) {
            Bukkit.getConsoleSender().sendMessage("§8" + PREFIX + "[DEBUG] " + message);
        }
    }

    private static String color(String text) {
        return text.replace("&", "§");
    }
}
