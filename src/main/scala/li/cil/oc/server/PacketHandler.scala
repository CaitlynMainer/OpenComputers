package li.cil.oc.server

import java.io.InputStream
import li.cil.oc.Localization
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.internal.Server
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.network.Connector
import li.cil.oc.common.Advancement
import li.cil.oc.common.PacketType
import li.cil.oc.common.component.{RackKVM => RackKVMComponent, RemoteTerminalHost, TextBuffer}
import li.cil.oc.common.menu
import li.cil.oc.common.entity.Drone
import li.cil.oc.common.entity.DroneInventory
import li.cil.oc.common.item.{Tablet, TabletWrapper}
import li.cil.oc.common.item.data.DriveData
import li.cil.oc.common.item.traits.FileSystemLike
import li.cil.oc.common.blockentity._
import li.cil.oc.common.blockentity.traits.Computer
import li.cil.oc.common.datacomponents.CompoundStorage
import li.cil.oc.common.{PacketHandler => CommonPacketHandler}
import net.minecraft.resources.ResourceLocation
import net.minecraft.Util
import org.apache.logging.log4j.MarkerManager
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.InteractionHand
import net.minecraft.nbt.{CompoundTag, NbtOps}
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.neoforged.neoforge.server.ServerLifecycleHooks

object PacketHandler extends CommonPacketHandler {
  private val securityMarker = MarkerManager.getMarker("SuspiciousPackets")

  private def logForgedPacket(player: ServerPlayer) =
    OpenComputers.log.warn(securityMarker, "Player {} tried to send GUI packets without opening them", player.getGameProfile)

  private def canInteractWith(buffer: api.internal.TextBuffer, player: Player): Boolean = buffer match {
    case textBuffer: TextBuffer => textBuffer.host match {
      case screen: Screen => screen.screens.exists(part =>
        // A Create contraption keeps the fake Screen at its local block position,
        // but EnvironmentHost exposes the real moving position for interaction.
        player.distanceToSqr(part.xPosition, part.yPosition, part.zPosition) <= 64)
      case remote: RemoteTerminalHost => remote.isBufferUsable(buffer, player)
      case _ => true
    }
    case _ => false
  }

  override protected def world(player: Player, dimension: ResourceLocation): Option[Level] =
    Option(ServerLifecycleHooks.getCurrentServer.getLevel(ResourceKey.create(Registries.DIMENSION, dimension)))

  override def dispatch(p: PacketParser): Unit = {
    p.packetType match {
      case PacketType.ComputerPower => onComputerPower(p)
      case PacketType.CopyToAnalyzer => onCopyToAnalyzer(p)
      case PacketType.DriveLock => onDriveLock(p)
      case PacketType.DriveMode => onDriveMode(p)
      case PacketType.DronePower => onDronePower(p)
      case PacketType.KeyDown => onKeyDown(p)
      case PacketType.KeyUp => onKeyUp(p)
      case PacketType.TextInput => onTextInput(p)
      case PacketType.Clipboard => onClipboard(p)
      case PacketType.MachineItemStateRequest => onMachineItemStateRequest(p)
      case PacketType.MouseClickOrDrag => onMouseClick(p)
      case PacketType.MouseScroll => onMouseScroll(p)
      case PacketType.MouseUp => onMouseUp(p)
      case PacketType.PetVisibility => onPetVisibility(p)
      case PacketType.RackMountableMapping => onRackMountableMapping(p)
      case PacketType.RackRelayState => onRackRelayState(p)
      case PacketType.RackKVMSelection => onRackKVMSelection(p)
      case PacketType.RobotAssemblerStart => onRobotAssemblerStart(p)
      case PacketType.RobotStateRequest => onRobotStateRequest(p)
      case PacketType.ServerPower => onServerPower(p)
      case PacketType.TabletCursorTick => onTabletCursorTick(p)
      case PacketType.TextBufferInit => onTextBufferInit(p)
      case PacketType.WaypointLabel => onWaypointLabel(p)
      case PacketType.HoloScreenResize => onHoloScreenResize(p)
      case _ => // Invalid packet.
    }
  }

  def onHoloScreenResize(p: PacketParser): Unit = {
    val screen = p.readBlockEntity[HoloScreen]()
    val side = p.readDirection()
    (screen, side, p.player) match {
      case (Some(holo), Some(resizeSide), player: ServerPlayer)
        if player.distanceToSqr(holo.x + 0.5, holo.y + 0.5, holo.z + 0.5) <= 64 =>
        if (holo.resize(resizeSide)) {
          holo.getLevel.sendBlockUpdated(holo.getBlockPos, holo.getBlockState, holo.getBlockState, 3)
        }
      case _ =>
    }
  }

