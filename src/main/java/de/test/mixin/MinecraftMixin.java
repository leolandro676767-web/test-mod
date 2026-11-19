package de.test.mixin;

import de.test.Safe;
import de.test.Walker;
import de.test.WorldMods;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zu Koordinaten gehen: Das Spiel bricht das Abbauen ab, sobald das Fenster nicht im Vordergrund ist
 * (die Maus ist dann nicht "gefangen"). Waehrend der Walker einen Block auf dem Weg abbaut, gilt die
 * Angriffstaste deshalb als gedrueckt - so laeuft er auch weiter, wenn man nebenbei etwas anderes macht.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @ModifyVariable(method = "continueAttack", at = @At("HEAD"), argsOnly = true, require = 0)
    private boolean test$walkerDig(boolean leftClick) {
        Minecraft mc = (Minecraft) (Object) this;
        return leftClick || Safe.test("Gehen", () -> Walker.forceAttack() && mc.gui.screen() == null);
    }

    /**
     * Nuker, Auto Farm und Baum-Faeller bauen selbst ab (ohne dass man hinschaut). Ohne gedrueckte Maustaste
     * wuerde das Spiel jedes Abbauen sofort wieder abbrechen - solange eines davon abbaut, hier nichts tun.
     */
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true, require = 0)
    private void test$moduleDig(boolean down, CallbackInfo ci) {
        if (Safe.test("Abbauen", WorldMods::digging)) ci.cancel();
    }
}
