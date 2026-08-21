# Fireball Predictor Mod - GameTest Suite Documentation

This document describes the automated GameTest validation suite implemented for the Fireball Predictor mod. The test suite leverages Minecraft's vanilla GameTest framework to headlessly simulate projectile trajectories and assert that the mathematical prediction model remains accurate and free of regressions.

## Why We Use GameTest

Whenever Minecraft updates its version or changes its internal collision, drag, or projectile physics, manual in-game testing is tedious and prone to human error. This automated suite spawns actual projectiles in a controlled environment, runs the prediction logic, allows the projectile to detonate, and asserts that the predicted block destruction matches the actual world state.

---

## Test Scenarios

The suite is organized across four domain-scoped test classes ([`TrajectoryTests.java`](../src/main/java/com/simonconrad/fireballpredictor/gametest/TrajectoryTests.java), [`DamageTests.java`](../src/main/java/com/simonconrad/fireballpredictor/gametest/DamageTests.java), [`OwnerTests.java`](../src/main/java/com/simonconrad/fireballpredictor/gametest/OwnerTests.java), and [`ThemeTests.java`](../src/main/java/com/simonconrad/fireballpredictor/gametest/ThemeTests.java), inheriting from [`GameTestBase.java`](../src/main/java/com/simonconrad/fireballpredictor/gametest/GameTestBase.java)) using the empty structure pattern (`fabric-gametest-api-v1:empty`):

### 1. Ghast Fireball Prediction (`testFireballPredictionAndExplosion`)
* **Entity**: `FireballEntity`
* **Starting State**: Spawns at relative `(1.5, 3.0, 3.5)` with an initial relative velocity of `(0.5, 0.0, 0.0)` and an acceleration power of `0.1` (vanilla ghast fireball standard).
* **Environment**: A target wall of `Blocks.DIRT` built at relative `x = 2`.
* **Details**: Asserts that normal fireball explosion power computation and vanilla raycasting math correctly predict which dirt blocks will be destroyed.

### 2. Standard Wither Skull Prediction (`testWitherSkullPredictionAndExplosion`)
* **Entity**: `WitherSkullEntity` (non-charged/black)
* **Starting State**: Spawns at relative `(1.5, 3.0, 3.5)` with an initial relative velocity of `(0.5, 0.0, 0.0)` and zero acceleration.
* **Environment**: A target wall of `Blocks.DIRT` built at relative `x = 2`.
* **Details**: Asserts that standard wither skull explosion power (1.0) and trajectory physics correctly predict block destruction.

### 3. Charged Wither Skull Prediction (`testChargedWitherSkullPredictionAndExplosion`)
* **Entity**: `WitherSkullEntity` (charged/blue)
* **Starting State**: Spawns at relative `(1.5, 3.0, 3.5)` with an initial relative velocity of `(0.5, 0.0, 0.0)` and zero acceleration.
* **Environment**: A target wall of `Blocks.DIRT` built at relative `x = 2`.
* **Details**: Verifies the mod's specialized math for charged wither skulls, specifically:
  * Capping block blast resistance at `4.0` (wiki value, equivalent to `0.8F` in internal code) in the prediction.
  * Handling the high drag (`0.73F` per tick) behavior unique to charged skulls.

### 4. Charged Wither Skull against Obsidian (`testChargedWitherSkullAgainstObsidian`)
* **Entity**: `WitherSkullEntity` (charged/blue)
* **Starting State**: Spawns at relative `(1.5, 3.0, 3.5)` with velocity `(0.5, 0.0, 0.0)`.
* **Environment**: A target wall of `Blocks.OBSIDIAN` at relative `x = 2`.
* **Details**: Confirms that charged wither skulls successfully predict and execute block destruction against high blast-resistance blocks like obsidian by capping resistance at `0.8F`.

