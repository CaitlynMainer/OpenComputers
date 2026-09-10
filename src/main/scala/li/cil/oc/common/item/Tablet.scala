package li.cil.oc.common.item

import com.google.common.cache.{CacheBuilder, RemovalListener, RemovalNotification}
import com.google.common.collect.ImmutableMap
import li.cil.oc.{api, client, server, Constants, Localization, OpenComputers, Settings}
import li.cil.oc.api.{internal, Driver, Machine}
import li.cil.oc.api.driver.item.Container
import li.cil.oc.api.machine.MachineHost
import li.cil.oc.api.network.{Connector, Message, Node}
import li.cil.oc.client.gui
import li.cil.oc.common.{menu, Slot, Tier}
import li.cil.oc.common.container.ComponentInventory
import li.cil.oc.common.item.data.TabletData
import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.integration.opencomputers.DriverScreen
import li.cil.oc.server.component.{Tablet => TabletComponent}
import li.cil.oc.util._
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.{Direction, HolderLookup}
import net.minecraft.core.component.{DataComponentHolder, DataComponents}
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world._
import net.minecraft.world.entity.{Entity, LivingEntity}
import net.minecraft.world.entity.player.{Inventory, Player}
import net.minecraft.world.item.{Item, ItemStack, TooltipFlag}
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.neoforged.api.distmarker.{Dist, OnlyIn}
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.MutableDataComponentHolder
import net.neoforged.neoforge.common.extensions.IItemExtension
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.tick.{EntityTickEvent, ServerTickEvent}
import net.neoforged.neoforge.server.ServerLifecycleHooks

import java.util
import java.util.UUID
import java.util.concurrent.{Callable, TimeUnit}
import scala.collection.JavaConverters.asJavaIterable
import scala.collection.convert.ImplicitConversionsToScala._
import scala.jdk.CollectionConverters._

class Tablet(props: Properties) extends Item(props) with traits.SimpleItem with traits.Chargeable with IItemExtension {
  final val TimeToAnalyze = 10

  // ----------------------------------------------------------------------- //

  override protected def tooltipExtended(stack: ItemStack, tooltip: util.List[Component], flag: TooltipFlag): Unit = {
    if (Tooltip.showExtendedTooltip(flag)) {
      val info = new TabletData(stack)
      // Ignore/hide the screen.
      val components = info.items.drop(1)
      if (components.length > 1) {
        Tooltip.add(tooltip, flag, "server.Components")
        components.collect {
          case component if !component.isEmpty => tooltip.add(Component.literal("- " + component.getHoverName.getString).setStyle(Tooltip.DefaultStyle))
        }
      }
    }
  }

  override def verifyComponentsAfterLoad(stack: ItemStack): Unit = {
    super.verifyComponentsAfterLoad(stack)
    // FIXME: This is a horrible hack!
    stack.set(DataComponents.RARITY, Rarity.byTier(new TabletData(stack).tier))
  }

  override def isBarVisible(stack: ItemStack) = true

  override def getBarWidth(stack: ItemStack): Int = {
    if (stack.has(DataComponents.CUSTOM_DATA)) {
      val data = Tablet.Client.getWeak(stack) match {
        case Some(wrapper) => wrapper.data
        case _ => new TabletData(stack)
      }
      val ratio = data.energy / data.maxEnergy
      Math.round(ratio * 13.0f).toInt
    }
    else 13
  }

  // ----------------------------------------------------------------------- //

