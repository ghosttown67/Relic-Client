package relic.client.account;

import com.google.gson.JsonObject;
import relic.client.account.auth.MicrosoftAuth;
import relic.client.account.auth.SessionAuth;

import java.util.UUID;
import java.util.function.Consumer;

public class Account {

    private final AccountType type;
    private String username;
    private UUID uuid;

    private String refreshToken;

    private String accessToken;

    private MicrosoftAuth.Launcher launcher;

    private Account(AccountType type, String username, UUID uuid, String refreshToken,
                    String accessToken, MicrosoftAuth.Launcher launcher) {
        this.type = type;
        this.username = username;
        this.uuid = uuid;
        this.refreshToken = refreshToken;
        this.accessToken = accessToken;
        this.launcher = launcher;
    }

    public static Account offline(String username) {
        return new Account(AccountType.OFFLINE, username, SessionSwapper.offlineUuid(username), null, null, null);
    }

    public static Account microsoft(MicrosoftAuth.Result result) {
        return new Account(AccountType.MICROSOFT, result.username(), result.uuid(), result.refreshToken(), null, null);
    }

    public static Account refresh(MicrosoftAuth.Result result, MicrosoftAuth.Launcher launcher) {
        return new Account(AccountType.REFRESH, result.username(), result.uuid(), result.refreshToken(), null, launcher);
    }

    public static Account session(SessionAuth.Profile profile, String accessToken) {
        return new Account(AccountType.SESSION, profile.username(), profile.uuid(), null,
                SessionAuth.clean(accessToken), null);
    }

    public boolean login(Consumer<String> status) {
        if (type == AccountType.OFFLINE) {

            uuid = SessionSwapper.offlineUuid(username);
            SessionSwapper.apply(username, uuid, "0", null);
            status.accept("Switched to " + username);
            return true;
        }
        if (type == AccountType.SESSION) {
            try {

                SessionAuth.Profile p = SessionAuth.validate(accessToken);
                username = p.username();
                uuid = p.uuid();
                SessionSwapper.apply(username, uuid, accessToken, null);
                status.accept("Logged in as " + username);
                return true;
            } catch (MicrosoftAuth.AuthException e) {
                status.accept(e.getMessage());
                return false;
            }
        }
        try {

            MicrosoftAuth.Result r = (type == AccountType.REFRESH)
                    ? MicrosoftAuth.refreshLegacy(refreshToken, launcher, status)
                    : MicrosoftAuth.refresh(refreshToken, status);

            username = r.username();
            uuid = r.uuid();
            if (r.refreshToken() != null) refreshToken = r.refreshToken();
            SessionSwapper.apply(r.username(), r.uuid(), r.mcAccessToken(), r.xuid());
            status.accept("Logged in as " + r.username());
            return true;
        } catch (MicrosoftAuth.AuthException e) {
            status.accept(e.getMessage());
            return false;
        }
    }

    public AccountType getType() {
        return type;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
        if (type == AccountType.OFFLINE) {
            this.uuid = SessionSwapper.offlineUuid(username);
        }
    }

    public UUID getUuid() {
        return uuid;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("type", type.name());
        o.addProperty("username", username);
        o.addProperty("uuid", uuid.toString());
        if (refreshToken != null) o.addProperty("refreshToken", refreshToken);
        if (accessToken != null) o.addProperty("accessToken", accessToken);
        if (launcher != null) o.addProperty("launcher", launcher.name());
        return o;
    }

    public static Account fromJson(JsonObject o) {
        try {
            AccountType type = AccountType.valueOf(o.get("type").getAsString());
            String username = o.get("username").getAsString();
            UUID uuid = o.has("uuid")
                    ? UUID.fromString(o.get("uuid").getAsString())
                    : SessionSwapper.offlineUuid(username);
            String refresh = o.has("refreshToken") ? o.get("refreshToken").getAsString() : null;
            String access = o.has("accessToken") ? o.get("accessToken").getAsString() : null;
            MicrosoftAuth.Launcher launcher = null;
            if (type == AccountType.REFRESH) {
                if (o.has("launcher")) {
                    try {
                        launcher = MicrosoftAuth.Launcher.valueOf(o.get("launcher").getAsString());
                    } catch (IllegalArgumentException ignored) {

                    }
                }

                if (launcher == null) launcher = MicrosoftAuth.DEFAULT_LAUNCHER;
            }
            return new Account(type, username, uuid, refresh, access, launcher);
        } catch (Exception e) {
            return null;
        }
    }
}
