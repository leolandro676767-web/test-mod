package de.test.mixin;

import de.test.Extras;
import de.test.Safe;
import de.test.RenderMods;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Camera Tweaks: Abstand in der 3rd-Person-Ansicht. Zoom: Sichtfeld verkleinern.
 * require = 0: trifft die Methode nicht, bleibt es wirkungslos.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "getMaxZoom(D)D", at = @At("HEAD"), cancellable = true, require = 0)
    private void test$maxZoom(double startingDistance, CallbackInfoReturnable<Double> cir) {
        if (Safe.test("Camera Tweaks", () -> RenderMods.cameraTweaks && RenderMods.cameraNoClip)) {
            cir.setReturnValue((double) RenderMods.cameraDistance);
        }
    }

    @Inject(method = "calculateFov(F)F", at = @At("RETURN"), cancellable = true, require = 0)
    private void test$zoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
        float f;
        try {
            f = Extras.zoomFactor();
        } catch (RuntimeException | LinkageError e) {
            f = 1f;
        }
        if (f > 1.001f) cir.setReturnValue(cir.getReturnValue() / f);
    }
}
