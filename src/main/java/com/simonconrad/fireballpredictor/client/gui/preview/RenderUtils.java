package com.simonconrad.fireballpredictor.client.gui.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared rendering utilities: colour packing, AA primitives, item-icon drawing,
 * and the monotonic animation clock.
 */
final class RenderUtils {

    private RenderUtils() {
    }

    // ---- Tuning constants ---------------------------------------------------

    static final int DASH_PERIOD_PX = 10;
    static final int DASH_ON_PX = 6;

    private static final long START_NANOS = System.nanoTime();

    // ---- Item-render state --------------------------------------------------

    /** Cached capability probes so a missing sprite/API doesn't throw per frame. */
    static boolean effectSpriteAvailable = true;

    /**
     * Only disabled for actual API/linkage problems. Runtime failures can be
     * title-screen / no-world transient failures.
     */
    static boolean itemRenderApiAvailable = true;

    static long nextItemRenderAttemptNanos = 0L;
    static final long ITEM_RENDER_RETRY_NANOS = 500_000_000L;

    // ---- Vanilla texture identifiers ----------------------------------------

    static final Identifier FIRE_CHARGE_ICON =
            Identifier.withDefaultNamespace("textures/item/fire_charge.png");
    static final Identifier WIND_CHARGE_ICON =
            Identifier.withDefaultNamespace("textures/item/wind_charge.png");
    static final Identifier BLAZE_POWDER_ICON =
            Identifier.withDefaultNamespace("textures/item/blaze_powder.png");
    static final Identifier GHAST_TEAR_ICON =
            Identifier.withDefaultNamespace("textures/item/ghast_tear.png");

    static final Identifier WITHER_SKELETON_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/skeleton/wither_skeleton.png");
    static final Identifier DRAGON_FIREBALL_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon_fireball.png");

    static final Identifier DEFAULT_SKIN_WIDE =
            Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
    static final Identifier DEFAULT_SKIN_LEGACY =
            Identifier.withDefaultNamespace("textures/entity/steve.png");

    static final Identifier DISPENSER_FRONT =
            Identifier.withDefaultNamespace("textures/block/dispenser_front.png");
    static final Identifier COMMAND_BLOCK_FRONT =
            Identifier.withDefaultNamespace("textures/block/command_block_front.png");

    /**
     * Command-block faces are animated strips; we only ever want frame 0.
     * If the vanilla frame count ever changes, the worst case is a slightly
     * mis-cropped icon — never a crash.
     */
    private static final int COMMAND_BLOCK_FRAMES = 4;

    // ---- Clock --------------------------------------------------------------

    /** Monotonic seconds since class load — no hourly wrap discontinuity. */
    static float seconds() {
        return (System.nanoTime() - START_NANOS) / 1_000_000_000.0f;
    }

    // ---- Colour helpers -----------------------------------------------------

    /** Lightens a single channel by blending toward 255. */
    static int lighten(int channel, float amount) {
        return Math.min(255, channel + Math.round((255 - channel) * amount));
    }

    /** Hermite smoothstep. */
    static float smoothstep(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /** Pack RGBA into an ARGB int (Minecraft's convention). */
    static int pack(int r, int g, int b, int a) {
        return ((Mth.clamp(a, 0, 255)) << 24)
                | ((Mth.clamp(r, 0, 255)) << 16)
                | ((Mth.clamp(g, 0, 255)) << 8)
                | (Mth.clamp(b, 0, 255));
    }

    // ---- UI helpers ---------------------------------------------------------

    /** Centred "disabled" label drawn with the Minecraft font. */
    static void drawDisabledLabel(Painter p, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return;
        }
        String label = "disabled";
        int tw = mc.font.width(label);
        p.graphics().text(mc.font, label,
                x + (w - tw) / 2,
                y + h / 2 - mc.font.lineHeight / 2,
                0x88A0A8B0);
    }

    // ---- Item icon drawing --------------------------------------------------

