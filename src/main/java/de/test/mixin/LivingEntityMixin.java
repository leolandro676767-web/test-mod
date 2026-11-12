package de.test.mixin;

import de.test.RenderMods;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pop Chams: Ereignis 35 = Totem der Unsterblichkeit ausgeloest. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "handleEntityEvent(B)V", at = @At("HEAD"), require = 0)
    private void test$entityEvent(byte id, CallbackInfo ci) {
        if (id == 35) RenderMods.onTotemPop((LivingEntity) (Object) this);
    }
}
