package de.test.mixin;

import de.test.CombatMods;
import de.test.Safe;
import de.test.Friends;
import de.test.Pickup;
import de.test.WorldMods;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Freunde: Schlaege auf Freunde abfangen, wenn "Auch eigene Schlaege abfangen" an ist.
 * Einsammeln: merken, welche Bloecke man selbst abgebaut hat (die Items daraus darf ein Bot einsammeln).
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true, require = 0)
    private void test$friendAttack(Player player, Entity target, CallbackInfo ci) {
        if (Safe.test("Freunde", () -> Friends.blockAttack(target))) {
            ci.cancel();
            return;
        }
        Safe.run("Auto Weapon", () -> CombatMods.beforeAttack(player, target));
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"), require = 0)
    private void test$destroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockState state = mc.level.getBlockState(pos);
        Block block = state.getBlock();
        Safe.run("Einsammeln", () -> Pickup.noteBreak(pos, block));
        Safe.run("Baum-F\u00e4ller", () -> WorldMods.onBreak(pos, state));
    }
}
