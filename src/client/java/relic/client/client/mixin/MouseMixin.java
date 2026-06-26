package relic.client.client.mixin;

import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import relic.client.module.impl.visual.FreecamModule;
import relic.client.module.impl.visual.MediaControllerModule;
import relic.client.module.impl.visual.ZoomModule;

@Mixin(Mouse.class)
public class MouseMixin {

    @Redirect(method = "updateMouse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void relic$redirectLook(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY) {
        FreecamModule freecam = FreecamModule.getInstance();
        if (freecam != null && freecam.isEnabled()) {
            freecam.rotate(cursorDeltaX, cursorDeltaY);
        } else {
            player.changeLookDirection(cursorDeltaX, cursorDeltaY);
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void relic$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Mouse self = (Mouse) (Object) this;
        if (!self.isCursorLocked()) return;

        ZoomModule zoom = ZoomModule.getInstance();
        if (zoom != null && zoom.isZooming() && vertical != 0) {
            zoom.onScroll(vertical);
            ci.cancel();
        }
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void relic$mediaControls(long window, MouseInput input, int action, CallbackInfo ci) {
        if (action != InputUtil.GLFW_PRESS || input.button() != InputUtil.GLFW_MOUSE_BUTTON_LEFT) return;

        Mouse self = (Mouse) (Object) this;
        if (self.isCursorLocked()) return;

        MediaControllerModule media = MediaControllerModule.getInstance();
        if (media != null && media.handleMouseClick(self.getX(), self.getY())) {
            ci.cancel();
        }
    }
}
