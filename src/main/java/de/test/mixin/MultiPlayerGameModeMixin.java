package de.test.mixin;

import de.test.Safe;
import de.test.Friends;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freunde: Schlaege auf Freunde abfangen, wenn "Auch eigene Schlaege abfangen" an ist. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true, require = 0)
    private void test$friendAttack(Player player, Entity target, CallbackInfo ci) {
        if (Safe.test("Freunde", () -> Friends.blockAttack(target))) ci.cancel();
    }
}
