package relic.client.module.impl.misc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import relic.client.module.Module;
import relic.client.module.setting.NumberSetting;
import relic.client.module.setting.StringSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class KillMessageModule extends Module {

    private static KillMessageModule instance;

    private static final long TRACK_WINDOW_MS = 5000L;

    private static final long REMOVAL_WINDOW_MS = 1000L;

    private final StringSetting message = new StringSetting("Message", "gg");
    private final NumberSetting delay   = new NumberSetting("Delay", 2, 0, 10, true);

    private final Map<Integer, Long> attacked = new HashMap<>();

    private final List<Long> pending = new ArrayList<>();

    public KillMessageModule() {
        super("KillMessage", "Sends a message when you kill someone", Category.MISC);
        addSettings(message, delay);
        instance = this;
    }

    public static KillMessageModule getInstance() {
        return instance;
    }

    public void onAttack(Entity target) {
        if (!isEnabled()) return;
        if (!(target instanceof PlayerEntity)) return;
        if (target == MinecraftClient.getInstance().player) return;
        attacked.put(target.getId(), System.currentTimeMillis());
    }

    @Override
    protected void onDisable() {
        attacked.clear();
        pending.clear();
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            attacked.clear();
            pending.clear();
            return;
        }

        long now = System.currentTimeMillis();

        Iterator<Map.Entry<Integer, Long>> it = attacked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> entry = it.next();
            long hitAt = entry.getValue();
            long since = now - hitAt;

            if (since > TRACK_WINDOW_MS) {
                it.remove();
                continue;
            }

            Entity entity = mc.world.getEntityById(entry.getKey());
            if (entity == null) {

                if (since <= REMOVAL_WINDOW_MS) queueMessage(now);
                it.remove();
            } else if (entity instanceof LivingEntity living && (living.isDead() || living.deathTime > 0)) {
                queueMessage(now);
                it.remove();
            }
        }

        pending.removeIf(sendAt -> {
            if (now >= sendAt) {
                send(mc);
                return true;
            }
            return false;
        });
    }

    private void queueMessage(long now) {
        pending.add(now + delay.getInt() * 1000L);
    }

    private void send(MinecraftClient mc) {
        ClientPlayNetworkHandler net = mc.getNetworkHandler();
        if (net == null) return;
        String text = message.getValue();
        if (text == null || text.isBlank()) return;

        if (text.startsWith("/")) {
            net.sendChatCommand(text.substring(1));
        } else {
            net.sendChatMessage(text);
        }
    }
}
