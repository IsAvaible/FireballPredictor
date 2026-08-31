# Fireball Predictor — 1.8.9 Forge Backport

A client-side backport of fireball predictor to **Minecraft 1.8.9 + Forge**.

The mod deterministically simulates and renders projectile flight paths, impact points, explosion blast domes, destroyed blocks, and combat damage for fireballs and wither skulls in real time.

---

## Features

| Feature | Description | Reference |
|---|---|---|
| **Trajectory Ribbon** | Real-time 3D flight path ribbon with billboard camera extrusion, soft faded edges, and quadratic alpha decay. Replicates 1.8.9 `EntityFireball.onUpdate` kinematics. | [docs/trajectory.md](docs/trajectory.md) |
| **Shockwave Blast Dome** | Low-poly 3D sphere rendered at the predicted detonation point with dynamic Fresnel rim shading and latitudinal alpha profile. | [docs/impact.md](docs/impact.md) |
| **Block Destruction Prediction** | Exact replica of 1.8.9 `Explosion.doExplosionA` (1,352 rays across a $16 \times 16 \times 16$ cube, $0.3$ step, $0.225$ decay, charged wither skull $0.8$ resistance cap). | [docs/impact.md](docs/impact.md) |
| **Block Crack Highlights** | Animated, staged vanilla destruction crack overlays (`sendBlockBreakProgress`) on blocks predicted to be broken. | [docs/rendering.md](docs/rendering.md) |
| **Impact Threat Warning Badge** | HUD badge displaying projectile type icon, flight countdown progress bar, and damage/knockback readout. | [docs/rendering.md](docs/rendering.md) |
| **Cracking Hearts Health Overlay** | Animated cracking overlay rendered directly over the player's health and absorption hearts on the vanilla HUD bar. | [docs/rendering.md](docs/rendering.md) |
| **1.8.9 Damage & Knockback Pipeline** | Precise client-side damage calculation: difficulty scaling $\to$ sword blocking $\to$ armor $\to$ Resistance potion $\to$ Enchantment Protection Factor (EPF) $\to$ absorption hearts. | [docs/impact.md](docs/impact.md) |
| **Multiplayer Velocity Delta Derivation** | Automatically derives real velocity from successive synced position deltas to overcome 1.8.9's lack of server velocity packets for projectiles. | [docs/trajectory.md](docs/trajectory.md) |
| **Deflection & World Invalidation** | Instantly re-simulates trajectory upon player deflections, block destructions, or new obstructions placed in the flight path. | [docs/trajectory.md](docs/trajectory.md) |
| **In-Game Config Screen** | Categorized configuration GUI (toggles, sliders, color & style cyclers) reachable from the Mods list, `/fireballpredictor`, or a keybind; changes apply live. | [docs/configuration.md](docs/configuration.md) |
| **Command-Summoned Projectiles** | NaN-safe position anchoring keeps `/summon`-ed fireballs trackable even when the vanilla client poisons their motion/position. | [docs/trajectory.md](docs/trajectory.md) |
| **Optional Server Component** | Install on a server for authoritative explosion power, velocity/acceleration, owner, tracking-restriction and `mobGriefing` gamerule syncing. | [docs/server.md](docs/server.md) |

---

## Documentation

Comprehensive technical documentation is available in the `docs/` directory:

* **[Trajectory Prediction](docs/trajectory.md)**: Kinematics equations, velocity delta estimation, drag coefficients (0.95 air, 0.73 charged skull, 0.8 water), and refresh triggers.
* **[Impact & Damage Estimation](docs/impact.md)**: 1,352-ray block destruction algorithm, dome geometry, and the full 1.8.9 damage/knockback pipeline.
* **[World & HUD Rendering](docs/rendering.md)**: OpenGL state management, billboard ribbon extrusion, Fresnel rim shading, crack progress, and HUD overlays.
* **[Configuration Guide](docs/configuration.md)**: Detailed breakdown of all `.cfg` options, categories, defaults, and ranges.
* **[Project Architecture & Scaffold](docs/scaffold.md)**: ForgeGradle 2.1 setup, MCP `stable_22` mappings, mod lifecycle, and comparison with the `master` branch.
* **[Developer & Agent Guide](agent.md)**: Technical reference for autonomous agents and contributors.

---

## Configuration

Settings can be edited **in game** through the config screen (Mods list → Config, the
`/fireballpredictor` client command, or the *Open configuration* keybind in Controls)
or directly in `.minecraft/config/FireballPredictor.cfg`:

Servers can additionally install the mod to sync powers/velocities and enforce
tracking restrictions via `config/fireballpredictor-server.json` (see
[docs/server.md](docs/server.md)).

```ini
# General mod switch
general {
    B:masterEnabled=true
}

# Trajectory ribbon visuals
trajectory {
    B:renderTrajectory=true
    S:trajectoryColor=FF8000
    D:trajectoryWidth=0.12
}

# Shockwave blast dome
dome {
    B:renderShockwaveDome=true
    S:domeColor=FF8000
}

# Block destruction
blocks {
    B:renderBlockHighlights=true
}

# HUD warning & damage overlays
hud {
    B:renderImpactWarning=true
    B:renderDamageText=true
    B:renderHeartsOverlay=true
    I:badgeOffsetX=0
    I:badgeOffsetY=0
}

# Prediction parameters
prediction {
    I:maxTrackedProjectiles=16
    I:maxTicks=200
    D:rayPowerMultiplier=1.3
}
```

See [docs/configuration.md](docs/configuration.md) for full configuration details.

---

## Key Differences from Modern Fabric (`master` Branch)

* **Architecture**: Implemented for Minecraft Forge 1.8.9 (Java 8 / ForgeGradle 2.1 / MCP `stable_22`) rather than Fabric on modern Minecraft.
* **Combat Mechanics**: Replicates 1.8.9 combat (classic armor reduction, sword blocking, 1.8.9 EPF calculations, no armor toughness).
* **Rendering**: Utilizes immediate-mode OpenGL with `GlStateManager`, `Tessellator`, and `WorldRenderer`.
* **Visuals & Config**: Focuses on the core `DEFAULT` theme and Forge `.cfg` file rather than YACL3 GUI screens and custom theme shaders.
* **Projectiles**: Supports 1.8.9 projectile entities (Ghast Fireballs, Blaze Fireballs, Normal/Charged Wither Skulls).

---

## Building and Installation

### Requirements
* **Java Development Kit (JDK)**: JDK 8 (Java 1.8)
* **Minecraft**: 1.8.9 with Minecraft Forge installed

### Build from Source
```bash
# Setup decompiled workspace
./gradlew setupDecompWorkspace

# Compile and package release JAR
./gradlew build
```

The compiled mod JAR will be located at:
```
build/libs/fireballpredictor-1.8.9-1.0.0.jar
```

Place the JAR in your `.minecraft/mods` directory.

---

## Fair Play Notice

Some competitive multiplayer servers classify projectile trajectory prediction as ESP and may disallow client-side trajectory mods. Use responsibly and ensure compliance with your server's rules.

---

## License

This project is licensed under the **GNU Lesser General Public License v3.0 (LGPL-3.0)**.
