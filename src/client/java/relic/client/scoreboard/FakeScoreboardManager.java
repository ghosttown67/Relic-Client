package relic.client.scoreboard;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import relic.client.module.impl.troll.FakeScoreboardModule;

import java.util.ArrayList;
import java.util.List;

public final class FakeScoreboardManager {

    private FakeScoreboardManager() {}

    private static final String OBJECTIVE_NAME = "relic_fake_sidebar";
    private static final String TEAM_PREFIX = "relic_sb_";

    private static final int MONEY  = 0x00FF00;
    private static final int SHARDS = 0xA503FC;
    private static final int KILLS  = 0xFF0000;
    private static final int DEATHS = 0xFC7703;
    private static final int TIMER  = 0xFFE600;

    private static ScoreboardObjective fakeObjective;
    private static ScoreboardObjective savedObjective;
    private static Scoreboard lastScoreboard;
    private static final List<String> teamNames = new ArrayList<>();
    private static String lastFingerprint = "";

    public static boolean isActive() {
        FakeScoreboardModule module = FakeScoreboardModule.getInstance();
        return module != null && module.isEnabled();
    }

    public static boolean isFakeObjective(ScoreboardObjective objective) {
        return objective != null && OBJECTIVE_NAME.equals(objective.getName());
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            reset();
            return;
        }
        if (!isActive()) {
            restoreOriginal(mc.world.getScoreboard());
            return;
        }

        Scoreboard scoreboard = mc.world.getScoreboard();
        if (scoreboard != lastScoreboard) {
            reset();
            lastScoreboard = scoreboard;
        }

        ScoreboardObjective current = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (savedObjective == null && current != null && !isFakeObjective(current)) {
            savedObjective = current;
        }

        String fingerprint = buildFingerprint();
        if (fakeObjective != null && fingerprint.equals(lastFingerprint)) {

            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, fakeObjective);
            return;
        }
        rebuild(scoreboard, fingerprint);
    }

    public static void reset() {
        if (lastScoreboard != null) {
            restoreOriginal(lastScoreboard);
        }
        fakeObjective = null;
        savedObjective = null;
        lastScoreboard = null;
        teamNames.clear();
        lastFingerprint = "";
    }

    private static void rebuild(Scoreboard scoreboard, String fingerprint) {
        cleanup(scoreboard);
        if (fakeObjective != null) {
            try { scoreboard.removeObjective(fakeObjective); } catch (Exception ignored) {}
        }

        fakeObjective = scoreboard.addObjective(
                OBJECTIVE_NAME,
                ScoreboardCriterion.DUMMY,
                Text.literal(title()),
                ScoreboardCriterion.RenderType.INTEGER,
                false,
                (NumberFormat) BlankNumberFormat.INSTANCE);
        scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, fakeObjective);

        List<MutableText> entries = entries();
        for (int i = 0; i < entries.size(); i++) {
            String teamName = TEAM_PREFIX + i;
            teamNames.add(teamName);

            Team existing = scoreboard.getTeam(teamName);
            if (existing != null) scoreboard.removeTeam(existing);
            Team team = scoreboard.addTeam(teamName);
            team.setPrefix(entries.get(i));

            String holderName = "§" + Integer.toHexString(i);
            ScoreHolder holder = ScoreHolder.fromName(holderName);
            scoreboard.removeScore(holder, fakeObjective);
            ScoreAccess score = scoreboard.getOrCreateScore(holder, fakeObjective);
            score.setScore(entries.size() - i);
            scoreboard.addScoreHolderToTeam(holderName, team);
        }
        lastFingerprint = fingerprint;
    }

    private static void restoreOriginal(Scoreboard scoreboard) {
        if (scoreboard == null) return;
        cleanup(scoreboard);
        if (fakeObjective != null) {
            try { scoreboard.removeObjective(fakeObjective); } catch (Exception ignored) {}
        }
        ScoreboardObjective slot = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (slot == null || isFakeObjective(slot)) {
            scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, savedObjective);
        }
        fakeObjective = null;
        savedObjective = null;
        lastFingerprint = "";
    }

    private static void cleanup(Scoreboard scoreboard) {
        for (String teamName : new ArrayList<>(teamNames)) {
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                try { scoreboard.removeTeam(team); } catch (Exception ignored) {}
            }
        }
        teamNames.clear();
    }

    private static List<MutableText> entries() {
        FakeScoreboardModule m = FakeScoreboardModule.getInstance();
        List<MutableText> lines = new ArrayList<>();

        if (m.showMoney())  lines.add(row(m.getMoneyIcon(), m.getMoneyDisplay(), MONEY));
        if (m.showShards()) lines.add(row(m.getShardsIcon(), m.getShards(), SHARDS));
        if (m.showKills())  lines.add(row(m.getKillsIcon(), m.getKills(), KILLS));
        if (m.showDeaths()) lines.add(row(m.getDeathsIcon(), m.getDeaths(), DEATHS));
        if (m.showTimer())  lines.add(row(m.getTimerIcon(), m.getTimer(), TIMER));
        return lines;
    }

    private static MutableText row(String icon, String value, int accent) {
        return colored(icon + " ", accent).append(colored(emptyToDash(value), 0xFFFFFF));
    }

    private static String buildFingerprint() {
        FakeScoreboardModule m = FakeScoreboardModule.getInstance();
        return title() + "|" + m.getMoneyDisplay() + "|" + m.getShards() + "|" + m.getKills() + "|"
                + m.getDeaths() + "|" + m.getTimer() + "|"
                + m.getMoneyIcon() + m.getShardsIcon() + m.getKillsIcon() + m.getDeathsIcon() + m.getTimerIcon() + "|"
                + m.showMoney() + m.showShards() + m.showKills() + m.showDeaths() + m.showTimer();
    }

    private static String title() {
        FakeScoreboardModule m = FakeScoreboardModule.getInstance();
        String t = m == null ? null : m.getTitle();
        if (t != null && !t.isBlank()) return t.trim();
        var player = MinecraftClient.getInstance().player;
        return player != null ? player.getName().getString() : "";
    }

    private static String emptyToDash(String value) {
        return value == null || value.isBlank() ? "---" : value.trim();
    }

    private static MutableText colored(String text, int rgb) {
        return Text.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }
}
