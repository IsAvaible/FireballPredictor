package com.simonconrad.fireballpredictor.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.simonconrad.fireballpredictor.config.ModConfig;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class ModKeyBindings {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("fireballpredictor", "general")
    );

    public static final String KEY_TOGGLE_TRACKING = "key.fireballpredictor.toggle_tracking";
    public static final String MSG_MOD_ENABLED = "fireballpredictor.message.mod_enabled";
    public static final String MSG_MOD_DISABLED = "fireballpredictor.message.mod_disabled";

    public static final KeyMapping TOGGLE_TRACKING = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            KEY_TOGGLE_TRACKING,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
    ));

    private ModKeyBindings() {}

    public static void init() {
        // Triggers static class loading and Fabric registration
    }

    public static void handleInput(Minecraft client) {
        while (TOGGLE_TRACKING.consumeClick()) {
            ModConfig config = ModConfig.instance();
            config.trackProjectiles = !config.trackProjectiles;
            ModConfig.save();

            if (client.player != null) {
                Component message = config.trackProjectiles
                        ? Component.translatable(MSG_MOD_ENABLED)
                        : Component.translatable(MSG_MOD_DISABLED);
                client.player.sendOverlayMessage(message);
            }
        }
    }
}
