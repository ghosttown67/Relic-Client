package relic.client.module.impl.visual;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import relic.client.api.media.MediaService;
import relic.client.gui.ImGuiManager;
import relic.client.gui.hud.HudElement;
import relic.client.gui.hud.HudProvider;
import relic.client.gui.theme.ColorTheme;
import relic.client.gui.theme.ThemeManager;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.NumberSetting;

import java.util.List;

public class MediaControllerModule extends Module implements HudProvider {

    private static final int PANEL_H = 60;
    private static final int ART_PAD = 4;
    private static final int PREV = 0, PLAY = 1, NEXT = 2;
    private static final int BUTTON_COUNT = 3;

    private final BooleanSetting albumArt = new BooleanSetting("Album Art", true);

    private final NumberSetting posX  = new NumberSetting("X %", 2, 0, 100, false);
    private final NumberSetting posY  = new NumberSetting("Y %", 6, 0, 100, false);
    private final NumberSetting width = new NumberSetting("Width", 190, 150, 320, true);

    private volatile float[][] buttons = new float[0][];

    private boolean pLeft, pRight, pDown;

    private static MediaControllerModule instance;

    public MediaControllerModule() {
        super("Media Controller", "Now-playing overlay with media controls", Category.MISC);
        addSettings(albumArt, posX, posY, width);
        instance = this;
    }

