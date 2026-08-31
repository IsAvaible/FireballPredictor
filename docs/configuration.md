# Configuration Guide (Forge 1.8.9)

This document details the configuration options available for the Minecraft 1.8.9 Forge backport of **Fireball Predictor**.

---

## 1. Configuration File

Settings are saved in the standard Forge configuration format at:
```
.minecraft/config/FireballPredictor.cfg
```

The configuration is managed by [ModConfig.java](../src/main/java/com/simonconrad/fireballpredictor/config/ModConfig.java) and loaded during Forge's `FMLPreInitializationEvent`.

---

## 2. Configuration Options

### 2.1 General (`general`)

| Option | Type | Default | Description |
|---|---|---|---|
| `masterEnabled` | `boolean` | `true` | Master toggle for all mod features, event listeners, and rendering. |

### 2.2 Trajectory Ribbon (`trajectory`)

| Option | Type | Default | Range | Description |
|---|---|---|---|---|
| `renderTrajectory` | `boolean` | `true` | `true` / `false` | Enables or disables the 3D in-world trajectory ribbon. |
| `trajectoryWidth` | `float` | `0.5` | `0.1` – `2.0` | Width of the trajectory ribbon in blocks (master default `0.5`; configs still on the old `0.12` default are migrated once). |
| `trajectoryColor` | `String` | `FF8000` | Hex RRGGBB | Hexadecimal color code for the trajectory ribbon (default: vibrant orange). |
| `trajectoryStyle` | `String` | `solid` | `solid` / `dashed` / `core_only` | Ribbon style: soft shroud + bright core, dashed variant, or core strip only. |
| `renderCoreGlow` | `boolean` | `true` | `true` / `false` | Extra bright core layer on top of the soft outer shroud (master's `renderCoreGlow`). |
| `enableRibbonPulse` | `boolean` | `true` | `true` / `false` | Subtle travelling brightness wave along the ribbon (master's `enableRibbonPulse`). |

### 2.3 Shockwave Blast Dome (`dome`)

| Option | Type | Default | Range | Description |
|---|---|---|---|---|
| `renderShockwaveDome` | `boolean` | `true` | `true` / `false` | Enables or disables the 3D spherical shockwave blast dome at the predicted impact point. |
| `domeColor` | `String` | `FF8000` | Hex RRGGBB | Hexadecimal color code for the shockwave blast dome. |
| `domeFresnelStrength` | `float` | `0.3` | `0.0` – `1.0` | Strength of the fresnel rim shading; the rim glow keeps the dome visible from the inside. |

### 2.4 Block Destruction Highlights (`blocks`)

| Option | Type | Default | Description |
|---|---|---|---|
| `renderBlockHighlights` | `boolean` | `true` | Highlights blocks predicted to be broken using animated, staged vanilla cracking overlays. |

### 2.5 Heads-Up Display (`hud`)

| Option | Type | Default | Range | Description |
|---|---|---|---|---|
| `renderImpactWarning` | `boolean` | `true` | `true` / `false` | Shows the threat warning badge when a projectile is heading towards the player. |
| `renderDamageText` | `boolean` | `true` | `true` / `false` | Shows the numerical hearts lost (e.g. `-4.5❤`) and knockback speed (e.g. `12.0b/s`) next to the badge. |
| `renderHeartsOverlay` | `boolean` | `true` | `true` / `false` | Displays cracking damage overlay animations directly over player health / absorption hearts. |
| `badgeOffsetX` | `int` | `0` | `-1000` – `1000` | Horizontal pixel offset for the HUD warning badge. |
| `badgeOffsetY` | `int` | `0` | `-1000` – `1000` | Vertical pixel offset for the HUD warning badge. |

### 2.6 Owner Tracking Filters (`tracking`)

| Option | Type | Default | Description |
|---|---|---|---|
| `trackMobProjectiles` | `boolean` | `true` | Track projectiles fired by hostile mobs (ghast, blaze, wither). |
| `trackOtherOwnerProjectiles` | `boolean` | `true` | Master for the non-mob source group (player, dispenser, command). |
| `trackPlayerProjectiles` | `boolean` | `true` | Track projectiles fired (or deflected) by players. |
| `trackDispenserProjectiles` | `boolean` | `true` | Track dispenser-fired projectiles. |
| `trackCommandProjectiles` | `boolean` | `true` | Track command-summoned / unmatched projectiles. |

These client filters are enforced **in addition** to the server-pushed restrictions from [docs/server.md](server.md); a server can always restrict further (`config/fireballpredictor-server.json` + `/fireballpredictor reload`), never widen.

### 2.7 Prediction Parameters (`prediction`)

| Option | Type | Default | Range | Description |
|---|---|---|---|---|
| `rayPowerMultiplier` | `float` | `1.3` | `0.7` – `1.3` | Multiplier for explosion destruction ray power. Vanilla uses a random float between `0.7` and `1.3`; `1.3` represents the worst-case upper bound. |
| `maxTrackedProjectiles` | `int` | `16` | `1` – `64` | Maximum number of simultaneously tracked projectiles in the loaded world. |
| `maxTicks` | `int` | `200` | `20` – `600` | Maximum lookahead tick limit for trajectory simulation (200 ticks = 10 seconds). |
