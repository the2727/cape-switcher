package com.example.capeswitcher.mixin;

import com.example.capeswitcher.CapeOverride;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void capeswitcher$overrideCape(CallbackInfoReturnable<PlayerSkin> cir) {
        if (!((Object) this instanceof LocalPlayer)) return;
        PlayerSkin replaced = CapeOverride.apply(cir.getReturnValue());
        if (replaced != null) cir.setReturnValue(replaced);
    }
}