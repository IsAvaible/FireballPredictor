package com.simonconrad.fireballpredictor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.simonconrad.fireballpredictor.FireballPredictor;
import com.simonconrad.fireballpredictor.tracking.TrackingRules;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-side configuration (dedicated and integrated servers alike) that
 * lets server owners disable prediction tracking for the "other" projectile
 * owner category — either the whole group or individual sub-options
 * (player, dispenser, command).
 *
 * <p>Stored as {@code config/fireballpredictor-server.json} and deliberately
 * independent of the YACL client config ({@link ModConfig}): it must load on
 * dedicated servers without touching client-only classes. The effective
 * restrictions are pushed to connected clients via
 * {@code TrackingRulesPayload} on join and after
 * {@code /fireballpredictor reload}; clients enforce them inside
 * {@code TrackedProjectile.evaluateFilter}.
 */
public final class ServerConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("fireballpredictor-server.json");

    private static final ServerConfig INSTANCE = new ServerConfig();

    /**
     * Master switch: disables tracking for the whole "other" owner group
     * (player, dispenser, command), regardless of the sub-options below.
     */
    public boolean disableOtherOwnerTracking = false;

    /** Sub-option: disables tracking of player-fired and player-deflected projectiles. */
    public boolean disablePlayerTracking = false;

    /** Sub-option: disables tracking of dispenser-fired projectiles. */
    public boolean disableDispenserTracking = false;

    /** Sub-option: disables tracking of command-summoned or unmatched projectiles. */
    public boolean disableCommandTracking = false;

    private ServerConfig() {
    }

    public static ServerConfig instance() {
        return INSTANCE;
    }

    /**
     * Load the config from disk, writing a default file on first start so
     * server owners can discover the available options.
     */
    public static void load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                ServerConfig loaded = GSON.fromJson(reader, ServerConfig.class);
                if (loaded != null) {
                    INSTANCE.disableOtherOwnerTracking = loaded.disableOtherOwnerTracking;
                    INSTANCE.disablePlayerTracking = loaded.disablePlayerTracking;
                    INSTANCE.disableDispenserTracking = loaded.disableDispenserTracking;
                    INSTANCE.disableCommandTracking = loaded.disableCommandTracking;
                }
            } catch (Exception e) {
                FireballPredictor.LOGGER.error("Failed to read server config {}; keeping current values", PATH, e);
            }
        } else {
            save();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            FireballPredictor.LOGGER.error("Failed to write server config {}", PATH, e);
        }
    }

    /**
     * Reload the config from disk.
     *
     * @return the refreshed disabled-owner mask to broadcast to clients
     */
    public static int reload() {
        load();
        return instance().disabledOwnerMask();
    }

    /**
     * Effective {@link TrackingRules} bitmask. The master switch collapses
     * to every bit of the "other" group; otherwise the sub-options combine.
     */
    public int disabledOwnerMask() {
        if (disableOtherOwnerTracking) {
            return TrackingRules.OTHER_GROUP;
        }
        int mask = 0;
        if (disablePlayerTracking) {
            mask |= TrackingRules.PLAYER;
        }
        if (disableDispenserTracking) {
            mask |= TrackingRules.DISPENSER;
        }
        if (disableCommandTracking) {
            mask |= TrackingRules.COMMAND;
        }
        return mask;
    }
}
