package dev.neonjava.neoncoremanage.webhook;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class WebhookManager {
    private static final String WEBHOOK_URL = "REDACTED_WEBHOOK_URL";
    private final HttpClient client = HttpClient.newHttpClient();

    /**
     * Sends a moderation embed to the Discord webhook.
     * Blocking — call this from an async thread (e.g. CompletableFuture.runAsync).
     */
    public void sendModerationLog(String action, String targetPlayer, String staff, String reason, String duration) {
        try {
            int color;
            switch (action) {
                case "BAN":
                case "TEMPBAN":
                    color = 0xFF3333; // red
                    break;
                case "UNBAN":
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
                    .uri(URI.create(WEBHOOK_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.err.println("[NeonCoreManage Webhook] Bad response: " + response.statusCode() + " — " + response.body());
            }
        } catch (Exception e) {
            System.err.println("[NeonCoreManage Webhook] Failed to send webhook: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