### 5. Normal Wither Skull against Obsidian (`testNormalWitherSkullAgainstObsidian`)
* **Entity**: `WitherSkullEntity` (non-charged/black)
* **Starting State**: Spawns at relative `(1.5, 3.0, 3.5)` with velocity `(0.5, 0.0, 0.0)`.
* **Environment**: A target wall of `Blocks.OBSIDIAN` at relative `x = 2`.
* **Details**: Validates that normal wither skulls do *not* break obsidian blocks (predicts 0 broken blocks, actual 0 broken), confirming that the blast resistance capping is correctly restricted to charged skulls.

### 6. Charged Wither Skull against Reinforced Deepslate (`testChargedWitherSkullAgainstReinforcedDeepslate`)
* **Entity**: `WitherSkullEntity` (charged/blue)
* **Starting State**: Spawns at relative `(1.5, 3.0, 3.5)` with velocity `(0.5, 0.0, 0.0)`.
* **Environment**: A target wall of `Blocks.REINFORCED_DEEPSLATE` at relative `x = 2`.
* **Details**: Asserts that charged wither skulls predict 0 broken blocks and break 0 blocks against reinforced deepslate, confirming that blast resistance capping (`0.8F`) does not bypass blast-immune/unbreakable block categories.

### 7. Normal Fireball against Waterlogged Slabs (`testNormalFireballAgainstWaterloggedSlab`)
* **Entity**: `FireballEntity` (power 1.0)
* **Starting State**: Spawns at relative `(1.5, 3.0, 3.5)` with velocity `(0.5, 0.0, 0.0)` and acceleration `0.1`.
* **Environment**: A target wall of waterlogged `Blocks.OAK_SLAB` at relative `x = 2`.
* **Details**: Asserts that a normal fireball correctly predicts 0 broken blocks and breaks 0 blocks, since waterlogged blocks inherit the fluid water's high blast resistance (100.0).

### 8. Charged Wither Skull against Waterlogged Slabs (`testChargedWitherSkullAgainstWaterloggedSlab`)
* **Entity**: `WitherSkullEntity` (charged/blue)
* **Starting State**: Spawns at relative `(1.5, 3.0, 3.5)` with velocity `(0.5, 0.0, 0.0)`.
* **Environment**: A target wall of waterlogged `Blocks.OAK_SLAB` at relative `x = 2`.
* **Details**: Verifies that a charged wither skull correctly predicts and destroys waterlogged slabs, since the capping logic reduces the overall block/fluid blast resistance to `0.8F`.

### 9. High-Power Fireball Prediction (`testHighPowerFireballPredictionAndExplosion`)
* **Entity**: `FireballEntity` (configured with explosion power 3)
* **Starting State**: Spawns at relative `(1.5, 3.0, 3.5)` with velocity `(0.5, 0.0, 0.0)`.
* **Environment**: A target wall of `Blocks.DIRT` built at relative `x = 2`.
* **Details**: Uses the new `setExplosionPower` accessor to simulate a fireball with custom high explosion power (power = 3) and verifies that the mod correctly scales both the predicted block destruction and actual crater size.

### 10. Wind Charge Prediction (`testWindChargePredictionAndExplosion`)
* **Entity**: `WindChargeEntity`
* **Starting State**: Spawns at relative `(1.5, 3.0, 3.5)` with an initial relative velocity of `(0.5, 0.0, 0.0)` and zero acceleration.
* **Environment**: A target wall of `Blocks.DIRT` built at relative `x = 2`.
* **Details**: Asserts that Wind Charges calculate drag of `1.0` (no drag), predict 0 broken blocks upon impact, and break 0 actual blocks in the world upon detonation (`assertNoDestruction`).

### 11. Water Drag Prediction (`testWaterDragPrediction`)
* **Entity**: `LargeFireball`
* **Starting State**: Spawns inside a 5x5x5 volume of `Blocks.WATER`.
* **Environment**: Submerged fluid trajectory environment.
* **Details**: Validates that standard fireballs moving through water correctly experience water drag reduction (speed reduced by a factor of 0.8 per tick in water).

### 12. Wind Charge Water Drag Immunity (`testWindChargeWaterDragPrediction`)
* **Entity**: `WindChargeEntity`
* **Starting State**: Spawns inside a 5x5x5 volume of `Blocks.WATER`.
* **Environment**: Submerged fluid trajectory environment.
* **Details**: Confirms that Wind Charges retain a 1.0 drag multiplier and maintain full speed without drag degradation even when moving through water.

