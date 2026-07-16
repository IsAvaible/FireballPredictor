# Fireball Impact Calculation

This document outlines how the mod predicts which blocks will be destroyed by a fireball's explosion prior to detonation.

## Implementation Details

### 1. [ImpactPredictor.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/math/ImpactPredictor.java)
Implements a client-side simulation of Minecraft's vanilla explosion ray-casting logic:
- **Explosion Algorithm**: Simulates 1356 rays extending to the outer boundaries of a 16x16x16 cube centered around the impact location.
- **Ray Progression**: Steps along each ray, checking block blast resistances and reducing the remaining ray power. Blocks where the remaining power is greater than 0 are added to the list of predicted broken blocks.
- **Deterministic and Configurable**: Vanilla explosions use a randomized power multiplier per ray (ranging randomly from `0.7F` to `1.3F`) which causes prediction jitter. To solve this, the mod uses a configurable multiplier `ModConfig.instance().rayPowerMultiplier` (default `1.3F`, adjustable between `0.7F` and `1.3F` via the YACL config screen) to ensure a perfectly stable prediction.
- **Accurate Coordinates**: Adhers to modern fireball logic where the raycast impact location, not the location of the fireball itself is the center of the explosion.

### 2. Explosion Power Syncing
Because fireball size/power is normally handled server-side, the mod synchronizes the power to the client to ensure accurate radius estimation:
- **Server Event Listener**: In [FireballPredictor.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/FireballPredictor.java), `EntityTrackingEvents.START_TRACKING` is registered on the server.
- **Explosion Power Retrieval**: Extracts the explosion power using the accessor [FireballEntityAccessor.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/mixin/FireballEntityAccessor.java).
- **Network Sync**: Sends a custom network payload [FireballPowerPayload.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/network/FireballPowerPayload.java) containing the entity ID and power to the tracking client.
- **Client Cache**: The client receives this payload and stores it in [ClientPowerCache.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/client/network/ClientPowerCache.java).
- **Fallback Power**: If the power packet has not yet been received or if the entity is not a vanilla fireball, `ImpactPredictor` falls back to `ModConfig.instance().clientFallbackFireballPower` (for fireballs) or `1.0F` (for wither skulls/other projectiles).

### 3. Asynchronous Execution & Snapshot Caching
To prevent game micro-stutters and keep frame rendering smooth when predicting multiple fireballs:
- **[BlockStateSnapshot.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/math/BlockStateSnapshot.java)**: When a collision is predicted on the main thread, a thread-safe local block state snapshot is captured inside the bounding box of the explosion. It implements `BlockView` and stores immutable references to `BlockState` and `FluidState`.
- **Asynchronous Raycasting**: The 1,356 explosion rays are simulated asynchronously on a background worker thread using this snapshot, bypassing non-thread-safe world calls and avoiding main-thread freezes.

## Validation Results
- Compiles and runs successfully under Minecraft `1.21.11` using the Fabric Loader.
- Replicates the block breaking patterns of vanilla explosions accurately, scaling dynamically with custom fireball sizes.
- Exposing the `rayPowerMultiplier` in the configuration screen allows players to choose between conservative (lower multiplier) and comprehensive (higher multiplier) block predictions.
