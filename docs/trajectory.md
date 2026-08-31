# Fireball Trajectory Prediction (Forge 1.8.9)

This document describes the client-side trajectory prediction system for the Minecraft 1.8.9 Forge backport of **Fireball Predictor**.

All trajectory calculations are computed deterministically on the client side using the mathematical kinematics of Minecraft 1.8.9's `EntityFireball.onUpdate` physics loop.

---

## 1. Kinematics & Physics Engine ([TrajectoryPredictor.java](../src/main/java/com/simonconrad/fireballpredictor/math/TrajectoryPredictor.java))

Minecraft 1.8.9 simulates projectile movement using discrete per-tick integration with acceleration and drag. The prediction engine replicates this loop tick-by-tick up to a maximum limit (`ModConfig.maxTicks`, default: `200` ticks / 10 seconds).

### 1.1 Step-by-Tick Simulation Algorithm

In each simulated tick $t \in [1, \text{maxTicks}]$:

1. **Tentative Step**:
   $$\vec{x}_{\text{next}} = \vec{x}_t + \vec{v}_t$$
2. **Block Raycasting**:
   Executes `world.rayTraceBlocks(start, end)` using vanilla block collision raycasting.
   - If a solid block is struck, the raycast hit vector $\vec{x}_{\text{hit}}$ is recorded.
3. **Entity Collision Raycasting**:
   Vanilla clamps the entity search ray to the block collision point (if one occurred), preventing ghost entity collisions behind walls:
   $$\vec{x}_{\text{rayEnd}} = \begin{cases} \vec{x}_{\text{blockHit}} & \text{if block hit exists} \\ \vec{x}_{\text{next}} & \text{otherwise} \end{cases}$$
   Entity raycasting checks all entities in `world.getEntitiesWithinAABBExcludingEntity` within an expanded bounding box around the trajectory segment, testing against expanded bounding boxes ($+0.3$ blocks).
   - If an entity is intercepted, collision occurs at the closest entity hit vector.
4. **Collision Termination**:
   If a block or entity is hit, the simulation records the terminal `MovingObjectPosition` (`Prediction.impact`), appends the exact collision point to `Prediction.path`, and exits immediately.
5. **Position & Drag Update**:
   If no collision occurs, the projectile moves to $\vec{x}_{\text{next}}$, and velocity is updated for the next tick:
   $$\vec{v}_{t+1} = (\vec{v}_t + \vec{a}) \times d$$
   where $\vec{a} = (\text{accX}, \text{accY}, \text{accZ})$ is the fireball's intrinsic acceleration vector, and $d$ is the active drag coefficient.

### 1.2 Drag Coefficients

Minecraft 1.8.9 projectile drag differs by entity type and environment:

| Entity Type | Condition | Air Drag ($d_{\text{air}}$) | Water Drag ($d_{\text{water}}$) |
|---|---|---|---|
| **Large Fireball** (`EntityLargeFireball`) | Any | `0.95` | `0.80` |
| **Small Fireball** (`EntitySmallFireball`) | Any | `0.95` | `0.80` |
| **Wither Skull** (`EntityWitherSkull`) | Normal (Black) | `0.95` | `0.80` |
| **Wither Skull** (`EntityWitherSkull`) | Charged / Blue (`isInvulnerable()`) | `0.73` | `0.80` |

*Water Detection*: Checked via `isTouchingWater()`, scanning integer block bounds around the entity's bounding box ($[\lfloor x - w/2 \rfloor, \lfloor x + w/2 \rfloor] \times [\lfloor y \rfloor, \lfloor y + h \rfloor] \times [\lfloor z - w/2 \rfloor, \lfloor z + w/2 \rfloor]$) for `Material.water`.

---

## 2. Velocity Estimation in 1.8.9 Multiplayer

In Minecraft 1.8.9 multiplayer protocol:
* The server sends an initial spawn packet (`S0EPacketSpawnObject`) containing entity coordinates and initial motion vectors.
* **Crucial Difference from Modern Versions**: The 1.8.9 server **never sends subsequent velocity updates** for projectiles during flight (`S12PacketEntityVelocity` is only sent to players and select entities, not fireballs). The client only receives position sync packets (`S14PacketEntity.S15PacketEntityRelMove` / `S18PacketEntityTeleport`).

### 2.1 Velocity Delta Derivation