### 13. BlockGetter Water Detection (`testBlockGetterWaterDetection`)
* **Environment**: Mock snapshot simulation.
* **Details**: Asserts that the custom `BlockStateSnapshot` implementation correctly exposes fluid states to `TrajectoryPredictor.isTouchingWater` for accurate drag evaluation.

### 14. Small Fireball Power and Non-Destruction (`testSmallFireballPowerAndNoDestruction`)
* **Entity**: `SmallFireball`
* **Starting State**: Spawns at relative `(1.5, 3.0, 3.5)` with velocity `(0.5, 0.0, 0.0)`.
* **Environment**: A target wall of `Blocks.DIRT` at relative `x = 2`.
* **Details**: Asserts that small fireballs resolve explosion power as `0.0f` and do not break blocks on collision (`assertNoDestruction`).

### 15. Dragon Fireball Power and Non-Destruction (`testDragonFireballPowerAndNoDestruction`)
* **Entity**: `DragonFireball`
* **Starting State**: Spawns at relative `(1.5, 3.0, 3.5)` with velocity `(0.5, 0.0, 0.0)`.
* **Environment**: A target wall of `Blocks.DIRT` at relative `x = 2`.
* **Details**: Asserts that Ender Dragon fireballs resolve explosion power as `0.0f` (spawning effect clouds rather than block-damaging explosions), evaluate direct-hit damage as `0.0f`, and predict/execute 0 broken blocks.

### 16. Inferred Explosion Power Fallback (`testInferredExplosionPowerFallback`)
* **Entity**: `LargeFireball` (unsynced entity ID)
* **Starting State**: Clears `ClientPowerCache` and resets `inferredFireballPower`. Spawns a fireball with default properties.
* **Environment**: A target wall of `Blocks.DIRT` built at relative `x = 2`.
* **Details**: Simulates an explosion power inference of `3.0f`, asserts that `ClientPowerLookup` and `ImpactPredictor` resolve the unsynced fireball's power to the inferred `3.0f`, and verifies that predicted block destruction matches high-power crater scaling.

### 17. Zero-Radius Affected Block Estimation and Hierarchy (`testZeroRadiusAffectedBlockEstimationAndHierarchy`)
* **Entity**: `LargeFireball`
* **Starting State**: Clears `ClientPowerCache` and resets inferred power state. Registers a fireball location in `FireballInferenceTracker`.
* **Environment**: Headless mock position simulation.
* **Details**: 
  1. Simulates zero-radius `ClientboundExplodePacket` (`radius = 0.0f`) with affected block list extending 3.9 blocks away. Verifies power estimation via $d_{\max} / 1.3$ yields $\sim 3.0\text{f}$.
  2. Verifies session max power retention ($P_{\text{session}}$ does not decrease when subsequent smaller explosions occur).
  3. Verifies fallback hierarchy precedence: Tier 2 explicit packet radius inference (`2.5f`) overrides Tier 4 block estimation (`3.0f`).

### 18. Inflated Packet Radius Sanity Check (`testInflatedPacketRadiusSanityCheckAndServerPresetPriority`)
* **Entity**: `LargeFireball`
* **Starting State**: Clears `ClientPowerCache` and resets inferred power state.
* **Environment**: Headless mock position simulation.
* **Details**:
  1. Simulates inflated packet radius (e.g. GommeHD `radius = 4.0f` with low block count), asserting that the inflated radius is rejected and block estimation (~1.44f) is used instead.
  2. Simulates legitimate high block count (40 blocks with `radius = 4.0f`), confirming that the 4.0f packet radius is accepted.

### 19. Server Fallback Power Management (`testServerFallbackPowerSetAndUnset`)
* **Environment**: Unit test configuration handling.
* **Details**: Verifies setting, updating, and clearing (`0.0f` / `null`) per-server fallback power presets in `ModConfig`.

