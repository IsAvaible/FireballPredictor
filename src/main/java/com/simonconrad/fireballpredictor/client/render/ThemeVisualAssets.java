package com.simonconrad.fireballpredictor.client.render;

/**
 * Shared immutable bitmap assets, font glyph bitmasks, and sprite palettes
 * used by both 3D world theme rendering and 2D GUI previews.
 */
public final class ThemeVisualAssets {

    private ThemeVisualAssets() {
    }

    /** 16 3x5 binary matrix font glyphs (15 bits per glyph: 5 rows of 3 bits). */
    public static final short[] MATRIX_GLYPHS = {
        0x7B6F, // 0
        0x2492, // 1
        0x73E7, // 2
        0x73CF, // 3
        0x5BC9, // 4
        0x79CF, // 5
        0x79EF, // 6
        0x7249, // 7
        0x7BEF, // 8
        0x7BCF, // 9
        0x7BE9, // A
        0x79E7, // E
        0x79E0, // F
        0x5BE5, // H
        0x52A5, // X
        0x7247  // Z
    };

    /** 8 5x5 binary arcade sprites (25 bits per sprite). */
    public static final int[] ARCADE_SPRITES = {
        0x155BEB1, // Space Invader
        0x0BFFFF4, // Retro Heart
        0x0CDDDA0, // Retro Cherries
        0x04FBE44, // Retro Star
        0x0FBEBEF, // Retro Ghost
        0x0EAEAEE, // Retro Coin / Pac-Dot
        0x0EBAAE0, // Retro Mushroom
        0x1F151F1  // Retro Question / Gem
    };

    /** Precomputed sprite RGB palette. */
    public static final int[][] ARCADE_SPRITE_COLORS = {
        {0, 240, 255},    // Space Invader: Cyan
        {255, 0, 85},     // Heart: Magenta
        {255, 50, 50},    // Cherries: Red
        {255, 230, 0},    // Star: Coin Gold
        {57, 255, 20},    // Ghost: Phosphor Green
        {255, 170, 0},    // Coin: Neon Orange
        {168, 85, 247},   // Mushroom: Arcade Purple
        {255, 255, 255}   // Gem: Pure White
    };

    /** Returns RGB array for the given arcade sprite index. */
    public static int[] getArcadeSpriteColor(int spriteIdx) {
        return ARCADE_SPRITE_COLORS[spriteIdx & 7];
    }

    /** Returns packed 0xRRGGBB integer for the given arcade sprite index. */
    public static int getArcadeSpriteColorPacked(int spriteIdx) {
        int[] rgb = ARCADE_SPRITE_COLORS[spriteIdx & 7];
        return (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }
}
