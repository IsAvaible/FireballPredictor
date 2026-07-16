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
  * Capping block blast resistance at `4.0` in the prediction.
  * Handling the high drag (`0.73F` per tick) behavior unique to charged skulls.

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

## Running the Tests

To run the GameTest suite headlessly, execute the following Gradle task in the project root:

```powershell
./gradlew runGameTest --no-daemon
```

### Expected Output
When all tests pass, you will see:
```text
[Server thread/INFO] (Minecraft) 4 tests are now running...
[Server thread/INFO] (Minecraft) Running test environment 'minecraft:default' batch 0 (4 tests)...
[Server thread/INFO] (Minecraft) [++++]
[Server thread/INFO] (Minecraft) ========= 4 GAME TESTS COMPLETE IN 1.096 s ======================
[Server thread/INFO] (Minecraft) All 4 required tests passed :)
BUILD SUCCESSFUL
```
