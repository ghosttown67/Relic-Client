package relic.client.module.impl.privacy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.StringSetting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PrivacyModule extends Module {

    private static PrivacyModule instance;

    private final StringSetting username = new StringSetting("Username", "Player", 16);
    private final BooleanSetting spoofName = new BooleanSetting("Spoof Name", true);
    private final BooleanSetting hideSkin = new BooleanSetting("Hide Skin", true);

    public PrivacyModule() {
        super("NameProtect", "Hides your username and skin behind a chosen identity", Category.PRIVACY);
        addSettings(username, spoofName, hideSkin);
        instance = this;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public static boolean shouldSpoofName() {
        return isActive() && instance.spoofName.isOn() && !instance.username.getValue().isBlank();
    }

    public static String getSpoofName() {
        return instance.username.getValue();
    }

    public static boolean shouldHideSkin() {
        return isActive() && instance.hideSkin.isOn();
    }

    public static String getRealName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.getSession() != null ? mc.getSession().getUsername() : null;
    }

    private static volatile Pattern realPattern;
    private static volatile String patternFor;

    private static Pattern pattern() {
        if (!shouldSpoofName()) return null;
        String real = getRealName();
        if (real == null || real.isBlank()) return null;
        Pattern p = realPattern;
        if (p == null || !real.equals(patternFor)) {
            p = Pattern.compile(Pattern.quote(real), Pattern.CASE_INSENSITIVE);
            realPattern = p;
            patternFor = real;
        }
        return p;
    }

    public static String spoofString(String in) {
        if (in == null || in.isEmpty()) return in;
        Pattern p = pattern();
        if (p == null) return in;
        Matcher m = p.matcher(in);
        if (!m.find()) return in;
        return m.replaceAll(Matcher.quoteReplacement(getSpoofName()));
    }

    public static Text spoofText(Text text) {
        if (text == null) return text;
        Pattern p = pattern();
        if (p == null) return text;
        if (!p.matcher(text.getString()).find()) return text;
        return walk(text, p, Matcher.quoteReplacement(getSpoofName()));
    }

    private static Text walk(Text text, Pattern p, String replacement) {
        TextContent content = text.getContent();
        MutableText out;
        if (content instanceof PlainTextContent.Literal literal) {
            out = Text.literal(p.matcher(literal.string()).replaceAll(replacement));
        } else {
            out = MutableText.of(content);
        }
        out.setStyle(text.getStyle());
        for (Text sibling : text.getSiblings()) {
            out.append(walk(sibling, p, replacement));
        }
        return out;
    }
}
