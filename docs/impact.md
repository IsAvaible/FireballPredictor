# Fireball Impact Calculation

This document outlines how the mod predicts which blocks will be destroyed by a fireball's explosion prior to detonation.

## Implementation Details

### 1. [ImpactPredictor.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/math/ImpactPredictor.java)
Implements a client-side simulation of Minecraft's vanilla explosion ray-casting logic:
- **Explosion Algorithm**: Simulates 1356 rays extending to the outer boundaries of a 16x16x16 cube centered around the impact location.
- **Ray Progression**: Steps along each ray, checking block blast resistances and reducing the remaining ray power. Blocks where the remaining power is greater than 0 are added to the list of predicted broken blocks.
- **Deterministic and Configurable**: Vanilla explosions use a randomized power multiplier per ray (ranging randomly from `0.7F` to `1.3F`) which causes prediction jitter. To solve this, the mod uses a configurable multiplier `ModConfig.instance().rayPowerMultiplier` (default `1.3F`, adjustable between `0.7F` and `1.3F` via the YACL config screen) to ensure a perfectly stable prediction.
- **Wind Charge Bypass**: `AbstractWindChargeEntity` instances use `WindChargeExplosionBehavior` in vanilla which prevents block destruction entirely. `ImpactPredictor.predictBrokenBlocks()` detects wind charges and immediately returns an empty list (`List.of()`), accurately predicting 0 broken blocks.
- **Accurate Coordinates**: Adhers to modern fireball logic where the raycast impact location, not the location of the fireball itself is the center of the explosion.

### 2. Explosion Power Syncing & Dynamic Inference
Because fireball size/power is normally handled server-side, the mod synchronizes or infers explosion power using a 3-tier resolution hierarchy:
- **Server Sync Payload**: In [FireballPredictor.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/FireballPredictor.java), `EntityTrackingEvents.START_TRACKING` sends [FireballPowerPayload.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/network/FireballPowerPayload.java) to tracking clients, stored in [ClientPowerCache.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/client/network/ClientPowerCache.java).
- **Dynamic Explosion Inference**: On minigame servers without server-side mod sync, [ClientPacketListenerMixin.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/mixin/ClientPacketListenerMixin.java) intercepts incoming `ClientboundExplodePacket` packets. To prevent duplicate execution from thread dispatches and avoid wrong-thread crashes, it filters for main-thread execution (`client.isSameThread()`) and delegates to [ExplosionInferenceHandler.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/client/network/ExplosionInferenceHandler.java).
- **Side-Safe Proximity Tracker**: [FireballInferenceTracker.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/client/network/FireballInferenceTracker.java) tracks recent fireball positions (`lastPos`) and predicted impact coordinates (`hitPos`), retaining them for 3000ms. If an explosion packet occurs within **3.0 blocks** of a tracked fireball's `lastPos` or `hitPos`, the explosion radius is stored as `inferredFireballPower`.
- **Entity Type Isolation**: `FireballInferenceTracker.isFireball` explicitly restricts power inference to standard fireball entities (`LargeFireball`, `SmallFireball`, `DragonFireball`), ensuring complete isolation from Wither Skulls and Wind Charges.
- **Fallback Power**: [ClientPowerLookup.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/client/network/ClientPowerLookup.java) checks `POWER_CACHE` -> `inferredFireballPower` -> `ModConfig.instance().clientFallbackFireballPower` (for fireballs) or `1.0F` (for wither skulls/wind charges). Inferred power automatically resets when changing worlds.

### 3. Asynchronous Execution & Snapshot Caching
To prevent game micro-stutters and keep frame rendering smooth when predicting multiple fireballs:
- **[BlockStateSnapshot.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/math/BlockStateSnapshot.java)**: When a collision is predicted on the main thread, a thread-safe local block state snapshot is captured inside the bounding box of the explosion. It implements `BlockView` and stores immutable references to `BlockState` and `FluidState`.
- **Asynchronous Raycasting**: The 1,356 explosion rays are simulated asynchronously on a background worker thread using this snapshot, bypassing non-thread-safe world calls and avoiding main-thread freezes.

## Validation Results
- Compiles and runs successfully under Minecraft `1.21.11` using the Fabric Loader.
- Replicates the block breaking patterns of vanilla explosions accurately, scaling dynamically with custom fireball sizes.
- Exposing the `rayPowerMultiplier` in the configuration screen allows players to choose between conservative (lower multiplier) and comprehensive (higher multiplier) block predictions.