  @OnlyIn(Dist.CLIENT)
  private def modelLocationFromState(running: Option[Boolean]) = {
    val suffix = running match {
      case Some(state) => if (state) "_on" else "_off"
      case _ => ""
    }
    ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, Constants.ItemName.Tablet + suffix))
  }

  def canCharge(stack: ItemStack): Boolean = true

  def charge(stack: ItemStack, amount: Double, simulate: Boolean): Double = {
    if (amount < 0) return amount
    val data = new TabletData(stack)
    traits.Chargeable.applyCharge(amount, data.energy, data.maxEnergy, used => if (!simulate) {
      data.energy += used
      data.saveData(stack)
    })
  }

  // ----------------------------------------------------------------------- //

  override def inventoryTick(stack: ItemStack, level: Level, entity: Entity, slot: Int, selected: Boolean): Unit =
    entity match {
      case player: Player =>
        // Play an audio cue to let players know when they finished analyzing a block.
        if (level.isClientSide && player.getUseItemRemainingTicks == TimeToAnalyze && api.Items.get(player.getUseItem) == api.Items.get(Constants.ItemName.Tablet)) {
          Audio.play(player.getX.toFloat, player.getY.toFloat + 2, player.getZ.toFloat, ".")
        }
        Tablet.get(stack, player).update(level, player, slot, selected)
      case _ =>
    }

  override def onItemUseFirst(stack: ItemStack, ctx: UseOnContext): InteractionResult = {
    Tablet.currentlyAnalyzing = Some((
      BlockPosition(ctx.getClickedPos, ctx.getLevel), ctx.getClickedFace,
      (ctx.getClickLocation.x - ctx.getClickedPos.getX).toFloat,
      (ctx.getClickLocation.y - ctx.getClickedPos.getY).toFloat,
      (ctx.getClickLocation.z - ctx.getClickedPos.getZ).toFloat
    ))
    super.onItemUseFirst(stack, ctx)
  }

  @Deprecated
  override def use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder[ItemStack] = {
    player.startUsingItem(hand)
    InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide)
  }

  override def getUseDuration(stack: ItemStack, entity: LivingEntity): Int = 72000

  override def releaseUsing(stack: ItemStack, level: Level, entity: LivingEntity, duration: Int): Unit = {
    entity match {
      case player: Player =>
        val didAnalyze = getUseDuration(stack, entity) - duration >= TimeToAnalyze
        if (didAnalyze) {
          if (!level.isClientSide) {
            Tablet.currentlyAnalyzing match {
              case Some((position, side, hitX, hitY, hitZ)) => try {
                val computer = Tablet.get(stack, player).machine
                if (computer.isRunning) {
                  val data = new CompoundTag()
                  computer.node.sendToReachable("tablet.use", data, stack, player, position, side, Float.box(hitX), Float.box(hitY), Float.box(hitZ))
                  if (!data.isEmpty) {
                    computer.signal("tablet_use", data)
                  }
                }
              }
              catch {
                case t: Throwable => OpenComputers.log.warn("Block analysis on tablet right click failed gloriously!", t)
              }
              case _ =>
            }
          }
        }
        else {
          if (player.isSecondaryUseActive) {
            if (!level.isClientSide) {
              player match {
                case srvPlr: ServerPlayer => MenuTypes.openTabletGui(srvPlr, Tablet.get(stack, player))
                case _ =>
              }
            }
          }
          else {
            if (!level.isClientSide) {
              val tablet = Tablet.get(stack, player)
              val computer = tablet.machine
              computer.start()
              tablet.syncRunningState()
              computer.lastError match {
                case message if message != null => player.sendSystemMessage(Localization.Analyzer.LastError(message))
                case _ =>
              }
            }
            else {
              Tablet.get(stack, player).componentSlots.collect {
                case Some(buffer: api.internal.TextBuffer) => buffer
              }.headOption match {
                case Some(buffer: api.internal.TextBuffer) => showGui(buffer)
                case _ =>
              }
            }
          }
        }
      case _ =>
    }
  }

  @OnlyIn(Dist.CLIENT)
  private def showGui(buffer: api.internal.TextBuffer): Unit = {
    Minecraft.getInstance.pushGuiLayer(new gui.Screen(buffer, true, () => true, () => buffer.isRenderingEnabled))
  }

  override def maxCharge(stack: ItemStack): Double = new TabletData(stack).maxEnergy

  override def getCharge(stack: ItemStack): Double = new TabletData(stack).energy

  override def setCharge(stack: ItemStack, amount: Double): Unit = {
    val data = new TabletData(stack)
    data.energy = (0.0 max amount) min maxCharge(stack)
    data.saveData(stack)
  }
}

