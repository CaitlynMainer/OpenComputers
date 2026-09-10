package li.cil.oc.common.init

import codechicken.lib.gui.modular.lib.geometry.Position.Mutable
import li.cil.oc.api.detail.{ItemAPI, ItemInfo}
import li.cil.oc.api.fs.FileSystem
import li.cil.oc.common.block.SimpleBlock
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.common.item.data._
import li.cil.oc.common.item.traits.SimpleItem
import li.cil.oc.common.openprinter.OpenPrinter
import li.cil.oc.common.{ContentVisibility, Loot, Tier, item}
import li.cil.oc.integration.opencomputers.ModOpenComputers
import li.cil.oc.server.machine.luac.LuaStateFactory
import li.cil.oc.util.{Rarity => OCRarity}
import li.cil.oc.{Constants, OpenComputers, Settings, common}
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item._
import net.minecraft.world.level.block.Block
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.{DeferredItem, DeferredRegister}

import java.nio.ByteBuffer
import java.util
import java.util.concurrent.Callable
import java.util.function.Consumer
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.CollectionHasAsScala

object OCItems extends ItemAPI {
  val ITEM_TO_SECTION = new mutable.HashMap[String, String]
  val SECTION_Y_VALUES = new mutable.HashMap[String, Int]
  private val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(Settings.resourceDomain)

  val descriptors = mutable.LinkedHashMap.empty[String, ItemInfo]

  val names = mutable.Map.empty[Any, String]

  val aliases = Map(
    "datacard" -> Constants.ItemName.DataCardTier1,
    "wlancard" -> Constants.ItemName.WirelessNetworkCardTier2
  )

  override def get(name: String): ItemInfo = descriptors.get(aliases.get(name).getOrElse(name)).orNull

  override def get(stack: ItemStack): ItemInfo = names.get(getBlockOrItem(stack)) match {
    case Some(name) => get(name)
    case _ => null
  }

  def registerBlockOnly[T <: Block](instance: T, id: String): T = {
    if (!descriptors.contains(id)) {
      instance match {
        case simple: SimpleBlock =>
          simple.setUnlocalizedName("oc." + id)
        case _ =>
      }
      descriptors += id -> new ItemInfo {
        override def name: String = id

        override def block = instance

        override def item = null

        override def createItemStack(size: Int): ItemStack = {
          OpenComputers.log.warn(s"Attempt to get ItemStack for block ${instance} without item form")
          ItemStack.EMPTY
        }
      }
      names += instance -> id
    }
    instance
  }

  def registerBlock[T <: Block](instance: T, id: String, itemProps: Properties, section_id: String= null): T = {
    if (!descriptors.contains(id)) {
      instance match {
        case simple: SimpleBlock =>
          simple.setUnlocalizedName("oc." + id)

          if (section_id != null)  ITEM_TO_SECTION.put(id, section_id);
          val ro: DeferredItem[Item] = ITEMS.register(id, () => new common.block.Item(simple, itemProps))

          descriptors += id -> new ItemInfo {
            override def name: String = id

            override def block = instance

            override def item = ro.get()

            override def createItemStack(size: Int): ItemStack = instance match {
              case simple: SimpleBlock => simple.createItemStack(size)
              case _ => new ItemStack(instance, size)
            }
          }
          names += instance -> id
        case _ =>
      }
    }
    instance
  }

  def registerItem[T <: Item](makeItem: => T, id: String, section_id: String= null): DeferredItem[T] = {
    if (descriptors.contains(id)) {
      throw new IllegalArgumentException("Duplicate item " + id)
    }

    if (section_id != null)   ITEM_TO_SECTION.put(id, section_id);
    // Construct items inside the supplier while the registry is writable.
    val ro: DeferredItem[T] = ITEMS.register(id, () => {
      val instance = makeItem
      names += instance -> id
      instance
    })
    descriptors += id -> new ItemInfo {
      override def name: String = id

      override def block: Block = null

      override def item: Item = ro.get()

      override def createItemStack(size: Int): ItemStack = ro.get() match {
        case simple: SimpleItem => simple.createItemStack(size)
        case _ => new ItemStack(ro.get(), size)
      }
    }
    ro
  }

  private def registerBasicItem(id: String, section_id: String, props: Item.Properties = defaultProps): DeferredItem[Item] = registerItem(new item.BasicItem(props, id), id, section_id)

  private def registerBasicTieredItem(id: String, section_id: String, props: Item.Properties): DeferredItem[Item] = registerItem(new item.BasicTieredItem(props, id), id, section_id)

  def registerStack(stack: ItemStack, id: String, section_id: String = null): ItemStack = {
    val immutableStack = stack.copy()
    if (section_id != null)  ITEM_TO_SECTION.put(id, section_id);
    descriptors += id -> new ItemInfo {
      override def name: String = id

      override def block = null

      override def createItemStack(size: Int): ItemStack = {
        val copy = immutableStack.copy()
        copy.setCount(size)
        copy
      }

      override def item: Item = immutableStack.getItem
    }
    stack
  }

  private def getBlockOrItem(stack: ItemStack): Any =
    if (stack.isEmpty) null
    else stack.getItem match {
      case block: BlockItem => block.getBlock
      case item => item
    }

  // ----------------------------------------------------------------------- //

  override def registerFloppy(display_name:String, name: String, loc: ResourceLocation, color: DyeColor, factory: Callable[FileSystem], doRecipeCycling: Boolean): ItemStack = {
    val stack = Loot.registerLootDisk(display_name, name, loc, color, factory, doRecipeCycling)
    OCItems.registerStack(stack, name, null)
    stack.copy()
  }

  override def registerEEPROM(name: String, code: Array[Byte], data: Array[Byte], readonly: Boolean): ItemStack = {
    val stack = createEEPROM(name, code, data, readonly)
    OCItems.registerStack(stack, name, null)
    stack.copy()
  }

