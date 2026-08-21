# Fireball Trajectory Prediction

This document describes the trajectory prediction system implemented in the mod. Trajectory calculations are performed entirely client-side to ensure real-time visual updates without generating server overhead.

## Implementation Details

### 1. [TrajectoryPredictor.java](../src/main/java/com/simonconrad/fireballpredictor/math/TrajectoryPredictor.java)
Contains the physics simulation engine that mimics Minecraft's projectile update loops:
- **Tick-by-Tick Simulation**: Steps through the fireball's movement tick-by-tick (up to a maximum of 200 ticks).
- **Collision Checking & Dual Hit Results**: In each simulated tick, it performs raycasts for blocks (`world.clip` with `ClipContext.Block.COLLIDER`) and entities (`ProjectileUtil.getEntityHitResult` with `canHitEntity` via [ProjectileAccessor.java](../src/main/java/com/simonconrad/fireballpredictor/mixin/ProjectileAccessor.java)). Visual trajectory ribbons, shockwave domes, and block destruction continue past entities to show full paths against terrain (`hitResult`), while damage estimation intercepts the first valid entity in the path (`damageHitResult`) to accurately predict direct-hit and splash damage.
- **Dynamic Entity Path Interception (`findDamageHitResult`)**: During flight, entities may step into or out of the fireball's remaining path. On each tick, `TrajectoryPredictor.findDamageHitResult(world, fireball, data)` re-evaluates entity collisions along only the remaining path segments without re-simulating the entire flight path.
- **Dome Boundary Intercept Computation (`computeTrajectoryDomeIntercept`)**: Performs exact ray-sphere segment boundary intersection backwards along the trajectory against the shockwave blast radius centered at `hitPos` to determine the incoming entry point vector on the dome shell, driving directional theme effects (e.g. `ELECTRIC_ARC`) radiating from the penetration point.
- **Physics Equations**: Applies acceleration in the direction of the velocity vector using the fireball's `accelerationPower` field, then applies entity-specific drag derived from its [`ProjectileProfile`](../src/main/java/com/simonconrad/fireballpredictor/projectile/ProjectileProfile.java) via [`VanillaProfiles`](../src/main/java/com/simonconrad/fireballpredictor/projectile/VanillaProfiles.java):
  - **Fireballs & Uncharged Wither Skulls**: Standard drag (`0.95` in air, `0.8` in water).
  - **Charged Wither Skulls**: High drag (`0.73` in air, `0.8` in water).
  - **Wind Charges (`AbstractWindCharge`)**: No drag (`1.0` in air and water).
- **Entity Filtering & Config Toggles**: Uses [`ProjectileFilterKey`](../src/main/java/com/simonconrad/fireballpredictor/projectile/ProjectileFilterKey.java) and [ModConfig.java](../src/main/java/com/simonconrad/fireballpredictor/config/ModConfig.java) to toggle tracking for specific entity types (`trackFireballs`, `trackWitherSkulls`, `trackWindCharges`) and inferred owner categories (`BLAZE`, `GHAST`, `ENDER_DRAGON`, `WITHER`, `PLAYER`, `DISPENSER`, `COMMAND`).
- **Asynchronous Execution Split**: Calculates predictions in two distinct phases:
  - **Simulation Phase (Main Thread)**: Quickly runs the 200-tick flight path raycast and captures a thread-safe `BlockStateSnapshot` at the collision point.
  - **Prediction Phase (Background Thread)**: Submits calculations for the detailed broken blocks list (`ImpactPredictor.predictBrokenBlocks`) and rendering dome mesh generation to a background worker thread.

### 2. [TrackedProjectile.java](../src/main/java/com/simonconrad/fireballpredictor/client/tracking/TrackedProjectile.java) & [FireballPredictorClient.java](../src/main/java/com/simonconrad/fireballpredictor/client/FireballPredictorClient.java)
- **Lifecycle Container**: `TrackedProjectile` wraps each active `ExplosiveProjectileEntity` on the client.
- **Filter & Restriction Mask Evaluation**: Checks `ModConfig` owner/entity toggles against `InferenceResult` and combines them with `ServerTrackingRules.isAllowed(owner)`.
- **Daemon Thread Executor**: Manages a background single-thread executor `"FireballPredictor-Worker"`.
- **Deduplicated Updates**: Tracks an `isCalculating` flag for each active `TrackedProjectile` to prevent queueing redundant simulation tasks if a task is already running.
- **Main Thread Safe Sync**: Once background calculations complete, applies the resulting `PredictionData` back to the main thread via the client's thread-safe executor (`client.execute()`).
- **Dynamic Recalculation Cache Invalidation**: Tracks cached parameters (explosion power, `isCharged()` state, owner attribution, ray power multiplier snapshot). If a parameter changes or a block update occurs near the path, it schedules an immediate recalculation.
- **Threat Assessment & Ranking (`isThreateningPlayer`)**: Evaluates incoming projectiles using direct hit checks, detonation proximity to the player within the blast danger radius, and trajectory proximity with short-term player velocity extrapolation. Ranks threats by maximum damage to drive the HUD warning badge and cracking hearts overlay.
- Cleans up tracking data when fireballs are destroyed or unloaded.

## Validation Results

- Compiles and runs successfully under Minecraft `26.2` using Fabric API and official Mojang mappings.
- The trajectory calculation aligns exactly with vanilla physics, ensuring predicted impact locations match the actual detonation points.
- Physics equations correctly handle varying speeds and custom `accelerationPower` properties.