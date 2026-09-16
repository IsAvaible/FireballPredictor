# Fireball Impact Calculation

This document outlines how the mod predicts which blocks will be destroyed by a fireball's explosion prior to detonation.

## Implementation Details

### 1. [ImpactPredictor.java](../src/main/java/com/simonconrad/fireballpredictor/math/ImpactPredictor.java)
Implements a client-side simulation of Minecraft's vanilla explosion ray-casting logic:
- **Explosion Algorithm**: Simulates 1352 rays extending to the outer boundaries of a 16x16x16 cube centered around the impact location.
- **Ray Progression**: Steps along each ray, checking block blast resistances and reducing the remaining ray power. Blocks where the remaining power is greater than 0 are added to the list of predicted broken blocks.
- **Deterministic and Configurable**: Vanilla explosions use a randomized power multiplier per ray (ranging randomly from `0.7F` to `1.3F`) which causes prediction jitter. To solve this, the mod uses a configurable multiplier `ModConfig.instance().rayPowerMultiplier` (default `1.3F`, adjustable between `0.7F` and `1.3F` via the YACL config screen) to ensure a perfectly stable prediction.
- **Charged Wither Skull Resistance Capping**: For dangerous/charged wither skulls (`isDangerous == true`), `ImpactPredictor` evaluates `WitherBoss.canDestroy(blockState)`. If the target block is destructible by a Wither, its effective blast resistance is capped at `0.8F` (equivalent to wiki blast resistance 4.0). Blast-immune/unbreakable blocks like bedrock or reinforced deepslate return `false` for `WitherBoss.canDestroy` and retain their natural blast resistance, preventing false-positive destruction predictions.
- **Wind Charge & Zero-Blast Bypass**: `ImpactPredictor.predictBrokenBlocks()` checks `profile.breaksBlocks()` via [`ProjectileProfile`](../src/main/java/com/simonconrad/fireballpredictor/projectile/ProjectileProfile.java). Projectiles that do not break blocks (e.g. wind charges and dragon/small fireballs) immediately return an empty list (`List.of()`), accurately predicting 0 broken blocks without running raycasts.
- **Accurate Coordinates**: Adheres to modern fireball logic where the raycast impact location, not the location of the fireball itself, is the center of the explosion.

### 2. Explosion Power Syncing & 4-Tier Resolution Hierarchy
Because fireball size/power is normally handled server-side, the mod resolves explosion power using a prioritized 4-tier resolution hierarchy in [ClientPowerLookup.java](../src/main/java/com/simonconrad/fireballpredictor/client/network/ClientPowerLookup.java):
1. **Tier 1 (Server Sync Payload)**: Checks `ClientPowerCache` for explicit power packets sent via [FireballPowerPayload.java](../src/main/java/com/simonconrad/fireballpredictor/network/FireballPowerPayload.java).
2. **Tier 2 (Server-Specific Config Preset)**: If connected to a multiplayer server (`play.example.com`), checks `ModConfig.instance().serverFallbackPowers` for a server-specific power preset. This allows users to explicitly override misleading server explosion packet radii per server IP.
3. **Tier 3 (Per-Owner Dynamic Real-Time Inference with Terrain Normalization, Snapping & EMA)**:
   - Checks for an active, unexpired inferred power in `OWNER_INFERENCES` keyed by `ProjectileOwner` (e.g. `PLAYER`, `GHAST`, `COMMAND`, `UNKNOWN`), refreshed whenever an active shot of that owner type is observed (180-second / 3-minute default TTL).
   - **Bidirectional Packet Radius Divergence Validation**: When an explosion packet arrives with `radius > 0.0f`, [`ExplosionInferenceHandler`](../src/main/java/com/simonconrad/fireballpredictor/client/network/ExplosionInferenceHandler.java) validates whether the claimed radius diverges significantly from observable block destruction (`estimatedBlockPower < radius * 0.75f || estimatedBlockPower > radius * 1.35f`). If divergence is detected (e.g. anti-cheat servers broadcasting dummy inflated `4.0` radii or dummy deflated `0.5`/`1.0` radii despite large crater formations), the mod rejects the packet radius and relies on normalized block estimation.
   - **Air & Water Detonation Safeguard**: Non-destructive detonations (such as mid-air explosions or explosions submerged in deep water where `estimatedBlockPower == 0.0f`) are guarded to ensure they do not overwrite legitimate active power inferences with zero.
   - **Terrain Geometry & Blast-Resistance Normalization**: When estimating power from broken blocks (e.g. Hypixel zero-radius packets), `ExplosionInferenceHandler` samples a $3\times3\times3$ voxel neighborhood around the impact site in `ClientLevel`. It normalizes `blockCount` against the solid enclosure ratio ($f_{\text{solid}} = N_{\text{solid}} / 27.0$, compensating for wall/floor geometry) and average blast resistance ($\bar{B}$, relative to baseline wool $\bar{B}_0 \approx 0.8$).
   - **Multi-Sample EMA Smoothing**: Repeated block estimations for an active owner are smoothed via an Exponential Moving Average ($\alpha = 0.65$) to eliminate terrain-specific sample jitter.
   - **Discrete Canonical Snapping**: Inferred values are snapped to standard integer ($\pm 0.25$) or half-integer ($\pm 0.15$) levels ($1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 5.0$) via `ClientPowerLookup.snapToCanonicalPower`.
   - **Vanilla Source Guard**: Known standard vanilla sources (`owner.isMob() || owner == ProjectileOwner.DISPENSER`) without custom inferences default to vanilla `1.0F`, preventing custom player blasts (such as power-3 Bedwars fireballs) from poisoning ghast or dispenser fireball dome sizes.
