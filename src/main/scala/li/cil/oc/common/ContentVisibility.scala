package li.cil.oc.common

import li.cil.oc.Settings
import li.cil.oc.common.init.{OCBlocks, OCItems}
import net.minecraft.world.item.{Item, ItemStack}

import java.util

/**
 * Optional content that stays registered for world compatibility, but can be
 * hidden from catalogs and have its recipes gated.
 */
object ContentVisibility {
  def isTier4(item: Item): Boolean = tier4Items.contains(item)

  def isOpenScreens(item: Item): Boolean = openScreensItems.contains(item)

  def isHidden(stack: ItemStack): Boolean = !stack.isEmpty && isHidden(stack.getItem)

  def isHidden(item: Item): Boolean =
    (Settings.get.hideTier4 && isTier4(item)) ||
      (Settings.get.hideOpenScreens && isOpenScreens(item))

  def hiddenItems: util.Set[Item] = {
    val hidden = new util.HashSet[Item]
    if (Settings.get.hideTier4) hidden.addAll(tier4Items)
    if (Settings.get.hideOpenScreens) hidden.addAll(openScreensItems)
    util.Set.copyOf(hidden)
  }

  lazy val tier4Items: util.Set[Item] = util.Set.of(
    OCBlocks.CaseTier4.asItem(),
    OCBlocks.HoloScreenTier4.asItem(),
    OCBlocks.ScreenTier4.asItem(),
    OCBlocks.FlatScreenBackTier4.asItem(),
    OCBlocks.FlatScreenFrontTier4.asItem(),
    OCItems.APUTier3.asItem(),
    OCItems.ChipTier4.asItem(),
    OCItems.ComponentBusTier4.asItem(),
    OCItems.CPUTier4.asItem(),
    OCItems.GraphicsCardTier4.asItem(),
    OCItems.HDDTier4.asItem(),
    OCItems.RAMTier7.asItem(),
    OCItems.ServerTier4.asItem(),
    OCItems.SSDTier3.asItem()
  )

  lazy val openScreensItems: util.Set[Item] = util.Set.of(
    OCBlocks.FlatScreenBackTier1.asItem(),
    OCBlocks.FlatScreenBackTier2.asItem(),
    OCBlocks.FlatScreenBackTier3.asItem(),
    OCBlocks.FlatScreenBackTier4.asItem(),
    OCBlocks.FlatScreenFrontTier1.asItem(),
    OCBlocks.FlatScreenFrontTier2.asItem(),
    OCBlocks.FlatScreenFrontTier3.asItem(),
    OCBlocks.FlatScreenFrontTier4.asItem()
  )
}