  def createEEPROM(name: String, code: Array[Byte], data: Array[Byte], readonly: Boolean): ItemStack = {
    val stack = get(Constants.ItemName.EEPROM).createItemStack(1)
    if (name != null) {
      stack.set(OCComponents.LABEL, name.trim.take(24))
    }
    if (code != null) {
      stack.set(OCComponents.EEPROM_CODE, ByteBuffer.wrap(code.take(Settings.get.eepromSize)))
    }
    if (data != null) {
      stack.set(OCComponents.EEPROM_DATA, ByteBuffer.wrap(data.take(Settings.get.eepromDataSize)))
    }
    stack.set(OCComponents.READONLY, readonly)

    stack
  }

  // ----------------------------------------------------------------------- //

  private def safeGetStack(name: String) = if (name == Constants.ItemName.LuaBios) Loot.defaultEEPROM.copy() else Option(get(name)).map(_.createItemStack(1)).getOrElse(ItemStack.EMPTY)

  def createConfiguredDrone(): ItemStack = {
    val data = new DroneData()

    data.name = "Crecopter"
    data.tier = Tier.Five
    data.storedEnergy = Settings.get.bufferDrone.toInt
    data.components = Array(
      safeGetStack(Constants.ItemName.InventoryUpgrade),
      safeGetStack(Constants.ItemName.InventoryUpgrade),
      safeGetStack(Constants.ItemName.InventoryControllerUpgrade),
      safeGetStack(Constants.ItemName.TankUpgrade),
      safeGetStack(Constants.ItemName.TankControllerUpgrade),
      safeGetStack(Constants.ItemName.LeashUpgrade),
      safeGetStack(Constants.ItemName.AngelUpgrade),

      safeGetStack(Constants.ItemName.WirelessNetworkCardTier2),

      LuaStateFactory.setDefaultArch(safeGetStack(Constants.ItemName.CPUTier3)),
      safeGetStack(Constants.ItemName.RAMTier6),
      safeGetStack(Constants.ItemName.RAMTier6)
    ).filter(!_.isEmpty)

    data.createItemStack()
  }

  def createConfiguredMicrocontroller(): ItemStack = {
    val data = new MicrocontrollerData()

    data.tier = Tier.Five
    data.storedEnergy = Settings.get.bufferMicrocontroller.toInt
    data.components = Array(
      safeGetStack(Constants.ItemName.SignUpgrade),
      safeGetStack(Constants.ItemName.PistonUpgrade),

      safeGetStack(Constants.ItemName.RedstoneCardTier1),
      safeGetStack(Constants.ItemName.WirelessNetworkCardTier2),

      LuaStateFactory.setDefaultArch(safeGetStack(Constants.ItemName.CPUTier3)),
      safeGetStack(Constants.ItemName.RAMTier6),
      safeGetStack(Constants.ItemName.RAMTier6)
    ).filter(!_.isEmpty)

    data.createItemStack()
  }

  def createConfiguredRobot(): ItemStack = {
    val data = new RobotData()

    data.name = Component.literal("Creatix")
    data.tier = Tier.Five
    data.robotEnergy = Settings.get.bufferRobot.toInt
    data.totalEnergy = data.robotEnergy
    data.components = Array(
      safeGetStack(Constants.BlockName.ScreenTier1),
      safeGetStack(Constants.BlockName.Keyboard),
      safeGetStack(Constants.BlockName.Geolyzer),
      safeGetStack(Constants.ItemName.InventoryUpgrade),
      safeGetStack(Constants.ItemName.InventoryUpgrade),
      safeGetStack(Constants.ItemName.InventoryUpgrade),
      safeGetStack(Constants.ItemName.InventoryUpgrade),
      safeGetStack(Constants.ItemName.InventoryControllerUpgrade),
      safeGetStack(Constants.ItemName.TankUpgrade),
      safeGetStack(Constants.ItemName.TankControllerUpgrade),
      safeGetStack(Constants.ItemName.CraftingUpgrade),
      safeGetStack(Constants.ItemName.HoverUpgradeTier2),
      safeGetStack(Constants.ItemName.AngelUpgrade),
      safeGetStack(Constants.ItemName.TradingUpgrade),
      safeGetStack(Constants.ItemName.ExperienceUpgrade),

      safeGetStack(Constants.ItemName.GraphicsCardTier3),
      safeGetStack(Constants.ItemName.RedstoneCardTier2),
      safeGetStack(Constants.ItemName.WirelessNetworkCardTier2),
      safeGetStack(Constants.ItemName.InternetCard),

      LuaStateFactory.setDefaultArch(safeGetStack(Constants.ItemName.CPUTier3)),
      safeGetStack(Constants.ItemName.RAMTier6),
      safeGetStack(Constants.ItemName.RAMTier6),

      safeGetStack(Constants.ItemName.LuaBios),
      safeGetStack(Constants.ItemName.OpenOS),
      safeGetStack(Constants.ItemName.HDDTier3)
    ).filter(!_.isEmpty)
    data.containers = Array(
      safeGetStack(Constants.ItemName.CardContainerTier3),
      safeGetStack(Constants.ItemName.UpgradeContainerTier3),
      safeGetStack(Constants.BlockName.DiskDrive)
    ).filter(!_.isEmpty)

    data.createItemStack()
  }

  def createConfiguredTablet(): ItemStack = {
    val data = new TabletData()

    data.tier = Tier.Five
    data.energy = Settings.get.bufferTablet
    data.maxEnergy = data.energy
    data.items = Array(
      safeGetStack(Constants.BlockName.ScreenTier1),
      safeGetStack(Constants.BlockName.Keyboard),

      safeGetStack(Constants.ItemName.SignUpgrade),
      safeGetStack(Constants.ItemName.PistonUpgrade),
      safeGetStack(Constants.BlockName.Geolyzer),
      safeGetStack(Constants.ItemName.NavigationUpgrade),
      safeGetStack(Constants.ItemName.Analyzer),

      safeGetStack(Constants.ItemName.GraphicsCardTier3),
      safeGetStack(Constants.ItemName.RedstoneCardTier2),
      safeGetStack(Constants.ItemName.WirelessNetworkCardTier2),

      LuaStateFactory.setDefaultArch(safeGetStack(Constants.ItemName.CPUTier3)),
      safeGetStack(Constants.ItemName.RAMTier6),
      safeGetStack(Constants.ItemName.RAMTier6),

      safeGetStack(Constants.ItemName.LuaBios),
      safeGetStack(Constants.ItemName.HDDTier3)
    ).padTo(32, ItemStack.EMPTY)
    data.items(31) = safeGetStack(Constants.ItemName.OpenOS)
    data.container = safeGetStack(Constants.BlockName.DiskDrive)

    data.createItemStack()
  }