    /**
     * Renders a vanilla item at an arbitrary size, with layered fallbacks:
     * <ol>
     *   <li>Proper GUI item render (only when a ClientLevel exists).</li>
     *   <li>Direct vanilla texture blit — curated entity/block faces for the
     *       icons this preview uses, plus a generic
     *       {@code textures/item/...} / {@code textures/block/...} lookup for
     *       anything else.</li>
     *   <li>Flat coloured swatch as emergency fallback.</li>
     * </ol>
     */
    static void drawItemIcon(Painter p, Item item, int x, int y, int size, int fallbackArgb) {
        if (item == Items.DRAGON_HEAD) {
            if (drawKnownVanillaIconTexture(p, item, x, y, size)) {
                return;
            }
        } else {
            if (tryDrawRenderedItemIcon(p, item, x, y, size)) {
                return;
            }
            if (drawKnownVanillaIconTexture(p, item, x, y, size)) {
                return;
            }
        }
        p.fill(x + 1, y + 1, x + size - 1, y + size - 1, fallbackArgb);
    }

    private static boolean tryDrawRenderedItemIcon(Painter p, Item item, int x, int y, int size) {
        if (!itemRenderApiAvailable) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();

        // Do not call item()/fakeItem() from the title screen. In 26.x this can
        // fail before a ClientLevel exists, and that failure must not permanently
        // disable item rendering.
        if (mc == null || mc.level == null) {
            return false;
        }

        long now = System.nanoTime();
        if (now < nextItemRenderAttemptNanos) {
            return false;
        }

        try {
            GuiGraphicsExtractor g = p.graphics();
            if (size != 16) {
                var pose = g.pose();
                pose.pushMatrix();
                try {
                    pose.translate(x, y);
                    float scale = size / 16.0f;
                    pose.scale(scale, scale);
                    g.item(new ItemStack(item), 0, 0);
                } finally {
                    pose.popMatrix();
                }
            } else {
                g.item(new ItemStack(item), x, y);
            }

            nextItemRenderAttemptNanos = 0L;
            return true;
        } catch (LinkageError err) {
            // Actual API mismatch / unavailable method: safe to disable permanently.
            itemRenderApiAvailable = false;
            return false;
        } catch (RuntimeException ex) {
            // Runtime failures are often transient (title screen / world transitions).
            nextItemRenderAttemptNanos = System.nanoTime() + ITEM_RENDER_RETRY_NANOS;
            return false;
        }
    }

    // ---- Raw-texture icon fallback -----------------------------------------

    /** A single blit-able source region inside a vanilla texture. */
    private record IconTexture(Identifier texture,
                               int u, int v,
                               int srcW, int srcH,
                               int texW, int texH) {

        /** Whole 16x16 sprite (normal item/block texture). */
        static IconTexture sprite(Identifier texture) {
            return new IconTexture(texture, 0, 0, 16, 16, 16, 16);
        }

        /** First frame of a vertically-stacked animated 16x16 strip. */
        static IconTexture firstFrame(Identifier texture, int frames) {
            return new IconTexture(texture, 0, 0, 16, 16, 16, 16 * Math.max(1, frames));
        }

        /** Arbitrary region of an entity texture (e.g. a head's front face). */
        static IconTexture region(Identifier texture, int u, int v,
                                  int srcW, int srcH, int texW, int texH) {
            return new IconTexture(texture, u, v, srcW, srcH, texW, texH);
        }
    }

    /** Sentinel meaning "no usable texture for this item"; keeps the cache simple. */
    private static final IconTexture NO_ICON =
            IconTexture.sprite(Identifier.withDefaultNamespace("textures/misc/unknown_pack.png"));

    /** Curated overrides where the item id does not map to a usable sprite. */
    private static final Map<Item, List<IconTexture>> CURATED_ICON_TEXTURES = new IdentityHashMap<>();

