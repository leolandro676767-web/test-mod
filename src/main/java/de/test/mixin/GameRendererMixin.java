package de.test.mixin;

import de.test.PlayerMods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Mining Trace: nach dem normalen Zielen ein Wesen durch den Block dahinter ersetzen. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "pick(F)V", at = @At("TAIL"), require = 0)
    private void test$afterPick(float partialTick, CallbackInfo ci) {
        PlayerMods.afterPick(Minecraft.getInstance());
    }
}