  def onComputerPower(p: PacketParser): Unit = {
    val containerId = p.readInt()
    val setPower = p.readBoolean()
    p.player match {
      case player: ServerPlayer => player.containerMenu match {
        case computer: menu.Case if computer.containerId == containerId => {
          computer.otherInventory match {
            case te: Computer => trySetComputerPower(te.machine, setPower, player)
            case _ => logForgedPacket(player)
          }
        }
        case robot: menu.Robot if robot.containerId == containerId => {
          robot.otherInventory match {
            case te: Computer => trySetComputerPower(te.machine, setPower, player)
            case _ => logForgedPacket(player)
          }
        }
        case tablet: menu.Tablet if tablet.containerId == containerId =>
          tablet.otherInventory match {
            case wrapper: TabletWrapper =>
              trySetComputerPower(wrapper.machine, setPower, player)
              wrapper.syncRunningState()
              player.containerMenu.broadcastChanges()
            case _ => logForgedPacket(player)
          }
        case _ => logForgedPacket(player)
      }
      case _ =>
    }
  }

  def onServerPower(p: PacketParser): Unit = {
    val containerId = p.readInt()
    val index = p.readInt()
    val setPower = p.readBoolean()
    p.player match {
      case player: ServerPlayer => player.containerMenu match {
        case server: menu.Server if server.containerId == containerId => {
          server.otherInventory match {
            case comp: component.Server => {
              if (comp.rack != null && comp.rack.getMountable(index) == comp)
                trySetComputerPower(comp.machine, setPower, player)
              // else: probably just lag, not invalid packet
            }
            case _ => logForgedPacket(player)
          }
        }
        case _ => logForgedPacket(player)
      }
      case _ =>
    }
  }

  def onCopyToAnalyzer(p: PacketParser): Unit = {
    val text = p.readUTF()
    val line = p.readInt()
    ComponentTracker.get(p.player.level, text) match {
      case Some(buffer: TextBuffer) => buffer.copyToAnalyzer(line, p.player.asInstanceOf[Player])
      case _ => // Invalid Packet
    }
  }

  def onDriveLock(p: PacketParser): Unit = p.player match {
    case player: ServerPlayer => {
      val heldItem = player.getItemInHand(InteractionHand.MAIN_HAND)
      heldItem.getItem match {
        case drive: FileSystemLike => DriveData.lock(heldItem, player)
        case _ => // Invalid packet
      }
    }
    case _ => // Invalid Packet
  }

  def onDriveMode(p: PacketParser): Unit = {
    val unmanaged = p.readBoolean()
    p.player match {
      case player: ServerPlayer =>
        val heldItem = player.getItemInHand(InteractionHand.MAIN_HAND)
        heldItem.getItem match {
          case drive: FileSystemLike => DriveData.setUnmanaged(heldItem, unmanaged)
          case _ => // Invalid packet.
        }
      case _ => // Invalid packet.
    }
  }

  def onDronePower(p: PacketParser): Unit = {
    val containerId = p.readInt()
    val power = p.readBoolean()
    p.player match {
      case player: ServerPlayer => player.containerMenu match {
        case drone: menu.Drone if drone.containerId == containerId => {
          drone.otherInventory match {
            case droneInv: DroneInventory => trySetDronePower(droneInv.drone, power, player)
            case _ => logForgedPacket(player)
          }
        }
        case _ => logForgedPacket(player)
      }
      case _ =>
    }
  }

  private def trySetComputerPower(computer: Machine, value: Boolean, player: ServerPlayer): Unit = {
    if (computer.canInteract(player.getName.getString)) {
      if (value) {
        if (!computer.isPaused) {
          computer.start()
          computer.lastError match {
            case message if message != null => player.sendSystemMessage(Localization.Analyzer.LastError(message))
            case _ =>
          }
        }
      }
      else computer.stop()
    }
  }

  private def trySetDronePower(drone: Drone, value: Boolean, player: ServerPlayer): Unit = {
    val computer = drone.machine
    if (computer.canInteract(player.getName.getString)) {
      if (value) {
        if (!computer.isPaused) {
          drone.start()
          computer.lastError match {
            case message if message != null => player.sendSystemMessage(Localization.Analyzer.LastError(message))
            case _ =>
          }
        }
      }
      else drone.stop()
    }
  }

