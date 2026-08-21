# Changelog

## 1.9.2

### Tracking & Bug Fixes
- **Breeze Mob Classification:** Correctly classified Breezes as hostile mob sources so Breeze wind charges respect mob tracking filters instead of falling back to command/other source filters.
- **Breeze Config Toggle:** Added a dedicated `Track Breezes` toggle under Mob Sources with schematic preview support and full localization across all 11 languages.

### Performance & Memory
- **Damage Raycast Early-Out:** Skips main-thread line-of-sight raycasts when players are safely out of blast range, eliminating frame drops during multi-projectile barrages.
- **Explosion & Memory Optimizations:** Precomputed explosion ray unit vectors, zero-allocation ray stepping, and lazy fluid snapshots significantly reduce CPU usage and GC micro-stutters.

## 1.9.1

### Visuals & Themes
- **Dynamic Shockwave Effects:** Dome visual effects (such as electric arcs) now radiate outward from the exact point where the incoming projectile penetrates the blast dome.
- **Consistent Trail Decoration Spacing:** Trajectory trail decorations (glyphs, wisps, blossoms, arcade sprites) are now anchored to world-space distance, maintaining uniform spacing and size on fast or long-distance paths.

### Collision & Tracking Accuracy
- **Line-of-Sight Collision Precedence:** Collision detection strictly prioritizes the closest obstacle, preventing occluded entities behind blocks from triggering hits and accurately displaying warning indicators on direct entity impacts.
- **Isolated Power Inference:** Dynamically inferred explosion powers are now tracked per source with a 90-second cache, preventing custom player blasts from altering vanilla mob or dispenser predictions.

### Performance & Memory
- **Asynchronous Prediction & Instant Ribbons:** Offloaded heavy explosion simulations and dome mesh construction to background worker threads while rendering trajectory ribbons instantly on spawn.
- **Sparse Memory & Safe Explosion Bounds:** Switched block destruction tracking to sparse memory and added safe limits for extreme custom explosion powers to prevent lag spikes.

## 1.9.0

### Per-Projectile Themes & Colors
- **Individual Themes & Colors:** Assign distinct visual themes or custom RGB trajectory and shockwave colors independently to Fireballs, Wind Charges, Wither Skulls, and Dragon Fireballs.
- **Themed Impact Particles:** Ground accent particles now adapt to each projectile's active visual theme.

### Theme Preview Gallery & UI
- **Multi-Target Selection:** Click any theme in `/fppreview` to apply it globally or to a specific projectile type via interactive chat buttons.
- **Config Menu Polish:** Reorganized "Themes & Colors" settings with dropdown selectors, smart option locking, and live per-projectile schematic previews.

## 1.8.1
- **Update Icon**: The icon was replaced with a new hand drawn pixel art. This also reduces the jar size.

## 1.8.0

### Visual Render Themes
- **16 Curated Visual Themes:** Added a suite of 16 special render themes that dynamically override the trajectory ribbon and shockwave dome with unique color palettes, patterns, and animations — Rainbow Chroma, Cyberpunk Neon, Matrix Digital, Inferno Firestorm, Thermal Heatmap, Celestial Nebula, Spectral Soul, Abyssal Sculk, High-Voltage Plasma, Aviation HUD, Aurora Borealis, Event Horizon, Sakura Drift, Prismatic Crystal, and 8-Bit Arcade (plus the Default custom-colors mode).
- **Theme Animation Speed:** Added a configurable animation speed multiplier (`0.0x` to `3.0x`) for animated themes; setting it to `0.0x` completely freezes theme animations into static gradients for motion-sensitive players.
- **Distance-Based Decoration LOD:** Particle, glyph, and sprite decorations scale down with camera distance and fade out entirely beyond ~60 blocks, keeping render budgets flat during heavy projectile barrages.

### Theme Preview Gallery
- **In-Game Preview Gallery:** Added `/fppreview` (or `/fireballpredictor preview`) to spawn all 16 visual themes simultaneously in a circular exhibition around the player with flight ribbons and animated shockwave blast domes.
- **Interactive Theme Selection:** Left-clicking any theme in the gallery targets its dome, ribbon, or nameplate and offers a clickable `[Confirm]` chat link to instantly apply and save it as the active theme.
- **One-Click Toggling:** Gallery status messages format the `/fppreview` command as a clickable chat link, with `on` / `off` / `clear` / `set <theme>` subcommands for explicit control.

