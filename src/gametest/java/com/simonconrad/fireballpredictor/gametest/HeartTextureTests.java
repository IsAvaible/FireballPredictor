package com.simonconrad.fireballpredictor.gametest;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

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
            "cracking_frozen_full",
            "cracking_frozen_full_blinking",
            "cracking_frozen_half",
            "cracking_frozen_half_blinking",
            "cracking_frozen_half_right",
            "cracking_frozen_half_right_blinking",
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
            "cracking_master_frozen_full",
            "cracking_master_frozen_full_blinking",
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
            String pathInHud = "/assets/fireballpredictor/textures/hud/heart/" + name + ".png";

            BufferedImage imgSprites = loadResourceImage(pathInSprites);
            BufferedImage imgHud = loadResourceImage(pathInHud);

            if (imgSprites.getWidth() != 9 || imgSprites.getHeight() != 9) {
                throw fail("Expected 9x9 sprite at " + pathInSprites + ", got " + imgSprites.getWidth() + "x" + imgSprites.getHeight());
            }
            if (imgHud.getWidth() != 9 || imgHud.getHeight() != 9) {
                throw fail("Expected 9x9 sprite at " + pathInHud + ", got " + imgHud.getWidth() + "x" + imgHud.getHeight());
            }
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testOverlayTransparencyConstraints(GameTestHelper context) {
        resetGlobalState();

        String[] halfRightOverlays = {
                "cracking_half_right", "cracking_half_right_blinking",
                "cracking_frozen_half_right", "cracking_frozen_half_right_blinking",
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
                "cracking_frozen_half", "cracking_frozen_half_blinking",
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
                "cracking_frozen_full", "cracking_frozen_full_blinking",
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
