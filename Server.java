// =============================
// File: Server.java
// =============================
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class Server {
    private final int port;
    private final Path repoRoot;
    private final LockManager lockManager = new LockManager();

    public Server(int port, String repoRoot) {
        this.port = port;
        this.repoRoot = Paths.get(repoRoot).toAbsolutePath().normalize();
    }

    private Path safeResolve(String raw) throws IOException {
        String rel = raw.startsWith("/") ? raw.substring(1) : raw;
        Path p = repoRoot.resolve(rel).normalize();
        if (!p.startsWith(repoRoot)) throw new IOException("Path traversal blocked");
        return p;
    }

    public void start() throws IOException {
        Files.createDirectories(repoRoot);
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("DFS Server listening on port " + port + ", repo=" + repoRoot);
            while (true) {
                Socket s = ss.accept();
                s.setTcpNoDelay(true);
                Thread t = new Thread(new ClientHandler(s, this::safeResolve, lockManager));
                t.setDaemon(true);
                t.start();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java Server <port> <repoRoot>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        new Server(port, args[1]).start();
    }
}
