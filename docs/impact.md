# Explosion Impact & Damage Estimation (Forge 1.8.9)

This document details the explosion simulation, block destruction raycasting, and combat damage calculation systems in the Minecraft 1.8.9 Forge backport of **Fireball Predictor**.

---

## 1. Block Destruction Simulation ([ImpactPredictor.java](../src/main/java/com/simonconrad/fireballpredictor/math/ImpactPredictor.java))

The mod replicates the block destruction algorithm of Minecraft 1.8.9's `Explosion.doExplosionA` client-side.

### 1.1 1,352-Ray Sphere Projection

Vanilla generates rays radiating from the explosion center across the surface of a $16 \times 16 \times 16$ cube:
* Total rays: $16^3 - 14^3 = 1352$ directional unit vectors $(\text{RAY\_DX}, \text{RAY\_DY}, \text{RAY\_DZ})$.
* Ray direction vector $\vec{d}$:
  $$d_x = \frac{j}{15} \times 2 - 1, \quad d_y = \frac{k}{15} \times 2 - 1, \quad d_z = \frac{l}{15} \times 2 - 1$$
  $$\hat{d} = \frac{\vec{d}}{\|\vec{d}\|}$$
  for all perimeter coordinates where $j, k, l \in \{0, 15\}$.

### 1.2 Ray Marching & Resistance Attenuation

For each of the 1,352 rays:
1. **Initial Ray Power**:
   $$\text{rayPower} = \text{power} \times \text{rayPowerMultiplier}$$
   *Note*: In vanilla Minecraft, rays have a random multiplier between $0.7$ and $1.3$. Fireball Predictor defaults to `rayPowerMultiplier = 1.3F` (the theoretical maximum worst-case) to ensure that all potentially destroyed blocks are highlighted.
2. **Ray Step**:
   The ray marches in increments of $\Delta s = 0.3$ blocks along $\hat{d}$.
3. **Power Decay**:
   $$\text{rayPower} \leftarrow \text{rayPower} - 0.225$$
4. **Block Blast Resistance**:
   If a non-air block is encountered at the stepped position:
   $$\text{resistance} = \text{block.getExplosionResistance}(world, pos)$$
   - **Charged Wither Skull Rule**: For charged (blue/invulnerable) wither skulls (`dangerous == true`), destructible blocks have their blast resistance clamped:
     $$\text{resistance} = \min(0.8F, \text{resistance})$$
     Indestructible blocks (`Blocks.bedrock`, `Blocks.end_portal`, `Blocks.end_portal_frame`, `Blocks.command_block`, `Blocks.barrier`) are immune to this cap via `canWitherSkullBreak()`.
   - **Power Attenuation**:
     $$\text{rayPower} \leftarrow \text{rayPower} - (\text{resistance} + 0.3) \times 0.3$$
5. **Collection**:
   If $\text{rayPower} > 0$ after encountering a non-air block, the `BlockPos` is added to the destruction set.

---

## 2. Shockwave Dome Geometry ([DomeMesh.java](../src/main/java/com/simonconrad/fireballpredictor/math/DomeMesh.java))

At the predicted impact point, a 3D spherical blast dome is rendered to visualize the full blast envelope:

* **Blast Radius**: $R_{\text{blast}} = \text{power} \times 2.0$ blocks.
* **Tessellation**: 20 latitude bands $\times$ 24 longitude bands ($480$ quads / $1,920$ vertices).
* **Equatorial Alpha Profile**:
  $$\alpha(h) = 82.0 \times 0.70 \times \sin(\pi h)$$
  where $h \in [0, 1]$ represents normalized latitude height. This creates soft, fading poles and a bright equatorial band.

---

## 3. Damage & Knockback Pipeline ([DamageCalculator.java](../src/main/java/com/simonconrad/fireballpredictor/math/DamageCalculator.java))

Minecraft 1.8.9's damage calculations differ significantly from 1.9+ (no armor toughness, no sweeping edge, different EPF formulas, sword blocking active). [DamageCalculator.java](../src/main/java/com/simonconrad/fireballpredictor/math/DamageCalculator.java) provides an exact mathematical replica of 1.8.9 `EntityLivingBase.damageEntity` and `EntityPlayer.attackEntityFrom`.

