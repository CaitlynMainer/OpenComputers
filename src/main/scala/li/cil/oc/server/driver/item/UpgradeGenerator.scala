package li.cil.oc.server.driver.item

import li.cil.oc.api
import li.cil.oc.api.driver.EnvironmentHost
import li.cil.oc.api.tileentity.Robot
import li.cil.oc.common.{Slot, Tier}
import li.cil.oc.server.component
import net.minecraft.item.ItemStack

object UpgradeGenerator extends Item {
  override def worksWith(stack: ItemStack) = isOneOf(stack, api.Items.get("generatorUpgrade"))

  override def createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    host match {
      case robot: Robot => new component.UpgradeGenerator(robot)
      case _ => null
    }

  override def slot(stack: ItemStack) = Slot.Upgrade

  override def tier(stack: ItemStack) = Tier.Two
}
