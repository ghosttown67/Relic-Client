package relic.client.module.impl.troll;

import relic.client.module.Module;
import relic.client.module.setting.BooleanSetting;
import relic.client.module.setting.StringSetting;
import relic.client.scoreboard.FakeScoreboardManager;

public class FakeScoreboardModule extends Module {

    private static FakeScoreboardModule instance;

    private final StringSetting title = new StringSetting("Title", "");

    private final BooleanSetting showMoney = new BooleanSetting("Show Money", true);
    private final StringSetting moneyIcon = new StringSetting("Money Icon", "$");
    private final StringSetting money = new StringSetting("Money", "2.6M");

    private final BooleanSetting showShards = new BooleanSetting("Show Shards", true);
    private final StringSetting shardsIcon = new StringSetting("Shards Icon", "★");
    private final StringSetting shards = new StringSetting("Shards", "865");

    private final BooleanSetting showKills = new BooleanSetting("Show Kills", true);
    private final StringSetting killsIcon = new StringSetting("Kills Icon", "🗡");
    private final StringSetting kills = new StringSetting("Kills", "57");

    private final BooleanSetting showDeaths = new BooleanSetting("Show Deaths", true);
    private final StringSetting deathsIcon = new StringSetting("Deaths Icon", "☠");
    private final StringSetting deaths = new StringSetting("Deaths", "54");

    private final BooleanSetting showTimer = new BooleanSetting("Show Timer", true);
    private final StringSetting timerIcon = new StringSetting("Timer Icon", "⌚");
    private final StringSetting timer = new StringSetting("Timer", "8d 15h");

    public FakeScoreboardModule() {
        super("FakeScoreboard", "Spoofs the DonutSMP sidebar client-side",
                Category.TROLL);
        addSettings(title,
                showMoney, moneyIcon, money,
                showShards, shardsIcon, shards,
                showKills, killsIcon, kills,
                showDeaths, deathsIcon, deaths,
                showTimer, timerIcon, timer);
        instance = this;
    }

    public static FakeScoreboardModule getInstance() {
        return instance;
    }

    @Override
    public void onTick() {
        FakeScoreboardManager.tick();
    }

    @Override
    protected void onDisable() {
        FakeScoreboardManager.reset();
    }

    public String getTitle() { return title.getValue(); }

    public String getMoneyDisplay() {
        return money.getValue();
    }

    public boolean showMoney()  { return showMoney.isOn(); }
    public boolean showShards() { return showShards.isOn(); }
    public boolean showKills()  { return showKills.isOn(); }
    public boolean showDeaths() { return showDeaths.isOn(); }
    public boolean showTimer()  { return showTimer.isOn(); }

    public String getMoneyIcon()  { return moneyIcon.getValue(); }
    public String getShardsIcon() { return shardsIcon.getValue(); }
    public String getKillsIcon()  { return killsIcon.getValue(); }
    public String getDeathsIcon() { return deathsIcon.getValue(); }
    public String getTimerIcon()  { return timerIcon.getValue(); }

    public String getShards() { return shards.getValue(); }
    public String getKills()  { return kills.getValue(); }
    public String getDeaths() { return deaths.getValue(); }
    public String getTimer()  { return timer.getValue(); }
}