    /** Resolved (or negatively resolved) lookups, so we probe resources once. */
    private static final Map<Item, IconTexture> ICON_TEXTURE_CACHE = new IdentityHashMap<>();

    static {
        // Plain item sprites (listed explicitly so they skip the generic probe).
        CURATED_ICON_TEXTURES.put(Items.FIRE_CHARGE, List.of(IconTexture.sprite(FIRE_CHARGE_ICON)));
        CURATED_ICON_TEXTURES.put(Items.WIND_CHARGE, List.of(IconTexture.sprite(WIND_CHARGE_ICON)));
        CURATED_ICON_TEXTURES.put(Items.BLAZE_POWDER, List.of(IconTexture.sprite(BLAZE_POWDER_ICON)));
        CURATED_ICON_TEXTURES.put(Items.GHAST_TEAR, List.of(IconTexture.sprite(GHAST_TEAR_ICON)));

        // Heads/skulls: no item sprite exists, so take the face off the entity skin.
        CURATED_ICON_TEXTURES.put(Items.WITHER_SKELETON_SKULL,
                List.of(IconTexture.region(WITHER_SKELETON_TEXTURE, 8, 8, 8, 8, 64, 32)));
        CURATED_ICON_TEXTURES.put(Items.PLAYER_HEAD, List.of(
                IconTexture.region(DEFAULT_SKIN_WIDE, 8, 8, 8, 8, 64, 64),
                IconTexture.region(DEFAULT_SKIN_LEGACY, 8, 8, 8, 8, 64, 64)));

        // Ender Dragon Head item fallback mapped directly to dragon fireball texture (16x16 sprite)
        CURATED_ICON_TEXTURES.put(Items.DRAGON_HEAD,
                List.of(IconTexture.sprite(DRAGON_FIREBALL_TEXTURE)));

        // Blocks: use the recognisable front face rather than the generic side.
        CURATED_ICON_TEXTURES.put(Items.DISPENSER, List.of(
                IconTexture.sprite(DISPENSER_FRONT)));
        CURATED_ICON_TEXTURES.put(Items.COMMAND_BLOCK,
                List.of(IconTexture.firstFrame(COMMAND_BLOCK_FRONT, COMMAND_BLOCK_FRAMES)));
    }

    /** Call on resource reload if you ever want the probes re-run. */
    static void invalidateIconTextureCache() {
        ICON_TEXTURE_CACHE.clear();
    }

    /**
     * Draws an icon directly from a vanilla texture. Used only when the real
     * item renderer is unavailable or fails.
     */
    private static boolean drawKnownVanillaIconTexture(Painter p, Item item, int x, int y, int size) {
        IconTexture icon = resolveIconTexture(item);
        if (icon == null) {
            return false;
        }
        try {
            p.graphics().blit(RenderPipelines.GUI_TEXTURED,
                    icon.texture(), x, y,
                    icon.u(), icon.v(),
                    size, size,
                    icon.srcW(), icon.srcH(),
                    icon.texW(), icon.texH());
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            // Never retry a blit that blew up; fall through to the flat swatch.
            ICON_TEXTURE_CACHE.put(item, NO_ICON);
            return false;
        }
    }

    /** First candidate texture that actually exists in the active resource packs. */
    private static IconTexture resolveIconTexture(Item item) {
        IconTexture cached = ICON_TEXTURE_CACHE.get(item);
        if (cached != null) {
            return cached == NO_ICON ? null : cached;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) {
            // Too early to probe — don't poison the cache with a false negative.
            return null;
        }

        IconTexture resolved = null;
        for (IconTexture candidate : candidatesFor(item)) {
            if (textureExists(candidate.texture())) {
                resolved = candidate;
                break;
            }
        }

        ICON_TEXTURE_CACHE.put(item, resolved == null ? NO_ICON : resolved);
        return resolved;
    }

