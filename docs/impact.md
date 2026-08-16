# Fireball Impact Calculation

This document outlines how the mod predicts which blocks will be destroyed by a fireball's explosion prior to detonation.

## Implementation Details

### 1. [ImpactPredictor.java](../src/main/java/com/simonconrad/fireballpredictor/math/ImpactPredictor.java)
Implements a client-side simulation of Minecraft's vanilla explosion ray-casting logic:
- **Explosion Algorithm**: Simulates 1352 rays extending to the outer boundaries of a 16x16x16 cube centered around the impact location.
- **Ray Progression**: Steps along each ray, checking block blast resistances and reducing the remaining ray power. Blocks where the remaining power is greater than 0 are added to the list of predicted broken blocks.
- **Deterministic and Configurable**: Vanilla explosions use a randomized power multiplier per ray (ranging randomly from `0.7F` to `1.3F`) which causes prediction jitter. To solve this, the mod uses a configurable multiplier `ModConfig.instance().rayPowerMultiplier` (default `1.3F`, adjustable between `0.7F` and `1.3F` via the YACL config screen) to ensure a perfectly stable prediction.
- **Charged Wither Skull Resistance Capping**: For dangerous/charged wither skulls (`isDangerous == true`), `ImpactPredictor` evaluates `WitherBoss.canDestroy(blockState)`. If the target block is destructible by a Wither, its effective blast resistance is capped at `0.8F` (equivalent to wiki blast resistance 4.0). Blast-immune/unbreakable blocks like bedrock or reinforced deepslate return `false` for `WitherBoss.canDestroy` and retain their natural blast resistance, preventing false-positive destruction predictions.
- **Wind Charge Bypass**: `AbstractWindChargeEntity` instances use `WindChargeExplosionBehavior` in vanilla which prevents block destruction entirely. `ImpactPredictor.predictBrokenBlocks()` detects wind charges and immediately returns an empty list (`List.of()`), accurately predicting 0 broken blocks.
- **Accurate Coordinates**: Adheres to modern fireball logic where the raycast impact location, not the location of the fireball itself, is the center of the explosion.

### 2. Explosion Power Syncing & 5-Tier Fallback Hierarchy
Because fireball size/power is normally handled server-side, the mod resolves explosion power using a prioritized 5-tier resolution hierarchy in [ClientPowerLookup.java](../src/main/java/com/simonconrad/fireballpredictor/client/network/ClientPowerLookup.java):
1. **Tier 1 (Server Sync Payload)**: Checks `ClientPowerCache.POWER_CACHE` for explicit power packets sent via [FireballPowerPayload.java](../src/main/java/com/simonconrad/fireballpredictor/network/FireballPowerPayload.java).
2. **Tier 2 (Server-Specific Config Preset)**: If connected to a multiplayer server (`play.example.com`), checks `ModConfig.instance().serverFallbackPowers` for a server-specific power preset. This allows users to explicitly override misleading server explosion packet radii per server IP.
3. **Tier 3 (Dynamic Packet Radius Inference)**: Intercepts `ClientboundExplodePacket` in [ClientPacketListenerMixin.java](../src/main/java/com/simonconrad/fireballpredictor/mixin/ClientPacketListenerMixin.java). If an explosion packet occurs within 3.0 blocks of a tracked fireball's location (`FireballInferenceTracker`) with `radius > 0.0F`, [ExplosionInferenceHandler.java](../src/main/java/com/simonconrad/fireballpredictor/client/network/ExplosionInferenceHandler.java) performs a sanity-check against actual block destruction (`blockCount` / `affectedBlocks`). If block destruction indicates a power significantly lower than `packet.radius()` (e.g. GommeHD sending `radius = 4.0` for a power 1 explosion), the packet radius is rejected as inflated and block estimation is used instead.
4. **Tier 4 (Dynamic Affected Block Estimation)**: When servers (like Hypixel) zero out explosion radii (`radius <= 0.0F`) or send inflated packet radii, [ExplosionInferenceHandler.java](../src/main/java/com/simonconrad/fireballpredictor/client/network/ExplosionInferenceHandler.java) calculates the maximum distance $d_{\max}$ of destroyed blocks or destroyed block count ($\sqrt[3]{\text{blockCount} \times 1.5}$). Power is estimated as $P_{\text{est}} = \max(1.0F, d_{\max} / 1.3F)$. Session-wide maximum power is retained ($P_{\text{session}} = \max(P_{\text{current}}, P_{\text{new}})$) until world disconnect.
5. **Tier 5 (Global Fallback)**: Returns `ModConfig.instance().globalFallbackFireballPower` (default `1.0F`).

