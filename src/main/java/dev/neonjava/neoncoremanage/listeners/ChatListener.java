package dev.neonjava.neoncoremanage.listeners;

import dev.neonjava.neoncoremanage.NeonCoreManage;
import dev.neonjava.neoncoremanage.database.DatabaseManager.MuteEntry;
import dev.neonjava.neoncoremanage.utils.StringSimilarity;
import dev.neonjava.neoncoremanage.utils.TimeUtils;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatListener implements Listener {
    private final NeonCoreManage plugin;
    private final Map<UUID, ChatRecord> lastChats = new ConcurrentHashMap<>();

    private record ChatRecord(String text, long timestamp) {}

    public ChatListener(NeonCoreManage plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        MuteEntry mute = plugin.getDb().getMute(uuid).join();
        if (mute != null) {
            if (mute.expiresAt() > 0 && mute.expiresAt() < System.currentTimeMillis()) {
                plugin.getDb().removeMute(uuid);
            } else {
                event.setCancelled(true);
                String remaining = TimeUtils.formatRemaining(mute.expiresAt());
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<red>You are currently muted!<br>" +
                        "<gray>Reason: <white>" + mute.reason() + "<br>" +
                        "<gray>Remaining: <white>" + remaining
                ));
                return;
            }
        }

        if (player.hasPermission("neoncore.chat.bypass")) {
            return;
        }

        String message = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
        
        if (containsBannedWord(message)) {
            event.setCancelled(true);
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>Your message has been blocked because it contains inappropriate language!"
            ));
            return;
        }

        long now = System.currentTimeMillis();
        ChatRecord lastChat = lastChats.get(uuid);
        
        double threshold = plugin.getBannedWordsConfig().getDouble("chat-filter.similarity-threshold", 70.0);
        long cooldownMs = plugin.getBannedWordsConfig().getLong("chat-filter.cooldown-seconds", 1L) * 1000L;
        long chatDelayMs = plugin.getBannedWordsConfig().getLong("chat-filter.chat-delay-seconds", 2L) * 1000L;

        if (lastChat != null) {
            long elapsed = now - lastChat.timestamp();
            
            if (elapsed < chatDelayMs) {
                event.setCancelled(true);
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<red>Please wait a moment before sending another message."
                ));
                return;
            }

            double similarity = StringSimilarity.getSimilarity(message, lastChat.text());
            if (similarity >= threshold && elapsed < cooldownMs) {
                event.setCancelled(true);
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<red>You cannot send similar messages so quickly!"
                ));
                return;
            }
        }

        lastChats.put(uuid, new ChatRecord(message, now));
    }

    private boolean containsBannedWord(String message) {
        String lowerMessage = message.toLowerCase(Locale.ROOT);
        org.bukkit.configuration.ConfigurationSection section = plugin.getBannedWordsConfig().getConfigurationSection("banned-words");
        if (section == null) return false;

        for (String lang : section.getKeys(false)) {
            List<String> words = section.getStringList(lang);
            for (String word : words) {
                String lowerWord = word.toLowerCase(Locale.ROOT);
                if (lowerMessage.contains(lowerWord)) {
                    return true;
                }
            }
        }
        return false;
    }
}
