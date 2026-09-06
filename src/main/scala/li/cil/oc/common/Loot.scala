package li.cil.oc.common

import com.google.gson.{GsonBuilder, JsonElement}
import com.mojang.serialization.JsonOps
import li.cil.oc.{api, Constants, OpenComputers, Settings}
import li.cil.oc.api.fs.FileSystem
import li.cil.oc.common.data.{EEPROM, LootDisk}
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.common.init.OCItems
import li.cil.oc.server.fs.{FileSystem => ServerFileSystem}
import li.cil.oc.util.Color
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.packs.resources.{ResourceManager, SimpleJsonResourceReloadListener}
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.util.RandomSource
import net.minecraft.world.item.{DyeColor, ItemStack}
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.AddReloadListenerEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.server.ServerLifecycleHooks

import java.io
import java.util.Random
import java.util.concurrent.Callable
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._


object Loot {
  //  val containers = Array(
  //    ChestGenHooks.DUNGEON_CHEST,
  //    ChestGenHooks.PYRAMID_DESERT_CHEST,
  //    ChestGenHooks.PYRAMID_JUNGLE_CHEST,
  //    ChestGenHooks.STRONGHOLD_LIBRARY)

  val factories = mutable.Map.empty[ResourceLocation, Callable[FileSystem]]

  val worldDisks = mutable.ArrayBuffer.empty[(ItemStack, Int)]

  def disksForCycling = if (disksForCyclingClient.nonEmpty) disksForCyclingClient else disksForCyclingServer

  val disksForCyclingServer = mutable.ArrayBuffer.empty[ItemStack]

  val disksForCyclingClient = mutable.ArrayBuffer.empty[ItemStack]

  val disksForSampling = mutable.ArrayBuffer.empty[ItemStack]

  val disksForClient = mutable.ArrayBuffer.empty[ItemStack]

  val eepromsForServer = mutable.ArrayBuffer.empty[ItemStack]
  val eepromsForClient = mutable.ArrayBuffer.empty[ItemStack]

  private val datapackDisks = mutable.ArrayBuffer.empty[(ItemStack, Int)]
  private val datapackCyclingDisks = mutable.ArrayBuffer.empty[ItemStack]
  private val datapackEEPROMs = mutable.ArrayBuffer.empty[(ResourceLocation, ItemStack)]
  private val datapackFactories = mutable.Map.empty[ResourceLocation, Callable[FileSystem]]
  private val datapackPreviousFactories = mutable.Map.empty[ResourceLocation, Option[Callable[FileSystem]]]

