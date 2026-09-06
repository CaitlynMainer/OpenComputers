package li.cil.oc.api.fs;

/**
 * Possible file modes.
 * <br>
 * This is used when opening files from a {@link FileSystem}.
 */
public enum Mode {
    /**
     * Open a file in reading mode.
     */
    Read(true, false, false, false, true),

    /**
     * Open a file in writing mode, overwriting existing contents.
     */
    Write(false, true, true, false, false),

    /**
     * Open a file in append mode, writing new data after existing contents.
     */
    Append(false, true, false, true, false),

    /**
     * Open an existing file for reading and writing.
     */
    ReadWrite(true, true, false, false, true),

    /**
     * Open a file for reading and writing, overwriting existing contents.
     */
    ReadWriteTruncate(true, true, true, false, false),

    /**
     * Open a file for reading and appending, creating it if necessary.
     */
    ReadAppend(true, true, false, true, false);

    private final boolean readable;
    private final boolean writable;
    private final boolean truncate;
    private final boolean append;
    private final boolean requiresExisting;

    Mode(final boolean readable, final boolean writable, final boolean truncate, final boolean append, final boolean requiresExisting) {
        this.readable = readable;
        this.writable = writable;
        this.truncate = truncate;
        this.append = append;
        this.requiresExisting = requiresExisting;
    }

    public boolean isReadable() {
        return readable;
    }

    public boolean isWritable() {
        return writable;
    }

    public boolean isTruncate() {
        return truncate;
    }

    public boolean isAppend() {
        return append;
    }

    public boolean requiresExisting() {
        return requiresExisting;
    }
}