### 3. Asynchronous Execution & Snapshot Caching
To prevent game micro-stutters and keep frame rendering smooth when predicting multiple fireballs:
- **[BlockStateSnapshot.java](../src/main/java/com/simonconrad/fireballpredictor/math/BlockStateSnapshot.java)**: When a collision is predicted on the main thread, a thread-safe local block state snapshot is captured inside the bounding box of the explosion. It implements `BlockGetter` (`BlockView`) and stores immutable references to `BlockState` and `FluidState`.
- **Asynchronous Raycasting**: The 1352 explosion rays are simulated asynchronously on a background worker thread (`FireballPredictor-Worker`) using this snapshot, bypassing non-thread-safe world calls and avoiding main-thread freezes.

### 4. Damage & Knockback Prediction ([DamageCalculator.java](../src/main/java/com/simonconrad/fireballpredictor/math/DamageCalculator.java))
The mod replicates Minecraft 26.2's complete explosion damage and knockback pipeline client-side without relying on server-only classes:
- **Blast Radius & Distance Falloff**: Effective blast radius is $r = \text{power} \times 2.0$. If the player is within range ($d \le r$), exposure is computed from distance falloff $(1.0 - d / r) \times \text{seenPercent}$. Raw explosion damage follows vanilla formula:
  $$\text{damage}_{\text{raw}} = \frac{\text{impact}^2 + \text{impact}}{2} \times 7.0 \times r + 1.0$$
- **Line-of-Sight Exposure (`getSeenPercent`)**: Replicates vanilla `ServerExplosion.getSeenPercent` deterministically on the main render thread, raycasting a grid across the player's bounding box to detect partial cover and terrain shielding ($0.0 \le \text{seenPercent} \le 1.0$).
- **Direct-Hit Damage**: When a projectile directly collides with the player, `calculateDirectHit` calculates direct impact damage (6.0 for Large Fireballs, 5.0 for Small Fireballs, 8.0 for Wither Skulls) alongside the accompanying detonation blast damage.
- **Vanilla 26.2 Damage Mitigation Pipeline (`computeFinalDamage`)**:
  1. **Difficulty Scaling**: Accounts for peaceful (0 damage), easy (half damage + 1), and hard (1.5x damage) scaling.
  2. **Armor & Toughness**: Evaluates `CombatRules.getDamageAfterAbsorb` using the player's current armor points and armor toughness attribute.
  3. **Resistance Effect**: Scales damage according to active Resistance effect amplifier ($25 - (\text{amplifier} + 1) \times 5$).
  4. **Data-Driven Enchantment Protection (EPF)**: Because vanilla's `EnchantmentHelper.getDamageProtection` is server-only, `DamageCalculator.getEnchantmentProtection` inspects equipped armor items, queries `EnchantmentEffectComponents.DAMAGE_PROTECTION`, and evaluates loot condition requirements using [CompositeLootItemConditionAccessor.java](../src/main/java/com/simonconrad/fireballpredictor/mixin/CompositeLootItemConditionAccessor.java) for `AllOfCondition` / `AnyOfCondition` / `InvertedLootItemCondition` / `DamageSourceCondition`. Resulting EPF (e.g. +1/lvl Protection, +2/lvl Blast Protection) is soft-capped at `MAX_EPF = 20.0F` via `CombatRules.getDamageAfterMagicAbsorb`.
  5. **Bypass Tags**: Respects `BYPASSES_ARMOR`, `BYPASSES_EFFECTS`, `BYPASSES_RESISTANCE`, and `BYPASSES_ENCHANTMENTS` damage-type tags.
- **Knockback Impulse (`computeKnockback`)**: Predicts the initial horizontal/vertical blast impulse magnitude $(1 - d/r) \times \text{seenPercent} \times (1 - \text{EXPLOSION\_KNOCKBACK\_RESISTANCE})$, reported in blocks per second (impulse $\times 20$).

## Validation Results
- Compiles and runs successfully under Minecraft `26.2` using the Fabric Loader.
- Replicates the block breaking patterns of vanilla explosions accurately, scaling dynamically with custom fireball sizes.
- Exposing the `rayPowerMultiplier` in the configuration screen allows players to choose between conservative (lower multiplier) and comprehensive (higher multiplier) block predictions.
- Damage and knockback predictions match actual in-game damage values within strict tolerances across naked, armor-equipped, blast-protected, and partially covered player states.
