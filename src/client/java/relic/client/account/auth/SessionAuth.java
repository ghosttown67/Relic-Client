package relic.client.account.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import relic.client.account.auth.MicrosoftAuth.AuthException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

public final class SessionAuth {

    private static final String MC_PROFILE_URL =
            "https://api.minecraftservices.com/minecraft/profile";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private SessionAuth() {}

    public record Profile(String username, UUID uuid) {}

    public static String clean(String token) {
        if (token == null) return "";
        String t = token.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1).trim();
        }
        if (t.regionMatches(true, 0, "Bearer ", 0, 7)) {
            t = t.substring(7).trim();
        }
        return t;
    }

    public static Profile validate(String token) throws AuthException {
        String t = clean(token);
        if (t.isEmpty()) {
            throw new AuthException("Paste a session token first.");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(MC_PROFILE_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")

                .header("Accept-Language", "en-US,en")
                .header("Authorization", "Bearer " + t)
                .header("User-Agent", MicrosoftAuth.USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> res;
        try {
            res = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new AuthException("Network error talking to Minecraft services.");
        }

        if (res.statusCode() == 401 || res.statusCode() == 403) {
            throw new AuthException("Token rejected — it's invalid or has expired.");
        }
        if (res.statusCode() / 100 != 2) {
            throw new AuthException("Minecraft services returned HTTP " + res.statusCode() + ".");
        }

        JsonObject profile;
        try {
            String body = res.body();
            profile = (body == null || body.isBlank())
                    ? new JsonObject()
                    : JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            throw new AuthException("Couldn't read the profile response.");
        }
        if (!profile.has("id") || !profile.has("name")) {
            throw new AuthException("This token's account doesn't own Minecraft: Java Edition.");
        }
        return new Profile(
                profile.get("name").getAsString(),
                dashUuid(profile.get("id").getAsString()));
    }

    private static UUID dashUuid(String undashed) {
        return UUID.fromString(undashed.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5"));
    }
}
