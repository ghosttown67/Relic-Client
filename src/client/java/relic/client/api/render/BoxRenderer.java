package relic.client.api.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public final class BoxRenderer {
    private BoxRenderer() {}

    public enum Mode { FILLED, OUTLINED, BOTH }

    public record ColoredBox(Box box, float red, float green, float blue, float alpha) {}

    public record Line(Vec3d from, Vec3d to, float red, float green, float blue, float alpha) {}

    private static final RenderPipeline FILLED_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("relic", "pipeline/esp_filled"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    private static final RenderLayer FILLED_LAYER = RenderLayer.of(
            "relic_esp_filled", RenderSetup.builder(FILLED_PIPELINE).translucent().build());

    private static final double WIDTH_PER_PX = 0.001;

    public static void draw(List<ColoredBox> boxes, Mode mode) {
        draw(boxes, mode, 2.0f, 0.3f);
    }

    public static void draw(List<ColoredBox> boxes, Mode mode, float lineWidth, float fillAlpha) {
        if (boxes.isEmpty()) return;

        Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
        Tessellator tessellator = Tessellator.getInstance();

        if (mode == Mode.FILLED || mode == Mode.BOTH) {
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (ColoredBox b : boxes) drawFilledBox(buffer, b, cam, fillAlpha);
            BuiltBuffer built = buffer.endNullable();
            if (built != null) FILLED_LAYER.draw(built);
        }

        if (mode == Mode.OUTLINED || mode == Mode.BOTH) {
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            double k = WIDTH_PER_PX * lineWidth;
            for (ColoredBox b : boxes) drawOutlinedBox(buffer, b, cam, k);
            BuiltBuffer built = buffer.endNullable();
            if (built != null) FILLED_LAYER.draw(built);
        }
    }

    public static void drawLines(List<Line> lines) {
        drawLines(lines, 1.5f);
    }

    public static void drawLines(List<Line> lines, float width) {
        if (lines.isEmpty()) return;

        Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        double k = WIDTH_PER_PX * width;
        for (Line l : lines) {
            appendLine(buffer,
                    l.from.x - cam.x, l.from.y - cam.y, l.from.z - cam.z,
                    l.to.x - cam.x,   l.to.y - cam.y,   l.to.z - cam.z,
                    k, (int) (l.red * 255), (int) (l.green * 255), (int) (l.blue * 255), (int) (l.alpha * 255));
        }

        BuiltBuffer built = buffer.endNullable();
        if (built != null) FILLED_LAYER.draw(built);
    }

    private static void appendLine(BufferBuilder buffer,
                                   double fx, double fy, double fz,
                                   double tx, double ty, double tz,
                                   double k, int r, int g, int b, int a) {
        double dx = tx - fx, dy = ty - fy, dz = tz - fz;
        double mx = (fx + tx) * 0.5, my = (fy + ty) * 0.5, mz = (fz + tz) * 0.5;

        double px = dy * mz - dz * my;
        double py = dz * mx - dx * mz;
        double pz = dx * my - dy * mx;
        double plen = Math.sqrt(px * px + py * py + pz * pz);
        if (plen < 1e-4) return;
        px /= plen; py /= plen; pz /= plen;

        double wf = k * Math.sqrt(fx * fx + fy * fy + fz * fz);
        double wt = k * Math.sqrt(tx * tx + ty * ty + tz * tz);

        buffer.vertex((float) (fx + px * wf), (float) (fy + py * wf), (float) (fz + pz * wf)).color(r, g, b, a);
        buffer.vertex((float) (fx - px * wf), (float) (fy - py * wf), (float) (fz - pz * wf)).color(r, g, b, a);
        buffer.vertex((float) (tx - px * wt), (float) (ty - py * wt), (float) (tz - pz * wt)).color(r, g, b, a);
        buffer.vertex((float) (tx + px * wt), (float) (ty + py * wt), (float) (tz + pz * wt)).color(r, g, b, a);
    }

    private static void drawFilledBox(BufferBuilder buffer, ColoredBox target, Vec3d cam, float fillAlpha) {
        float x1 = (float) (target.box.minX - cam.x);
        float y1 = (float) (target.box.minY - cam.y);
        float z1 = (float) (target.box.minZ - cam.z);
        float x2 = (float) (target.box.maxX - cam.x);
        float y2 = (float) (target.box.maxY - cam.y);
        float z2 = (float) (target.box.maxZ - cam.z);
        int r = (int) (target.red * 255);
        int g = (int) (target.green * 255);
        int b = (int) (target.blue * 255);
        int a = (int) (target.alpha * fillAlpha * 255);

        buffer.vertex(x1, y1, z1).color(r, g, b, a);
        buffer.vertex(x2, y1, z1).color(r, g, b, a);
        buffer.vertex(x2, y1, z2).color(r, g, b, a);
        buffer.vertex(x1, y1, z2).color(r, g, b, a);

        buffer.vertex(x1, y2, z1).color(r, g, b, a);
        buffer.vertex(x1, y2, z2).color(r, g, b, a);
        buffer.vertex(x2, y2, z2).color(r, g, b, a);
        buffer.vertex(x2, y2, z1).color(r, g, b, a);

        buffer.vertex(x1, y1, z1).color(r, g, b, a);
        buffer.vertex(x1, y2, z1).color(r, g, b, a);
        buffer.vertex(x2, y2, z1).color(r, g, b, a);
        buffer.vertex(x2, y1, z1).color(r, g, b, a);

        buffer.vertex(x1, y1, z2).color(r, g, b, a);
        buffer.vertex(x2, y1, z2).color(r, g, b, a);
        buffer.vertex(x2, y2, z2).color(r, g, b, a);
        buffer.vertex(x1, y2, z2).color(r, g, b, a);

        buffer.vertex(x1, y1, z1).color(r, g, b, a);
        buffer.vertex(x1, y1, z2).color(r, g, b, a);
        buffer.vertex(x1, y2, z2).color(r, g, b, a);
        buffer.vertex(x1, y2, z1).color(r, g, b, a);

        buffer.vertex(x2, y1, z1).color(r, g, b, a);
        buffer.vertex(x2, y2, z1).color(r, g, b, a);
        buffer.vertex(x2, y2, z2).color(r, g, b, a);
        buffer.vertex(x2, y1, z2).color(r, g, b, a);
    }

    private static void drawOutlinedBox(BufferBuilder buffer, ColoredBox target, Vec3d cam, double k) {
        double x1 = target.box.minX - cam.x, y1 = target.box.minY - cam.y, z1 = target.box.minZ - cam.z;
        double x2 = target.box.maxX - cam.x, y2 = target.box.maxY - cam.y, z2 = target.box.maxZ - cam.z;
        int r = (int) (target.red * 255);
        int g = (int) (target.green * 255);
        int b = (int) (target.blue * 255);
        int a = (int) (target.alpha * 255);

        appendLine(buffer, x1, y1, z1, x2, y1, z1, k, r, g, b, a);
        appendLine(buffer, x2, y1, z1, x2, y1, z2, k, r, g, b, a);
        appendLine(buffer, x2, y1, z2, x1, y1, z2, k, r, g, b, a);
        appendLine(buffer, x1, y1, z2, x1, y1, z1, k, r, g, b, a);

        appendLine(buffer, x1, y2, z1, x2, y2, z1, k, r, g, b, a);
        appendLine(buffer, x2, y2, z1, x2, y2, z2, k, r, g, b, a);
        appendLine(buffer, x2, y2, z2, x1, y2, z2, k, r, g, b, a);
        appendLine(buffer, x1, y2, z2, x1, y2, z1, k, r, g, b, a);

        appendLine(buffer, x1, y1, z1, x1, y2, z1, k, r, g, b, a);
        appendLine(buffer, x2, y1, z1, x2, y2, z1, k, r, g, b, a);
        appendLine(buffer, x2, y1, z2, x2, y2, z2, k, r, g, b, a);
        appendLine(buffer, x1, y1, z2, x1, y2, z2, k, r, g, b, a);
    }
}
