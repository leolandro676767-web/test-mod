package de.test.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import de.test.RenderMods;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hand View: Hand in der Ego-Sicht verschieben und skalieren. */
@Mixin(targets = "net.minecraft.client.renderer.ItemInHandRenderer")
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), require = 0)
    private void test$handStart(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose,
            @Local(argsOnly = true) InteractionHand hand) {
        pose.pushPose();
        if (!RenderMods.handView) return;
        float side = hand == InteractionHand.OFF_HAND ? -1f : 1f;
        pose.translate(side * RenderMods.handX / 100f, RenderMods.handY / 100f, RenderMods.handZ / 100f);
        float s = Math.max(0.1f, RenderMods.handScale / 100f);
        pose.scale(s, s, s);
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"), require = 0)
    private void test$handEnd(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose) {
        pose.popPose();
    }
}
