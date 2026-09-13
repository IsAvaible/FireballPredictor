package com.simonconrad.fireballpredictor.gametest;

import java.io.InputStream;

import com.simonconrad.fireballpredictor.client.render.HeartOverlayRenderer;
import com.simonconrad.fireballpredictor.client.render.HeartOverlayRenderer.HeartType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

public class HeartTextureTests extends GameTestBase {

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testHeartSpriteResolutionAndAssetPresence(GameTestHelper context) {
        resetGlobalState();

        for (HeartType type : HeartType.values()) {
            for (boolean hardcore : new boolean[]{false, true}) {
                for (boolean blinking : new boolean[]{false, true}) {
                    Identifier sprite = HeartOverlayRenderer.getHalfRightSprite(type, hardcore, blinking);
                    if (sprite == null) {
                        throw fail("Sprite identifier must not be null for type=" + type
                                + " hardcore=" + hardcore + " blinking=" + blinking);
                    }
                    if (!"fireballpredictor".equals(sprite.getNamespace())) {
                        throw fail("Expected namespace 'fireballpredictor', but got: " + sprite.getNamespace());
                    }

                    // Verify resource exists in gui/sprites/
                    String pathInSprites = "/assets/fireballpredictor/textures/gui/sprites/" + sprite.getPath() + ".png";
                    try (InputStream is = getClass().getResourceAsStream(pathInSprites)) {
                        if (is == null) {
                            throw fail("Missing asset resource for sprite: " + pathInSprites);
                        }
                    } catch (Exception e) {
                        throw fail("Failed to open asset: " + pathInSprites + " due to " + e.getMessage());
                    }

                    // Verify resource also exists in textures/hud/heart/ (parity)
                    String pathInHud = "/assets/fireballpredictor/textures/" + sprite.getPath() + ".png";
                    try (InputStream is = getClass().getResourceAsStream(pathInHud)) {
                        if (is == null) {
                            throw fail("Missing asset resource for sprite in hud path: " + pathInHud);
                        }
                    } catch (Exception e) {
                        throw fail("Failed to open asset: " + pathInHud + " due to " + e.getMessage());
                    }
                }
            }
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testDetermineHealthHeartType(GameTestHelper context) {
        resetGlobalState();

        Player player = context.makeMockPlayer(GameType.SURVIVAL);

        // 1. Clean player -> NORMAL
        HeartType cleanType = HeartOverlayRenderer.determineHealthHeartType(player);
        if (cleanType != HeartType.NORMAL) {
            throw fail("Clean player should have NORMAL heart type, but got " + cleanType);
        }

        // 2. Poisoned player -> POISONED
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
        HeartType poisonType = HeartOverlayRenderer.determineHealthHeartType(player);
        if (poisonType != HeartType.POISONED) {
            throw fail("Poisoned player should have POISONED heart type, but got " + poisonType);
        }

        // 3. Poison + Wither -> POISONED (vanilla checks poison first)
        player.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
        HeartType poisonWitherType = HeartOverlayRenderer.determineHealthHeartType(player);
        if (poisonWitherType != HeartType.POISONED) {
            throw fail("Player with both poison and wither should have POISONED heart type, but got " + poisonWitherType);
        }

        // 4. Only Wither -> WITHERED
        player.removeEffect(MobEffects.POISON);
        HeartType witherType = HeartOverlayRenderer.determineHealthHeartType(player);
        if (witherType != HeartType.WITHERED) {
            throw fail("Player with wither should have WITHERED heart type, but got " + witherType);
        }

        // 5. Clean effects, fully freeze -> FROZEN
        player.removeEffect(MobEffects.WITHER);
        player.setTicksFrozen(player.getTicksRequiredToFreeze() + 10);
        if (!player.isFullyFrozen()) {
            throw fail("Player should be fully frozen");
        }
        HeartType frozenType = HeartOverlayRenderer.determineHealthHeartType(player);
        if (frozenType != HeartType.FROZEN) {
            throw fail("Fully frozen player should have FROZEN heart type, but got " + frozenType);
        }

        context.succeed();
    }
}
