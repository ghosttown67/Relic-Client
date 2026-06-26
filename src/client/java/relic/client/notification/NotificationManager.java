package relic.client.notification;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import relic.client.api.discord.DiscordWebhook;
import relic.client.api.sound.SoundManager;

import java.util.concurrent.CopyOnWriteArrayList;

public final class NotificationManager {

    public static final int DEFAULT_ACCENT = 0xFF6C7BD9;
    public static final long DEFAULT_HOLD_MS = 2000L;

    private static final String SOUND = "/sounds/notification.wav";

    private static final int MARGIN = 6;
    private static final int PAD = 6;
    private static final int ACCENT_BAR = 3;
    private static final int GAP = 4;
    private static final int MAX_CONTENT_W = 200;
    private static final int MAX_ACTIVE = 8;

    private static final int BG_COLOR = 0xE8161620;
    private static final int MSG_COLOR = 0xFFB8B8C8;

    private static NotificationManager instance;

    private final CopyOnWriteArrayList<Notification> active = new CopyOnWriteArrayList<>();

    private NotificationManager() {}

    public static NotificationManager getInstance() {
        if (instance == null) {
            instance = new NotificationManager();
        }
        return instance;
    }

    public void push(String title, String message) {
        push(title, message, DEFAULT_ACCENT, DEFAULT_HOLD_MS, true);
    }

    public void push(String title, String message, int accent, long holdMillis, boolean playSound) {
        active.add(new Notification(title, message, accent, holdMillis));

        while (active.size() > MAX_ACTIVE) {
            active.remove(0);
        }
        if (playSound) {
            SoundManager.play(SOUND);
        }
    }

    public void alert(String title, String message) {
        alert(title, message, DEFAULT_ACCENT, DEFAULT_HOLD_MS, true);
    }

    public void alert(String title, String message, int accent, long holdMillis, boolean playSound) {
        push(title, message, accent, holdMillis, playSound);
        DiscordWebhook.send(title, message, accent);
    }

    public void render(DrawContext context) {
        if (active.isEmpty()) return;

        active.removeIf(Notification::isExpired);

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int screenW = mc.getWindow().getScaledWidth();
        int line = tr.fontHeight + 1;

        int y = MARGIN;
        for (Notification n : active) {
            String title = n.getTitle();
            String message = n.getMessage();

            int contentW = Math.min(MAX_CONTENT_W,
                    Math.max(tr.getWidth(title), n.hasMessage() ? tr.getWidth(message) : 0));
            title = tr.trimToWidth(title, contentW);
            if (n.hasMessage()) message = tr.trimToWidth(message, contentW);

            int cardW = PAD * 2 + ACCENT_BAR + 4 + contentW;
            int cardH = PAD * 2 + line + (n.hasMessage() ? line : 0);

            int offset = Math.round(n.slideFraction() * (cardW + MARGIN));
            int x = screenW - cardW - MARGIN + offset;

            context.fill(x, y, x + cardW, y + cardH, BG_COLOR);
            context.fill(x, y, x + ACCENT_BAR, y + cardH, n.getAccent());

            int textX = x + ACCENT_BAR + 4 + PAD - 2;
            int textY = y + PAD;
            context.drawTextWithShadow(tr, title, textX, textY, n.getAccent());
            if (n.hasMessage()) {
                context.drawTextWithShadow(tr, message, textX, textY + line, MSG_COLOR);
            }

            y += cardH + GAP;
        }
    }
}
