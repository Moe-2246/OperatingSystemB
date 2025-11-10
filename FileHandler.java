import java.io.*;
import java.nio.charset.StandardCharsets;

public class FileHandler {
    private final String path;
    private final String mode; // ro/wo/rw
    private final ByteArrayOutputStream cache;
    private boolean dirty = false;

    public FileHandler(String path, String mode, byte[] initial) {
        this.path = path;
        this.mode = mode;
        this.cache = new ByteArrayOutputStream(Math.max(1024, initial.length));
        try { this.cache.write(initial); } catch (IOException ignored) {}
    }

    public String getPath() { return path; }
    public boolean isReadOnly() { return mode.equals("ro"); }
    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    public synchronized byte[] getBytes() { return cache.toByteArray(); }
    public synchronized String readString() { return new String(getBytes(), StandardCharsets.UTF_8); }

    public synchronized void writeString(String s) {
        if (isReadOnly()) throw new IllegalStateException("opened in ro mode");
        cache.reset();
        try { cache.write(s.getBytes(StandardCharsets.UTF_8)); } catch (IOException ignored) {}
        dirty = true;
    }

    public synchronized void appendString(String s) {
        if (isReadOnly()) throw new IllegalStateException("opened in ro mode");
        try { cache.write(s.getBytes(StandardCharsets.UTF_8)); } catch (IOException ignored) {}
        dirty = true;
    }
}