  private val defaultEEPROMId = ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, Constants.ItemName.LuaBios)
  private val defaultOpenOSId = ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, Constants.ItemName.OpenOS)

  def defaultEEPROM: ItemStack = synchronized {
    datapackEEPROMs.collectFirst {
      case (id, stack) if id == defaultEEPROMId => stack.copy()
    }.orElse {
      eepromsForClient.find(_.get(OCComponents.LABEL.get()) == "EEPROM (Lua BIOS)").map(_.copy())
    }.getOrElse(ItemStack.EMPTY)
  }

  def defaultOpenOS: ItemStack = synchronized {
    datapackDisks.collectFirst {
      case (stack, _) if stack.get(OCComponents.LOOT_DISK.get()) == defaultOpenOSId => stack.copy()
    }.orElse {
      Option(OCItems.get(Constants.ItemName.OpenOS)).map(_.createItemStack(1))
    }.getOrElse(ItemStack.EMPTY)
  }

  def resetDisksForClient(): Unit = synchronized {
    disksForClient.clear()
    for ((stack, _) <- datapackDisks
         if !disksForClient.exists(ItemStack.isSameItemSameComponents(_, stack))) {
      disksForClient += stack.copy()
    }
  }

  // IDs registered into Items.descriptors via Items.registerStack for loot disks
  // (see createLootDisk below). decorateCreativeTab must skip these when iterating
  // descriptors, since the same stacks are already added via disksForClient — iterating
  // both caused floppy loot disks to be registered twice in the creative tab, crashing
  // BuildCreativeModeTabContentsEvent with "Itemstack ... already exists in the tab's list".
  val lootDiskDescriptorIds = mutable.Set.empty[String]

  def isLootDisk(stack: ItemStack): Boolean = api.Items.get(stack) == api.Items.get(Constants.ItemName.Floppy) && stack.has(OCComponents.LOOT_DISK.get())

  def randomDisk(rng: Random) =
    if (disksForSampling.nonEmpty) Some(disksForSampling(rng.nextInt(disksForSampling.length)))
    else None

  /**
   * Select a disk for a loot-table result. This is deliberately evaluated when
   * the chest is opened, after datapack reloads have populated the disk list.
   */
  def randomDiskForLoot(rng: RandomSource): ItemStack = synchronized {
    if (disksForSampling.nonEmpty) disksForSampling(rng.nextInt(disksForSampling.length)).copy()
    else ItemStack.EMPTY
  }

  def registerLootDisk(display_name: String, name: String, loc: ResourceLocation, color: DyeColor, factory: Callable[FileSystem], doRecipeCycling: Boolean): ItemStack = {
    val stack = OCItems.get(Constants.ItemName.Floppy).createItemStack(1)
    stack.set(OCComponents.LABEL, name)
    stack.set(DataComponents.CUSTOM_NAME, Component.literal(display_name))
    stack.set(OCComponents.LOOT_DISK, loc)
    stack.set(OCComponents.DISK_COLOR, color)

    OpenComputers.log.debug(s"Registering loot disk '$name' from mod ${loc.getNamespace}: $stack")

    Loot.factories += loc -> factory

    if (doRecipeCycling && !disksForCyclingServer.exists(_.get(OCComponents.LOOT_DISK.get()) == loc)) {
      Loot.disksForCyclingServer += stack
    }

    stack.copy()
  }


  @SubscribeEvent
  def addReloadListener(e: AddReloadListenerEvent): Unit = {
    e.addListener(new SimpleJsonResourceReloadListener(new GsonBuilder().create(), LootDisk.DIRECTORY) {
      override protected def apply(definitions: java.util.Map[ResourceLocation, JsonElement], manager: ResourceManager, profiler: ProfilerFiller): Unit = {
        applyDatapackDisks(definitions, manager)
      }
    })
    e.addListener(new SimpleJsonResourceReloadListener(new GsonBuilder().create(), EEPROM.DIRECTORY) {
      override protected def apply(definitions: java.util.Map[ResourceLocation, JsonElement], manager: ResourceManager, profiler: ProfilerFiller): Unit = {
        applyDatapackEEPROMs(definitions, manager)
      }
    })
  }

  @SubscribeEvent
  def initForWorld(e: LevelEvent.Load): Unit = e.getLevel match {
    case world: ServerLevel if world.dimension == Level.OVERWORLD => {
      refreshWorldDisks(world.getServer)
    }
    case _ =>
  }

  private def refreshWorldDisks(server: net.minecraft.server.MinecraftServer): Unit = {
    worldDisks.clear()
    disksForSampling.clear()

    val path = server.getWorldPath(new LevelResource(Settings.savePath)).toFile
    if (path.exists && path.isDirectory) {
      val listFile = new io.File(path, "loot/loot.properties")
      if (listFile.exists && listFile.isFile) {
        try {
          val listStream = new io.FileInputStream(listFile)
          try {
            val list = new java.util.Properties()
            list.load(listStream)
            parseLootDisks(list, worldDisks, external = true)
          }
          finally listStream.close()
        }
        catch {
          case t: Throwable => OpenComputers.log.warn("Failed opening loot descriptor file in saves folder.", t)
        }
      }
    }

    for (entry <- datapackDisks if !worldDisks.exists(existing => sameLootDisk(existing._1, entry._1))) {
      worldDisks += entry
    }
    for ((stack, count) <- worldDisks if count > 0) {
      for (_ <- 0 until count) disksForSampling += stack
    }
  }

  private def refreshAndSyncWorldDisks(): Unit = {
    Option(ServerLifecycleHooks.getCurrentServer).foreach { server =>
      refreshWorldDisks(server)
      for (player <- server.getPlayerList.getPlayers.asScala) {
        li.cil.oc.server.PacketSender.sendLootDisks(player)
      }
    }
  }

  private def sameLootDisk(left: ItemStack, right: ItemStack): Boolean =
    left.get(OCComponents.LOOT_DISK.get()) == right.get(OCComponents.LOOT_DISK.get())

  private def applyDatapackDisks(definitions: java.util.Map[ResourceLocation, JsonElement], manager: ResourceManager): Unit = synchronized {
    clearDatapackDisks()

    definitions.asScala.toSeq.sortBy(_._1.toString).foreach { case (id, element) =>
      try {
        val data = LootDisk.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow()

        val contents = id.withPrefix(LootDisk.DIRECTORY + "/")
        val filesystem = ServerFileSystem.fromResource(manager, contents)
        if (filesystem == null) throw new IllegalArgumentException("no filesystem resources found below " + contents)

        val factory = new Callable[FileSystem] {
          override def call(): FileSystem = filesystem
        }
        datapackPreviousFactories.getOrElseUpdate(id, factories.get(id))
        datapackFactories += id -> factory
        val hadCyclingDisk = disksForCyclingServer.exists(_.get(OCComponents.LOOT_DISK.get()) == id)
        // Keep the API descriptor key compatible with the legacy loot.properties
        // path (for example, api.Items.get("openos")), while retaining the
        // datapack label as the visible/custom disk name.
        val stack = registerLootDisk(data.label, id.getPath, id, data.color, factory, data.recipeCycling)
        datapackDisks += ((stack, data.weight))
        if (!disksForClient.exists(ItemStack.isSameItemSameComponents(_, stack))) disksForClient += stack.copy()
        if (data.recipeCycling && !hadCyclingDisk) datapackCyclingDisks += stack
      }
      catch {
        case t: Exception => OpenComputers.log.warn(s"Skipping bad loot disk definition '$id'.", t)
      }
    }

    refreshAndSyncWorldDisks()
  }

  private def clearDatapackDisks(): Unit = {
    disksForClient --= disksForClient.filter(existing =>
      datapackDisks.exists(previous => ItemStack.isSameItemSameComponents(existing, previous._1)))
    for ((stack, _) <- datapackDisks) {
      worldDisks --= worldDisks.filter(entry => sameLootDisk(entry._1, stack))
      disksForSampling --= disksForSampling.filter(existing => sameLootDisk(existing, stack))
    }
    disksForCyclingServer --= disksForCyclingServer.filter(existing =>
      datapackCyclingDisks.exists(added => ItemStack.isSameItemSameComponents(existing, added)))

    for ((id, factory) <- datapackFactories) {
      if (factories.get(id).exists(_ eq factory)) {
        datapackPreviousFactories.get(id).flatten match {
          case Some(previous) => factories += id -> previous
          case _ => factories -= id
        }
      }
    }
    datapackDisks.clear()
    datapackCyclingDisks.clear()
    datapackFactories.clear()
    datapackPreviousFactories.clear()
  }

  private def applyDatapackEEPROMs(definitions: java.util.Map[ResourceLocation, JsonElement], manager: ResourceManager): Unit = synchronized {
    eepromsForServer --= eepromsForServer.filter(existing =>
      datapackEEPROMs.exists(previous => ItemStack.isSameItemSameComponents(existing, previous._2)))
    eepromsForClient --= eepromsForClient.filter(existing =>
      datapackEEPROMs.exists(previous => ItemStack.isSameItemSameComponents(existing, previous._2)))
    datapackEEPROMs.clear()

    definitions.asScala.toSeq.sortBy(_._1.toString).foreach { case (id, element) =>
      try {
        val eeprom = EEPROM.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow()
        val code = eeprom.code.toScala.map(readEEPROMResource("code", id, _, manager))
        val data = eeprom.data.toScala.map(readEEPROMResource("data", id, _, manager))
        val stack = OCItems.registerEEPROM(eeprom.label, code.orNull, data.orNull, eeprom.readOnly)
        datapackEEPROMs += ((id, stack))
        eepromsForServer += stack
        eepromsForClient += stack.copy()
      }
      catch {
        case t: Throwable => OpenComputers.log.warn(s"Skipping bad EEPROM loot definition '$id'.", t)
      }
    }

    Option(ServerLifecycleHooks.getCurrentServer).foreach { server =>
      for (player <- server.getPlayerList.getPlayers.asScala) {
        li.cil.oc.server.PacketSender.sendLootEEPROMs(player)
      }
    }
  }

  private def readEEPROMResource(field: String, id: ResourceLocation, file: String, manager: ResourceManager): Array[Byte] = {
    val location = id.withPath(root => EEPROM.DIRECTORY + "/" + root + "/" + file)
    ServerFileSystem.readResource(manager, location) match {
      case Some(bytes) => bytes
      case _ => throw new IllegalArgumentException("missing " + field + " resource " + location)
    }
  }

  private def parseLootDisks(list: java.util.Properties, acc: mutable.ArrayBuffer[(ItemStack, Int)], external: Boolean): Unit = {
    for (key <- list.stringPropertyNames.asScala) {
      val value = list.getProperty(key)
      try value.split(":") match {
        case Array(name, count, color) =>
          val stack = createLootDisk(name, key, external, Some(Color.byName(color)))
          acc += ((stack, count.toInt))
        case Array(name, count) =>
          val stack = createLootDisk(name, key, external)
          acc += ((stack, count.toInt))
        case _ =>
          val stack = createLootDisk(value, key, external)
          acc += ((stack, 1))
      }
      catch {
        case t: Throwable => OpenComputers.log.warn("Bad loot descriptor: " + value, t)
      }
    }
  }

  def createLootDisk(name: String, path: String, external: Boolean, color: Option[DyeColor] = None) = {
    val callable = if (external) new Callable[FileSystem] {
      override def call(): FileSystem = api.FileSystem.asReadOnly(api.FileSystem.fromSaveDirectory("loot/" + path, 0, false))
    } else new Callable[FileSystem] {
      override def call(): FileSystem = api.FileSystem.fromResource(ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, "loot/" + path))
    }
    val stack = OCItems.registerFloppy(name, path, ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, path), color.getOrElse(DyeColor.LIGHT_GRAY), callable, doRecipeCycling = true)
    lootDiskDescriptorIds += path
    stack
  }
}
