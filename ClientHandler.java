import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

class ClientHandler implements Runnable {
    private final Socket sock;
    private final InputStream in;
    private final OutputStream out;
    private final Function<String, Path> resolver;
    private final LockManager lockManager;

    // Track locks held by this connection so we can release on CLOSE/disconnect
    private final Set<String> readLocks = new HashSet<>();
    private final Set<String> writeLocks = new HashSet<>();

    ClientHandler(Socket sock, Function<String, Path> resolver, LockManager lockManager) throws IOException {
        this.sock = sock;
        this.resolver = resolver;
        this.lockManager = lockManager;
        this.in = sock.getInputStream();
        this.out = sock.getOutputStream();
    }

    @Override public void run() {
        try (sock) {
            while (true) {
                String cmd = readLine();
                if (cmd == null) break; // disconnected
                switch (cmd) {
                    case "OPEN": handleOpen(); break;
                    case "WRITEBACK": handleWriteback(); break;
                    case "CLOSE": handleClose(); break;
                    default: writeErr("Unknown cmd: " + cmd);
                }
            }
        } catch (IOException e) {
            // connection dropped; fall-through to cleanup
        } finally {
            // Release any remaining locks
            for (String p : readLocks) lockManager.releaseRead(p);
            for (String p : writeLocks) lockManager.releaseWrite(p);
            readLocks.clear();
            writeLocks.clear();
        }
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            baos.write(b);
        }
        if (baos.size() == 0 && b == -1) return null;
        return baos.toString(StandardCharsets.UTF_8);
    }

    private void writeLine(String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.UTF_8));
        out.write('\n');
    }

    private void writeOK() throws IOException { writeLine("OK"); out.flush(); }
    private void writeErr(String msg) throws IOException { writeLine("ERR"); writeLine(msg); out.flush(); }

    private void handleOpen() throws IOException {
        String mode = readLine();
        String pathStr = readLine();
        if (mode == null || pathStr == null) { writeErr("Protocol: OPEN missing fields"); return; }
        mode = mode.trim().toLowerCase(Locale.ROOT);
        boolean isRO = mode.equals("ro");
        boolean isWO = mode.equals("wo");
        boolean isRW = mode.equals("rw");
        if (!(isRO || isWO || isRW)) { writeErr("MODE"); return; }

        Path p;
        try { p = resolver.apply(pathStr); } catch (IOException ex) { writeErr(ex.getMessage()); return; }

        // Acquire lock (blocking as per assignment note)
        if (isRO) {
            lockManager.acquireRead(pathStr);
            readLocks.add(pathStr);
        } else { // WO or RW
            lockManager.acquireWrite(pathStr);
            writeLocks.add(pathStr);
        }

        try {
            if ((isWO || isRW)) {
                // Ensure parent exists so a new file can be created later on WRITEBACK
                Path parent = p.getParent();
                if (parent != null) Files.createDirectories(parent);
            }
            byte[] data = new byte[0];
            if (Files.exists(p)) data = Files.readAllBytes(p);
            else if (isRO) { writeErr("ENOENT"); return; }

            writeLine("OK");
            writeLine(Integer.toString(data.length));
            out.write(data);
            out.flush();
        } catch (IOException e) {
            writeErr("IO: " + e.getMessage());
        }
    }

    private void handleWriteback() throws IOException {
        String pathStr = readLine();
        String lenStr = readLine();
        if (pathStr == null || lenStr == null) { writeErr("Protocol: WRITEBACK missing fields"); return; }
        int len;
        try { len = Integer.parseInt(lenStr.trim()); } catch (NumberFormatException e) { writeErr("Bad length"); return; }

        if (!writeLocks.contains(pathStr)) { writeErr("WRITEBACK without write lock"); return; }

        Path p;
        try { p = resolver.apply(pathStr); } catch (IOException ex) { writeErr(ex.getMessage()); return; }

        byte[] buf = in.readNBytes(len);
        try {
            Path parent = p.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent != null ? parent : p.getParent(), ".dfs-", ".tmp");
            Files.write(tmp, buf);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            writeOK();
        } catch (IOException e) {
            writeErr("IO: " + e.getMessage());
        }
    }

    private void handleClose() throws IOException {
        String pathStr = readLine();
        if (pathStr == null) { writeErr("Protocol: CLOSE missing path"); return; }
        if (writeLocks.remove(pathStr)) lockManager.releaseWrite(pathStr);
        if (readLocks.remove(pathStr)) lockManager.releaseRead(pathStr);
        writeOK();
    }
}