### 20. Native & Environmental Owner Inference (`testOwnerInferenceNativeAndSweep`)
* **Entities**: `Ghast`, `Blaze`, `LargeFireball`
* **Details**:
  1. Validates native owner detection (`setOwner` / `NATIVE_NBT`).
  2. Validates environmental sweep detection when owner NBT is absent, resolving owner by orientation and proximity.

### 21. Dispenser & Player Deflection Inference (`testOwnerInferenceDispenserAndDeflection`)
* **Entities**: `DispenserBlock`, `LargeFireball`, mock `Player`
* **Details**:
  1. Verifies dispenser adjacency detection (`DISPENSER` owner).
  2. Simulates player hit/punch velocity reversal, confirming owner re-attribution to `PLAYER` (`isDeflected() == true`) and filter evaluation logic.

### 22. Breeze Owner Classification & Tracking Filter (`testBreezeOwnerClassificationAndFilter`)
* **Entities**: `Breeze`, `BreezeWindCharge`
* **Details**:
  1. Asserts that `OwnerClassifier.classifyEntity(breeze)` resolves to `ProjectileOwner.BREEZE` and `isMob()` is true.
  2. Asserts that native owner assignment resolves to `BREEZE`.
  3. Verifies `TrackedProjectile.evaluateFilter` gates on `trackMobProjectiles`, `trackBreezeWindCharges`, and `trackWindCharges`, while ignoring non-mob toggles (`trackOtherOwnerProjectiles`).

### 23. Server Tracking Restrictions (`testServerTrackingRestrictions`)
* **Entities**: `LargeFireball`
* **Details**: Asserts that server restriction masks (`ServerTrackingRules`) override local client config settings (including deflection bypass) for player, dispenser, command, or group restrictions, and that clearing restrictions restores local settings immediately.

### 23. Server Mask Sanitization (`testPacketSanitization`)
* **Details**: Verifies bitmask sanitization, ensuring unsupported or out-of-range bit values in network payloads are stripped cleanly.

### 24. Disconnect Mask Clearing (`testDisconnectReset`)
* **Details**: Asserts that server tracking restrictions are cleared (`mask = 0`) on server disconnect to prevent cross-server rule contamination.

### 25. GUI Option Server Availability (`testGuiOptionAvailability`)
* **Details**: Verifies GUI option lock state calculations under active server restriction bitmasks.

### 26. Wind Charge Zero Damage Estimate (`testWindChargeZeroDamageEstimate`)
* **Entity**: `WindCharge`
* **Details**: Asserts that wind charges calculate a damage estimate of `DamageEstimate.NONE`, predicting 0.0 hearts lost and 0.0 final damage upon impact.

### 27. Zero-Power Explosion Damage Bypass (`testDamageCalculatorZeroPowerNoDamage`)
* **Entities**: `SmallFireball`, `DragonFireball`
* **Details**: Verifies that explosive projectiles with power $\le 0$ evaluate immediately to `DamageEstimate.NONE` mirroring vanilla's early exit for zero-radius explosions.

### 28. Out-of-Range Blast Estimate (`testDamageCalculatorOutOfRange`)
* **Entity**: `LargeFireball` (power 1.0, blast radius 2.0 blocks)
* **Details**: Spawns a mock player 5.0 blocks away from the detonation point, asserting that `DamageCalculator.calculate` returns `DamageEstimate.NONE` with `inRange() == false`.

### 29. Unarmored Blast Damage Accuracy (`testDamageCalculatorMatchesRealExplosionNoArmor`)
* **Entity**: `LargeFireball` (power 1.0)
* **Details**: Places an unarmored mock player 1.0 block away from a detonation point. Runs `DamageCalculator.calculate`, creates an actual in-game explosion, and asserts that predicted damage matches the real damage inflicted within a 0.01 threshold.

### 30. Armor & Blast Protection Mitigation (`testDamageCalculatorWithArmorAndBlastProtection`)
* **Entity**: `LargeFireball` (power 1.0)
* **Details**: Equips a mock player with full diamond armor enchanted with Protection IV and Blast Protection IV. Asserts that `DamageCalculator.getEnchantmentProtection` correctly evaluates EPF and matches the reduced damage taken in a real explosion.