  def onKeyDown(p: PacketParser): Unit = {
    val address = p.readUTF()
    val key = p.readChar()
    val code = p.readInt()
    ComponentTracker.get(p.player.level, address) match {
      case Some(buffer: api.internal.TextBuffer) if canInteractWith(buffer, p.player) => buffer.keyDown(key, code, p.player)
      case _ => // Invalid Packet
    }
  }

  def onKeyUp(p: PacketParser): Unit = {
    val address = p.readUTF()
    val key = p.readChar()
    val code = p.readInt()
    ComponentTracker.get(p.player.level, address) match {
      case Some(buffer: api.internal.TextBuffer) if canInteractWith(buffer, p.player) => buffer.keyUp(key, code, p.player)
      case _ => // Invalid Packet
    }
  }

  def onTextInput(p: PacketParser): Unit = {
    val address = p.readUTF()
    val codePt = p.readInt()
    if (codePt >= 0 && codePt <= Character.MAX_CODE_POINT) {
      ComponentTracker.get(p.player.level, address) match {
        case Some(buffer: api.internal.TextBuffer) if canInteractWith(buffer, p.player) => buffer.textInput(codePt, p.player)
        case _ => // Invalid Packet
      }
    }
  }

  def onClipboard(p: PacketParser): Unit = {
    val address = p.readUTF()
    val copy = p.readUTF()
    if (copy.length > Settings.get.maxClipboardTextLength) return
    ComponentTracker.get(p.player.level, address) match {
      case Some(buffer: api.internal.TextBuffer) if canInteractWith(buffer, p.player) => buffer.clipboard(copy, p.player)
      case _ => // Invalid Packet
    }
  }

  def onMouseClick(p: PacketParser): Unit = {
    val address = p.readUTF()
    val x = p.readFloat()
    val y = p.readFloat()
    val dragging = p.readBoolean()
    val button = p.readByte()
    ComponentTracker.get(p.player.level, address) match {
      case Some(buffer: api.internal.TextBuffer) if canInteractWith(buffer, p.player) =>
        val player = p.player
        if (dragging) buffer.mouseDrag(x, y, button, player)
        else buffer.mouseDown(x, y, button, player)
      case _ => // Invalid Packet
    }
  }

  def onMouseUp(p: PacketParser): Unit = {
    val address = p.readUTF()
    val x = p.readFloat()
    val y = p.readFloat()
    val button = p.readByte()
    ComponentTracker.get(p.player.level, address) match {
      case Some(buffer: api.internal.TextBuffer) if canInteractWith(buffer, p.player) =>
        val player = p.player
        buffer.mouseUp(x, y, button, player)
      case _ => // Invalid Packet
    }
  }

  def onMouseScroll(p: PacketParser): Unit = {
    val address = p.readUTF()
    val x = p.readFloat()
    val y = p.readFloat()
    val button = p.readByte()
    ComponentTracker.get(p.player.level, address) match {
      case Some(buffer: api.internal.TextBuffer) if canInteractWith(buffer, p.player) =>
        val player = p.player
        buffer.mouseScroll(x, y, button, player)
      case _ => // Invalid Packet
    }
  }

  def onPetVisibility(p: PacketParser): Unit = {
    val value = p.readBoolean()
    p.player match {
      case player: ServerPlayer =>
        if (if (value) {
          PetVisibility.hidden.remove(player.getName.getString)
        }
        else {
          PetVisibility.hidden.add(player.getName.getString)
        }) {
          // Something changed.
          PacketSender.sendPetVisibility(Some(player.getName.getString))
        }
      case _ => // Invalid packet.
    }
  }

  def onRackMountableMapping(p: PacketParser): Unit = {
    val containerId = p.readInt()
    val mountableIndex = p.readInt()
    val nodeIndex = p.readInt()
    val side = p.readDirection()
    p.player match {
      case player: ServerPlayer => player.containerMenu match {
        case rack: menu.Rack if rack.containerId == containerId => {
          rack.otherInventory match {
            case t: Rack => {
              if (t.stillValid(player))
                t.connect(mountableIndex, nodeIndex - 1, side)
              // else: probably just lag, not invalid packet
            }
            case _ => logForgedPacket(player)
          }
        }
        case _ => logForgedPacket(player)
      }
      case _ =>
    }
  }

