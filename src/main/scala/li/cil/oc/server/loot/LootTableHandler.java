package li.cil.oc.server.loot;

import li.cil.oc.OpenComputers;
import li.cil.oc.api.Items;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Set;

/** Adds OpenComputers content to vanilla dungeon and village chest tables. */
public final class LootTableHandler {
    public static final LootTableHandler INSTANCE = new LootTableHandler();

    private static final Set<String> DUNGEON_TABLES = Set.of(
            "chests/simple_dungeon",
            "chests/desert_pyramid",
            "chests/jungle_temple",
            "chests/stronghold_library"
    );

    private static final float DUNGEON_DISK_CHANCE = 0.25f;
    private static final float VILLAGE_DISK_CHANCE = 0.12f;

    @SubscribeEvent
    public void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation name = event.getName();
        if (!"minecraft".equals(name.getNamespace())) return;

        boolean dungeon = DUNGEON_TABLES.contains(name.getPath());
        boolean village = name.getPath().startsWith("chests/village/");
        if (!dungeon && !village) return;

        Item floppy = Items.get("floppy").item();
        if (floppy == null) {
            OpenComputers.log().warn("Could not inject loot disks: the floppy item is not registered yet.");
            return;
        }

        event.getTable().addPool(LootPool.lootPool()
                .name("opencomputers_disks")
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(dungeon ? DUNGEON_DISK_CHANCE : VILLAGE_DISK_CHANCE))
                .add(LootItem.lootTableItem(floppy)
                        .setWeight(1)
                        .apply(RandomLootDisk.randomLootDisk()))
                .build());

        event.getTable().addPool(LootPool.lootPool()
                .name("opencomputers_components")
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(dungeon ? 0.35f : 0.60f))
                .add(LootItem.lootTableItem(item("printedcircuitboard")).setWeight(dungeon ? 2 : 4))
                .add(LootItem.lootTableItem(item("transistor")).setWeight(dungeon ? 2 : 5))
                .add(LootItem.lootTableItem(item("disk")).setWeight(dungeon ? 2 : 4))
                .add(LootItem.lootTableItem(item("cpu1")).setWeight(dungeon ? 2 : 1))
                .add(LootItem.lootTableItem(item("ram1")).setWeight(dungeon ? 2 : 1))
                .add(LootItem.lootTableItem(item("hdd1")).setWeight(dungeon ? 1 : 1))
                .build());
    }

    private static Item item(String name) {
        return Items.get(name).item();
    }

    private LootTableHandler() {}
}
