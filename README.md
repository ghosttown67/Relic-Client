# Relic Client

A Fabric utility client for **Minecraft 1.21.11**, built around an ImGui ClickGUI with a
modular feature system, alt-account management, and a privacy-focused toolset.

> Open the ClickGUI in-game with **Right Shift** (rebindable). Chat commands use the `.` prefix.

## Discord

Join the community for support, updates, and builds: **https://discord.gg/JruewPZDDy**

## Features

### Combat
- **AimAssist** — Aims at players for sword combat; pairs with TriggerBot.
- **TriggerBot** — Attacks the entity under your crosshair.
- **AutoLog** — Disconnects when your health drops too low.

### Visual
- **ESP** — Highlights selected entities.
- **BlockESP** — Highlights selected blocks in the world.
- **StorageESP** — Highlights storage blocks.
- **HoleESP** — Highlights holes, tunnels and staircases.
- **Tracers** — Draws lines to entities.
- **Nametags** — Shows player name, health, ping, gear and item tags.
- **Trajectories** — Predicts projectile flight paths.
- **DamageTag** — Shows floating damage numbers when you hit.
- **Xray** — See ores through terrain.
- **Fullbright** — Lights the world up as if it were fully lit.
- **Freecam** — Detaches the camera from your player.
- **Zoom** — Magnifies the view; scroll the mouse wheel to adjust.
- **Ghost Mode** — Keep playing after you die: hides the death screen and spoofs your health client-side.
- **PaperDoll** — Shows your player model with armour, plus vanilla stat icons.
- **InventoryHUD** — Shows your inventory on screen.
- **Media Controller** — Now-playing overlay with media controls.
- **RegionMap** — DonutSMP region map with live player tracking.
- **HUD** — Configurable client information overlay.

### Base Hunting
- **ChunkFinder** — Highlights chunk anomalies left by player activity.
- **OreSim** — Simulates ore generation from a known seed.
- **BlockAlert** — Notifies when selected blocks are detected nearby.
- **AmethystESP** — Flags amethyst buds from block-update packets.
- **EndermanBlockESP** — Highlights endermen carrying selected blocks.
- **ChickenPatternESP** — Flags chunks where chickens and dropped eggs cluster.
- **SheepPatternESP** — Flags chunks where sheep have grazed grass into dirt.

### Privacy
- **NameProtect** — Hides your username and skin behind a chosen identity.
- **CoordObfuscator** — Masks F3 coordinates and the server IP for screenshots.
- **Hotbar Obfuscator** — Disguises your items in the hotbar and inventory.
- **Block Obfuscator** — Disguises the listed blocks as deepslate.

### Exploit
- **Blink** — Holds outgoing movement packets, releasing them when disabled.
- **PacketCanceller** — Drops selected outgoing/incoming packets.
- **XCarry** — Carry items in the crafting grid by blocking inventory close.

### Misc
- **AutoReconnect** — Reconnects to the last server after a disconnect.
- **AutoFirework** — Re-uses a firework when the last boost fades while gliding.
- **InventoryMove** — Move around while a GUI (inventory/container) is open.
- **SetHome** — Silently sets a home and hides the confirmation message.
- **CoordCopy** — Silently copies your coords to the clipboard and a file.
- **ProximityAlert** — Alerts when a new player enters render distance.
- **RainNotifier** — Notifies when it starts or stops raining.
- **KillMessage** — Sends a message when you kill someone.
- **DiscordRPC** — Shows Relic Client on your Discord profile.

### Troll
- **FakeScoreboard** — Spoofs the DonutSMP sidebar client side.

### Account Manager
- Manage multiple Minecraft accounts and swap sessions in-game via the **Accounts** button on the
  Multiplayer screen. Supports Microsoft login and session-based accounts.

## Commands

Chat commands are prefixed with `.`

| Command | Description |
|---|---|
| `.clearchat` | Clears the chat |
| `.disconnect` | Disconnects from the current server |
| `.dismount` | Dismounts your current entity |
| `.drop` | Drops items |
| `.hclip` | Horizontal clip through blocks |
| `.vclip` | Vertical clip through blocks |
| `.panic` | Disables all modules |
| `.say` | Sends a chat message |

## Building

Requires **JDK 21**.

```bash
./gradlew build
```

The compiled mod jar is written to `build/libs/`. Drop it into your `mods/` folder alongside
[Fabric Loader](https://fabricmc.net/) and [Fabric API](https://modrinth.com/mod/fabric-api).

For development setup, see the [Fabric documentation](https://docs.fabricmc.net/develop/getting-started/setting-up-a-development-environment).

## License

Released under the CC0 license — see [LICENSE](LICENSE).