class TabletWrapper(var stack: ItemStack, var player: Player) extends ComponentInventory with MachineHost with internal.Tablet with MenuProvider {
  // Remember our *original* level, so we know which tablets to clear on dimension
  // changes of players holding tablets - since the player entity instance may be
  // kept the same and components are not required to properly handle level changes.
  val getEnvironmentLevel: Level = player.level

  lazy val machine: api.machine.Machine = if (getEnvironmentLevel.isClientSide) null else Machine.create(this)

  val data = new TabletData()
  // Allow T3 tablets to have 8-bit color since they use a T3 screen. For
  // balance/ergonomic reasons, tablets are always limited to T2 resolution of
  // 80x25.
  lazy val colorDepth = if (data.tier >= Tier.Three) api.internal.TextBuffer.ColorDepth.EightBit else api.internal.TextBuffer.ColorDepth.FourBit

  val tablet: TabletComponent = if (getEnvironmentLevel.isClientSide) null else new TabletComponent(this)

  //// Client side only
  private var isInitialized = !getEnvironmentLevel.isClientSide

  var timesChanged: Int = 0

  var isDirty: Boolean = true
  ////

  // Server side only
  private var lastRunning = false

  var autoSave = true
  ////

  def isCreative: Boolean = data.tier == Tier.Five

  def items: Array[ItemStack] = data.items

  override def facing: Direction = RotationHelper.fromYaw(player.getYRot)

  override def toLocal(value: Direction): Direction =
    RotationHelper.toLocal(Direction.NORTH, facing, value)

  override def toGlobal(value: Direction): Direction =
    RotationHelper.toGlobal(Direction.NORTH, facing, value)

  def readFromNBT(provider: HolderLookup.Provider): Unit = {
    loadData(stack)
    if (!getEnvironmentLevel.isClientSide) {
      tablet.loadData(stack)
      machine.loadData(stack)
    }
  }

  def writeToNBT(provider: HolderLookup.Provider): Unit = {
    saveData(stack)
    if (!getEnvironmentLevel.isClientSide) {
      tablet.saveData(stack)
      machine.saveData(stack)
    }
  }

  readFromNBT(player.registryAccess())
  if (!getEnvironmentLevel.isClientSide) {
    api.Network.joinNewNetwork(machine.node)
    val charge = Math.max(0, this.data.energy - tablet.node.globalBuffer)
    tablet.node.changeBuffer(charge)
    writeToNBT(player.registryAccess())
  }

  // ----------------------------------------------------------------------- //

  override def getDisplayName = getName

  override def createMenu(id: Int, playerInventory: Inventory, player: Player) =
    new menu.Tablet(id, playerInventory, stack, this, containerSlotType, containerSlotTier)

  // ----------------------------------------------------------------------- //

  override def onConnect(node: Node): Unit = {
    if (node == this.node) {
      connectComponents()
      node.connect(tablet.node)
    }
    else node.host match {
      case buffer: api.internal.TextBuffer =>
        buffer.setMaximumColorDepth(colorDepth)
        buffer.setMaximumResolution(80, 25)
      case _ =>
    }
  }

  override protected def connectItemNode(node: Node): Unit = {
    super.connectItemNode(node)
    if (node != null) node.host match {
      case buffer: api.internal.TextBuffer => componentSlots collect {
        case Some(keyboard: api.internal.Keyboard) => buffer.node.connect(keyboard.node)
      }
      case keyboard: api.internal.Keyboard => componentSlots collect {
        case Some(buffer: api.internal.TextBuffer) => keyboard.node.connect(buffer.node)
      }
      case _ =>
    }
  }

