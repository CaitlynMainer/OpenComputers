package li.cil.oc.server.fs

import java.io
import java.io.RandomAccessFile
import li.cil.oc.api.fs.Mode
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag

import java.nio.file.{Files, StandardCopyOption}

trait FileOutputStreamFileSystem extends FileInputStreamFileSystem with OutputStreamFileSystem {
  override def spaceTotal = -1

  override def spaceUsed = -1

  // ----------------------------------------------------------------------- //

  override def delete(path: String) = {
    val file = new io.File(root, FileSystem.validatePath(path))
    file == root || file.delete()
  }

  override def makeDirectory(path: String) = new io.File(root, FileSystem.validatePath(path)).mkdir()

  override def rename(from: String, to: String) = {
    try {
      Files.move(new io.File(root, FileSystem.validatePath(from)).toPath, new io.File(root, FileSystem.validatePath(to)).toPath, StandardCopyOption.REPLACE_EXISTING)
      true
    } catch {
      case e: Exception => false
    }
  }

  override def setLastModified(path: String, time: Long) = new io.File(root, FileSystem.validatePath(path)).setLastModified(time)

  // ----------------------------------------------------------------------- //

  override protected def openOutputHandle(id: Int, path: String, mode: Mode): Option[OutputHandle] =
    Some(new FileHandle(new RandomAccessFile(new io.File(root, path), mode match {
      case _ if mode.isWritable => "rw"
      case _ => throw new IllegalArgumentException()
    }), this, id, path, mode))

  // ----------------------------------------------------------------------- //

  override def saveData(nbt: CompoundTag): Unit = {
    super.saveData(nbt)
    root.mkdirs()
    root.setLastModified(System.currentTimeMillis())
  }

  // ----------------------------------------------------------------------- //

  protected class FileHandle(val file: RandomAccessFile, owner: OutputStreamFileSystem, handle: Int, path: String, initialMode: Mode) extends OutputHandle(owner, handle, path, initialMode) {
    if (initialMode.isTruncate) {
      file.setLength(0)
    }

    override def position() = file.getFilePointer

    override def length() = file.length()

    override def close(): Unit = {
      super.close()
      file.close()
    }

    override def seek(to: Long) = {
      if (to < 0) throw new io.IOException("invalid offset")
      file.seek(to)
      to
    }

    override def read(value: Array[Byte]) =
      if (mode.isReadable) file.read(value)
      else throw new io.IOException("bad file descriptor")

    override def write(value: Array[Byte]) = {
      if (mode.isAppend) file.seek(file.length())
      file.write(value)
    }
  }

}
