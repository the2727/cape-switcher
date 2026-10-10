package com.example.capeswitcher;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;

/**
 * A static (no animation) player model with a cape and elytra wings added.
 * UVs assume the combined texture: skin 64x64 on top, cape 64x32 below (64x96 total).
 */
public final class PreviewModel {
    private static PlayerModel model;
    private static boolean modelSlim;
    private static ModelPart cape, leftWing, rightWing;

    private PreviewModel() {}

    public static PlayerModel get(boolean slim, boolean showCape, boolean showElytra) {
        if (model == null || modelSlim != slim) build(slim);
        cape.visible = showCape && !showElytra;
        leftWing.visible = showCape && showElytra;
        rightWing.visible = showCape && showElytra;
        return model;
    }

    private static void build(boolean slim) {
        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, slim);
        PartDefinition root = mesh.getRoot();

        // Cape: same box as vanilla, hung just behind the body (2px back)
        root.addOrReplaceChild("cs_cape",
                CubeListBuilder.create().texOffs(0, 64).addBox(-5.0F, 0.0F, -1.0F, 10.0F, 16.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, 0.0F, (float) Math.PI, 0.0F));

        // Elytra wings, same boxes and resting angles as vanilla's elytra model
        CubeDeformation grow = new CubeDeformation(1.0F);
        root.addOrReplaceChild("cs_left_wing",
                CubeListBuilder.create().texOffs(22, 64).addBox(-10.0F, 0.0F, 0.0F, 10.0F, 20.0F, 2.0F, grow),
                PartPose.offsetAndRotation(5.0F, 0.0F, 2.0F, 0.2617994F, 0.0F, -0.2617994F));
        root.addOrReplaceChild("cs_right_wing",
                CubeListBuilder.create().texOffs(22, 64).mirror().addBox(0.0F, 0.0F, 0.0F, 10.0F, 20.0F, 2.0F, grow),
                PartPose.offsetAndRotation(-5.0F, 0.0F, 2.0F, 0.2617994F, 0.0F, 0.2617994F));

        ModelPart baked = LayerDefinition.create(mesh, 64, 96).bakeRoot();
        PlayerModel m = new PlayerModel(baked, slim);

        cape = baked.getChild("cs_cape");
        leftWing = baked.getChild("cs_left_wing");
        rightWing = baked.getChild("cs_right_wing");
        model = m;
        modelSlim = slim;
    }
}