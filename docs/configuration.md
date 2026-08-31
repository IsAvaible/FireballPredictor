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
| `trajectoryWidth` | `float` | `0.12` | `0.02` – `1.0` | Width of the trajectory ribbon in blocks. |
| `trajectoryColor` | `String` | `FF8000` | Hex RRGGBB | Hexadecimal color code for the trajectory ribbon (default: vibrant orange). |

### 2.3 Shockwave Blast Dome (`dome`)

| Option | Type | Default | Range | Description |
|---|---|---|---|---|
| `renderShockwaveDome` | `boolean` | `true` | `true` / `false` | Enables or disables the 3D spherical shockwave blast dome at the predicted impact point. |
| `domeColor` | `String` | `FF8000` | Hex RRGGBB | Hexadecimal color code for the shockwave blast dome. |

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

### 2.6 Prediction Parameters (`prediction`)

| Option | Type | Default | Range | Description |
|---|---|---|---|---|
| `rayPowerMultiplier` | `float` | `1.3` | `0.7` – `1.3` | Multiplier for explosion destruction ray power. Vanilla uses a random float between `0.7` and `1.3`; `1.3` represents the worst-case upper bound. |
| `maxTrackedProjectiles` | `int` | `16` | `1` – `64` | Maximum number of simultaneously tracked projectiles in the loaded world. |
| `maxTicks` | `int` | `200` | `20` – `600` | Maximum lookahead tick limit for trajectory simulation (200 ticks = 10 seconds). |
