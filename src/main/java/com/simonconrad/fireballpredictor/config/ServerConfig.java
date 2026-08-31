package com.simonconrad.fireballpredictor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.simonconrad.fireballpredictor.FireballPredictor;
import com.simonconrad.fireballpredictor.tracking.TrackingRules;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * Server-side configuration (dedicated and integrated servers alike) that lets server
 * owners disable prediction tracking for the "other" projectile owner category -
 * either the whole group or individual sub-options (player, dispenser, command).
 * 1.8.9 port of master's ServerConfig.
 *
 * <p>Stored as {@code config/fireballpredictor-server.json} and deliberately
 * independent of the client Forge config ({@link ModConfig}): it must load on
 * dedicated servers without touching client-only classes. The effective restrictions
 * (plus the {@code mobGriefing} gamerule that gates block destruction) are pushed to
 * connected clients through the {@code fireballpredictor} custom channel on join,
 * after {@code /fireballpredictor reload} and after any {@code /gamerule} change.
 */
public final class ServerConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static File configFile;

    /**
     * Master switch: disables tracking for the whole "other" owner group
     * (player, dispenser, command), regardless of the sub-options below.
     */
    public boolean disableOtherOwnerTracking = false;

    /** Sub-option: disables tracking of player-fired projectiles. */
    public boolean disablePlayerTracking = false;

    /** Sub-option: disables tracking of dispenser-fired projectiles. */
    public boolean disableDispenserTracking = false;

    /** Sub-option: disables tracking of command-summoned or unmatched projectiles. */
    public boolean disableCommandTracking = false;

    private static final ServerConfig INSTANCE = new ServerConfig();

    private ServerConfig() {
    }

    public static ServerConfig instance() {
        return INSTANCE;
    }

    public static void init(File configDir) {
        configFile = new File(configDir, "fireballpredictor-server.json");
    }

    /**
     * Load the config from disk, writing a default file on first start so server
     * owners can discover the available options.
     */
    public static void load() {
        if (configFile == null) {
            return;
        }
        if (configFile.exists()) {
            try (Reader reader = new FileReader(configFile)) {
                ServerConfig loaded = GSON.fromJson(reader, ServerConfig.class);
                if (loaded != null) {
                    INSTANCE.disableOtherOwnerTracking = loaded.disableOtherOwnerTracking;
                    INSTANCE.disablePlayerTracking = loaded.disablePlayerTracking;
                    INSTANCE.disableDispenserTracking = loaded.disableDispenserTracking;
                    INSTANCE.disableCommandTracking = loaded.disableCommandTracking;
                }
            } catch (Exception e) {
                FireballPredictor.LOGGER.error(
                        "Failed to read server config {}; keeping current values", configFile, e);
            }
        } else {
            save();
        }
    }

    public static void save() {
        if (configFile == null) {
            return;
        }
        try {
            File parent = configFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (Writer writer = new FileWriter(configFile)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            FireballPredictor.LOGGER.error("Failed to write server config {}", configFile, e);
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
     * Effective {@link TrackingRules} bitmask. The master switch collapses to every
     * bit of the "other" group; otherwise the sub-options combine.
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
