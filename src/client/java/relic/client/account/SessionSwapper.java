package relic.client.account;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import relic.client.client.mixin.MinecraftClientAccessor;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

public final class SessionSwapper {

    private SessionSwapper() {}

    public static UUID offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    public static void apply(String username, UUID uuid, String accessToken, String xuid) {
        apply(new Session(username, uuid, accessToken,
                Optional.ofNullable(xuid), Optional.empty()));
    }

    public static void apply(Session session) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Runnable task = () -> ((MinecraftClientAccessor) mc).relic$setSession(session);
        if (mc.isOnThread()) task.run();
        else mc.execute(task);
    }
}
