package com.simonconrad.fireballpredictor.client.render;

import java.io.InputStream;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages dynamically synthesized 9x9 pixel-art health bar cracking overlays that adapt
 * to the exact silhouette of whatever heart or health icon is loaded by the active resource pack.
 *
 * <p>Preserves authentic 9x9 Minecraft pixel art, the exact fissure and ember color palette,
 * and 1-to-1 pixel sizing. Supports both standard warm cracking and status-aware frozen heart cracking
 * (Thermal Scorch).
 */
public final class DynamicHeartOverlayManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("FireballPredictor/HeartOverlay");

    // Standard warm overlay dynamic textures (Normal, Poison, Wither, Absorption)
    public static final Identifier DYNAMIC_FULL =
            Identifier.fromNamespaceAndPath("fireballpredictor", "dynamic_hud_heart_cracking_full");
    public static final Identifier DYNAMIC_FULL_BLINKING =
            Identifier.fromNamespaceAndPath("fireballpredictor", "dynamic_hud_heart_cracking_full_blinking");
    public static final Identifier DYNAMIC_HALF =
            Identifier.fromNamespaceAndPath("fireballpredictor", "dynamic_hud_heart_cracking_half");
    public static final Identifier DYNAMIC_HALF_BLINKING =
            Identifier.fromNamespaceAndPath("fireballpredictor", "dynamic_hud_heart_cracking_half_blinking");
    public static final Identifier DYNAMIC_HALF_RIGHT =
            Identifier.fromNamespaceAndPath("fireballpredictor", "dynamic_hud_heart_cracking_half_right");
    public static final Identifier DYNAMIC_HALF_RIGHT_BLINKING =
            Identifier.fromNamespaceAndPath("fireballpredictor", "dynamic_hud_heart_cracking_half_right_blinking");

    // Frozen Thermal Scorch dynamic textures
    public static final Identifier DYNAMIC_FROZEN_SCORCH_FULL =
            Identifier.fromNamespaceAndPath("fireballpredictor", "dynamic_hud_heart_cracking_frozen_scorch_full");
    public static final Identifier DYNAMIC_FROZEN_SCORCH_FULL_BLINKING =
            Identifier.fromNamespaceAndPath("fireballpredictor", "dynamic_hud_heart_cracking_frozen_scorch_full_blinking");
    public static final Identifier DYNAMIC_FROZEN_SCORCH_HALF =
            Identifier.fromNamespaceAndPath("fireballpredictor", "dynamic_hud_heart_cracking_frozen_scorch_half");
    public static final Identifier DYNAMIC_FROZEN_SCORCH_HALF_BLINKING =
            Identifier.fromNamespaceAndPath("fireballpredictor", "dynamic_hud_heart_cracking_frozen_scorch_half_blinking");
    public static final Identifier DYNAMIC_FROZEN_SCORCH_HALF_RIGHT =
            Identifier.fromNamespaceAndPath("fireballpredictor", "dynamic_hud_heart_cracking_frozen_scorch_half_right");
    public static final Identifier DYNAMIC_FROZEN_SCORCH_HALF_RIGHT_BLINKING =
            Identifier.fromNamespaceAndPath("fireballpredictor", "dynamic_hud_heart_cracking_frozen_scorch_half_right_blinking");

    private static final Identifier MASTER_FULL_ID =
            Identifier.fromNamespaceAndPath("fireballpredictor", "textures/gui/sprites/hud/heart/cracking_master_full.png");
    private static final Identifier MASTER_BLINK_ID =
            Identifier.fromNamespaceAndPath("fireballpredictor", "textures/gui/sprites/hud/heart/cracking_master_full_blinking.png");

    private static final Identifier MASTER_SCORCH_FULL_ID =
            Identifier.fromNamespaceAndPath("fireballpredictor", "textures/gui/sprites/hud/heart/cracking_master_frozen_scorch_full.png");
    private static final Identifier MASTER_SCORCH_BLINK_ID =
            Identifier.fromNamespaceAndPath("fireballpredictor", "textures/gui/sprites/hud/heart/cracking_master_frozen_scorch_full_blinking.png");

    private static final Identifier VANILLA_HEART_ID =
            Identifier.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/heart/full.png");
    public static final Identifier VANILLA_CONTAINER_ID =
            Identifier.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/heart/container.png");

    private static final DynamicTexture[] DYNAMIC_TEXTURES = new DynamicTexture[12];
    private static volatile boolean initialized = false;

    private DynamicHeartOverlayManager() {
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Resolves the active overlay texture identifier for standard hearts.
     */
    public static Identifier getOverlayTexture(boolean leftLost, boolean rightLost, boolean blinking) {
        return getOverlayTexture(leftLost, rightLost, blinking, false);
    }

    /**
     * Resolves the active overlay texture identifier supporting frozen hearts.
     */
    public static Identifier getOverlayTexture(boolean leftLost, boolean rightLost, boolean blinking, boolean frozen) {
        if (initialized) {
            if (frozen) {
                if (leftLost && rightLost) {
                    return blinking ? DYNAMIC_FROZEN_SCORCH_FULL_BLINKING : DYNAMIC_FROZEN_SCORCH_FULL;
                } else if (leftLost) {
                    return blinking ? DYNAMIC_FROZEN_SCORCH_HALF_BLINKING : DYNAMIC_FROZEN_SCORCH_HALF;
                } else if (rightLost) {
                    return blinking ? DYNAMIC_FROZEN_SCORCH_HALF_RIGHT_BLINKING : DYNAMIC_FROZEN_SCORCH_HALF_RIGHT;
                }
                return null;
            }
            if (leftLost && rightLost) {
                return blinking ? DYNAMIC_FULL_BLINKING : DYNAMIC_FULL;
            } else if (leftLost) {
                return blinking ? DYNAMIC_HALF_BLINKING : DYNAMIC_HALF;
            } else if (rightLost) {
                return blinking ? DYNAMIC_HALF_RIGHT_BLINKING : DYNAMIC_HALF_RIGHT;
            }
            return null;
        }
        return HeartOverlayRenderer.getOverlaySprite(leftLost, rightLost, blinking, frozen);
    }

    /**
     * Synthesizes dynamic textures by sampling the active heart silhouette mask and
     * applying it to the 9x9 master pixel-art crack templates.
     */
    public static void reload(ResourceManager resourceManager) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        TextureManager textureManager = client.getTextureManager();
        if (textureManager == null || resourceManager == null) {
            return;
        }

        NativeImage masterFull = null;
        NativeImage masterBlink = null;
        NativeImage scorchFull = null;
        NativeImage scorchBlink = null;
        try {
            // 1. Load active heart sprite to determine silhouette and border mask
            boolean[][] mask = extractHeartMask(resourceManager, VANILLA_HEART_ID,
                    "/assets/minecraft/textures/gui/sprites/hud/heart/full.png");

            // 2. Load master 9x9 pixel art templates
            masterFull = loadNativeImage(resourceManager, MASTER_FULL_ID,
                    "/assets/fireballpredictor/textures/gui/sprites/hud/heart/cracking_master_full.png");
            masterBlink = loadNativeImage(resourceManager, MASTER_BLINK_ID,
                    "/assets/fireballpredictor/textures/gui/sprites/hud/heart/cracking_master_full_blinking.png");

            scorchFull = loadNativeImage(resourceManager, MASTER_SCORCH_FULL_ID,
                    "/assets/fireballpredictor/textures/gui/sprites/hud/heart/cracking_master_frozen_scorch_full.png");
            scorchBlink = loadNativeImage(resourceManager, MASTER_SCORCH_BLINK_ID,
                    "/assets/fireballpredictor/textures/gui/sprites/hud/heart/cracking_master_frozen_scorch_full_blinking.png");

            if (masterFull == null || masterBlink == null) {
                LOGGER.warn("Failed to load master pixel-art cracking textures; using static fallback.");
                return;
            }

            // 3. Synthesize standard warm variants
            NativeImage imgFull = synthesize(masterFull, mask, true, true);
            NativeImage imgFullBlink = synthesize(masterBlink, mask, true, true);
            NativeImage imgHalfLeft = synthesize(masterFull, mask, true, false);
            NativeImage imgHalfLeftBlink = synthesize(masterBlink, mask, true, false);
            NativeImage imgHalfRight = synthesize(masterFull, mask, false, true);
            NativeImage imgHalfRightBlink = synthesize(masterBlink, mask, false, true);

            // 4. Synthesize frozen scorch variants using the exact same silhouette & border mask
            NativeImage sFullSrc = scorchFull != null ? scorchFull : masterFull;
            NativeImage sBlinkSrc = scorchBlink != null ? scorchBlink : masterBlink;
            NativeImage sFull = synthesize(sFullSrc, mask, true, true);
            NativeImage sFullBlink = synthesize(sBlinkSrc, mask, true, true);
            NativeImage sHalfLeft = synthesize(sFullSrc, mask, true, false);
            NativeImage sHalfLeftBlink = synthesize(sBlinkSrc, mask, true, false);
            NativeImage sHalfRight = synthesize(sFullSrc, mask, false, true);
            NativeImage sHalfRightBlink = synthesize(sBlinkSrc, mask, false, true);

            // 5. Close old dynamic textures if any
            closeDynamicTextures();

            // 6. Register standard warm textures
            registerTexture(textureManager, 0, DYNAMIC_FULL, imgFull);
            registerTexture(textureManager, 1, DYNAMIC_FULL_BLINKING, imgFullBlink);
            registerTexture(textureManager, 2, DYNAMIC_HALF, imgHalfLeft);
            registerTexture(textureManager, 3, DYNAMIC_HALF_BLINKING, imgHalfLeftBlink);
            registerTexture(textureManager, 4, DYNAMIC_HALF_RIGHT, imgHalfRight);
            registerTexture(textureManager, 5, DYNAMIC_HALF_RIGHT_BLINKING, imgHalfRightBlink);

            // 7. Register frozen scorch textures
            registerTexture(textureManager, 6, DYNAMIC_FROZEN_SCORCH_FULL, sFull);
            registerTexture(textureManager, 7, DYNAMIC_FROZEN_SCORCH_FULL_BLINKING, sFullBlink);
            registerTexture(textureManager, 8, DYNAMIC_FROZEN_SCORCH_HALF, sHalfLeft);
            registerTexture(textureManager, 9, DYNAMIC_FROZEN_SCORCH_HALF_BLINKING, sHalfLeftBlink);
            registerTexture(textureManager, 10, DYNAMIC_FROZEN_SCORCH_HALF_RIGHT, sHalfRight);
            registerTexture(textureManager, 11, DYNAMIC_FROZEN_SCORCH_HALF_RIGHT_BLINKING, sHalfRightBlink);

            initialized = true;
            LOGGER.info("Successfully synthesized silhouette-adaptive 9x9 pixel-art heart damage overlays (standard & frozen).");
        } catch (Exception e) {
            LOGGER.error("Error synthesizing dynamic heart overlays", e);
            initialized = false;
        } finally {
            if (masterFull != null) masterFull.close();
            if (masterBlink != null) masterBlink.close();
            if (scorchFull != null) scorchFull.close();
            if (scorchBlink != null) scorchBlink.close();
        }
    }

    /**
     * Synthesizes a 9x9 masked overlay image.
     *
     * @param master the 9x9 master pixel-art texture
     * @param mask 9x9 boolean mask where true indicates an active pixel in the icon silhouette
     * @param includeLeft whether left-half units (x <= 4) are lost/cracked
     * @param includeRight whether right-half units (x >= 5) are lost/cracked
     * @return a new 9x9 NativeImage containing the masked pixels
     */
    public static NativeImage synthesize(NativeImage master, boolean[][] mask, boolean includeLeft, boolean includeRight) {
        NativeImage result = new NativeImage(9, 9, false);
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                boolean halfAllowed = (x <= 4 && includeLeft) || (x >= 5 && includeRight);
                if (halfAllowed && mask[y][x]) {
                    result.setPixel(x, y, master.getPixel(x, y));
                } else {
                    result.setPixel(x, y, 0); // alpha 0
                }
            }
        }
        return result;
    }

    /**
     * Extracts a 9x9 boolean silhouette mask from the active heart icon sprite in the resource pack.
     */
    public static boolean[][] extractHeartMask(ResourceManager resourceManager) {
        return extractHeartMask(resourceManager, VANILLA_HEART_ID,
                "/assets/minecraft/textures/gui/sprites/hud/heart/full.png");
    }

    public static boolean isStandardHeartBorderCoord(int x, int y) {
        return HeartMaskHelper.isStandardHeartBorderCoord(x, y);
    }

    public static boolean isDarkBorderPixel(int pixel) {
        return HeartMaskHelper.isDarkBorderPixel(pixel);
    }

    /**
     * Extracts a 9x9 boolean silhouette mask from a specific heart icon sprite in the resource pack.
     * Dark outer border outline pixels (common in custom/PvP resource packs) are filtered out to ensure
     * damage cracking overlays only cover the interior fill and never overwrite the structural heart borders.
     */
    public static boolean[][] extractHeartMask(ResourceManager resourceManager, Identifier spriteId, String fallbackPath) {
        boolean[][] mask = new boolean[9][9];
        NativeImage heartImg = loadNativeImage(resourceManager, spriteId, fallbackPath);

        if (heartImg == null) {
            return defaultVanillaMask();
        }

        NativeImage containerImg = null;
        if (resourceManager != null) {
            containerImg = loadNativeImage(resourceManager, VANILLA_CONTAINER_ID,
                    "/assets/minecraft/textures/gui/sprites/hud/heart/container.png");
        }

        int activePixels = 0;
        try {
            int w = heartImg.getWidth();
            int h = heartImg.getHeight();
            int cw = containerImg != null ? containerImg.getWidth() : 0;
            int ch = containerImg != null ? containerImg.getHeight() : 0;

            for (int y = 0; y < 9; y++) {
                for (int x = 0; x < 9; x++) {
                    int sampleX = Math.clamp((int) ((x + 0.5f) * w / 9.0f), 0, w - 1);
                    int sampleY = Math.clamp((int) ((y + 0.5f) * h / 9.0f), 0, h - 1);

                    int pixel = heartImg.getPixel(sampleX, sampleY);

                    // Cross-reference container border if available
                    boolean isContainerBorder = false;
                    if (containerImg != null) {
                        int csx = Math.clamp((int) ((x + 0.5f) * cw / 9.0f), 0, cw - 1);
                        int csy = Math.clamp((int) ((y + 0.5f) * ch / 9.0f), 0, ch - 1);
                        int cPixel = containerImg.getPixel(csx, csy);
                        if (ARGB.alpha(cPixel) > 16 && HeartMaskHelper.isDarkBorderPixel(cPixel)) {
                            isContainerBorder = true;
                        }
                    }

                    boolean active = HeartMaskHelper.isMaskPixelActive(x, y, pixel, isContainerBorder);
                    mask[y][x] = active;
                    if (active) {
                        activePixels++;
                    }
                }
            }
        } finally {
            heartImg.close();
            if (containerImg != null) {
                containerImg.close();
            }
        }

        if (activePixels == 0) {
            return defaultVanillaMask();
        }
        return mask;
    }

    public static boolean[][] defaultVanillaMask() {
        return HeartMaskHelper.defaultVanillaMask();
    }

    private static void registerTexture(TextureManager manager, int index, Identifier id, NativeImage image) {
        DynamicTexture texture = new DynamicTexture(() -> "heart_cracking_overlay", image);
        texture.upload();
        manager.register(id, texture);
        DYNAMIC_TEXTURES[index] = texture;
    }

    private static void closeDynamicTextures() {
        for (int i = 0; i < DYNAMIC_TEXTURES.length; i++) {
            if (DYNAMIC_TEXTURES[i] != null) {
                try {
                    DYNAMIC_TEXTURES[i].close();
                } catch (Exception ignored) {
                }
                DYNAMIC_TEXTURES[i] = null;
            }
        }
    }

    private static NativeImage loadNativeImage(ResourceManager resourceManager, Identifier id, String classpathFallback) {
        if (resourceManager != null) {
            Optional<Resource> resource = resourceManager.getResource(id);
            if (resource.isPresent()) {
                try (InputStream stream = resource.get().open()) {
                    return NativeImage.read(stream);
                } catch (Exception e) {
                    LOGGER.debug("Could not read resource {}, trying fallback", id);
                }
            }
        }
        try (InputStream stream = DynamicHeartOverlayManager.class.getResourceAsStream(classpathFallback)) {
            if (stream != null) {
                return NativeImage.read(stream);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not read classpath stream {}", classpathFallback);
        }
        return null;
    }
}