### 31. Line-of-Sight Cover Reduction (`testDamageCalculatorCoverReducesDamage`)
* **Entity**: `LargeFireball` (power 1.0)
* **Details**: Places a stone cover barrier partially obscuring line-of-sight between the explosion center and the player. Asserts that `DamageCalculator.getSeenPercent` detects partial exposure ($0 < \text{seenPercent} < 1$) and reduces predicted damage compared to unobstructed exposure.

### 32. Large Fireball Direct Hit (`testDamageCalculatorDirectHit`)
* **Entity**: `LargeFireball`
* **Details**: Asserts that `DamageCalculator.calculateDirectHit` correctly combines 6.0 direct impact damage with the detonation blast damage.

### 33. Wither Skull Direct Hit (`testDamageCalculatorWitherSkullDirectHit`)
* **Entity**: `WitherSkull`
* **Details**: Asserts that `DamageCalculator.calculateDirectHit` accounts for 8.0 direct impact damage combined with wither skull blast damage.

### 34. Direct-Hit Collision In-Game Accuracy (`testDamageCalculatorDirectHitRealCollision`)
* **Entity**: `LargeFireball`
* **Details**: Spawns a live fireball moving directly into a mock player's hitbox, asserting that predicted direct-hit damage matches the exact health loss observed upon real collision.

### 35. Absorption Hearts Clamping (`testDamageCalculatorAbsorptionClamp`)
* **Entity**: `LargeFireball` (power 4.0)
* **Details**: Gives a player 20 health and 10 absorption points. Verifies that `DamageEstimate.heartsLost` correctly includes absorption and clamps to $(20 + 10) / 2 = 15.0$ hearts without overflowing.

### 36. Trajectory vs Damage Hit Result Separation (`testTrajectorySimulationEntityCollision`)
* **Entities**: `LargeFireball`, mock `Player`, stone wall at $x = 10$
* **Details**: Spawns a player at $x = 5$ in front of a wall at $x = 10$. Asserts that trajectory simulation produces a visual `hitResult` continuing to the wall at $x = 10$ (for ribbon/dome rendering), while `damageHitResult` captures the intercepted player at $x = 5$ and `collision().kind()` evaluates to `CollisionKind.ENTITY`.

### 37. Entity-in-Front Collision Precedence (`testEntityInFrontOfBlockPrecedence`)
* **Entities**: `LargeFireball`, mock `Player` at $x = 3$, stone wall at $x = 5$
* **Details**: Verifies that when an entity stands in front of a block wall along the trajectory segment, `firstCollision` evaluates to `CollisionKind.ENTITY`, `damageHitResult` targets the player, and visual `hitResult` anchors at the block wall.

### 38. Block-in-Front Collision Precedence (`testBlockInFrontOfEntityPrecedence`)
* **Entities**: `LargeFireball`, stone wall at $x = 3$, mock `Player` at $x = 5$
* **Details**: Verifies that when a block wall stands in front of an entity, the wall occludes the entity: `firstCollision` evaluates to `CollisionKind.BLOCK`, and both visual `hitResult` and `damageHitResult` anchor at the block wall.

### 39. CanHitEntity Projectile Accessor Fallback (`testCanHitEntityFallback`)
* **Entities**: `LargeFireball`, mock `Player`
* **Details**: Verifies that `TrajectoryPredictor.canHitEntity` successfully invokes `ProjectileAccessor.fireballpredictor$canHitEntity` to check valid entity targeting.

### 40. Dynamic Entity Movement Damage Prediction (`testDynamicEntityMovementDamagePrediction`)
* **Entities**: `LargeFireball`, mock `Player`, stone wall
* **Details**: Asserts that `TrajectoryPredictor.findDamageHitResult` dynamically updates as players move into, step out of, and return to the projectile's remaining path segments without requiring full trajectory recalculation.

### 41. Warning Projectile Type Resolution (`testWarningProjectileTypeResolution`)
* **Entities**: `LargeFireball`, `SmallFireball`, `DragonFireball`, `WitherSkull`, `WindCharge`
* **Details**: Validates that all projectile types resolve to their correct `WarningProjectileType` enum constants, custom icons, dragon fireball textures, and progress bar color themes.

