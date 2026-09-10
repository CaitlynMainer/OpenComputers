package li.cil.oc.client.renderer.tileentity

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import li.cil.oc.client
import li.cil.oc.client.renderer.RenderTypes
import li.cil.oc.common.blockentity.Projector
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.{BlockEntityRenderer, BlockEntityRendererProvider}
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.Direction
import net.minecraft.client.Minecraft
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.{HitResult, Vec3}
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.ai.attributes.Attributes
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.{ClientTickEvent, InputEvent, RenderLevelStageEvent}
import org.joml.{Matrix4f, Vector4f}

import com.google.common.cache.{Cache, CacheBuilder, RemovalNotification}
import java.util.concurrent.TimeUnit
import scala.collection.mutable

object ProjectorRenderer extends BlockEntityRendererProvider[Projector] {
  private val ProjectionWidth = 13.0
  private val ProjectionHeight = 8.125
  private val MaxProjectionDistance = 12.5

  private[tileentity] case class ProjectorHit(t: Double, x: Double, y: Double)

  private val renderedProjectors = mutable.LinkedHashMap.empty[Long, Projector]

  override def create(ctx: BlockEntityRendererProvider.Context): BlockEntityRenderer[Projector] = new ProjectorRenderer()

  private val textures: Cache[Projector, ProjectorTexture] = CacheBuilder.newBuilder()
    .expireAfterAccess(5, TimeUnit.SECONDS)
    .removalListener((notification: RemovalNotification[Projector, ProjectorTexture]) => notification.getValue.close())
    .build[Projector, ProjectorTexture]()

  private[tileentity] def textureFor(projector: Projector): ProjectorTexture =
    textures.get(projector, () => new ProjectorTexture())

  @SubscribeEvent
  def onClientTick(event: ClientTickEvent.Pre): Unit = {
    textures.cleanUp()
  }

  private[tileentity] def markRendered(projector: Projector): Unit =
    renderedProjectors.update(projector.getBlockPos.asLong, projector)

  @SubscribeEvent
  def onRenderLevelStage(event: RenderLevelStageEvent): Unit =
    if (event.getStage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) renderedProjectors.clear()

  /** Open the normal OC screen GUI when the player uses the projected screen. */
  @SubscribeEvent
  def onInteractionKeyMapping(event: InputEvent.InteractionKeyMappingTriggered): Unit = {
    if (!event.isUseItem || event.getHand != InteractionHand.MAIN_HAND || renderedProjectors.isEmpty) return

    val minecraft = Minecraft.getInstance
    val player = minecraft.player
    if (minecraft.level == null || player == null || minecraft.screen != null || minecraft.gameMode == null) return

    val partialTicks = minecraft.getTimer.getGameTimeDeltaTicks
    val start = player.getEyePosition(partialTicks)
    val reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue
    val end = start.add(player.getViewVector(partialTicks).scale(reach))

    renderedProjectors.values
      .filter(projector => projector.getLevel == minecraft.level && !projector.isRemoved && projector.isScreenMode)
      .flatMap(projector => projectedHit(projector, start, end).map(hit => (hit, projector)))
      .toVector
      .sortBy(_._1.t)
      .headOption
      .foreach { case (_, projector) =>
        minecraft.pushGuiLayer(new client.gui.Screen(
          projector.screenBuffer,
          hasMouse = true,
          () => true,
          () => projector.screenBuffer.isRenderingEnabled))
        event.setSwingHand(false)
        event.setCanceled(true)
      }
  }

