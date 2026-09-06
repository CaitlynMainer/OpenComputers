package li.cil.oc.common.item.data

import com.google.common.base.Strings
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.ImmutableItemStack
import li.cil.oc.common.NameList
import li.cil.oc.common.datacomponents.{OCComponents, RobotChargeInfo}
import li.cil.oc.integration.opencomputers.{DriverScreen, Item}
import li.cil.oc.util.ExtendedDataComponentHolder._
import li.cil.oc.util.ItemUtils
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.{DataComponentHolder, DataComponents}
import net.minecraft.world.item.ItemStack

import scala.jdk.CollectionConverters._
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.ColorRGBA
import net.neoforged.neoforge.common.MutableDataComponentHolder
import net.neoforged.neoforge.server.ServerLifecycleHooks

object RobotData {
  val names = NameList.load(Settings.resourceDomain, "robot")

  def randomName = if (names.length > 0) names((math.random() * names.length).toInt) else "Robot"
}

class RobotData extends ItemData(Constants.BlockName.Robot) {
  def this(stack: ItemStack) = {
    this()
    loadData(stack)
  }

  var name: Component = Component.empty()

  // Overall energy including components.
  var totalEnergy = 0

  // Energy purely stored in robot component - this is what we have to restore manually.
  var robotEnergy = 0
  var tier = 0

  var components = Array.empty[ItemStack]
  var containers = Array.empty[ItemStack]
  var lightColor = 0xF23030
  var flag: Option[ResourceLocation] = None

  private final val StoredEnergyTag = Settings.namespace + "storedEnergy"
  private final val RobotEnergyTag = Settings.namespace + "robotEnergy"
  private final val TierTag = Settings.namespace + "tier"
  private final val ComponentsTag = Settings.namespace + "components"
  private final val ContainersTag = Settings.namespace + "containers"
  private final val LightColorTag = Settings.namespace + "lightColor"

  override def loadData(holder: DataComponentHolder): Unit = {
    name = holder.getComponent(DataComponents.CUSTOM_NAME) match {
      case Some(value) => value
      case None => Component.literal(RobotData.randomName)
    }

    for(RobotChargeInfo(total, stored) <- holder.getComponent(OCComponents.ROBOT_CHARGE)) {
      totalEnergy = total
      robotEnergy = stored
    }

    for(tier <- holder.getComponent(OCComponents.TIER)) {
      this.tier = tier
    }

    for(items <- holder.getComponent(OCComponents.COMPONENTS)) {
      components = items.toArray.map(_.mutableCopy())
    }

    for(items <- holder.getComponent(OCComponents.CONTAINERS)) {
      containers = items.toArray.map(_.mutableCopy())
    }

    for(color <- holder.getComponent(OCComponents.LIGHT_COLOR)) {
      lightColor = color.rgba
    }

    flag = holder.getComponent(OCComponents.ROBOT_FLAG)
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    holder.setComponent(DataComponents.CUSTOM_NAME, name)
    holder.setComponent(OCComponents.ROBOT_CHARGE, RobotChargeInfo(totalEnergy, robotEnergy))
    holder.setComponent(OCComponents.TIER, tier.toByte)
    holder.setComponent(OCComponents.COMPONENTS, components.map(ImmutableItemStack.copyOf).toList)
    holder.setComponent(OCComponents.CONTAINERS, containers.map(ImmutableItemStack.copyOf).toList)
    holder.setComponent(OCComponents.LIGHT_COLOR, new ColorRGBA(lightColor))
    holder.setComponent(OCComponents.ROBOT_FLAG, flag)
  }

  def copyItemStack(provider: HolderLookup.Provider) = {
    val stack = createItemStack()
    // Forget all node addresses and so on. This is used when 'picking' a
    // robot in creative mode.
    val newInfo = new RobotData(stack)
    newInfo.components.foreach(cs => Option(api.Driver.driverFor(cs)) match {
      case Some(driver) if driver == DriverScreen =>
        Item.updateDataTag(cs, nbt => nbt.getAllKeys.asScala.toSeq.foreach(nbt.remove))
      case _ =>
    })
    // Don't show energy info (because it's unreliable) but fill up the
    // internal buffer. This is for creative use only, anyway.
    newInfo.totalEnergy = 0
    newInfo.robotEnergy = 50000
    newInfo.saveData(stack)
    stack
  }
}