  def onRackRelayState(p: PacketParser): Unit = {
    val containerId = p.readInt()
    val enabled = p.readBoolean()
    p.player.containerMenu match {
      case rack: menu.Rack if rack.containerId == containerId => {
        (rack.otherInventory, p.player) match {
          case (t: Rack, player: ServerPlayer) if t.stillValid(player) =>
            t.isRelayEnabled = enabled
            t.setChanged()
          case _ =>
        }
      }
      case _ => // Invalid packet or container closed early.
    }
  }

  def onRackKVMSelection(p: PacketParser): Unit = {
    val rack = p.readBlockEntity[Rack]()
    val kvmSlot = p.readInt()
    val serverSlot = p.readInt()
    (rack, p.player) match {
      case (Some(targetRack), player: ServerPlayer) if kvmSlot >= 0 && kvmSlot < targetRack.getContainerSize =>
        targetRack.getMountable(kvmSlot) match {
          case kvm: RackKVMComponent if kvm.isRemoteUsable(player) => kvm.selectRackSlot(serverSlot)
          case _ => logForgedPacket(player)
        }
      case _ =>
    }
  }

  def onRobotAssemblerStart(p: PacketParser): Unit = {
    val containerId = p.readInt()
    p.player.containerMenu match {
      case assembler: menu.Assembler if assembler.containerId == containerId => {
        assembler.assembler match {
          case te: Assembler =>
            if (te.start(p.player match {
              case player: ServerPlayer => player.isCreative
              case _ => false
            })) te.output.foreach(stack => Advancement.onAssemble(stack, p.player))
          case _ =>
        }
      }
      case _ => // Invalid packet or container closed early.
    }
  }

  def onRobotStateRequest(p: PacketParser): Unit = {
    p.readBlockEntity[RobotProxy]() match {
      case Some(proxy) => proxy.getEnvironmentLevel.sendBlockUpdated(proxy.getBlockPos, proxy.getEnvironmentLevel.getBlockState(proxy.getBlockPos), proxy.getEnvironmentLevel.getBlockState(proxy.getBlockPos), 3)
      case _ => // Invalid packet.
    }
  }

  def onMachineItemStateRequest(p: PacketParser): Unit = p.player match {
    case player: ServerPlayer => {
      val stack = p.readItemStack()
      PacketSender.sendMachineItemState(player, stack, Tablet.get(stack, p.player).machine.isRunning)
    }
    case _ => // ignore
  }

  def onTabletCursorTick(p: PacketParser): Unit = p.player match {
    case player: ServerPlayer => Tablet.tickCursorHeld(player, p.readUTF())
    case _ => // Ignore.
  }

  def onTextBufferInit(p: PacketParser): Unit = {
    val address = p.readUTF()
    p.player match {
      case entity: ServerPlayer =>
        ComponentTracker.get(p.player.level, address) match {
          case Some(buffer: TextBuffer) =>
            buffer.host match {
              case screen: Screen => screen.ensureServerBufferLoaded()
              case _ =>
            }
            if (buffer.host match {
              case screen: Screen if !screen.isOrigin => false
              case _ => true
            }) {
              val nbt = new CompoundStorage()
              buffer.data.saveData(nbt)
              PacketSender.sendTextBufferInit(
                address,
                CompoundStorage.CODEC.encode(nbt, NbtOps.INSTANCE, new CompoundTag()).getOrThrow().asInstanceOf[CompoundTag],
                buffer.getMaximumWidth,
                buffer.getMaximumHeight,
                buffer.getMaximumColorDepth.ordinal,
                buffer.getViewportWidth,
                buffer.getViewportHeight,
                entity
              )
            }
          case _ => // Invalid packet.
        }
      case _ => // Invalid packet.
    }
  }

  def onWaypointLabel(p: PacketParser): Unit = {
    val entity = p.readBlockEntity[Waypoint]()
    val label = p.readUTF().take(32)
    entity match {
      case Some(waypoint) => p.player match {
        case player: ServerPlayer if player.distanceToSqr(waypoint.x + 0.5, waypoint.y + 0.5, waypoint.z + 0.5) <= 64 =>
          if (label != waypoint.label) {
            waypoint.label = label
            PacketSender.sendWaypointLabel(waypoint)
          }
        case _ =>
      }
      case _ => // Invalid packet.
    }
  }

  protected override def createParser(stream: InputStream, player: Player) = new PacketParser(stream, player)
}