To maintain high accuracy without relying on stale `motionX/Y/Z` fields, [FireballPredictorClient.java](../src/main/java/com/simonconrad/fireballpredictor/client/FireballPredictorClient.java) derives real velocity from successive synced position deltas:

$$\vec{v}_{\text{derived}} = \begin{cases} \vec{x}_{\text{current}} - \vec{x}_{\text{lastSynced}} & \text{if hasDelta} \\ \vec{v}_{\text{motion}} & \text{initial tick} \end{cases}$$

This ensures that deflections (e.g., player hitting a fireball with a sword, punch, or arrow) are detected immediately on the next received position packet, re-aligning the trajectory ribbon within $\le 1$ tick.

### 2.2 Position Anchoring & NaN Robustness (command-summoned projectiles)

A fireball spawned **without a shooter** (e.g. `/summon Fireball`, command blocks) and **without a `power` NBT tag** makes the vanilla 1.8.9 client construct `EntityFireball(World, x, y, z, 0, 0, 0)` from the spawn packet. That constructor normalizes the zero acceleration vector — `0 / 0` — into **NaN**, and `onUpdate()` then spreads the NaN through `motionX/Y/Z` and `posX/Y/Z`, making the entity invisible and previously poisoning the whole prediction pipeline (NaN silently passes every `>` / `<=` range check).

The tracker therefore resolves a finite **anchor** each tick:

1. Prefer the entity position when finite.
2. Otherwise fall back to `serverPosX/Y/Z / 32.0` — the packet-maintained server position (set by `S0EPacketSpawnObject`, updated by `S0EPacketSpawnObject`/`S14`/`S18` movement packets), which always stays finite.
3. If neither is finite, the entry is left without a prediction for that tick.

Velocity deltas, deviation checks and the simulation start point all use the anchor; non-finite acceleration/motion components are treated as `0`, and `simulate` rejects non-finite inputs outright (returning an empty path instead of a NaN path). Stationary projectiles (no motion **and** no acceleration) short-circuit to an empty path — they only detonate on contact.

---

## 3. Dynamic Refresh & Invalidation ([FireballPredictorClient.java](../src/main/java/com/simonconrad/fireballpredictor/client/FireballPredictorClient.java))

Trajectory predictions are cached inside `Tracked.prediction`. To avoid redundant computations while reacting immediately to world mutations, `needsRefresh()` triggers a re-simulation when:

1. **Initial Computation**: `t.prediction == null`.
2. **State Mutation**: Explosion power or charged skull state (`isDangerous()`) changes.
3. **Trajectory Deviation**: Distance between the entity's actual position $\vec{x}_{\text{actual}}$ and expected position $\vec{x}_{\text{expected}} = \text{path}[\text{elapsed}]$ exceeds $0.25$ blocks (detects player deflections and velocity adjustments):
   $$(\Delta x)^2 + (\Delta y)^2 + (\Delta z)^2 > 0.25^2$$
4. **Impact Block Disappearance**: The block at the predicted impact point was broken or replaced by air before arrival.
5. **Path Obstruction Scan**: Every 5 ticks (`ticksExisted % 5 == 0`), upcoming trajectory segments are scanned against `world.getBlockState(pos)` to detect newly placed blocks or player builds intersecting the flight path.

---

## 4. Threat & Player Intercept Detection

To power the HUD warning badge and cracking hearts overlay, the predictor evaluates whether an incoming projectile threatens the client player:

1. **Direct Collision Intercept (`findEntityIntercept`)**:
   Cheaply sweeps the player's current bounding box (expanded by $0.3$ blocks) against each remaining linear path segment $(\text{path}[i], \text{path}[i+1])$ from elapsed time onwards.
2. **Blast Danger Radius Proximity**:
   Evaluates if the player's position is within the blast danger zone:
   $$R_{\text{danger}} = (\text{power} \le 0 \text{ ? } 1.0 : \text{power}) \times 2.0 \times 2.0 = 4 \times \text{power}$$
   $$\text{dist}^2(\vec{x}_{\text{player}}, \vec{x}_{\text{impact}}) \le R_{\text{danger}}^2$$
3. **Flight Path Proximity**:
   Checks if the player is within $R_{\text{danger}}$ of any point along the remaining trajectory ribbon, alerting the player even if the final detonation is behind them.
