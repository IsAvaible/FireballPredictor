# Fireball Predictor — Developer & Agent Guide (Forge 1.8.9)

This document provides a comprehensive technical overview and architecture reference for AI agents and developers working on the **Minecraft 1.8.9 Forge backport** of **Fireball Predictor**.

---

## Codebase Architecture Overview

The mod deterministically simulates and renders projectile trajectories, explosion impacts, block destruction patterns, and combat damage for Minecraft 1.8.9 (Forge).

```
src/main/java/com/simonconrad/fireballpredictor/
├── FireballPredictor.java                # Mod entrypoint & Forge lifecycle
├── client/
│   └── FireballPredictorClient.java      # Client tick tracker & event coordinator
├── config/
│   └── ModConfig.java                    # Forge configuration loader
├── hud/
│   └── HudRenderer.java                  # HUD impact warning badge & cracking hearts
├── math/
│   ├── DamageCalculator.java             # 1.8.9 damage mitigation & knockback pipeline
│   ├── DomeMesh.java                     # Low-poly sphere mesh generator
│   ├── ImpactPredictor.java              # 1,352-ray block destruction simulation
│   └── TrajectoryPredictor.java          # Deterministic projectile kinematics loop
└── render/
    └── PredictionRenderer.java           # 3D immediate-mode ribbon & dome rendering
```

---

## Detailed Module Breakdown

### 1. Mod Entrypoint & Lifecycle
* [FireballPredictor.java](src/main/java/com/simonconrad/fireballpredictor/FireballPredictor.java): Registers the `@Mod` annotation (`modid = "fireballpredictor"`, `version = "1.0.0"`, `acceptedMinecraftVersions = "[1.8.9]"`). During `preInit`, loads [ModConfig.java](src/main/java/com/simonconrad/fireballpredictor/config/ModConfig.java) and registers [FireballPredictorClient.java](src/main/java/com/simonconrad/fireballpredictor/client/FireballPredictorClient.java) to `MinecraftForge.EVENT_BUS` if running on the client.

### 2. Client Tracking & Event Management
* [FireballPredictorClient.java](src/main/java/com/simonconrad/fireballpredictor/client/FireballPredictorClient.java):
  - **Entity Discovery**: In `onClientTick` (`Phase.END`), monitors `mc.theWorld.loadedEntityList` for alive `EntityFireball` instances up to `ModConfig.maxTrackedProjectiles`.
  - **Multiplayer Velocity Delta Derivation**: 1.8.9 servers do not send velocity packets for projectiles after spawn. Derives real velocity per tick from consecutive synced positions (`posX - lastPosX`), ensuring immediate deflection responsiveness.
  - **Dynamic Invalidation (`needsRefresh`)**: Triggers re-simulation if power/danger flags mutate, entity deviates $>0.25$ blocks from predicted path, target block disappears, or new obstructions appear along the path (rescanned every 5 ticks).
  - **Threat & Damage Evaluation**: Sweeps player AABB against path segments for direct hits, checks danger radius proximity, computes damage and knockback via [DamageCalculator.java](src/main/java/com/simonconrad/fireballpredictor/math/DamageCalculator.java), and selects the highest-priority threat.
  - **Block Highlights**: Sends staged and blinking crack progress to `world.sendBlockBreakProgress`.
  - **World Teardown (`resetAll`)**: Clears highlights and tracking maps when unloading worlds or disabling the master switch.

### 3. Trajectory Physics Simulation
* [TrajectoryPredictor.java](src/main/java/com/simonconrad/fireballpredictor/math/TrajectoryPredictor.java):
  - **Kinematics Loop**: Steps tick-by-tick (up to `ModConfig.maxTicks`) mimicking `EntityFireball.onUpdate`.
  - **Collision Raycasting**: Performs `world.rayTraceBlocks` and entity intersection sweeps clamped to the block hit point.
  - **Drag Physics**: Applies `0.95` air drag for large/small fireballs and regular wither skulls, `0.73` air drag for charged wither skulls (`isInvulnerable()`), and `0.80` for water contact (`isTouchingWater`).
  - **Player Intercept Sweep (`findEntityIntercept`)**: Linearly sweeps player bounding box against future trajectory segments for fast direct-hit detection.

### 4. Explosion Block Destruction
* [ImpactPredictor.java](src/main/java/com/simonconrad/fireballpredictor/math/ImpactPredictor.java):
  - **1,352 Directional Rays**: Replicates `Explosion.doExplosionA` ray distribution around the perimeter of a $16 \times 16 \times 16$ cube.
  - **Worst-Case Simulation**: Initializes ray power to $\text{power} \times \text{rayPowerMultiplier}$ (default `1.3F`).
  - **Ray Marching**: Steps by $0.3$ blocks, decays by $0.225$ per step, attenuates power by $(\text{resistance} + 0.3) \times 0.3$.
  - **Charged Wither Skull Cap**: Clamps destructible block blast resistance to $0.8\text{F}$ (`canWitherSkullBreak` protects bedrock, end portal, end portal frame, command block, and barrier).

