package com.alienroots;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Right-click while looking at a block (up to 128 blocks away) to call down the asteroid. */
public class AlienSeedItem extends Item {
    private static final double RANGE = 128.0;
    private static final int COOLDOWN_TICKS = 100;

    public AlienSeedItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        HitResult hit = player.raycast(RANGE, 0.0f, false);

        if (hit.getType() != HitResult.Type.BLOCK) {
            if (!world.isClient) {
                player.sendMessage(Text.literal("Aim at a block within " + (int) RANGE + " blocks."), true);
            }
            return TypedActionResult.fail(stack);
        }

        if (!world.isClient) {
            BlockPos target = ((BlockHitResult) hit).getBlockPos();
            AsteroidStrike.launch((ServerWorld) world, target);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_ENDER_DRAGON_SHOOT, SoundCategory.PLAYERS, 1.0f, 0.6f);
            player.getItemCooldownManager().set(this, COOLDOWN_TICKS);
            stack.decrementUnlessCreative(1, player);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true; // enchanted-style shimmer
    }
}
