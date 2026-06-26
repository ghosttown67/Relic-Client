package relic.client.module.impl.visual;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import relic.client.api.utils.TpsTracker;
import relic.client.gui.ImGuiManager;
import relic.client.gui.RelicLogo;
import relic.client.gui.hud.HudElement;
import relic.client.gui.hud.HudProvider;
import relic.client.gui.theme.ThemeManager;
import relic.client.module.Module;
import relic.client.module.ModuleManager;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;

public class HudModule extends Module implements HudProvider {

    private final BooleanSetting watermark  = new BooleanSetting("Watermark", true);
    private final NumberSetting  wmX        = new NumberSetting("Watermark X", 1, 0, 100, false);
    private final NumberSetting  wmY        = new NumberSetting("Watermark Y", 1, 0, 100, false);
    private final BooleanSetting moduleList = new BooleanSetting("Module List", true);
    private final NumberSetting  listX      = new NumberSetting("List X", 99, 0, 100, false);
    private final NumberSetting  listY      = new NumberSetting("List Y", 1, 0, 100, false);
    private final BooleanSetting coords      = new BooleanSetting("Coordinates", true);
    private final BooleanSetting tpsPing     = new BooleanSetting("TPS + Ping", true);
    private final BooleanSetting fps         = new BooleanSetting("FPS", true);
    private final BooleanSetting velocity    = new BooleanSetting("Velocity", true);
    private final BooleanSetting memory      = new BooleanSetting("Memory", true);
    private final BooleanSetting facing      = new BooleanSetting("Facing Direction", true);
    private final BooleanSetting direction   = new BooleanSetting("Direction Vector", true);
    private final NumberSetting  infoX      = new NumberSetting("Info X", 1, 0, 100, false);
    private final NumberSetting  infoY      = new NumberSetting("Info Y", 99, 0, 100, false);

    private long fpsSum;
    private int fpsCount;
    private long lastFpsSampleMs;

    private static final long STAT_INTERVAL_MS = 500;
    private double lastPosX, lastPosZ;
    private long lastSpeedMs;
    private double speedBps;
    private boolean speedInit;
    private long memUsed, memMax;

    public HudModule() {
        super("HUD", "Client information overlay", Category.VISUAL);
        addSettings(watermark, wmX, wmY, moduleList, listX, listY,
                coords, tpsPing, fps, velocity, memory, facing, direction, infoX, infoY);
    }

    @Override
    protected void onEnable() {
        fpsSum = 0;
        fpsCount = 0;
        lastFpsSampleMs = 0;
        speedInit = false;
        speedBps = 0;
    }

