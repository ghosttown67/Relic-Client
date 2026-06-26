package relic.client.api.discord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import relic.client.config.ClientSettings;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class DiscordWebhook {

    private static final Gson GSON = new Gson();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String HOST_HINT = "discord.com/api/webhooks/";
    private static final String HOST_HINT_ALT = "discordapp.com/api/webhooks/";

    private DiscordWebhook() {}

    public static void send(String title, String message, int accentArgb) {
        send(title, message, accentArgb, Map.of());
    }

    public static void send(String title, String message, int accentArgb, Map<String, String> fields) {
        String url = ClientSettings.getWebhookUrl();
        if (url == null || url.isBlank()) return;
        post(url.trim(), title, message, accentArgb, fields, null);
    }

    public static void sendTest(String url, java.util.function.Consumer<Boolean> onResult) {
        if (url == null || url.isBlank() || !looksLikeWebhook(url)) {
            onResult.accept(false);
            return;
        }
        post(url.trim(), "Relic Client", "Webhook connected — alerts will arrive here.",
                relic.client.notification.NotificationManager.DEFAULT_ACCENT, Map.of(), onResult);
    }

    public static boolean looksLikeWebhook(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase();
        return u.startsWith("https://") && (u.contains(HOST_HINT) || u.contains(HOST_HINT_ALT));
    }

    private static void post(String url, String title, String message, int accentArgb,
                             Map<String, String> fields, java.util.function.Consumer<Boolean> onResult) {
        final HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload(title, message, accentArgb, fields)))
                    .build();
        } catch (Exception e) {
            if (onResult != null) onResult.accept(false);
            return;
        }

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, error) -> {
                    boolean ok = error == null && response.statusCode() / 100 == 2;
                    if (!ok) {
                        String detail = error != null ? error.getMessage()
                                : "HTTP " + response.statusCode();
                        System.err.println("[Relic Client] Discord webhook send failed: " + detail);
                    }
                    if (onResult != null) onResult.accept(ok);
                });
    }

    private static String payload(String title, String message, int accentArgb, Map<String, String> fields) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", clamp(title, 256));
        if (message != null && !message.isBlank()) {
            embed.addProperty("description", clamp(message, 4096));
        }

        embed.addProperty("color", accentArgb & 0xFFFFFF);
        embed.addProperty("timestamp",
                OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        if (fields != null && !fields.isEmpty()) {
            JsonArray arr = new JsonArray();

            int n = 0;
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (n++ >= 25) break;
                JsonObject f = new JsonObject();
                f.addProperty("name", clamp(e.getKey(), 256));
                f.addProperty("value", clamp(emptyToDash(e.getValue()), 1024));
                f.addProperty("inline", true);
                arr.add(f);
            }
            embed.add("fields", arr);
        }

        JsonArray embeds = new JsonArray();
        embeds.add(embed);

        JsonObject root = new JsonObject();
        root.addProperty("username", "Relic Client");
        root.add("embeds", embeds);
        return GSON.toJson(root);
    }

    private static String clamp(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String emptyToDash(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }
}
