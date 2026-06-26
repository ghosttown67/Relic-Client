package relic.client.gui;

import imgui.ImGui;

import java.util.HashMap;
import java.util.Map;

public final class Animations {

    private static final Map<String, Float> VALUES = new HashMap<>();

    private Animations() {}

    public static float to(String id, float target, float speed) {
        float dt = ImGui.getIO().getDeltaTime();
        if (dt <= 0f || Float.isNaN(dt)) dt = 1f / 60f;
        dt = Math.min(dt, 0.1f);

        float current = VALUES.getOrDefault(id, target);

        float t = 1f - (float) Math.exp(-speed * dt);
        current += (target - current) * t;
        if (Math.abs(target - current) < 0.0005f) current = target;

        VALUES.put(id, current);
        return current;
    }

    public static void set(String id, float value) {
        VALUES.put(id, value);
    }

    public static float get(String id, float fallback) {
        return VALUES.getOrDefault(id, fallback);
    }
}
