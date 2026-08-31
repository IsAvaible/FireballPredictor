# Project Scaffold & Architecture (Forge 1.8.9)

This document describes the project structure, build configuration, and mod lifecycle for the Minecraft 1.8.9 Forge backport of **Fireball Predictor**.

---

## 1. Project Structure

```
FireballPredictor/
├── .gitignore
├── LICENSE
├── README.md
├── agent.md
├── build.gradle
├── gradle.properties
├── gradlew / gradlew.bat
├── docs/
│   ├── configuration.md
│   ├── impact.md
│   ├── rendering.md
│   ├── scaffold.md
│   └── trajectory.md
└── src/
    └── main/
        ├── java/
        │   └── com/simonconrad/fireballpredictor/
        │       ├── FireballPredictor.java              # Mod entrypoint (@Mod)
        │       ├── client/
        │       │   └── FireballPredictorClient.java    # Tick tracker & event listener
        │       ├── config/
        │       │   └── ModConfig.java                  # Forge configuration loader
        │       ├── hud/
        │       │   └── HudRenderer.java                # Warning badge & hearts overlay
        │       ├── math/
        │       │   ├── DamageCalculator.java           # 1.8.9 damage & knockback math
        │       │   ├── DomeMesh.java                   # Procedural sphere mesh builder
        │       │   ├── ImpactPredictor.java            # 1,352-ray block destruction
        │       │   └── TrajectoryPredictor.java        # Deterministic flight simulation
        │       └── render/
        │           └── PredictionRenderer.java         # Immediate-mode world rendering
        └── resources/
            ├── mcmod.info                              # Forge mod metadata
            └── assets/fireballpredictor/textures/hud/heart/
                ├── cracking_full.png
                ├── cracking_full_blinking.png
                ├── cracking_half.png
                ├── cracking_half_blinking.png
                ├── cracking_half_right.png
                ├── cracking_half_right_blinking.png
                ├── cracking_half_absorbing_right.png
                └── cracking_half_absorbing_right_blinking.png
```

---

## 2. Build & Toolchain Configuration ([build.gradle](../build.gradle))

* **Forge Version**: `1.8.9-11.15.1.2318-1.8.9`
* **ForgeGradle Plugin**: `net.minecraftforge.gradle:ForgeGradle:2.1-SNAPSHOT`
* **MCP Mappings**: `stable_22`
* **Java Target**: Java 8 (`sourceCompatibility = targetCompatibility = '1.8'`)
* **Gradle Wrapper**: Gradle 2.14 (required by ForgeGradle 2.1)

### Build Commands:
```bash
# Setup decompiled Minecraft workspace
./gradlew setupDecompWorkspace

# Build release jar (outputs to build/libs/fireballpredictor-1.8.9-1.0.0.jar)
./gradlew build

# Launch development Minecraft client
./gradlew runClient
```

---

## 3. Mod Lifecycle & Event Flow

```
                     FMLPreInitializationEvent
                               │
                ┌──────────────┴──────────────┐
                ▼                             ▼
        ModConfig.load()             (If client-side)
        Loads .cfg file          MinecraftForge.EVENT_BUS.register(
                                   new FireballPredictorClient()
                                 )
```

### Event Subscribers:

1. **`TickEvent.ClientTickEvent` (Phase.END)**:
   - Discovers new `EntityFireball` instances in `mc.theWorld.loadedEntityList`.
   - Derives projectile velocities from synced position packets.
   - Re-simulates flight paths upon deflection or world alteration.
   - Evaluates threat levels against the player.
   - Updates animated block break progress (`sendBlockBreakProgress`).
2. **`RenderWorldLastEvent`**:
   - Renders 3D camera-facing trajectory ribbons and 3D spherical shockwave blast domes.
3. **`RenderGameOverlayEvent.Post`**:
   - `ElementType.HOTBAR`: Renders the HUD impact warning badge and numerical readout.
   - `ElementType.HEALTH`: Renders the animated cracking overlay on top of health and absorption hearts.

---

## 4. Key Differences from the Modern Fabric (master) Branch

| Feature Area | Modern Fabric (`master` / 26.2) | 1.8.9 Forge Backport |
|---|---|---|
| **Mod Loader** | Fabric Loader | Minecraft Forge 1.8.9 |
| **Damage Pipeline** | Modern CombatRules, Armor Toughness, Enchantment Components | 1.8.9 Armor (`25-armor/25`), Sword Blocking, Classic EPF (cap 20) |
| **Rendering** | Modern RenderPipelines, Iris Shader integration, YACL preview | Immediate mode `GlStateManager`, `Tessellator`, `WorldRenderer` |
| **Multiplayer Sync** | S2C Entity Velocity packets available | Velocity derived from consecutive synced position deltas |
| **Configuration** | YACL3 GUI + live 2D/3D preview gallery | Forge `.cfg` file (`ModConfig.java`) |
| **Themes** | 16 stylized procedural visual themes | Standard classic visualization |
| **Projectiles** | Large/Small Fireballs, Wither Skulls, Wind Charges, Dragon Fireballs | Large Fireballs, Small Fireballs, Wither Skulls (Normal & Charged) |