### 3.1 Explosion Raw Damage

1. **Line-of-Sight Exposure**:
   Evaluates `world.getBlockDensity(explosionPos, playerBoundingBox)` by raycasting a 3D grid from the explosion origin to points across the player's bounding box ($0.0 \le \text{density} \le 1.0$).
2. **Impact Factor**:
   $$d_{10} = \left(1.0 - \frac{\text{distance}}{R_{\text{blast}}}\right) \times \text{density}$$
3. **Raw Blast Damage**:
   $$\text{damage}_{\text{raw}} = \left\lfloor \frac{d_{10}^2 + d_{10}}{2.0} \times 8.0 \times R_{\text{blast}} + 1.0 \right\rfloor$$

### 3.2 Direct Impact Damage

When a projectile directly collides with the player:
* **Large Fireball**: $6.0$ base damage (direct hit) + explosion blast damage.
* **Small Fireball**: $5.0$ base damage (fire damage, extinguished by Fire Resistance).
* **Wither Skull**: $8.0$ (with shooter / mob damage) or $5.0$ (without shooter / magic damage) + explosion blast damage.
* **Worst-Case Evaluation**: Replicates the original mod by evaluating both the direct hit damage and the ensuing detonation blast damage, reporting the higher threat.

### 3.3 The 1.8.9 Mitigation Pipeline (`applyPlayerPipeline`)

Damage is processed sequentially through the exact 1.8.9 mitigation stages:

```
[Raw Damage]
     │
     ▼
[Creative / Spectator Check]  ── (Immune -> 0)
     │
     ▼
[Fire Resistance Check]        ── (If Fire Damage & Active -> 0)
     │
     ▼
[Difficulty Scaling]
     ├─ Peaceful:  damage = 0
     ├─ Easy:      damage = damage / 2 + 1
     ├─ Normal:    damage = damage
     └─ Hard:      damage = damage * 1.5
     │
     ▼
[Sword Blocking]               ── (If Blocking & !BypassArmor: damage = (1 + damage) / 2)
     │
     ▼
[Armor Reduction]              ── (If !BypassArmor: damage = damage * (25 - armorPoints) / 25)
     │
     ▼
[Resistance Potion Effect]     ── (amplifier = level - 1: damage = damage * (25 - (amplifier + 1) * 5) / 25)
     │
     ▼
[Enchantment Protection (EPF)] ── (Worst-case EPF roll, capped at 20: damage = damage * (25 - EPF) / 25)
     │
     ▼
[Absorption Allocation]        ── (Absorption hearts absorbed first, remainder dealt to red health)
     │
     ▼
[Final Hearts Lost Readout]
```

### 3.4 1.8.9 Enchantment Protection Factor (EPF)

In 1.8.9, each armor piece computes its protection modifier according to:
$$\text{pieceEPF} = \left\lfloor \frac{6 + \text{level}^2}{3} \times \text{typeMultiplier} \right\rfloor$$

| Enchantment | Type Multiplier | Effective against Explosion | Effective against Direct Hit |
|---|---|---|---|
| **Protection** | `0.75` | Yes | Yes |
| **Blast Protection** | `1.50` | Yes | No |
| **Fire Protection** | `1.25` | No | Yes (Fireball) |
| **Projectile Protection** | `1.50` | No | Yes (Direct Hit) |

Total EPF is summed across all 4 armor slots and capped at $20$:
$$\text{EPF} = \min\left(20, \sum_{\text{armor}} \text{pieceEPF}\right)$$

### 3.5 Explosion Knockback in 1.8.9

In Minecraft 1.8.9, explosion knockback velocity (blocks per tick) equals the exposure density $d_{10}$, reduced by the highest Blast Protection level on the player's armor:

$$\text{knockback}_{\text{tick}} = \max\left(0.0, d_{10} - \left\lfloor d_{10} \times \text{maxBlastLevel} \times 0.15 \right\rfloor\right)$$
$$\text{knockback}_{\text{bps}} = \text{knockback}_{\text{tick}} \times 20.0 \text{ blocks/sec}$$

*Note*: In 1.8.9, the Resistance potion effect does **not** reduce explosion knockback (that mechanic was introduced in 1.9).
