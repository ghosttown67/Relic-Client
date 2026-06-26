package relic.client.client.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import relic.client.command.CommandManager;

@Mixin(ClientPlayNetworkHandler.class)
public class ChatMessageMixin {

    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void relic$onSendChatMessage(String content, CallbackInfo ci) {
        if (CommandManager.dispatch(content)) {
            ci.cancel();
        }
    }
}
