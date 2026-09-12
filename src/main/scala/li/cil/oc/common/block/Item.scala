package li.cil.oc.common.block

import li.cil.oc.{Constants, Settings, api}
import li.cil.oc.common.{block, blockentity}
import li.cil.oc.common.item.data.{MicrocontrollerData, PrintData, RobotData}
import li.cil.oc.util.{Rarity, RotationHelper, SableCompat}
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item
import net.minecraft.world.item.Item.{Properties, TooltipContext}
import net.minecraft.world.item.{BlockItem, ItemStack, TooltipFlag}
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

class Item(value: Block, props: Properties) extends BlockItem(value, props) {
  override def appendHoverText(stack: ItemStack, ctx: TooltipContext, tooltip: java.util.List[Component], flag: TooltipFlag): Unit = {
    getBlock match {
      case _: block.Microcontroller =>
        stack.set(DataComponents.RARITY, Rarity.byTier(new MicrocontrollerData(stack).tier))
      case _: block.RobotProxy =>
        stack.set(DataComponents.RARITY, Rarity.byTier(new RobotData(stack).tier))
      case _ =>
    }
    super.appendHoverText(stack, ctx, tooltip, flag)
  }

  override def getName(stack: ItemStack): Component = {
    if (api.Items.get(stack) == api.Items.get(Constants.BlockName.Print)) {
      val data = new PrintData(stack)
      data.label.map(Component.literal).getOrElse(super.getName(stack))
    }
    else super.getName(stack)
  }

  @Deprecated
  override def getDescriptionId: String = getBlock match {
    case simple: SimpleBlock => simple.getDescriptionId
    case _ => Settings.namespace + "tile"
  }

  override def placeBlock(ctx: BlockPlaceContext, newState: BlockState): Boolean = {
    // When placing robots in creative mode, we have to copy the stack
    // manually before it's placed to ensure different component addresses
    // in the different robots, to avoid interference of screens e.g.
    val needsCopying = ctx.getPlayer.isCreative && api.Items.get(ctx.getItemInHand) == api.Items.get(Constants.BlockName.Robot)
    val ctxToUse = if (needsCopying) {
      val stackToUse = new RobotData(ctx.getItemInHand).copyItemStack(ctx.getLevel.registryAccess())
      val hitResult = new BlockHitResult(ctx.getClickLocation, ctx.getClickedFace, ctx.getClickedPos, ctx.isInside)
      new BlockPlaceContext(ctx.getLevel, ctx.getPlayer, ctx.getHand, stackToUse, hitResult)
    }
    else ctx
    if (super.placeBlock(ctxToUse, newState)) {
      // If it's a rotatable block try to make it face the player.
      ctx.getLevel.getBlockEntity(ctxToUse.getClickedPos) match {
        case keyboard: blockentity.Keyboard => // Ignore.
        case rotatable: blockentity.traits.Rotatable =>
          val localClickPos = Vec3.atCenterOf(ctxToUse.getClickedPos)
          val forward = Vec3.directionFromRotation(ctxToUse.getPlayer.getXRot, ctxToUse.getPlayer.getYRot).reverse()
          val side = Vec3.directionFromRotation(0, ctxToUse.getPlayer.getYRot + 90).reverse()
          val localYaw = SableCompat.localHeading(ctxToUse.getLevel, localClickPos, forward, side)
          val localPitch = SableCompat.localPitch(ctxToUse.getLevel, localClickPos, forward)
          rotatable.setFromPitchAndYaw(localPitch.toFloat, localYaw.toFloat)
          if (!rotatable.isInstanceOf[blockentity.RobotProxy]) {
            rotatable.invertRotation()
          }
        case _ => // Ignore.
      }
      true
    }
    else false
  }
}