### 42. Visual Themes Roster & Zero-Allocation Math (`testVisualThemesRosterAndColorMath`)
* **Details**: Validates that all 16 `VisualTheme` enum constants evaluate non-null display names, valid 24-bit RGB packed colors, fast LUT math, and deterministic alpha modulations without throwing exceptions or generating invalid color values.

### 43. Circular Theme Preview Gallery (`testThemePreviewGallery`)
* **Details**: Tests enabling and disabling the 3D `/fppreview` exhibition gallery, raycasting track targeting, chat confirmation prompt generation, and dynamic radius calculations.

### 44. Visual Theme Config Option Disabling (`testVisualThemeConfigOptionDisabling`)
* **Details**: Verifies that color options in `ModConfigGui` dynamically disable availability when non-default visual themes are active.

### 45. Negative Power Sentinel Fallthrough (`testNegativePowerSentinelFallsThrough`)
* **Details**: Tests that server `-1.0f` power cache entries correctly fall through to inferred power estimation.

### 46. Theme Time and Color Pins (`testThemeTimeAndColorPins`)
* **Details**: Verifies that theme animations freeze at zero speed and that `DEFAULT` core color brightening preserves pixel-identical parity.

### 47. Visual Themes Preview Representation (`testVisualThemesPreviewRepresentation`)
* **Details**: Validates that all visual themes evaluate correctly across the entire 0.0–1.0 progress and time spectrum for description panel previews.

### 48. Trajectory Dome Intercept Geometry (`testComputeTrajectoryDomeIntercept_Geometry`)
* **Details**: Asserts that `TrajectoryPredictor.computeTrajectoryDomeIntercept` accurately computes the entry unit vector on the shockwave dome boundary for vertical, horizontal, diagonal, and degenerate trajectory paths.

### 49. Per-Owner Power Inference Isolation (`testPerOwnerPowerInferenceIsolation`)
* **Details**: Validates that custom power 3.0 player explosions do not poison standard power 1.0 ghast fireball predictions, isolating mob owner types from player blasts.

### 50. Per-Owner Power Inference Multi-Upgrade (`testPerOwnerPowerInferenceUpgrade`)
* **Details**: Asserts that independent power inferences for `GHAST` (e.g. 2.0) and `PLAYER` (e.g. 3.5) coexist simultaneously and resolve correctly for subsequent fireballs.

### 51. Inference TTL Expiration (`testInferenceTtlExpiration`)
* **Details**: Verifies that `InferredPowerEntry` respects the 90-second TTL expiration threshold.

### 52. Inference TTL Refresh on New Shot (`testInferenceTtlRefreshOnNewShot`)
* **Details**: Asserts that observing a new active shot of an owner type refreshes the TTL timestamp of that owner's active inferred power.

### 53. Stable Enum Serialization (`testFireballOwnerPayloadStableEnumSerialization`)
* **Details**: Validates UTF-8 string encoding and decoding of `ProjectileOwner` enum names in `FireballOwnerPayload`, verifying fallback to `UNKNOWN` for invalid/unrecognized strings.

### 54. ClientPowerCache Encapsulation (`testClientPowerCacheEncapsulation`)
* **Details**: Tests accessor methods (`get`, `put`, `remove`, `clear`, `containsKey`) on `ClientPowerCache`.

### 55. Dispenser Power Inference Isolation (`testDispenserPowerInferenceIsolation`)
* **Details**: Verifies that un-inferred dispenser fireballs resolve to vanilla `1.0F` default and are protected against cross-pollution from previous power 3.5 player explosions.

### 56. Extreme Power Snapshot Safety & Degradation (`testExtremePowerSnapshotSafetyAndDegradation`)
* **Entity**: `LargeFireball` (configured with extreme explosion power 100)
* **Details**: Asserts that `TrajectoryPredictor.simulateTrajectory` returns `snapshot() == null` when bounding box volume exceeds `MAX_SNAPSHOT_BLOCKS = 65_536`, avoiding cubic memory allocation and main-thread freezes. Confirms that flight path ribbon and visual hit results remain preserved while predicted broken blocks degrade gracefully to an empty list.

