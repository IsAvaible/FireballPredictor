# Fireball Trajectory Prediction

This document describes the trajectory prediction system implemented in the mod. Trajectory calculations are performed entirely client-side to ensure real-time visual updates without generating server overhead.

## Implementation Details

### 1. [TrajectoryPredictor.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/math/TrajectoryPredictor.java)
Contains the physics simulation engine that mimics Minecraft's projectile update loops:
- **Tick-by-Tick Simulation**: Steps through the fireball's movement tick-by-tick (up to a maximum of 200 ticks).
- **Collision Checking**: In each simulated tick, it performs raycasts for both blocks (using `world.raycast` and `RaycastContext`) and entities (using `ProjectileUtil.getEntityCollision` with the fireball's bounding box).
- **Physics Equations**: Applies acceleration in the direction of the velocity vector using the fireball's `accelerationPower` field, then applies standard air drag (velocity multiplied by `0.95`).
- **Asynchronous Execution Split**: Calculates predictions in two distinct phases:
  - **Simulation Phase (Main Thread)**: Quickly runs the 200-tick flight path raycast and captures a thread-safe `BlockStateSnapshot` at the collision point.
  - **Prediction Phase (Background Thread)**: Submits calculations for the detailed broken blocks list (`ImpactPredictor.predictBrokenBlocks`) and rendering dome mesh generation to a background worker thread.

### 2. [FireballPredictorClient.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/client/FireballPredictorClient.java)
- Listens to `ClientTickEvents.END_CLIENT_TICK`.
- **Daemon Thread Executor**: Manages a background single-thread executor `"FireballPredictor-Worker"`.
- **Deduplicated Updates**: Tracks an `isCalculating` flag for each active `ExplosiveProjectileEntity` to prevent queueing redundant simulation tasks if a task is already running.
- **Main Thread Safe Sync**: Once background calculations complete, applies the resulting `PredictionData` back to the main thread via the client's thread-safe executor (`client.execute()`).
- **Dynamic Recalculation Cache Invalidation**: Tracks the projectile properties (such as the cached explosion power and `isCharged()` states for wither skulls) used in the last successful prediction calculation. If a mismatch is detected, it schedules an immediate recalculation.
- Cleans up tracking data when fireballs are destroyed or unloaded.

## Validation Results

- Compiles and runs successfully under Minecraft `1.21.11` using Fabric API and Yarn mappings.
- The trajectory calculation aligns exactly with vanilla physics, ensuring predicted impact locations match the actual detonation points.
- Physics equations correctly handle varying speeds and custom `accelerationPower` properties.