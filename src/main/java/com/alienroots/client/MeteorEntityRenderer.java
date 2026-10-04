package com.alienroots.client;

import com.alienroots.MeteorEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Draws the meteor as a tumbling, glowing cluster of lumpy blocks:
 * a molten magma core wrapped in crying obsidian, amethyst and alien froglight.
 */
public class MeteorEntityRenderer extends EntityRenderer<MeteorEntity> {
    private static final class Lump {
        final BlockState state;
        final float x, y, z, size, rotY, rotX;

        Lump(Block block, float x, float y, float z, float size, float rotY, float rotX) {
            this.state = block.getDefaultState();
            this.x = x;
            this.y = y;
            this.z = z;
            this.size = size;
            this.rotY = rotY;
            this.rotX = rotX;
        }
    }

    private static final Lump[] LUMPS = {
            new Lump(Blocks.MAGMA_BLOCK, 0.00f, 0.00f, 0.00f, 1.90f, 0, 0),
            new Lump(Blocks.CRYING_OBSIDIAN, 0.95f, 0.45f, -0.35f, 1.25f, 25, 10),
            new Lump(Blocks.CRYING_OBSIDIAN, -0.85f, -0.55f, 0.45f, 1.20f, 40, -15),
            new Lump(Blocks.AMETHYST_BLOCK, 0.20f, -0.95f, 0.80f, 0.95f, 15, 30),
            new Lump(Blocks.AMETHYST_BLOCK, -0.55f, 0.95f, -0.65f, 1.00f, 50, 5),
            new Lump(Blocks.VERDANT_FROGLIGHT, 0.85f, -0.25f, 0.95f, 0.75f, 30, 20),
            new Lump(Blocks.VERDANT_FROGLIGHT, -0.90f, 0.20f, -0.85f, 0.70f, 10, 45),
            new Lump(Blocks.BLACKSTONE, 0.10f, 0.60f, 1.00f, 0.80f, 35, 0),
    };

    public MeteorEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(MeteorEntity entity) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }

    @Override
    public void render(MeteorEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        BlockRenderManager blocks = MinecraftClient.getInstance().getBlockRenderManager();
        float t = entity.spin + tickDelta;
        float s = entity.getMeteorScale();
        int fullBright = LightmapTextureManager.MAX_LIGHT_COORDINATE; // it glows, even at night

        matrices.push();
        matrices.translate(0.0, 2.0, 0.0); // entity origin is the bottom of its 4x4 box; centre the rock in it
        matrices.scale(s, s, s);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(t * 9.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(t * 5.0f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(t * 3.0f));

        for (Lump l : LUMPS) {
            matrices.push();
            matrices.translate(l.x, l.y, l.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(l.rotY));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(l.rotX));
            matrices.scale(l.size, l.size, l.size);
            matrices.translate(-0.5, -0.5, -0.5);
            blocks.renderBlockAsEntity(l.state, matrices, vertexConsumers, fullBright, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
        matrices.pop();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }
}
