# Fireball Visualization and Rendering

This document describes the client-side visual effects (VFX) used to represent predicted fireball trajectories and blast zones. All rendering is performed using standard Minecraft rendering frameworks, ensuring compatibility and stability.

## Implemented Visual Effects

### 1. Trajectory Ribbon Trail
- **Render Setup**: Passes rendering state via [PredictionRenderer.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/client/render/PredictionRenderer.java) to a custom feature renderer [PredictionFeatureRenderer.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/client/render/PredictionFeatureRenderer.java), drawing to the translucent `FIREBALL_TRAIL` render type (configured with the `RenderPipelines.LIGHTNING` pipeline).
- **Billboard Geometry**: Builds a 3D procedural billboarded ribbon by mapping coordinates along the predicted path. The ribbon's width is dynamically calculated based on the camera look vector to maintain visual thickness.
- **Color and Alpha Gradients**: Colored orange (`255, 128, 0`). The edges are set to an alpha of `0` to create a soft, blurred glow. The center alpha fades from `200` at the start to `60` at the end to seamlessly merge with the impact shockwave dome.

### 2. Shockwave Dome
- **Render Setup**: Also uses [PredictionFeatureRenderer.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/client/render/PredictionFeatureRenderer.java) to draw to the `SHOCKWAVE_DOME` render type, which is configured with a custom `PREDICTION_PIPELINE` translucent pipeline registry.
- **Geometric Dome**: Generated via a 3D sphere mesh algorithm using 16 latitude and 16 longitude bands. To optimize performance, the dome mesh is pre-computed and cached in `PredictionRenderData` during the tick prediction phase, rather than rebuilt every frame.
- **Blending**: Matches the orange trajectory color (`255, 128, 0`) with a low, semi-transparent maximum alpha of `60` at the equator, fading out towards the poles. This prevents visual clutter while clearly delineating the damage radius.

### 3. Dynamic Block Highlights (Cracking & Blinking)
- **Vanilla Breaking Overlay**: Instead of custom OpenGL blocks, the mod uses Minecraft's native breaking progress overlay via `client.world.setBlockBreakingInfo`.
- **Dynamic Progression**: The cracking stage (from 0 to 9) advances from stage 3 to stage 9 as the fireball gets closer to the predicted impact point, conveying flight progress.
- **Adaptive Blinking**: The highlighted blocks blink (toggling between visible and hidden) at a rate proportional to the remaining ticks. As the fireball nears impact, the blinking frequency increases rapidly.
- **Cleanup**: In [FireballPredictorClient.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/client/FireballPredictorClient.java), when a prediction changes or a fireball despawns, the mod clears all breaking overlays by resetting the stages to `-1`.

### 4. Ambient Particle Accents
- **Heat Visuals**: Randomly spawns client-side `FLAME`, `LAVA`, and `CAMPFIRE_COSY_SMOKE` particles on top of the predicted breakable blocks.
- **Density**: Simulates heat build-up prior to impact. The spawning is throttle-controlled in [FireballPredictorClient.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/client/FireballPredictorClient.java) to maintain high performance.

## Rendering System Integration

- **Event & Submission Phase**: In `LevelRenderEvents.END_MAIN` (registered in [FireballPredictorClient.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/client/FireballPredictorClient.java)), `PredictionRenderer.render()` is called. Rather than direct immediate rendering, it constructs a `PredictionSubmit` record (containing trajectory path, camera look vectors, colors, and copied JOML `Matrix4f` pose matrices) and submits it to the `translucentModels` queue in `SubmitNodeCollection`.
- **Execution & Dispatch Phase**: The Minecraft 26.2 rendering engine sorts and batches submits. The custom [PredictionFeatureRenderer.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/client/render/PredictionFeatureRenderer.java) handles `PredictionSubmit` and writes the quad vertices to the trail and dome buffers.
- **Renderer Registration**: Registered via [FeatureRenderDispatcherMixin.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/mixin/FeatureRenderDispatcherMixin.java), which injects into the `FeatureRenderDispatcher` constructor to put `PredictionFeatureRenderer.TYPE` into the dispatcher's `featureRenderers` map.
- **YACL Config Integration**: In [ModConfig.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/config/ModConfig.java), users can individually toggle these features:
  - `renderTrajectory`: Enables/disables the ribbon path.
  - `renderShockwaveDome`: Enables/disables the 3D blast sphere.
  - `renderBlockHighlights`: Enables/disables the cracking animation overlay.
  - `renderParticleAccents`: Enables/disables the ambient particles.
- **ModMenu Screen**: Configured in [ModMenuIntegration.java](file:///c:/Users/simon/Documents/Programming/MinecraftModding/FireballPredictor/src/main/java/com/simonconrad/fireballpredictor/client/compat/ModMenuIntegration.java), allowing real-time toggle of visual effects in-game.

## Verification & Environment Compatibility
- **Renderer Performance**: Completely client-side; has zero impact on server ticks.
- **Compat Notes**: Using modern translucent rendering setups and vanilla breaking progress prevents compatibility issues with modern rendering environments and shaders (Sodium/Iris).
- **Java Requirements**: Source and target code compile under Java 25, conforming to modern Fabric Loader standards.
