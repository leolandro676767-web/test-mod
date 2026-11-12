package de.test.mixin;

import de.test.PlayerMods;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Portals: das Spiel schliesst Menues im Netherportal nicht mehr. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "handlePortalTransitionEffect", at = @At("HEAD"), cancellable = true, require = 0)
    private void test$portal(boolean active, CallbackInfo ci) {
        if (PlayerMods.portals) ci.cancel();
    }
}