  override def onDisconnect(node: Node): Unit = {
    if (node == this.node) {
      disconnectComponents()
      tablet.node.remove()
    }
  }

  override def onMessage(message: Message): Unit = {}

  override def host: TabletWrapper = this

  override def getContainerSize: Int = items.length

  override def canPlaceItem(slot: Int, stack: ItemStack): Boolean = slot == getContainerSize - 1 && (Option(Driver.driverFor(stack, getClass)) match {
    case Some(driver) =>
      // Same special cases, similar as in robot, but allow keyboards,
      // because clip-on keyboards kinda seem to make sense, I guess.
      driver != DriverScreen &&
        driver.slot(stack) == containerSlotType &&
        driver.tier(stack) <= containerSlotTier
    case _ => false
  })

  override def stillValid(player: Player): Boolean = machine != null && machine.canInteract(player.getName.getString)

  override def setChanged(): Unit = {
    data.saveData(stack)
    player.getInventory.setChanged()
  }

  // ----------------------------------------------------------------------- //

  override def xPosition: Double = player.getX

  override def yPosition: Double = player.getY + player.getEyeHeight

  override def zPosition: Double = player.getZ

  override def markChanged(): Unit = {}

  // ----------------------------------------------------------------------- //

  def containerSlotType: String =
    if (data.container.isEmpty) Slot.None
    else Option(Driver.driverFor(data.container, getClass)) match {
      case Some(driver: Container) => driver.providedSlot(data.container)
      case _ => Slot.None
    }

  def containerSlotTier: Int =
    if (data.container.isEmpty) Tier.None
    else Option(Driver.driverFor(data.container, getClass)) match {
      case Some(driver: Container) => driver.providedTier(data.container)
      case _ => Tier.None
    }

  override def internalComponents(): java.lang.Iterable[ItemStack] = (0 until getContainerSize).collect {
    case slot if !getItem(slot).isEmpty && isComponentSlot(slot, getItem(slot)) => getItem(slot)
  }.asJava

  override def componentSlot(address: String): Int = componentSlots.indexWhere(_.exists(env => env.node != null && env.node.address == address))

  override def onMachineConnect(node: Node): Unit = onConnect(node)

  override def onMachineDisconnect(node: Node): Unit = onDisconnect(node)

  // ----------------------------------------------------------------------- //

  override def node: Node = Option(machine).fold(null: Node)(_.node)

  // ----------------------------------------------------------------------- //

  /** Synchronize the running flag after a direct power-state change. */
  def syncRunningState(): Unit = {
    val running = machine.isRunning
    val changed = lastRunning != running || data.isRunning != running
    data.isRunning = running
    if (changed) {
      lastRunning = running
      setChanged()
      player match {
        case mp: ServerPlayer => server.PacketSender.sendMachineItemState(mp, stack, running)
        case _ =>
      }
      if (running) {
        componentSlots collect {
          case Some(buffer: api.internal.TextBuffer) =>
            buffer.setPowerState(true)
        }
      }
    }
  }

  def update(level: Level, player: Player, slot: Int, selected: Boolean): Unit = {
    this.player = player
    if (!isInitialized) {
      isInitialized = true
      // This delayed initialization on the client side is required to allow
      // the server to set up the tablet wrapper first (since packets generated
      // in the component setup would otherwise be queued before the events that
      // caused this wrapper's initialization).
      connectComponents()
      componentSlots collect {
        case Some(buffer: api.internal.TextBuffer) =>
          buffer.setMaximumColorDepth(colorDepth)
          buffer.setMaximumResolution(80, 25)
          buffer match {
            case concrete: li.cil.oc.common.component.TextBuffer => {
              // For some reason the TextBuffer needs re-initilization here, might be an X/Y Problem though.
              concrete.markInitialized()
            }
            case _ =>
          }
      }

      client.PacketSender.sendMachineItemStateRequest(stack, level.registryAccess())
    }
    if (!level.isClientSide) {
      if (isCreative && level.getGameTime % Settings.get.tickFrequency == 0) {
        machine.node.asInstanceOf[Connector].changeBuffer(Double.PositiveInfinity)
      }
      machine.update()
      updateComponents()
      data.energy = tablet.node.globalBuffer()
      data.maxEnergy = tablet.node.globalBufferSize()
      syncRunningState()
    }
  }

