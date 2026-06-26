package relic.client.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import relic.client.account.auth.MicrosoftAuth;
import relic.client.account.auth.SessionAuth;
import relic.client.notification.NotificationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class AccountManager {

    private static final AccountManager INSTANCE = new AccountManager();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("relic-accounts.json");
    private static final int ERROR_ACCENT = 0xFFE0533D;

    private final List<Account> accounts = new ArrayList<>();

    private volatile String status = "";
    private volatile boolean busy;

    private AccountManager() {}

    public static AccountManager getInstance() {
        return INSTANCE;
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public void addOffline(String username) {
        if (username == null || username.isBlank()) return;
        String name = username.trim();
        for (Account a : accounts) {
            if (a.getType() == AccountType.OFFLINE && a.getUsername().equalsIgnoreCase(name)) {
                status = "A cracked account named \"" + name + "\" already exists.";
                return;
            }
        }
        accounts.add(Account.offline(name));
        save();
    }

    public void remove(Account account) {
        if (accounts.remove(account)) save();
    }

    private Account findByUuid(AccountType type, java.util.UUID uuid) {
        for (Account a : accounts) {
            if (a.getType() == type && a.getUuid().equals(uuid)) return a;
        }
        return null;
    }

    public boolean isBusy() {
        return busy;
    }

    public String getStatus() {
        return status;
    }

    public void beginAddMicrosoft() {
        if (busy) return;
        busy = true;
        status = "Opening your browser to sign in...";
        runAsync(() -> {
            try {
                MicrosoftAuth.Result result = MicrosoftAuth.login(s -> status = s);

                Account existing = findByUuid(AccountType.MICROSOFT, result.uuid());
                if (existing != null) accounts.remove(existing);
                accounts.add(Account.microsoft(result));
                save();
                status = (existing != null ? "Updated " : "Added ") + result.username();
                notify(existing != null ? "Account updated" : "Account added", result.username(), true);
            } catch (MicrosoftAuth.AuthException e) {
                status = e.getMessage();
                notify("Microsoft login failed", e.getMessage(), false);
            } finally {
                busy = false;
            }
        });
    }

    public void beginAddSession(String token) {
        if (busy) return;
        busy = true;
        status = "Validating session token...";
        runAsync(() -> {
            try {
                SessionAuth.Profile profile = SessionAuth.validate(token);

                Account existing = findByUuid(AccountType.SESSION, profile.uuid());
                if (existing != null) accounts.remove(existing);
                accounts.add(Account.session(profile, token));
                save();
                status = (existing != null ? "Updated " : "Added ") + profile.username();
                notify(existing != null ? "Account updated" : "Account added", profile.username(), true);
            } catch (MicrosoftAuth.AuthException e) {
                status = e.getMessage();
                notify("Session token rejected", e.getMessage(), false);
            } finally {
                busy = false;
            }
        });
    }

    public void beginAddRefresh(String refreshToken, MicrosoftAuth.Launcher launcher) {
        if (busy) return;
        busy = true;
        status = "Redeeming refresh token...";
        runAsync(() -> {
            try {
                MicrosoftAuth.Result result = MicrosoftAuth.refreshLegacy(refreshToken, launcher, s -> status = s);

                Account existing = findByUuid(AccountType.REFRESH, result.uuid());
                if (existing != null) accounts.remove(existing);
                accounts.add(Account.refresh(result, launcher));
                save();
                status = (existing != null ? "Updated " : "Added ") + result.username();
                notify(existing != null ? "Account updated" : "Account added", result.username(), true);
            } catch (MicrosoftAuth.AuthException e) {
                status = e.getMessage();
                notify("Refresh token rejected", e.getMessage(), false);
            } finally {
                busy = false;
            }
        });
    }

    public void beginLogin(Account account) {
        if (busy) return;
        busy = true;
        status = "Logging in...";
        runAsync(() -> {
            try {
                boolean ok = account.login(s -> status = s);
                if (ok) notify("Switched account", account.getUsername(), true);
                else notify("Login failed", status, false);
                if (ok) save();
            } finally {
                busy = false;
            }
        });
    }

    private void runAsync(Runnable task) {
        Thread t = new Thread(task, "Relic-Accounts");
        t.setDaemon(true);
        t.start();
    }

    private void notify(String title, String message, boolean ok) {
        NotificationManager.getInstance().push(title, message,
                ok ? NotificationManager.DEFAULT_ACCENT : ERROR_ACCENT,
                NotificationManager.DEFAULT_HOLD_MS, true);
    }

    public void load() {
        accounts.clear();
        if (!Files.exists(FILE)) return;
        try {
            JsonElement root = JsonParser.parseString(Files.readString(FILE));
            JsonArray arr = root.getAsJsonObject().getAsJsonArray("accounts");
            if (arr == null) return;
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                Account a = Account.fromJson(el.getAsJsonObject());
                if (a != null) accounts.add(a);
            }
        } catch (Exception e) {
            System.err.println("[Relic Client] Failed to load accounts (using none):");
            e.printStackTrace();
        }
    }

    public void save() {
        JsonArray arr = new JsonArray();
        for (Account a : accounts) arr.add(a.toJson());
        JsonObject root = new JsonObject();
        root.add("accounts", arr);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root));
        } catch (Exception e) {
            System.err.println("[Relic Client] Failed to save accounts:");
            e.printStackTrace();
        }
    }
}
