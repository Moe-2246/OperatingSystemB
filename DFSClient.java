import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class DFSClient implements Closeable {
    private Socket sock;
    private InputStream in;
    private OutputStream out;

    private FileHandler current; // 1ファイルを開いている想定（Shellが複数管理してもOK）

    public DFSClient() {}

    private static String[] parseServer(String servername) {
        // servername 形式: host:port
        String[] hp = servername.split(":", 2);
        if (hp.length != 2) throw new IllegalArgumentException("servername must be host:port");
        return hp;
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            baos.write(b);
        }
        if (baos.size() == 0 && b == -1) throw new EOFException();
        return baos.toString(StandardCharsets.UTF_8);
    }

    private void writeLine(String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.UTF_8));
        out.write('\n');
    }

    public void open(String servername, String filepath, String mode) throws IOException {
        if (current != null) throw new IllegalStateException("Already open; close() first");
        String[] hp = parseServer(servername);
        sock = new Socket(hp[0], Integer.parseInt(hp[1]));
        sock.setTcpNoDelay(true);
        in = sock.getInputStream();
        out = sock.getOutputStream();

        mode = mode.toLowerCase(Locale.ROOT);
        if (!(mode.equals("ro") || mode.equals("wo") || mode.equals("rw")))
            throw new IllegalArgumentException("mode must be ro|wo|rw");

        writeLine("OPEN");
        writeLine(mode);
        writeLine(filepath);
        out.flush();

        String status = readLine();
        if ("ERR".equals(status)) {
            String msg = readLine();
            closeSocketOnly();
            throw new IOException("OPEN failed: " + msg);
        }
        if (!"OK".equals(status)) {
            closeSocketOnly();
            throw new IOException("Protocol desync after OPEN");
        }
        int len = Integer.parseInt(readLine());
        byte[] buf = in.readNBytes(len);
        current = new FileHandler(filepath, mode, buf);
    }

    public String read() {
        ensureOpen();
        return current.readString();
    }

    public void write(String message) {
        ensureOpen();
        current.writeString(message);
    }

    public void append(String message) {
        ensureOpen();
        current.appendString(message);
    }

    public void close() throws IOException {
        ensureOpen();
        // write-back if needed
        if (current.isDirty() && !current.isReadOnly()) {
            writeLine("WRITEBACK");
            writeLine(current.getPath());
            byte[] buf = current.getBytes();
            writeLine(Integer.toString(buf.length));
            out.write(buf);
            out.flush();
            String st = readLine();
            if ("ERR".equals(st)) {
                String msg = readLine();
                throw new IOException("WRITEBACK failed: " + msg);
            }
            if (!"OK".equals(st)) throw new IOException("Protocol desync on WRITEBACK");
            current.clearDirty();
        }
        // close (release lock on server)
        writeLine("CLOSE");
        writeLine(current.getPath());
        out.flush();
        String st = readLine();
        if ("ERR".equals(st)) {
            String msg = readLine();
            throw new IOException("CLOSE failed: " + msg);
        }
        if (!"OK".equals(st)) throw new IOException("Protocol desync on CLOSE");

        current = null;
        closeSocketOnly();
    }

    private void closeSocketOnly() {
        try { if (sock != null) sock.close(); } catch (IOException ignored) {}
        sock = null; in = null; out = null;
    }

    private void ensureOpen() {
        if (current == null) throw new IllegalStateException("No open file");
    }

    @Override public void close() throws IOException {
        if (current != null) close();
        closeSocketOnly();
    }
}
