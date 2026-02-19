package io.github.sxnsh1ness.discordlink.discord.listeners;

import io.github.sxnsh1ness.discordlink.DiscordLink;
import io.github.sxnsh1ness.discordlink.util.Logger;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;

import java.util.List;

public class DiscordConsoleListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;
        if (!DiscordLink.getInstance().getConfigManager().isConsoleEnabled()) return;
        if (!DiscordLink.getInstance().getConfigManager().isConsoleCommandsAllowed()) return;

        String consoleChannelId = DiscordLink.getInstance().getConfigManager().getConsoleChannelId();
        if (consoleChannelId.isEmpty()) return;
        if (!event.getChannel().getId().equals(consoleChannelId)) return;
        if (event.getMember() == null) return;
        List<String> allowedRoles = DiscordLink.getInstance().getConfigManager().getConsoleCommandRoleIds();
        boolean hasPermission = event.getMember().getRoles().stream()
                .anyMatch(r -> allowedRoles.contains(r.getId()));

        if (!hasPermission) {
            event.getMessage().addReaction(Emoji.fromUnicode("❌")).queue();
            return;
        }

        String command = event.getMessage().getContentRaw().trim();
        if (command.startsWith("/")) command = command.substring(1);
        if (command.isEmpty()) return;

        final String finalCommand = command;
        Logger.info("[Discord Console] " + event.getAuthor().getAsTag() + " executed: " + finalCommand);

        Bukkit.getScheduler().runTask(DiscordLink.getInstance(), () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
        });

        event.getMessage().addReaction(Emoji.fromUnicode("✅")).queue();
    }
}
