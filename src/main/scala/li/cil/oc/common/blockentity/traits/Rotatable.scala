package li.cil.oc.common.blockentity.traits

import li.cil.oc.api.internal
import li.cil.oc.common.block.SimpleBlock
import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import li.cil.oc.util.ExtendedEnumFacing._
import li.cil.oc.util.ExtendedLevel._
import li.cil.oc.util.RotationHelper
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.state.BlockState

/** TileEntity base class for rotatable blocks. */
trait Rotatable extends RotationAware with internal.Rotatable {
  // ----------------------------------------------------------------------- //
  // Lookup tables
  // ----------------------------------------------------------------------- //

  private val pitch2Direction = Array(Direction.UP, Direction.NORTH, Direction.DOWN)

  private val yaw2Direction = Array(Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST)

  // ----------------------------------------------------------------------- //
  // Accessors
  // ----------------------------------------------------------------------- //

  def pitch: Direction = (if (isMoving) movingBlockState else getBlockState) match {
    case rotatable if rotatable.getProperties.contains(PropertyRotatable.Pitch) => rotatable.getValue(PropertyRotatable.Pitch)
    case _ => Direction.NORTH
  }

  def pitch_=(value: Direction): Unit =
    trySetPitchYaw(value match {
      case Direction.DOWN | Direction.UP => value
      case _ => Direction.NORTH
    }, yaw)

  def yaw: Direction = (if (isMoving) movingBlockState else getBlockState) match {
    case rotatable if rotatable.getProperties.contains(PropertyRotatable.Yaw) => rotatable.getValue(PropertyRotatable.Yaw)
    case rotatable if rotatable.getProperties.contains(PropertyRotatable.Facing) => rotatable.getValue(PropertyRotatable.Facing)
    case _ => Direction.SOUTH
  }

  def yaw_=(value: Direction): Unit =
    trySetPitchYaw(pitch, value match {
      case Direction.DOWN | Direction.UP => yaw
      case _ => value
    })

  def setFromEntityPitchAndYaw(entity: Entity) =
    trySetPitchYaw(
      pitch2Direction((entity.getXRot / 90).round + 1),
      yaw2Direction((entity.getYRot / 360 * 4).round & 3))

  def setFromEntityPitchAndYaw(entity: Entity, localYaw: Direction) =
    trySetPitchYaw(
      pitch2Direction((entity.getXRot / 90).round + 1),
      localYaw)

  def setFromPitchAndYaw(localPitch: Float, localYaw: Float) =
    trySetPitchYaw(
      pitch2Direction((localPitch / 90).round + 1),
      yaw2Direction((localYaw / 360 * 4).round & 3))

  def setFromFacing(value: Direction) =
    value match {
      case Direction.DOWN | Direction.UP =>
        trySetPitchYaw(value, yaw)
      case yaw =>
        trySetPitchYaw(Direction.NORTH, yaw)
    }

  /** Public bridge for integrations that transform a block entity off-world. */
  def setFromPitchAndYaw(newPitch: Direction, newYaw: Direction): Boolean =
    trySetPitchYaw(newPitch, newYaw)

  def invertRotation() =
    trySetPitchYaw(pitch match {
      case Direction.DOWN | Direction.UP => pitch.getOpposite
      case _ => Direction.NORTH
    }, yaw.getOpposite)

  override def facing = pitch match {
    case Direction.DOWN | Direction.UP => pitch
    case _ => yaw
  }

  def rotate(axis: Direction) = {
    val state = getLevel.getBlockState(getBlockPos)
    state.getBlock match {
      case simple: SimpleBlock => {
        val valid = simple.getValidRotations(getLevel, getBlockPos)
        if (valid != null && valid.contains(axis)) {
          val (newPitch, newYaw) = facing.getRotation(axis) match {
            case value@(Direction.UP | Direction.DOWN) =>
              if (value == pitch) (value, yaw.getRotation(axis))
              else (value, yaw)
            case value => (Direction.NORTH, value)
          }
          trySetPitchYaw(newPitch, newYaw)
        }
        else false
      }
      case _ if axis == Direction.UP || axis == Direction.DOWN => {
        val updated = state.rotate(getLevel, getBlockPos, if (axis == Direction.DOWN) Rotation.COUNTERCLOCKWISE_90 else Rotation.CLOCKWISE_90)
        updated != state && getLevel.setBlockAndUpdate(getBlockPos, updated)
      }
      case _ => false
    }
  }

  override def toLocal(value: Direction): Direction = if (value == null) null else RotationHelper.toLocal(pitch, yaw, value)

  override def toGlobal(value: Direction): Direction = if (value == null) null else RotationHelper.toGlobal(pitch, yaw, value)

  def validFacings = Array(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)

  // ----------------------------------------------------------------------- //

  protected def onRotationChanged(): Unit = {
    if (isServer) {
      ServerPacketSender.sendRotatableState(this)
    }
    else {
      getLevel.notifyBlockUpdate(getBlockPos)
    }
    getLevel.updateNeighborsAt(getBlockPos, getBlockState.getBlock)
  }

  // ----------------------------------------------------------------------- //

  /** Updates cached translation array and sends notification to clients. */
  protected def updateTranslation(): Unit = {
    if (getLevel != null) {
      onRotationChanged()
    }
  }

  /** Validates new values against the allowed rotations as set in our block. */
  protected def trySetPitchYaw(pitch: Direction, yaw: Direction) = {
    var safePitch = pitch
    var safeYaw = yaw
    if (!pitch2Direction.contains(pitch))
      safePitch = Direction.NORTH
    if (!validFacings.contains(yaw))
      safeYaw = validFacings.headOption.getOrElse(Direction.NORTH)
    val oldState = getLevel.getBlockState(getBlockPos)
    def setState(newState: BlockState): Boolean = {
      if (oldState.hashCode() != newState.hashCode()) {
        getLevel.setBlockAndUpdate(getBlockPos, newState)
        updateTranslation()
        true
      }
      else false
    }
    getBlockState.getBlock match {
      case rotatable if oldState.hasProperty(PropertyRotatable.Pitch) && oldState.hasProperty(PropertyRotatable.Yaw) =>
        setState(oldState.setValue(PropertyRotatable.Pitch, safePitch).setValue(PropertyRotatable.Yaw, safeYaw))
      case rotatable if oldState.hasProperty(PropertyRotatable.Facing) =>
        setState(oldState.setValue(PropertyRotatable.Facing, safeYaw))
      case _ => false
    }
  }
}
