# Fireball Impact Calculation

This document outlines how the mod predicts which blocks will be destroyed by a fireball's explosion prior to detonation.

## Implementation Details

### 1. [ImpactPredictor.java](../src/main/java/com/simonconrad/fireballpredictor/math/ImpactPredictor.java)
Implements a client-side simulation of Minecraft's vanilla explosion ray-casting logic:
- **Explosion Algorithm**: Simulates 1356 rays extending to the outer boundaries of a 16x16x16 cube centered around the impact location.
- **Ray Progression**: Steps along each ray, checking block blast resistances and reducing the remaining ray power. Blocks where the remaining power is greater than 0 are added to the list of predicted broken blocks.
- **Deterministic and Configurable**: Vanilla explosions use a randomized power multiplier per ray (ranging randomly from `0.7F` to `1.3F`) which causes prediction jitter. To solve this, the mod uses a configurable multiplier `ModConfig.instance().rayPowerMultiplier` (default `1.3F`, adjustable between `0.7F` and `1.3F` via the YACL config screen) to ensure a perfectly stable prediction.
- **Wind Charge Bypass**: `AbstractWindChargeEntity` instances use `WindChargeExplosionBehavior` in vanilla which prevents block destruction entirely. `ImpactPredictor.predictBrokenBlocks()` detects wind charges and immediately returns an empty list (`List.of()`), accurately predicting 0 broken blocks.
- **Accurate Coordinates**: Adhers to modern fireball logic where the raycast impact location, not the location of the fireball itself is the center of the explosion.

### 2. Explosion Power Syncing & 5-Tier Fallback Hierarchy
Because fireball size/power is normally handled server-side, the mod resolves explosion power using a prioritized 5-tier resolution hierarchy in [ClientPowerLookup.java](../src/main/java/com/simonconrad/fireballpredictor/client/network/ClientPowerLookup.java):
1. **Tier 1 (Server Sync Payload)**: Checks `ClientPowerCache.POWER_CACHE` for explicit power packets sent via [FireballPowerPayload.java](../src/main/java/com/simonconrad/fireballpredictor/network/FireballPowerPayload.java).
2. **Tier 2 (Dynamic Packet Radius Inference)**: Intercepts `ExplosionS2CPacket` in [ClientPlayNetworkHandlerMixin.java](../src/main/java/com/simonconrad/fireballpredictor/mixin/ClientPlayNetworkHandlerMixin.java). If an explosion packet occurs within 3.0 blocks of a tracked fireball's location (`FireballInferenceTracker`) with `radius > 0.0F`, `inferredPacketRadius` stores the exact packet radius. This takes precedence over server config presets.
3. **Tier 3 (Server-Specific Config Preset)**: If connected to a multiplayer server (`play.example.com`), checks `ModConfig.instance().serverFallbackPowers` for a server-specific power preset. Takes precedence over dynamic block destruction estimation.
4. **Tier 4 (Dynamic Affected Block Estimation)**: When servers (like Hypixel) zero out explosion radii (`radius <= 0.0F`), [ExplosionInferenceHandler.java](../src/main/java/com/simonconrad/fireballpredictor/client/network/ExplosionInferenceHandler.java) calculates the maximum distance $d_{\max}$ of destroyed blocks / destroyed block count. Power is estimated as $P_{\text{est}} = \max(1.0F, d_{\max} / 1.3F)$. Session-wide maximum power is retained ($P_{\text{session}} = \max(P_{\text{current}}, P_{\text{new}})$) until world disconnect.
5. **Tier 5 (Global Fallback)**: Returns `ModConfig.instance().globalFallbackFireballPower` (default `1.0F`).


### 3. Asynchronous Execution & Snapshot Caching
To prevent game micro-stutters and keep frame rendering smooth when predicting multiple fireballs:
- **[BlockStateSnapshot.java](../src/main/java/com/simonconrad/fireballpredictor/math/BlockStateSnapshot.java)**: When a collision is predicted on the main thread, a thread-safe local block state snapshot is captured inside the bounding box of the explosion. It implements `BlockView` and stores immutable references to `BlockState` and `FluidState`.
- **Asynchronous Raycasting**: The 1,356 explosion rays are simulated asynchronously on a background worker thread using this snapshot, bypassing non-thread-safe world calls and avoiding main-thread freezes.

## Validation Results
- Compiles and runs successfully under Minecraft `1.21.11` using the Fabric Loader.
- Replicates the block breaking patterns of vanilla explosions accurately, scaling dynamically with custom fireball sizes.
- Exposing the `rayPowerMultiplier` in the configuration screen allows players to choose between conservative (lower multiplier) and comprehensive (higher multiplier) block predictions.