  // ----------------------------------------------------------------------- //

  override def loadData(holder: DataComponentHolder): Unit = {
    data.loadData(holder)
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    saveComponents()
    data.saveData(holder)
  }
}

object Tablet {
  // This is super-hacky, but since it's only used on the client we get away
  // with storing context information for analyzing a block in the singleton.
  var currentlyAnalyzing: Option[(BlockPosition, Direction, Float, Float, Float)] = None

  def getId(stack: ItemStack): Option[String] = {
    val tag = ItemUtils.getTag(stack)
    if (tag != null && tag.contains(Settings.namespace + "tablet", Tag.TAG_STRING)) {
      Some(tag.getString(Settings.namespace + "tablet"))
    }
    else None
  }

  def getOrCreateId(stack: ItemStack): String = {
    var id: String = null
    CustomData.update(DataComponents.CUSTOM_DATA, stack, data => {
      if (!data.contains(Settings.namespace + "tablet")) {
        data.putString(Settings.namespace + "tablet", UUID.randomUUID().toString)
      }
      id = data.getString(Settings.namespace + "tablet")
    })

    id
  }

  def get(stack: ItemStack, holder: Player): TabletWrapper = {
    if (holder.level.isClientSide) Client.get(stack, holder)
    else Server.get(stack, holder)
  }

  @SubscribeEvent
  def onLevelSave(e: LevelEvent.Save): Unit = {
    Server.saveAll(e.getLevel.asInstanceOf[Level])
  }

  @SubscribeEvent
  def onPlayerSave(e: PlayerEvent.SaveToFile): Unit = {
    Server.save(e.getEntity)
  }

  @SubscribeEvent
  def onLevelUnload(e: LevelEvent.Unload): Unit = {
    Client.clear(e.getLevel.asInstanceOf[Level])
    Server.clear(e.getLevel.asInstanceOf[Level])
  }

  @SubscribeEvent
  def onClientTick(e: ClientTickEvent.Pre): Unit = {
    Client.cleanUp()
    ServerLifecycleHooks.getCurrentServer match {
      case integrated: IntegratedServer if Minecraft.getInstance.isPaused =>
        // While the game is paused, manually keep all tablets alive, to avoid
        // them being cleared from the cache, causing them to stop.
        Client.keepAlive()
        Server.keepAlive()
      case _ => // Never mind!
    }
  }

  @SubscribeEvent
  def onServerTick(e: ServerTickEvent.Pre): Unit = {
    Server.cleanUp()
  }

  @SubscribeEvent
  def onEntityTick(e: EntityTickEvent.Post): Unit = e.getEntity match {
    case player: Player =>
      // The stack carried by an open menu is not part of the player's inventory,
      // so vanilla does not call Item#inventoryTick for it.
      val stack = player.containerMenu.getCarried
      if (!stack.isEmpty && stack.getItem.isInstanceOf[Tablet]) {
        stack.inventoryTick(player.level, player, -1, false)
      }
    case _ =>
  }

  abstract class Cache extends Callable[TabletWrapper] with RemovalListener[String, TabletWrapper] {
    val cache: com.google.common.cache.Cache[String, TabletWrapper] = com.google.common.cache.CacheBuilder.newBuilder().
      expireAfterAccess(timeout, TimeUnit.SECONDS).
      removalListener(this).
      asInstanceOf[CacheBuilder[String, TabletWrapper]].
      build[String, TabletWrapper]()

    protected def timeout = 10

    // To allow access in cache entry init.
    private var currentStack: ItemStack = _

