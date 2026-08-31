package com.simonconrad.fireballpredictor.client.network;

import com.simonconrad.fireballpredictor.network.FireballSyncMessage;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import com.simonconrad.fireballpredictor.tracking.TrackingRules;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client-side cache of the data pushed by a server that also runs Fireball Predictor
 * (power / velocity / acceleration / owner per fireball, plus the server-wide tracking
 * rules and the mobGriefing gamerule). On vanilla servers nothing ever arrives here and
 * every accessor falls back to neutral defaults.
 *
 * <p>1.8.9 equivalent of master's ClientPowerCache + ClientOwnerCache + ServerTrackingRules,
 * consolidated into one class.
 */
public final class ClientFireballSync {

    /** Per-fireball data received from the server. */
    public static final class Entry {
        public final float power;
        public final ProjectileOwner owner;
        public final int ownerEntityId;
        public final double motionX, motionY, motionZ;
        public final double accelX, accelY, accelZ;
        /** {@code getTotalWorldTime()} when this entry (last) arrived. */
        public final long receivedAtTick;

        Entry(FireballSyncMessage message, long receivedAtTick) {
            this.power = message.power;
            this.owner = ProjectileOwner.fromOrdinalClamped(message.ownerOrdinal);
            this.ownerEntityId = message.ownerEntityId;
            this.motionX = message.motionX;
            this.motionY = message.motionY;
            this.motionZ = message.motionZ;
            this.accelX = message.accelX;
            this.accelY = message.accelY;
            this.accelZ = message.accelZ;
            this.receivedAtTick = receivedAtTick;
        }

        public boolean hasPower() {
            return power > 0.0F;
        }
    }

    /** How many ticks a received server velocity stays authoritative for. */
    public static final long VELOCITY_FRESH_TICKS = 2;

    /** Entries expire if the server did not refresh them for this many ticks. */
    public static final long ENTRY_TTL_TICKS = 20 * 30;

    private static final Map<Integer, Entry> ENTRIES = new HashMap<Integer, Entry>();

    private static int disabledOwnerMask = 0;
    private static boolean mobGriefing = true;
    private static boolean rulesKnown;

    private ClientFireballSync() {
    }

    // ------------------------------------------------------------ fireball data

    /** Applies a {@link FireballSyncMessage} on the client thread. */
    public static void applyFireballSync(FireballSyncMessage message) {
        long now = Minecraft.getMinecraft().theWorld != null
                ? Minecraft.getMinecraft().theWorld.getTotalWorldTime() : 0L;
        ENTRIES.put(message.entityId, new Entry(message, now));
        prune(now);
    }

    /** @return the synced data for the entity, or {@code null} on vanilla servers. */
    public static Entry get(int entityId) {
        return ENTRIES.get(entityId);
    }

    /**
     * @return the synced explosion power for the entity, or a negative value when the
     *         server sent none / is not running the mod.
     */
    public static float getPower(int entityId) {
        Entry entry = ENTRIES.get(entityId);
        return entry != null && entry.hasPower() ? entry.power : -1.0F;
    }

    public static ProjectileOwner getOwner(int entityId) {
        Entry entry = ENTRIES.get(entityId);
        return entry != null ? entry.owner : ProjectileOwner.UNKNOWN;
    }

    /**
     * Whether the server-provided motion is still fresh enough to be preferred over the
     * position-delta estimate (covers the spawn window where deltas are not yet available
     * and corrects command-summoned projectiles the spawn packet leaves at rest).
     */
    public static boolean hasFreshVelocity(int entityId, long worldTime) {
        Entry entry = ENTRIES.get(entityId);
        return entry != null && worldTime - entry.receivedAtTick <= VELOCITY_FRESH_TICKS;
    }

    public static void remove(int entityId) {
        ENTRIES.remove(entityId);
    }

    public static void clearFireballs() {
        ENTRIES.clear();
    }

    private static void prune(long now) {
        Iterator<Map.Entry<Integer, Entry>> it = ENTRIES.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().receivedAtTick > ENTRY_TTL_TICKS) {
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------- rules

    /** Applies a {@link com.simonconrad.fireballpredictor.network.ServerRulesMessage}. */
    public static void applyServerRules(int mask, boolean griefing) {
        disabledOwnerMask = TrackingRules.sanitize(mask);
        mobGriefing = griefing;
        rulesKnown = true;
    }

    /** Whether the server disables tracking for the given owner category. */
    public static boolean isOwnerDisabled(ProjectileOwner owner) {
        return TrackingRules.isDisabled(disabledOwnerMask, owner);
    }

    /**
     * Whether explosions break blocks on this server (the {@code mobGriefing} gamerule,
     * which 1.8.9 fireballs pass as the {@code smoking} flag). Defaults to true on
     * vanilla servers, matching the client's previous behaviour.
     */
    public static boolean explosionsBreakBlocks() {
        return mobGriefing;
    }

    public static int getDisabledOwnerMask() {
        return disabledOwnerMask;
    }

    /** Whether a modded server has sent its rules at least once. */
    public static boolean isRulesKnown() {
        return rulesKnown;
    }

    /** Clears everything on disconnect. */
    public static void clear() {
        ENTRIES.clear();
        disabledOwnerMask = 0;
        mobGriefing = true;
        rulesKnown = false;
    }
}
