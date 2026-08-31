# Fireball Visualization and Rendering

This document describes the client-side visual effects (VFX) used to represent predicted fireball trajectories and blast zones. All rendering is performed using standard Minecraft rendering frameworks, ensuring compatibility and stability.

## Implemented Visual Effects

### 1. Trajectory Ribbon Trail
- **Render Buffer**: Shared custom `RenderPipeline` ([PredictionPipelines.java](../src/main/java/com/simonconrad/fireballpredictor/client/render/PredictionPipelines.java)), registered as a mod-owned pipeline built from `RenderPipelines.DEBUG_FILLED_SNIPPET` (`core/position_color`, `POSITION_COLOR` quad format, `BlendFunction.TRANSLUCENT`, `DepthStencilState(GREATER_THAN_OR_EQUAL, depthWrite = false)`, `withCull(false)`). Vertices carry UV=(0,0) and a full-bright lightmap so output equals the configured vertex color.
- **Billboard Geometry**: Builds a 3D procedural billboarded ribbon by mapping coordinates along the predicted path. The ribbon's width is dynamically calculated based on the camera look vector to maintain visual thickness.
- **Core-and-Glow Dual Pass**: Draws a dual-pass ribbon consisting of a wider, soft outer shroud (base color with edge transparency) and a vibrant, high-alpha inner energy core (~35% width) to add volumetric depth.
- **Dynamic Taper & Landing Readability**: Tapers smoothly from 40% width / 30% alpha at the projectile position to full width over 1 tick, and tapers inward slightly near the collision point to pinpoint the exact landing location without cone distortion.
- **Motion & Pulse Effects**: Time-based sine-wave alpha pulsing (`enableRibbonPulse`) modulates alpha along the path, with frequency scaling near impact to build visual anticipation.
- **Visual Styles**: Configurable via `trajectoryStyle` (`SOLID`, `DASHED` HUD indicator style, or `CORE_ONLY` high-contrast minimalist line).

### 2. Shockwave Dome
- **Render Buffer**: Shares `PredictionPipelines.PREDICTION`; dome quads are emitted first so the ribbon blends on top.
- **Procedural Dome Quads**: Renders a procedural hemisphere built from smooth quadrilateral latitude/longitude strips.
- **Pulse Animation**: Pulsates gracefully using a time-based sine wave algorithm to draw player attention.

### 3. Block Break Highlights & Mining Safeguards
- **Vanilla Cracking Overlay**: Sends virtual `destroyBlockProgress` network packets directly to the client render engine.
- **Phase Mapping**: Maps predicted explosion damage cleanly to block destruction stages `0` through `9`.
- **Flashing Pre-Impact Alert**: Oscillates cracking severity as the fireball gets closer to impact.
- **Mining Alpha Fade & Crumbling Safeguards**: `RenderSetup` for the overlay is constructed without `affectsCrumbling()` to prevent the block-break pass from re-drawing the prediction buffer. `PredictionRenderer.breakingFade(...)` scales trail and dome alpha down to 45% (`BREAKING_FADE = 0.45f`) while actively mining (`client.gameMode.isDestroying()`), ensuring vanilla cracking overlays (`CRUMBLING`) remain completely legible.

### 4. Ambient Particle Accents
- **Heat Visuals**: Randomly spawns client-side `FLAME`, `LAVA`, and `CAMPFIRE_COSY_SMOKE` particles on top of the predicted breakable blocks.
- **Density**: Simulates heat build-up prior to impact. The spawning is throttle-controlled in [FireballPredictorClient.java](../src/main/java/com/simonconrad/fireballpredictor/client/FireballPredictorClient.java) to maintain high performance and automatically paused when the game is paused.

### 5. HUD Impact Warning Badge
- **Collision Warning**: When the local player is directly in the path of an incoming projectile, [PredictionRenderer.java](../src/main/java/com/simonconrad/fireballpredictor/client/render/PredictionRenderer.java) renders an anchorable HUD warning badge.
- **Progress Bar**: Displays a dynamic countdown bar indicating remaining travel time before impact. Supports wind charges with a custom icon and color palette.

---

## Mod Configuration

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

---

## Sodium & Iris Custom Render Pipeline Compatibility

Shader packs managed by Iris modify the render pipeline lookup mechanism. Custom translucent overlays require explicit pipeline registration and shader program assignment to display correctly alongside active shaders.

### 1. Custom Pipeline (`PredictionPipelines.PREDICTION`)
- **Base**: `RenderPipelines.DEBUG_FILLED_SNIPPET` (`POSITION_COLOR` quad format, `BlendFunction.TRANSLUCENT`, `DepthStencilState(GREATER_THAN_OR_EQUAL, depthWrite = false)`).
- **Culling**: Configured with `.withCull(false)` so both inner and outer surfaces of the ribbon billboard and dome hemisphere are rendered.
- **Cracking Overlay Compatibility**: Using `depthWrite = false` prevents depth buffer conflicts with block breaking overlays (`CRUMBLING`), ensuring block mining crack animations remain legible under all conditions.

### 2. Iris Shader Program Registration (`IrisCompat`)
- **Primary Registration (`ShaderKey.LIGHTNING`)**: Via internal reflection, `IrisCompat` assigns `PredictionPipelines.PREDICTION` to Iris `ShaderKey.LIGHTNING`. This routes prediction rendering to `gbuffers_lightning`, which shader packs treat as emissive fullbright geometry without dark terrain shading or alpha-testing pixel discards (`AlphaTests.OFF`).
- **Fallback Registration (`IrisProgram.BASIC`)**: If internal APIs are unavailable, `IrisCompat` falls back to public `IrisApi.assignPipeline(..., IrisProgram.BASIC)` (`gbuffers_basic`). *Note: Fallback geometry receives G-buffer lighting and alpha-discarding, which may appear darker with sharper edges.*
- **No Shadow Casting**: Shadow passes remain unassigned so HUD-like trajectory ribbons and blast domes do not cast world shadows.
- **Soft-Loading & Safety**: Guarded by `FabricLoader.getInstance().isModLoaded("iris")` to prevent class-loading exceptions when Iris is not installed.


