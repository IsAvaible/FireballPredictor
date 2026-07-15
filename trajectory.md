# Fireball Trajectory Prediction

This document describes the trajectory prediction system implemented in the mod. Trajectory calculations are performed entirely client-side to ensure real-time visual updates without generating server overhead.

## Implementation Details

### 1. [TrajectoryPredictor.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/math/TrajectoryPredictor.java)
Contains the physics simulation engine that mimics Minecraft's projectile update loops:
- **Tick-by-Tick Simulation**: Steps through the fireball's movement tick-by-tick (up to a maximum of 200 ticks).
- **Collision Checking**: In each simulated tick, it performs raycasts for both blocks (using `world.raycast` and `RaycastContext`) and entities (using `ProjectileUtil.getEntityCollision` with the fireball's bounding box).
- **Physics Equations**: Applies acceleration in the direction of the velocity vector using the fireball's `accelerationPower` field, then applies standard air drag (velocity multiplied by `0.95`).
- **Triggering Impact Predictions**: Once a collision is detected, the simulation stops and triggers `ImpactPredictor.predictBrokenBlocks` to calculate the resulting blast area.

### 2. [FireballPredictorClient.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/client/FireballPredictorClient.java)
- Listens to `ClientTickEvents.END_CLIENT_TICK`.
- Iterates over all active, alive `ExplosiveProjectileEntity` instances in the client world.
- Calculates and stores updated `PredictionData` containing the simulated flight path and collision results.
- Cleans up tracking data when fireballs are destroyed or unloaded.

## Validation Results

- Compiles and runs successfully under Minecraft `1.21.11` using Fabric API and Yarn mappings.
- The trajectory calculation aligns exactly with vanilla physics, ensuring predicted impact locations match the actual detonation points.
- Physics equations correctly handle varying speeds and custom `accelerationPower` properties.