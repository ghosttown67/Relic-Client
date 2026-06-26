package relic.client.account.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.util.Util;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public final class MicrosoftAuth {

    public static final String CLIENT_ID = "4673b348-3efa-4f6a-bbb6-34e141cdc638";

    private static final int PORT = 9675;
    private static final String REDIRECT_URI = "http://127.0.0.1:" + PORT;

    private static final String SCOPE = "XboxLive.signin offline_access";

    public enum Launcher {

        LUNAR("Lunar Client", "00000000402b5328", "service::user.auth.xboxlive.com::MBI_SSL", "t="),
        FEATHER("Feather", "00000000415D0F27", null, null),
        MODRINTH("Modrinth", "00000000402b5328", "service::user.auth.xboxlive.com::MBI_SSL", "t=");

        private final String displayName;
        private final String clientId;
        private final String scope;
        private final String rpsPrefix;

        Launcher(String displayName, String clientId, String scope, String rpsPrefix) {
            this.displayName = displayName;
            this.clientId = clientId;
            this.scope = scope;
            this.rpsPrefix = rpsPrefix;
        }

        public String displayName() { return displayName; }
        public String clientId() { return clientId; }
    }

    public static final Launcher DEFAULT_LAUNCHER = Launcher.LUNAR;

    private static final String RPS_MODERN = "d=";
    private static final String RPS_LEGACY = "t=";

    private static final String AUTHORIZE_URL = "https://login.live.com/oauth20_authorize.srf";
    private static final String TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL =
            "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL =
            "https://api.minecraftservices.com/minecraft/profile";

    private static final long LOGIN_TIMEOUT_MINUTES = 5;

    static final String USER_AGENT = "XAL Win32 2021.11.20220411.002";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private MicrosoftAuth() {}

    public record Result(String username, UUID uuid, String mcAccessToken,
                         String xuid, String refreshToken) {}

    public static class AuthException extends Exception {
        public AuthException(String message) { super(message); }
    }

    public static Result login(Consumer<String> status) throws AuthException {
        HttpServer server = null;
        try {
            CompletableFuture<String> codeFuture = new CompletableFuture<>();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.createContext("/", exchange -> handleRedirect(exchange, codeFuture));
            server.setExecutor(null);
            server.start();

            status.accept("Opening your browser to sign in...");
            openBrowser(authorizeUrl());
            status.accept("Waiting for you to sign in in your browser...");

            String authCode;
            try {
                authCode = codeFuture.get(LOGIN_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                throw new AuthException("Timed out waiting for the browser sign-in.");
            } catch (AuthExceptionWrapper e) {
                throw e.unwrap();
            } catch (Exception e) {
                throw new AuthException("Sign-in was cancelled.");
            }

            status.accept("Signing in...");
            JsonObject tok = postForm(TOKEN_URL, Map.of(
                    "client_id", CLIENT_ID,
                    "code", authCode,
                    "grant_type", "authorization_code",
                    "redirect_uri", REDIRECT_URI,
                    "scope", SCOPE));
            if (!tok.has("access_token")) {
                throw new AuthException(errorOf(tok, "Microsoft login failed"));
            }
            return finishFromMsToken(
                    tok.get("access_token").getAsString(),
                    optString(tok, "refresh_token"),
                    RPS_MODERN,
                    status);
        } catch (java.io.IOException e) {
            throw new AuthException("Could not start the local login server on port " + PORT
                    + " (is another launcher using it?).");
        } finally {
            if (server != null) server.stop(0);
        }
    }

    public static Result refresh(String refreshToken, Consumer<String> status) throws AuthException {
        status.accept("Refreshing Microsoft session...");
        JsonObject res = postForm(TOKEN_URL, Map.of(
                "client_id", CLIENT_ID,
                "grant_type", "refresh_token",
                "refresh_token", refreshToken,
                "redirect_uri", REDIRECT_URI,
                "scope", SCOPE));
        if (!res.has("access_token")) {
            throw new AuthException(errorOf(res, "Session expired — please re-add this account"));
        }
        return finishFromMsToken(
                res.get("access_token").getAsString(),
                optString(res, "refresh_token", refreshToken),
                RPS_MODERN,
                status);
    }

    public static Result refreshLegacy(String refreshToken, Launcher launcher, Consumer<String> status)
            throws AuthException {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthException("Paste a refresh token first.");
        }
        if (launcher == null) launcher = DEFAULT_LAUNCHER;
        status.accept("Refreshing launcher session...");
        String token = refreshToken.trim();

        Map<String, String> form = new java.util.HashMap<>();
        form.put("client_id", launcher.clientId);
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", token);
        if (launcher.scope != null) form.put("scope", launcher.scope);

        JsonObject res = postForm(TOKEN_URL, form);
        if (!res.has("access_token")) {
            String desc = optString(res, "error_description", "");

            if (desc.contains("different client") || desc.contains("client id")) {
                throw new AuthException("This refresh token wasn't issued by the selected launcher. "
                        + "Pick the launcher it came from, and make sure it's a fresh token "
                        + "(these rotate and are single-use — copy it again).");
            }
            throw new AuthException(errorOf(res,
                    "Refresh token rejected — it's invalid or has expired"));
        }

        return finishFromMsToken(
                res.get("access_token").getAsString(),
                optString(res, "refresh_token", token),
                launcher.rpsPrefix,
                status);
    }

    private static String authorizeUrl() {
        return AUTHORIZE_URL
                + "?client_id=" + enc(CLIENT_ID)
                + "&response_type=code"
                + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&scope=" + enc(SCOPE)

                + "&prompt=select_account";
    }

    private static void handleRedirect(HttpExchange exchange, CompletableFuture<String> codeFuture) {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String code = params.get("code");
        String error = params.get("error");

        String html;
        if (code != null) {
            html = page("Signed in", "You're signed in. You can close this tab and return to Minecraft.");
            codeFuture.complete(code);
        } else {
            String reason = error != null ? error : "no authorization code returned";
            html = page("Sign-in failed", "Microsoft sign-in failed (" + reason + "). You can close this tab.");
            codeFuture.completeExceptionally(
                    new AuthExceptionWrapper(new AuthException("Microsoft sign-in failed: " + reason)));
        }
        try {
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        } catch (Exception ignored) {

        }
    }

    private static String page(String title, String message) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>" + title
                + "</title></head><body style=\"font-family:sans-serif;background:#121212;color:#f3f4f7;"
                + "display:flex;align-items:center;justify-content:center;height:100vh;margin:0\">"
                + "<div style=\"text-align:center\"><h2>" + title + "</h2><p>" + message + "</p></div>"
                + "</body></html>";
    }

    private static void openBrowser(String url) {
        try {
            Util.getOperatingSystem().open(URI.create(url));
        } catch (Exception ignored) {

        }
    }

    private static Result finishFromMsToken(String msAccessToken, String refreshToken,
                                            String rpsPrefix, Consumer<String> status) throws AuthException {
        status.accept("Authenticating with Xbox Live...");
        JsonObject xbl = (rpsPrefix != null)
                ? postJson(XBL_URL, xblBody(msAccessToken, rpsPrefix), null)
                : xblAutoDetect(msAccessToken);
        String xblToken = requireString(xbl, "Token", "Xbox Live login failed");
        String userHash = userHashOf(xbl);

        status.accept("Authorizing XSTS...");
        JsonObject xsts = postJson(XSTS_URL, xstsBody(xblToken), null);
        if (xsts.has("XErr")) {
            throw new AuthException(xstsError(xsts.get("XErr").getAsLong()));
        }
        String xstsToken = requireString(xsts, "Token", "XSTS authorization failed");

        status.accept("Logging into Minecraft...");
        JsonObject mc = postJson(MC_LOGIN_URL,
                "{\"identityToken\":\"XBL3.0 x=" + userHash + ";" + xstsToken + "\"}", null);
        String mcToken = requireString(mc, "access_token", "Minecraft login failed");

        status.accept("Fetching profile...");
        JsonObject profile = getJson(MC_PROFILE_URL, mcToken);
        if (!profile.has("id") || !profile.has("name")) {
            throw new AuthException("This Microsoft account doesn't own Minecraft: Java Edition.");
        }
        return new Result(
                profile.get("name").getAsString(),
                dashUuid(profile.get("id").getAsString()),
                mcToken,
                optString(xsts, "xuid"),
                refreshToken);
    }

    private static JsonObject xblAutoDetect(String msAccessToken) throws AuthException {
        JsonObject legacy = tryXbl(msAccessToken, RPS_LEGACY);
        if (legacy != null && legacy.has("Token")) return legacy;
        JsonObject modern = tryXbl(msAccessToken, RPS_MODERN);
        if (modern != null && modern.has("Token")) return modern;

        throw new AuthException("Xbox Live rejected this launcher session with both ticket "
                + "formats — the refresh token may be invalid or expired (copy a fresh one).");
    }

    private static JsonObject tryXbl(String msAccessToken, String rpsPrefix) {
        try {
            return postJson(XBL_URL, xblBody(msAccessToken, rpsPrefix), null);
        } catch (AuthException e) {
            return null;
        }
    }

    private static String xblBody(String msAccessToken, String rpsPrefix) {
        JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName", "user.auth.xboxlive.com");

        props.addProperty("RpsTicket", rpsPrefix + msAccessToken);
        JsonObject body = new JsonObject();
        body.add("Properties", props);
        body.addProperty("RelyingParty", "http://auth.xboxlive.com");
        body.addProperty("TokenType", "JWT");
        return body.toString();
    }

    private static String xstsBody(String xblToken) {
        JsonObject props = new JsonObject();
        com.google.gson.JsonArray tokens = new com.google.gson.JsonArray();
        tokens.add(xblToken);
        props.add("UserTokens", tokens);
        props.addProperty("SandboxId", "RETAIL");
        JsonObject body = new JsonObject();
        body.add("Properties", props);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType", "JWT");
        return body.toString();
    }

    private static String userHashOf(JsonObject xbl) throws AuthException {
        try {
            return xbl.getAsJsonObject("DisplayClaims").getAsJsonArray("xui")
                    .get(0).getAsJsonObject().get("uhs").getAsString();
        } catch (Exception e) {
            throw new AuthException("Xbox Live login returned no user hash.");
        }
    }

    private static String xstsError(long xerr) {
        return switch (Long.toString(xerr)) {
            case "2148916233" -> "This account has no Xbox profile — sign in once at minecraft.net first.";
            case "2148916235" -> "Xbox Live isn't available in this account's region.";
            case "2148916236", "2148916237" -> "This account needs adult verification.";
            case "2148916238" -> "This is a child account; add it to a Family to continue.";
            default -> "XSTS authorization failed (error " + xerr + ").";
        };
    }

    private static JsonObject postForm(String url, Map<String, String> form) throws AuthException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(sb.toString()))
                .build();
        return send(request);
    }

    private static JsonObject postJson(String url, String json, String bearer) throws AuthException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        return send(b.build());
    }

    private static JsonObject getJson(String url, String bearer) throws AuthException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en")
                .header("Authorization", "Bearer " + bearer)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        return send(request);
    }

    private static JsonObject send(HttpRequest request) throws AuthException {
        HttpResponse<String> res;
        try {
            res = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new AuthException("Timed out talking to " + request.uri().getHost() + ".");
        } catch (java.io.IOException | InterruptedException e) {

            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new AuthException("Network error talking to " + request.uri().getHost() + ": " + detail);
        }

        String body = res.body();
        if (body == null || body.isBlank()) {

            if (res.statusCode() / 100 != 2) {
                throw new AuthException(request.uri().getHost() + " returned HTTP " + res.statusCode() + ".");
            }
            return new JsonObject();
        }

        String contentType = res.headers().firstValue("Content-Type").orElse("");
        if (contentType.toLowerCase().contains("x-www-form-urlencoded")
                || (!body.stripLeading().startsWith("{") && looksLikeForm(body))) {
            return formToJson(body);
        }
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {

            System.err.println("[Relic Client] Non-JSON auth response from " + request.uri()
                    + " (HTTP " + res.statusCode() + "):\n" + body);
            throw new AuthException(request.uri().getHost() + " returned HTTP " + res.statusCode()
                    + " (" + snippet(body) + ").");
        }
    }

    private static boolean looksLikeForm(String body) {
        String s = body.strip();
        if (s.isEmpty() || s.startsWith("<")) return false;
        int eq = s.indexOf('=');
        return eq > 0 && !s.regionMatches(0, "{", 0, 1);
    }

    private static JsonObject formToJson(String body) {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, String> e : parseQuery(body).entrySet()) {
            o.addProperty(e.getKey(), e.getValue());
        }
        return o;
    }

    private static String snippet(String body) {
        String s = body.strip().replaceAll("\\s+", " ");
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }

    private static String requireString(JsonObject o, String key, String failMsg) throws AuthException {
        if (o.has(key) && !o.get(key).isJsonNull()) return o.get(key).getAsString();
        throw new AuthException(failMsg);
    }

    private static String optString(JsonObject o, String key) {
        return optString(o, key, null);
    }

    private static String optString(JsonObject o, String key, String def) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : def;
    }

    private static String errorOf(JsonObject res, String fallback) {
        String desc = optString(res, "error_description");
        if (desc != null) return desc;
        String err = optString(res, "error");
        return err != null ? fallback + ": " + err : fallback;
    }

    private static UUID dashUuid(String undashed) {
        return UUID.fromString(undashed.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5"));
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new java.util.HashMap<>();
        if (query == null) return map;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String k = java.net.URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String v = java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            map.put(k, v);
        }
        return map;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static final class AuthExceptionWrapper extends RuntimeException {
        AuthExceptionWrapper(AuthException cause) { super(cause); }
        AuthException unwrap() { return (AuthException) getCause(); }
    }
}