  def createChargedHoverBoots(): ItemStack = {
    val data = new HoverBootsData()
    data.charge = Settings.get.bufferHoverBoots

    data.createItemStack()
  }

  // ----------------------------------------------------------------------- //

  private def defaultProps = new Properties()

  def init(bus: IEventBus): Unit = {
    ITEMS.register(bus)
  }

  /////////////////////////////////////////////////////////////////
  // Crafting materials.
  /////////////////////////////////////////////////////////////////
  val CuttingWire: DeferredItem[Item] = registerBasicItem(Constants.ItemName.CuttingWire, Constants.SectionName.Materials)
  val Acid: DeferredItem[item.Acid] = registerItem(new item.Acid(defaultProps), Constants.ItemName.Acid, Constants.SectionName.Materials)
  val RawCircuitBoard: DeferredItem[Item] = registerBasicItem(Constants.ItemName.RawCircuitBoard, Constants.SectionName.Materials)
  val CircuitBoard: DeferredItem[Item] = registerBasicItem(Constants.ItemName.CircuitBoard, Constants.SectionName.Materials)
  val PrintedCircuitBoard: DeferredItem[Item] = registerBasicItem(Constants.ItemName.PrintedCircuitBoard, Constants.SectionName.Materials)
  val Card: DeferredItem[Item] = registerItem(new item.BasicItem(defaultProps, "cardbase"), Constants.ItemName.Card, Constants.SectionName.Materials)
  val Transistor: DeferredItem[Item] = registerBasicItem(Constants.ItemName.Transistor, Constants.SectionName.Materials)
  val ChipTier1: DeferredItem[item.Microchip] = registerItem(new item.Microchip(defaultProps, Tier.One), Constants.ItemName.ChipTier1, Constants.SectionName.Materials)
  val ChipTier2: DeferredItem[item.Microchip] = registerItem(new item.Microchip(defaultProps.rarity(Rarity.UNCOMMON), Tier.Two), Constants.ItemName.ChipTier2, Constants.SectionName.Materials)
  val ChipTier3: DeferredItem[item.Microchip] = registerItem(new item.Microchip(defaultProps.rarity(Rarity.RARE), Tier.Three), Constants.ItemName.ChipTier3, Constants.SectionName.Materials)
  val ChipTier4: DeferredItem[item.Microchip] = registerItem(new item.Microchip(defaultProps.rarity(OCRarity.LEGENDARY), Tier.Four), Constants.ItemName.ChipTier4, Constants.SectionName.Materials)
  val Alu: DeferredItem[Item] = registerBasicItem(Constants.ItemName.Alu, Constants.SectionName.Materials)
  val ControlUnit: DeferredItem[Item] = registerItem(new item.BasicItem(defaultProps, "controlunit"), Constants.ItemName.ControlUnit, Constants.SectionName.Materials)
  val Disk: DeferredItem[Item] = registerBasicItem(Constants.ItemName.Disk, Constants.SectionName.Materials)
  val Interweb: DeferredItem[Item] = registerBasicItem(Constants.ItemName.Interweb, Constants.SectionName.Materials)
  val ButtonGroup: DeferredItem[Item] = registerBasicItem(Constants.ItemName.ButtonGroup, Constants.SectionName.Materials, defaultProps)
  val ArrowKeys: DeferredItem[Item] = registerBasicItem(Constants.ItemName.ArrowKeys, Constants.SectionName.Materials)
  val NumPad: DeferredItem[Item] = registerBasicItem(Constants.ItemName.NumPad, Constants.SectionName.Materials)

  val TabletCaseTier1: DeferredItem[item.TabletCase] = registerItem(new item.TabletCase(defaultProps, Tier.One), Constants.ItemName.TabletCaseTier1, Constants.SectionName.Materials)
  val TabletCaseTier2: DeferredItem[item.TabletCase] = registerItem(new item.TabletCase(defaultProps.rarity(Rarity.UNCOMMON), Tier.Two), Constants.ItemName.TabletCaseTier2, Constants.SectionName.Materials)
  val TabletCaseTier3: DeferredItem[item.TabletCase] = registerItem(new item.TabletCase(defaultProps.rarity(Rarity.RARE), Tier.Three), Constants.ItemName.TabletCaseTier3, Constants.SectionName.Materials)
  val TabletCaseCreative: DeferredItem[item.TabletCase] = registerItem(new item.TabletCase(defaultProps.rarity(Rarity.EPIC), Tier.Five), Constants.ItemName.TabletCaseCreative, Constants.SectionName.Materials)
  val MicrocontrollerCaseTier1: DeferredItem[item.MicrocontrollerCase] = registerItem(new item.MicrocontrollerCase(defaultProps, Tier.One), Constants.ItemName.MicrocontrollerCaseTier1, Constants.SectionName.Materials)
  val MicrocontrollerCaseTier2: DeferredItem[item.MicrocontrollerCase] = registerItem(new item.MicrocontrollerCase(defaultProps.rarity(Rarity.UNCOMMON), Tier.Two), Constants.ItemName.MicrocontrollerCaseTier2, Constants.SectionName.Materials)
  val MicrocontrollerCaseTier3: DeferredItem[item.MicrocontrollerCase] = registerItem(new item.MicrocontrollerCase(defaultProps.rarity(Rarity.RARE), Tier.Three), Constants.ItemName.MicrocontrollerCaseTier3, Constants.SectionName.Materials)
  val MicrocontrollerCaseCreative: DeferredItem[item.MicrocontrollerCase] = registerItem(new item.MicrocontrollerCase(defaultProps.rarity(Rarity.EPIC), Tier.Five), Constants.ItemName.MicrocontrollerCaseCreative, Constants.SectionName.Materials)
  val DroneCaseTier1: DeferredItem[item.DroneCase] = registerItem(new item.DroneCase(defaultProps, Tier.One), Constants.ItemName.DroneCaseTier1, Constants.SectionName.Materials)
  val DroneCaseTier2: DeferredItem[item.DroneCase] = registerItem(new item.DroneCase(defaultProps.rarity(Rarity.UNCOMMON), Tier.Two), Constants.ItemName.DroneCaseTier2, Constants.SectionName.Materials)
  val DroneCaseTier3: DeferredItem[item.DroneCase] = registerItem(new item.DroneCase(defaultProps.rarity(OCRarity.LEGENDARY), Tier.Three), Constants.ItemName.DroneCaseTier3, Constants.SectionName.Materials)
  val DroneCaseCreative: DeferredItem[item.DroneCase] = registerItem(new item.DroneCase(defaultProps.rarity(Rarity.EPIC), Tier.Five), Constants.ItemName.DroneCaseCreative, Constants.SectionName.Materials)

