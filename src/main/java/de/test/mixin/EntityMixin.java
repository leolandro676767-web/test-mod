package de.test.mixin;

import de.test.CombatMods;
import de.test.Safe;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hitboxes: Gegner beim Anvisieren etwas groesser machen (nur auf dem eigenen Bildschirm). */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getPickRadius", at = @At("RETURN"), cancellable = true, require = 0)
    private void test$hitboxes(CallbackInfoReturnable<Float> cir) {
        Entity self = (Entity) (Object) this;
        float[] extra = {0f};
        Safe.run("Hitboxes", () -> extra[0] = CombatMods.pickExtra(self));
        if (extra[0] > 0f) cir.setReturnValue(cir.getReturnValue() + extra[0]);
    }
}
