package li.cil.oc.server.loot;

import li.cil.oc.OpenComputers;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class LootFunctions {
    public static final ResourceLocation DYN_ITEM_DATA =
            ResourceLocation.fromNamespaceAndPath(OpenComputers.ID(), "item_data");

    public static final ResourceLocation DYN_VOLATILE_CONTENTS =
            ResourceLocation.fromNamespaceAndPath(OpenComputers.ID(), "volatile_contents");

    private static final DeferredRegister<LootItemFunctionType<?>> DR =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, OpenComputers.ID());

    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<CopyColor>> COPY_COLOR =
            DR.register("copy_color", () -> new LootItemFunctionType<>(CopyColor.CODEC));

    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<RandomLootDisk>> RANDOM_LOOT_DISK =
            DR.register("random_loot_disk", () -> new LootItemFunctionType<>(RandomLootDisk.CODEC));

    public static void init(IEventBus bus) {
        DR.register(bus);
    }

    private LootFunctions() {}
}
