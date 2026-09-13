package li.cil.oc.integration.create

import com.simibubi.create.AllDisplaySources
import com.simibubi.create.api.behaviour.display.DisplaySource
import li.cil.oc.common.blockentity.BlockEntityTypes
import li.cil.oc.integration.{ModProxy, Mods}

object ModCreate extends ModProxy {
  override def getMod = Mods.Create

  override def initialize(): Unit = {
    CreateContraptionTransformers.register()
    CreateDrivers.register()
    registerComputerDisplaySource()
  }

  /**
   * Create's computer display source is associated only with ComputerCraft's
   * block entities. Associate it with OC's adapter, which is the block a
   * Display Link is linked to when the Display Link is exposed to an OC
   * network.
   */
  private def registerComputerDisplaySource(): Unit = {
    val source = AllDisplaySources.COMPUTER.get()
    Seq(BlockEntityTypes.ADAPTER).foreach { blockEntityType =>
      DisplaySource.BY_BLOCK_ENTITY.add(blockEntityType.get(), source)
    }
  }
}
