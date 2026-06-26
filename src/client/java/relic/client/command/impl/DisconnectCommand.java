package relic.client.command.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import relic.client.command.Command;
import relic.client.command.CommandManager;

public class DisconnectCommand extends Command {

    public DisconnectCommand() {
        super("disconnect", "Disconnect from the server", "dc");
    }

    @Override
    public void execute(String[] args) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayNetworkHandler net = mc.getNetworkHandler();
        if (net == null) {
            CommandManager.sendMessage("Not connected.");
            return;
        }
        String reason = args.length > 0 ? String.join(" ", args) : "Disconnected";
        net.getConnection().disconnect(Text.literal(reason));
    }
}
