package io.github.sxnsh1ness.discordlink;

import io.github.sxnsh1ness.discordlink.config.ConfigManager;
import io.github.sxnsh1ness.discordlink.database.Database;
import io.github.sxnsh1ness.discordlink.discord.DiscordBot;
import io.github.sxnsh1ness.discordlink.linking.LinkManager;
import io.github.sxnsh1ness.discordlink.minecraft.commands.DiscordCommand;
import io.github.sxnsh1ness.discordlink.minecraft.commands.DiscordLinkAdminCommand;
import io.github.sxnsh1ness.discordlink.minecraft.listeners.MinecraftChatListener;
import io.github.sxnsh1ness.discordlink.minecraft.listeners.PlayerEventListener;
import io.github.sxnsh1ness.discordlink.twofa.TwoFAManager;
import io.github.sxnsh1ness.discordlink.util.Logger;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class DiscordLink extends JavaPlugin {

    @Getter
    private static DiscordLink instance;
    @Getter
    private ConfigManager configManager;
    @Getter
    private Database database;
    @Getter
    private DiscordBot discordBot;
    @Getter
    private LinkManager linkManager;
    @Getter
    private TwoFAManager twoFAManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResource("messages.yml", false);

        this.configManager = new ConfigManager();
        this.database = new Database();

        if (!database.initialize()) {
            Logger.severe("Failed to initialize database! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.linkManager = new LinkManager();
        this.twoFAManager = new TwoFAManager();

        String token = configManager.getToken();
        if (token == null || token.equals("YOUR_BOT_TOKEN_HERE") || token.isEmpty()) {
            Logger.severe("Discord bot token is not configured! Please set it in config.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.discordBot = new DiscordBot();
        if (!discordBot.start()) {
            Logger.severe("Failed to start Discord bot! Check your token and internet connection.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new PlayerEventListener(), this);
        getServer().getPluginManager().registerEvents(new MinecraftChatListener(), this);

        Objects.requireNonNull(getCommand("discord")).setExecutor(new DiscordCommand());
        Objects.requireNonNull(getCommand("discordlink")).setExecutor(new DiscordLinkAdminCommand());

        Logger.info("&aDiscordLink enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (discordBot != null) {
            discordBot.shutdown();
        }
        if (database != null) {
            database.close();
        }
        Logger.info("&cDiscordLink disabled.");
    }

    public void reload() {
        reloadConfig();
        configManager.reload();
        if (discordBot != null) {
            discordBot.reload();
        }
    }
}
