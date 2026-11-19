package de.test.mixin;

import de.test.Extras;
import de.test.Safe;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Boat Fly: nach der normalen Steuerung (und Schwerkraft) die Bewegung des eigenen Boots selbst setzen. */
@Mixin(AbstractBoat.class)
public abstract class AbstractBoatMixin {
    @Inject(method = "controlBoat", at = @At("TAIL"), require = 0)
    private void test$boatFly(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Safe.run("Boat Fly", () -> Extras.boatTick(self));
    }
}
