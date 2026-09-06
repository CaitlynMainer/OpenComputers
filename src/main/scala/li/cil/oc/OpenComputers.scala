package li.cil.oc

import li.cil.oc.client.ColorHandler
import li.cil.oc.common.blockentity.BlockEntityTypes
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.common.entity.EntityTypes
import li.cil.oc.common.init.{OCBlocks, OCItems}
import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.common.VillageContent
import li.cil.oc.common.openprinter.OpenPrinter
import li.cil.oc.common.recipe.Recipes
import li.cil.oc.common.{IMC, Proxy}
import li.cil.oc.integration.Mods
import li.cil.oc.server.command.CommandHandler
import li.cil.oc.server.loot.{LootConditions, LootFunctions}
import li.cil.oc.server.loot.LootTableHandler
import li.cil.oc.util.ThreadPoolFactory
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.{IEventBus, SubscribeEvent}
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.{FMLCommonSetupEvent, InterModProcessEvent}
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.fml.{InterModComms, ModContainer}
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforgespi.Environment
import org.apache.logging.log4j.{Logger, LogManager}

import java.nio.file.Paths
import scala.jdk.CollectionConverters._

object OpenComputers {
  final val ID = "opencomputers"

  final val Name = "OpenComputers"

  final val McVersion = "1.21.1-neoforge"

  @volatile var Version = "unknown"

  final val log: Logger = LogManager.getLogger(Name)

  var proxy: Proxy = _

  private var instance: Option[OpenComputers] = None

  def get = instance match {
    case Some(oc) => oc
    case _ => throw new IllegalStateException("not initialized")
  }
}

@Mod(OpenComputers.ID)
class OpenComputers(modBus: IEventBus, modContainer: ModContainer) {
  OpenComputers.Version = modContainer.getModInfo.getVersion.toString

  OpenComputers.proxy = {
    val cls = Environment.get.getDist match {
      case Dist.CLIENT => Class.forName("li.cil.oc.client.Proxy")
      case _ => Class.forName("li.cil.oc.common.ServerProxy")
    }
    cls.getConstructor(classOf[IEventBus]).newInstance(modBus).asInstanceOf[Proxy]
  }

  Settings.load(FMLPaths.CONFIGDIR.get().resolve(Paths.get("opencomputers", "settings.conf")).toFile())

  modBus.register(this)
  OCComponents.init(modBus)
  OCItems.init(modBus)
  OCBlocks.init(modBus)
  CreativeTab.CREATIVE_TABS.register(modBus)
  BlockEntityTypes.init(modBus)
  Recipes.init(modBus)
  LootConditions.init(modBus)
  LootFunctions.init(modBus)
  VillageContent.init(modBus)
  EntityTypes.ENTITY_TYPES.register(modBus)
  modBus.addListener(EntityTypes.onAttributeCreation)
  MenuTypes.MENU.register(modBus)
  OpenPrinter.init(modBus, modContainer)
  modBus.register(CreativeTab)
  OpenComputers.instance = Some(this)
  modBus.register(OpenComputers.proxy)
  OpenComputers.proxy.preInit()
  NeoForge.EVENT_BUS.register(ThreadPoolFactory)
  NeoForge.EVENT_BUS.register(LootTableHandler.INSTANCE)
  NeoForge.EVENT_BUS.register(VillageContent.INSTANCE)
  NeoForge.EVENT_BUS.addListener(CommandHandler.onRegisterCommands)
  modBus.register(ColorHandler)

  Mods.preInit() // Must happen after loading Settings but before registry events are fired.

  @SubscribeEvent
  def imc(e: InterModProcessEvent): Unit = {
    // Technically requires synchronization because IMC.sendTo doesn't check the loading stage.
    e.enqueueWork((() => {
      InterModComms.getMessages(OpenComputers.ID).sequential.iterator().asScala.foreach(IMC.handleMessage)
    }): Runnable)
  }

  @SubscribeEvent
  def onCommonSetup(e: FMLCommonSetupEvent): Unit = {
    OpenComputers.proxy.init(e)
  }
}
