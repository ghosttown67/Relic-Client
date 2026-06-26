package relic.client.notification;

public class Notification {

    public static final long SLIDE_MS = 300L;

    private final String title;
    private final String message;
    private final int accent;
    private final long createdAt;
    private final long holdMillis;

    public Notification(String title, String message, int accent, long holdMillis) {
        this.title = title;
        this.message = message == null ? "" : message;
        this.accent = accent;
        this.holdMillis = holdMillis;
        this.createdAt = System.currentTimeMillis();
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public boolean hasMessage() {
        return !message.isEmpty();
    }

    public int getAccent() {
        return accent;
    }

    private long age() {
        return System.currentTimeMillis() - createdAt;
    }

    public boolean isExpired() {
        return age() >= SLIDE_MS + holdMillis + SLIDE_MS;
    }

    public float slideFraction() {
        long t = age();
        if (t < SLIDE_MS) {
            return ease(1.0f - (float) t / SLIDE_MS);
        }
        long holdEnd = SLIDE_MS + holdMillis;
        if (t < holdEnd) {
            return 0.0f;
        }
        float out = (float) (t - holdEnd) / SLIDE_MS;
        return ease(Math.min(1.0f, out));
    }

    private static float ease(float x) {
        x = Math.max(0.0f, Math.min(1.0f, x));
        return x * x * (3 - 2 * x);
    }
}
