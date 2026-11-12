package de.test.mixin;

import de.test.MiscMods;
import de.test.PlayerMods;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ausgehende Pakete fuer Blink, Anti Hunger und Potion Saver. require = 0: fehlt die Methode, startet das Spiel trotzdem. */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), argsOnly = true, require = 0)
    private Packet<?> test$transform(Packet<?> packet) {
        return MiscMods.transform(packet);
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void test$onSend(Packet<?> packet, CallbackInfo ci) {
        if (PlayerMods.onSend(packet)) ci.cancel();
    }
}