4. **Tier 4 (Global Config Fallback)**: When no unexpired inference exists, falls back cleanly to `ModConfig.instance().globalFallbackFireballPower` (default `1.0F`), ensuring inferences expire naturally without permanent session-wide cross-pollution.

### 3. Synchronous Execution & Direct World Raymarching
Prediction runs directly and synchronously on the client tick without worker thread hopping or intermediate snapshot arrays:
- **Direct World Queries**: `ImpactPredictor.predictBrokenBlocks()` queries `ClientLevel.getBlockState(mutable)` directly during the 1,352-ray march, matching vanilla Minecraft's explosion architecture.
- **Power Clamping & Safety**: Raymarching enforces an extreme power safety cap of `power <= 50.0f` (and non-positive power check). Projectiles exceeding power 50 gracefully return an empty broken blocks list (`List.of()`) to prevent runaway loop cycles.
- **Non-Destructive Projectile Bypass**: Projectiles that do not break blocks (e.g. wind charges and dragon/small fireballs via `profile.breaksBlocks()`) or when mob griefing is disabled skip raycasting entirely and return `List.of()`.
- **Zero-Latency Unified Rendering**: Trajectory ribbons, visual hit markers, shockwave dome meshes, damage estimates, and broken block overlays are generated synchronously and render together immediately on frame 0.

### 4. Damage & Knockback Prediction ([DamageCalculator.java](../src/main/java/com/simonconrad/fireballpredictor/math/DamageCalculator.java))
The mod replicates Minecraft 26.2's complete explosion damage and knockback pipeline client-side without relying on server-only classes:
- **Blast Radius & Distance Falloff**: Effective blast radius is $r = \text{power} \times 2.0$. If the player is within range ($d \le r$), exposure is computed from distance falloff $(1.0 - d / r) \times \text{seenPercent}$. Raw explosion damage follows vanilla formula:
  $$\text{damage}_{\text{raw}} = \frac{\text{impact}^2 + \text{impact}}{2} \times 7.0 \times r + 1.0$$
- **Line-of-Sight Exposure (`getSeenPercent`)**: Replicates vanilla `ServerExplosion.getSeenPercent` deterministically on the main render thread, raycasting a grid across the player's bounding box to detect partial cover and terrain shielding ($0.0 \le \text{seenPercent} \le 1.0$).
- **Direct-Hit Damage**: When a projectile directly collides with the player, `calculateDirectHit` evaluates profile-driven direct impact damage (6.0 for Large Fireballs, 5.0 for Small Fireballs, 8.0 for Wither Skulls, 0 for Dragon Fireballs and Wind Charges) alongside the accompanying detonation blast damage.
- **Vanilla 26.2 Damage Mitigation Pipeline (`computeFinalDamage`)**:
  1. **Difficulty Scaling**: Accounts for peaceful (0 damage), easy (half damage + 1), and hard (1.5x damage) scaling.
  2. **Armor & Toughness**: Evaluates `CombatRules.getDamageAfterAbsorb` using the player's current armor points and armor toughness attribute.
  3. **Resistance Effect**: Scales damage according to active Resistance effect amplifier ($25 - (\text{amplifier} + 1) \times 5$).
  4. **Data-Driven Enchantment Protection (EPF)**: Because vanilla's `EnchantmentHelper.getDamageProtection` is server-only, `DamageCalculator.getEnchantmentProtection` inspects equipped armor items, strictly verifies slot compatibility via `holder.value().matchingSlot(slot)`, queries `EnchantmentEffectComponents.DAMAGE_PROTECTION`, and evaluates loot condition requirements using [CompositeLootItemConditionAccessor.java](../src/main/java/com/simonconrad/fireballpredictor/mixin/CompositeLootItemConditionAccessor.java) for `AllOfCondition` / `AnyOfCondition` / `InvertedLootItemCondition` / `DamageSourceCondition`. Resulting EPF (e.g. +1/lvl Protection, +2/lvl Blast Protection) is soft-capped at `MAX_EPF = 20.0F` via `CombatRules.getDamageAfterMagicAbsorb`.
  5. **Bypass Tags**: Respects `BYPASSES_ARMOR`, `BYPASSES_EFFECTS`, `BYPASSES_RESISTANCE`, and `BYPASSES_ENCHANTMENTS` damage-type tags.
- **Knockback Impulse (`computeKnockback`)**: Predicts the initial horizontal/vertical blast impulse magnitude $(1 - d/r) \times \text{seenPercent} \times (1 - \text{EXPLOSION\_KNOCKBACK\_RESISTANCE})$, reported in blocks per second (impulse $\times 20$).

## Validation Results
- Compiles and runs successfully under Minecraft `26.2` using the Fabric Loader.
- Replicates the block breaking patterns of vanilla explosions accurately, scaling dynamically with custom fireball sizes.
- Exposing the `rayPowerMultiplier` in the configuration screen allows players to choose between conservative (lower multiplier) and comprehensive (higher multiplier) block predictions.
- Damage and knockback predictions match actual in-game damage values within strict tolerances across naked, armor-equipped, blast-protected, and partially covered player states.