  private[tileentity] def projectedHit(projector: Projector, start: Vec3, end: Vec3): Option[ProjectorHit] = {
    val stack = new PoseStack()
    val pos = projector.getBlockPos
    stack.translate(pos.getX.toFloat, pos.getY.toFloat, pos.getZ.toFloat)
    stack.translate(0.5, 0.5, 0.5)
    projector.getProjectionDirection match {
      case Direction.WEST  => stack.mulPose(Axis.YP.rotationDegrees(-90))
      case Direction.NORTH => stack.mulPose(Axis.YP.rotationDegrees(180))
      case Direction.EAST  => stack.mulPose(Axis.YP.rotationDegrees(90))
      case _               =>
    }
    stack.translate(0, 0, projectionDistance(projector))

    val inverse = new Matrix4f(stack.last().pose()).invert()
    def transform(value: Vec3): Vector4f =
      inverse.transform(new Vector4f(value.x.toFloat, value.y.toFloat, value.z.toFloat, 1f))

    val a = transform(start)
    val b = transform(end)
    val dz = b.z - a.z
    if (math.abs(dz) < 1.0e-5f) return None

    val t = -a.z / dz
    if (t < 0 || t > 1) return None

    val x = a.x + (b.x - a.x) * t
    val y = a.y + (b.y - a.y) * t
    val planeWidth = ProjectionWidth
    val planeHeight = ProjectionHeight
    val projectionScale = projectionDistance(projector) / MaxProjectionDistance
    val scaledPlaneWidth = planeWidth * projectionScale
    val scaledPlaneHeight = planeHeight * projectionScale
    val renderWidth = math.max(1, projector.screenBuffer.renderWidth)
    val renderHeight = math.max(1, projector.screenBuffer.renderHeight)
    val scale = math.min(scaledPlaneWidth / renderWidth, scaledPlaneHeight / renderHeight)
    val contentWidth = renderWidth * scale
    val contentHeight = renderHeight * scale
    val left = (scaledPlaneWidth - contentWidth) * 0.5
    val top = (scaledPlaneHeight - contentHeight) * 0.5
    val localX = x.toDouble + scaledPlaneWidth * 0.5
    val localY = scaledPlaneHeight * 0.5 - y.toDouble

    if (localX < left || localX >= left + contentWidth || localY < top || localY >= top + contentHeight) None
    else {
      val bufferX = (localX - left) / contentWidth * projector.screenBuffer.getViewportWidth
      val bufferY = (localY - top) / contentHeight * projector.screenBuffer.getViewportHeight
      Some(ProjectorHit(t.toDouble, bufferX, bufferY))
    }
  }

  private[tileentity] def projectionDistance(projector: Projector): Float = {
    val maxProjectionDistance = MaxProjectionDistance
    val rayStartOffset = 0.501
    val surfaceOffset = 0.001
    val blockPos = projector.getBlockPos
    val direction = projector.getProjectionDirection
    val origin = new Vec3(blockPos.getX + 0.5, blockPos.getY + 0.5, blockPos.getZ + 0.5)
    val step = new Vec3(direction.getStepX, direction.getStepY, direction.getStepZ)
    val rayStart = origin.add(step.scale(rayStartOffset))
    val rayEnd = origin.add(step.scale(maxProjectionDistance))
    val hit = projector.getLevel.clip(new ClipContext(
      rayStart,
      rayEnd,
      ClipContext.Block.COLLIDER,
      ClipContext.Fluid.NONE,
      CollisionContext.empty()
    ))

    if (hit.getType == HitResult.Type.BLOCK)
      math.max(rayStartOffset, hit.getLocation.distanceTo(origin) - surfaceOffset).toFloat
    else maxProjectionDistance.toFloat
  }
}

class ProjectorRenderer extends BlockEntityRenderer[Projector] {
  // The projected plane is deliberately much larger than the projector's
  // block-space AABB. Do not let the normal block-entity frustum test make it
  // disappear as the camera approaches or crosses the edge of the plane.
  override def shouldRenderOffScreen(projector: Projector): Boolean = true

  override def getRenderBoundingBox(projector: Projector): AABB = projector.projectionBounds

  override def render(projector: Projector, partialTick: Float, stack: PoseStack, buffer: MultiBufferSource, packedLight: Int, packedOverlay: Int): Unit = {
    if (!projector.isOn) return
    // hasPower belongs to the projector's pixel framebuffer. The native
    // screen emulator has its own power state and must still render when the
    // pixel framebuffer previously ran out of power.
    if (!projector.isScreenMode && !projector.hasPower) return

    stack.pushPose()
    stack.translate(0.5, 0.5, 0.5)
    projector.getProjectionDirection match {
      case Direction.WEST  => stack.mulPose(Axis.YP.rotationDegrees(-90))
      case Direction.NORTH => stack.mulPose(Axis.YP.rotationDegrees(180))
      case Direction.EAST  => stack.mulPose(Axis.YP.rotationDegrees(90))
      case _               =>
    }
    stack.translate(0, 0, ProjectorRenderer.projectionDistance(projector))

    if (projector.isScreenMode) {
      ProjectorRenderer.markRendered(projector)
      renderScreen(projector, stack, buffer)
    }
    else renderPixels(projector, stack, buffer)

    stack.popPose()
  }

