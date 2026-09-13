package li.cil.oc.util

import dev.ryanhcode.sable.companion.SableCompanion
import li.cil.oc.api.network.EnvironmentHost
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d

/**
 * The coordinate boundary between OC's local block space and the physical
 * world space exposed by Sable/Aeronautics.
 *
 * Sable Companion supplies an identity implementation when Sable is not
 * present, so callers do not need optional-mod branches or Sable internals.
 */
object SableCompat {
  def physicalPosition(level: Level, position: Vec3): Vec3 = {
    if (level == null || position == null) position
    else {
      val projected = SableCompanion.INSTANCE.projectOutOfSubLevel(level,
        new Vector3d(position.x, position.y, position.z))
      new Vec3(projected.x, projected.y, projected.z)
    }
  }

  def physicalPosition(host: EnvironmentHost): Vec3 = {
    val level = host.getEnvironmentLevel
    physicalPosition(level, new Vec3(host.xPosition, host.yPosition, host.zPosition))
  }

  def physicalPosition(level: Level, position: BlockPos): Vec3 =
    physicalPosition(level, Vec3.atCenterOf(position))

  /** Transform a local block-space direction into a world-space vector. */
  def physicalDirection(level: Level, position: Vec3, facing: Direction): Vec3 = {
    if (facing == null) Vec3.ZERO
    else if (level == null || position == null) Vec3.atLowerCornerOf(facing.getNormal)
    else {
      val origin = physicalPosition(level, position)
      val target = physicalPosition(level, position.add(facing.getStepX, facing.getStepY, facing.getStepZ))
      target.subtract(origin)
    }
  }

  /** Transform a world-space direction vector into a local-space vector.
   *
   * 'position' should be a location on the target sublevel
   */
  def localDirection(level: Level, position: Vec3, facing: Vec3): Vec3 = {
    if (facing == null) Vec3.ZERO
    else if (level == null || position == null) facing
    else {
      val sublevel = SableCompanion.INSTANCE.getContaining(level, position)
      if (sublevel == null) facing
      else sublevel.logicalPose().transformNormalInverse(facing)
    }
  }

  def distanceSquared(level: Level, a: Vec3, b: Vec3): Double = {
    if (level == null) a.distanceToSqr(b)
    else SableCompanion.INSTANCE.distanceSquaredWithSubLevels(level, a, b)
  }

  def isInPlotGrid(level: Level, position: Vec3): Boolean =
    level != null && position != null && SableCompanion.INSTANCE.isInPlotGrid(level, position)

  def physicalFacing(level: Level, position: Vec3, facing: Direction): Direction = {
    if (facing == null) null
    else {
      val direction = physicalDirection(level, position, facing)
      Direction.getNearest(direction.x, direction.y, direction.z)
    }
  }

  /** Get the transformed direction's horizontal heading in degrees, converted from local-space to world-space.
    *
    * Zero points north (-Z), and values increase clockwise when viewed from
    * above: east is 90, south is 180, and west is 270.
    */
  def physicalHeading(level: Level, position: Vec3, facing: Direction): Double = {
    val direction = physicalDirection(level, position, facing)
    val horizontalLength = math.sqrt(direction.x * direction.x + direction.z * direction.z)
    if (horizontalLength < 1.0e-9) 0.0
    else {
      val heading = math.toDegrees(math.atan2(direction.x, -direction.z)) % 360.0
      if (heading < 0.0) heading + 360.0 else heading
    }
  }

  /** Get the transformed direction's horizontal heading in degrees, converted from world-space to local-space.
   *
   * Zero points north (-Z), and values increase clockwise when viewed from
   * above: east is 90, south is 180, and west is 270.
   */
  def localHeading(level: Level, position: Vec3, facing: Vec3, side: Vec3): Double = {
    var direction = localDirection(level, position, facing)
    var horizontalLength = math.sqrt(direction.x * direction.x + direction.z * direction.z)
    if (horizontalLength < 1.0e-3) { // Try to use the side/right vector to deduce yaw
      direction = localDirection(level, position, side)
      horizontalLength = math.sqrt(direction.x * direction.x + direction.z * direction.z)
      if (horizontalLength < 1.0e-3) 0.0
      else {
        val heading = math.toDegrees(math.atan2(direction.z, direction.x)) % 360.0
        if (heading < 0.0) heading + 360.0 else heading
      }
    }
    else {
      val heading = math.toDegrees(math.atan2(direction.x, -direction.z)) % 360.0
      if (heading < 0.0) heading + 360.0 else heading
    }
  }

  /** Get the transformed direction's elevation above the horizontal plane, converted from local-space to world-space. */
  def physicalPitch(level: Level, position: Vec3, facing: Direction): Double = {
    val direction = physicalDirection(level, position, facing)
    math.toDegrees(math.atan2(direction.y, math.sqrt(direction.x * direction.x + direction.z * direction.z)))
  }

  /** Get the transformed direction's elevation above the horizontal plane, converted from world-space to local-space. */
  def localPitch(level: Level, position: Vec3, facing: Vec3): Double = {
    val direction = localDirection(level, position, facing)
    math.toDegrees(math.atan2(direction.y, math.sqrt(direction.x * direction.x + direction.z * direction.z)))
  }

  /** Convert a physical cardinal direction into the local direction at a block. */
  def localFacing(level: Level, position: Vec3, facing: Direction): Direction = {
    if (level == null || position == null || facing == null) facing
    else Direction.values.find(physicalFacing(level, position, _) == facing).getOrElse(facing)
  }
}
