package com.simonconrad.fireballpredictor.server;

import com.simonconrad.fireballpredictor.FireballPredictor;
import com.simonconrad.fireballpredictor.config.ServerConfig;
import com.simonconrad.fireballpredictor.network.FireballSyncMessage;
import com.simonconrad.fireballpredictor.network.ServerRulesMessage;
import com.simonconrad.fireballpredictor.tracking.OwnerClassifier;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Server-side half of the mod (runs on dedicated and integrated servers):
 *
 * <ul>
 *   <li><b>Power syncing</b> - a large fireball's {@code explosionPower} (summon NBT
 *       {@code ExplosionPower}) is never transmitted by the vanilla protocol; clients
 *       always assume 1. Sent whenever a player starts tracking a fireball.</li>
 *   <li><b>Velocity + acceleration syncing</b> - 1.8.9 fireball tracker entries never send
 *       S12 velocity packets, and command-summoned fireballs (no shooter) do not even get
 *       the spawn packet's velocity applied. The true server motion and raw acceleration
 *       are synced on tracking start and refreshed whenever the projectile enters a new
 *       chunk.</li>
 *   <li><b>Owner syncing</b> - authoritative owner classification (mob / player /
 *       dispenser / command) for the client-side tracking filters.</li>
 *   <li><b>Gamerule + tracking-rules syncing</b> - the {@code mobGriefing} gamerule
 *       (gates block destruction of fireball explosions in 1.8.9) and the server config's
 *       disabled-owner mask are pushed on join, after {@code /fireballpredictor reload}
 *       and one tick after any {@code /gamerule} command.</li>
 * </ul>
 */
public final class FireballPredictorServer {

    /** Chunk-crossing velocity/power refreshes are only sent within this range. */
    private static final double RESYNC_RANGE = 96.0;

    /** Set by the CommandEvent hook; consumed on the next server tick end. */
    private boolean pendingGameruleResync;

    // -------------------------------------------------------------- fireballs

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.target instanceof EntityFireball)) {
            return;
        }
        if (event.entityPlayer instanceof EntityPlayerMP) {
            syncFireball((EntityPlayerMP) event.entityPlayer, (EntityFireball) event.target);
        }
    }

    /**
     * Fireballs move fast and 1.8.9 never re-sends their velocity; re-syncing whenever a
     * projectile crosses a chunk boundary keeps the clients' prediction anchored to the
     * true server motion without per-tick traffic.
     */
    @SubscribeEvent
    public void onEnteringChunk(EntityEvent.EnteringChunk event) {
        Entity entity = event.entity;
        if (!(entity instanceof EntityFireball)) {
            return;
        }
        EntityFireball fireball = (EntityFireball) entity;
        if (fireball.worldObj == null || fireball.worldObj.isRemote
                || !(fireball.worldObj instanceof WorldServer)) {
            return;
        }
        WorldServer world = (WorldServer) fireball.worldObj;
        double rangeSq = RESYNC_RANGE * RESYNC_RANGE;
        for (Object raw : world.playerEntities) {
            if (!(raw instanceof EntityPlayerMP)) {
                continue;
            }
            EntityPlayerMP player = (EntityPlayerMP) raw;
            double dx = player.posX - fireball.posX;
            double dy = player.posY - fireball.posY;
            double dz = player.posZ - fireball.posZ;
            if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                syncFireball(player, fireball);
            }
        }
    }

    private static void syncFireball(EntityPlayerMP player, EntityFireball fireball) {
        // Power: only statically known for large fireballs; a negative value tells the
        // client to fall back to its per-type defaults (skull = 1, small fireball = 0).
        float power = fireball instanceof EntityLargeFireball
                ? (float) ((EntityLargeFireball) fireball).explosionPower
                : -1.0F;

        ProjectileOwner owner = OwnerClassifier.resolveAuthoritative(fireball);
        Entity ownerEntity = fireball.shootingEntity;
        int ownerEntityId = ownerEntity != null ? ownerEntity.getEntityId() : -1;

        FireballPredictor.network.sendTo(new FireballSyncMessage(
                fireball.getEntityId(), power, owner.ordinal(), ownerEntityId,
                finiteOrZero(fireball.motionX), finiteOrZero(fireball.motionY), finiteOrZero(fireball.motionZ),
                finiteOrZero(fireball.accelerationX), finiteOrZero(fireball.accelerationY),
                finiteOrZero(fireball.accelerationZ)), player);
    }

    private static double finiteOrZero(double value) {
        return value == value && !Double.isInfinite(value) ? value : 0.0;
    }

    // ------------------------------------------------------------------ rules

    @SubscribeEvent
    public void onPlayerLoggedIn(
            net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            sendRules((EntityPlayerMP) event.player);
        }
    }

    /**
     * The vanilla gamerule change happens while the command executes - after this event
     * fires - so the re-sync is deferred to the end of the current server tick.
     */
    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        if ("gamerule".equals(event.command.getCommandName())) {
            pendingGameruleResync = true;
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && pendingGameruleResync) {
            pendingGameruleResync = false;
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null) {
                broadcastRules(server);
            }
        }
    }

    /** Pushes the tracking-rules mask and the mobGriefing gamerule to one player. */
    public static void sendRules(EntityPlayerMP player) {
        WorldServer world = player.getServerForPlayer();
        boolean griefing = world != null && world.getGameRules().getBoolean("mobGriefing");
        FireballPredictor.network.sendTo(
                new ServerRulesMessage(ServerConfig.instance().disabledOwnerMask(), griefing), player);
    }

    /** Pushes the tracking-rules mask and the mobGriefing gamerule to everyone online. */
    public static void broadcastRules(MinecraftServer server) {
        for (WorldServer world : server.worldServers) {
            for (Object raw : world.playerEntities) {
                if (raw instanceof EntityPlayerMP) {
                    sendRules((EntityPlayerMP) raw);
                }
            }
        }
    }
}
