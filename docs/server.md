# Server-Side Component & Sync Protocol (Forge 1.8.9)

The mod works fully client-side on vanilla servers, but installing it on a server
(dedicated or integrated) unlocks authoritative syncing that fixes several 1.8.9
protocol gaps. All traffic uses the FML simple channel `fireballpredictor`.

---

## 1. Why syncing is needed in 1.8.9

| Data | Vanilla behaviour | Consequence without sync |
|---|---|---|
| Explosion power | `EntityLargeFireball.explosionPower` (summon NBT `ExplosionPower`) is a plain int; never sent to clients | Dome radius, block destruction and damage always predicted with power 1 |
| Projectile velocity | Fireball tracker entries are registered with `sendVelocityUpdates = false`; the spawn packet carries the *acceleration* as "speed", and the velocity is only applied client-side when a shooter id is attached | Command-summoned fireballs start at rest client-side; motion must be guessed from position deltas |
| Acceleration | The client constructor normalizes the spawn-packet vector to a length of 0.1 | `/summon Fireball ~ ~ ~ {power:[2,0,0]}` accelerates 20x faster on the server than the client predicts |
| Owner | `shootingEntity` is never synced for fireballs in multiplayer | Player/dispenser/command projectiles can only be guessed |
| `mobGriefing` | Gamerules are server-only state | Clients would predict block destruction on servers where explosions don't break blocks |

---

## 2. Messages (server → client)

### 2.1 `FireballSyncMessage` (discriminator 0)

Sent per fireball when a player starts tracking it (`PlayerEvent.StartTracking`) and
re-sent whenever the projectile enters a new chunk (`EntityEvent.EnteringChunk`,
within 96 blocks of a player). Fields:

* `entityId` – fireball entity id on the client
* `power` – large fireball explosion power; negative when not statically known
  (wither skulls = 1 and small fireballs = 0 are client-side defaults anyway)
* `ownerOrdinal` / `ownerEntityId` – `ProjectileOwner` classification
  (native owner → facing-dispenser adjacency → `COMMAND`) and owner entity id
* `motionX/Y/Z` – true server velocity; the client prefers it while fresh (≤ 2 ticks),
  then falls back to position-delta estimation (which also catches mid-flight deflections)
* `accelX/Y/Z` – raw server acceleration (constant per fireball); overrides the
  client's normalized value permanently

The client caches entries in `ClientFireballSync` (30 s TTL, cleared on world switch).

### 2.2 `ServerRulesMessage` (discriminator 1)

* `disabledOwnerMask` – bitmask of the "other" owner category the server disables
  (`TrackingRules.PLAYER = 1`, `DISPENSER = 2`, `COMMAND = 4`; master switch collapses
  to all bits). Clients enforce the mask on top of their own tracking config.
* `mobGriefing` – the `mobGriefing` gamerule of the player's world. When false,
  1.8.9 fireball explosions never destroy blocks (`EntityLargeFireball.onImpact`
  passes it as the `smoking` flag of `World.newExplosion`), so clients suppress the
  block-destruction prediction and crack overlays (dome + damage still render).

Sent on join (`PlayerLoggedInEvent`), after `/fireballpredictor reload`, and one tick
after any `/gamerule` command (`CommandEvent` + `ServerTickEvent` deferral, because the
gamerule mutates while the command executes).

---

## 3. Server configuration

`config/fireballpredictor-server.json` (JSON, independent of the client `.cfg` so it
loads on dedicated servers):

```json
{
  "disableOtherOwnerTracking": false,
  "disablePlayerTracking": false,
  "disableDispenserTracking": false,
  "disableCommandTracking": false
}
```

* `disableOtherOwnerTracking` – master switch for the whole "other" group
* The sub-options disable individual owner categories

`/fireballpredictor reload` (permission level 2) reloads the file and re-broadcasts the
rules + gamerules to every online player.

---

## 4. Client fallbacks (vanilla servers)

Without server support nothing changes compared to the previous backport behaviour:

* power: per-type defaults (`EntityLargeFireball` field = 1, wither skull = 1, small fireball = 0)
* velocity: position-delta estimation with NaN-safe anchoring (see `docs/trajectory.md`)
* acceleration: sanitized client field
* owner: environmental inference at discovery (nearby capable shooter whose look vector
  aligns with the projectile, then facing-dispenser adjacency, else `COMMAND`)
* `mobGriefing`: assumed true
