package relic.client.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import relic.client.account.AccountManager;
import relic.client.config.ConfigManager;
import relic.client.event.ClientTickEvent;
import relic.client.event.FreeVersionNotice;
import relic.client.event.ModuleEvents;
import relic.client.event.RenderEvent;
import relic.client.gui.screen.AccountsScreen;
import relic.client.keybinds.ModuleKeybinds;

@Environment(EnvType.CLIENT)
public class RelicClientClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {

		ClientTickEvent.register();
		RenderEvent.register();
		ModuleEvents.register();
		ModuleKeybinds.register();

		FreeVersionNotice.register();

		ConfigManager.load();
		AccountManager.getInstance().load();

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ConfigManager.save();
			AccountManager.getInstance().save();
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof MultiplayerScreen) {
				ButtonWidget button = ButtonWidget.builder(Text.literal("Accounts"),
								b -> client.setScreen(new AccountsScreen(screen)))
						.dimensions(6, 6, 100, 20)
						.build();
				Screens.getButtons(screen).add(button);
			}
		});
	}
}
