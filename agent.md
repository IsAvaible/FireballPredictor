# FireballPredictor — 1.21.11 Backport Agent Notes

This branch backports the 26.2 (Mojang-mappings) master line onto **Minecraft 1.21.11
with Yarn mappings**. Keep these adaptation notes in mind when working on this branch.

## Build environment

- **Java 21** (Gradle toolchain), Gradle wrapper 9.6.1, Fabric Loom 1.17-SNAPSHOT.
- Low-memory sandbox: run builds with `-Dorg.gradle.jvmargs=-Xmx1280m` and keep a
  swapfile active. `./gradlew build` must pass and `./gradlew runGameTest` must be
  green (baseline: 14/14) before a port commit is made.
- `genSources` produces Yarn-named sources; the merged named jar under
  `~/.cache/gradle/caches/fabric-loom/minecraftMaven/.../minecraft-merged-...-v2.jar`
  is the `javap` ground truth for the MC API surface.

## Mapping conventions (Mojang → Yarn)

- `AbstractHurtingProjectile` → `ExplosiveProjectileEntity`; `LargeFireball` → `FireballEntity`;
  `WitherSkull` → `WitherSkullEntity` (`isDangerous` → `isCharged`); `AbstractWindCharge` →
  `AbstractWindChargeEntity`; `tickCount` → `age`.
- `Level` → `World`; `ClientLevel` → `ClientWorld`; `Minecraft` → `MinecraftClient`;
  `getCurrentServer` → `getCurrentServerEntry` (`.ip` → `.address`).
- `Vec3` → `Vec3d`; `BlockPos.containing` → `ofFloored`; `AABB` → `Box`;
  `clip(ClipContext)` → `raycast(RaycastContext)`; `.level()` → `.getEntityWorld()`;
  `getDeltaMovement` → `getVelocity`; `getLocation` → `getPos`; `.lengthSqr` → `.lengthSquared`;
  `atCenterOf` → `ofCenter`.
- Client: `getDeltaTracker().getGameTimeDeltaPartialTick(true)` →
  `getRenderTickCounter().getTickProgress(true)`; `Camera.getPosition()` → `getCameraPos()`;
  `GuiGraphics` → `DrawContext`; `Font` → `TextRenderer` (`font` → `textRenderer`).
- Payloads: `CustomPacketPayload` → `CustomPayload` (`Type/type()` → `Id/getId()`);
  `StreamCodec.composite` → `PacketCodec.tuple` + `PacketCodecs`; `RegistryFriendlyByteBuf` →
  `RegistryByteBuf`; `PayloadTypeRegistry.clientboundPlay` → `playS2C`.
- Mixins on this branch: `ClientPlayNetworkHandlerMixin`, `FireballEntityMixin`.

## Rendering (1.21.11 specifics)

1.21.11 has the **RenderPipeline** API (`com.mojang.blaze3d.pipeline.RenderPipeline` +
`RenderSetup` builders, `RenderPipelines` static finals) but **no public RenderLayer factory
and no `RenderPipeline.register()`**. `RenderLayer.of(name, setup)` is package-private.

- `PredictionPipelines` builds the mod pipeline with `RenderPipeline.builder()` and exposes
  `PREDICTION` through the `RenderLayerAccessor` invoker mixin
  (`@Invoker("of") static RenderLayer fireballpredictor$create(String, RenderSetup)`).
- `PredictionFeatureRenderer` is a plain static emitter (no 26.2 feature-renderer API):
  `VertexConsumer` (`net.minecraft.client.render.VertexConsumer`), vertices end without `.next()`.
- Events: `WorldRenderEvents.END_MAIN` (vs `LevelRenderEvents`), `HudRenderCallback`
  (vs `HudElementRegistry`), `context.matrices()`/`context.consumers()`.
- GUI (1.21.11 `DrawContext`): `getMatrices()` returns `org.joml.Matrix3x2fStack`
  (`pushMatrix/popMatrix/translate(x,y)/scale(x,y)`); `drawText(..., boolean shadow)` requires
  the shadow flag; item icons render via `drawItem` under a 3x2 transform;
  `blitSprite` does not exist → `SpriteBlitter` helper resolves gui-atlas
  `SpriteIdentifier`s and calls `drawSpriteStretched`.
- Iris: reflection-only access (no compile dep) — internal
  `IrisPipelines.assignPipeline(pipeline, ShaderKey.LIGHTNING)`, fallback public
  `IrisApi.assignPipeline(pipeline, IrisProgram.BASIC)`.

## Port workflow

1. Translate with `/home/user/port/translate.py` (deterministic rule set, reviewed against
   javap ground truth), hand-adapt rendering/client/pipeline files.
2. `./gradlew build` + `./gradlew runGameTest` green, then one commit per version.
3. Upload `git format-patch` of the ported commits to 0x0.st and record the URL in
   `backport-patches.md` at the repo root.