  val InkCartridgeEmpty: DeferredItem[Item] = registerBasicItem(Constants.ItemName.InkCartridgeEmpty, Constants.SectionName.Materials, defaultProps.stacksTo(1))
  val InkCartridge: DeferredItem[item.InkCartridge] = registerItem(new item.InkCartridge(defaultProps.stacksTo(1)), Constants.ItemName.InkCartridge, Constants.SectionName.Materials)
  val Chamelium: DeferredItem[item.Chamelium] = registerItem(new item.Chamelium(defaultProps), Constants.ItemName.Chamelium, Constants.SectionName.Materials)

  val DiamondChip: DeferredItem[Item] = registerItem(new item.BasicItem(defaultProps, "diamondchip"), Constants.ItemName.DiamondChip, Constants.SectionName.Materials)
  val NetheriteSilicon: DeferredItem[Item] = registerBasicItem(Constants.ItemName.NetheriteSilicon, Constants.SectionName.Materials, defaultProps)

  // All kinds of tools.
  val Analyzer: DeferredItem[item.Analyzer] = registerItem(new item.Analyzer(defaultProps), Constants.ItemName.Analyzer, Constants.SectionName.Tools)
  val Debugger: DeferredItem[item.Debugger] = registerItem(new item.Debugger(defaultProps), Constants.ItemName.Debugger, Constants.SectionName.Tools)
  val Terminal: DeferredItem[item.Terminal] = registerItem(new item.Terminal(defaultProps.stacksTo(1)), Constants.ItemName.Terminal, Constants.SectionName.Tools)
  val TexturePicker: DeferredItem[item.TexturePicker] = registerItem(new item.TexturePicker(defaultProps), Constants.ItemName.TexturePicker, Constants.SectionName.Tools)
  val Manual: DeferredItem[item.Manual] = registerItem(new item.Manual(defaultProps), Constants.ItemName.Manual, Constants.SectionName.Tools)
  val Wrench: DeferredItem[item.Wrench] = registerItem(new item.Wrench(defaultProps.stacksTo(1)), Constants.ItemName.Wrench, Constants.SectionName.Tools)

  // 1.5.11
  val HoverBoots: DeferredItem[item.HoverBoots] = registerItem(new item.HoverBoots(defaultProps.stacksTo(1).rarity(Rarity.UNCOMMON).setNoRepair), Constants.ItemName.HoverBoots, Constants.SectionName.Tools)

  // 1.5.18
  val Nanomachines: DeferredItem[item.Nanomachines] = registerItem(new item.Nanomachines(defaultProps.rarity(Rarity.UNCOMMON)), Constants.ItemName.Nanomachines, Constants.SectionName.Tools)

  /////////////////////////////////////////////////////////////////
  // General purpose components.
  /////////////////////////////////////////////////////////////////
  val CPUTier1: DeferredItem[item.CPU] = registerItem(new item.CPU(defaultProps, Tier.One), Constants.ItemName.CPUTier1, Constants.SectionName.Component)
  val CPUTier2: DeferredItem[item.CPU] = registerItem(new item.CPU(defaultProps.rarity(Rarity.UNCOMMON), Tier.Two), Constants.ItemName.CPUTier2, Constants.SectionName.Component)
  val CPUTier3: DeferredItem[item.CPU] = registerItem(new item.CPU(defaultProps.rarity(Rarity.RARE), Tier.Three), Constants.ItemName.CPUTier3, Constants.SectionName.Component)
  val CPUTier4: DeferredItem[item.CPU] = registerItem(new item.CPU(defaultProps.rarity(OCRarity.LEGENDARY), Tier.Four), Constants.ItemName.CPUTier4, Constants.SectionName.Component)

  val ComponentBusTier1: DeferredItem[item.ComponentBus] = registerItem(new item.ComponentBus(defaultProps, Tier.One), Constants.ItemName.ComponentBusTier1, Constants.SectionName.Component)
  val ComponentBusTier2: DeferredItem[item.ComponentBus] = registerItem(new item.ComponentBus(defaultProps.rarity(Rarity.UNCOMMON), Tier.Two), Constants.ItemName.ComponentBusTier2, Constants.SectionName.Component)
  val ComponentBusTier3: DeferredItem[item.ComponentBus] = registerItem(new item.ComponentBus(defaultProps.rarity(Rarity.RARE), Tier.Three), Constants.ItemName.ComponentBusTier3, Constants.SectionName.Component)
  val ComponentBusTier4: DeferredItem[item.ComponentBus] = registerItem(new item.ComponentBus(defaultProps.rarity(OCRarity.LEGENDARY), Tier.Four), Constants.ItemName.ComponentBusTier4, Constants.SectionName.Component)
  val ComponentBusCreative: DeferredItem[item.ComponentBus] = registerItem(new item.ComponentBus(defaultProps.rarity(Rarity.EPIC), Tier.Five), Constants.ItemName.ComponentBusCreative, Constants.SectionName.Component)

