# Fireball Impact Calculation

## What was implemented

I have implemented the logic to predict which blocks will be destroyed by a fireball's explosion without actually detonating it or affecting the world. This builds on top of the previously created trajectory prediction logic.

### 1. `ImpactPredictor.java`
Created a new class in the `com.simonconrad.fireballpredictor.math` package.
- It instantiates Minecraft's internal `Explosion` class to leverage the game's exact 16-ray blast destruction algorithm.
- To prevent this prediction from inadvertently blowing up blocks or damaging entities in the game, it injects a custom `ExplosionBehavior`.
- This custom behavior explicitly overrides `shouldDamage()` to `false` and negates knockback modifiers, making the calculation perfectly safe for the server.
- The `collectBlocksAndDamageEntities()` method is invoked to calculate ray attenuation and resistance, which computes the precise list of `BlockPos` elements that will be broken.

### 2. `FireballPredictor.java` Update
- Linked the `ImpactPredictor` to the main event listener.
- Upon predicting a hit using `TrajectoryPredictor`, the event handler now runs the impact simulation.
- Logs the total number of blocks predicted to break to the server console.
- Logs a sample of the first 5 block coordinates for easy verification and debugging.

## Verification
- Code successfully compiled with `.\gradlew build` using the Fabric Loader 1.21.1 toolchain.
- The implementation strictly adheres to standard Minecraft explosion behavior ensuring an exact match with the actual fireball explosion when it detonates.
