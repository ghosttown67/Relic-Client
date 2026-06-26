package relic.client.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;

@Environment(EnvType.CLIENT)
public final class FreeVersionNotice {

    private static final String DISCORD_INVITE = "https://discord.gg/JruewPZDDy";

    private static boolean shownThisLaunch = false;

    private FreeVersionNotice() {}

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (shownThisLaunch) return;
            shownThisLaunch = true;
            client.execute(() -> show(client));
        });
    }

    private static void show(MinecraftClient client) {
        if (client.player == null) return;

        client.player.sendMessage(Text.literal(""), false);
        client.player.sendMessage(
                Text.literal("Relic Client").setStyle(Style.EMPTY.withColor(Formatting.AQUA).withBold(true))
                        .append(Text.literal(" (Free version)").setStyle(Style.EMPTY.withColor(Formatting.GRAY))),
                false);
        client.player.sendMessage(
                Text.literal("You are using the free version of Relic Client.")
                        .setStyle(Style.EMPTY.withColor(Formatting.WHITE)),
                false);

        Style link = Style.EMPTY
                .withColor(Formatting.AQUA)
                .withUnderline(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(DISCORD_INVITE)))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to open the Discord invite")));

        MutableText discord = Text.literal("Join our Discord: ")
                .setStyle(Style.EMPTY.withColor(Formatting.GRAY))
                .append(Text.literal(DISCORD_INVITE).setStyle(link));
        client.player.sendMessage(discord, false);

        client.player.sendMessage(Text.literal(""), false);
    }
}
