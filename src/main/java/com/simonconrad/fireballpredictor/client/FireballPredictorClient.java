package com.simonconrad.fireballpredictor.client;

import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.hud.HudRenderer;
import com.simonconrad.fireballpredictor.math.DamageCalculator;
import com.simonconrad.fireballpredictor.math.DomeMesh;
import com.simonconrad.fireballpredictor.math.ImpactPredictor;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import com.simonconrad.fireballpredictor.render.PredictionRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks live fireballs (large/small fireballs, wither skulls),
 * keeps their predictions fresh and drives the world rendering, block highlight
 * cracks, HUD warning badge and damage estimation - the 1.8.9 equivalent of the
 * original mod's FireballPredictorClient + TrackedProjectile.
 */
public final class FireballPredictorClient {

    /** Per-projectile prediction state. Fields are read by the renderer. */
    public static final class Tracked {
        public final EntityFireball fireball;
        public TrajectoryPredictor.Prediction prediction;
        public int predictionAge = -1;
        public float power;
        public boolean dangerous;
        public List<BlockPos> brokenBlocks = new ArrayList<BlockPos>();
        public DomeMesh dome = DomeMesh.EMPTY;
        public Vec3 impactPos;                 // impact point used for warning/damage
        public boolean directHitPlayer;
        public final Set<BlockPos> lastHighlighted = new HashSet<BlockPos>();

        // Velocity estimation: the 1.8.9 client never receives velocity updates for
        // projectiles after spawn, so the current velocity is derived from the last two
        // synced positions. lastPos* holds the previous tick's position once hasDelta.
        private double lastPosX, lastPosY, lastPosZ;
        private boolean hasDelta;

        Tracked(EntityFireball fireball) {
            this.fireball = fireball;
        }
    }

    private final Map<Integer, Tracked> tracked = new HashMap<Integer, Tracked>();
    private WorldClient lastWorld;

    // HUD state (read by HudRenderer)
    public boolean impactWarningVisible;
    public float impactWarningProgress;
    public int impactWarningType = HudRenderer.TYPE_FIREBALL;
    public DamageCalculator.DamageEstimate currentEstimate = DamageCalculator.DamageEstimate.NONE;
    public int currentEstimateType = HudRenderer.TYPE_FIREBALL;
    public boolean damageOverlayActive;

    public Map<Integer, Tracked> getTracked() {
        return tracked;
    }

