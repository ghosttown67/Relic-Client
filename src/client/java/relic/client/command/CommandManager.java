package relic.client.command;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import relic.client.command.impl.ClearChatCommand;
import relic.client.command.impl.DisconnectCommand;
import relic.client.command.impl.DismountCommand;
import relic.client.command.impl.DropCommand;
import relic.client.command.impl.HClipCommand;
import relic.client.command.impl.PanicCommand;
import relic.client.command.impl.SayCommand;
import relic.client.command.impl.VClipCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public final class CommandManager {

    public static final String PREFIX = ".";

    private static final int ACCENT = 0x6C7BD9;
    private static final int TEXT = 0xB8B8C8;

    private static final List<Command> commands = new ArrayList<>();

    static {
        register(new ClearChatCommand());
        register(new PanicCommand());
        register(new VClipCommand());
        register(new HClipCommand());
        register(new DropCommand());
        register(new DismountCommand());
        register(new DisconnectCommand());
        register(new SayCommand());
    }

    private CommandManager() {}

    public static void register(Command command) {
        commands.add(command);
    }

    public static List<Command> getCommands() {
        return commands;
    }

    public static boolean dispatch(String message) {
        if (message == null || !message.startsWith(PREFIX)) return false;

        String body = message.substring(PREFIX.length()).trim();
        if (body.isEmpty()) return false;

        String[] parts = body.split("\\s+");
        String label = parts[0];
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);

        for (Command command : commands) {
            if (command.matches(label)) {
                try {
                    command.execute(args);
                } catch (Exception e) {
                    sendMessage("Error: " + e.getMessage());
                }
                return true;
            }
        }

        return false;
    }

    public static Suggestions buildSuggestions(String text) {
        if (text == null || !text.startsWith(PREFIX)) return null;

        String body = text.substring(PREFIX.length());

        if (body.contains(" ")) return null;

        String typed = body.toLowerCase(Locale.ROOT);

        StringRange range = StringRange.between(PREFIX.length(), text.length());

        List<Suggestion> matches = new ArrayList<>();
        TreeSet<String> seen = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Command command : commands) {
            addSuggestion(matches, seen, range, command.getName(), command.getDescription(), typed);
            for (String alias : command.getAliases()) {
                addSuggestion(matches, seen, range, alias, command.getDescription(), typed);
            }
        }
        return new Suggestions(range, matches);
    }

    private static void addSuggestion(List<Suggestion> out, TreeSet<String> seen, StringRange range,
                                      String label, String description, String typed) {
        if (!label.toLowerCase(Locale.ROOT).startsWith(typed)) return;
        if (!seen.add(label)) return;
        out.add(new Suggestion(range, label, new LiteralMessage(description)));
    }

    public static void sendMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        client.player.sendMessage(
                Text.literal("[Relic] ").styled(s -> s.withColor(ACCENT))
                        .append(Text.literal(message).styled(s -> s.withColor(TEXT))),
                false);
    }
}