  val RAMTier1: DeferredItem[item.Memory] = registerItem(new item.Memory(defaultProps, Tier.One), Constants.ItemName.RAMTier1, Constants.SectionName.Component)
  val RAMTier2: DeferredItem[item.Memory] = registerItem(new item.Memory(defaultProps, Tier.Two), Constants.ItemName.RAMTier2, Constants.SectionName.Component)
  val RAMTier3: DeferredItem[item.Memory] = registerItem(new item.Memory(defaultProps.rarity(Rarity.UNCOMMON), Tier.Three), Constants.ItemName.RAMTier3, Constants.SectionName.Component)
  val RAMTier4: DeferredItem[item.Memory] = registerItem(new item.Memory(defaultProps.rarity(Rarity.UNCOMMON), Tier.Four), Constants.ItemName.RAMTier4, Constants.SectionName.Component)
  val RAMTier5: DeferredItem[item.Memory] = registerItem(new item.Memory(defaultProps.rarity(Rarity.RARE), Tier.Five), Constants.ItemName.RAMTier5, Constants.SectionName.Component)
  val RAMTier6: DeferredItem[item.Memory] = registerItem(new item.Memory(defaultProps.rarity(Rarity.RARE), Tier.Six), Constants.ItemName.RAMTier6, Constants.SectionName.Component)
  val RAMTier7: DeferredItem[item.Memory] = registerItem(new item.Memory(defaultProps.rarity(OCRarity.LEGENDARY), Tier.Seven), Constants.ItemName.RAMTier7, Constants.SectionName.Component)
  val RAMTier8: DeferredItem[item.Memory] = registerItem(new item.Memory(defaultProps.rarity(OCRarity.LEGENDARY), Tier.Eight), Constants.ItemName.RAMTier8, Constants.SectionName.Component)
  // 1.9
  val RAMCreative: DeferredItem[Item] = registerBasicItem(Constants.ItemName.RAMCreative, Constants.SectionName.Component, defaultProps.rarity(Rarity.EPIC))

  val ServerTier1: DeferredItem[item.Server] = registerItem(new item.Server(defaultProps.stacksTo(1), Tier.One), Constants.ItemName.ServerTier1, Constants.SectionName.Component)
  val ServerTier2: DeferredItem[item.Server] = registerItem(new item.Server(defaultProps.stacksTo(1).rarity(Rarity.UNCOMMON), Tier.Two), Constants.ItemName.ServerTier2, Constants.SectionName.Component)
  val ServerTier3: DeferredItem[item.Server] = registerItem(new item.Server(defaultProps.stacksTo(1).rarity(Rarity.RARE), Tier.Three), Constants.ItemName.ServerTier3, Constants.SectionName.Component)
  val ServerTier4: DeferredItem[item.Server] = registerItem(new item.Server(defaultProps.stacksTo(1).rarity(OCRarity.LEGENDARY), Tier.Four), Constants.ItemName.ServerTier4, Constants.SectionName.Component)
  val ServerCreative: DeferredItem[item.Server] = registerItem(new item.Server(defaultProps.stacksTo(1).rarity(Rarity.EPIC), Tier.Five), Constants.ItemName.ServerCreative, Constants.SectionName.Component)

  val APUTier1: DeferredItem[item.APU] = registerItem(new item.APU(defaultProps.rarity(Rarity.UNCOMMON), Tier.One), Constants.ItemName.APUTier1, Constants.SectionName.Component)
  val APUTier2: DeferredItem[item.APU] = registerItem(new item.APU(defaultProps.rarity(Rarity.RARE), Tier.Two), Constants.ItemName.APUTier2, Constants.SectionName.Component)
  val APUTier3: DeferredItem[item.APU] = registerItem(new item.APU(defaultProps.rarity(OCRarity.LEGENDARY), Tier.Three), Constants.ItemName.APUTier3, Constants.SectionName.Component)
  val APUCreative: DeferredItem[item.APU] = registerItem(new item.APU(defaultProps.rarity(Rarity.EPIC), Tier.Five), Constants.ItemName.APUCreative, Constants.SectionName.Component)

  // 1.6
  val TerminalServer: DeferredItem[item.TerminalServer] = registerItem(new item.TerminalServer(defaultProps.stacksTo(1)), Constants.ItemName.TerminalServer, Constants.SectionName.Component)
  val RackKVM: DeferredItem[Item] = registerBasicItem(Constants.ItemName.RackKVM, Constants.SectionName.Component, defaultProps.stacksTo(1))
  val DiskDriveMountable: DeferredItem[item.DiskDriveMountable] = registerItem(new item.DiskDriveMountable(defaultProps.stacksTo(1)), Constants.ItemName.DiskDriveMountable, Constants.SectionName.Component)

  // 1.9
  val CapacitorMountable: DeferredItem[Item] = registerBasicItem(Constants.ItemName.CapacitorMountable, Constants.SectionName.Component, defaultProps.stacksTo(1))

