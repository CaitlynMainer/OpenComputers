package li.cil.oc.common.condition

import com.mojang.serialization.MapCodec
import li.cil.oc.{OpenComputers, Settings}
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.conditions.ICondition
import net.neoforged.neoforge.registries.{DeferredRegister, NeoForgeRegistries}

final class Tier4EnabledCondition private extends ICondition {
  override def test(context: ICondition.IContext): Boolean = !Settings.get.hideTier4

  override def codec(): MapCodec[_ <: ICondition] = Tier4EnabledCondition.CODEC
}

object Tier4EnabledCondition {
  val INSTANCE = new Tier4EnabledCondition
  val CODEC: MapCodec[Tier4EnabledCondition] = MapCodec.unit(INSTANCE).stable()
}

final class OpenScreensEnabledCondition private extends ICondition {
  override def test(context: ICondition.IContext): Boolean = !Settings.get.hideOpenScreens

  override def codec(): MapCodec[_ <: ICondition] = OpenScreensEnabledCondition.CODEC
}

object OpenScreensEnabledCondition {
  val INSTANCE = new OpenScreensEnabledCondition
  val CODEC: MapCodec[OpenScreensEnabledCondition] = MapCodec.unit(INSTANCE).stable()
}

object ContentConditions {
  private val Conditions = DeferredRegister.create[MapCodec[_ <: ICondition]](NeoForgeRegistries.Keys.CONDITION_CODECS, OpenComputers.ID)

  Conditions.register("tier4_enabled", () => Tier4EnabledCondition.CODEC)
  Conditions.register("openscreens_enabled", () => OpenScreensEnabledCondition.CODEC)

  def init(bus: IEventBus): Unit = Conditions.register(bus)
}
