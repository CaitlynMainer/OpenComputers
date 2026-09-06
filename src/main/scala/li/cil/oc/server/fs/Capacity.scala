package li.cil.oc.server.fs

import java.io
import li.cil.oc.Settings
import li.cil.oc.api.fs.Mode
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag

trait Capacity extends OutputStreamFileSystem {
  private var used = computeSize("/")

  // Used when loading data from disk to virtual file systems, to allow
  // exceeding the actual capacity of a file system.
  private var ignoreCapacity = false

  protected def capacity: Long

  // ----------------------------------------------------------------------- //

  override def spaceTotal = capacity

  override def spaceUsed = used

  // ----------------------------------------------------------------------- //

  override def delete(path: String) = {
    val freed = Settings.get.fileCost + size(path)
    if (super.delete(path)) {
      used = math.max(0, used - freed)
      true
    }
    else false
  }

  override def rename(from: String, to: String): Boolean = {
    if (exists(to)) {
      val freed = Settings.get.fileCost + size(to)
      if (super.rename(from, to)) {
        used = math.max(0, used - freed)
        true
      }
      else false
    } else {
      super.rename(from, to)
    }
  }

  override def makeDirectory(path: String) = {
    if (capacity - used < Settings.get.fileCost && !ignoreCapacity) {
      throw new io.IOException("not enough space")
    }
    if (super.makeDirectory(path)) {
      used += Settings.get.fileCost
      true
    }
    else false
  }

  // ----------------------------------------------------------------------- //

  override def close(): Unit = {
    super.close()
    used = computeSize("/")
  }

  // ----------------------------------------------------------------------- //

  override def loadData(nbt: CompoundTag): Unit = {
    try {
      ignoreCapacity = true
      super.loadData(nbt)
    } finally {
      ignoreCapacity = false
    }

    used = computeSize("/")
  }

  override def saveData(nbt: CompoundTag): Unit = {
    super.saveData(nbt)

    // For the tooltip.
    nbt.putLong("capacity.used", used)
  }

  // ----------------------------------------------------------------------- //

  override abstract protected def openOutputHandle(id: Int, path: String, mode: Mode): Option[OutputHandle] = {
    val delta =
      if (exists(path))
        if (mode.isTruncate)
          -size(path).toInt // Overwrite, file gets cleared.
        else
          0 // Append, no immediate changes.
      else
        Settings.get.fileCost // File creation.
    if (capacity - used < delta && !ignoreCapacity) {
      throw new io.IOException("not enough space")
    }
    super.openOutputHandle(id, path, mode) match {
      case None => None
      case Some(stream) =>
        used = math.max(0, used + delta)
        if (mode.isAppend && !mode.isReadable) {
          stream.seek(stream.length())
        }
        Some(new CountingOutputHandle(this, stream))
    }
  }

  // ----------------------------------------------------------------------- //

  private def computeSize(path: String): Long =
    Settings.get.fileCost +
      size(path) +
      (if (isDirectory(path))
        list(path).foldLeft(0L)((acc, child) => acc + computeSize(path + child))
      else 0)

  protected class CountingOutputHandle(override val owner: Capacity, val inner: OutputHandle) extends OutputHandle(inner.owner, inner.handle, inner.path, inner.mode) {
    override def isClosed = inner.isClosed

    override def restoreMode(value: Mode): Unit = {
      super.restoreMode(value)
      inner.restoreMode(value)
    }

    override def length = inner.length

    override def position = inner.position

    override def close() = inner.close()

    override def seek(to: Long) = inner.seek(to)

    override def read(b: Array[Byte]) = inner.read(b)

    override def write(b: Array[Byte]): Unit = {
      val previousLength = inner.length
      val required = math.max(0L, (if (mode.isAppend) previousLength else inner.position) + b.length - previousLength)
      if (owner.capacity - owner.used < required && !ignoreCapacity)
        throw new io.IOException("not enough space")
      inner.write(b)
      owner.used = owner.used + math.max(0L, inner.length - previousLength)
    }
  }

}