  // Card components.
  val DebugCard: DeferredItem[item.DebugCard] = registerItem(new item.DebugCard(defaultProps), Constants.ItemName.DebugCard, Constants.SectionName.Component)
  val GraphicsCardTier1: DeferredItem[item.GraphicsCard] = registerItem(new item.GraphicsCard(defaultProps, Tier.One), Constants.ItemName.GraphicsCardTier1, Constants.SectionName.Component)
  val GraphicsCardTier2: DeferredItem[item.GraphicsCard] = registerItem(new item.GraphicsCard(defaultProps.rarity(Rarity.UNCOMMON), Tier.Two), Constants.ItemName.GraphicsCardTier2, Constants.SectionName.Component)
  val GraphicsCardTier3: DeferredItem[item.GraphicsCard] = registerItem(new item.GraphicsCard(defaultProps.rarity(Rarity.RARE), Tier.Three), Constants.ItemName.GraphicsCardTier3, Constants.SectionName.Component)
  val GraphicsCardTier4: DeferredItem[item.GraphicsCard] = registerItem(new item.GraphicsCard(defaultProps.rarity(OCRarity.LEGENDARY), Tier.Four), Constants.ItemName.GraphicsCardTier4, Constants.SectionName.Component)
  val QuadGraphicsCard: DeferredItem[Item] = registerItem(new item.QuadGraphicsCard(defaultProps.rarity(OCRarity.LEGENDARY)), Constants.ItemName.QuadGraphicsCard, Constants.SectionName.Component)
  val RedstoneCardTier1: DeferredItem[item.RedstoneCard] = registerItem(new item.RedstoneCard(defaultProps, Tier.One), Constants.ItemName.RedstoneCardTier1, Constants.SectionName.Component)
  val RedstoneCardTier2: DeferredItem[item.RedstoneCard] = registerItem(new item.RedstoneCard(defaultProps.rarity(Rarity.UNCOMMON), Tier.Two), Constants.ItemName.RedstoneCardTier2, Constants.SectionName.Component)
  val NetworkCard: DeferredItem[Item] = registerItem(new item.BasicTieredComponentItem(defaultProps, "networkcard"), Constants.ItemName.NetworkCard, Constants.SectionName.Component)
  val WirelessNetworkCardTier2: DeferredItem[item.WirelessNetworkCard] = registerItem(new item.WirelessNetworkCard(defaultProps.rarity(Rarity.UNCOMMON), Tier.Two), Constants.ItemName.WirelessNetworkCardTier2, Constants.SectionName.Component)
  val InternetCard: DeferredItem[Item] = registerItem(new item.BasicTieredComponentItem(defaultProps.rarity(Rarity.UNCOMMON), "internetcard"), Constants.ItemName.InternetCard, Constants.SectionName.Component)
  val LinkedCard: DeferredItem[item.LinkedCard] = registerItem(new item.LinkedCard(defaultProps.rarity(Rarity.RARE)), Constants.ItemName.LinkedCard, Constants.SectionName.Component)

  // 1.5.13
  val DataCardTier1: DeferredItem[item.DataCard] = registerItem(new item.DataCard(defaultProps, Tier.One), Constants.ItemName.DataCardTier1, Constants.SectionName.Component)

  // 1.5.15
  val DataCardTier2: DeferredItem[item.DataCard] = registerItem(new item.DataCard(defaultProps.rarity(Rarity.UNCOMMON), Tier.Two), Constants.ItemName.DataCardTier2, Constants.SectionName.Component)
  val DataCardTier3: DeferredItem[item.DataCard] = registerItem(new item.DataCard(defaultProps.rarity(Rarity.RARE), Tier.Three), Constants.ItemName.DataCardTier3, Constants.SectionName.Component)

  // Physical navigation for computers, including computers inside moving
  // Sable sub-levels.
  val NavigationCard: DeferredItem[Item] = registerItem(new item.BasicTieredComponentItem(defaultProps.rarity(Rarity.UNCOMMON), "navigationcard"), Constants.ItemName.NavigationCard, Constants.SectionName.Component)

  // 1.7.2
  val WirelessNetworkCardTier1: DeferredItem[item.WirelessNetworkCard] = registerItem(new item.WirelessNetworkCard(defaultProps, Tier.One), Constants.ItemName.WirelessNetworkCardTier1, Constants.SectionName.Component)