  private def renderPixels(projector: Projector, stack: PoseStack, buffer: MultiBufferSource): Unit = {
    val texture = ProjectorRenderer.textureFor(projector)
    texture.update(projector)

    val projectionScale = ProjectorRenderer.projectionDistance(projector) / ProjectorRenderer.MaxProjectionDistance
    val halfWidth = (ProjectorRenderer.ProjectionWidth * projectionScale * 0.5).toFloat
    val halfHeight = (ProjectorRenderer.ProjectionHeight * projectionScale * 0.5).toFloat // 320:200, or 16:10.
    val matrix = stack.last().pose()
    val vertices = buffer.getBuffer(texture.renderType)
    // The projection is viewed from the emitter side, i.e. the reverse side
    // of this plane. Mirror U so framebuffer coordinates retain their
    // documented top-left origin instead of appearing right-to-left.
    vertices.addVertex(matrix, -halfWidth, halfHeight, 0).setColor(255, 255, 255, 255).setUv(1, 0)
    vertices.addVertex(matrix, halfWidth, halfHeight, 0).setColor(255, 255, 255, 255).setUv(0, 0)
    vertices.addVertex(matrix, halfWidth, -halfHeight, 0).setColor(255, 255, 255, 255).setUv(0, 1)
    vertices.addVertex(matrix, -halfWidth, -halfHeight, 0).setColor(255, 255, 255, 255).setUv(1, 1)
  }

  private def renderScreen(projector: Projector, stack: PoseStack, buffer: MultiBufferSource): Unit = {
    if (!projector.screenBuffer.isRenderingEnabled) return

    val projectionScale = ProjectorRenderer.projectionDistance(projector) / ProjectorRenderer.MaxProjectionDistance
    val planeWidth = (ProjectorRenderer.ProjectionWidth * projectionScale).toFloat
    val planeHeight = (ProjectorRenderer.ProjectionHeight * projectionScale).toFloat // 320:200 physical aspect ratio.
    val renderWidth = math.max(1, projector.screenBuffer.renderWidth)
    val renderHeight = math.max(1, projector.screenBuffer.renderHeight)
    val scale = math.min(planeWidth / renderWidth, planeHeight / renderHeight)

    stack.pushPose()
    // TextBuffer rendering uses a top-left origin and grows downwards.
    // The projector is viewed from the emitter side. In that view the
    // renderer's horizontal axis is reversed; flip X while retaining the
    // normal top-to-bottom screen orientation on Y.
    stack.translate(renderWidth * scale * 0.5f, renderHeight * scale * 0.5f, 0)
    stack.scale(-scale, -scale, 1)
    projector.screenBuffer.renderText(stack, buffer)
    stack.popPose()
  }

}

final class ProjectorTexture {
  private val texture = new DynamicTexture(320, 200, false)
  private val name = "oc_projector_" + System.nanoTime()
  private val location: ResourceLocation = net.minecraft.client.Minecraft.getInstance.getTextureManager.register(name, texture)
  val renderType = RenderTypes.createFontTex(texture.getId)

  def close(): Unit = {
    texture.close()
    net.minecraft.client.Minecraft.getInstance.getTextureManager.release(location)
  }

  def update(projector: Projector): Unit = {
    if (!projector.clientPixelsDirty) return
    projector.synchronized {
      val image = texture.getPixels
      for (y <- 0 until projector.height; x <- 0 until projector.width) {
        val color = projector.pixels(x + y * projector.width)
        val a = (color >>> 24) & 0xFF
        val r = (color >>> 16) & 0xFF
        val g = (color >>> 8) & 0xFF
        val b = color & 0xFF
        image.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r)
      }
      texture.upload()
      projector.clientPixelsDirty = false
    }
  }
}
