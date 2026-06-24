package dev.neonjava.neoncoremanage;

import dev.neonjava.neoncoremanage.commands.CoreCommands;
import dev.neonjava.neoncoremanage.database.DatabaseManager;
import dev.neonjava.neoncoremanage.listeners.ChatListener;
import dev.neonjava.neoncoremanage.listeners.JoinListener;
import dev.neonjava.neoncoremanage.webhook.WebhookManager;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class NeonCoreManage extends JavaPlugin {
    private DatabaseManager db;
    private WebhookManager webhook;
    
    private FileConfiguration cmdsConfig;
    private FileConfiguration bannedWordsConfig;
    private File cmdsFile;
    private File bannedWordsFile;

    private final java.util.Set<java.util.UUID> godPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public record TpaRequest(java.util.UUID sender, java.util.UUID receiver, boolean isHere, long timestamp) {}
    private final java.util.Map<java.util.UUID, TpaRequest> tpaRequests = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<java.util.UUID> tpaDisabled = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<java.util.UUID, io.papermc.paper.threadedregions.scheduler.ScheduledTask> activeTeleports = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, org.bukkit.Location> deathLocations = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfigs();
        
        db = new DatabaseManager(this);
        db.init();

        webhook = new WebhookManager(this);

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new dev.neonjava.neoncoremanage.listeners.GodListener(this), this);
        getServer().getPluginManager().registerEvents(new dev.neonjava.neoncoremanage.listeners.TeleportListener(this), this);
        getServer().getPluginManager().registerEvents(new dev.neonjava.neoncoremanage.listeners.DeathListener(this), this);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            CoreCommands coreCmds = new CoreCommands(this);
            coreCmds.registerAll(commands);
        });

        getLogger().info("NeonCoreManage loaded successfully and is Folia compatible!");
    }

    @Override
    public void onDisable() {
        if (db != null) {
            db.close();
        }
        getLogger().info("NeonCoreManage disabled successfully!");
    }

    private void saveDefaultConfigs() {
        saveDefaultConfig();
        cmdsFile = new File(getDataFolder(), "cmds.yml");
        bannedWordsFile = new File(getDataFolder(), "banned_words.yml");

        if (!cmdsFile.exists()) {
            saveResource("cmds.yml", false);
        }
        if (!bannedWordsFile.exists()) {
            saveResource("banned_words.yml", false);
        }

        cmdsConfig = YamlConfiguration.loadConfiguration(cmdsFile);
        bannedWordsConfig = YamlConfiguration.loadConfiguration(bannedWordsFile);
    }

    public void reloadConfigs() {
        reloadConfig();
        cmdsConfig = YamlConfiguration.loadConfiguration(cmdsFile);
        bannedWordsConfig = YamlConfiguration.loadConfiguration(bannedWordsFile);
    }

    private final java.util.Map<java.util.UUID, java.util.Set<java.util.UUID>> ignoreCache = new java.util.concurrent.ConcurrentHashMap<>();

    public java.util.Map<java.util.UUID, java.util.Set<java.util.UUID>> getIgnoreCache() {
        return ignoreCache;
    }

    public boolean isIgnoring(java.util.UUID ignorer, java.util.UUID ignored) {
        java.util.Set<java.util.UUID> ignoredSet = ignoreCache.get(ignorer);
        return ignoredSet != null && ignoredSet.contains(ignored);
    }

    public java.util.Set<java.util.UUID> getGodPlayers() {
        return godPlayers;
    }

    public java.util.Map<java.util.UUID, TpaRequest> getTpaRequests() {
        return tpaRequests;
    }

    public java.util.Set<java.util.UUID> getTpaDisabled() {
        return tpaDisabled;
    }

    public java.util.Map<java.util.UUID, io.papermc.paper.threadedregions.scheduler.ScheduledTask> getActiveTeleports() {
        return activeTeleports;
    }

    public java.util.Map<java.util.UUID, org.bukkit.Location> getDeathLocations() {
        return deathLocations;
    }

    public DatabaseManager getDb() {
        return db;
    }

    public WebhookManager getWebhook() {
        return webhook;
    }

    public FileConfiguration getCmdsConfig() {
        return cmdsConfig;
    }

    public FileConfiguration getBannedWordsConfig() {
        return bannedWordsConfig;
    }
}
