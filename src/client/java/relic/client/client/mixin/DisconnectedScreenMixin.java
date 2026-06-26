package relic.client.client.mixin;

import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.SimplePositioningWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import relic.client.module.impl.misc.AutoReconnectModule;

@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {

    @Shadow @Final private DirectionalLayoutWidget grid;

    protected DisconnectedScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void relic$addReconnectButtons(CallbackInfo ci) {
        AutoReconnectModule module = AutoReconnectModule.getInstance();
        if (module == null || !module.isEnabled() || !module.canReconnect()) return;

        if (module.hideButtons()) {

            module.onDisconnectScreen(null);
            return;
        }

        DirectionalLayoutWidget row = DirectionalLayoutWidget.horizontal();
        row.spacing(4);
        row.add(ButtonWidget.builder(Text.literal("Reconnect"), b -> module.reconnect())
                .width(98).build());
        ButtonWidget autoButton = row.add(
                ButtonWidget.builder(Text.literal("Auto Reconnect"), b -> module.toggleCountdown())
                        .width(98).build());

        grid.add(row);
        grid.refreshPositions();
        row.forEachChild(this::addDrawableChild);
        SimplePositioningWidget.setPos(grid, getNavigationFocus());

        module.onDisconnectScreen(autoButton);
    }
}