  // 1.9
  val AudioCardTier1: DeferredItem[item.AudioCard] = registerItem(new item.AudioCard(defaultProps), Constants.ItemName.AudioCardTier1, Constants.SectionName.Component)
  /////////////////////////////////////////////////////////////////
  // Upgrade components.
  /////////////////////////////////////////////////////////////////
  val AngelUpgrade: DeferredItem[Item] = registerItem(new item.BasicTieredItem(defaultProps.rarity(Rarity.UNCOMMON), "upgradeangel"), Constants.ItemName.AngelUpgrade, Constants.SectionName.Upgrade)
  val BatteryUpgradeTier1: DeferredItem[item.UpgradeBattery] = registerItem(new item.UpgradeBattery(defaultProps, Tier.One), Constants.ItemName.BatteryUpgradeTier1, Constants.SectionName.Upgrade)
  val BatteryUpgradeTier2: DeferredItem[item.UpgradeBattery] = registerItem(new item.UpgradeBattery(defaultProps.rarity(Rarity.UNCOMMON), Tier.Two), Constants.ItemName.BatteryUpgradeTier2, Constants.SectionName.Upgrade)
  val BatteryUpgradeTier3: DeferredItem[item.UpgradeBattery] = registerItem(new item.UpgradeBattery(defaultProps.rarity(Rarity.RARE), Tier.Three), Constants.ItemName.BatteryUpgradeTier3, Constants.SectionName.Upgrade)
  val ChunkloaderUpgrade: DeferredItem[Item] = registerItem(new item.BasicTieredItem(defaultProps.rarity(Rarity.RARE), "upgradechunkloader"), Constants.ItemName.ChunkloaderUpgrade, Constants.SectionName.Upgrade)
  val CardContainerTier1: DeferredItem[item.UpgradeContainerCard] = registerItem(new item.UpgradeContainerCard(defaultProps, Tier.One), Constants.ItemName.CardContainerTier1, Constants.SectionName.Upgrade)
  val CardContainerTier2: DeferredItem[item.UpgradeContainerCard] = registerItem(new item.UpgradeContainerCard(defaultProps.rarity(Rarity.UNCOMMON), Tier.Two), Constants.ItemName.CardContainerTier2, Constants.SectionName.Upgrade)
  val CardContainerTier3: DeferredItem[item.UpgradeContainerCard] = registerItem(new item.UpgradeContainerCard(defaultProps.rarity(Rarity.RARE), Tier.Three), Constants.ItemName.CardContainerTier3, Constants.SectionName.Upgrade)
  val UpgradeContainerTier1: DeferredItem[item.UpgradeContainerUpgrade] = registerItem(new item.UpgradeContainerUpgrade(defaultProps, Tier.One), Constants.ItemName.UpgradeContainerTier1, Constants.SectionName.Upgrade)
  val UpgradeContainerTier2: DeferredItem[item.UpgradeContainerUpgrade] = registerItem(new item.UpgradeContainerUpgrade(defaultProps.rarity(Rarity.UNCOMMON), Tier.Two), Constants.ItemName.UpgradeContainerTier2, Constants.SectionName.Upgrade)
  val UpgradeContainerTier3: DeferredItem[item.UpgradeContainerUpgrade] = registerItem(new item.UpgradeContainerUpgrade(defaultProps.rarity(Rarity.RARE), Tier.Three), Constants.ItemName.UpgradeContainerTier3, Constants.SectionName.Upgrade)
  val CraftingUpgrade: DeferredItem[Item] = registerItem(new item.BasicTieredItem(defaultProps.rarity(Rarity.UNCOMMON), "upgradecrafting"), Constants.ItemName.CraftingUpgrade, Constants.SectionName.Upgrade)
  val DatabaseUpgradeTier1: DeferredItem[item.UpgradeDatabase] = registerItem(new item.UpgradeDatabase(defaultProps, Tier.One), Constants.ItemName.DatabaseUpgradeTier1, Constants.SectionName.Upgrade)
  val DatabaseUpgradeTier2: DeferredItem[item.UpgradeDatabase] = registerItem(new item.UpgradeDatabase(defaultProps.rarity(Rarity.UNCOMMON), Tier.Two), Constants.ItemName.DatabaseUpgradeTier2, Constants.SectionName.Upgrade)
  val DatabaseUpgradeTier3: DeferredItem[item.UpgradeDatabase] = registerItem(new item.UpgradeDatabase(defaultProps.rarity(Rarity.RARE), Tier.Three), Constants.ItemName.DatabaseUpgradeTier3, Constants.SectionName.Upgrade)
  val ExperienceUpgrade: DeferredItem[item.UpgradeExperience] = registerItem(new item.UpgradeExperience(defaultProps.rarity(Rarity.RARE)), Constants.ItemName.ExperienceUpgrade, Constants.SectionName.Upgrade)
  val GeneratorUpgrade: DeferredItem[item.UpgradeGenerator] = registerItem(new item.UpgradeGenerator(defaultProps.rarity(Rarity.UNCOMMON)), Constants.ItemName.GeneratorUpgrade, Constants.SectionName.Upgrade)
  val InventoryUpgrade: DeferredItem[Item] = registerItem(new item.BasicTieredItem(defaultProps, "upgradeinventory"), Constants.ItemName.InventoryUpgrade, Constants.SectionName.Upgrade)
  val InventoryControllerUpgrade: DeferredItem[Item] = registerItem(new item.BasicTieredItem(defaultProps.rarity(Rarity.UNCOMMON), "upgradeinventorycontroller"), Constants.ItemName.InventoryControllerUpgrade, Constants.SectionName.Upgrade)
  val NavigationUpgrade: DeferredItem[Item] = registerItem(new item.BasicTieredItem(defaultProps.rarity(Rarity.UNCOMMON), "upgradenavigation"), Constants.ItemName.NavigationUpgrade, Constants.SectionName.Upgrade)
  val PistonUpgrade: DeferredItem[Item] = registerItem(new item.BasicTieredItem(defaultProps.rarity(Rarity.UNCOMMON), "upgradepiston"), Constants.ItemName.PistonUpgrade, Constants.SectionName.Upgrade)
  val SignUpgrade: DeferredItem[Item] = registerItem(new item.BasicTieredItem(defaultProps.rarity(Rarity.UNCOMMON), "upgradesign"), Constants.ItemName.SignUpgrade, Constants.SectionName.Upgrade)
  val SolarGeneratorUpgrade: DeferredItem[item.UpgradeSolarGenerator] = registerItem(new item.UpgradeSolarGenerator(defaultProps.rarity(Rarity.UNCOMMON)), Constants.ItemName.SolarGeneratorUpgrade, Constants.SectionName.Upgrade)
  val TankUpgrade: DeferredItem[item.UpgradeTank] = registerItem(new item.UpgradeTank(defaultProps), Constants.ItemName.TankUpgrade, Constants.SectionName.Upgrade)
  val TankControllerUpgrade: DeferredItem[Item] = registerItem(new item.BasicTieredItem(defaultProps.rarity(Rarity.UNCOMMON), "upgradetankcontroller"), Constants.ItemName.TankControllerUpgrade, Constants.SectionName.Upgrade)
  val TractorBeamUpgrade: DeferredItem[Item] = registerItem(new item.BasicTieredItem(defaultProps.rarity(Rarity.RARE), "upgradetractorbeam"), Constants.ItemName.TractorBeamUpgrade, Constants.SectionName.Upgrade)
  val LeashUpgrade: DeferredItem[Item] = registerItem(new item.BasicTieredItem(defaultProps, "upgradeleash"), Constants.ItemName.LeashUpgrade, Constants.SectionName.Upgrade)

  // 1.5.8
  val HoverUpgradeTier1: DeferredItem[item.UpgradeHover] = registerItem(new item.UpgradeHover(defaultProps, Tier.One), Constants.ItemName.HoverUpgradeTier1, Constants.SectionName.Upgrade)
  val HoverUpgradeTier2: DeferredItem[item.UpgradeHover] = registerItem(new item.UpgradeHover(defaultProps.rarity(Rarity.UNCOMMON), Tier.Two), Constants.ItemName.HoverUpgradeTier2, Constants.SectionName.Upgrade)

  // 1.6
  val TradingUpgrade: DeferredItem[Item] = registerItem(new item.BasicTieredItem(defaultProps.rarity(Rarity.UNCOMMON), "upgradetrading"), Constants.ItemName.TradingUpgrade, Constants.SectionName.Upgrade)
  val MFU: DeferredItem[item.UpgradeMF] = registerItem(new item.UpgradeMF(defaultProps.rarity(Rarity.RARE)), Constants.ItemName.MFU, Constants.SectionName.Upgrade)

  // 1.8
  val StickyPistonUpgrade: DeferredItem[Item] = registerItem(new item.BasicTieredItem(defaultProps, "upgradestickypiston"), Constants.ItemName.StickyPistonUpgrade, Constants.SectionName.Upgrade)

