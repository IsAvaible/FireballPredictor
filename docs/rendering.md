# World & HUD Rendering Pipeline (Forge 1.8.9)

This document describes the world-space and HUD overlay rendering pipeline for the Minecraft 1.8.9 Forge backport of **Fireball Predictor**.

---

## 1. 3D World Rendering Pipeline ([PredictionRenderer.java](../src/main/java/com/simonconrad/fireballpredictor/render/PredictionRenderer.java))

World rendering hooks into Forge's `RenderWorldLastEvent`. Rendering is performed in camera-relative coordinates using Minecraft 1.8.9's OpenGL 1.1 / 2.0 immediate-mode wrappers (`GlStateManager`, `Tessellator`, `WorldRenderer`).

### 1.1 OpenGL State Lifecycle

To render clean translucent effects without visual artifacts or depth fighting with block break animations:

```java
// Setup (PredictionRenderer.setupTranslucent)
GlStateManager.pushMatrix();
GlStateManager.translate(-viewerPosX, -viewerPosY, -viewerPosZ);
GlStateManager.disableTexture2D();
GlStateManager.enableBlend();
GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
GlStateManager.depthMask(false);    // Prevents writing to depth buffer, allowing block cracks underneath
GlStateManager.disableCull();       // Double-sided ribbon and dome quads
GlStateManager.disableLighting();   // Fullbright emission without dark terrain shading

// ... draw immediate-mode quads with DefaultVertexFormats.POSITION_COLOR ...

// Teardown (PredictionRenderer.restoreTranslucent)
GlStateManager.enableLighting();
GlStateManager.enableCull();
GlStateManager.depthMask(true);
GlStateManager.disableBlend();
GlStateManager.enableTexture2D();
GlStateManager.popMatrix();
```

---

## 2. Trajectory Ribbon Geometry

The trajectory is rendered as an animated, camera-facing billboard ribbon with soft edge falloff.

### 2.1 Billboard Normal Calculation

For each segment between trajectory points $\vec{p}_1$ and $\vec{p}_2$:
1. Direction vector:
   $$\hat{d} = \frac{\vec{p}_2 - \vec{p}_1}{\|\vec{p}_2 - \vec{p}_1\|}$$
2. Perpendicular billboard vector relative to camera look vector $\hat{l}$:
   $$\vec{p} = \hat{d} \times \hat{l}$$
   If $\|\vec{p}\| < 10^{-3}$ (camera looking directly along the flight line), falls back to the horizontal perpendicular $\vec{p} = (d_z, 0, -d_x)$.
3. Normalized perpendicular:
   $$\hat{p} = \frac{\vec{p}}{\|\vec{p}\|}$$

### 2.2 Dual-Quad Soft Edge Profile

Each segment is extruded into two quads using 4 vertices across: $[\vec{p}_1 + \hat{p} w_1, \vec{p}_1, \vec{p}_2, \vec{p}_2 + \hat{p} w_2]$ and $[\vec{p}_1, \vec{p}_1 - \hat{p} w_1, \vec{p}_2 - \hat{p} w_2, \vec{p}_2]$.

```
Outer Edge (Alpha = 0)  ──────────────────────────────────────────  p2 + r2
                              ▲ Quad 1 (Fading)
Center Line (Alpha = Max) ══════════════════════════════════════════  p2 (Center)
                              ▼ Quad 2 (Fading)
Outer Edge (Alpha = 0)  ──────────────────────────────────────────  p2 - r2
```

* **Width & Taper**: Width starts at `ModConfig.trajectoryWidth` (default: $0.12$ blocks) and tapers to $0$ over the final 20% of the flight path.
* **Alpha Attenuation**: Alpha decays quadratically from origin to impact point:
  $$\alpha(\text{prog}) = \left(200 - 140 \times \text{prog}^2\right) \times \alpha_{\text{blend}}$$

---

## 3. Shockwave Blast Dome Rendering

The blast dome is drawn using the pre-computed `DomeMesh` geometry centered at the predicted impact coordinate $\vec{c}$.

### 3.1 Fresnel Rim Calculation

To create a holographic energy sphere appearance where the rim shines brighter than the center:

1. Vertex world position: $\vec{w} = \vec{c} + \vec{v}$.
2. Surface normal unit vector: $\hat{n} = \frac{\vec{v}}{\|\vec{v}\|}$.
3. View direction vector: $\hat{u} = \frac{\vec{w} - \vec{c}_{\text{cam}}}{\|\vec{w} - \vec{c}_{\text{cam}}\|}$.
4. Fresnel coefficient:
   $$F = 0.35 + 0.65 \times (1.0 - |\hat{n} \cdot \hat{u}|)^2$$
5. Final Vertex Alpha:
   $$\alpha_{\text{final}} = \min(110, \alpha_{\text{mesh}} \times F)$$

---

## 4. Block Destruction Highlighting ([FireballPredictorClient.java](../src/main/java/com/simonconrad/fireballpredictor/client/FireballPredictorClient.java))

Blocks predicted to be broken by the explosion are highlighted in real time using vanilla block damage crack textures:

* **Progress Scaling**: Crack damage progresses from stage 3 to stage 9 as the projectile approaches its target:
  $$\text{stage} = \min(9, \max(0, \lfloor (0.3 + 0.7 \times \text{progress}) \times 10 \rfloor))$$
* **Blinking Interval**: Cracks blink periodically during flight:
  $$\text{period} = \max\left(3, \frac{\text{ticksToImpact}}{4}\right), \quad \text{visible} = (t \pmod{\text{period}}) < \frac{3 \times \text{period}}{4}$$
* **Engine Call**: `world.sendBlockBreakProgress(pos.hashCode(), pos, stage)`.
* **Cleanup**: Active highlights are cleared (stage $-1$) on projectile detonation, despawn, or world transition.

---

## 5. HUD Overlays ([HudRenderer.java](../src/main/java/com/simonconrad/fireballpredictor/hud/HudRenderer.java))

HUD elements hook into `RenderGameOverlayEvent.Post`.

### 5.1 Impact Warning Badge (`ElementType.HOTBAR`)

When an incoming projectile is threatening the player:
* **Background & Frame**: Rendered via `Gui.drawRect` with semi-transparent dark backdrop (`0xC8000000`) and border.
* **Item Icon**: Renders the representative projectile icon (`Items.fire_charge` for fireballs, `Items.skull` (wither) for skulls) using `RenderItem.renderItemAndEffectIntoGUI` with standard GUI item lighting.
* **Travel Progress Bar**: A progress bar beneath the icon tracks travel time elapsed vs total predicted flight time.
* **Damage & Knockback Readout**: Next to the badge, formatted damage and knockback velocity are rendered (e.g. `-4.5❤  12.0b/s`).

### 5.2 Cracking Hearts Health Overlay (`ElementType.HEALTH`)

When the player is in range of an explosion or direct hit:
* **Custom Textures**: Renders cracking overlay sprites over the vanilla hearts in the HUD:
  - `cracking_full.png` / `cracking_half.png` / `cracking_half_right.png`
  - `cracking_half_absorbing_right.png` (for golden absorption hearts)
  - `*_blinking.png` variants blinking at $6\text{-tick}$ intervals.
* **Damage Allocation**: Damage is subtracted from golden absorption hearts first; any overflow is subtracted from red health.
* **Layout Compatibility**: Follows the exact heart positioning math of 1.8.9's `GuiIngame.renderHealth` (10 hearts per row, standard spacing, multi-row wrapping).
