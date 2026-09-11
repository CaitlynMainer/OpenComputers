package li.cil.oc.server.component.traits

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.util.{BlockInventorySource, BlockPosition, EntityInventorySource, InventorySource}
import li.cil.oc.util.ExtendedBlock._
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.{FakePlayerFactory, TriState}
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.items.wrapper.InvWrapper

import scala.collection.convert.ImplicitConversionsToScala._
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.vehicle.Minecart
import net.minecraft.world.phys.shapes.CollisionContext
import net.neoforged.neoforge.event.level.BlockEvent

trait LevelAware {
  def position: BlockPosition

  def world = position.world.get

  def fakePlayer: Player = {
    val player = FakePlayerFactory.get(world.asInstanceOf[ServerLevel], Settings.get.fakePlayerProfile)
    player.setPos(position.x + 0.5, position.y + 0.5, position.z + 0.5)
    player
  }

  private def mayInteract(blockPos: BlockPosition, face: Direction): Boolean = {
    try {
      val trace = new BlockHitResult(fakePlayer.position, face, blockPos.toBlockPos, false)
      val event = new PlayerInteractEvent.RightClickBlock(fakePlayer, InteractionHand.MAIN_HAND, blockPos.toBlockPos, trace)
      NeoForge.EVENT_BUS.post(event)
      !event.isCanceled && event.getUseBlock != TriState.FALSE
    } catch {
      case t: Throwable =>
        OpenComputers.log.warn("Some event handler threw up while checking for permission to access a block.", t)
        true
    }
  }

  private def mayInteract(entity: Entity): Boolean = {
    try {
      val event = new PlayerInteractEvent.EntityInteract(fakePlayer, InteractionHand.MAIN_HAND, entity)
      NeoForge.EVENT_BUS.post(event)
      !event.isCanceled
    } catch {
      case t: Throwable =>
        OpenComputers.log.warn("Some event handler threw up while checking for permission to access an entity.", t)
        true
    }
  }

  def mayInteract(inv: InventorySource): Boolean = (inv.inventory match {
    case inv: InvWrapper if inv.getInv != null => inv.getInv.stillValid(fakePlayer)
    case _ => true
  }) && (inv match {
    case inv: BlockInventorySource => mayInteract(inv.position, inv.side)
    case inv: EntityInventorySource => mayInteract(inv.entity)
    case _ => true
  })

  def entitiesInBounds[Type <: Entity](clazz: Class[Type], bounds: AABB) = {
    world.getEntitiesOfClass(clazz, bounds)
  }

  def entitiesInBlock[Type <: Entity](clazz: Class[Type], blockPos: BlockPosition) = {
    entitiesInBounds(clazz, blockPos.bounds)
  }

  def entitiesOnSide[Type <: Entity](clazz: Class[Type], side: Direction) = {
    entitiesInBlock(clazz, position.offset(side))
  }

  def closestEntity[Type <: Entity](clazz: Class[Type], side: Direction) = {
    val blockPos = position.offset(side)
    val candidates = world.getEntitiesOfClass(clazz, blockPos.bounds, null)
    if (!candidates.isEmpty) Some(candidates.minBy(e => fakePlayer.distanceToSqr(e))) else None
  }

  def blockContent(side: Direction) = {
    closestEntity[Entity](classOf[Entity], side) match {
      case Some(_@(_: LivingEntity | _: Minecart)) =>
        (true, "entity")
      case _ =>
        val blockPos = position.offset(side)
        val state = world.getBlockState(blockPos.toBlockPos)
        val block = state.getBlock
        if (state.isAir()) {
          (false, "air")
        }
        else if (block.isInstanceOf[LiquidBlock] || world.isFluidAtPosition(blockPos.toBlockPos, !_.isEmpty)) {
          val event = new BlockEvent.BreakEvent(world, blockPos.toBlockPos, state, fakePlayer)
          NeoForge.EVENT_BUS.post(event)
          (event.isCanceled, "liquid")
        }
        else if (block.isReplaceable(blockPos)) {
          val event = new BlockEvent.BreakEvent(world, blockPos.toBlockPos, state, fakePlayer)
          NeoForge.EVENT_BUS.post(event)
          (event.isCanceled, "replaceable")
        }
        else if (state.getCollisionShape(world, blockPos.toBlockPos, CollisionContext.empty).isEmpty) {
          (true, "passable")
        }
        else {
          (true, "solid")
        }
    }
  }
}
