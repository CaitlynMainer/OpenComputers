package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.client.renderer.markdown.{Document, MarkupFormat}
import li.cil.oc.client.renderer.TextBufferRenderCache
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.{Font, GuiGraphics}

private[markdown] class CodeBlockSegment(val parent: Segment, lines: Seq[String]) extends Segment with InteractiveSegment {
  private val padding = 2
  private val contents = lines.mkString("\n")

  override def nextX(indent: Int, maxWidth: Int, renderer: Font): Int = 0

  override def nextY(indent: Int, maxWidth: Int, renderer: Font): Int =
    math.max(1, lines.size) * Document.lineHeight(renderer) + padding * 2

  override def render(graphics: GuiGraphics, x: Int, y: Int, indent: Int, maxWidth: Int, renderer: Font, mouseX: Int, mouseY: Int): Option[InteractiveSegment] = {
    val height = nextY(indent, maxWidth, renderer)
    graphics.fill(x + indent, y, x + maxWidth, y + height, 0x66000000)
    var currentY = y + padding
    for (line <- if (lines.nonEmpty) lines else Seq("")) {
      TextBufferRenderCache.renderer.generateChars(line.toCharArray)
      TextBufferRenderCache.renderer.drawString(graphics.pose, line, x + indent + padding, currentY)
      currentY += Document.lineHeight(renderer)
    }
    // Code blocks intentionally use one hit area, so clicking anywhere in the
    // block copies the complete unformatted source.
    if (mouseX >= x + indent && mouseY >= y && mouseX <= x + maxWidth && mouseY <= y + height) Some(this) else None
  }

  override def tooltip: Option[String] = Some("oc:gui.Analyzer.CopyToClipboard")

  override def onMouseClick(mouseX: Int, mouseY: Int): Boolean = {
    Minecraft.getInstance.keyboardHandler.setClipboard(contents)
    true
  }

  override def toString(format: MarkupFormat.Value): String = format match {
    case MarkupFormat.Markdown => s"```\n$contents\n```"
    case MarkupFormat.IGWMod => contents
  }

}
