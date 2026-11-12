package de.test.mixin;

import de.test.RenderMods;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Camera Tweaks: Abstand in der 3rd-Person-Ansicht. require = 0: trifft die Methode nicht, bleibt es wirkungslos. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "getMaxZoom(D)D", at = @At("HEAD"), cancellable = true, require = 0)
    private void test$maxZoom(double startingDistance, CallbackInfoReturnable<Double> cir) {
        if (RenderMods.cameraTweaks && RenderMods.cameraNoClip) {
            cir.setReturnValue((double) RenderMods.cameraDistance);
        }
    }
}
