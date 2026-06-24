package dev.neonjava.neoncoremanage.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.neonjava.neoncoremanage.NeonCoreManage;
import dev.neonjava.neoncoremanage.database.DatabaseManager;
import dev.neonjava.neoncoremanage.database.DatabaseManager.BanEntry;
import dev.neonjava.neoncoremanage.database.DatabaseManager.MuteEntry;
import dev.neonjava.neoncoremanage.utils.TimeUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public class CoreCommands {
    private final NeonCoreManage plugin;

    public CoreCommands(NeonCoreManage plugin) {
        this.plugin = plugin;
    }

    public void registerAll(Commands registrar) {
        registrar.register(buildGiverank(), "Gives a rank to a player", Collections.emptyList());
        registrar.register(buildCancelrank(), "Cancels a pending rank for a player", Collections.emptyList());
        registrar.register(buildNeonmanage(), "Plugin management commands", Collections.emptyList());
        
        if (plugin.getConfig().getBoolean("modules.moderation", true)) {
            registrar.register(buildBan(), "Bans a player permanently", Collections.emptyList());
            registrar.register(buildTempban(), "Bans a player temporarily", Collections.emptyList());
            registrar.register(buildUnban(), "Unbans a player", Collections.emptyList());
            registrar.register(buildMute(), "Mutes a player permanently", Collections.emptyList());
            registrar.register(buildTempmute(), "Mutes a player temporarily", Collections.emptyList());
            registrar.register(buildUnmute(), "Unmutes a player", Collections.emptyList());
            registrar.register(buildBanip(), "IP bans a player or IP address", Collections.emptyList());
            registrar.register(buildUnbanip(), "Unbans an IP address", Collections.emptyList());
        }

        if (plugin.getConfig().getBoolean("modules.god", true)) {
            registrar.register(buildGod(), "Toggles god mode for a player", Collections.emptyList());
        }

        if (plugin.getConfig().getBoolean("modules.time", true)) {
            registrar.register(buildDay(), "Sets world time to day", Collections.emptyList());
            registrar.register(buildNight(), "Sets world time to night", Collections.emptyList());
            registrar.register(buildNoon(), "Sets world time to noon", Collections.emptyList());
        }

        if (plugin.getConfig().getBoolean("modules.heal", true)) {
            registrar.register(buildHeal(), "Heals a player", Collections.emptyList());
        }

        if (plugin.getConfig().getBoolean("modules.teleport", true)) {
            registrar.register(buildTpa(), "Requests to teleport to a player", Collections.emptyList());
            registrar.register(buildTpahere(), "Requests a player to teleport to you", Collections.emptyList());
            registrar.register(buildTpaccept(), "Accepts a teleport request", Collections.emptyList());
            registrar.register(buildTpdeny(), "Denies a teleport request", Collections.emptyList());
            registrar.register(buildTpatoggle(), "Toggles teleport requests", Collections.emptyList());
        }

        if (plugin.getConfig().getBoolean("modules.broadcast", true)) {
            registrar.register(buildBroadcast(), "Broadcasts a message to the server", Collections.emptyList());
        }

        if (plugin.getConfig().getBoolean("modules.ignore", true)) {
            registrar.register(buildIgnore(), "Toggles ignoring of a player", Collections.emptyList());
        }

        if (plugin.getConfig().getBoolean("modules.inventory", true)) {
            registrar.register(buildClearInventory(), "Clears a player's inventory", Collections.emptyList());
            registrar.register(buildInvsee(), "View another player's inventory", Collections.emptyList());
        }

        if (plugin.getConfig().getBoolean("modules.back", true)) {
            registrar.register(buildBack(), "Teleport to your last death location", Collections.emptyList());
        }

        if (plugin.getConfig().getBoolean("modules.msg", true)) {
            registrar.register(buildMsg(), "Send a private message to a player", Collections.emptyList());
        }
    }

    private LiteralCommandNode<CommandSourceStack> buildGiverank() {
        return Commands.literal("giverank")
                .requires(stack -> stack.getSender().hasPermission("neoncore.giverank"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                builder.suggest(p.getName());
                            }
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("rank", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    Set<String> ranks = plugin.getCmdsConfig().getConfigurationSection("ranks").getKeys(false);
                                    for (String rank : ranks) {
                                        builder.suggest(rank);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String playerName = StringArgumentType.getString(ctx, "player");
                                    String rankName = StringArgumentType.getString(ctx, "rank");
                                    
                                    org.bukkit.configuration.ConfigurationSection rankSection = plugin.getCmdsConfig().getConfigurationSection("ranks." + rankName);
                                    if (rankSection == null) {
                                        ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>Rank not found in config!"));
                                        return 0;
                                    }

                                    String staffName = ctx.getSource().getSender().getName();
                                    Player targetPlayer = Bukkit.getPlayerExact(playerName);

                                    if (targetPlayer != null && targetPlayer.isOnline()) {
                                        // Execute immediately since player is online
                                        List<String> commands = rankSection.getStringList("commands");
                                        CompletableFuture.runAsync(() -> {
                                            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                                                for (String cmd : commands) {
                                                    String finalCmd = cmd.replace("{player}", targetPlayer.getName());
                                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                                                }
                                            });
                                            plugin.getWebhook().sendModerationLog("GIVERANK", targetPlayer.getName(), staffName, "Rank: " + rankName + " (Applied immediately)", "N/A");
                                        });

                                        ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                                                "<green>Successfully applied rank <yellow>" + rankName + "</yellow> to <yellow>" + targetPlayer.getName() + "</yellow>."
                                        ));
                                    } else {
                                        // Queue for when player logs in
                                        plugin.getDb().addPendingRank(playerName, rankName, staffName);
                                        ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                                                "<green>Player <yellow>" + playerName + "</yellow> is offline. Rank <yellow>" + rankName + "</yellow> has been queued and will be applied when they join."
                                        ));
                                    }
                                    return 1;
                                })))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildBan() {
        return Commands.literal("ban")
                .requires(stack -> stack.getSender().hasPermission("neoncore.ban"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                builder.suggest(p.getName());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeBan(ctx, -1))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> executeBan(ctx, -1))))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildTempban() {
        return Commands.literal("tempban")
                .requires(stack -> stack.getSender().hasPermission("neoncore.ban"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                builder.suggest(p.getName());
                            }
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("duration", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("30d");
                                    builder.suggest("7d");
                                    builder.suggest("1d");
                                    builder.suggest("12h");
                                    builder.suggest("1h");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> executeBan(ctx, 1))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> executeBan(ctx, 2)))))
                .build();
    }

    private int executeBan(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int mode) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String reason = "Banned by administrator";
        long durationMs = -1;

        if (mode == 1 || mode == 2) {
            String durStr = StringArgumentType.getString(ctx, "duration");
            durationMs = TimeUtils.parseDuration(durStr);
            if (durationMs <= 0) {
                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>Invalid duration format!"));
                return 0;
            }
        }

        try {
            reason = StringArgumentType.getString(ctx, "reason");
        } catch (IllegalArgumentException e) {
            // Keep default reason
        }

        long expiresAt = durationMs > 0 ? System.currentTimeMillis() + durationMs : -1;
        String finalReason = reason;
        long finalExpiresAt = expiresAt;
        String durFormatted = durationMs > 0 ? TimeUtils.formatRemaining(expiresAt) : "Permanent";

        CompletableFuture.runAsync(() -> {
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            UUID uuid = target.getUniqueId();
            String staff = ctx.getSource().getSender().getName();

            plugin.getDb().addBan(new BanEntry(uuid.toString(), target.getName(), staff, finalReason, finalExpiresAt)).join();

            Player online = target.getPlayer();
            if (online != null) {
                online.getScheduler().run(plugin, task -> online.kick(MiniMessage.miniMessage().deserialize(
                        "<red><bold>YOU ARE BANNED FROM THE SERVER</bold></red>\n\n" +
                        "<gray>Reason: <white>" + finalReason + "\n" +
                        "<gray>Time left: <white>" + durFormatted + "\n\n" +
                        "<yellow>Appeal on - <underlined>discord.ninemc.fun</underlined></yellow>"
                )), null);
            }

            plugin.getWebhook().sendModerationLog("BAN", target.getName(), staff, finalReason, durFormatted);
            ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                    "<green>Successfully banned <yellow>" + target.getName() + "</yellow> for: <gray>" + finalReason
            ));
        });

        return 1;
    }

    private LiteralCommandNode<CommandSourceStack> buildUnban() {
        return Commands.literal("unban")
                .requires(stack -> stack.getSender().hasPermission("neoncore.ban"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .executes(ctx -> {
                            String playerName = StringArgumentType.getString(ctx, "player");
                            CompletableFuture.runAsync(() -> {
                                @SuppressWarnings("deprecation")
                                OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
                                if (!target.hasPlayedBefore() && !target.isOnline()) {
                                    ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                                            "<red>Player <yellow>" + playerName + "</yellow> has never joined the server!"
                                    ));
                                    return;
                                }
                                boolean removed = plugin.getDb().removeBan(target.getUniqueId()).join();
                                if (!removed) {
                                    ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                                            "<red>Player <yellow>" + target.getName() + "</yellow> is not banned!"
                                    ));
                                    return;
                                }
                                String staff = ctx.getSource().getSender().getName();
                                plugin.getWebhook().sendModerationLog("UNBAN", target.getName(), staff, "Unbanned", "N/A");
                                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                                        "<green>Successfully unbanned <yellow>" + target.getName() + "</yellow>."
                                ));
                            });
                            return 1;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildMute() {
        return Commands.literal("mute")
                .requires(stack -> stack.getSender().hasPermission("neoncore.mute"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                builder.suggest(p.getName());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeMute(ctx, -1))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> executeMute(ctx, -1))))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildTempmute() {
        return Commands.literal("tempmute")
                .requires(stack -> stack.getSender().hasPermission("neoncore.mute"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                builder.suggest(p.getName());
                            }
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("duration", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("30d");
                                    builder.suggest("7d");
                                    builder.suggest("1d");
                                    builder.suggest("12h");
                                    builder.suggest("1h");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> executeMute(ctx, 1))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> executeMute(ctx, 2)))))
                .build();
    }

    private int executeMute(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int mode) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String reason = "Muted by administrator";
        long durationMs = -1;

        if (mode == 1 || mode == 2) {
            String durStr = StringArgumentType.getString(ctx, "duration");
            durationMs = TimeUtils.parseDuration(durStr);
            if (durationMs <= 0) {
                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>Invalid duration format!"));
                return 0;
            }
        }

        try {
            reason = StringArgumentType.getString(ctx, "reason");
        } catch (IllegalArgumentException e) {
            // Keep default reason
        }

        long expiresAt = durationMs > 0 ? System.currentTimeMillis() + durationMs : -1;
        String finalReason = reason;
        long finalExpiresAt = expiresAt;
        String durFormatted = durationMs > 0 ? TimeUtils.formatRemaining(expiresAt) : "Permanent";

        CompletableFuture.runAsync(() -> {
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            UUID uuid = target.getUniqueId();
            String staff = ctx.getSource().getSender().getName();

            plugin.getDb().addMute(new MuteEntry(uuid.toString(), target.getName(), staff, finalReason, finalExpiresAt)).join();

            plugin.getWebhook().sendModerationLog("MUTE", target.getName(), staff, finalReason, durFormatted);
            ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                    "<green>Successfully muted <yellow>" + target.getName() + "</yellow> for: <gray>" + finalReason
            ));
        });

        return 1;
    }

    private LiteralCommandNode<CommandSourceStack> buildUnmute() {
        return Commands.literal("unmute")
                .requires(stack -> stack.getSender().hasPermission("neoncore.mute"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .executes(ctx -> {
                            String playerName = StringArgumentType.getString(ctx, "player");
                            CompletableFuture.runAsync(() -> {
                                @SuppressWarnings("deprecation")
                                OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
                                if (!target.hasPlayedBefore() && !target.isOnline()) {
                                    ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                                            "<red>Player <yellow>" + playerName + "</yellow> has never joined the server!"
                                    ));
                                    return;
                                }
                                boolean removed = plugin.getDb().removeMute(target.getUniqueId()).join();
                                if (!removed) {
                                    ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                                            "<red>Player <yellow>" + target.getName() + "</yellow> is not muted!"
                                    ));
                                    return;
                                }
                                String staff = ctx.getSource().getSender().getName();
                                plugin.getWebhook().sendModerationLog("UNMUTE", target.getName(), staff, "Unmuted", "N/A");
                                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                                        "<green>Successfully unmuted <yellow>" + target.getName() + "</yellow>."
                                ));
                            });
                            return 1;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildCancelrank() {
        return Commands.literal("cancelrank")
                .requires(stack -> stack.getSender().hasPermission("neoncore.giverank"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .executes(ctx -> {
                            String playerName = StringArgumentType.getString(ctx, "player");
                            CompletableFuture.runAsync(() -> {
                                plugin.getDb().removePendingRank(playerName).join();
                                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                                        "<green>Successfully cancelled any pending ranks for <yellow>" + playerName + "</yellow>."
                                ));
                            });
                            return 1;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildNeonmanage() {
        return Commands.literal("neonmanage")
                .requires(stack -> stack.getSender().hasPermission("neoncore.admin"))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                                plugin.reloadConfigs();
                                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                                        "<green>NeonCoreManage configuration files reloaded successfully!"
                                ));
                            });
                            return 1;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildGod() {
        return Commands.literal("god")
                .requires(stack -> stack.getSender().hasPermission("neoncore.god"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player sender)) {
                        ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>This command can only be run by a player!"));
                        return 0;
                    }
                    UUID uuid = sender.getUniqueId();
                    if (plugin.getGodPlayers().contains(uuid)) {
                        plugin.getGodPlayers().remove(uuid);
                        sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>God mode <red>disabled</red>!"));
                    } else {
                        plugin.getGodPlayers().add(uuid);
                        sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>God mode <yellow>enabled</yellow>!"));
                    }
                    return 1;
                })
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                builder.suggest(p.getName());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String playerName = StringArgumentType.getString(ctx, "player");
                            Player target = Bukkit.getPlayerExact(playerName);
                            if (target == null) {
                                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>Player not found!"));
                                return 0;
                            }
                            UUID uuid = target.getUniqueId();
                            if (plugin.getGodPlayers().contains(uuid)) {
                                plugin.getGodPlayers().remove(uuid);
                                target.sendMessage(MiniMessage.miniMessage().deserialize("<green>God mode <red>disabled</red> by staff!"));
                                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<green>God mode <red>disabled</red> for " + target.getName()));
                            } else {
                                plugin.getGodPlayers().add(uuid);
                                target.sendMessage(MiniMessage.miniMessage().deserialize("<green>God mode <yellow>enabled</yellow> by staff!"));
                                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<green>God mode <yellow>enabled</yellow> for " + target.getName()));
                            }
                            return 1;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildDay() {
        return Commands.literal("day")
                .requires(stack -> stack.getSender().hasPermission("neoncore.time"))
                .executes(ctx -> setWorldTime(ctx, 1000))
                .build();
    }
    
    private LiteralCommandNode<CommandSourceStack> buildNight() {
        return Commands.literal("night")
                .requires(stack -> stack.getSender().hasPermission("neoncore.time"))
                .executes(ctx -> setWorldTime(ctx, 13000))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildNoon() {
        return Commands.literal("noon")
                .requires(stack -> stack.getSender().hasPermission("neoncore.time"))
                .executes(ctx -> setWorldTime(ctx, 6000))
                .build();
    }

    private int setWorldTime(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, long time) {
        org.bukkit.World world;
        if (ctx.getSource().getSender() instanceof Player p) {
            world = p.getWorld();
        } else {
            if (Bukkit.getWorlds().isEmpty()) {
                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>No worlds found on the server!"));
                return 0;
            }
            world = Bukkit.getWorlds().get(0);
        }
        
        Bukkit.getRegionScheduler().run(plugin, world.getSpawnLocation(), task -> {
            world.setTime(time);
            ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                    "<green>World time set to <yellow>" + time + "</yellow> in <yellow>" + world.getName() + "</yellow>."
            ));
        });
        return 1;
    }

    private LiteralCommandNode<CommandSourceStack> buildBanip() {
        return Commands.literal("banip")
                .requires(stack -> stack.getSender().hasPermission("neoncore.banip"))
                .then(Commands.argument("player_or_ip", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                builder.suggest(p.getName());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeBanIp(ctx, false))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> executeBanIp(ctx, true))))
                .build();
    }

    private int executeBanIp(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, boolean hasReason) {
        String input = StringArgumentType.getString(ctx, "player_or_ip");
        String reason = hasReason ? StringArgumentType.getString(ctx, "reason") : "Banned by administrator";
        String staff = ctx.getSource().getSender().getName();

        CompletableFuture.runAsync(() -> {
            String targetIp = null;
            String targetName = input;
            Player targetPlayer = Bukkit.getPlayerExact(input);

            if (targetPlayer != null) {
                if (targetPlayer.getAddress() != null) {
                    targetIp = targetPlayer.getAddress().getAddress().getHostAddress();
                }
                targetName = targetPlayer.getName();
            } else if (isValidIp(input)) {
                targetIp = input;
                targetName = "IP: " + input;
            } else {
                targetIp = plugin.getDb().getLastKnownIp(input).join();
            }

            if (targetIp == null) {
                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                        "<red>Could not resolve IP address for: <yellow>" + input + "</yellow>"
                ));
                return;
            }

            plugin.getDb().addIpBan(new DatabaseManager.IpBanEntry(targetIp, staff, reason, -1)).join();

            final String finalIp = targetIp;
            final String finalReason = reason;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getAddress() != null && p.getAddress().getAddress().getHostAddress().equals(finalIp)) {
                    p.getScheduler().run(plugin, task -> p.kick(MiniMessage.miniMessage().deserialize(
                            "<red><bold>YOUR IP IS BANNED FROM THE SERVER</bold></red>\n\n" +
                            "<gray>Reason: <white>" + finalReason + "\n" +
                            "<gray>Time left: <white>Permanent\n\n" +
                            "<yellow>Appeal on - <underlined>discord.ninemc.fun</underlined></yellow>"
                    )), null);
                }
            }

            plugin.getWebhook().sendModerationLog("BANIP", targetName, staff, reason, "Permanent");
            ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                    "<green>Successfully IP banned <yellow>" + targetName + "</yellow> (IP: " + targetIp + ") for: <gray>" + reason
            ));
        });

        return 1;
    }

    private LiteralCommandNode<CommandSourceStack> buildUnbanip() {
        return Commands.literal("unbanip")
                .requires(stack -> stack.getSender().hasPermission("neoncore.banip"))
                .then(Commands.argument("ip", StringArgumentType.string())
                        .executes(ctx -> {
                            String ip = StringArgumentType.getString(ctx, "ip");
                            CompletableFuture.runAsync(() -> {
                                plugin.getDb().removeIpBan(ip).join();
                                String staff = ctx.getSource().getSender().getName();
                                plugin.getWebhook().sendModerationLog("UNBANIP", ip, staff, "Unbanned IP", "N/A");
                                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                                        "<green>Successfully unbanned IP: <yellow>" + ip + "</yellow>."
                                ));
                            });
                            return 1;
                        }))
                .build();
    }

    private boolean isValidIp(String ip) {
        return ip.matches("^([0-9]{1,3}\\.){3}[0-9]{1,3}$") || ip.contains(":");
    }

    private LiteralCommandNode<CommandSourceStack> buildHeal() {
        return Commands.literal("heal")
                .requires(stack -> stack.getSender().hasPermission("neoncore.heal"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player sender)) {
                        ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>This command can only be run by a player!"));
                        return 0;
                    }
                    healPlayer(sender);
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>You have been healed!"));
                    return 1;
                })
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                builder.suggest(p.getName());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "player");
                            Player target = Bukkit.getPlayerExact(name);
                            if (target == null) {
                                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>Player not found!"));
                                return 0;
                            }
                            healPlayer(target);
                            target.sendMessage(MiniMessage.miniMessage().deserialize("<green>You have been healed by staff!"));
                            ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<green>Healed " + target.getName()));
                            return 1;
                        }))
                .build();
    }

    private void healPlayer(Player p) {
        p.getScheduler().run(plugin, task -> {
            p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
            p.setFoodLevel(20);
            p.setFireTicks(0);
        }, null);
    }

    private LiteralCommandNode<CommandSourceStack> buildTpa() {
        return Commands.literal("tpa")
                .requires(stack -> stack.getSender().hasPermission("neoncore.tpa"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                builder.suggest(p.getName());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> sendTpaRequest(ctx, false)))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildTpahere() {
        return Commands.literal("tpahere")
                .requires(stack -> stack.getSender().hasPermission("neoncore.tpa"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                builder.suggest(p.getName());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> sendTpaRequest(ctx, true)))
                .build();
    }

    private int sendTpaRequest(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, boolean isHere) {
        if (!(ctx.getSource().getSender() instanceof Player sender)) {
            ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>This command can only be run by a player!"));
            return 0;
        }

        String targetName = StringArgumentType.getString(ctx, "player");
        Player receiver = Bukkit.getPlayerExact(targetName);

        if (receiver == null || !receiver.isOnline()) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Player not found or offline!"));
            return 0;
        }

        if (sender.getUniqueId().equals(receiver.getUniqueId())) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>You cannot teleport to yourself!"));
            return 0;
        }

        if (plugin.getTpaDisabled().contains(receiver.getUniqueId())) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>" + receiver.getName() + "</yellow> <red>has teleportation requests disabled!</red>"));
            return 0;
        }

        plugin.getTpaRequests().put(receiver.getUniqueId(), new NeonCoreManage.TpaRequest(
                sender.getUniqueId(),
                receiver.getUniqueId(),
                isHere,
                System.currentTimeMillis()
        ));

        if (isHere) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<green>Sent a teleport-here request to <yellow>" + receiver.getName() + "</yellow>."
            ));
            receiver.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<yellow>" + sender.getName() + " </yellow><gray>has requested you to teleport to them.</gray><br>" +
                    "<green><click:run_command:'/tpaccept'><hover:show_text:'<green>Click to accept teleport request'>[Click to Accept]</hover></click></green> <gray>or</gray> " +
                    "<red><click:run_command:'/tpdeny'><hover:show_text:'<red>Click to deny teleport request'>[Click to Deny]</hover></click></red>"
            ));
        } else {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<green>Sent a teleport request to <yellow>" + receiver.getName() + "</yellow>."
            ));
            receiver.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<yellow>" + sender.getName() + " </yellow><gray>has requested to teleport to you.</gray><br>" +
                    "<green><click:run_command:'/tpaccept'><hover:show_text:'<green>Click to accept teleport request'>[Click to Accept]</hover></click></green> <gray>or</gray> " +
                    "<red><click:run_command:'/tpdeny'><hover:show_text:'<red>Click to deny teleport request'>[Click to Deny]</hover></click></red>"
            ));
        }

        return 1;
    }

    private LiteralCommandNode<CommandSourceStack> buildTpaccept() {
        return Commands.literal("tpaccept")
                .requires(stack -> stack.getSender().hasPermission("neoncore.tpa"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player receiver)) {
                        ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>This command can only be run by a player!"));
                        return 0;
                    }

                    NeonCoreManage.TpaRequest request = plugin.getTpaRequests().remove(receiver.getUniqueId());
                    if (request == null || (System.currentTimeMillis() - request.timestamp()) > 60000L) {
                        receiver.sendMessage(MiniMessage.miniMessage().deserialize("<red>You have no pending teleport requests!"));
                        return 0;
                    }

                    Player sender = Bukkit.getPlayer(request.sender());
                    if (sender == null || !sender.isOnline()) {
                        receiver.sendMessage(MiniMessage.miniMessage().deserialize("<red>The sender of the request is no longer online!"));
                        return 0;
                    }

                    Player tpTarget = request.isHere() ? receiver : sender;
                    Player destination = request.isHere() ? sender : receiver;

                    int delay = plugin.getConfig().getInt("teleport.delay", 3);
                    if (delay <= 0) {
                        performTeleport(tpTarget, destination);
                    } else {
                        tpTarget.sendMessage(MiniMessage.miniMessage().deserialize(
                                "<green>Teleporting to <yellow>" + destination.getName() + "</yellow> in <yellow>" + delay + "</yellow> seconds. Do not move!"
                        ));
                        destination.sendMessage(MiniMessage.miniMessage().deserialize(
                                "<yellow>" + tpTarget.getName() + "</yellow> will teleport to you in <yellow>" + delay + "</yellow> seconds."
                        ));

                        long delayTicks = delay * 20L;
                        io.papermc.paper.threadedregions.scheduler.ScheduledTask task = tpTarget.getScheduler().runDelayed(plugin, t -> {
                            plugin.getActiveTeleports().remove(tpTarget.getUniqueId());
                            performTeleport(tpTarget, destination);
                        }, () -> {}, delayTicks);

                        plugin.getActiveTeleports().put(tpTarget.getUniqueId(), task);
                    }

                    return 1;
                })
                .build();
    }

    private void performTeleport(Player target, Player destination) {
        target.getScheduler().run(plugin, task -> {
            target.teleportAsync(destination.getLocation()).thenAccept(success -> {
                if (success) {
                    target.sendMessage(MiniMessage.miniMessage().deserialize("<green>Teleportation complete!"));
                } else {
                    target.sendMessage(MiniMessage.miniMessage().deserialize("<red>Teleportation failed!"));
                }
            });
        }, null);
    }

    private LiteralCommandNode<CommandSourceStack> buildTpdeny() {
        return Commands.literal("tpdeny")
                .requires(stack -> stack.getSender().hasPermission("neoncore.tpa"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player receiver)) {
                        ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>This command can only be run by a player!"));
                        return 0;
                    }

                    NeonCoreManage.TpaRequest request = plugin.getTpaRequests().remove(receiver.getUniqueId());
                    if (request == null || (System.currentTimeMillis() - request.timestamp()) > 60000L) {
                        receiver.sendMessage(MiniMessage.miniMessage().deserialize("<red>You have no pending teleport requests!"));
                        return 0;
                    }

                    Player sender = Bukkit.getPlayer(request.sender());
                    if (sender != null && sender.isOnline()) {
                        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                                "<red>" + receiver.getName() + " denied your teleport request."
                        ));
                    }
                    receiver.sendMessage(MiniMessage.miniMessage().deserialize("<green>Teleport request denied."));
                    return 1;
                })
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildTpatoggle() {
        return Commands.literal("tpatoggle")
                .requires(stack -> stack.getSender().hasPermission("neoncore.tpa"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player p)) {
                        ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>This command can only be run by a player!"));
                        return 0;
                    }

                    if (plugin.getTpaDisabled().contains(p.getUniqueId())) {
                        plugin.getTpaDisabled().remove(p.getUniqueId());
                        p.sendMessage(MiniMessage.miniMessage().deserialize("<green>Teleport requests <yellow>enabled</yellow>!"));
                    } else {
                        plugin.getTpaDisabled().add(p.getUniqueId());
                        p.sendMessage(MiniMessage.miniMessage().deserialize("<green>Teleport requests <red>disabled</red>!"));
                    }
                    return 1;
                })
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildBroadcast() {
        return Commands.literal("broadcast")
                .requires(stack -> stack.getSender().hasPermission("neoncore.broadcast"))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String message = StringArgumentType.getString(ctx, "message");
                            String prefix = plugin.getConfig().getString("broadcast.prefix", "<red><bold>[ALERT]</bold></red> ");
                            Component formatted = MiniMessage.miniMessage().deserialize(prefix + message);
                            Bukkit.broadcast(formatted);
                            return 1;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildIgnore() {
        return Commands.literal("ignore")
                .requires(stack -> stack.getSender().hasPermission("neoncore.ignore"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                builder.suggest(p.getName());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player sender)) {
                                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>This command can only be run by a player!"));
                                return 0;
                            }

                            String targetName = StringArgumentType.getString(ctx, "player");
                            @SuppressWarnings("deprecation")
                            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                            if (target.getUniqueId().equals(sender.getUniqueId())) {
                                sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>You cannot ignore yourself!"));
                                return 0;
                            }

                            UUID ignorerUuid = sender.getUniqueId();
                            UUID ignoredUuid = target.getUniqueId();

                            if (plugin.isIgnoring(ignorerUuid, ignoredUuid)) {
                                plugin.getIgnoreCache().getOrDefault(ignorerUuid, Collections.emptySet()).remove(ignoredUuid);
                                plugin.getDb().removeIgnore(ignorerUuid, ignoredUuid);
                                sender.sendMessage(MiniMessage.miniMessage().deserialize(
                                        "<green>You are no longer ignoring <yellow>" + targetName + "</yellow>."
                                ));
                            } else {
                                plugin.getIgnoreCache().computeIfAbsent(ignorerUuid, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(ignoredUuid);
                                plugin.getDb().addIgnore(ignorerUuid, ignoredUuid);
                                sender.sendMessage(MiniMessage.miniMessage().deserialize(
                                        "<green>You are now ignoring <yellow>" + targetName + "</yellow>."
                                ));
                            }
                            return 1;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildClearInventory() {
        return Commands.literal("clear")
                .requires(stack -> stack.getSender().hasPermission("neoncore.clear"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player sender)) {
                        ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>This command can only be run by a player!"));
                        return 0;
                    }
                    sender.getScheduler().run(plugin, task -> {
                        sender.getInventory().clear();
                        sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>Your inventory has been cleared!"));
                    }, null);
                    return 1;
                })
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) builder.suggest(p.getName());
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "player");
                            Player target = Bukkit.getPlayerExact(name);
                            if (target == null) {
                                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>Player not found!"));
                                return 0;
                            }
                            target.getScheduler().run(plugin, task -> {
                                target.getInventory().clear();
                                target.sendMessage(MiniMessage.miniMessage().deserialize("<red>Your inventory has been cleared by staff!"));
                                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<green>Cleared inventory of <yellow>" + target.getName() + "</yellow>."));
                            }, null);
                            return 1;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildInvsee() {
        return Commands.literal("invsee")
                .requires(stack -> stack.getSender().hasPermission("neoncore.invsee"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) builder.suggest(p.getName());
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player sender)) {
                                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>This command can only be run by a player!"));
                                return 0;
                            }
                            String name = StringArgumentType.getString(ctx, "player");
                            Player target = Bukkit.getPlayerExact(name);
                            if (target == null) {
                                sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Player not found!"));
                                return 0;
                            }
                            sender.getScheduler().run(plugin, task -> {
                                sender.openInventory(target.getInventory());
                            }, null);
                            return 1;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildBack() {
        return Commands.literal("back")
                .requires(stack -> stack.getSender().hasPermission("neoncore.back"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player sender)) {
                        ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>This command can only be run by a player!"));
                        return 0;
                    }
                    org.bukkit.Location deathLoc = plugin.getDeathLocations().get(sender.getUniqueId());
                    if (deathLoc == null) {
                        sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>You have no death location to teleport to!"));
                        return 0;
                    }
                    int delay = plugin.getConfig().getInt("back.delay", 3);
                    if (delay <= 0) {
                        sender.getScheduler().run(plugin, task -> {
                            sender.teleportAsync(deathLoc).thenAccept(success -> {
                                if (success) {
                                    plugin.getDeathLocations().remove(sender.getUniqueId());
                                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>Teleported to your last death location!"));
                                } else {
                                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Failed to teleport!"));
                                }
                            });
                        }, null);
                    } else {
                        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                                "<yellow>Teleporting to your death location in <white>" + delay + "s</white>... Don't move!"
                        ));
                        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask = sender.getScheduler().runDelayed(plugin, task -> {
                            plugin.getActiveTeleports().remove(sender.getUniqueId());
                            sender.teleportAsync(deathLoc).thenAccept(success -> {
                                if (success) {
                                    plugin.getDeathLocations().remove(sender.getUniqueId());
                                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>Teleported to your last death location!"));
                                } else {
                                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Failed to teleport!"));
                                }
                            });
                        }, null, delay * 20L);
                        plugin.getActiveTeleports().put(sender.getUniqueId(), scheduledTask);
                    }
                    return 1;
                })
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildMsg() {
        return Commands.literal("msg")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (Player p : Bukkit.getOnlinePlayers()) builder.suggest(p.getName());
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    if (!(ctx.getSource().getSender() instanceof Player sender)) {
                                        ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>This command can only be run by a player!"));
                                        return 0;
                                    }
                                    // Check if sender is muted
                                    dev.neonjava.neoncoremanage.database.DatabaseManager.MuteEntry mute = plugin.getDb().getMute(sender.getUniqueId()).join();
                                    if (mute != null) {
                                        if (mute.expiresAt() > 0 && mute.expiresAt() < System.currentTimeMillis()) {
                                            plugin.getDb().removeMute(sender.getUniqueId());
                                        } else {
                                            String remaining = dev.neonjava.neoncoremanage.utils.TimeUtils.formatRemaining(mute.expiresAt());
                                            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                                                    "<red>You cannot send private messages while muted!<br>" +
                                                    "<gray>Reason: <white>" + mute.reason() + "<br>" +
                                                    "<gray>Remaining: <white>" + remaining
                                            ));
                                            return 0;
                                        }
                                    }
                                    String targetName = StringArgumentType.getString(ctx, "player");
                                    String message = StringArgumentType.getString(ctx, "message");
                                    Player target = Bukkit.getPlayerExact(targetName);
                                    if (target == null) {
                                        sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Player not found!"));
                                        return 0;
                                    }
                                    if (target.getUniqueId().equals(sender.getUniqueId())) {
                                        sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>You cannot message yourself!"));
                                        return 0;
                                    }
                                    // Check if target is ignoring sender
                                    if (plugin.isIgnoring(target.getUniqueId(), sender.getUniqueId())) {
                                        sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>This player cannot receive your messages."));
                                        return 0;
                                    }
                                    sender.sendMessage(MiniMessage.miniMessage().deserialize(
                                            "<gray>[<gold>You</gold> → <gold>" + target.getName() + "</gold>] <white>" + message
                                    ));
                                    target.sendMessage(MiniMessage.miniMessage().deserialize(
                                            "<gray>[<gold>" + sender.getName() + "</gold> → <gold>You</gold>] <white>" + message
                                    ));
                                    return 1;
                                })))
                .build();
    }
}
