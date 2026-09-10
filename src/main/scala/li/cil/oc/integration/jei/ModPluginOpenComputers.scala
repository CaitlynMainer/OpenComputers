package li.cil.oc.integration.jei

import li.cil.oc.OpenComputers
import li.cil.oc.client.gui.Relay
import li.cil.oc.common.{ContentVisibility, Loot}
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.common.init.{OCBlocks, OCItems}
import li.cil.oc.integration.util.ItemSearch
import li.cil.oc.util.StackOption
import mezz.jei.api.{IModPlugin, JeiPlugin}
import mezz.jei.api.constants.VanillaTypes
import mezz.jei.api.ingredients.subtypes.{ISubtypeInterpreter, UidContext}
import mezz.jei.api.registration.{IAdvancedRegistration, IGuiHandlerRegistration, IRecipeCategoryRegistration, IRecipeRegistration, ISubtypeRegistration}
import mezz.jei.api.runtime.IJeiRuntime
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

import scala.jdk.CollectionConverters._

@JeiPlugin
class ModPluginOpenComputers extends IModPlugin {
  override def getPluginUid: ResourceLocation = ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "jei_plugin")

  override def registerCategories(registry: IRecipeCategoryRegistration): Unit = {
    registry.addRecipeCategories(ManualUsageHandler.ManualUsageRecipeCategory)
    registry.addRecipeCategories(CallbackDocHandler.CallbackDocRecipeCategory)
  }

  override def registerRecipes(registration: IRecipeRegistration): Unit = {
    registration.addRecipes(ManualUsageHandler.RecipeType, ManualUsageHandler.getRecipes(registration))
    registration.addRecipes(CallbackDocHandler.RecipeType, CallbackDocHandler.getRecipes(registration))
  }

  override def registerGuiHandlers(registration: IGuiHandlerRegistration): Unit =
    registration.addGuiContainerHandler(classOf[Relay], RelayGuiHandler)

  override def registerAdvanced(registration: IAdvancedRegistration): Unit = {
    val guiHelper = registration.getJeiHelpers.getGuiHelper
    ManualUsageHandler.ManualUsageRecipeCategory.initialize(guiHelper)
    CallbackDocHandler.CallbackDocRecipeCategory.initialize(guiHelper)
  }

  private var stackUnderMouse: (AbstractContainerScreen[_], Int, Int) => StackOption = _

  override def onRuntimeAvailable(jeiRuntime: IJeiRuntime): Unit = {
    if (stackUnderMouse == null) {
      ItemSearch.stackFocusing += ((container, mouseX, mouseY) => stackUnderMouse(container, mouseX, mouseY))
    }
    stackUnderMouse = (_, _, _) => StackOption(jeiRuntime.getIngredientListOverlay.getIngredientUnderMouse(VanillaTypes.ITEM_STACK))
    ModJEI.runtime = Option(jeiRuntime)
    ModJEI.ingredientRegistry = Option(jeiRuntime.getIngredientManager)
    val hiddenStacks = ContentVisibility.hiddenItems.asScala.map(new ItemStack(_)).toSeq.asJava
    if (!hiddenStacks.isEmpty) {
      jeiRuntime.getIngredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hiddenStacks)
    }
    Option(Loot.defaultEEPROM).filter(!_.isEmpty).foreach(ModJEI.addItemAtRuntime)
  }

  override def onRuntimeUnavailable(): Unit = {
    ModJEI.runtime = None
    ModJEI.ingredientRegistry = None
  }

  override def registerItemSubtypes(subtypeRegistry: ISubtypeRegistration): Unit = {
    val componentInterpreter = new ISubtypeInterpreter[ItemStack] {
      override def getSubtypeData(stack: ItemStack, ctx: UidContext): Object = stack.getComponentsPatch

      override def getLegacyStringSubtypeInfo(stack: ItemStack, ctx: UidContext): String = ""
    }

    Seq(
      OCBlocks.Microcontroller.asItem(),
      OCBlocks.Robot.asItem(),
      OCItems.EEPROM.asItem(),
      OCItems.Drone.asItem(),
      OCItems.Tablet.asItem()
    ).foreach(subtypeRegistry.registerSubtypeInterpreter(_, componentInterpreter))

    subtypeRegistry.registerSubtypeInterpreter(OCItems.Floppy.asItem(), new ISubtypeInterpreter[ItemStack] {
      override def getSubtypeData(stack: ItemStack, ctx: UidContext): Object = {
        stack.get(OCComponents.LOOT_DISK.get())
      }

      override def getLegacyStringSubtypeInfo(stack: ItemStack, ctx: UidContext): String = {
        val data = getSubtypeData(stack, ctx)
        if (data == null) "" else data.toString
      }
    })
  }
}
