package li.cil.oc.common

import com.google.common.base.Strings
import cpw.mods.fml.common.FMLLog
import cpw.mods.fml.common.event._
import cpw.mods.fml.common.network.NetworkRegistry
import cpw.mods.fml.common.registry.EntityRegistry
import cpw.mods.fml.common.registry.GameRegistry
import li.cil.oc._
import li.cil.oc.common.entity.Drone
import li.cil.oc.common.init.Blocks
import li.cil.oc.common.init.Items
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.recipe.Recipes
import li.cil.oc.integration.Mods
import li.cil.oc.server._
import li.cil.oc.server.machine.luac.LuaStateFactory
import li.cil.oc.server.machine.luac.NativeLua52Architecture
import li.cil.oc.server.machine.luaj.LuaJLuaArchitecture
import net.minecraft.item.ItemStack
import net.minecraftforge.oredict.OreDictionary

import scala.collection.convert.WrapAsScala._

class Proxy {
  def preInit(e: FMLPreInitializationEvent) {
    checkForBrokenJavaVersion()

    Settings.load(e.getSuggestedConfigurationFile)

    OpenComputers.log.info("Initializing blocks and items.")

    Blocks.init()
    Items.init()

    OpenComputers.log.info("Initializing additional OreDict entries.")

    registerExclusive("craftingPiston", new ItemStack(net.minecraft.init.Blocks.piston), new ItemStack(net.minecraft.init.Blocks.sticky_piston))
    registerExclusive("torchRedstoneActive", new ItemStack(net.minecraft.init.Blocks.redstone_torch))
    registerExclusive("nuggetGold", new ItemStack(net.minecraft.init.Items.gold_nugget))

    val nuggetIron = Items.get(Constants.ItemName.IronNugget).createItemStack(1)
    registerExclusive("nuggetIron", nuggetIron)

    Delegator.subItem(nuggetIron) match {
      case Some(subItem: item.IronNugget) =>
        if (OreDictionary.getOres("nuggetIron").exists(nuggetIron.isItemEqual)) {
          Recipes.addSubItem(subItem, "nuggetIron")
          Recipes.addItem(net.minecraft.init.Items.iron_ingot, "ingotIron")
        }
        else {
          subItem.showInItemList = false
        }
      case _ =>
    }

    // Avoid issues with Extra Utilities registering colored obsidian as `obsidian`
    // oredict entry, but not normal obsidian, breaking some recipes.
    OreDictionary.registerOre("obsidian", net.minecraft.init.Blocks.obsidian)

    // To still allow using normal endstone for crafting drones.
    OreDictionary.registerOre("oc:stoneEndstone", net.minecraft.init.Blocks.end_stone)

    OpenComputers.log.info("Initializing OpenComputers API.")

    api.CreativeTab.instance = CreativeTab
    api.API.driver = driver.Registry
    api.API.fileSystem = fs.FileSystem
    api.API.items = Items
    api.API.machine = machine.Machine
    api.API.network = network.Network

    api.API.config = Settings.get.config

    api.Machine.LuaArchitecture =
      if (LuaStateFactory.isAvailable && !Settings.get.forceLuaJ) classOf[NativeLua52Architecture]
      else classOf[LuaJLuaArchitecture]
    api.Machine.add(api.Machine.LuaArchitecture)
    if (Settings.get.registerLuaJArchitecture)
      api.Machine.add(classOf[LuaJLuaArchitecture])
  }

  def init(e: FMLInitializationEvent) {
    OpenComputers.channel = NetworkRegistry.INSTANCE.newEventDrivenChannel("OpenComputers")
    OpenComputers.channel.register(server.PacketHandler)

    Loot.init()
    Achievement.init()

    EntityRegistry.registerModEntity(classOf[Drone], "Drone", 0, OpenComputers, 80, 1, true)

    OpenComputers.log.info("Initializing mod integration.")
    Mods.init()

    OpenComputers.log.info("Initializing recipes.")
    Recipes.init()
  }

  def postInit(e: FMLPostInitializationEvent) {
    // Don't allow driver registration after this point, to avoid issues.
    driver.Registry.locked = true
  }

  private def registerExclusive(name: String, items: ItemStack*) {
    if (OreDictionary.getOres(name).isEmpty) {
      for (item <- items) {
        OreDictionary.registerOre(name, item)
      }
    }
  }

  // Yes, this could be boiled down even further, but I like to keep it
  // explicit like this, because it makes it a) clearer, b) easier to
  // extend, in case that should ever be needed.

  // Example usage: OpenComputers.ID + ":rack" -> "serverRack"
  private val blockRenames = Map[String, String](
  )

  // Example usage: OpenComputers.ID + ":tabletCase" -> "tabletCase1"
  private val itemRenames = Map[String, String](
    OpenComputers.ID + ":microcontrollerCase" -> Constants.ItemName.MicrocontrollerCaseTier1,
    OpenComputers.ID + ":droneCase" -> Constants.ItemName.DroneCaseTier1,
    OpenComputers.ID + ":tabletCase" -> Constants.ItemName.TabletCaseTier1
  )

  def missingMappings(e: FMLMissingMappingsEvent) {
    for (missing <- e.get()) {
      if (missing.`type` == GameRegistry.Type.BLOCK) {
        blockRenames.get(missing.name) match {
          case Some(name) =>
            if (Strings.isNullOrEmpty(name)) missing.ignore()
            else missing.remap(GameRegistry.findBlock(OpenComputers.ID, name))
          case _ => missing.warn()
        }
      }
      else if (missing.`type` == GameRegistry.Type.ITEM) {
        itemRenames.get(missing.name) match {
          case Some(name) =>
            if (Strings.isNullOrEmpty(name)) missing.ignore()
            else missing.remap(GameRegistry.findItem(OpenComputers.ID, name))
          case _ => missing.warn()
        }
      }
    }
  }

  // OK, seriously now, I've gotten one too many bug reports because of this Java version being broken.

  private final val BrokenJavaVersions = Set("1.6.0_65, Apple Inc.")

  def isBrokenJavaVersion = {
    val javaVersion = System.getProperty("java.version") + ", " + System.getProperty("java.vendor")
    BrokenJavaVersions.contains(javaVersion)
  }

  def checkForBrokenJavaVersion() = if (isBrokenJavaVersion) {
    FMLLog.bigWarning("You're using a broken Java version! Please update now, or remove OpenComputers. DO NOT REPORT THIS! UPDATE YOUR JAVA!")
    throw new Exception("You're using a broken Java version! Please update now, or remove OpenComputers. DO NOT REPORT THIS! UPDATE YOUR JAVA!")
  }
}
