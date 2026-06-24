package dev.neonjava.neoncoremanage.webhook;

import dev.neonjava.neoncoremanage.NeonCoreManage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class WebhookManager {
    private final NeonCoreManage plugin;
    private final HttpClient client = HttpClient.newHttpClient();

    public WebhookManager(NeonCoreManage plugin) {
        this.plugin = plugin;
    }

    /**
     * Sends a moderation embed to the Discord webhook.
     * Blocking — call this from an async thread.
     */
    public void sendModerationLog(String action, String targetPlayer, String staff, String reason, String duration) {
        String webhookUrl = plugin.getConfig().getString("discord.moderation-webhook-url");
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.equals("YOUR_WEBHOOK_URL_HERE")) {
            return;
        }

        try {
            int color;
            switch (action) {
                case "BAN":
                case "TEMPBAN":
                case "BANIP":
                    color = 0xFF3333; // red
                    break;
                case "UNBAN":
                case "UNBANIP":
                    color = 0x00CC66; // green
                    break;
                case "MUTE":
                case "TEMPMUTE":
                    color = 0xFF8800; // orange
                    break;
                case "UNMUTE":
                    color = 0x33AAFF; // blue
                    break;
                case "GIVERANK":
                    color = 0xAA00FF; // purple
                    break;
                default:
                    color = 0xAAAAAA; // grey
            }

            String safeTarget   = targetPlayer == null ? "Unknown" : escape(targetPlayer);
            String safeStaff    = staff        == null ? "Console"  : escape(staff);
            String safeReason   = reason       == null ? "None"     : escape(reason);
            String safeDuration = duration     == null ? "N/A"      : escape(duration);

            String json = "{"
                    + "\"embeds\": [{"
                    + "\"title\": \"🛡️ " + escape(action) + "\","
                    + "\"color\": " + color + ","
                    + "\"fields\": ["
                    + "{\"name\": \"Target Player\", \"value\": \"" + safeTarget   + "\", \"inline\": true},"
                    + "{\"name\": \"Staff Member\",  \"value\": \"" + safeStaff    + "\", \"inline\": true},"
                    + "{\"name\": \"Duration\",      \"value\": \"" + safeDuration + "\", \"inline\": true},"
                    + "{\"name\": \"Reason\",        \"value\": \"" + safeReason   + "\", \"inline\": false}"
                    + "],"
                    + "\"timestamp\": \"" + java.time.Instant.now().toString() + "\""
                    + "}]"
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                plugin.getLogger().warning("[NeonCoreManage Webhook] Bad response: " + response.statusCode() + " — " + response.body());
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[NeonCoreManage Webhook] Failed to send webhook: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sends player chat messages to the Discord Chat Bridge webhook.
     * Blocking — call this from an async thread.
     */
    public void sendChatBridge(String player, String message) {
        if (!plugin.getConfig().getBoolean("modules.chat-bridge", true)) {
            return;
        }
        String webhookUrl = plugin.getConfig().getString("discord.chat-bridge-webhook-url");
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.equals("YOUR_WEBHOOK_URL_HERE")) {
            return;
        }

        try {
            String format = plugin.getConfig().getString("discord.chat-bridge-format", "%player%: %message%");
            String content = format.replace("%player%", player).replace("%message%", message);

            String json = "{"
                    + "\"content\": \"" + escape(content) + "\""
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                plugin.getLogger().warning("[NeonCoreManage Webhook Bridge] Bad response: " + response.statusCode() + " — " + response.body());
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[NeonCoreManage Webhook Bridge] Failed to send chat message: " + e.getMessage());
        }
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
