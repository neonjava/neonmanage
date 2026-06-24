package dev.neonjava.neoncoremanage.listeners;

import dev.neonjava.neoncoremanage.NeonCoreManage;
import dev.neonjava.neoncoremanage.database.DatabaseManager.BanEntry;
import dev.neonjava.neoncoremanage.database.DatabaseManager.PendingRank;
import dev.neonjava.neoncoremanage.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class JoinListener implements Listener {
    private final NeonCoreManage plugin;

    public JoinListener(NeonCoreManage plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        String ip = event.getAddress().getHostAddress();
        dev.neonjava.neoncoremanage.database.DatabaseManager.IpBanEntry ipBan = plugin.getDb().getIpBan(ip).join();
        if (ipBan != null) {
            if (ipBan.expiresAt() > 0 && ipBan.expiresAt() < System.currentTimeMillis()) {
                plugin.getDb().removeIpBan(ip).join();
            } else {
                String remaining = TimeUtils.formatRemaining(ipBan.expiresAt());
                Component kickMessage = MiniMessage.miniMessage().deserialize(
                        "<red><bold>YOUR IP IS BANNED FROM THE SERVER</bold></red>\n\n" +
                        "<gray>Reason: <white>" + ipBan.reason() + "\n" +
                        "<gray>Time left: <white>" + remaining + "\n\n" +
                        "<yellow>Appeal on - <underlined>discord.ninemc.fun</underlined></yellow>"
                );
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage);
                return;
            }
        }

        UUID uuid = event.getUniqueId();
        BanEntry ban = plugin.getDb().getBan(uuid).join();

        if (ban != null) {
            // If the ban has expired, remove it and allow the player in
            if (ban.expiresAt() > 0 && ban.expiresAt() < System.currentTimeMillis()) {
                plugin.getDb().removeBan(uuid).join(); // wait for removal before allowing
                return; // allow login
            }

            String remaining = TimeUtils.formatRemaining(ban.expiresAt());

            Component kickMessage = MiniMessage.miniMessage().deserialize(
                    "<red><bold>YOU ARE BANNED FROM THE SERVER</bold></red>\n\n" +
                    "<gray>Reason: <white>" + ban.reason() + "\n" +
                    "<gray>Time left: <white>" + remaining + "\n\n" +
                    "<yellow>Appeal on - <underlined>discord.ninemc.fun</underlined></yellow>"
            );

            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        
        if (player.getAddress() != null) {
            String ip = player.getAddress().getAddress().getHostAddress();
            plugin.getDb().savePlayerIp(player.getUniqueId(), playerName, ip);
        }

        // Send brand to client for F3 menu branding
        player.getScheduler().run(plugin, task -> {
            try {
                if (!Bukkit.getMessenger().getOutgoingChannels(plugin).contains("minecraft:brand")) {
                    Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "minecraft:brand");
                }
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                String brand = "neon-core";
                byte[] stringBytes = brand.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                int value = stringBytes.length;
                while ((value & 0xFFFFFF80) != 0L) {
                    baos.write((value & 0x7F) | 0x80);
                    value >>>= 7;
                }
                baos.write(value & 0x7F);
                baos.write(stringBytes);
                player.sendPluginMessage(plugin, "minecraft:brand", baos.toByteArray());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send server brand to player: " + e.getMessage());
            }
        }, null);

        plugin.getDb().getPendingRank(playerName).thenAccept(pending -> {
            if (pending != null) {
                String rankName = pending.rankName();
                String staffName = pending.staffName();

                org.bukkit.configuration.ConfigurationSection rankSection = plugin.getCmdsConfig().getConfigurationSection("ranks." + rankName);
                if (rankSection == null) {
                    plugin.getLogger().warning("Pending rank '" + rankName + "' for player " + playerName + " was not found in configs!");
                    plugin.getDb().removePendingRank(playerName);
                    return;
                }

                List<String> commands = rankSection.getStringList("commands");
                // Dispatch commands on the global region thread
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                    for (String cmd : commands) {
                        String finalCmd = cmd.replace("{player}", playerName);
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                    }
                    // Inform the player
                    player.sendMessage(MiniMessage.miniMessage().deserialize(
                            "<green>Your pending rank <yellow>" + rankName + "</yellow> has been applied!"
                    ));
                });

                // Send webhook & clean up pending list
                plugin.getWebhook().sendModerationLog("GIVERANK", playerName, staffName, "Rank: " + rankName + " (Claimed on join)", "N/A");
                plugin.getDb().removePendingRank(playerName);
            }
        });
    }
}
