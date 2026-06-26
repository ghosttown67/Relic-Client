package relic.client.account;

public enum AccountType {
    MICROSOFT("Microsoft"),
    SESSION("Session"),
    REFRESH("Refresh Token"),
    OFFLINE("Cracked");

    private final String displayName;

    AccountType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
