package com.alienroots;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public final class ModItems {
    public static final Item ALIEN_SEED = Registry.register(
            Registries.ITEM,
            Identifier.of(AlienRoots.MOD_ID, "alien_seed"),
            new AlienSeedItem(new Item.Settings().maxCount(16).rarity(Rarity.EPIC)));

    public static final RegistryKey<ItemGroup> GROUP_KEY =
            RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(AlienRoots.MOD_ID, "main"));

    public static void register() {
        // Own creative tab: "Alien Roots"
        Registry.register(Registries.ITEM_GROUP, GROUP_KEY, FabricItemGroup.builder()
                .icon(() -> new ItemStack(ALIEN_SEED))
                .displayName(Text.translatable("itemGroup.alienroots.main"))
                .entries((context, entries) -> entries.add(ALIEN_SEED))
                .build());

        // ...and also in the vanilla Tools & Utilities tab
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(ALIEN_SEED));
    }

    private ModItems() {}
}
