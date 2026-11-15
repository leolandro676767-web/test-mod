package de.test.mixin;

import java.util.Map;
import java.util.WeakHashMap;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import de.test.RenderMods;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item Physics: Items am Boden liegen flach. Drehen und Huepfen werden angehalten (Alter und
 * Versatz auf 0), dann wird das Item flach hingelegt und zufaellig, aber fest, gedreht.
 */
@Mixin(targets = "net.minecraft.client.renderer.entity.ItemEntityRenderer")
public abstract class ItemEntityRendererMixin {
    /** Feste Drehung je Item (aus dem urspruenglichen Versatz), damit nicht alle gleich liegen. */
    @Unique
    private static final Map<ItemEntityRenderState, Float> TEST$ANGLE = new WeakHashMap<>();
    @Unique
    private static final float TEST$LIFT = 0.35f; // so weit hebt Minecraft Items an (Huepfen + Mitte)

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void test$flat(CallbackInfo ci, @Local(argsOnly = true) ItemEntityRenderState state) {
        if (!RenderMods.itemPhysics) return;
        TEST$ANGLE.put(state, state.bobOffset);
        state.bobOffset = 0f;
        state.ageInTicks = 0f;
    }

    @Inject(method = "submit", at = @At("HEAD"), require = 0)
    private void test$start(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose,
            @Local(argsOnly = true) ItemEntityRenderState state) {
        pose.pushPose();
        if (!RenderMods.itemPhysics) return;
        Float angle = TEST$ANGLE.get(state);
        pose.translate(0f, 0.02f, 0f);
        pose.mulPose(new Quaternionf().rotationY(angle == null ? 0f : angle));
        pose.mulPose(new Quaternionf().rotationX((float) (Math.PI / 2)));
        // Minecraft hebt das Item danach nach "oben" an - nach dem Hinlegen ist das zur Seite
        pose.translate(0f, -TEST$LIFT, 0f);
    }

    @Inject(method = "submit", at = @At("RETURN"), require = 0)
    private void test$end(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose) {
        pose.popPose();
    }
}
