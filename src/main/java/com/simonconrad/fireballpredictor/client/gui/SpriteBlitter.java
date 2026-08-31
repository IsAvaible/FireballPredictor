package com.simonconrad.fireballpredictor.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;

import static net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;

/**
 * 1.21.11 counterpart of 26.2's {@code GuiGraphics.blitSprite}. The vanilla gui
 * atlas is addressed through {@link SpriteIdentifier}s and drawn stretched.
 */
public final class SpriteBlitter {

    private SpriteBlitter() {
    }

    /** Draws the gui-atlas sprite identified by {@code textureId} at (x, y) with the given size. */
    public static void draw(DrawContext g, Identifier textureId, int x, int y, int w, int h) {
        draw(g, GUI_TEXTURED, textureId, x, y, w, h);
    }

    /** Variant with an explicit pipeline for parity with blitSprite call sites. */
    public static void draw(DrawContext g, RenderPipeline pipeline, Identifier textureId, int x, int y, int w, int h) {
        g.drawSpriteStretched(pipeline,
                g.getSprite(new SpriteIdentifier(Identifier.ofVanilla("gui"), textureId)),
                x, y, w, h);
    }
}
