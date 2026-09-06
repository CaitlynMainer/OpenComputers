package li.cil.oc.integration.computercraft

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import dan200.computercraft.api.filesystem.WritableMount
import li.cil.oc.api.fs.Mode
import li.cil.oc.server.fs.OutputStreamFileSystem

import java.nio.file.{OpenOption, StandardOpenOption}
import scala.collection.{mutable}
import scala.jdk.CollectionConverters._

class ComputerCraftWritableFileSystem(override val mount: WritableMount)
  extends ComputerCraftFileSystem(mount)
  with OutputStreamFileSystem {

  override def delete(path: String) = try {
    mount.delete(path)
    true
  } catch {
    case _: Throwable => false
  }

  override def makeDirectory(path: String) = try {
    mount.makeDirectory(path)
    true
  } catch {
    case _: Throwable => false
  }

  override protected def openOutputHandle(id: Int, path: String, mode: Mode): Option[OutputHandle] = try {
    val options = mutable.Set[OpenOption](StandardOpenOption.WRITE)
    if (mode.isReadable) options += StandardOpenOption.READ
    if (!mode.requiresExisting) options += StandardOpenOption.CREATE
    if (mode.isTruncate) options += StandardOpenOption.TRUNCATE_EXISTING
    val channel = mount.openFile(path, options.asJava)
    if (mode.isAppend && !mode.isReadable) channel.position(channel.size())
    Some(new ComputerCraftOutputHandle(mount, channel, this, id, path, mode))
  } catch {
    case _: Throwable => None
  }

  protected class ComputerCraftOutputHandle(val mount: WritableMount, val channel: SeekableByteChannel, owner: OutputStreamFileSystem, handle: Int, path: String, initialMode: Mode) extends OutputHandle(owner, handle, path, initialMode) {
    override def length() = channel.size()

    override def position() = channel.position()

    override def close(): Unit = {
      super.close()
      channel.close()
    }

    override def seek(to: Long) = {
      if (to < 0) throw new IOException("invalid offset")
      channel.position(to)
      to
    }

    override def read(value: Array[Byte]) =
      if (mode.isReadable) channel.read(ByteBuffer.wrap(value))
      else throw new IOException("bad file descriptor")

    override def write(value: Array[Byte]) = {
      if (mode.isAppend) channel.position(channel.size())
      val buffer = ByteBuffer.wrap(value)
      while (buffer.hasRemaining) channel.write(buffer)
    }
  }

}
