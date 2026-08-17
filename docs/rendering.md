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
- **Collision Warning**: When the local player is directly in the path of an incoming projectile or within its blast danger radius, [PredictionRenderer.java](../src/main/java/com/simonconrad/fireballpredictor/client/render/PredictionRenderer.java) renders an anchorable HUD warning badge via `HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, ...)`.
- **Projectile Category Theming ([WarningProjectileType.java](../src/main/java/com/simonconrad/fireballpredictor/client/render/WarningProjectileType.java))**: The warning badge adapts dynamically to the incoming projectile type:
  - **Fireballs** (`FIREBALL`): Renders `Items.FIRE_CHARGE` with a fiery orange progress bar (`#FFE67A00`).
  - **Wither Skulls** (`WITHER_SKULL`): Renders `Items.WITHER_SKELETON_SKULL` with a slate-grey progress bar (`#FFA0A8B0`).
  - **Wind Charges** (`WIND_CHARGE`): Renders `Items.WIND_CHARGE` with an ice-blue progress bar (`#FFCFD6F7`).
  - **Dragon Fireballs** (`DRAGON_FIREBALL`): Renders a custom dragon fireball texture (`textures/entity/enderdragon/dragon_fireball.png`, fallback `Items.DRAGON_HEAD`) with a distinct magenta/purple progress bar (`#FFC832D4`).
- **Dynamic Countdown Progress Bar**: Fills or depletes smoothly based on the ratio of remaining travel ticks to total trajectory flight time.
- **Shared Positioning Helper**: `PredictionRenderer.impactBadgePosition(client)` calculates the exact on-screen position accounting for screen dimensions, anchor placement, and user-configured X/Y pixel offsets.

### 6. Cracking Damage Hearts Overlay & Knockback Readout ([HeartOverlayRenderer.java](../src/main/java/com/simonconrad/fireballpredictor/client/render/HeartOverlayRenderer.java))
- **Health Bar Overlay**: Hooked after `VanillaHudElements.HEALTH_BAR` via Fabric HUD Element Registry. Overlays fiery, cracking heart sprites directly on top of the health bar to show the exact health/absorption points predicted to be lost upon detonation.
- **Two-Stage Damage Allocation**: Replicates Minecraft's vanilla damage consumption order:
  1. Absorption hearts are consumed first, starting from the highest absorption point.
  2. Any remaining unmitigated damage consumes current health, starting from the highest health point.
- **Independent Half-Heart Evaluation**: Evaluates left and right half-heart units independently per slot to support odd health values, partial absorption, and multiple stacked heart rows without visual misalignments.
- **Flashing Pre-Impact Alert**: Alternates between steady and blinking sprite states based on `player.level().getGameTime()`:
  - `hud/heart/cracking_full` / `hud/heart/cracking_full_blinking`
  - `hud/heart/cracking_half` / `hud/heart/cracking_half_blinking`
  - `hud/heart/cracking_half_right` / `hud/heart/cracking_half_right_blinking`
- **Damage & Knockback Readout**: Renders a compact, high-contrast text readout (e.g. `-4.5❤  ⚡12.3b/s`) next to the impact warning badge indicating exact heart loss and predicted initial knockback velocity in blocks per second. Supports displaying knockback even when damage is zero (e.g. Wind Charges or heavy blast protection). Automatically mirrors alignment (left vs right of badge) depending on screen anchor.

---

## Mod Configuration

- **Event Registration**: Render calls are hooked into the Fabric rendering pipeline via `LevelRenderEvents.END_MAIN` in [FireballPredictorClient.java](../src/main/java/com/simonconrad/fireballpredictor/client/FireballPredictorClient.java). This ensures that transparent rendering elements sort correctly against other translucent objects in the world (such as water or glass). HUD overlays are registered via Fabric's `HudElementRegistry`.
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
  - `renderDamageHeartsOverlay`: Enables/disables the cracking hearts overlay on the player's health bar.
  - `showKnockbackEstimator`: Enables/disables the numerical damage and knockback speed text readout next to the impact badge.
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