    /**
     * Curated entry if present, otherwise a best-effort guess derived from the
     * item's registry id: item sprite first, then the common block-face names.
     */
    private static List<IconTexture> candidatesFor(Item item) {
        List<IconTexture> curated = CURATED_ICON_TEXTURES.get(item);
        if (curated != null) {
            return curated;
        }

        Identifier id;
        try {
            id = BuiltInRegistries.ITEM.getKey(item);
        } catch (RuntimeException | LinkageError ignored) {
            return List.of();
        }
        if (id == null) {
            return List.of();
        }

        String ns = id.getNamespace();
        String path = id.getPath();

        List<IconTexture> out = new ArrayList<>(6);
        out.add(IconTexture.sprite(texture(ns, "textures/item/" + path + ".png")));
        out.add(IconTexture.sprite(texture(ns, "textures/block/" + path + ".png")));
        out.add(IconTexture.sprite(texture(ns, "textures/block/" + path + "_front.png")));
        out.add(IconTexture.sprite(texture(ns, "textures/block/" + path + "_side.png")));
        out.add(IconTexture.sprite(texture(ns, "textures/block/" + path + "_top.png")));
        out.add(IconTexture.sprite(texture(ns, "textures/block/" + path + "_still.png")));
        return out;
    }

    private static Identifier texture(String namespace, String path) {
        try {
            return Identifier.fromNamespaceAndPath(namespace, path);
        } catch (RuntimeException ignored) {
            return Identifier.withDefaultNamespace("textures/misc/unknown_pack.png");
        }
    }

    private static boolean textureExists(Identifier id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) {
            return false;
        }
        try {
            return mc.getResourceManager().getResource(id).isPresent();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    // ---- Anti-aliased primitives shared across renderers --------------------

    /**
     * One anti-aliased vertical slice of a ribbon.
     * Used by both the trajectory and tracking renderers.
     */
    static void ribbonColumn(Painter p, int px, float cy, float half,
                             int r, int g, int b, float alpha) {
        alpha = Mth.clamp(alpha, 0.0f, 1.0f);
        if (alpha <= 0.004f || half <= 0.0f) {
            return;
        }
        if (half < 0.5f) {
            p.pixel(px, Mth.floor(cy),
                    pack(r, g, b, Math.round(255 * alpha * (half / 0.5f))));
            return;
        }
        int top = Mth.floor(cy - half);
        int bottom = Mth.floor(cy + half);
        for (int py = top; py <= bottom; py++) {
            float dist = Math.abs(py + 0.5f - cy);
            float coverage = Mth.clamp(half - dist, 0.0f, 1.0f);
            if (coverage <= 0.0f) {
                continue;
            }
            float glow = 0.75f + 0.25f * (1.0f - (dist / half) * (dist / half));
            int a = Math.round(255 * alpha * coverage * glow);
            if (a > 0) {
                p.pixel(px, py, pack(r, g, b, a));
            }
        }
    }

    /**
     * Small radially-faded blob used for the projectile head and impact spark.
     */
    static void softDisc(Painter p, float cx, float cy, float radius,
                         int r, int g, int b, float alpha) {
        alpha = Mth.clamp(alpha, 0.0f, 1.0f);
        if (alpha <= 0.004f || radius <= 0.0f) {
            return;
        }
        int y0 = Mth.floor(cy - radius);
        int y1 = Mth.ceil(cy + radius);
        int x0 = Mth.floor(cx - radius);
        int x1 = Mth.ceil(cx + radius);
        for (int py = y0; py <= y1; py++) {
            float dy = py + 0.5f - cy;
            for (int px = x0; px <= x1; px++) {
                float dx = px + 0.5f - cx;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float coverage = Mth.clamp(radius - dist, 0.0f, 1.0f);
                if (coverage <= 0.0f) {
                    continue;
                }
                float falloff = 1.0f - 0.55f * (dist / radius) * (dist / radius);
                int a = Math.round(255 * alpha * coverage * falloff);
                if (a > 0) {
                    p.pixel(px, py, pack(r, g, b, a));
                }
            }
        }
    }
}