    private var currentHolder: Player = _

    def get(stack: ItemStack, holder: Player): TabletWrapper = {
      val id = getOrCreateId(stack)
      cache.synchronized {
        currentStack = stack
        currentHolder = holder

        // if the item is still cached, we can detect if it is dirty (client side only)
        if (holder.level.isClientSide) {
          Client.getWeak(stack) match {
            case Some(weak) =>
              val timesChanged = holder.getInventory.getTimesChanged
              if (timesChanged != weak.timesChanged) {
                if (!weak.isDirty) {
                  weak.isDirty = true
                  client.PacketSender.sendMachineItemStateRequest(stack, holder.level.registryAccess())
                }
                weak.timesChanged = timesChanged
              }
            case _ =>
          }
        }

        var wrapper = cache.get(id, this)

        // Force re-load on world change, in case some components store a
        // reference to the world object.
        if (holder.level != wrapper.getEnvironmentLevel) {
          wrapper.writeToNBT(holder.registryAccess())
          wrapper.autoSave = false
          cache.invalidate(id)
          cache.cleanUp()
          wrapper = cache.get(id, this)
        }

        currentStack = null
        currentHolder = null

        wrapper.stack = stack
        wrapper.player = holder
        wrapper
      }
    }

    def call: TabletWrapper = {
      new TabletWrapper(currentStack, currentHolder)
    }

    def onRemoval(e: RemovalNotification[String, TabletWrapper]): Unit = {
      val tablet = e.getValue
      if (tablet.node != null) {
        // Server.
        tablet.machine.stop()

        // Cache eviction tears down the live machine. Persist that stopped
        // state as well, otherwise a tablet that was just turned off may
        // retain its previous running flag while it is in a charger.
        tablet.data.isRunning = tablet.machine.isRunning
        if (tablet.autoSave) tablet.writeToNBT(tablet.player.registryAccess())

        for (node <- tablet.machine.node.network.nodes) {
          node.remove()
        }
        tablet.setChanged()
      }
    }

    def clear(level: Level): Unit = {
      cache.synchronized {
        val tabletsInWorld = cache.asMap.filter(_._2.getEnvironmentLevel == level)
        cache.invalidateAll(asJavaIterable(tabletsInWorld.keys))
        cache.cleanUp()
      }
    }

    def cleanUp(): Unit = {
      cache.synchronized(cache.cleanUp())
    }

    def keepAlive(): ImmutableMap[String, TabletWrapper] = {
      // Just touching to update last access time.
      cache.getAllPresent(asJavaIterable(cache.asMap.keys))
    }
  }

  object Client extends Cache {
    override protected def timeout = 5

    def getWeak(stack: ItemStack): Option[TabletWrapper] = {
      getId(stack).flatMap(key => Option(cache.asMap.get(key)))
    }

    def get(stack: ItemStack): Option[TabletWrapper] = {
      val id = getId(stack);
      if (id.nonEmpty) {
        cache.synchronized(Option(cache.getIfPresent(id)))
      }
      else None
    }
  }

  object Server extends Cache {
    def invalidate(stack: ItemStack): Unit = {
      val id = getOrCreateId(stack)
      cache.synchronized {
        // Inventory transfers commonly give the charger a copy of the
        // player's stack. Make cache teardown persist into that actual stack.
        Option(cache.getIfPresent(id)).foreach(_.stack = stack)
        cache.invalidate(id)
        cache.cleanUp()
      }
    }

    def save(player: Player): Unit = {
      cache.synchronized {
        for (tablet <- cache.asMap.values if tablet.player == player) {
          tablet.writeToNBT(player.registryAccess())
        }
      }
    }

    def saveAll(level: Level): Unit = {
      cache.synchronized {
        for (tablet <- cache.asMap.values if tablet.getEnvironmentLevel == level) {
          tablet.writeToNBT(tablet.player.registryAccess())
        }
      }
    }
  }

}
