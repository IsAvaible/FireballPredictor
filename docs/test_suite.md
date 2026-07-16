# Fireball Predictor Mod - GameTest Suite Documentation

This document describes the automated GameTest validation suite implemented for the Fireball Predictor mod. The test suite leverages Minecraft's vanilla GameTest framework to headlessly simulate projectile trajectories and assert that the mathematical prediction model remains accurate and free of regressions.

## Why We Use GameTest

Whenever Minecraft updates its version or changes its internal collision, drag, or projectile physics, manual in-game testing is tedious and prone to human error. This automated suite spawns actual projectiles in a controlled environment, runs the prediction logic, allows the projectile to detonate, and asserts that the predicted block destruction matches the actual world state.

---

## Test Scenarios

The suite is defined in [FireballPredictorGameTest.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/gametest/FireballPredictorGameTest.java) and consists of three scenarios using the empty structure pattern (`fabric-gametest-api-v1:empty`):

### 1. Ghast Fireball Prediction (`testFireballPredictionAndExplosion`)
* **Entity**: `FireballEntity`
* **Starting State**: Spawns at relative `(1.5, 3.0, 3.5)` with an initial relative velocity of `(0.5, 0.0, 0.0)` and an acceleration power of `0.05`.
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

### 6. Normal Fireball against Waterlogged Slabs (`testNormalFireballAgainstWaterloggedSlab`)
* **Entity**: `FireballEntity` (power 1.0)
* **Starting State**: Spawns at relative `(1.5, 3.0, 3.5)` with velocity `(0.5, 0.0, 0.0)` and acceleration `0.05`.
* **Environment**: A target wall of waterlogged `Blocks.OAK_SLAB` at relative `x = 2`.
* **Details**: Asserts that a normal fireball correctly predicts 0 broken blocks and breaks 0 blocks, since waterlogged blocks inherit the fluid water's high blast resistance (100.0).

### 7. Charged Wither Skull against Waterlogged Slabs (`testChargedWitherSkullAgainstWaterloggedSlab`)
* **Entity**: `WitherSkullEntity` (charged/blue)
* **Starting State**: Spawns at relative `(1.5, 3.0, 3.5)` with velocity `(0.5, 0.0, 0.0)`.
* **Environment**: A target wall of waterlogged `Blocks.OAK_SLAB` at relative `x = 2`.
* **Details**: Verifies that a charged wither skull correctly predicts and destroys waterlogged slabs, since the capping logic reduces the overall block/fluid blast resistance to `0.8F`.

### 8. High-Power Fireball Prediction (`testHighPowerFireballPredictionAndExplosion`)
* **Entity**: `FireballEntity` (configured with explosion power 3)
* **Starting State**: Spawns at relative `(1.5, 3.0, 3.5)` with velocity `(0.5, 0.0, 0.0)`.
* **Environment**: A target wall of `Blocks.DIRT` built at relative `x = 2`.
* **Details**: Uses the new `setExplosionPower` accessor to simulate a fireball with custom high explosion power (power = 3) and verifies that the mod correctly scales both the predicted block destruction and actual crater size.

---

## Key Technical Solutions

### Rotated Velocity Vector Translation
Because the GameTest framework randomly rotates and mirrors test structures when positioning them in the batch grid, a static absolute velocity vector like `(0.5, 0.0, 0.0)` would cause projectiles to fly in wrong directions. 
We solve this by translating the velocity vector using the structure's rotation origin dynamically:
```java
Vec3d rotatedVelocity = context.getAbsolute(new Vec3d(0.5, 0.0, 0.0))
                               .subtract(context.getAbsolute(Vec3d.ZERO));
```

### High-Drag Projectile Range Capping
Vanilla charged wither skulls have a high drag constant of `0.73F` (compared to `0.95F` normally). Without active acceleration, their travel distance converges mathematically to `1.85` blocks. To guarantee a successful collision before the skull slows to a complete stop, the target wall is built at relative `x = 2` (only 0.5 blocks away from the starting position).

---

## Test Implementation Architecture

To ensure the test suite is maintainable and adheres to DRY principles, it is structured around several modular helper methods:
* **`buildWall`**: Overloaded helper to set up a 5x5 target wall of a given `Block` or `BlockState` at relative `x = 2`.
* **`spawnProjectile`**: Spawns any type of `ExplosiveProjectileEntity` (like `FireballEntity` or `WitherSkullEntity`) at a standardized starting relative position `(1.5, 3.0, 3.5)` with rotated velocity `(0.5, 0.0, 0.0)`.
* **`getBrokenBlocks`**: Scans the target wall area and collects all positions where the block type has changed.
* **`getPredictedBrokenBlocks`**: Simulates the trajectory of a projectile client-side to generate predicted broken block positions.
* **`assertExplosionDestruction`**: A parameterized assertion helper that verifies trajectory predictions match actual world impact for destructive test cases.
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
[Server thread/INFO] (Minecraft) 9 tests are now running...
[Server thread/INFO] (Minecraft) Running test environment 'minecraft:default' batch 0 (9 tests)...
[Server thread/INFO] (Minecraft) [+++++++++]
[Server thread/INFO] (Minecraft) ========= 9 GAME TESTS COMPLETE IN 1.258 s ======================
[Server thread/INFO] (Minecraft) All 9 required tests passed :)
BUILD SUCCESSFUL
```
