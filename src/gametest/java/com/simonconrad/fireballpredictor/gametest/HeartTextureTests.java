package com.simonconrad.fireballpredictor.gametest;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;

import com.simonconrad.fireballpredictor.client.render.HeartMaskHelper;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ARGB;

public class HeartTextureTests extends GameTestBase {

    private static final String[] STATIC_SPRITE_NAMES = {
            "cracking_full",
            "cracking_full_blinking",
            "cracking_half",
            "cracking_half_blinking",
            "cracking_half_right",
            "cracking_half_right_blinking",
            "cracking_master_full",
            "cracking_master_full_blinking",
            "cracking_frozen_scorch_full",
            "cracking_frozen_scorch_full_blinking",
            "cracking_frozen_scorch_half",
            "cracking_frozen_scorch_half_blinking",
            "cracking_frozen_scorch_half_right",
            "cracking_frozen_scorch_half_right_blinking",
            "cracking_frozen_shatter_full",
            "cracking_frozen_shatter_full_blinking",
            "cracking_frozen_shatter_half",
            "cracking_frozen_shatter_half_blinking",
            "cracking_frozen_shatter_half_right",
            "cracking_frozen_shatter_half_right_blinking",
            "cracking_master_frozen_scorch_full",
            "cracking_master_frozen_scorch_full_blinking",
            "cracking_master_frozen_shatter_full",
            "cracking_master_frozen_shatter_full_blinking"
    };

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testOverlaySpriteAssetsAndDimensions(GameTestHelper context) {
        resetGlobalState();

        for (String name : STATIC_SPRITE_NAMES) {
            String pathInSprites = "/assets/fireballpredictor/textures/gui/sprites/hud/heart/" + name + ".png";

            BufferedImage imgSprites = loadResourceImage(pathInSprites);

            if (imgSprites.getWidth() != 9 || imgSprites.getHeight() != 9) {
                throw fail("Expected 9x9 sprite at " + pathInSprites + ", got " + imgSprites.getWidth() + "x" + imgSprites.getHeight());
            }
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testOverlayTransparencyConstraints(GameTestHelper context) {
        resetGlobalState();

        String[] halfRightOverlays = {
                "cracking_half_right", "cracking_half_right_blinking",
                "cracking_frozen_scorch_half_right", "cracking_frozen_scorch_half_right_blinking",
                "cracking_frozen_shatter_half_right", "cracking_frozen_shatter_half_right_blinking"
        };
        for (String name : halfRightOverlays) {
            BufferedImage img = loadResourceImage("/assets/fireballpredictor/textures/gui/sprites/hud/heart/" + name + ".png");
            // Left half (x <= 4) must be 100% transparent
            for (int y = 0; y < 9; y++) {
                for (int x = 0; x <= 4; x++) {
                    int alpha = (img.getRGB(x, y) >> 24) & 0xFF;
                    if (alpha != 0) {
                        throw fail("Pixel (" + x + "," + y + ") in " + name + " must have alpha 0, but got " + alpha);
                    }
                }
            }
            // Right half (x >= 5) must contain exactly 14 crack pixels
            int rightCracks = 0;
            for (int y = 0; y < 9; y++) {
                for (int x = 5; x < 9; x++) {
                    if (((img.getRGB(x, y) >> 24) & 0xFF) > 16) {
                        rightCracks++;
                    }
                }
            }
            if (rightCracks != 14) {
                throw fail(name + " has " + rightCracks + " crack pixels on right half, expected exactly 14");
            }
        }

        String[] halfLeftOverlays = {
                "cracking_half", "cracking_half_blinking",
                "cracking_frozen_scorch_half", "cracking_frozen_scorch_half_blinking",
                "cracking_frozen_shatter_half", "cracking_frozen_shatter_half_blinking"
        };
        for (String name : halfLeftOverlays) {
            BufferedImage img = loadResourceImage("/assets/fireballpredictor/textures/gui/sprites/hud/heart/" + name + ".png");
            // Right half (x >= 5) must be 100% transparent
            for (int y = 0; y < 9; y++) {
                for (int x = 5; x < 9; x++) {
                    int alpha = (img.getRGB(x, y) >> 24) & 0xFF;
                    if (alpha != 0) {
                        throw fail("Pixel (" + x + "," + y + ") in " + name + " must have alpha 0, but got " + alpha);
                    }
                }
            }
            // Left half (x <= 4) must contain exactly 20 crack pixels
            int leftCracks = 0;
            for (int y = 0; y < 9; y++) {
                for (int x = 0; x <= 4; x++) {
                    if (((img.getRGB(x, y) >> 24) & 0xFF) > 16) {
                        leftCracks++;
                    }
                }
            }
            if (leftCracks != 20) {
                throw fail(name + " has " + leftCracks + " crack pixels on left half, expected exactly 20");
            }
        }

        String[] fullOverlays = {
                "cracking_full", "cracking_full_blinking",
                "cracking_frozen_scorch_full", "cracking_frozen_scorch_full_blinking",
                "cracking_frozen_shatter_full", "cracking_frozen_shatter_full_blinking"
        };
        for (String name : fullOverlays) {
            BufferedImage img = loadResourceImage("/assets/fireballpredictor/textures/gui/sprites/hud/heart/" + name + ".png");
            int fullCracks = 0;
            for (int y = 0; y < 9; y++) {
                for (int x = 0; x < 9; x++) {
                    if (((img.getRGB(x, y) >> 24) & 0xFF) > 16) {
                        fullCracks++;
                    }
                }
            }
            if (fullCracks != 34) {
                throw fail(name + " has " + fullCracks + " crack pixels, expected exactly 34");
            }
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testMasterPixelArtAssets(GameTestHelper context) {
        resetGlobalState();

        String[] masterFulls = {
                "cracking_master_full",
                "cracking_master_frozen_scorch_full",
                "cracking_master_frozen_shatter_full"
        };
        String[] masterBlinks = {
                "cracking_master_full_blinking",
                "cracking_master_frozen_scorch_full_blinking",
                "cracking_master_frozen_shatter_full_blinking"
        };

        int[][] corners = {{1, 1}, {7, 1}, {1, 7}, {7, 7}};

        for (int i = 0; i < masterFulls.length; i++) {
            BufferedImage full = loadResourceImage("/assets/fireballpredictor/textures/gui/sprites/hud/heart/" + masterFulls[i] + ".png");
            BufferedImage blink = loadResourceImage("/assets/fireballpredictor/textures/gui/sprites/hud/heart/" + masterBlinks[i] + ".png");

            for (int[] corner : corners) {
                int x = corner[0];
                int y = corner[1];
                int alphaFull = (full.getRGB(x, y) >> 24) & 0xFF;
                int alphaBlink = (blink.getRGB(x, y) >> 24) & 0xFF;
                if (alphaFull == 0) {
                    throw fail("Corner pixel (" + x + "," + y + ") in " + masterFulls[i] + " must have alpha > 0");
                }
                if (alphaBlink == 0) {
                    throw fail("Corner pixel (" + x + "," + y + ") in " + masterBlinks[i] + " must have alpha > 0");
                }
            }
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testDynamicSynthesisMasking(GameTestHelper context) {
        resetGlobalState();

        String[][] pairs = {
                {"cracking_master_full", "cracking_full"},
                {"cracking_master_full_blinking", "cracking_full_blinking"},
                {"cracking_master_frozen_scorch_full", "cracking_frozen_scorch_full"},
                {"cracking_master_frozen_scorch_full_blinking", "cracking_frozen_scorch_full_blinking"},
                {"cracking_master_frozen_shatter_full", "cracking_frozen_shatter_full"},
                {"cracking_master_frozen_shatter_full_blinking", "cracking_frozen_shatter_full_blinking"}
        };

        boolean[][] vanillaMask = {
                {false, false, false, false, false, false, false, false, false},
                {false, false,  true,  true, false,  true,  true, false, false},
                {false,  true,  true,  true,  true,  true,  true,  true, false},
                {false,  true,  true,  true,  true,  true,  true,  true, false},
                {false,  true,  true,  true,  true,  true,  true,  true, false},
                {false, false,  true,  true,  true,  true,  true, false, false},
                {false, false, false,  true,  true,  true, false, false, false},
                {false, false, false, false,  true, false, false, false, false},
                {false, false, false, false, false, false, false, false, false}
        };

        for (String[] pair : pairs) {
            BufferedImage master = loadResourceImage("/assets/fireballpredictor/textures/gui/sprites/hud/heart/" + pair[0] + ".png");
            BufferedImage orig = loadResourceImage("/assets/fireballpredictor/textures/gui/sprites/hud/heart/" + pair[1] + ".png");

            for (int y = 0; y < 9; y++) {
                for (int x = 0; x < 9; x++) {
                    int expectedRgb = vanillaMask[y][x] ? master.getRGB(x, y) : 0;
                    int actualRgb = orig.getRGB(x, y);
                    int expectedAlpha = (expectedRgb >> 24) & 0xFF;
                    int actualAlpha = (actualRgb >> 24) & 0xFF;
                    if (expectedAlpha == 0 && actualAlpha == 0) {
                        continue;
                    }
                    if (expectedRgb != actualRgb) {
                        throw fail("Synthesis mismatch for " + pair[0] + " at (" + x + "," + y + "): expected=0x" +
                                Integer.toHexString(expectedRgb) + ", actual=0x" + Integer.toHexString(actualRgb));
                    }
                }
            }
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testThermalScorchPaletteNoAbsorptionYellow(GameTestHelper context) {
        resetGlobalState();

        String[] scorchBlinkOverlays = {
                "cracking_frozen_scorch_full_blinking",
                "cracking_frozen_scorch_half_blinking",
                "cracking_frozen_scorch_half_right_blinking",
                "cracking_master_frozen_scorch_full_blinking"
        };

        for (String name : scorchBlinkOverlays) {
            BufferedImage img = loadResourceImage("/assets/fireballpredictor/textures/gui/sprites/hud/heart/" + name + ".png");
            for (int y = 0; y < 9; y++) {
                for (int x = 0; x < 9; x++) {
                    int rgb = img.getRGB(x, y);
                    int alpha = (rgb >> 24) & 0xFF;
                    if (alpha == 0) continue;
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;

                    if (r == 0xFF && g == 0xCD && b == 0x6E) {
                        throw fail(name + " contains absorption heart yellow (#FFCD6E) at (" + x + "," + y + ")");
                    }
                    if (r == 0xFF && g == 0xF5 && b == 0xB4) {
                        throw fail(name + " contains pale absorption yellow (#FFF5B4) at (" + x + "," + y + ")");
                    }
                }
            }
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testStandardHeartBorderCoordinatesCountAndAccuracy(GameTestHelper context) {
        resetGlobalState();

        int borderCoordCount = 0;
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                if (HeartMaskHelper.isStandardHeartBorderCoord(x, y)) {
                    borderCoordCount++;
                }
            }
        }

        // Must exactly match the 20 perimeter border pixels of standard Minecraft hearts
        if (borderCoordCount != 20) {
            throw fail("Expected exactly 20 standard heart border coordinates, but got " + borderCoordCount);
        }

        // Cleft coordinate (4,1) and lobe tops must be recognized as border coordinates
        if (!HeartMaskHelper.isStandardHeartBorderCoord(4, 1)) {
            throw fail("Coordinate (4,1) (heart cleft) must be identified as a standard border coordinate");
        }
        if (!HeartMaskHelper.isStandardHeartBorderCoord(4, 8)) {
            throw fail("Coordinate (4,8) (bottom tip) must be identified as a standard border coordinate");
        }

        // Interior fill coordinates must NOT be border coordinates
        if (HeartMaskHelper.isStandardHeartBorderCoord(4, 3)
                || HeartMaskHelper.isStandardHeartBorderCoord(2, 2)
                || HeartMaskHelper.isStandardHeartBorderCoord(6, 2)) {
            throw fail("Interior heart coordinates must not be classified as border coordinates");
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testDarkBorderPixelDetection(GameTestHelper context) {
        resetGlobalState();

        // Transparent pixels should not be dark borders
        if (HeartMaskHelper.isDarkBorderPixel(0)) {
            throw fail("Transparent pixel (alpha 0) should not be dark border");
        }

        // Black and dark border outline colors
        int black = ARGB.color(255, 0, 0, 0);
        int darkOutline1 = ARGB.color(255, 30, 0, 0);
        int darkOutline2 = ARGB.color(255, 20, 20, 20);
        int darkOutline3 = ARGB.color(255, 36, 12, 12);
        if (!HeartMaskHelper.isDarkBorderPixel(black)
                || !HeartMaskHelper.isDarkBorderPixel(darkOutline1)
                || !HeartMaskHelper.isDarkBorderPixel(darkOutline2)
                || !HeartMaskHelper.isDarkBorderPixel(darkOutline3)) {
            throw fail("Dark border colors must be recognized by isDarkBorderPixel");
        }

        // Normal heart fill and effect colors must NOT be dark border pixels
        int heartRed = ARGB.color(255, 255, 0, 0);
        int heartShadedRed = ARGB.color(255, 180, 20, 20);
        int absorptionGold = ARGB.color(255, 255, 205, 110);
        int iceBlue = ARGB.color(255, 100, 200, 255);
        if (HeartMaskHelper.isDarkBorderPixel(heartRed)
                || HeartMaskHelper.isDarkBorderPixel(heartShadedRed)
                || HeartMaskHelper.isDarkBorderPixel(absorptionGold)
                || HeartMaskHelper.isDarkBorderPixel(iceBlue)) {
            throw fail("Vibrant heart fill colors must not be classified as dark border pixels");
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testBlinkingMasterCleftPixelColor(GameTestHelper context) {
        resetGlobalState();

        BufferedImage masterBlink = loadResourceImage("/assets/fireballpredictor/textures/gui/sprites/hud/heart/cracking_master_full_blinking.png");
        int rgb = masterBlink.getRGB(4, 1);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // Cleft at (4,1) must be dark reddish brown seam [781400] instead of pale yellow [FFF5B4]
        if (r == 0xFF && g == 0xF5 && b == 0xB4) {
            throw fail("Master blinking cleft at (4,1) must not be pale yellow (#FFF5B4)");
        }
        if (r != 120 || g != 20 || b != 0) {
            throw fail("Master blinking cleft at (4,1) expected #781400 (120, 20, 0), got (" + r + "," + g + "," + b + ")");
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testBakedBorderMaskFiltering(GameTestHelper context) {
        resetGlobalState();

        boolean[][] vanillaMask = HeartMaskHelper.defaultVanillaMask();
        int darkBorderColor = ARGB.color(255, 18, 10, 10);
        int redFillColor = ARGB.color(255, 220, 20, 20);

        // Simulate a custom resource pack sprite with baked borders and red interior
        int keptCount = 0;
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                int pixel;
                if (vanillaMask[y][x]) {
                    pixel = redFillColor;
                } else if (HeartMaskHelper.isStandardHeartBorderCoord(x, y)) {
                    pixel = darkBorderColor;
                } else {
                    pixel = 0; // transparent
                }

                int alpha = ARGB.alpha(pixel);
                boolean isBorderCoord = HeartMaskHelper.isStandardHeartBorderCoord(x, y);
                boolean isDarkBorder = HeartMaskHelper.isDarkBorderPixel(pixel);

                boolean keep = (alpha > 16) && !(isBorderCoord && isDarkBorder);
                if (keep) {
                    keptCount++;
                    if (!vanillaMask[y][x]) {
                        throw fail("Pixel at (" + x + "," + y + ") was kept but is not in vanilla interior mask");
                    }
                } else if (vanillaMask[y][x]) {
                    throw fail("Pixel at (" + x + "," + y + ") is in vanilla interior mask but was filtered out");
                }
            }
        }

        if (keptCount != 34) {
            throw fail("Expected exactly 34 interior pixels after filtering baked border, got " + keptCount);
        }

        context.succeed();
    }

    private BufferedImage loadResourceImage(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw fail("Missing asset resource: " + path);
            }
            return ImageIO.read(is);
        } catch (Exception e) {
            throw fail("Failed to read image at " + path + ": " + e.getMessage());
        }
    }
}