YACL3 description side-panel previews are implemented by [ConfigPreviewRenderer.java](../src/main/java/com/simonconrad/fireballpredictor/client/gui/preview/ConfigPreviewRenderer.java) implementing `dev.isxander.yacl3.gui.image.ImageRenderer`.

Options under the **Visuals** and **Tracking** categories annotate `@CustomImage(factory = …)` so the description panel shows a live schematic while you edit:

| Mode | Factory | Reflects pending values of |
| --- | --- | --- |
| Trajectory ribbon | `TrajectoryFactory` / `TrajectoryWindFactory` | `renderTrajectory`, `trajectoryColor` / `windChargeTrajectoryColor`, `trajectoryWidth`, `trajectoryStyle`, `renderCoreGlow`, `enableRibbonPulse` |
| Shockwave dome | `ShockwaveFactory` / `ShockwaveWindFactory` | `renderShockwaveDome`, `renderBlockHighlights`, `shockwaveColor` / `windChargeShockwaveColor`, `domeFresnelStrength` |
| HUD warning badge | `HudFactory` | `renderImpactWarning`, `impactWarningBadgeAnchor`, `impactWarningBadgeOffsetX/Y` |
| Damage hearts overlay | `DamageHeartsFactory` | `renderDamageHeartsOverlay` |
| Damage & knockback readout | `KnockbackEstimatorFactory` | `showKnockbackEstimator` |
| Tracking overviews | `TrackMasterFactory` / `TrackMobMasterFactory` / `TrackOtherMasterFactory` | Master chip overviews & source toggles |
| Single tracking lock-on | `TrackFireballFactory` / `TrackWitherFactory` / `TrackWindFactory` / etc. | Per-source target tracking toggles |

Each frame the renderer reads `Option.pendingValue()` via the autogen `OptionAccess`, so colour pickers, cyclers, and sliders update the schematic immediately without saving.

### Modular Preview Architecture (`com.simonconrad.fireballpredictor.client.gui.preview`)
- **[Painter.java](../src/main/java/com/simonconrad/fireballpredictor/client/gui/preview/Painter.java)**: Immediate-mode drawing context handling clipping rects and GUI primitives.
- **[Arc.java](../src/main/java/com/simonconrad/fireballpredictor/client/gui/preview/Arc.java)**: Bezier curve computation for 2D schematic flight paths.
- **[TrajectoryRenderer.java](../src/main/java/com/simonconrad/fireballpredictor/client/gui/preview/TrajectoryRenderer.java)**: Renders 2D animated path with ribbon width, color, pulse wave, core glow, and `SOLID`/`DASHED`/`CORE_ONLY` styles.
- **[ShockwaveRenderer.java](../src/main/java/com/simonconrad/fireballpredictor/client/gui/preview/ShockwaveRenderer.java)**: Renders 3x3 block grid, animated dome disc via banded horizontal scanlines, Fresnel rim shading, and crack overlays.
- **[HudRenderer.java](../src/main/java/com/simonconrad/fireballpredictor/client/gui/preview/HudRenderer.java)**: Renders miniature screen frame showing HUD anchor alignment, X/Y pixel offsets, and dynamic progress bar.
- **[DamageEstimatorRenderer.java](../src/main/java/com/simonconrad/fireballpredictor/client/gui/preview/DamageEstimatorRenderer.java)**: Renders animated cracking damage hearts on a 10-heart health bar with rising fiery embers, and the impact badge with damage/knockback readout.
- **[TrackingRenderer.java](../src/main/java/com/simonconrad/fireballpredictor/client/gui/preview/TrackingRenderer.java)**: Renders master chip overviews and target lock-on badges.
- **[RenderUtils.java](../src/main/java/com/simonconrad/fireballpredictor/client/gui/preview/RenderUtils.java)**: Color interpolation and alpha math helpers with dynamic icon texture cache invalidation on resource reload.

