# Fireball Visualization and Rendering

This document describes the client-side visual effects (VFX) used to represent predicted fireball trajectories and blast zones. All rendering is performed using standard Minecraft rendering frameworks, ensuring compatibility and stability.

## Implemented Visual Effects

### 1. Trajectory Ribbon Trail
- **Render Buffer**: Uses [PredictionRenderer.java](../src/main/java/com/simonconrad/fireballpredictor/client/render/PredictionRenderer.java) drawing to a standard translucent buffer (`RenderLayers.lightning()`).
- **Billboard Geometry**: Builds a 3D procedural billboarded ribbon by mapping coordinates along the predicted path. The ribbon's width is dynamically calculated based on the camera look vector to maintain visual thickness.
- **Core-and-Glow Dual Pass**: Draws a dual-pass ribbon consisting of a wider, soft outer shroud (base color with edge transparency) and a vibrant, high-alpha inner energy core (~35% width) to add volumetric depth.
- **Dynamic Taper & Landing Readability**: Tapers smoothly from 40% width / 30% alpha at the projectile position to full width over 1 tick, and tapers inward slightly near the collision point to pinpoint the exact landing location without cone distortion.
- **Motion & Pulse Effects**: Time-based sine-wave alpha pulsing (`enableRibbonPulse`) modulates alpha along the path, with frequency scaling near impact to build visual anticipation.
- **Visual Styles**: Configurable via `trajectoryStyle` (`SOLID`, `DASHED` HUD indicator style, or `CORE_ONLY` high-contrast minimalist line).

### 2. Shockwave Dome
- **Render Buffer**: Also uses `RenderLayers.lightning()` within [PredictionRenderer.java](../src/main/java/com/simonconrad/fireballpredictor/client/render/PredictionRenderer.java).
- **Geometric Dome**: Generated via a 3D sphere mesh algorithm using 16 latitude and 16 longitude bands. To optimize performance, the dome mesh is pre-computed and cached in `PredictionRenderData` during the tick prediction phase, rather than rebuilt every frame.
- **Blending**: Matches the entity's trajectory color (orange `255, 128, 0` for fireballs/skulls, or `windChargeShockwaveColor` white `255, 255, 255` for wind charges) with a low, semi-transparent maximum alpha of `60` at the equator, fading out towards the poles. This prevents visual clutter while clearly delineating the damage radius.

### 3. Dynamic Block Highlights (Cracking & Blinking)
- **Vanilla Breaking Overlay**: Instead of custom OpenGL blocks, the mod uses Minecraft's native breaking progress overlay via `client.world.setBlockBreakingInfo`.
- **Dynamic Progression**: The cracking stage (from 0 to 9) advances from stage 3 to stage 9 as the fireball gets closer to the predicted impact point, conveying flight progress.
- **Adaptive Blinking**: The highlighted blocks blink (toggling between visible and hidden) at a rate proportional to the remaining ticks. As the fireball nears impact, the blinking frequency increases rapidly.
- **Cleanup**: In [FireballPredictorClient.java](../src/main/java/com/simonconrad/fireballpredictor/client/FireballPredictorClient.java), when a prediction changes or a fireball despawns, the mod clears all breaking overlays by resetting the stages to `-1`.

### 4. Ambient Particle Accents
- **Heat Visuals**: Randomly spawns client-side `FLAME`, `LAVA`, and `CAMPFIRE_COSY_SMOKE` particles on top of the predicted breakable blocks.
- **Density**: Simulates heat build-up prior to impact. The spawning is throttle-controlled in [FireballPredictorClient.java](../src/main/java/com/simonconrad/fireballpredictor/client/FireballPredictorClient.java) to maintain high performance and automatically paused when the game is paused.

### 5. HUD Impact Warning Badge
- **Collision Warning**: When the local player is directly in the path of an incoming projectile, [PredictionRenderer.java](../src/main/java/com/simonconrad/fireballpredictor/client/render/PredictionRenderer.java) renders an anchorable HUD warning badge.
- **Entity Icons & Colors**: For fireballs/skulls, renders a fire charge item icon and standard progress bar. For wind charges, renders the `Items.WIND_CHARGE` item icon and a `#cfd6f7` progress bar.

## Rendering System Integration

- **Event Registration**: Render calls are hooked into the Fabric rendering pipeline via `WorldRenderEvents.END_MAIN` in [FireballPredictorClient.java](../src/main/java/com/simonconrad/fireballpredictor/client/FireballPredictorClient.java). This ensures that transparent rendering elements sort correctly against other translucent objects in the world (such as water or glass).
- **YACL Config Integration**: In [ModConfig.java](../src/main/java/com/simonconrad/fireballpredictor/config/ModConfig.java), users can individually toggle and customize these features:
  - `renderTrajectory`: Enables/disables the ribbon path.
  - `trajectoryStyle`: Selects visual style (`solid`, `dashed`, `core_only`).
  - `renderCoreGlow`: Enables/disables the inner energy core pass.
  - `enableRibbonPulse`: Enables/disables the time-based alpha motion pulsing.
  - `renderShockwaveDome`: Enables/disables the 3D blast sphere.
  - `renderBlockHighlights`: Enables/disables the cracking animation overlay.
  - `renderParticleAccents`: Enables/disables the ambient particles.
  - `trajectoryColor` & `shockwaveColor`: Custom color configuration for fireballs and wither skulls.
  - `windChargeTrajectoryColor` & `windChargeShockwaveColor`: Custom color configuration for wind charges (defaults to white).
- **ModMenu Screen**: Configured in [ModMenuIntegration.java](../src/main/java/com/simonconrad/fireballpredictor/client/compat/ModMenuIntegration.java), allowing real-time toggle of visual effects in-game.

## Verification & Environment Compatibility
- **Renderer Performance**: Completely client-side; has zero impact on server ticks.
- **Compat Notes**: Using standard `RenderLayers.lightning()` (which is fully supported by optimization and shader mods like Sodium and Iris) and vanilla breaking progress prevents compatibility issues with modern rendering environments.
- **Testing Rendering with Shaders**:
  - Run **Vanilla**: `.\gradlew runClient`
  - Run with **Sodium + Iris + Shader**: `.\gradlew runClient -Pshaders` or `.\gradlew runClientWithShaders`
  - Automatically downloads and pre-selects the **Complementary Reimagined** shader pack in `run/config/iris.properties` for testing rendering pipeline compatibility under performance mods.
- **Java Requirements**: Source and target code compile under Java 21, conforming to modern Fabric Loader standards.