    @Override
    public boolean wantsImGuiOverlay() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && !mc.options.hudHidden;
    }

    @Override
    public void onImGuiRender(ImDrawList dl) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        updateStats(mc);

        ImFont font = font();
        float s = (float) mc.getWindow().getScaleFactor();
        float sw = ImGui.getIO().getDisplaySizeX();
        float sh = ImGui.getIO().getDisplaySizeY();
        if (sw <= 0 || sh <= 0) return;

        int accent = col(ThemeManager.get().accent());
        int white = 0xFFFFFFFF;

        if (watermark.isOn()) {
            float[] b = watermarkSize(font, s);
            drawWatermark(dl, font, s, anchor(sw, b[0], wmX), anchor(sh, b[1], wmY), accent);
        }
        if (moduleList.isOn()) {
            List<Module> active = activeModules(font, s);
            float[] b = listSize(font, s, active);
            drawModuleList(dl, font, s, anchor(sw, b[0], listX), anchor(sh, b[1], listY), b[0], active, accent);
        }
        List<String> lines = infoLines(mc);
        if (!lines.isEmpty()) {
            float[] b = infoSize(font, s, lines);
            drawInfo(dl, font, s, anchor(sw, b[0], infoX), anchor(sh, b[1], infoY), lines, white);
        }
    }

    private HudElement wmElement, listElement, infoElement;

    @Override
    public List<HudElement> hudElements() {
        if (wmElement == null) {
            wmElement = part("Watermark", wmX, wmY,
                    (f, s) -> watermarkSize(f, s),
                    (dl, f, s, x, y) -> drawWatermark(dl, f, s, x, y, col(ThemeManager.get().accent())));
            listElement = part("Module List", listX, listY,
                    (f, s) -> listSize(f, s, activeModules(f, s)),
                    (dl, f, s, x, y) -> {
                        List<Module> active = activeModules(f, s);
                        drawModuleList(dl, f, s, x, y, listSize(f, s, active)[0], active, col(ThemeManager.get().accent()));
                    });
            infoElement = part("Info", infoX, infoY,
                    (f, s) -> infoSize(f, s, infoLines(MinecraftClient.getInstance())),
                    (dl, f, s, x, y) -> drawInfo(dl, f, s, x, y, infoLines(MinecraftClient.getInstance()), 0xFFFFFFFF));
        }
        List<HudElement> list = new ArrayList<>();
        if (watermark.isOn()) list.add(wmElement);
        if (moduleList.isOn()) list.add(listElement);
        if (coords.isOn() || tpsPing.isOn() || fps.isOn()
                || velocity.isOn() || memory.isOn() || facing.isOn() || direction.isOn()) list.add(infoElement);
        return list;
    }

    private interface Sizer { float[] size(ImFont font, float s); }

    private interface Drawer { void draw(ImDrawList dl, ImFont font, float s, float x, float y); }

    private HudElement part(String name, NumberSetting px, NumberSetting py, Sizer sizer, Drawer drawer) {
        return new HudElement() {
            @Override public String name() { return name; }
            @Override public float getXPercent() { return px.getValue(); }
            @Override public float getYPercent() { return py.getValue(); }
            @Override public void setPercent(float x, float y) { px.setValue(x); py.setValue(y); }
            @Override public float width(float dispW, float dispH, float scale) { return sizer.size(font(), scale)[0]; }
            @Override public float height(float dispW, float dispH, float scale) { return sizer.size(font(), scale)[1]; }
            @Override public void renderPreview(ImDrawList dl, float x, float y, float dispW, float dispH, float scale) {
                drawer.draw(dl, font(), scale, x, y);
            }
        };
    }

    private static float fontSize(float s) { return 9.5f * s; }
    private static float fontHeight(ImFont font, float s) { return font.calcTextSizeAY(fontSize(s), Float.MAX_VALUE, 0f, "Ay"); }
    private static float lineHeight(ImFont font, float s) { return fontHeight(font, s) + 3 * s; }

    private static float logoSize(float s) { return 26f * s; }

    private float[] watermarkSize(ImFont font, float s) {
        float sz = logoSize(s);
        return new float[]{sz, sz};
    }

    private void drawWatermark(ImDrawList dl, ImFont font, float s, float x, float y, int accent) {
        RelicLogo.draw(dl, x, y, logoSize(s), accent, 0xFFFFFFFF);
    }

    private List<Module> activeModules(ImFont font, float s) {
        float size = fontSize(s);
        return ModuleManager.getInstance().getAllModules().stream()
                .filter(Module::isEnabled)
                .sorted((a, b) -> Float.compare(width(font, size, b.getName()), width(font, size, a.getName())))
                .toList();
    }

    private float[] listSize(ImFont font, float s, List<Module> active) {
        float size = fontSize(s);
        float maxW = 0f;
        for (Module m : active) maxW = Math.max(maxW, width(font, size, m.getName()));
        return new float[]{maxW + 4 * s, active.size() * lineHeight(font, s)};
    }

    private void drawModuleList(ImDrawList dl, ImFont font, float s, float x, float y, float boxW,
                                List<Module> active, int accent) {
        float size = fontSize(s);
        float fontH = fontHeight(font, s);
        float line = lineHeight(font, s);
        float right = x + boxW;
        float ty = y;
        int border = col(ThemeManager.get().border());
        for (Module m : active) {
            String name = m.getName();
            float w = width(font, size, name);
            float left = right - w - 4 * s;
            float top = ty - 1 * s;
            float bottom = ty + fontH + 1 * s;
            dl.addRectFilled(left, top, right, bottom, col(0x90000000));

            dl.addRect(left, top, right, bottom, border, 0f, 0, 1f);
            text(dl, font, size, right - w - 2 * s, ty, accent, name, true);
            ty += line;
        }
    }

    private List<String> infoLines(MinecraftClient mc) {
        List<String> lines = new ArrayList<>();
        if (mc.player == null) return lines;
        if (coords.isOn()) {
            Vec3d pos = mc.player.getEntityPos();
            boolean nether = mc.world != null && mc.world.getRegistryKey() == World.NETHER;
            double owX, owZ, nX, nZ;
            if (nether) {
                owX = pos.x * 8; owZ = pos.z * 8; nX = pos.x; nZ = pos.z;
            } else {
                owX = pos.x; owZ = pos.z; nX = pos.x / 8; nZ = pos.z / 8;
            }
            lines.add(String.format("XYZ: %.0f, %.0f, %.0f", pos.x, pos.y, pos.z));
            lines.add(String.format("Overworld: %.0f, %.0f", owX, owZ));
            lines.add(String.format("Nether: %.0f, %.0f", nX, nZ));
        }
        if (tpsPing.isOn()) {
            int ping = 0;
            if (mc.getNetworkHandler() != null) {
                PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                if (entry != null) ping = entry.getLatency();
            }
            lines.add(String.format("TPS: %.1f   Ping: %dms", TpsTracker.getTps(), ping));
        }
        if (fps.isOn()) {
            int cur = mc.getCurrentFps();
            lines.add(String.format("FPS: %d   Avg: %d", cur, sampleAvgFps(cur)));
        }
        if (velocity.isOn()) {
            lines.add(String.format("Speed: %.2f b/s", speedBps));
        }
        if (memory.isOn()) {
            lines.add(String.format("Memory: %s / %s", fmtMem(memUsed), fmtMem(memMax)));
        }
        if (facing.isOn() || direction.isOn()) {

            double yawRad = Math.toRadians(mc.player.getYaw());
            double dx = -Math.sin(yawRad);
            double dz = Math.cos(yawRad);
            if (facing.isOn()) {
                lines.add("Facing: " + compass(mc.player.getYaw()));
            }
            if (direction.isOn()) {
                lines.add(String.format("Vector: %.2f, %.2f, %.2f", dx, 0.0, dz));
            }
        }
        return lines;
    }

    private void updateStats(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        if (speedInit && now - lastSpeedMs < STAT_INTERVAL_MS) return;

        double x = mc.player.getX();
        double z = mc.player.getZ();
        if (speedInit) {
            double dt = (now - lastSpeedMs) / 1000.0;
            if (dt > 0) speedBps = Math.hypot(x - lastPosX, z - lastPosZ) / dt;
        }
        lastPosX = x;
        lastPosZ = z;
        lastSpeedMs = now;
        speedInit = true;

        Runtime rt = Runtime.getRuntime();
        memUsed = rt.totalMemory() - rt.freeMemory();
        memMax = rt.maxMemory();
    }

    private static String compass(float yaw) {
        String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        int idx = Math.floorMod(Math.round(yaw / 45f), 8);
        return dirs[idx];
    }

    private static String fmtMem(long bytes) {
        double mb = bytes / 1048576.0;
        if (mb >= 1024) {
            double gb = mb / 1024.0;
            return gb == Math.floor(gb) ? String.format("%.0f GB", gb) : String.format("%.1f GB", gb);
        }
        return String.format("%.0f MB", mb);
    }

    private float[] infoSize(ImFont font, float s, List<String> lines) {
        float size = fontSize(s);
        float maxW = 0f;
        for (String l : lines) maxW = Math.max(maxW, width(font, size, l));
        return new float[]{maxW, lines.size() * lineHeight(font, s)};
    }

    private void drawInfo(ImDrawList dl, ImFont font, float s, float x, float y, List<String> lines, int color) {
        float size = fontSize(s);
        float line = lineHeight(font, s);
        float ty = y;
        for (String l : lines) {
            text(dl, font, size, x, ty, color, l, true);
            ty += line;
        }
    }

    private int sampleAvgFps(int cur) {
        long now = System.currentTimeMillis();
        if (now - lastFpsSampleMs >= 1000) {
            fpsSum += cur;
            fpsCount++;
            lastFpsSampleMs = now;
        }
        return fpsCount > 0 ? (int) (fpsSum / fpsCount) : cur;
    }

    private static ImFont font() {
        ImFont f = ImGuiManager.getOverlayFont();
        return f != null ? f : ImGui.getFont();
    }

    private static float anchor(float screen, float content, NumberSetting pct) {
        return (screen - content) * pct.getValue() / 100f;
    }

    private static void text(ImDrawList dl, ImFont font, float size, float x, float y, int col, String s, boolean shadow) {
        if (s.isEmpty()) return;
        int sz = Math.round(size);
        float fx = (float) Math.floor(x), fy = (float) Math.floor(y);
        if (shadow) dl.addText(font, sz, fx + 1, fy + 1, 0xC0000000, s);
        dl.addText(font, sz, fx, fy, col, s);
    }

    private static float width(ImFont font, float size, String s) {
        return font.calcTextSizeAX(size, Float.MAX_VALUE, 0f, s);
    }

    private static int col(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
