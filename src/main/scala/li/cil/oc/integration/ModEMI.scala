package li.cil.oc.integration

import dev.emi.emi.api.{EmiEntrypoint, EmiPlugin, EmiRegistry}
import li.cil.oc.common.ContentVisibility

@EmiEntrypoint
final class ModEMI extends EmiPlugin {
  override def register(registry: EmiRegistry): Unit = {
    if (!ContentVisibility.hiddenItems.isEmpty) {
      registry.removeEmiStacks(stack => ContentVisibility.isHidden(stack.getItemStack))
    }
  }
}
