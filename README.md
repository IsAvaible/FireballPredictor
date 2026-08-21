# Fireball Predictor

A client-side Fabric mod for Minecraft 26.2 and 1.21.11 that visualizes the trajectory and impact of fireballs, wither skulls, and wind charges in real-time.

[![Fireball Predictor Video](https://img.youtube.com/vi/_VXAXr188n0/maxresdefault.jpg)](https://www.youtube.com/watch?v=_VXAXr188n0)


[![Modrinth Downloads](https://img.shields.io/modrinth/dt/fireball-predictor?logo=modrinth&logoColor=white&label=Modrinth)](https://modrinth.com/mod/fireball-predictor)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1613094?logo=curseforge&logoColor=white&label=CurseForge)](https://www.curseforge.com/minecraft/mc-mods/fireball-predictor/)

## What it does

Fireball Predictor calculates exactly where explosive projectiles will land and what damage they will do before they hit. 

By syncing explosion power from the server to the client, the mod deterministically simulates the projectile's kinematics and replicates vanilla Minecraft's explosion raycasting algorithm. The result is a highly accurate, real-time prediction rendered directly in the world.

## Features

- **Trajectory Ribbon:** A translucent trail showing the projectile's calculated flight path, accounting for drag and acceleration.
- **Shockwave Dome:** Visualizes the predicted explosion radius at the exact point of impact.
- **Block Highlights:** Highlights the specific blocks that will be destroyed by the explosion.
- **Impact Warning Badge:** On-screen HUD warning badge with a countdown timer when on a collision course.
- **Cracking Damage Hearts Overlay:** Overlays your health bar with animated cracking hearts showing the exact health predicted to be lost, fully accounting for distance falloff, line-of-sight cover, armor, Protection/Blast Protection enchantments, absorption hearts, and Resistance effects.
- **Damage & Knockback Readout:** Live HUD readout displaying the predicted heart loss and initial knockback velocity (in blocks/second) for the most threatening incoming projectile.
- **Multiple Projectiles:** Supports fireballs, uncharged and charged wither skulls, wind charges, and dragon fireballs.
- **Smart Owner Tracking:** Infers which mob, player, dispenser, or command spawned each projectile and lets you filter highlights per source.
- **Server Enforcement:** Server owners can disable tracking of "other"-source projectiles (players, dispensers, commands) for every connected client, either for the whole group or per sub-option.
- **Configurable:** Tweak visual settings, colors, owner filters, HUD badge anchors, and damage overlay toggles through an in-game mod menu with live previews.

## Fair Play
Some servers might classify this mod as Extra-Sensory Perception (ESP), which is a bannable offense on competitive networks. Always play fair and only use this mod on servers where it is explicitly allowed.

## Server Configuration

When the mod is installed on a server, it creates `config/fireballpredictor-server.json` on first start. Server owners can use it to disable prediction tracking of "other"-source projectiles (e.g. in PvP contexts where predicting player-owned fireballs is unwanted) for all mod users on the server:

```json
{
  "disableOtherOwnerTracking": false,
  "disablePlayerTracking": false,
  "disableDispenserTracking": false,
  "disableCommandTracking": false
}
```

- `disableOtherOwnerTracking`: master switch — disables the whole "other" owner group (player, dispenser, command projectiles).
- `disablePlayerTracking` / `disableDispenserTracking` / `disableCommandTracking`: sub-options to disable individual sources.

Restrictions are pushed to clients when they join and override their local settings. After editing the file, run `/fireballpredictor reload` (operator permission) to apply changes to all connected players without a restart.

## Requirements & Compatibility

| Side | Requirement | Details |
| --- | --- | --- |
| **Minecraft** | `26.2` / `1.21.11` | Fabric Loader `>=0.19.3` |
| **Java** | `Java 25` | Gradle toolchain target |
| **Required Mods** | [Fabric API](https://modrinth.com/mod/fabric-api) | Core networking and lifecycle hooks |
| | [YetAnotherConfigLib (YACL v3)](https://modrinth.com/mod/yacl) | `>=3.9.6` — In-game config screen & live previews |
| **Optional Mods** | [ModMenu](https://modrinth.com/mod/modmenu) | In-game configuration screen button |
| | [Iris Shaders](https://modrinth.com/mod/iris) + [Sodium](https://modrinth.com/mod/sodium) | Soft-loaded fullbright translucent pipeline integration |

## Setup & Build Instructions

This mod requires **Java 25** and uses the Gradle toolchain.

```bash
# Build the mod JAR
./gradlew build

# Run the client development environment
./gradlew runClient

# Run the client development environment with Iris & Sodium activated
./gradlew runClientWithShaders

# Run the server development environment
./gradlew runServer

# Run the automated GameTest suite
./gradlew runGameTest
```

Consult the [/docs](docs) directory for more technical details.

## Publishing a New Release

To publish a new version to **[Modrinth](https://modrinth.com/mod/fireball-predictor)**, **[CurseForge](https://www.curseforge.com/minecraft/mc-mods/fireball-predictor/)**, and **[GitHub Releases](https://github.com/IsAvaible/FireballPredictor/releases)**:

1. Update `mod_version` in [gradle.properties](gradle.properties) and add the version release notes under [CHANGELOG.md](CHANGELOG.md).
2. Commit and push a release tag matching `v*`:
   ```bash
   git commit -am "Prepare release v1.3.0"
   git tag v1.3.0+26.2
   git push origin v1.3.0+26.2
   ```
The GitHub Actions workflow will automatically build the mod and publish the binary and changelog to Modrinth, CurseForge, and GitHub Releases.
