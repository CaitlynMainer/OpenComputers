package li.cil.oc.server.loot;

import li.cil.oc.common.Loot$;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jetbrains.annotations.NotNull;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** Replaces a floppy placeholder with one of the currently registered loot disks. */
public final class RandomLootDisk extends LootItemConditionalFunction {
    public static final MapCodec<RandomLootDisk> CODEC = RecordCodecBuilder.mapCodec(
            instance -> commonFields(instance).apply(instance, RandomLootDisk::new)
    );

    private RandomLootDisk(List<LootItemCondition> conditions) {
        super(conditions);
    }

    @Override
    public LootItemFunctionType<RandomLootDisk> getType() {
        return LootFunctions.RANDOM_LOOT_DISK.get();
    }

    public static LootItemConditionalFunction.Builder<?> randomLootDisk() {
        return simpleBuilder(RandomLootDisk::new);
    }

    @Override
    protected @NotNull ItemStack run(ItemStack stack, @NotNull LootContext context) {
        if (stack.isEmpty()) return stack;

        ItemStack disk = Loot$.MODULE$.randomDiskForLoot(context.getRandom());
        return disk.isEmpty() ? ItemStack.EMPTY : disk.copyWithCount(stack.getCount());
    }
}
