package dev.neonjava.neoncoremanage.webhook;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class WebhookManager {
    private static final String WEBHOOK_URL = "REDACTED_WEBHOOK_URL";
    private final HttpClient client = HttpClient.newHttpClient();

    public CompletableFuture<Void> sendModerationLog(String action, String targetPlayer, String staff, String reason, String duration) {
        return CompletableFuture.runAsync(() -> {
            try {
                int color = 0x00FF00;
                if (action.contains("BAN")) {
                    color = 0xFF0000;
                } else if (action.contains("MUTE")) {
                    color = 0xFFA500;
                }

                String cleanReason = reason == null ? "None" : reason.replace("\\", "\\\\").replace("\"", "\\\"");

                String json = "{"
                        + "\"embeds\": [{"
                        + "\"title\": \"🛡️ Moderation Action: " + action + "\","
                        + "\"color\": " + color + ","
                        + "\"fields\": ["
                        + "{\"name\": \"Target Player\", \"value\": \"" + targetPlayer + "\", \"inline\": true},"
                        + "{\"name\": \"Staff Member\", \"value\": \"" + staff + "\", \"inline\": true},"
                        + "{\"name\": \"Duration\", \"value\": \"" + duration + "\", \"inline\": true},"
                        + "{\"name\": \"Reason\", \"value\": \"" + cleanReason + "\", \"inline\": false}"
                        + "],"
                        + "\"timestamp\": \"" + java.time.Instant.now().toString() + "\""
                        + "}]"
                        + "}";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(WEBHOOK_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                                System.err.println("[NeonCoreManage Webhook] Bad response status: " + response.statusCode());
                            }
                        });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
