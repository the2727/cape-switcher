package com.example.capeswitcher;

import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;


public final class CapeOverride {
    private CapeOverride() {}

    /** Returns a replacement skin for the local player, or null to leave it alone. */
    public static PlayerSkin apply(PlayerSkin skin) {
        if (skin == null || !CapeConfig.localOverride || !CapeManager.hasSwitched()) return null;

        String id = CapeManager.activeCapeId();
        if (id == null) {
            return new PlayerSkin(skin.body(), null, null, skin.model(), skin.secure());
        }
        Identifier tex = CapeManager.flatCapeTexture(id);
        if (tex == null) return null;

        ClientAsset.Texture capeTex = new ClientAsset.ResourceTexture(tex, tex);
        return new PlayerSkin(skin.body(), capeTex, capeTex, skin.model(), skin.secure());
    }
}