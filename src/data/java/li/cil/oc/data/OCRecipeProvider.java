package li.cil.oc.data;

import li.cil.oc.OpenComputers;
import li.cil.oc.common.ContentVisibility;
import li.cil.oc.common.block.ChameliumBlock;
import li.cil.oc.common.condition.OpenScreensEnabledCondition;
import li.cil.oc.common.condition.Tier4EnabledCondition;
import li.cil.oc.common.datacomponents.OCComponents;
import li.cil.oc.common.init.OCBlocks;
import li.cil.oc.common.init.OCItems;
import li.cil.oc.common.openprinter.OpenPrinter;
import net.minecraft.core.HolderLookup;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

class OCRecipeProvider extends RecipeProvider {
    private final CompletableFuture<HolderLookup.Provider> registries;

    private static final Map<DyeColor, TagKey<Item>> DYE_TAGS = Map.ofEntries(
        Map.entry(DyeColor.BLACK, Tags.Items.DYES_BLACK),
        Map.entry(DyeColor.RED, Tags.Items.DYES_RED),
        Map.entry(DyeColor.GREEN, Tags.Items.DYES_GREEN),
        Map.entry(DyeColor.BROWN, Tags.Items.DYES_BROWN),
        Map.entry(DyeColor.BLUE, Tags.Items.DYES_BLUE),
        Map.entry(DyeColor.PURPLE, Tags.Items.DYES_PURPLE),
        Map.entry(DyeColor.CYAN, Tags.Items.DYES_CYAN),
        Map.entry(DyeColor.LIGHT_GRAY, Tags.Items.DYES_LIGHT_GRAY),
        Map.entry(DyeColor.GRAY, Tags.Items.DYES_GRAY),
        Map.entry(DyeColor.PINK, Tags.Items.DYES_PINK),
        Map.entry(DyeColor.LIME, Tags.Items.DYES_LIME),
        Map.entry(DyeColor.YELLOW, Tags.Items.DYES_YELLOW),
        Map.entry(DyeColor.LIGHT_BLUE, Tags.Items.DYES_LIGHT_BLUE),
        Map.entry(DyeColor.MAGENTA, Tags.Items.DYES_MAGENTA),
        Map.entry(DyeColor.ORANGE, Tags.Items.DYES_ORANGE),
        Map.entry(DyeColor.WHITE, Tags.Items.DYES_WHITE)
    );

    OCRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
        this.registries = registries;
    }

    public void buildRecipes(RecipeOutput output) {
        final var registryLookup = registries.join();
        final var delegate = output;
        output = new RecipeOutput() {
            @Override
            public Advancement.Builder advancement() {
                return delegate.advancement();
            }

            @Override
            public void accept(final ResourceLocation id, final net.minecraft.world.item.crafting.Recipe<?> recipe,
                               @Nullable final AdvancementHolder advancement, final ICondition... conditions) {
                final var result = recipe.getResultItem(registryLookup).getItem();
                var gatedConditions = conditions;
                if (ContentVisibility.isTier4(result)) {
                    gatedConditions = append(gatedConditions, Tier4EnabledCondition.INSTANCE());
                }
                if (ContentVisibility.isOpenScreens(result)) {
                    gatedConditions = append(gatedConditions, OpenScreensEnabledCondition.INSTANCE());
                }
                delegate.accept(id, recipe, advancement, gatedConditions);
            }
        };

        addMaterials(output);
        addTools(output);
        addComponents(output);
        addCards(output);
        addUpgrades(output);
        addStorage(output);
        addBlocks(output);
        addOpenPrinter(output);

        addFloppy(output, "openos", "OpenOS (Operating System)", OCItems.Manual(), DyeColor.GREEN);
        addFloppy(output, "oppm", "OPPM (Package Manager)", OCItems.Interweb(), DyeColor.CYAN);
    }

    private static ICondition[] append(final ICondition[] conditions, final ICondition condition) {
        final var result = Arrays.copyOf(conditions, conditions.length + 1);
        result[conditions.length] = condition;
        return result;
    }

    private void addMaterials(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.CuttingWire())
            .pattern("sis")
            .define('s', Tags.Items.RODS_WOODEN)
            .define('i', Tags.Items.NUGGETS_IRON)
            .unlockedBy(getHasName(OCItems.Manual()), has(OCItems.Manual()))
            .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, OCItems.Acid())
            .requires(Items.WATER_BUCKET)
            .requires(Items.SUGAR)
            .requires(Tags.Items.SLIME_BALLS)
            .requires(Items.FERMENTED_SPIDER_EYE)
            .requires(Items.BONE)
            .unlockedBy(getHasName(OCItems.Manual()), has(OCItems.Manual()))
            .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, OCItems.RawCircuitBoard(), 8)
            .requires(Tags.Items.INGOTS_GOLD)
            .requires(Items.CLAY)
            .requires(Tags.Items.DYES_GREEN)
            .unlockedBy(getHasName(OCItems.Manual()), has(OCItems.Manual()))
            .save(output);

        RecipeProvider.oreSmelting(output, java.util.List.of(OCItems.RawCircuitBoard()), RecipeCategory.MISC, OCItems.PrintedCircuitBoard(), 0.1f, 200, "");

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.Card())
            .pattern("i ")
            .pattern("iP")
            .pattern("ig")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('g', Tags.Items.NUGGETS_GOLD)
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.Transistor(), 8)
            .pattern("iii")
            .pattern("gpg")
            .pattern(" r ")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('g', Tags.Items.NUGGETS_GOLD)
            .define('r', Tags.Items.DUSTS_REDSTONE)
            .define('p', Items.PAPER)
            .unlockedBy(getHasName(OCItems.Manual()), has(OCItems.Manual()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.ChipTier1(), 8)
            .pattern("iii")
            .pattern("rTr")
            .pattern("iii")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('r', Tags.Items.DUSTS_REDSTONE)
            .define('T', OCItems.Transistor())
            .unlockedBy(getHasName(OCItems.Transistor()), has(OCItems.Transistor()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.ChipTier2(), 4)
            .pattern("ggg")
            .pattern("rTr")
            .pattern("ggg")
            .define('g', Tags.Items.NUGGETS_GOLD)
            .define('r', Tags.Items.DUSTS_REDSTONE)
            .define('T', OCItems.Transistor())
            .unlockedBy(getHasName(OCItems.Transistor()), has(OCItems.Transistor()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.ChipTier3(), 2)
            .pattern("ddd")
            .pattern("rTr")
            .pattern("ddd")
            .define('d', OCItems.DiamondChip())
            .define('r', Tags.Items.DUSTS_REDSTONE)
            .define('T', OCItems.Transistor())
            .unlockedBy(getHasName(OCItems.Transistor()), has(OCItems.Transistor()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.ChipTier4(), 2)
            .pattern("nnn")
            .pattern("rTr")
            .pattern("nnn")
            .define('n', OCItems.NetheriteSilicon())
            .define('r', Tags.Items.DUSTS_REDSTONE)
            .define('T', OCItems.Transistor())
            .unlockedBy(getHasName(OCItems.Transistor()), has(OCItems.Transistor()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.Alu())
            .pattern("iri")
            .pattern("TCT")
            .pattern("iTi")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('r', Tags.Items.DUSTS_REDSTONE)
            .define('T', OCItems.Transistor())
            .define('C', OCItems.ChipTier1())
            .unlockedBy(getHasName(OCItems.Transistor()), has(OCItems.Transistor()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.ControlUnit())
            .pattern("grg")
            .pattern("TcT")
            .pattern("gTg")
            .define('g', Tags.Items.NUGGETS_GOLD)
            .define('r', Tags.Items.DUSTS_REDSTONE)
            .define('c', Items.CLOCK)
            .define('T', OCItems.Transistor())
            .unlockedBy(getHasName(OCItems.Card()), has(OCItems.Card()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.Disk())
            .pattern(" i ")
            .pattern("i i")
            .pattern(" i ")
            .define('i', Tags.Items.NUGGETS_IRON)
            .unlockedBy(getHasName(OCItems.Manual()), has(OCItems.Manual()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.Interweb())
            .pattern("sss")
            .pattern("ses")
            .pattern("sss")
            .define('s', Tags.Items.STRINGS)
            .define('e', Tags.Items.ENDER_PEARLS)
            .unlockedBy(getHasName(OCItems.Manual()), has(OCItems.Manual()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.ButtonGroup())
            .pattern("bbb")
            .pattern("bbb")
            .define('b', Items.STONE_BUTTON)
            .unlockedBy(getHasName(OCItems.Manual()), has(OCItems.Manual()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.ArrowKeys())
            .pattern(" b ")
            .pattern("bbb")
            .define('b', Items.STONE_BUTTON)
            .unlockedBy(getHasName(OCItems.Manual()), has(OCItems.Manual()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.NumPad())
            .pattern("bbb")
            .pattern("bbb")
            .pattern("bbb")
            .define('b', Items.STONE_BUTTON)
            .unlockedBy(getHasName(OCItems.Manual()), has(OCItems.Manual()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.TabletCaseTier1())
            .pattern("gbg")
            .pattern("BMC")
            .pattern("gPg")
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('b', Items.STONE_BUTTON)
            .define('C', OCItems.ChipTier3())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('B', OCItems.ComponentBusTier1())
            .define('M', OCBlocks.ScreenTier2())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.TabletCaseTier2())
            .pattern("cbg")
            .pattern("BMC")
            .pattern("cPg")
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('b', Items.STONE_BUTTON)
            .define('c', OCItems.ChipTier2())
            .define('C', OCItems.ChipTier3())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('B', OCItems.ComponentBusTier3())
            .define('M', OCBlocks.ScreenTier2())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.TabletCaseTier3())
            .pattern("Cbg")
            .pattern("BMC")
            .pattern("CPg")
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('b', Items.STONE_BUTTON)
            .define('C', OCItems.ChipTier3())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('B', OCItems.ComponentBusTier4())
            .define('M', OCBlocks.ScreenTier3())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);


        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.MicrocontrollerCaseTier2())
            .pattern("gCg")
            .pattern("rcr")
            .pattern("gPg")
            .define('g', Tags.Items.NUGGETS_GOLD)
            .define('r', Tags.Items.STORAGE_BLOCKS_REDSTONE)
            .define('c', Tags.Items.CHESTS)
            .define('C', OCItems.ChipTier3())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.MicrocontrollerCaseTier3())
            .pattern("dCd")
            .pattern("rcr")
            .pattern("dPd")
            .define('d', OCItems.DiamondChip())
            .define('r', Tags.Items.STORAGE_BLOCKS_REDSTONE)
            .define('c', Tags.Items.CHESTS)
            .define('C', OCItems.ChipTier4())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.DroneCaseTier1())
            .pattern("EcE")
            .pattern("CMC")
            .pattern("EBE")
            .define('c', Items.COMPASS)
            .define('E', Tags.Items.END_STONES)
            .define('C', OCItems.ChipTier1())
            .define('B', OCItems.ComponentBusTier2())
            .define('M', OCItems.MicrocontrollerCaseTier1())
            .unlockedBy(getHasName(OCItems.MicrocontrollerCaseTier1()), has(OCItems.MicrocontrollerCaseTier1()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.DroneCaseTier2())
            .pattern("EcE")
            .pattern("CMC")
            .pattern("EBE")
            .define('c', Items.COMPASS)
            .define('E', Tags.Items.END_STONES)
            .define('C', OCItems.ChipTier2())
            .define('B', OCItems.ComponentBusTier3())
            .define('M', OCItems.MicrocontrollerCaseTier2())
            .unlockedBy(getHasName(OCItems.MicrocontrollerCaseTier2()), has(OCItems.MicrocontrollerCaseTier2()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.DroneCaseTier3())
            .pattern("EcE")
            .pattern("CMC")
            .pattern("EBE")
            .define('c', Items.COMPASS)
            .define('E', Tags.Items.END_STONES)
            .define('C', OCItems.ChipTier3())
            .define('B', OCItems.ComponentBusTier4())
            .define('M', OCItems.MicrocontrollerCaseTier3())
            .unlockedBy(getHasName(OCItems.MicrocontrollerCaseTier3()), has(OCItems.MicrocontrollerCaseTier3()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.InkCartridgeEmpty())
            .pattern("idi")
            .pattern("TbT")
            .pattern("iPi")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('b', Items.BUCKET)
            .define('d', Items.DISPENSER)
            .define('T', OCItems.Transistor())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, OCItems.InkCartridge())
            .requires(OCItems.InkCartridgeEmpty())
            .requires(Tags.Items.DYES_CYAN)
            .requires(Tags.Items.DYES_MAGENTA)
            .requires(Tags.Items.DYES_YELLOW)
            .requires(Tags.Items.DYES_BLACK)
            .unlockedBy(getHasName(OCItems.InkCartridgeEmpty()), has(OCItems.InkCartridgeEmpty()))
            .unlockedBy(getHasName(OCItems.Manual()), has(OCItems.Manual()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.Chamelium(), 16)
            .pattern("grg")
            .pattern("rcr")
            .pattern("gwg")
            .define('g', Tags.Items.GRAVELS)
            .define('c', Items.COAL)
            .define('r', Tags.Items.DUSTS_REDSTONE)
            .define('w', Items.WATER_BUCKET)
            .unlockedBy(getHasName(OCItems.Manual()), has(OCItems.Manual()))
            .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, OCItems.DiamondChip(), 6)
            .requires(OCItems.CuttingWire())
            .requires(Tags.Items.GEMS_DIAMOND)
            .unlockedBy(getHasName(OCItems.CuttingWire()), has(OCItems.CuttingWire()))
            .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, OCItems.NetheriteSilicon(), 12)
            .requires(Items.NETHERITE_SCRAP)
            .requires(Items.NETHERITE_SCRAP)
            .requires(Items.NETHERITE_SCRAP)
            .requires(Items.AMETHYST_SHARD)
            .requires(Tags.Items.GEMS_QUARTZ)
            .unlockedBy(getHasName(Items.NETHERITE_SCRAP), has(Items.NETHERITE_SCRAP))
            .save(output);
    }

    private void addTools(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.Analyzer())
            .pattern("r ")
            .pattern("Tg")
            .pattern("Pg")
            .define('g', Tags.Items.NUGGETS_GOLD)
            .define('r', Items.REDSTONE_TORCH)
            .define('T', OCItems.Transistor())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.Terminal())
            .pattern("iSi")
            .pattern("CMR")
            .pattern("iKi")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('C', OCItems.ChipTier3())
            .define('M', OCBlocks.ScreenTier2())
            .define('K', OCBlocks.Keyboard())
            .define('R', OCItems.WirelessNetworkCardTier2())
            .define('S', OCItems.SolarGeneratorUpgrade())
            .unlockedBy(getHasName(OCItems.ChipTier3()), has(OCItems.ChipTier3()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.TexturePicker())
            .pattern("DRG")
            .pattern("BAP")
            .pattern("YMW")
            .define('D', Tags.Items.DYES_BLACK)
            .define('R', Tags.Items.DYES_RED)
            .define('G', Tags.Items.DYES_GREEN)
            .define('B', Tags.Items.DYES_BLUE)
            .define('P', Tags.Items.DYES_PURPLE)
            .define('Y', Tags.Items.DYES_YELLOW)
            .define('M', Tags.Items.DYES_MAGENTA)
            .define('W', Tags.Items.DYES_WHITE)
            .define('A', OCItems.Analyzer())
            .unlockedBy(getHasName(OCItems.Analyzer()), has(OCItems.Analyzer()))
            .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, OCItems.Manual())
            .requires(Items.BOOK)
            .requires(OCItems.ChipTier1())
            .unlockedBy(getHasName(Items.BOOK), has(Items.BOOK))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.Wrench())
            .pattern("i i")
            .pattern(" C ")
            .pattern(" i ")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('C', OCItems.ChipTier2())
            .unlockedBy(getHasName(OCItems.Manual()), has(OCItems.Manual()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.HoverBoots())
            .pattern("iHi")
            .pattern("LDL")
            .pattern("iCi")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('L', Tags.Items.LEATHERS)
            .define('C', OCBlocks.Capacitor())
            .define('D', OCItems.DroneCaseTier1())
            .define('H', OCItems.HoverUpgradeTier2())
            .unlockedBy(getHasName(OCItems.HoverUpgradeTier2()), has(OCItems.HoverUpgradeTier2()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.Nanomachines())
            .pattern("SRS")
            .pattern("PAM")
            .pattern("SCS")
            .define('S', OCItems.Chamelium())
            .define('A', OCItems.Acid())
            .define('C', OCBlocks.Capacitor())
            .define('R', OCItems.WirelessNetworkCardTier2())
            .define('M', OCItems.RAMTier1())
            .define('P', OCItems.CPUTier2())
            .unlockedBy(getHasName(OCItems.WirelessNetworkCardTier2()), has(OCItems.WirelessNetworkCardTier2()))
            .save(output);
    }

    private void addComponents(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.ComponentBusTier1())
            .pattern("iri")
            .pattern("CU ")
            .pattern("iPi")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('r', Tags.Items.DUSTS_REDSTONE)
            .define('C', OCItems.ChipTier1())
            .define('U', OCItems.ControlUnit())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.ComponentBusTier2())
            .pattern("grg")
            .pattern("CU ")
            .pattern("gPg")
            .define('g', Tags.Items.NUGGETS_GOLD)
            .define('r', Tags.Items.DUSTS_REDSTONE)
            .define('C', OCItems.ChipTier2())
            .define('U', OCItems.ControlUnit())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.ComponentBusTier3())
            .pattern("drd")
            .pattern("CU ")
            .pattern("dPd")
            .define('d', OCItems.DiamondChip())
            .define('r', Tags.Items.DUSTS_REDSTONE)
            .define('C', OCItems.ChipTier3())
            .define('U', OCItems.ControlUnit())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.ComponentBusTier4())
            .pattern("nrn")
            .pattern("CU ")
            .pattern("nPn")
            .define('n', OCItems.NetheriteSilicon())
            .define('r', Tags.Items.DUSTS_REDSTONE)
            .define('C', OCItems.ChipTier4())
            .define('U', OCItems.ControlUnit())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);


        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.RAMTier1())
            .pattern("cic")
            .pattern(" P ")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('c', OCItems.ChipTier1())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.RAMTier2())
            .pattern("cCc")
            .pattern(" P ")
            .define('c', OCItems.ChipTier1())
            .define('C', OCItems.ChipTier2())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.RAMTier3())
            .pattern("cic")
            .pattern(" P ")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('c', OCItems.ChipTier2())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.RAMTier4())
            .pattern("cCc")
            .pattern(" P ")
            .define('c', OCItems.ChipTier2())
            .define('C', OCItems.ChipTier3())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.RAMTier5())
            .pattern("cic")
            .pattern(" P ")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('c', OCItems.ChipTier3())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.RAMTier6())
            .pattern("CCC")
            .pattern("cPc")
            .define('c', OCItems.ChipTier2())
            .define('C', OCItems.ChipTier3())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.RAMTier7())
            .pattern("cic")
            .pattern(" P ")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('c', OCItems.ChipTier4())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.RAMTier8())
            .pattern("CCC")
            .pattern("cPc")
            .define('c', OCItems.ChipTier3())
            .define('C', OCItems.ChipTier4())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.ServerTier1())
            .pattern("iRi")
            .pattern("CBC")
            .pattern("oPo")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('o', Tags.Items.OBSIDIANS_NORMAL)
            .define('C', OCItems.ChipTier1())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('B', OCItems.ComponentBusTier1())
            .define('R', OCItems.RAMTier2())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.ServerTier2())
            .pattern("gRg")
            .pattern("CBC")
            .pattern("oPo")
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('o', Tags.Items.OBSIDIANS_NORMAL)
            .define('C', OCItems.ChipTier2())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('B', OCItems.ComponentBusTier2())
            .define('R', OCItems.RAMTier4())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.ServerTier3())
            .pattern("dRd")
            .pattern("CBC")
            .pattern("oPo")
            .define('d', Tags.Items.GEMS_DIAMOND)
            .define('o', Tags.Items.OBSIDIANS_NORMAL)
            .define('C', OCItems.ChipTier3())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('B', OCItems.ComponentBusTier3())
            .define('R', OCItems.RAMTier6())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.ServerTier4())
            .pattern("dRd")
            .pattern("CBC")
            .pattern("oPo")
            .define('d', Items.NETHERITE_SCRAP)
            .define('o', Tags.Items.OBSIDIANS_NORMAL)
            .define('C', OCItems.ChipTier4())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('B', OCItems.ComponentBusTier4())
            .define('R', OCItems.RAMTier8())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.TerminalServer())
            .pattern("oRo")
            .pattern("RCR")
            .pattern("oPo")
            .define('o', Tags.Items.OBSIDIANS_NORMAL)
            .define('C', OCItems.ChipTier2())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('R', OCItems.WirelessNetworkCardTier2())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.RackKVM())
            .pattern("SCS")
            .pattern("KTK")
            .pattern(" P ")
            .define('S', OCBlocks.ScreenTier1())
            .define('C', OCItems.ChipTier3())
            .define('K', OCBlocks.Keyboard())
            .define('T', OCItems.TerminalServer())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.DiskDriveMountable())
            .pattern("oCo")
            .pattern("fDf")
            .pattern("oPo")
            .define('o', Tags.Items.OBSIDIANS_NORMAL)
            .define('f', Items.IRON_BARS)
            .define('C', OCItems.ChipTier1())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('D', OCBlocks.DiskDrive())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);
    }

    private void addCards(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.GraphicsCardTier1())
            .pattern("CAM")
            .pattern(" B ")
            .define('C', OCItems.ChipTier1())
            .define('B', OCItems.Card())
            .define('A', OCItems.Alu())
            .define('M', OCItems.RAMTier1())
            .unlockedBy(getHasName(OCItems.ChipTier1()), has(OCItems.ChipTier1()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.GraphicsCardTier2())
            .pattern("CAM")
            .pattern(" B ")
            .define('C', OCItems.ChipTier2())
            .define('B', OCItems.Card())
            .define('A', OCItems.Alu())
            .define('M', OCItems.RAMTier3())
            .unlockedBy(getHasName(OCItems.ChipTier2()), has(OCItems.ChipTier2()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.GraphicsCardTier3())
            .pattern("CAM")
            .pattern(" B ")
            .define('C', OCItems.ChipTier3())
            .define('B', OCItems.Card())
            .define('A', OCItems.Alu())
            .define('M', OCItems.RAMTier5())
            .unlockedBy(getHasName(OCItems.ChipTier3()), has(OCItems.ChipTier3()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.GraphicsCardTier4())
            .pattern("CAM")
            .pattern(" B ")
            .define('C', OCItems.ChipTier4())
            .define('B', OCItems.Card())
            .define('A', OCItems.Alu())
            .define('M', OCItems.RAMTier7())
            .unlockedBy(getHasName(OCItems.ChipTier4()), has(OCItems.ChipTier4()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.QuadGraphicsCard())
            .pattern("GRG")
            .pattern("RNR")
            .pattern("GRG")
            .define('G', OCItems.GraphicsCardTier2())
            .define('R', OCItems.RAMTier7())
            .define('N', Items.NETHER_STAR)
            .unlockedBy(getHasName(OCItems.GraphicsCardTier2()), has(OCItems.GraphicsCardTier2()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.RedstoneCardTier1())
            .pattern("rC")
            .pattern(" B")
            .define('r', Items.REDSTONE_TORCH)
            .define('C', OCItems.ChipTier1())
            .define('B', OCItems.Card())
            .unlockedBy(getHasName(OCItems.Card()), has(OCItems.Card()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.RedstoneCardTier2())
            .pattern("rCe")
            .pattern(" B ")
            .define('e', Tags.Items.ENDER_PEARLS)
            .define('r', Tags.Items.STORAGE_BLOCKS_REDSTONE)
            .define('C', OCItems.ChipTier2())
            .define('B', OCItems.Card())
            .unlockedBy(getHasName(OCItems.Card()), has(OCItems.Card()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.NetworkCard())
            .pattern("cC")
            .pattern(" B")
            .define('C', OCItems.ChipTier1())
            .define('B', OCItems.Card())
            .define('c', OCBlocks.Cable())
            .unlockedBy(getHasName(OCItems.Card()), has(OCItems.Card()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.WirelessNetworkCardTier1())
            .pattern("rCr")
            .pattern(" B ")
            .define('r', Items.REDSTONE_TORCH)
            .define('C', OCItems.ChipTier1())
            .define('B', OCItems.Card())
            .unlockedBy(getHasName(OCItems.Card()), has(OCItems.Card()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.WirelessNetworkCardTier2())
            .pattern("eC")
            .pattern(" B")
            .define('e', Tags.Items.ENDER_PEARLS)
            .define('C', OCItems.ChipTier2())
            .define('B', OCItems.Card())
            .unlockedBy(getHasName(OCItems.Card()), has(OCItems.Card()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.InternetCard())
            .pattern("ICr")
            .pattern(" Bo")
            .define('r', Items.REDSTONE_TORCH)
            .define('o', Tags.Items.OBSIDIANS_NORMAL)
            .define('C', OCItems.ChipTier2())
            .define('I', OCItems.Interweb())
            .define('B', OCItems.Card())
            .unlockedBy(getHasName(OCItems.Card()), has(OCItems.Card()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.DataCardTier1())
            .pattern("iAC")
            .pattern(" B ")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('C', OCItems.ChipTier2())
            .define('B', OCItems.Card())
            .define('A', OCItems.Alu())
            .unlockedBy(getHasName(OCItems.Card()), has(OCItems.Card()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.NavigationCard())
            .pattern("cC")
            .pattern(" B")
            .define('c', Items.COMPASS)
            .define('C', OCItems.ChipTier2())
            .define('B', OCItems.Card())
            .unlockedBy(getHasName(OCItems.Card()), has(OCItems.Card()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.DataCardTier2())
            .pattern("gPC")
            .pattern(" B ")
            .define('g', Tags.Items.NUGGETS_GOLD)
            .define('C', OCItems.ChipTier3())
            .define('B', OCItems.Card())
            .define('P', OCItems.CPUTier1())
            .unlockedBy(getHasName(OCItems.Card()), has(OCItems.Card()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.DataCardTier3())
            .pattern("dPM")
            .pattern(" B ")
            .define('d', OCItems.DiamondChip())
            .define('B', OCItems.Card())
            .define('M', OCItems.RAMTier5())
            .define('P', OCItems.CPUTier2())
            .unlockedBy(getHasName(OCItems.Card()), has(OCItems.Card()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.AudioCardTier1())
            .pattern("CNU")
            .pattern(" B ")
            .define('C', OCItems.ChipTier2())
            .define('B', OCItems.Card())
            .define('N', Items.NOTE_BLOCK)
            .define('U', OCItems.ControlUnit())
            .unlockedBy(getHasName(OCItems.Card()), has(OCItems.Card()))
            .save(output);
    }

    private void addUpgrades(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.AngelUpgrade())
            .pattern("iei")
            .pattern("CpC")
            .pattern("iei")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('e', Tags.Items.ENDER_PEARLS)
            .define('p', Items.STICKY_PISTON)
            .define('C', OCItems.ChipTier1())
            .unlockedBy(getHasName(OCItems.ChipTier1()), has(OCItems.ChipTier1()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.BatteryUpgradeTier1())
            .pattern("igi")
            .pattern("fCf")
            .pattern("igi")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('g', Tags.Items.NUGGETS_GOLD)
            .define('f', Items.IRON_BARS)
            .define('C', OCBlocks.Capacitor())
            .unlockedBy(getHasName(OCBlocks.Capacitor()), has(OCBlocks.Capacitor()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.BatteryUpgradeTier2())
            .pattern("iCi")
            .pattern("fgf")
            .pattern("iCi")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('g', Tags.Items.NUGGETS_GOLD)
            .define('f', Items.IRON_BARS)
            .define('C', OCBlocks.Capacitor())
            .unlockedBy(getHasName(OCBlocks.Capacitor()), has(OCBlocks.Capacitor()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.BatteryUpgradeTier3())
            .pattern("iCi")
            .pattern("CDC")
            .pattern("iCi")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('D', OCItems.DiamondChip())
            .define('C', OCBlocks.Capacitor())
            .unlockedBy(getHasName(OCBlocks.Capacitor()), has(OCBlocks.Capacitor()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.ChunkloaderUpgrade())
            .pattern("gsg")
            .pattern("CeC")
            .pattern("oPo")
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('s', Tags.Items.GLASS_BLOCKS)
            .define('o', Tags.Items.OBSIDIANS_NORMAL)
            .define('e', Items.ENDER_EYE)
            .define('C', OCItems.ChipTier3())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.CardContainerTier1())
            .pattern("iCi")
            .pattern("pc ")
            .pattern("iBi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('c', Tags.Items.CHESTS)
            .define('p', Items.PISTON)
            .define('C', OCItems.ChipTier1())
            .define('B', OCItems.Card())
            .unlockedBy(getHasName(OCItems.Card()), has(OCItems.Card()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.CardContainerTier2())
            .pattern("iCi")
            .pattern("pc ")
            .pattern("iBi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('c', Tags.Items.CHESTS)
            .define('p', Items.PISTON)
            .define('C', OCItems.ChipTier2())
            .define('B', OCItems.Card())
            .unlockedBy(getHasName(OCItems.Card()), has(OCItems.Card()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.CardContainerTier3())
            .pattern("gCg")
            .pattern("pc ")
            .pattern("gBg")
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('c', Tags.Items.CHESTS)
            .define('p', Items.PISTON)
            .define('C', OCItems.ChipTier2())
            .define('B', OCItems.Card())
            .unlockedBy(getHasName(OCItems.Card()), has(OCItems.Card()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.UpgradeContainerTier1())
            .pattern("iCi")
            .pattern("pc ")
            .pattern("iBi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('c', Tags.Items.CHESTS)
            .define('p', Items.PISTON)
            .define('C', OCItems.ChipTier1())
            .define('B', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.UpgradeContainerTier2())
            .pattern("iCi")
            .pattern("pc ")
            .pattern("iBi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('c', Tags.Items.CHESTS)
            .define('p', Items.PISTON)
            .define('C', OCItems.ChipTier2())
            .define('B', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.UpgradeContainerTier3())
            .pattern("gCg")
            .pattern("pc ")
            .pattern("gBg")
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('c', Tags.Items.CHESTS)
            .define('p', Items.PISTON)
            .define('C', OCItems.ChipTier2())
            .define('B', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.CraftingUpgrade())
            .pattern("i i")
            .pattern("CwC")
            .pattern("iPi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('w', Items.CRAFTING_TABLE)
            .define('C', OCItems.ChipTier1())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.DatabaseUpgradeTier1())
            .pattern("iAi")
            .pattern("CHC")
            .pattern("iPi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('C', OCItems.ChipTier1())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('A', OCItems.Analyzer())
            .define('H', OCItems.HDDTier1())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.DatabaseUpgradeTier2())
            .pattern("iAi")
            .pattern("CHC")
            .pattern("iPi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('C', OCItems.ChipTier2())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('A', OCItems.Analyzer())
            .define('H', OCItems.HDDTier2())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.DatabaseUpgradeTier3())
            .pattern("iAi")
            .pattern("CHC")
            .pattern("iPi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('C', OCItems.ChipTier3())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('A', OCItems.Analyzer())
            .define('H', OCItems.HDDTier3())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.ExperienceUpgrade())
            .pattern("g g")
            .pattern("CeC")
            .pattern("gPg")
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('e', Tags.Items.GEMS_EMERALD)
            .define('C', OCItems.ChipTier2())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.GeneratorUpgrade())
            .pattern("i i")
            .pattern("CpC")
            .pattern("iPi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('p', Items.PISTON)
            .define('C', OCItems.ChipTier1())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.InventoryUpgrade())
            .pattern("whw")
            .pattern("dcp")
            .pattern("wCw")
            .define('w', ItemTags.PLANKS)
            .define('c', Tags.Items.CHESTS)
            .define('h', Items.HOPPER)
            .define('d', Items.DROPPER)
            .define('p', Items.PISTON)
            .define('C', OCItems.ChipTier1())
            .unlockedBy(getHasName(OCItems.ChipTier1()), has(OCItems.ChipTier1()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.InventoryControllerUpgrade())
            .pattern("gAg")
            .pattern("dCp")
            .pattern("gPg")
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('d', Items.DROPPER)
            .define('p', Items.PISTON)
            .define('C', OCItems.ChipTier2())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('A', OCItems.Analyzer())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.PistonUpgrade())
            .pattern("ipi")
            .pattern("sCs")
            .pattern("iPi")
            .define('s', Tags.Items.RODS_WOODEN)
            .define('i', Tags.Items.INGOTS_IRON)
            .define('p', Items.PISTON)
            .define('C', OCItems.ChipTier1())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.SignUpgrade())
            .pattern("ibi")
            .pattern("CsC")
            .pattern("ipi")
            .define('s', Tags.Items.RODS_WOODEN)
            .define('b', Tags.Items.DYES_BLACK)
            .define('i', Tags.Items.INGOTS_IRON)
            .define('p', Items.STICKY_PISTON)
            .define('C', OCItems.ChipTier1())
            .unlockedBy(getHasName(OCItems.ChipTier1()), has(OCItems.ChipTier1()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.SolarGeneratorUpgrade())
            .pattern("ggg")
            .pattern("ClC")
            .pattern("iPi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('g', Tags.Items.GLASS_BLOCKS)
            .define('l', Tags.Items.STORAGE_BLOCKS_LAPIS)
            .define('C', OCItems.ChipTier3())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.TankUpgrade())
            .pattern("wbw")
            .pattern("dcp")
            .pattern("wCw")
            .define('w', ItemTags.PLANKS)
            .define('c', Items.CAULDRON)
            .define('b', Items.IRON_BARS)
            .define('d', Items.DISPENSER)
            .define('p', Items.PISTON)
            .define('C', OCItems.ChipTier1())
            .unlockedBy(getHasName(OCItems.ChipTier1()), has(OCItems.ChipTier1()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.TankControllerUpgrade())
            .pattern("gbg")
            .pattern("dCp")
            .pattern("gPg")
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('b', Items.GLASS_BOTTLE)
            .define('d', Items.DISPENSER)
            .define('p', Items.PISTON)
            .define('C', OCItems.ChipTier2())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.TractorBeamUpgrade())
            .pattern("gpg")
            .pattern("iBi")
            .pattern("gCg")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('p', Items.PISTON)
            .define('C', OCItems.ChipTier3())
            .define('B', OCBlocks.Capacitor())
            .unlockedBy(getHasName(OCItems.ChipTier3()), has(OCItems.ChipTier3()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.LeashUpgrade())
            .pattern("ili")
            .pattern("lCl")
            .pattern("ili")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('l', Items.LEAD)
            .define('C', OCItems.ControlUnit())
            .unlockedBy(getHasName(OCItems.ControlUnit()), has(OCItems.ControlUnit()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.HoverUpgradeTier1())
            .pattern("fCf")
            .pattern("ili")
            .pattern("fPf")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('f', Tags.Items.FEATHERS)
            .define('l', Tags.Items.LEATHERS)
            .define('C', OCItems.ChipTier1())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.HoverUpgradeTier2())
            .pattern("eCe")
            .pattern("gig")
            .pattern("ePe")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('g', Tags.Items.NUGGETS_GOLD)
            .define('e', Tags.Items.END_STONES)
            .define('C', OCItems.ChipTier2())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.TradingUpgrade())
            .pattern("gcg")
            .pattern("eCe")
            .pattern("dPp")
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('e', Tags.Items.GEMS_EMERALD)
            .define('c', Tags.Items.CHESTS)
            .define('d', Items.DROPPER)
            .define('p', Items.PISTON)
            .define('C', OCItems.ChipTier2())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.MFU())
            .pattern("clc")
            .pattern("LAL")
            .pattern("clc")
            .define('l', Tags.Items.GEMS_LAPIS)
            .define('c', OCItems.Chamelium())
            .define('A', OCBlocks.Adapter())
            .define('L', OCItems.LinkedCard())
            .unlockedBy(getHasName(OCItems.LinkedCard()), has(OCItems.LinkedCard()))
            .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, OCItems.StickyPistonUpgrade())
            .requires(OCItems.PistonUpgrade())
            .requires(Tags.Items.SLIME_BALLS)
            .unlockedBy(getHasName(OCItems.PistonUpgrade()), has(OCItems.PistonUpgrade()))
            .save(output);
    }

    private void addStorage(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.EEPROM())
            .pattern("gTg")
            .pattern("pCp")
            .pattern("grg")
            .define('g', Tags.Items.NUGGETS_GOLD)
            .define('p', Items.PAPER)
            .define('r', Items.REDSTONE_TORCH)
            .define('T', OCItems.Transistor())
            .define('C', OCItems.ChipTier1())
            .unlockedBy(getHasName(OCItems.Manual()), has(OCItems.Manual()))
            .save(output);

        var floppy = new ItemStack(OCItems.Floppy().get());
        floppy.set(OCComponents.DISK_COLOR().get(), DyeColor.LIGHT_GRAY);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, floppy)
            .pattern("ili")
            .pattern("pDp")
            .pattern("ipi")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('p', Items.PAPER)
            .define('l', Items.LEVER)
            .define('D', OCItems.Disk())
            .unlockedBy(getHasName(OCItems.Disk()), has(OCItems.Disk()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.HDDTier1())
            .pattern("CDi")
            .pattern("PDp")
            .pattern("CDi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('p', Items.PISTON)
            .define('D', OCItems.Disk())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('C', OCItems.ChipTier1())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.HDDTier2())
            .pattern("CDg")
            .pattern("PDp")
            .pattern("CDg")
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('p', Items.PISTON)
            .define('D', OCItems.Disk())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('C', OCItems.ChipTier2())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.HDDTier3())
            .pattern("CDd")
            .pattern("PDp")
            .pattern("CDd")
            .define('d', Tags.Items.GEMS_DIAMOND)
            .define('p', Items.PISTON)
            .define('D', OCItems.Disk())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('C', OCItems.ChipTier3())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.HDDTier4())
            .pattern("CDn")
            .pattern("PDp")
            .pattern("CDn")
            .define('n', Items.NETHERITE_SCRAP)
            .define('p', Items.PISTON)
            .define('D', OCItems.Disk())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('C', OCItems.ChipTier4())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.SSDTier1())
            .pattern("CCC")
            .pattern("BPR")
            .pattern("CcC")
            .define('c', OCItems.CPUTier1())
            .define('C', OCItems.ChipTier2())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('B', OCItems.ComponentBusTier2())
            .define('R', OCItems.RAMTier3())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.SSDTier2())
            .pattern("CCC")
            .pattern("BPR")
            .pattern("CcC")
            .define('c', OCItems.CPUTier1())
            .define('C', OCItems.ChipTier3())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('B', OCItems.ComponentBusTier3())
            .define('R', OCItems.RAMTier5())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.SSDTier3())
            .pattern("CCC")
            .pattern("BPR")
            .pattern("CcC")
            .define('c', OCItems.CPUTier1())
            .define('C', OCItems.ChipTier4())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('B', OCItems.ComponentBusTier4())
            .define('R', OCItems.RAMTier7())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);
    }

    private void addOpenPrinter(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OpenPrinter.PRINTER, 1)
            .pattern("iri")
            .pattern("cpc")
            .pattern("iri")
            .define('i', Items.IRON_NUGGET)
            .define('r', Items.REDSTONE)
            .define('p', OCItems.PrintedCircuitBoard())
            .define('c', OCItems.ChipTier1())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OpenPrinter.SHREDDER, 1)
            .pattern("i i")
            .pattern("iSi")
            .pattern("i i")
            .define('i', Items.IRON_NUGGET)
            .define('S', Items.SHEARS)
            .unlockedBy(getHasName(OpenPrinter.PRINTER), has(OpenPrinter.PRINTER))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OpenPrinter.BRIEFCASE, 1)
            .pattern("SLS")
            .pattern("SiS")
            .pattern("SLS")
            .define('S', Items.STICK)
            .define('L', Items.LEATHER)
            .define('i', Items.IRON_NUGGET)
            .unlockedBy(getHasName(OpenPrinter.PRINTER), has(OpenPrinter.PRINTER))
            .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Items.PAPER, 1)
            .requires(OpenPrinter.PAPER_SHREDS)
            .requires(Items.WATER_BUCKET)
            .unlockedBy(getHasName(OpenPrinter.PAPER_SHREDS), has(OpenPrinter.PAPER_SHREDS))
            .save(output, ResourceLocation.fromNamespaceAndPath(OpenComputers.ID(), "paper_from_shreds"));

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OpenPrinter.FILE_CABINET, 1)
            .pattern("i i")
            .pattern("ici")
            .pattern("i i")
            .define('i', Items.IRON_NUGGET)
            .define('c', Items.CHEST)
            .unlockedBy(getHasName(OpenPrinter.PRINTER), has(OpenPrinter.PRINTER))
            .save(output);


        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OpenPrinter.FOLDER, 1)
            .pattern("P P")
            .pattern(" P ")
            .define('P', Items.PAPER)
            .unlockedBy(getHasName(OpenPrinter.FOLDER), has(OpenPrinter.FOLDER))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OpenPrinter.BLACK_INK, 1)
            .pattern("BBB")
            .pattern(" i ")
            .define('B', Items.BLACK_DYE)
            .define('i', Items.IRON_NUGGET)
            .unlockedBy(getHasName(OpenPrinter.PRINTER), has(OpenPrinter.PRINTER))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OpenPrinter.BLACK_INK, 1)
            .pattern("BBB")
            .pattern(" Z ")
            .define('B', Items.BLACK_DYE)
            .define('Z', OpenPrinter.BLACK_INK)
            .unlockedBy(getHasName(OpenPrinter.BLACK_INK), has(OpenPrinter.BLACK_INK))
            .save(output, ResourceLocation.fromNamespaceAndPath(OpenComputers.ID(), "printer_ink_black_refill"));

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OpenPrinter.COLOR_INK, 1)
            .pattern("RGB")
            .pattern(" i ")
            .define('R', Items.RED_DYE)
            .define('G', Items.GREEN_DYE)
            .define('B', Items.BLUE_DYE)
            .define('i', Items.IRON_NUGGET)
            .unlockedBy(getHasName(OpenPrinter.PRINTER), has(OpenPrinter.PRINTER))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OpenPrinter.COLOR_INK, 1)
            .pattern("RGB")
            .pattern(" Z ")
            .define('R', Items.RED_DYE)
            .define('G', Items.GREEN_DYE)
            .define('B', Items.BLUE_DYE)
            .define('Z', OpenPrinter.COLOR_INK)
            .unlockedBy(getHasName(OpenPrinter.COLOR_INK), has(OpenPrinter.COLOR_INK))
            .save(output, ResourceLocation.fromNamespaceAndPath(OpenComputers.ID(), "printer_ink_color_refill"));
    }

    private void addBlocks(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.Adapter())
            .pattern("ici")
            .pattern("cCc")
            .pattern("iPi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('C', OCItems.ChipTier1())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('c', OCBlocks.Cable())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.Cable(), 4)
            .pattern(" i ")
            .pattern("iri")
            .pattern(" i ")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('r', Tags.Items.DUSTS_REDSTONE)
            .unlockedBy(getHasName(OCItems.Manual()), has(OCItems.Manual()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.Capacitor())
            .pattern("iTi")
            .pattern("gpg")
            .pattern("iPi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('g', Tags.Items.NUGGETS_GOLD)
            .define('p', Items.PAPER)
            .define('T', OCItems.Transistor())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.CaseTier2())
            .pattern("gCg")
            .pattern("bcb")
            .pattern("gPg")
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('c', Tags.Items.CHESTS)
            .define('b', Items.IRON_BARS)
            .define('C', OCItems.ChipTier2())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.CaseTier3())
            .pattern("dCd")
            .pattern("bcb")
            .pattern("dPd")
            .define('d', Tags.Items.GEMS_DIAMOND)
            .define('c', Tags.Items.CHESTS)
            .define('b', Items.IRON_BARS)
            .define('C', OCItems.ChipTier3())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.CaseTier4())
            .pattern("nCn")
            .pattern("bcb")
            .pattern("nPn")
            .define('n', Items.NETHERITE_SCRAP)
            .define('c', Tags.Items.CHESTS)
            .define('b', Items.IRON_BARS)
            .define('C', OCItems.ChipTier4())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        var chamelium = new ItemStack(OCBlocks.ChameliumBlock());
        chamelium.set(OCComponents.CHAMELIUM_COLOR(), ChameliumBlock.DEFAULT_COLOR());
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, chamelium)
            .pattern("CCC")
            .pattern("CCC")
            .pattern("CCC")
            .define('C', OCItems.Chamelium())
            .unlockedBy(getHasName(OCItems.Chamelium()), has(OCItems.Chamelium()))
            .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, OCItems.Chamelium(), 9)
            .requires(OCBlocks.ChameliumBlock())
            .unlockedBy(getHasName(OCBlocks.ChameliumBlock()), has(OCBlocks.ChameliumBlock()))
            .save(output, ResourceLocation.fromNamespaceAndPath(OpenComputers.ID(), "chamelium/splitting"));

        for (var dye : DYE_TAGS.entrySet()) {
            var dyedChamelium = new ItemStack(OCBlocks.ChameliumBlock());
            dyedChamelium.set(OCComponents.CHAMELIUM_COLOR(), dye.getKey());
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, dyedChamelium)
                .requires(OCBlocks.ChameliumBlock())
                .requires(dye.getValue())
                .unlockedBy(getHasName(OCBlocks.ChameliumBlock()), has(OCBlocks.ChameliumBlock()))
                .save(output, ResourceLocation.fromNamespaceAndPath(OpenComputers.ID(), "chamelium/coloring/" + dye.getKey().getName()));
        }

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.Charger())
            .pattern("igi")
            .pattern("cCc")
            .pattern("iPi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('C', OCItems.ChipTier2())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('c', OCBlocks.Capacitor())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.Disassembler())
            .pattern("UgA")
            .pattern("p o")
            .pattern("ili")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('g', Tags.Items.GLASS_PANES)
            .define('l', Items.LAVA_BUCKET)
            .define('o', Tags.Items.OBSIDIANS_NORMAL)
            .define('p', Items.PISTON)
            .define('U', OCItems.ControlUnit())
            .define('A', OCItems.Analyzer())
            .unlockedBy(getHasName(OCItems.Manual()), has(OCItems.Manual()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.DiskDrive())
            .pattern("iCi")
            .pattern("ps ")
            .pattern("iPi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('s', Tags.Items.RODS_WOODEN)
            .define('p', Items.PISTON)
            .define('C', OCItems.ChipTier1())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.Geolyzer())
            .pattern("gcg")
            .pattern("eCe")
            .pattern("gPg")
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('c', Items.COMPASS)
            .define('e', Items.ENDER_EYE)
            .define('C', OCItems.ChipTier2())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.HologramTier1())
            .pattern("CgC")
            .pattern("PdP")
            .pattern("oGo")
            .define('d', OCItems.DiamondChip())
            .define('g', Tags.Items.GLASS_PANES)
            .define('o', Tags.Items.OBSIDIANS_NORMAL)
            .define('G', Tags.Items.DUSTS_GLOWSTONE)
            .define('C', OCItems.ChipTier2())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.HologramTier2())
            .pattern("CgC")
            .pattern("PdP")
            .pattern("obo")
            .define('d', Tags.Items.GEMS_DIAMOND)
            .define('g', Tags.Items.GLASS_BLOCKS)
            .define('o', Tags.Items.OBSIDIANS_NORMAL)
            .define('b', Items.BLAZE_POWDER)
            .define('C', OCItems.ChipTier3())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.HologramTier3())
            .pattern("CgC")
            .pattern("PnP")
            .pattern("obo")
            .define('n', Items.NETHERITE_SCRAP)
            .define('g', Tags.Items.GLASS_BLOCKS)
            .define('o', Tags.Items.OBSIDIANS_NORMAL)
            .define('b', Items.BLAZE_POWDER)
            .define('C', OCItems.ChipTier4())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.Projector())
            .pattern("GLG")
            .pattern("XTD")
            .pattern("GBG")
            .define('G', Tags.Items.INGOTS_GOLD)
            .define('L', Items.REDSTONE_LAMP)
            .define('X', OCItems.ComponentBusTier1())
            .define('T', OCItems.Transistor())
            .define('D', Tags.Items.GEMS_DIAMOND)
            .define('B', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.Keyboard())
            .pattern("BBB")
            .pattern("BAN")
            .define('B', OCItems.ButtonGroup())
            .define('A', OCItems.ArrowKeys())
            .define('N', OCItems.NumPad())
            .unlockedBy(getHasName(OCItems.ArrowKeys()), has(OCItems.ArrowKeys()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.MotionSensor())
            .pattern("gdg")
            .pattern("dCd")
            .pattern("gPg")
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('d', Items.DAYLIGHT_DETECTOR)
            .define('P', OCItems.PrintedCircuitBoard())
            .define('C', OCItems.CPUTier2())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.PowerConverter())
            .pattern("ici")
            .pattern("gCg")
            .pattern("iPi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('C', OCItems.ChipTier1())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('c', OCBlocks.Cable())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.PowerDistributor())
            .pattern("igi")
            .pattern("cCc")
            .pattern("iPi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('C', OCItems.ChipTier1())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('c', OCBlocks.Cable())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.Printer())
            .pattern("ihi")
            .pattern("pCp")
            .pattern("iPi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('h', Items.HOPPER)
            .define('p', Items.PISTON)
            .define('C', OCItems.ChipTier3())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.Raid())
            .pattern("iPi")
            .pattern("MDM")
            .pattern("iCi")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('C', OCItems.ChipTier2())
            .define('M', OCItems.RAMTier1())
            .define('P', OCItems.CPUTier3())
            .define('D', OCBlocks.DiskDrive())
            .unlockedBy(getHasName(OCBlocks.DiskDrive()), has(OCBlocks.DiskDrive()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.Redstone())
            .pattern("iCi")
            .pattern("rRr")
            .pattern("iPi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('r', Tags.Items.STORAGE_BLOCKS_REDSTONE)
            .define('C', OCItems.ChipTier3())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('R', OCItems.RedstoneCardTier1())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.Relay())
            .pattern("ici")
            .pattern("cLc")
            .pattern("iPi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('P', OCItems.PrintedCircuitBoard())
            .define('c', OCBlocks.Cable())
            .define('L', OCItems.NetworkCard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.ScreenTier1())
            .pattern("iri")
            .pattern("rCG")
            .pattern("iri")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('r', Tags.Items.DUSTS_REDSTONE)
            .define('G', Tags.Items.GLASS_BLOCKS)
            .define('C', OCItems.ChipTier1())
            .unlockedBy(getHasName(OCItems.ChipTier1()), has(OCItems.ChipTier1()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.ScreenTier2())
            .pattern("gcg")
            .pattern("pCG")
            .pattern("gyg")
            .define('c', Tags.Items.DYES_RED)
            .define('p', Tags.Items.DYES_GREEN)
            .define('y', Tags.Items.DYES_BLUE)
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('G', Tags.Items.GLASS_BLOCKS)
            .define('C', OCItems.ChipTier2())
            .unlockedBy(getHasName(OCItems.ChipTier2()), has(OCItems.ChipTier2()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.ScreenTier3())
            .pattern("ogo")
            .pattern("gCG")
            .pattern("ogo")
            .define('g', Tags.Items.DUSTS_GLOWSTONE)
            .define('G', Tags.Items.GLASS_BLOCKS)
            .define('o', Tags.Items.OBSIDIANS_NORMAL)
            .define('C', OCItems.ChipTier3())
            .unlockedBy(getHasName(OCItems.ChipTier3()), has(OCItems.ChipTier3()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.ScreenTier4())
            .pattern("ogo")
            .pattern("gCG")
            .pattern("ogo")
            .define('g', Items.GLOWSTONE)
            .define('G', Tags.Items.GLASS_BLOCKS_TINTED)
            .define('o', Tags.Items.OBSIDIANS_NORMAL)
            .define('C', OCItems.ChipTier4())
            .unlockedBy(getHasName(OCItems.ChipTier4()), has(OCItems.ChipTier4()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.FlatScreenBackTier1())
            .pattern("ggr")
            .pattern("cpr")
            .pattern("ggr")
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('r', Tags.Items.DUSTS_REDSTONE)
            .define('c', OCItems.ChipTier1())
            .define('p', Tags.Items.GLASS_PANES)
            .unlockedBy(getHasName(OCItems.ChipTier1()), has(OCItems.ChipTier1()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.FlatScreenBackTier2())
            .pattern("ddr")
            .pattern("cpg")
            .pattern("ddb")
            .define('d', OCItems.DiamondChip())
            .define('r', Tags.Items.DYES_RED)
            .define('g', Tags.Items.DYES_GREEN)
            .define('b', Tags.Items.DYES_BLUE)
            .define('c', OCItems.ChipTier2())
            .define('p', Tags.Items.GLASS_PANES)
            .unlockedBy(getHasName(OCItems.ChipTier2()), has(OCItems.ChipTier2()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.FlatScreenBackTier3())
            .pattern("oog")
            .pattern("cpg")
            .pattern("oog")
            .define('o', Tags.Items.OBSIDIANS_NORMAL)
            .define('g', Tags.Items.DUSTS_GLOWSTONE)
            .define('c', OCItems.ChipTier3())
            .define('p', Tags.Items.GLASS_PANES)
            .unlockedBy(getHasName(OCItems.ChipTier3()), has(OCItems.ChipTier3()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.FlatScreenBackTier4())
            .pattern("oog")
            .pattern("cpg")
            .pattern("oog")
            .define('o', Tags.Items.OBSIDIANS_NORMAL)
            .define('g', Items.GLOWSTONE)
            .define('c', OCItems.ChipTier4())
            .define('p', Tags.Items.GLASS_PANES)
            .unlockedBy(getHasName(OCItems.ChipTier4()), has(OCItems.ChipTier4()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.FlatScreenFrontTier1())
            .pattern("rgg")
            .pattern("rcp")
            .pattern("rgg")
            .define('g', Tags.Items.INGOTS_GOLD)
            .define('r', Tags.Items.DUSTS_REDSTONE)
            .define('c', OCItems.ChipTier1())
            .define('p', Tags.Items.GLASS_PANES)
            .unlockedBy(getHasName(OCItems.ChipTier1()), has(OCItems.ChipTier1()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.FlatScreenFrontTier2())
            .pattern("rdd")
            .pattern("gcp")
            .pattern("bdd")
            .define('d', OCItems.DiamondChip())
            .define('r', Tags.Items.DYES_RED)
            .define('g', Tags.Items.DYES_GREEN)
            .define('b', Tags.Items.DYES_BLUE)
            .define('c', OCItems.ChipTier2())
            .define('p', Tags.Items.GLASS_PANES)
            .unlockedBy(getHasName(OCItems.ChipTier2()), has(OCItems.ChipTier2()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.FlatScreenFrontTier3())
            .pattern("goo")
            .pattern("gcp")
            .pattern("goo")
            .define('o', Tags.Items.OBSIDIANS_NORMAL)
            .define('g', Tags.Items.DUSTS_GLOWSTONE)
            .define('c', OCItems.ChipTier3())
            .define('p', Tags.Items.GLASS_PANES)
            .unlockedBy(getHasName(OCItems.ChipTier3()), has(OCItems.ChipTier3()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.FlatScreenFrontTier4())
            .pattern("goo")
            .pattern("gcp")
            .pattern("goo")
            .define('o', Tags.Items.OBSIDIANS_NORMAL)
            .define('g', Items.GLOWSTONE)
            .define('c', OCItems.ChipTier4())
            .define('p', Tags.Items.GLASS_PANES)
            .unlockedBy(getHasName(OCItems.ChipTier4()), has(OCItems.ChipTier4()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.Rack())
            .pattern("dWd")
            .pattern("bcb")
            .pattern("RPD")
            .define('d', Tags.Items.GEMS_DIAMOND)
            .define('c', Tags.Items.CHESTS)
            .define('b', Items.IRON_BARS)
            .define('P', OCItems.PrintedCircuitBoard())
            .define('W', OCItems.WirelessNetworkCardTier2())
            .define('R', OCBlocks.Relay())
            .define('D', OCBlocks.PowerDistributor())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.Waypoint())
            .pattern("iCi")
            .pattern("TIT")
            .pattern("iPi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('C', OCItems.ChipTier1())
            .define('T', OCItems.Transistor())
            .define('P', OCItems.PrintedCircuitBoard())
            .define('I', OCItems.Interweb())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCItems.MicrocontrollerCaseTier1())
            .pattern("iCi")
            .pattern("rcr")
            .pattern("iPi")
            .define('i', Tags.Items.NUGGETS_IRON)
            .define('r', Tags.Items.DUSTS_REDSTONE)
            .define('c', Tags.Items.CHESTS)
            .define('C', OCItems.ChipTier1())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.Endstone(), 4)
            .pattern("eCe")
            .pattern("CeC")
            .pattern("eCe")
            .define('e', Tags.Items.ENDER_PEARLS)
            .define('C', OCBlocks.ChameliumBlock())
            .unlockedBy(getHasName(OCBlocks.ChameliumBlock()), has(OCBlocks.ChameliumBlock()))
            .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, OCBlocks.HoloScreenTier1())
            .requires(OCBlocks.HologramTier2())
            .requires(OCBlocks.ScreenTier1())
            .unlockedBy(getHasName(OCBlocks.HologramTier2()), has(OCBlocks.HologramTier2()))
            .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, OCBlocks.HoloScreenTier2())
            .requires(OCBlocks.HologramTier2())
            .requires(OCBlocks.ScreenTier2())
            .unlockedBy(getHasName(OCBlocks.HologramTier2()), has(OCBlocks.HologramTier2()))
            .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, OCBlocks.HoloScreenTier3())
            .requires(OCBlocks.HologramTier2())
            .requires(OCBlocks.ScreenTier3())
            .unlockedBy(getHasName(OCBlocks.HologramTier2()), has(OCBlocks.HologramTier2()))
            .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, OCBlocks.HoloScreenTier4())
            .requires(OCBlocks.HologramTier3())
            .requires(OCBlocks.ScreenTier4())
            .unlockedBy(getHasName(OCBlocks.HologramTier3()), has(OCBlocks.HologramTier3()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.Assembler())
            .pattern("iwi")
            .pattern("pCp")
            .pattern("iPi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('w', Items.CRAFTING_TABLE)
            .define('p', Items.PISTON)
            .define('C', OCItems.ChipTier2())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.CaseTier1())
            .pattern("iCi")
            .pattern("bcb")
            .pattern("iPi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('c', Tags.Items.CHESTS)
            .define('b', Items.IRON_BARS)
            .define('C', OCItems.ChipTier1())
            .define('P', OCItems.PrintedCircuitBoard())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.NetSplitter())
            .pattern("ici")
            .pattern("cpc")
            .pattern("iPi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('p', Items.PISTON)
            .define('P', OCItems.PrintedCircuitBoard())
            .define('c', OCBlocks.Cable())
            .unlockedBy(getHasName(OCItems.PrintedCircuitBoard()), has(OCItems.PrintedCircuitBoard()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, OCBlocks.Transposer(), 4)
            .pattern("iIi")
            .pattern("hbh")
            .pattern("iTi")
            .define('i', Tags.Items.INGOTS_IRON)
            .define('b', Items.BUCKET)
            .define('h', Items.HOPPER)
            .define('I', OCItems.InventoryControllerUpgrade())
            .define('T', OCItems.TankControllerUpgrade())
            .unlockedBy(getHasName(OCItems.InventoryControllerUpgrade()), has(OCItems.InventoryControllerUpgrade()))
            .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, OCBlocks.CarpetedCapacitor())
            .requires(OCBlocks.Capacitor())
            .requires(ItemTags.WOOL_CARPETS)
            .unlockedBy(getHasName(OCBlocks.Capacitor()), has(OCBlocks.Capacitor()))
            .save(output);
    }

    private void addFloppy(RecipeOutput output, String id, String name, ItemLike item, DyeColor color) {
        var result = new ItemStack(OCItems.Floppy().get());
        result.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        result.set(OCComponents.LOOT_DISK().get(), ResourceLocation.fromNamespaceAndPath(OpenComputers.ID(), id));
        result.set(OCComponents.DISK_COLOR().get(), color);
        result.set(OCComponents.LABEL(), id);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, result)
            .requires(OCItems.Floppy())
            .requires(item)
            .unlockedBy(getHasName(OCItems.Floppy()), has(OCItems.Floppy()))
            .save(output, ResourceLocation.fromNamespaceAndPath(OpenComputers.ID(), "lootdisks/" + id));
    }
}
