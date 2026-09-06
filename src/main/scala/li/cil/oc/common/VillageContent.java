package li.cil.oc.common;

import com.google.common.collect.ImmutableSet;
import li.cil.oc.OpenComputers;
import li.cil.oc.api.Items;
import li.cil.oc.common.Loot$;
import li.cil.oc.common.init.OCBlocks;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/** The village-facing side of the OpenComputers loot story. */
public final class VillageContent {
    public static final VillageContent INSTANCE = new VillageContent();
    private static final String LEGACY_IT_NERD_NAME_KEY = "entity.opencomputers.villager.it_nerd";
    private static final ResourceKey<PoiType> IT_NERD_JOB_SITE = ResourceKey.create(
            Registries.POINT_OF_INTEREST_TYPE,
            ResourceLocation.fromNamespaceAndPath(OpenComputers.ID(), "it_nerd"));
    private static final String[] IT_NERD_NAMES = NameList.load(OpenComputers.ID(), "it_nerd");

    private static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, OpenComputers.ID());

    public static final DeferredHolder<PoiType, PoiType> IT_NERD_POI =
            POI_TYPES.register("it_nerd", () -> new PoiType(
                    ImmutableSet.copyOf(OCBlocks.CaseTier3().get().getStateDefinition().getPossibleStates()),
                    1,
                    1));

    private static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, OpenComputers.ID());

    public static final DeferredHolder<VillagerProfession, VillagerProfession> IT_NERD =
            PROFESSIONS.register("it_nerd", () -> new VillagerProfession(
                    "it_nerd",
                    (Holder<PoiType> holder) -> holder.is(IT_NERD_JOB_SITE),
                    (Holder<PoiType> holder) -> holder.is(IT_NERD_JOB_SITE),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_LIBRARIAN));

    public static void init(IEventBus bus) {
        POI_TYPES.register(bus);
        PROFESSIONS.register(bus);
    }

    @SubscribeEvent
    public void onVillagerTrades(VillagerTradesEvent event) {
        if (!IT_NERD.get().equals(event.getType())) return;

        event.getTrades().put(1, List.of(
                new VillagerTrades.EmeraldForItems(item("transistor"), 4, 1, 12),
                new VillagerTrades.EmeraldForItems(item("disk"), 2, 1, 12),
                new VillagerTrades.ItemsForEmeralds(item("printedcircuitboard"), 2, 1, 12),
                new VillagerTrades.ItemsForEmeralds(item("transistor"), 4, 1, 12)
        ));
        event.getTrades().put(2, List.of(
                new VillagerTrades.EmeraldForItems(item("circuitboard"), 3, 1, 10),
                new VillagerTrades.ItemsForEmeralds(item("cpu1"), 1, 4, 8),
                new VillagerTrades.ItemsForEmeralds(item("ram1"), 1, 5, 8)
        ));
        event.getTrades().put(3, List.of(
                new VillagerTrades.ItemsForEmeralds(item("hdd1"), 1, 8, 6),
                new VillagerTrades.ItemsForEmeralds(item("diskdrivemountable"), 1, 10, 4),
                new LootDiskListing(8, 3, 10)
        ));
        event.getTrades().put(4, List.of(
                new LootDiskListing(6, 2, 15),
                new VillagerTrades.ItemsForEmeralds(item("cpu2"), 1, 12, 4),
                new VillagerTrades.ItemsForEmeralds(item("ram2"), 1, 12, 4)
        ));
    }

    @SubscribeEvent
    public void onVillagerJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (!IT_NERD.get().equals(villager.getVillagerData().getProfession())) return;
        boolean legacyName = villager.getCustomName() != null
                && villager.getCustomName().getContents() instanceof TranslatableContents contents
                && LEGACY_IT_NERD_NAME_KEY.equals(contents.getKey());
        if ((!villager.hasCustomName() || legacyName) && IT_NERD_NAMES.length > 0) {
            villager.setCustomName(Component.literal(IT_NERD_NAMES[villager.getRandom().nextInt(IT_NERD_NAMES.length)]));
        }
    }

    private static Item item(String name) {
        return Items.get(name).item();
    }

    private static final class LootDiskListing implements VillagerTrades.ItemListing {
        private final int emeralds;
        private final int maxUses;
        private final int xp;

        private LootDiskListing(int emeralds, int maxUses, int xp) {
            this.emeralds = emeralds;
            this.maxUses = maxUses;
            this.xp = xp;
        }

        @Override
        public MerchantOffer getOffer(Entity entity, RandomSource random) {
            ItemStack disk = Loot$.MODULE$.randomDiskForLoot(random);
            if (disk.isEmpty()) return null;
            return new MerchantOffer(
                    new ItemCost(net.minecraft.world.item.Items.EMERALD, emeralds),
                    disk,
                    maxUses,
                    xp,
                    0.05f);
        }
    }

    private VillageContent() {}
}
