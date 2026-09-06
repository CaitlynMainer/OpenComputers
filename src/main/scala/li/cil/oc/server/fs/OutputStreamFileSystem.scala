package li.cil.oc.server.fs

import java.io.FileNotFoundException
import java.io.IOException
import li.cil.oc.api
import li.cil.oc.api.fs.Mode
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag

import scala.collection.mutable
import net.minecraft.nbt.Tag

trait OutputStreamFileSystem extends InputStreamFileSystem {
  private val handles = mutable.Map.empty[Int, OutputHandle]

  // ----------------------------------------------------------------------- //

  override def isReadOnly = false

  // ----------------------------------------------------------------------- //

  override def open(path: String, mode: Mode) = this.synchronized(mode match {
    case _ if !mode.isWritable => super.open(path, mode)
    case _ =>
      FileSystem.validatePath(path)
      if (!isDirectory(path) && (!mode.requiresExisting || exists(path))) {
        val handle = Iterator.continually((Math.random() * Int.MaxValue).toInt + 1).filterNot(handles.contains).next()
        openOutputHandle(handle, path, mode) match {
          case Some(fileHandle) =>
            handles += handle -> fileHandle
            handle
          case _ => throw new FileNotFoundException(path)
        }
      } else throw new FileNotFoundException(path)
  })

  override def getHandle(handle: Int): api.fs.Handle = this.synchronized(Option(super.getHandle(handle)).orElse(handles.get(handle)).orNull)

  override def close() = this.synchronized {
    super.close()
    for (handle <- handles.values)
      handle.close()
    handles.clear()
  }

  // ----------------------------------------------------------------------- //

  private final val OutputTag = "output"
  private final val HandleTag = "handle"
  private final val PathTag = "path"
  private final val ModeTag = "mode"
  private final val PositionTag = "position"

  override def loadData(nbt: CompoundTag): Unit = {
    super.loadData(nbt)

    val handlesNbt = nbt.getList(OutputTag, Tag.TAG_COMPOUND)
    (0 until handlesNbt.size).map(handlesNbt.getCompound).foreach(handleNbt => {
      val handle = handleNbt.getInt(HandleTag)
      val path = handleNbt.getString(PathTag)
      val mode = if (handleNbt.contains(ModeTag, Tag.TAG_STRING)) {
        try Mode.valueOf(handleNbt.getString(ModeTag))
        catch { case _: IllegalArgumentException => Mode.Append }
      } else Mode.Append
      val position = if (handleNbt.contains(PositionTag)) handleNbt.getLong(PositionTag) else -1L
      val reopenMode = mode match {
        case Mode.Write => Mode.Append
        case Mode.ReadWriteTruncate => Mode.ReadWrite
        case _ => mode
      }
      openOutputHandle(handle, path, reopenMode) match {
        case Some(fileHandle) =>
          fileHandle.restoreMode(mode)
          if (position >= 0) fileHandle.seek(position)
          handles += handle -> fileHandle
        case _ => // The source file seems to have changed since last time.
      }
    })
  }

  override def saveData(nbt: CompoundTag): Unit = this.synchronized {
    super.saveData(nbt)

    val handlesNbt = new ListTag()
    for (file <- handles.values) {
      assert(!file.isClosed)
      val handleNbt = new CompoundTag()
      handleNbt.putInt(HandleTag, file.handle)
      handleNbt.putString(PathTag, file.path)
      handleNbt.putString(ModeTag, file.mode.name())
      handleNbt.putLong(PositionTag, file.position)
      handlesNbt.add(handleNbt)
    }
    nbt.put(OutputTag, handlesNbt)
  }

  // ----------------------------------------------------------------------- //

  protected def openOutputHandle(id: Int, path: String, mode: Mode): Option[OutputHandle]

  // ----------------------------------------------------------------------- //

  protected abstract class OutputHandle(val owner: OutputStreamFileSystem, val handle: Int, val path: String, var mode: Mode) extends api.fs.Handle {
    protected var _isClosed = false

    def isClosed = _isClosed

    def restoreMode(value: Mode): Unit = mode = value

    override def close() = if (!isClosed) {
      _isClosed = true
      owner.handles -= handle
    }

    override def read(into: Array[Byte]): Int = throw new IOException("bad file descriptor")

    override def seek(to: Long): Long = throw new IOException("bad file descriptor")
  }

}
