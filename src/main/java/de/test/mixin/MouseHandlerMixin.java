package de.test.mixin;

import de.test.CombatMods;
import de.test.Extras;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zoom: Mausrad aendert die Staerke (statt den Hotbar-Platz), Maus beim Zoomen langsamer. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow
    private double accumulatedDX;
    @Shadow
    private double accumulatedDY;

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void test$zoomScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        try {
            if (Extras.onScroll(yoffset)) ci.cancel();
        } catch (RuntimeException | LinkageError ignored) {
            // im Zweifel normal scrollen
        }
    }

    @Inject(method = "turnPlayer(D)V", at = @At("HEAD"), require = 0)
    private void test$zoomMouse(double mousea, CallbackInfo ci) {
        double f;
        try {
            f = Extras.mouseScale();
        } catch (RuntimeException | LinkageError e) {
            f = 1.0;
        }
        if (f != 1.0) {
            accumulatedDX *= f;
            accumulatedDY *= f;
        }
    }

    /** Aim Assist und Bow Aimbot: in jedem Bild ein Stueck weiterdrehen (wie eine Mausbewegung). */
    @Inject(method = "turnPlayer(D)V", at = @At("TAIL"), require = 0)
    private void test$aim(double mousea, CallbackInfo ci) {
        try {
            CombatMods.aimFrame();
        } catch (RuntimeException | LinkageError ignored) {
            // dann eben nicht
        }
    }
}