### 57. Non-Breaking Projectile Snapshot Bypass (`testNonBreakingProjectileSnapshotBypass`)
* **Entities**: `WindCharge`, `SmallFireball`
* **Details**: Asserts that non-destructive projectiles (`profile.breaksBlocks() == false`) bypass `BlockStateSnapshot` creation entirely on the main thread (`snapshot() == null`).

### 58. Sparse BlockStateSnapshot Air Retrieval (`testSparseBlockStateSnapshotAirRetrieval`)
* **Environment**: Headless mock snapshot simulation.
* **Details**: Validates sparse null-coalescing state retrieval in `BlockStateSnapshot`: returns `Blocks.STONE` for solid blocks, non-empty `Fluids.WATER` for fluid blocks, default `Blocks.AIR` / empty fluids for air positions and out-of-bounds queries, and tests safe fallback on oversized constructor requests.

---

## Key Technical Solutions

### Rotated Velocity Vector Translation
Because the GameTest framework randomly rotates and mirrors test structures when positioning them in the batch grid, a static absolute velocity vector like `(0.5, 0.0, 0.0)` would cause projectiles to fly in wrong directions. 
We solve this by translating the velocity vector using the structure's rotation origin dynamically:
```java
Vec3 rotatedVelocity = context.getAbsolute(new Vec3(0.5, 0.0, 0.0))
                              .subtract(context.getAbsolute(Vec3.ZERO));
```

### High-Drag Projectile Range Capping
Vanilla charged wither skulls have a high drag constant of `0.73F` (compared to `0.95F` normally). Without active acceleration, their travel distance converges mathematically to `1.85` blocks. To guarantee a successful collision before the skull slows to a complete stop, the target wall is built at relative `x = 2` (only 0.5 blocks away from the starting position).

---

## Test Implementation Architecture

To ensure the test suite is maintainable and adheres to DRY principles, it is structured around several modular helper methods:
* **`buildWall`**: Overloaded helper to set up a 5x5 target wall of a given `Block` or `BlockState` at relative `x = 2`.
* **`spawnProjectile`**: Spawns any type of `ExplosiveProjectileEntity` (like `FireballEntity`, `WitherSkullEntity`, or `WindChargeEntity`) at a standardized starting relative position `(1.5, 3.0, 3.5)` with rotated velocity `(0.5, 0.0, 0.0)`.
* **`spawnMockPlayer`**: Spawns a headless mock `Player` at a given relative coordinate for damage, exposure, and collision testing.
* **`getBrokenBlocks`**: Scans the target wall area and collects all positions where the block type has changed.
* **`getPredictedBrokenBlocks`**: Simulates the trajectory of a projectile client-side to generate predicted broken block positions.
* **`assertExplosionDestruction`**: A parameterized assertion helper that verifies trajectory predictions match actual world impact for destructive test cases, enforcing 0 false negatives, a minimum 50% coverage ratio, and a strict 2.0x over-prediction cap.
* **`assertNoDestruction`**: An assertion helper verifying that no blocks are predicted to break or actually broken (e.g. for non-destructive interactions).

---

## Running the Tests

To run the GameTest suite headlessly, execute the following Gradle task in the project root:

```powershell
./gradlew runGameTest --no-daemon
```

### Expected Output
When all tests pass, you will see:
```text
[Server thread/INFO] (Minecraft) 60 tests are now running...
[Server thread/INFO] (Minecraft) Running test environment 'minecraft:default' batch 0 (50 tests)...
[Server thread/INFO] (Minecraft) Running test environment 'minecraft:default' batch 1 (10 tests)...
[Server thread/INFO] (Minecraft) [++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++]
[Server thread/INFO] (Minecraft) ========= 60 GAME TESTS COMPLETE IN 1.102 s ======================
[Server thread/INFO] (Minecraft) All 60 required tests passed :)
BUILD SUCCESSFUL
```
