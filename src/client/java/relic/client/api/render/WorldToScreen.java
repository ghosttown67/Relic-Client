package relic.client.api.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class WorldToScreen {
    private WorldToScreen() {}

    public static float[] project(Vec3d world) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();

        Vector3f rel = new Vector3f(
                (float) (world.x - camPos.x),
                (float) (world.y - camPos.y),
                (float) (world.z - camPos.z));
        Quaternionf inverseView = new Quaternionf(camera.getRotation()).conjugate();
        inverseView.transform(rel);

        if (rel.z >= 0.0f) return null;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        float aspect = (float) mc.getWindow().getFramebufferWidth()
                / (float) mc.getWindow().getFramebufferHeight();

        double fovDeg = mc.options.getFov().getValue();
        float fy = (float) (1.0 / Math.tan(Math.toRadians(fovDeg) / 2.0));
        float fx = fy / aspect;

        float ndcX = (rel.x * fx) / -rel.z;
        float ndcY = (rel.y * fy) / -rel.z;

        float x = (ndcX * 0.5f + 0.5f) * sw;
        float y = (0.5f - ndcY * 0.5f) * sh;
        return new float[]{x, y};
    }
}
