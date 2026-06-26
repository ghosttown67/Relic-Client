package relic.client.api.utils;

public final class TpsTracker {
    private TpsTracker() {}

    private static long lastUpdate = 0;
    private static double tps = 20.0;

    public static void onTimeUpdate() {
        long now = System.currentTimeMillis();
        if (lastUpdate != 0) {
            long delta = now - lastUpdate;
            if (delta > 0) {

                double sample = Math.min(20.0, 20000.0 / delta);
                tps = tps * 0.8 + sample * 0.2;
            }
        }
        lastUpdate = now;
    }

    public static double getTps() {
        return tps;
    }
}