### Configuration & Compatibility
- **Theme Dropdown & Gallery Button:** The settings menu now presents the visual theme as a dropdown selector with a "Toggle Preview Gallery" button positioned directly beneath it.
- **Smart Option Availability:** Trajectory and shockwave color options are automatically disabled while a non-default theme is active (since the theme overrides them) and re-enabled when returning to Default.
- **Localization:** Added the new theme, gallery, and animation-speed strings across all 11 supported languages.

### Bug Fixes
- **Wither Skull Prediction Fix:** The server now sends a `-1.0f` power sentinel for projectiles without a statically known power (such as wither skulls), and the client treats non-positive cached powers as "no value" — restoring shockwave domes, block-destruction highlights, and damage estimates for these projectiles.

## 1.7.2

### HUD & Knockback Readout
- **Zero-Damage Knockback Display:** The damage and knockback HUD readout now displays predicted knockback velocity for blast attacks even when damage is fully absorbed by armor, resistance, or distance (such as Wind Charges or heavy Blast Protection).
- **Themed Readout Text Colors:** The readout text dynamically matches the color of the incoming projectile's badge and progress bar (fiery orange for Fireballs, slate grey for Wither Skulls, breeze ice-blue for Wind Charges, and magenta-purple for Dragon Fireballs).
- **Cleaned-Up Formatting:** Automatically hides the `-0.0❤` label on zero-damage attacks (displaying cleanly as `⚡14.2b/s`) and omits knockback on zero-knockback hits.

### Performance & Polish
- **Config Menu Smoothness:** Optimized the live shockwave dome preview in the settings screen, eliminating lag and frame drops when viewing explosion visuals.
- **Client Memory Optimizations:** Eliminated transient per-tick memory allocations during block-break highlight tracking.
- **Dynamic Resource Pack Reloads:** Hooked config preview textures to Fabric's resource reload listener so custom resource pack changes reflect immediately on `F3+T`.

## 1.7.1

### HUD & Visual Improvements
- **Absorption Heart Textures:** Added dedicated half-heart cracking sprites for absorption hearts to accurately reflect partial absorption damage on the HUD.

### Performance & Internal Refactoring
- **State & Math Optimizations:** Consolidated client state resets, eliminated redundant damage and knockback math calculations, and added a power cache disconnect cleanup handler.
- **Rendering & Data Cleanup:** Deduplicated ribbon, heart, and preview drawing utilities, converted `PredictionData` to a record, and eliminated per-frame badge coordinate allocations.

## 1.7.0

### Damage & Knockback Estimation
- **Cracking Damage Hearts Overlay:** Overlays your health bar with animated cracking hearts highlighting the exact health predicted to be lost upon detonation. Fully accounts for explosion distance falloff, line-of-sight cover, armor defense, Protection & Blast Protection enchantments, absorption hearts, and Resistance effects.
- **Damage & Knockback Readout:** Added a HUD readout next to the impact warning badge displaying predicted heart loss and initial knockback velocity (in blocks per second) for the most threatening incoming projectile.

### HUD Impact Warning Upgrades
- **Projectile Category Theming:** The impact warning badge now features tailored icons and themed countdown progress bars for Fireballs (fire charge / fiery orange), Wither Skulls (wither skeleton skull / slate grey), Wind Charges (wind charge / ice blue), and Dragon Fireballs (custom dragon fireball sprite / magenta-purple).

### Configuration Options
- **Damage Estimation Settings:** Added options in the visual settings menu to independently toggle the cracking hearts health bar overlay and the damage/knockback numerical readout.

## 1.6.2

### Projectile Owner & Filter Priority
- **Wind Charge Filtering Priority:** Enforced evaluation hierarchy so owner source filters (e.g., Player, Dispenser, Mob) take priority over projectile type toggles for wind charges. Player-thrown or dispenser-fired wind charges now correctly respect player and dispenser tracking settings.

### Live Config Previews
- **Trajectory Toggle Schematic Preview:** Disabling trajectory ribbon rendering in the config menu now continues to animate the traveling projectile head and impact flash in the preview schematic, skipping only the path ribbon instead of displaying a static "disabled" text label.

## 1.6.1

