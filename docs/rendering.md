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
- **Fresnel Rim Effect**: Per-vertex Schlick Fresnel (`F0 = 0.04`, exponent 5) is evaluated on the CPU in [PredictionFeatureRenderer.java](../src/main/java/com/simonconrad/fireballpredictor/client/render/PredictionFeatureRenderer.java) and baked into the vertex alpha. Patches facing the camera become transparent while the silhouette rim (grazing angle) is pushed toward the alpha ceiling, giving the dome a glass-bubble look that tracks the camera. Because culling is disabled, the far side of the hemisphere receives the full rim term and reads as the bright shell of the blast. The latitude profile remains as a base density; `domeFresnelStrength` blends between the legacy flat profile (0) and full Fresnel shading (1).
- **Pulse Animation**: Pulsates gracefully using a time-based sine wave algorithm to draw player attention.

### 3. Block Break Highlights & Mining Safeguards
- **Vanilla Cracking Overlay**: Sends virtual `destroyBlockProgress` network packets directly to the client render engine.
- **Phase Mapping**: Maps predicted explosion damage cleanly to block destruction stages `0` through `9`.
- **Flashing Pre-Impact Alert**: Oscillates cracking severity as the fireball gets closer to impact.
- **Depth & Overlay Safeguards**: Translucent prediction rendering uses `depthWrite = false` in `PredictionPipelines.PREDICTION` to prevent depth buffer conflicts with block breaking overlays (`CRUMBLING`), ensuring vanilla cracking overlays remain completely legible without obscuring block mining progress.

### 4. Ambient Particle Accents
- **Heat Visuals**: Randomly spawns client-side `FLAME`, `LAVA`, and `CAMPFIRE_COSY_SMOKE` particles on top of the predicted breakable blocks.
- **Density**: Simulates heat build-up prior to impact. The spawning is throttle-controlled in [FireballPredictorClient.java](../src/main/java/com/simonconrad/fireballpredictor/client/FireballPredictorClient.java) to maintain high performance and automatically paused when the game is paused.

### 5. HUD Impact Warning Badge
- **Collision Warning**: When the local player is directly in the path of an incoming projectile, [PredictionRenderer.java](../src/main/java/com/simonconrad/fireballpredictor/client/render/PredictionRenderer.java) renders an anchorable HUD warning badge.
- **Progress Bar**: Displays a dynamic countdown bar indicating remaining travel time before impact. Supports wind charges with a custom icon and color palette.

---

## Mod Configuration

- **Event Registration**: Render calls are hooked into the Fabric rendering pipeline via `LevelRenderEvents.END_MAIN` in [FireballPredictorClient.java](../src/main/java/com/simonconrad/fireballpredictor/client/FireballPredictorClient.java). This ensures that transparent rendering elements sort correctly against other translucent objects in the world (such as water or glass).
- **YACL Config Integration**: In [ModConfig.java](../src/main/java/com/simonconrad/fireballpredictor/config/ModConfig.java), users can individually toggle and customize these features across General, Visuals, and Tracking categories:
  - `renderTrajectory`: Enables/disables the ribbon path.
  - `trajectoryWidth`: Line width multiplier for the trajectory ribbon trail (`0.1` to `2.0`).
  - `trajectoryStyle`: Selects visual style (`SOLID`, `DASHED`, `CORE_ONLY`).
  - `renderCoreGlow`: Enables/disables the inner energy core pass.
  - `enableRibbonPulse`: Enables/disables the time-based alpha motion pulsing.
  - `renderShockwaveDome`: Enables/disables the 3D blast sphere.
  - `domeFresnelStrength`: Strength of the Fresnel rim glow on the shockwave dome (0 = legacy flat shading, 1 = full Fresnel).
  - `renderBlockHighlights`: Enables/disables the cracking animation overlay.
  - `renderParticleAccents`: Enables/disables the ambient particles.
  - `trajectoryColor` & `shockwaveColor`: Custom color configuration for fireballs and wither skulls.
  - `windChargeTrajectoryColor` & `windChargeShockwaveColor`: Custom color configuration for wind charges (defaults to white).
  - `renderImpactWarning`, `impactWarningBadgeAnchor`, `impactWarningBadgeOffsetX/Y`: HUD collision warning badge visibility, screen anchor alignment, and pixel offsets.
  - `globalFallbackFireballPower`, `serverFallbackPowers`, `rayPowerMultiplier`: Fallback explosion power levels, per-server IP power overrides, and ray simulation blast resistance scaling.
  - `trackProjectiles`, `trackMobProjectiles`, `trackOtherOwnerProjectiles`: Hierarchical master, mob-master, and non-mob master switches.
  - Per-source filters: `trackFireballs`, `trackWitherSkulls`, `trackWindCharges`, `trackBlazeFireballs`, `trackGhastFireballs`, `trackEnderDragonFireballs`, `trackWitherMob`, `trackPlayerProjectiles`, `trackDispenserProjectiles`, `trackCommandProjectiles`.

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

---

## Config Screen Live Previews

YACL3 description side-panel previews are implemented by [[ConfigPreviewRenderer.java](../src/main/java/com/simonconrad/fireballpredictor/client/gui/preview/ConfigPreviewRenderer.java)] implementing `dev.isxander.yacl3.gui.image.ImageRenderer`.

Options under the **Visuals** category annotate `@CustomImage(factory = …)` so the description panel shows a live schematic while you edit:

| Mode | Factory | Reflects pending values of |
| --- | --- | --- |
| Trajectory ribbon | `TrajectoryFactory` / `TrajectoryWindFactory` | `renderTrajectory`, `trajectoryColor` / `windChargeTrajectoryColor`, `trajectoryWidth`, `trajectoryStyle`, `renderCoreGlow`, `enableRibbonPulse` |
| Shockwave dome | `ShockwaveFactory` / `ShockwaveWindFactory` | `renderShockwaveDome`, `renderBlockHighlights`, `shockwaveColor` / `windChargeShockwaveColor`, `domeFresnelStrength` |
| HUD warning badge | `HudFactory` | `renderImpactWarning`, `impactWarningBadgeAnchor`, `impactWarningBadgeOffsetX/Y` |

Each frame the renderer reads `Option.pendingValue()` via the autogen `OptionAccess`, so colour pickers, cyclers, and sliders update the schematic immediately without saving.
