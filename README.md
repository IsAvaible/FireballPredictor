# Fireball Predictor — 1.8.9 Forge backport

A rough, from-scratch backport of [IsAvaible/FireballPredictor](https://github.com/IsAvaible/FireballPredictor)
(a client-side Fabric mod for Minecraft 26.2) to **Minecraft 1.8.9 + Forge**.

The original predicts fireball trajectories, explosion impacts, block destruction and damage
and renders all of it in real time. This backport re-implements the core of that feature set
against the 1.8.9 codebase (MCP `stable_22` mappings), porting the *algorithms* rather than
the code — the original uses modern mappings and 26.x APIs which do not exist in 1.8.9.

## What is ported

| Feature | Status |
|---|---|
| Trajectory prediction (ribbon) | ✅ Replicates 1.8.9 `EntityFireball.onUpdate` kinematics (acceleration, 0.95/0.73 drag, water drag, block + entity raycasts) |
| Impact prediction (block & entity collisions) | ✅ |
| Shockwave dome at the impact point | ✅ Low-poly sphere, fresnel-style rim, lat-band alpha profile like the original |
| Explosion block destruction prediction | ✅ Exact 1.8.9 `Explosion.doExplosionA` raycast replica (1352 rays, 0.3 step, 0.225 decay), charged-skull 0.8 resistance cap, `rayPowerMultiplier` = vanilla upper bound |
| Block crack highlights (`destroyBlockProgress`) | ✅ Staged + blinking like the original |
| Impact warning badge (HUD) | ✅ Top-left badge, projectile icon, travel-progress bar |
| Damage & knockback readout | ✅ 1.8.9 pipeline: `(int)((d²+d)/2·8·r+1)`, blocking, difficulty scaling, armor, Resistance, EPF (Protection / Blast / Projectile / Fire Protection), absorption; knockback incl. Blast Protection reduction + Resistance |
| Cracking hearts overlay | ✅ Painted over the vanilla health bar, absorption-first damage allocation, blinking (original 9×9 textures reused, LGPL) |
| Projectile types | ✅ Ghast fireball, blaze small fireball, wither skull (charged drag/resistance). Dragon fireballs and wind charges don't exist in 1.8.9. |
| Refresh on deflection / world change | ✅ Prediction re-simulated when the entity deviates from the path, the impact block disappears, or blocks appear in the path |

## What is intentionally NOT ported

* **Themes / theme animation** (DEFAULT theme visuals only)
* **Config GUI & live previews** (YACL/ModMenu don't exist for 1.8.9; config is the Forge `config/FireballPredictor.cfg`)
* **Server-side config enforcement & networking** (owner/power sync payloads) — the 1.8.9
  client cannot learn ghast-fireball explosion power, so power is read from the fireball's
  own field (always 1 on a vanilla client; a server companion mod could set it)
* **Smart owner inference/filters** — everything hostile is tracked. `shootingEntity` is
  not synced to 1.8.9 clients in multiplayer, so owner filtering is not possible client-side.
* **Iris shader compat, particles, multi-language**

## Known limitations

* **Velocity drift in multiplayer**: 1.8.9 does not sync per-tick projectile velocity, so the
  client estimates it from consecutive synced positions; a deflection causes a brief
  re-convergence (usually ≤ 1 tick).
* **Explosion power**: ghast fireball power defaults to 1 (radius 2 blocks). Servers that
  modify `field_92057_e` (ExplosionPower) are not reflected unless they ship a companion mod.
* **Wither skull direct-hit damage** assumes 8.0 (shooter known) / 5.0 (magic); on a pure
  client the shooter is unknown, so 5.0 is used.
* **Damage is an estimate**: it assumes worst-case explosion ray power (like the original)
  and ignores armor durability loss, fire ticks and the 0.5s damage-cooldown.

## Fair play

The original mod's warning applies: some servers classify trajectory prediction as ESP and
may ban for it. Only use this mod where it is allowed.

## Building

This is a ForgeGradle 2.1 project and requires **JDK 8** and **Gradle 2.14** (FG 2.1 cannot
run on modern Gradle/JDK). On a machine with JDK 8:

```bash
./gradlew setupDecompWorkspace
./gradlew build
```

The built jar lands in `build/libs/fireballpredictor-1.8.9-1.0.0.jar` (reobfuscated, ready for
production). Run `./gradlew runClient` to test in a dev client.