### 5. 1.8.9 Damage & Knockback Calculator
* [DamageCalculator.java](src/main/java/com/simonconrad/fireballpredictor/math/DamageCalculator.java):
  - **Line-of-Sight Exposure**: Raycasts player bounding box using `world.getBlockDensity`.
  - **Blast Formula**: $\lfloor \frac{d_{10}^2 + d_{10}}{2} \times 8 \times R + 1 \rfloor$ where $d_{10} = (1 - \text{dist}/R) \times \text{density}$.
  - **1.8.9 Pipeline**: Evaluates creative immunity $\to$ fire resistance $\to$ difficulty scaling (Peaceful/Easy/Normal/Hard) $\to$ sword blocking ($-50\%$) $\to$ armor points $\to$ Resistance potion $\to$ Enchantment Protection Factor (EPF capped at 20) $\to$ absorption hearts.
  - **Knockback**: Exposure density reduced by Blast Protection level, converted to blocks/second ($\times 20$).

### 6. Shockwave Dome Geometry
* [DomeMesh.java](src/main/java/com/simonconrad/fireballpredictor/math/DomeMesh.java):
  - Generates a low-poly sphere mesh ($R = \text{power} \times 2$) with 20 latitude $\times$ 24 longitude bands ($480$ quads / $1,920$ vertices).
  - Pre-computes latitude-based alpha profile $82.0 \times 0.70 \times \sin(\pi h)$ for bright equator and soft poles.

### 7. 3D World Rendering
* [PredictionRenderer.java](src/main/java/com/simonconrad/fireballpredictor/render/PredictionRenderer.java):
  - Hooked via `RenderWorldLastEvent` in camera-relative coordinates.
  - Utilizes `GlStateManager`, `Tessellator`, and `WorldRenderer` (`GL11.GL_QUADS`, `DefaultVertexFormats.POSITION_COLOR`).
  - Disables depth writing (`depthMask(false)`) to prevent occlusion of block cracking overlays.
  - Extrudes billboard trajectory ribbons with soft faded edges and quadratic alpha falloff.
  - Renders 3D shockwave dome with dynamic Fresnel rim shading ($F = 0.35 + 0.65 \times (1 - |\hat{n} \cdot \hat{u}|)^2$).

### 8. HUD Overlays & Cracking Hearts
* [HudRenderer.java](src/main/java/com/simonconrad/fireballpredictor/hud/HudRenderer.java):
  - Hooked via `RenderGameOverlayEvent.Post` (`HOTBAR` and `HEALTH`).
  - Renders impact warning badge with projectile item icon, countdown progress bar, and damage/knockback readout.
  - Overlays custom cracking textures over `GuiIngame.renderHealth` slots with absorption damage allocation and blinking pulses.

### 9. Configuration
* [ModConfig.java](src/main/java/com/simonconrad/fireballpredictor/config/ModConfig.java):
  - Manages `.minecraft/config/FireballPredictor.cfg` using Forge's `Configuration` API.
  - Controls ribbon/dome visuals, colors, block highlights, HUD badge offsets, and prediction parameters.

---

## Documentation Sitemap

| Document | Purpose |
|---|---|
| [README.md](README.md) | Mod overview, features, installation, and build summary |
| [docs/trajectory.md](docs/trajectory.md) | Kinematics equations, velocity delta estimation, drag coefficients, refresh triggers |
| [docs/impact.md](docs/impact.md) | 1,352-ray block destruction algorithm, dome geometry, 1.8.9 damage & knockback math |
| [docs/rendering.md](docs/rendering.md) | OpenGL state lifecycle, billboard ribbon extrusion, Fresnel rim shading, HUD rendering |
| [docs/scaffold.md](docs/scaffold.md) | Project structure, ForgeGradle 2.1 build setup, event subscriptions, master comparison |
| [docs/configuration.md](docs/configuration.md) | Complete reference for all `.cfg` options, defaults, and ranges |

---

## Build and Environment Details

* **JDK Target**: Java 8 (JDK 1.8)
* **Gradle Wrapper**: Gradle 2.14
* **ForgeGradle Version**: ForgeGradle 2.1-SNAPSHOT
* **Minecraft Version**: 1.8.9 (`1.8.9-11.15.1.2318-1.8.9`)
* **MCP Mappings**: `stable_22`

### Commands:
```bash
# Setup decompiled workspace
./gradlew setupDecompWorkspace

# Build production jar
./gradlew build

# Run development client
./gradlew runClient
```

---

## Important 1.8.9 Protocol Quirks & Implementation Notes

1. **Velocity Packet Omission**: 1.8.9 multiplayer servers do not send `S12PacketEntityVelocity` for fireballs. Velocity must be derived from consecutive position packets (`t.lastPosX/Y/Z`) to detect deflections.
2. **Explosion Power**: 1.8.9 client cannot read server-side NBT `ExplosionPower` for ghast fireballs; it defaults to `1.0F` ($R = 2$).
3. **No Armor Toughness**: 1.8.9 damage formulas do not use armor toughness or modern combat rules.
4. **No Sweeping / Cooldown**: Combat calculations reflect 1.8.9 spam-click mechanics and sword blocking.
5. **No Dragon Fireballs / Wind Charges**: These projectile types do not exist in Minecraft 1.8.9.