    // ---------------------------------------------------------------- events

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            if (lastWorld != null) {
                resetAll(mc);
            }
            lastWorld = null;
            return;
        }
        if (mc.theWorld != lastWorld) {
            resetAll(mc);
            lastWorld = mc.theWorld;
        }
        if (!ModConfig.masterEnabled) {
            resetAll(mc);
            return;
        }
        tick(mc);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || !ModConfig.masterEnabled || tracked.isEmpty()) {
            return;
        }
        PredictionRenderer.render(mc, tracked, event.partialTicks);
    }

    @SubscribeEvent
    public void onOverlayPost(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.type == RenderGameOverlayEvent.ElementType.HOTBAR) {
            HudRenderer.renderBadge(mc, this, event.resolution);
        } else if (event.type == RenderGameOverlayEvent.ElementType.HEALTH) {
            HudRenderer.renderHearts(mc, this, event.resolution);
        }
    }

    // ------------------------------------------------------------------- tick

    private void tick(Minecraft mc) {
        WorldClient world = mc.theWorld;
        EntityPlayer player = mc.thePlayer;

        // Discover new projectiles.
        if (tracked.size() < ModConfig.maxTrackedProjectiles) {
            @SuppressWarnings("unchecked")
            List<Entity> entities = world.loadedEntityList;
            for (int i = 0; i < entities.size() && tracked.size() < ModConfig.maxTrackedProjectiles; i++) {
                Entity entity = entities.get(i);
                if (entity instanceof EntityFireball && entity.isEntityAlive()
                        && !tracked.containsKey(entity.getEntityId())) {
                    tracked.put(entity.getEntityId(), new Tracked((EntityFireball) entity));
                }
            }
        }

        // Remove dead ones, refresh predictions.
        Iterator<Map.Entry<Integer, Tracked>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Tracked> entry = it.next();
            Tracked t = entry.getValue();
            EntityFireball fireball = t.fireball;

            if (fireball.isDead || !fireball.isEntityAlive() || fireball.worldObj != world) {
                clearHighlights(world, t);
                it.remove();
                continue;
            }

            double velX = t.hasDelta ? fireball.posX - t.lastPosX : fireball.motionX;
            double velY = t.hasDelta ? fireball.posY - t.lastPosY : fireball.motionY;
            double velZ = t.hasDelta ? fireball.posZ - t.lastPosZ : fireball.motionZ;

            if (needsRefresh(world, t)) {
                refreshPrediction(world, t, velX, velY, velZ);
            }

            t.lastPosX = fireball.posX;
            t.lastPosY = fireball.posY;
            t.lastPosZ = fireball.posZ;
            t.hasDelta = true;
        }

        // Threat + damage + highlight stage per tracked projectile.
        boolean warningDetected = false;
        int minTicksToImpact = Integer.MAX_VALUE;
        float mostRelevantProgress = 0.0F;
        int warningType = HudRenderer.TYPE_FIREBALL;

        DamageCalculator.DamageEstimate bestEstimate = DamageCalculator.DamageEstimate.NONE;
        int bestEstimateType = HudRenderer.TYPE_FIREBALL;
        boolean estimateFound = false;

        for (Tracked t : tracked.values()) {
            EntityFireball fireball = t.fireball;
            TrajectoryPredictor.Prediction prediction = t.prediction;
            if (prediction == null) {
                continue;
            }

            int elapsed = Math.max(0, fireball.ticksExisted - t.predictionAge);
            List<Vec3> path = prediction.path;
            int ticksToImpact = Math.max(0, path.size() - 1 - elapsed);

            // --- direct hit / impact point ---
            Vec3 intercept = path.size() > 1 ? TrajectoryPredictor.findEntityIntercept(prediction, elapsed, player) : null;
            t.directHitPlayer = intercept != null;
            if (intercept != null) {
                t.impactPos = intercept;
            } else if (prediction.impact != null) {
                t.impactPos = prediction.impact.hitVec;
            } else if (!path.isEmpty()) {
                t.impactPos = path.get(path.size() - 1);
            } else {
                t.impactPos = null;
            }

            // --- warning badge ---
            if (t.impactPos != null && path.size() > 1 && isThreateningPlayer(player, t, elapsed, ticksToImpact)) {
                warningDetected = true;
                float travelProgress = fireball.ticksExisted + ticksToImpact <= 0 ? 1.0F
                        : (float) fireball.ticksExisted / (float) (fireball.ticksExisted + ticksToImpact);
                if (ticksToImpact < minTicksToImpact) {
                    minTicksToImpact = ticksToImpact;
                    mostRelevantProgress = travelProgress;
                    warningType = typeFor(fireball);
                }
            }

            // --- damage estimation ---
            if ((ModConfig.renderDamageText || ModConfig.renderHeartsOverlay) && t.impactPos != null
                    && DamageCalculator.isFinite(t.impactPos)) {
                DamageCalculator.DamageEstimate estimate = estimateDamage(world, player, t);
                if (estimate.inRange) {
                    if (!estimateFound
                            || estimate.finalDamage > bestEstimate.finalDamage
                            || (estimate.finalDamage == bestEstimate.finalDamage
                            && estimate.knockbackBlocksPerSecond > bestEstimate.knockbackBlocksPerSecond)) {
                        bestEstimate = estimate;
                        bestEstimateType = typeFor(fireball);
                        estimateFound = true;
                    }
                }
            }

            // --- block destruction highlights ---
            if (ModConfig.renderBlockHighlights) {
                updateHighlights(world, t, fireball.ticksExisted, ticksToImpact);
            } else if (!t.lastHighlighted.isEmpty()) {
                clearHighlights(world, t);
            }
        }

        impactWarningVisible = warningDetected;
        if (warningDetected) {
            impactWarningProgress = mostRelevantProgress;
            impactWarningType = warningType;
        }

        damageOverlayActive = estimateFound;
        currentEstimate = estimateFound ? bestEstimate : DamageCalculator.DamageEstimate.NONE;
        currentEstimateType = estimateFound ? bestEstimateType : HudRenderer.TYPE_FIREBALL;
    }

    // -------------------------------------------------------------- refresh

    private boolean needsRefresh(WorldClient world, Tracked t) {
        EntityFireball fireball = t.fireball;
        TrajectoryPredictor.Prediction prediction = t.prediction;

        if (prediction == null) {
            return true;
        }
        // Power or charged-skull state changed.
        float power = TrajectoryPredictor.resolvePower(fireball);
        boolean dangerous = TrajectoryPredictor.isDangerous(fireball);
        if (power != t.power || dangerous != t.dangerous) {
            return true;
        }

        List<Vec3> path = prediction.path;
        int elapsed = fireball.ticksExisted - t.predictionAge;
        if (elapsed < 0 || elapsed >= path.size() - 1) {
            return true;
        }

        // The entity deviated from the predicted path (deflection, wrong velocity, lag).
        Vec3 expected = path.get(elapsed);
        double dx = fireball.posX - expected.xCoord;
        double dy = fireball.posY - expected.yCoord;
        double dz = fireball.posZ - expected.zCoord;
        if (dx * dx + dy * dy + dz * dz > 0.25 * 0.25) {
            return true;
        }

        // The predicted impact block disappeared (world changed).
        if (prediction.impact != null
                && prediction.impact.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
            if (world.getBlockState(prediction.impact.getBlockPos()).getBlock().isAir(world, prediction.impact.getBlockPos())) {
                return true;
            }
        }

        // Periodically rescan the upcoming path for newly placed blocks.
        if (fireball.ticksExisted % 5 == 0) {
            int start = Math.max(0, elapsed);
            for (int i = start; i < path.size() - 1; i++) {
                Vec3 pos = path.get(i);
                BlockPos bp = new BlockPos(pos.xCoord, pos.yCoord, pos.zCoord);
                if (world.getBlockState(bp).getBlock().getCollisionBoundingBox(world, bp, world.getBlockState(bp)) != null) {
                    return true;
                }
            }
        }

        return false;
    }

    private void refreshPrediction(WorldClient world, Tracked t, double velX, double velY, double velZ) {
        EntityFireball fireball = t.fireball;
        t.power = TrajectoryPredictor.resolvePower(fireball);
        t.dangerous = TrajectoryPredictor.isDangerous(fireball);

        TrajectoryPredictor.Prediction prediction = TrajectoryPredictor.simulate(
                fireball, world, ModConfig.maxTicks, velX, velY, velZ);
        t.prediction = prediction;
        t.predictionAge = fireball.ticksExisted;

        if (t.power > 0.0F && prediction.impact != null
                && DamageCalculator.isFinite(prediction.impact.hitVec)) {
            t.brokenBlocks = ImpactPredictor.predictBrokenBlocks(
                    world, prediction.impact.hitVec, t.power, t.dangerous, ModConfig.rayPowerMultiplier);
            t.dome = DomeMesh.build(t.power);
        } else {
            t.brokenBlocks = new ArrayList<BlockPos>();
            t.dome = DomeMesh.EMPTY;
        }
    }

    // -------------------------------------------------------------- threat

    private boolean isThreateningPlayer(EntityPlayer player, Tracked t, int elapsed, int ticksToImpact) {
        Vec3 playerPos = new Vec3(player.posX, player.posY, player.posZ);
        double dangerRadius = (t.power <= 0.0F ? 1.0F : t.power) * 2.0F * 2.0F;
        double dangerSq = dangerRadius * dangerRadius;

        if (t.directHitPlayer) {
            return true;
        }
        if (t.impactPos != null && playerPos.squareDistanceTo(t.impactPos) <= dangerSq) {
            return true;
        }
        // Proximity along the remaining flight path.
        List<Vec3> path = t.prediction.path;
        for (int i = Math.max(0, elapsed); i < path.size(); i++) {
            if (playerPos.squareDistanceTo(path.get(i)) <= dangerSq) {
                return true;
            }
        }
        return false;
    }

    private DamageCalculator.DamageEstimate estimateDamage(WorldClient world, EntityPlayer player, Tracked t) {
        EntityFireball fireball = t.fireball;
        if (t.directHitPlayer) {
            DamageCalculator.SourceType directType = directSourceType(fireball);
            return DamageCalculator.estimateDirectHit(world, player, t.impactPos, t.power,
                    directDamage(fireball), directType);
        }

        if (t.power <= 0.0F) {
            return DamageCalculator.DamageEstimate.NONE;
        }
        double distSq = player.getDistanceSq(t.impactPos.xCoord, t.impactPos.yCoord, t.impactPos.zCoord);
        float radius = t.power * DamageCalculator.BLAST_RADIUS_MULTIPLIER;
        if (distSq > (double) radius * radius) {
            return DamageCalculator.DamageEstimate.NONE;
        }
        return DamageCalculator.estimateExplosion(world, player, t.impactPos, t.power);
    }

    private static float directDamage(EntityFireball fireball) {
        if (fireball instanceof EntityWitherSkull) {
            return fireball.shootingEntity != null ? 8.0F : 5.0F;
        }
        if (fireball instanceof EntitySmallFireball) {
            return 5.0F;
        }
        return 6.0F; // large (ghast) fireball
    }

    private static DamageCalculator.SourceType directSourceType(EntityFireball fireball) {
        if (fireball instanceof EntityWitherSkull) {
            return fireball.shootingEntity != null
                    ? DamageCalculator.SourceType.DIRECT_WITHER_MOB
                    : DamageCalculator.SourceType.DIRECT_WITHER_MAGIC;
        }
        return DamageCalculator.SourceType.DIRECT_FIREBALL;
    }

    private static int typeFor(EntityFireball fireball) {
        if (fireball instanceof EntityWitherSkull) {
            return HudRenderer.TYPE_WITHER_SKULL;
        }
        return HudRenderer.TYPE_FIREBALL;
    }

    // ----------------------------------------------------------- highlights

    private void updateHighlights(WorldClient world, Tracked t, int age, int ticksToImpact) {
        Set<BlockPos> current = new HashSet<BlockPos>();

        if (!t.brokenBlocks.isEmpty()) {
            int totalTicks = age + ticksToImpact;
            double progress = totalTicks <= 0 ? 1.0 : (double) age / (double) totalTicks;
            double mapped = 0.3 + progress * 0.7;
            int baseStage = Math.min(9, Math.max(0, (int) (mapped * 10)));

            int period = Math.max(3, ticksToImpact / 4);
            boolean visible = (age % period) < ((period * 3) / 4);
            int stage = visible ? baseStage : -1;

            for (BlockPos pos : t.brokenBlocks) {
                if (world.getBlockState(pos).getBlock().isAir(world, pos)) {
                    continue;
                }
                world.sendBlockBreakProgress(pos.hashCode(), pos, stage);
                if (stage >= 0) {
                    current.add(pos);
                }
            }
        }

        // Clear highlights that are no longer active.
        for (BlockPos pos : t.lastHighlighted) {
            if (!current.contains(pos)) {
                world.sendBlockBreakProgress(pos.hashCode(), pos, -1);
            }
        }
        t.lastHighlighted.clear();
        t.lastHighlighted.addAll(current);
    }

    private void clearHighlights(WorldClient world, Tracked t) {
        for (BlockPos pos : t.lastHighlighted) {
            world.sendBlockBreakProgress(pos.hashCode(), pos, -1);
        }
        t.lastHighlighted.clear();
    }

    private void resetAll(Minecraft mc) {
        if (mc.theWorld instanceof WorldClient) {
            for (Tracked t : tracked.values()) {
                clearHighlights((WorldClient) mc.theWorld, t);
            }
        }
        tracked.clear();
        impactWarningVisible = false;
        damageOverlayActive = false;
        currentEstimate = DamageCalculator.DamageEstimate.NONE;
    }
}
