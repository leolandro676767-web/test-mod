package de.test.mixin;

import de.test.CombatMods;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Aim Assist und Bow Aimbot: in jedem Bild nach der Mausbewegung ein Stueck weiterdrehen. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
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