    public static MediaControllerModule getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        MediaService.getInstance().start();
    }

    private HudElement element;

    @Override
    public List<HudElement> hudElements() {
        if (element == null) {
            element = new HudElement() {
                @Override public String name() { return "Media Controller"; }
                @Override public float getXPercent() { return posX.getValue(); }
                @Override public float getYPercent() { return posY.getValue(); }
                @Override public void setPercent(float x, float y) { posX.setValue(x); posY.setValue(y); }
                @Override public float width(float dispW, float dispH, float scale) { return width.getInt() * scale; }
                @Override public float height(float dispW, float dispH, float scale) { return PANEL_H * scale; }
                @Override public void renderPreview(ImDrawList dl, float x, float y, float dispW, float dispH, float scale) {
                    onImGuiRender(dl);
                }
            };
        }
        return List.of(element);
    }

    @Override
    public boolean wantsImGuiOverlay() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && mc.world != null && !mc.options.hudHidden;
    }

    @Override
    public void onImGuiRender(ImDrawList dl) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;

        MediaService svc = MediaService.getInstance();
        svc.uploadPendingArt();

        ImFont font = ImGuiManager.getOverlayFont();
        if (font == null) font = ImGui.getFont();

        ColorTheme theme = ThemeManager.get();
        int bg = col(theme.bg());
        int accent = col(theme.accent());
        int textCol = col(theme.text());
        int dimText = col(withAlpha(theme.text(), 0xB0));
        int frame = col(theme.frame());
        int border = col(theme.border());
        int buttonOff = col(theme.buttonOff());

        float s = (float) mc.getWindow().getScaleFactor();
        float dispW = ImGui.getIO().getDisplaySizeX();
        float dispH = ImGui.getIO().getDisplaySizeY();
        if (dispW <= 0 || dispH <= 0) return;

        float w = width.getInt() * s;
        float h = PANEL_H * s;
        float x = (dispW - w) * posX.getValue() / 100f;
        float y = (dispH - h) * posY.getValue() / 100f;
        float pad = ART_PAD * s;
        float r = 4f * s;
        float edge = 2f * s;

        float titleSize = 9.5f * s;
        float subSize   = 8f * s;

        double[] mouse = (mc.mouse != null && !mc.mouse.isCursorLocked())
                ? new double[]{mc.mouse.getX(), mc.mouse.getY()} : null;

        dl.addRectFilled(x, y, x + w, y + h, bg, r);
        dl.addRect(x, y, x + w, y + h, border, r);
        dl.addRectFilled(x, y + r, x + edge, y + h - r, accent);

        boolean showArt = albumArt.isOn() && svc.hasArt() && svc.artGlId() != 0;
        float artSize = h - 2 * pad;
        float artX = x + edge + pad;
        float artY = y + pad;
        if (showArt) {
            dl.addRectFilled(artX, artY, artX + artSize, artY + artSize, col(0xFF101010), r * 0.5f);
            drawCoverArt(dl, svc, artX, artY, artSize);
        }

        float contentX = showArt ? artX + artSize + 6 * s : x + 8 * s;
        float contentRight = x + w - 8 * s;
        float contentW = contentRight - contentX;

        String title = trim(font, titleSize, svc.getTitle(), contentW - 8 * s);
        String artist = trim(font, subSize, svc.getArtist(), contentW);
        text(dl, font, titleSize, contentX, y + 6 * s, textCol, title);
        text(dl, font, subSize, contentX, y + 6 * s + titleSize + 2 * s, dimText, artist);

        dl.addCircleFilled(x + w - 7 * s, y + 9 * s, 2f * s,
                svc.isPlaying() ? accent : col(withAlpha(theme.text(), 0x55)));

        long pos = svc.getProgressMs();
        long dur = svc.getDurationMs();
        float rowY = y + 32 * s;
        String cur = formatTime(pos);
        String tot = dur > 0 ? formatTime(dur) : "--:--";
        float curW = textW(font, subSize, cur);
        float totW = textW(font, subSize, tot);
        text(dl, font, subSize, contentX, rowY, dimText, cur);
        text(dl, font, subSize, contentRight - totW, rowY, dimText, tot);
        float barX = contentX + curW + 5 * s;
        float barEnd = contentRight - totW - 5 * s;
        float barH = 3f * s;
        float barY = rowY + subSize / 2f - barH / 2f;
        if (barEnd > barX) {
            dl.addRectFilled(barX, barY, barEnd, barY + barH, frame, barH / 2f);
            if (dur > 0) {
                float ratio = Math.min(1f, pos / (float) dur);
                dl.addRectFilled(barX, barY, barX + (barEnd - barX) * ratio, barY + barH, accent, barH / 2f);
            }
        }

        float bw = 22 * s, bh = 14 * s, gap = 6 * s;
        float by = y + h - bh - 5 * s;
        float[][] rects = new float[BUTTON_COUNT][];
        for (int i = 0; i < BUTTON_COUNT; i++) {
            float rx = contentX + i * (bw + gap);
            boolean hover = mouse != null && mouse[0] >= rx && mouse[0] < rx + bw
                    && mouse[1] >= by && mouse[1] < by + bh;
            dl.addRectFilled(rx, by, rx + bw, by + bh, hover ? accent : buttonOff, r * 0.5f);
            float cx = rx + bw / 2f, cy = by + bh / 2f;
            switch (i) {
                case PREV -> drawSkip(dl, cx, cy, s, textCol, false);
                case PLAY -> { if (svc.isPlaying()) drawPause(dl, cx, cy, s, textCol);
                               else drawPlay(dl, cx, cy, s, textCol); }
                case NEXT -> drawSkip(dl, cx, cy, s, textCol, true);
            }
            rects[i] = new float[]{rx, by, bw, bh};
        }
        buttons = rects;
    }

    private void drawCoverArt(ImDrawList dl, MediaService svc, float boxX, float boxY, float boxSize) {
        int texW = svc.artWidth(), texH = svc.artHeight();
        if (texW <= 0 || texH <= 0) return;
        float scale = Math.min(boxSize / texW, boxSize / texH);
        float dw = Math.max(1f, texW * scale);
        float dh = Math.max(1f, texH * scale);
        float dx = boxX + (boxSize - dw) / 2f;
        float dy = boxY + (boxSize - dh) / 2f;
        dl.addImage(svc.artGlId(), dx, dy, dx + dw, dy + dh);
    }

    private static void tri(ImDrawList dl, float baseX, float cy, float w, float hh, int col, boolean pointRight) {
        float apexX = pointRight ? baseX + w : baseX - w;
        dl.addTriangleFilled(baseX, cy - hh, baseX, cy + hh, apexX, cy, col);
    }

    private static void drawPlay(ImDrawList dl, float cx, float cy, float s, int col) {
        tri(dl, cx - 3.5f * s, cy, 8 * s, 5 * s, col, true);
    }

    private static void drawPause(ImDrawList dl, float cx, float cy, float s, int col) {
        dl.addRectFilled(cx - 4 * s, cy - 5 * s, cx - 1 * s, cy + 5 * s, col);
        dl.addRectFilled(cx + 1 * s, cy - 5 * s, cx + 4 * s, cy + 5 * s, col);
    }

    private static void drawSkip(ImDrawList dl, float cx, float cy, float s, int col, boolean next) {
        float hh = 5 * s, w = 6 * s, ov = 0.75f * s;
        if (next) {
            tri(dl, cx - 7 * s, cy, w, hh, col, true);
            tri(dl, cx - 1 * s - ov, cy, w + ov, hh, col, true);
        } else {
            tri(dl, cx + 7 * s, cy, w, hh, col, false);
            tri(dl, cx + 1 * s + ov, cy, w + ov, hh, col, false);
        }
    }

    private static void text(ImDrawList dl, ImFont font, float size, float x, float y, int col, String s) {
        if (!s.isEmpty()) dl.addText(font, Math.round(size), (float) Math.floor(x), (float) Math.floor(y), col, s);
    }

    private static float textW(ImFont font, float size, String text) {
        return font.calcTextSizeAX(size, Float.MAX_VALUE, 0f, text);
    }

    private static String trim(ImFont font, float size, String text, float maxW) {
        if (text.isEmpty() || textW(font, size, text) <= maxW) return text;
        for (int len = text.length() - 1; len > 0; len--) {
            String sub = text.substring(0, len);
            if (textW(font, size, sub) <= maxW) return sub;
        }
        return "";
    }

    private static String formatTime(long ms) {
        long totalSec = Math.max(0, ms) / 1000;
        return String.format("%d:%02d", totalSec / 60, totalSec % 60);
    }

    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    private static int col(int argb) {
        int a = (argb >> 24) & 0xFF;
        int rr = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | rr;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) return;
        long win = mc.getWindow().getHandle();
        MediaService svc = MediaService.getInstance();

        if (mc.currentScreen == null) {
            boolean left  = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT)  == GLFW.GLFW_PRESS;
            boolean right = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS;
            boolean down  = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_DOWN)  == GLFW.GLFW_PRESS;

            if (left && !pLeft)   svc.previous();
            if (right && !pRight) svc.next();
            if (down && !pDown)   svc.playPause();

            pLeft = left; pRight = right; pDown = down;
        }
    }

    public boolean handleMouseClick(double mx, double my) {
        if (!isEnabled()) return false;
        MediaService svc = MediaService.getInstance();
        float[][] rects = buttons;
        for (int i = 0; i < rects.length; i++) {
            if (hit(rects[i], mx, my)) {
                switch (i) {
                    case PREV -> svc.previous();
                    case PLAY -> svc.playPause();
                    case NEXT -> svc.next();
                }
                return true;
            }
        }
        return false;
    }

    private static boolean hit(float[] r, double mx, double my) {
        return r.length == 4 && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }
}
