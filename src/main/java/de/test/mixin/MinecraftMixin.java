package de.test.mixin;

import de.test.Safe;
import de.test.Walker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

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
}
