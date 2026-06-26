package relic.client.client.mixin;

import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import relic.client.command.CommandManager;

import java.util.concurrent.CompletableFuture;

@Mixin(ChatInputSuggestor.class)
public class ChatInputSuggestorMixin {

    @Shadow @Final
    TextFieldWidget textField;

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    public void show(boolean narrateFirstSuggestion) {}

    @Shadow
    public void clearWindow() {}

    @Inject(method = "refresh", at = @At("HEAD"), cancellable = true)
    private void relic$suggestClientCommands(CallbackInfo ci) {
        String text = textField.getText();
        Suggestions suggestions = CommandManager.buildSuggestions(text);
        if (suggestions == null) return;

        textField.setSuggestion(null);
        if (suggestions.isEmpty()) {
            clearWindow();
        } else {
            this.pendingSuggestions = CompletableFuture.completedFuture(suggestions);
            show(false);
        }
        ci.cancel();
    }
}
