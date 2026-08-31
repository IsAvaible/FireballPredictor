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

## 2. Trajectory Beam Geometry (3D Volumetric Cylinder)

The trajectory is rendered as a smooth, 3D volumetric cylindrical energy beam with soft outer falloff and an inner concentrated laser core.

### 2.1 3D Cylindrical Geometry & Rotation-Minimizing Frame (RMF)

To eliminate 2D flat-tape billboard distortion and maintain consistent volumetric thickness from all camera angles:
1. **Catmull-Rom Spline Sub-stepping**: Trajectory waypoints between discrete simulation ticks are sub-sampled smoothly with Catmull-Rom splines ($\le 0.20$ blocks/sub-step).
2. **Parallel Transport (RMF)**: Generates an orthonormal 3D reference frame $(\hat{t}_m, \hat{u}_m, \hat{w}_m)$ along the beam curve, ensuring zero axial twist.
3. **8-Sided Cylinder Extrusion**: Extrudes an 8-sided prism around the flight path using radial basis offsets:
   $$\vec{d}_{m, k} = \cos(k \pi / 4) \hat{u}_m + \sin(k \pi / 4) \hat{w}_m \quad (k = 0 \dots 7)$$
   Adjacent rings share exact radial vertices $(Q_m + \vec{d}_{m, k} R_m)$, forming a seamless, continuous 3D tube.

### 2.2 Two-Pass Volumetric Shading

1. **Outer Shroud Cylinder** - full radius ($R = \frac{1}{2} w$), translucent soft glow envelope.
2. **Inner Core Cylinder** (`renderCoreGlow`) - concentrated inner core beam at $35\%$ radius with bright alpha.

* **Width & Taper**: Radius tapers to a point over the final 20% of the flight path to the impact epicenter.
* **Alpha Attenuation**: Alpha decays quadratically from origin to impact point, with smooth tick blend-in and optional travelling pulse.

---

## 3. Shockwave Blast Dome Rendering

The blast dome is drawn using the pre-computed `DomeMesh` geometry (32 latitude $\times$ 48 longitude bands forming a hemisphere $y \ge 0$) centered at the predicted block impact coordinate $\vec{c}$. Domes are emitted **before** the trajectory ribbons (into the same translucent GL state), so ribbons blend on top of the blast spheres — the same ordering as master's shared prediction render type.

### 3.1 Fresnel Rim Calculation (Schlick approximation)

To create a holographic energy sphere appearance where the rim shines brighter than the center:

1. Dome-space vertex position: $\vec{v}$ on the upper hemispherical surface $x = R\sin\theta\cos\phi, y = R\cos\theta, z = R\sin\theta\sin\phi$ with $\theta \in [0, \pi/2]$.
2. Surface normal unit vector: $\hat{n} = \frac{\vec{v}}{\|\vec{v}\|}$.
3. View direction: $\hat{u} = \frac{\vec{c}_{\text{cam}} - \vec{v}}{\|\vec{c}_{\text{cam}} - \vec{v}\|}$ (camera position relative to the dome centre).
4. Schlick fresnel coefficient ($F_0 = 0.04$, exponent 5):
   $$F = F_0 + (1 - F_0) \times (1 - |\hat{n} \cdot \hat{u}|)^5$$
5. Final vertex alpha:
   - When outside: $\alpha_{\text{final}} = \mathrm{clamp}_{[0,\,110]}\Big(\alpha_{\text{base}} \times \big(1 - s + s\,F\big) + g \times s \times F\Big)$
   - When inside: an ambient visibility floor ($F_{\text{inside}} = 0.45 + 0.55 F$) ensures the dome ceiling and walls remain clearly visible without fading to zero.

The fixed rim glow term is what keeps the silhouette readable where the latitude profile fades to zero (poles), and — because back-face culling is disabled — makes the far/inner side of the shell glow when the camera is **inside** the blast sphere, which the previous plain-multiplier approach rendered almost invisible.

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