### Trajectory Physics & Accuracy Fixes
- **Vanilla Physics Alignment:** Fixed a timing offset in trajectory calculations to align tick-exact with vanilla Minecraft physics, improving prediction accuracy and eliminating unnecessary visual re-renders.
- **Water Drag Simulation:** Trajectory predictions for fireballs and wither skulls now accurately account for liquid drag when traveling through water.
- **Unbreakable Block Protections:** Charged wither skulls no longer falsely predict the destruction of unbreakable blocks such as Bedrock and Reinforced Deepslate.

### Explosive Projectile Adjustments
- **Blaze & Dragon Fireballs:** Small fireballs (from Blazes) and Dragon fireballs no longer display misleading shockwave domes or block destruction highlights, while keeping path ribbons and warning indicators active.

## 1.6.0

### Smart Projectile Owner Tracking
- **Multi-Tier Owner Inference Engine:** Client-side 5-tier inference engine (`Native NBT` -> `Server Packet` -> `Environmental Sweep` -> `Dispenser Fallback` -> `Unknown/Command`) that deduces origin for all hostile and neutral projectiles.
- **Hierarchical Owner Filtering:** Tiered configuration featuring a global master toggle (`trackProjectiles`), mob-master toggle (`trackMobProjectiles`), individual mob switches (Blaze, Ghast, Ender Dragon, Wither), non-mob master toggle (`trackOtherOwnerProjectiles`), and per-source switches (Player, Dispenser, Command).
- **Player Deflection Re-attribution:** Projectiles punched or hit by players (such as Ghast fireballs) automatically re-attribute ownership to `PLAYER`, hiding prediction highlights unless player tracking is enabled.

### Server-Side Tracking Restrictions
- **Server Enforcement of Owner Filters:** Servers can now disable prediction tracking for the "other" owner category on their server via `config/fireballpredictor-server.json` — either the whole group (`disableOtherOwnerTracking`) or individual sub-options (`disablePlayerTracking`, `disableDispenserTracking`, `disableCommandTracking`).
- **Live Rule Sync:** Restrictions are pushed to clients with a new `TrackingRulesPayload` on join and are enforced client-side, overriding local config (including the deflection bypass). `/fireballpredictor reload` reloads the server config and re-syncs all connected players without a restart.

### Performance & Stability
- **Memory & Thread Safety:** Resolved client-side memory retention in owner caching and ensured background trajectory computations run completely thread-safe.


## 1.5.0

### Live Config Previews
- **Interactive GUI Previews:** Added real-time 2D schematic previews in the YACL configuration screen, allowing live visual previews of trajectory styling, shockwave domes, HUD layout, and tracking reticles.

### Visual Improvements
- **Smoother Dome Rendering:** Refined trajectory prediction math for smoother impact dome geometry.

### Localization & UX
- **Simplified Terminology:** Streamlined trajectory configuration titles and descriptions across all 11 supported languages for improved clarity and consistency.

## 1.4.0

### Trajectory Ribbon Styles & Config Overhaul
- **Customization Options:** Added customizable trajectory ribbon styles (`Solid`, `Dashed`, `Core Only`), a core glow toggle, dynamic motion pulse animations, and path-end tapering.
- **Enhanced Rendering:** Upgraded ribbon visuals with dual-pass rendering featuring a distinct inner core and soft outer glow.
- **Reorganized Settings:** Streamlined the YACL configuration menu into distinct sub-groups for easier navigation.

### Iris Shaderpack Compatibility
- **Shader Support:** Added full integration with Iris so prediction ribbons and impact domes render correctly with shaders enabled.
- **Visual Clarity:** Implemented double-sided rendering and balanced alpha transparency caps to maintain visibility of block breaking overlays.

### Bug Fixes & Optimizations
- **Pause State:** Paused ambient particle accent animations when the game client is paused.
- **Asset Optimization:** Reduced mod JAR size by optimizing icon resolution.

## 1.3.1

### Server Radius Sanity & Fallbacks
- **Fake Packet Protection:** Added block-destruction sanity checks to reject inflated explosion packet radii sent by servers like GommeHD, ensuring fireball power isn't calculated incorrectly.
- **Config Reset (Auto / None):** Setting a server's fallback power to `0.00` in the config UI now displays as `0.00 (Auto / None)` and clears the override so dynamic detection can take back over.
- **Priority Fix:** Server-specific power fallback presets now reliably take priority over packet radius inference.

### Infrastructure
- Added automated release publishing to Modrinth, CurseForge, and GitHub.

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
