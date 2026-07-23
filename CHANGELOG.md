# Changelog

## 1.3.0

### Wind Charge Support
- Added trajectory prediction and impact dome rendering for Wind Charges.
- Added HUD warning badges and custom color settings for Wind Charge indicators.

### Dynamic Explosion Power Learning
- Added automatic server fireball power estimation based on client-side explosion packet analysis.
- Automatically resets inferred power on world disconnect to prevent cross-server data bleed.

### Custom Server & Hypixel Compatibility
- Added explosion power estimation from damaged block distances when explosion packet radius is zero (e.g. Hypixel custom fireballs).
- Added per-server fallback power configuration settings (`serverFallbackPowers`).

### Config & Localization
- Added YACL configuration options for per-server fallbacks and Wind Charge customization.

## 1.2.0

### Fireball Syncing & Caching Fixes
- Fixed inconsistent client-side prediction sync by converting the fireball accessor to a standard interface.
- Added power syncing on NBT loading and setter writes to ensure client predictions recalculate immediately when a fireball's power or dangerous state changes.

## 1.1.0

**Added**

* Configuration options: Color pickers (trajectory path, shockwave dome), line width slider, and warning indicator toggle.
* Prediction support for charged wither skulls (caps blast resistance at `0.8F`).
* Fluid blast resistance calculation (e.g., waterlogged blocks) for accurate water-impact predictions.
* Automated tests for easier development.

**Fixed**

* Client-side memory leak where unloaded projectile power data remained in the cache.

**Performance**

* Offloaded explosion raycasting and dome rendering to background threads to eliminate client-side lag spikes during trajectory prediction.