  /////////////////////////////////////////////////////////////////
  // Storage media of all kinds.
  /////////////////////////////////////////////////////////////////
  val EEPROM: DeferredItem[item.EEPROM] = registerItem(new item.EEPROM(defaultProps), Constants.ItemName.EEPROM, Constants.SectionName.Component)
  val Floppy: DeferredItem[item.FloppyDisk] = registerItem(new item.FloppyDisk(defaultProps), Constants.ItemName.Floppy, Constants.SectionName.Component)
  val HDDTier1: DeferredItem[item.HardDiskDrive] = registerItem(new item.HardDiskDrive(defaultProps, Tier.One), Constants.ItemName.HDDTier1, Constants.SectionName.Component)
  val HDDTier2: DeferredItem[item.HardDiskDrive] = registerItem(new item.HardDiskDrive(defaultProps.rarity(Rarity.UNCOMMON), Tier.Two), Constants.ItemName.HDDTier2, Constants.SectionName.Component)
  val HDDTier3: DeferredItem[item.HardDiskDrive] = registerItem(new item.HardDiskDrive(defaultProps.rarity(Rarity.RARE), Tier.Three), Constants.ItemName.HDDTier3, Constants.SectionName.Component)
  val HDDTier4: DeferredItem[item.HardDiskDrive] = registerItem(new item.HardDiskDrive(defaultProps.rarity(OCRarity.LEGENDARY), Tier.Four), Constants.ItemName.HDDTier4, Constants.SectionName.Component)

  val SSDTier1: DeferredItem[item.SolidStateDrive] = registerItem(new item.SolidStateDrive(defaultProps.rarity(Rarity.UNCOMMON), Tier.Two), Constants.ItemName.SSDTier1, Constants.SectionName.Component)
  val SSDTier2: DeferredItem[item.SolidStateDrive] = registerItem(new item.SolidStateDrive(defaultProps.rarity(Rarity.RARE), Tier.Three), Constants.ItemName.SSDTier2, Constants.SectionName.Component)
  val SSDTier3: DeferredItem[item.SolidStateDrive] = registerItem(new item.SolidStateDrive(defaultProps.rarity(OCRarity.LEGENDARY), Tier.Four), Constants.ItemName.SSDTier3, Constants.SectionName.Component)


  /////////////////////////////////////////////////////////////////
  // Special purpose items that don't fit into any other category.
  /////////////////////////////////////////////////////////////////
  val Tablet: DeferredItem[item.Tablet] = registerItem(new item.Tablet(defaultProps.stacksTo(1)), Constants.ItemName.Tablet, Constants.SectionName.Misc)
  val Drone: DeferredItem[item.Drone] = registerItem(new item.Drone(defaultProps), Constants.ItemName.Drone, Constants.SectionName.Misc)
  val Present: DeferredItem[item.Present] = registerItem(new item.Present(defaultProps), Constants.ItemName.Present, Constants.SectionName.Misc)

  def decorateCreativeTab(displayItems: Consumer[ItemStack], searchItems: Consumer[ItemStack]): Unit = {
    decorateCreativeTab(displayItems, searchItems, util.Collections.emptyList(), util.Collections.emptySet())
  }

  def decorateCreativeTab(displayItems: Consumer[ItemStack], searchItems: Consumer[ItemStack],
                          additionalDisplayItems: util.Collection[ItemStack],
                          additionalSearchItems: util.Collection[ItemStack]): Unit = {
    import Constants.{BlockName => B, ItemName => I}
    // Assembled devices are not usable without their component data. Their
    // configured creative variants are added explicitly below.
    val excluded = mutable.Set(B.Microcontroller, B.Print, B.RobotAfterimage, B.Robot, I.Drone, I.Tablet)

    if (!ModOpenComputers.hasRedstoneCardT2){
      excluded += I.RedstoneCardTier2
    }

    val sectionMap = new util.HashMap[String, util.List[ItemStack]]
    val sectionStacks = mutable.ArrayBuffer.empty[ItemStack]

    def addToSection(sectionId: String, stack: ItemStack): Unit = {
      if (!stack.isEmpty && !ContentVisibility.isHidden(stack) && !sectionStacks.exists(ItemStack.isSameItemSameComponents(_, stack))) {
        sectionMap.computeIfAbsent(sectionId, (s) => new util.LinkedList).add(stack)
        sectionStacks += stack
      }
    }

    for ((id, info) <- descriptors if !excluded.contains(id)){
      val sectionId = ITEM_TO_SECTION.getOrElse(id, Constants.SectionName.Misc)
      addToSection(sectionId, info.createItemStack(1))
    }

    Loot.disksForClient.foreach(addToSection(Constants.SectionName.Misc, _))
    Loot.eepromsForClient.foreach(addToSection(Constants.SectionName.Component, _))
    additionalDisplayItems.forEach(addToSection(Constants.SectionName.Misc, _))

    for (i <- 0 until 9) {
      displayItems.accept(ItemStack.EMPTY)
    }

    SECTION_Y_VALUES.clear()
    var y = 0
    val sectionKeys = sectionMap.keySet.asScala
      .filter(_ != null)
      .toList
      .sorted
    sectionKeys.foreach(key =>{
      var itemCount = 0
      val sectionItems = sectionMap.get(key)
      sectionItems.forEach(stack => {
        if (!stack.isEmpty) {
          displayItems.accept(stack)
          searchItems.accept(stack)
          itemCount += 1
        }
      })
      SECTION_Y_VALUES.put(key, y)
      val rowCount = Math.ceil(itemCount / 9.0f).toInt
      y += rowCount + 1
      if (!key.equals(sectionKeys.last)) {
        var padding = 9 - itemCount % 9
        if (padding < 9) padding += 9
        for (i <- 0 until padding) {
          displayItems.accept(ItemStack.EMPTY)
        }
      }
    })

    additionalSearchItems.forEach(stack => if (!ContentVisibility.isHidden(stack)) searchItems.accept(stack))

    displayItems.accept(OCItems.createConfiguredDrone())
    displayItems.accept(OCItems.createConfiguredMicrocontroller())
    displayItems.accept(OCItems.createConfiguredRobot())
    displayItems.accept(OCItems.createConfiguredTablet())

  }
}
