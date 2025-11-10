import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class DFSShell {
    public static void main(String[] args) throws Exception {
        System.out.println("DFS Shell. Commands:\n" +
                "open <host> <port> <path> <mode(ro|wo|rw)>\n" +
                "read\nwrite <text>\nappend <text>\nclose\nexit");

        DFSClient client = new DFSClient();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                if (line.startsWith("open ")) {
                    String[] t = line.split(" ", 5);
                    if (t.length < 5) { System.out.println("Usage: open <host> <port> <path> <mode>"); continue; }
                    String server = t[1] + ":" + t[2];
                    String path = t[3];
                    String mode = t[4];
                    client.open(server, path, mode);
                    System.out.println("OPEN ok");
                } else if (line.equals("read")) {
                    System.out.print(client.read());
                    if (!client.read().endsWith("\n")) System.out.println();
                } else if (line.startsWith("write ")) {
                    String payload = line.substring("write ".length());
                    client.write(payload);
                    System.out.println("WRITE cached (dirty)");
                } else if (line.startsWith("append ")) {
                    String payload = line.substring("append ".length());
                    client.append(payload);
                    System.out.println("APPEND cached (dirty)");
                } else if (line.equals("close")) {
                    client.close();
                    System.out.println("CLOSE ok");
                } else if (line.equals("exit")) {
                    try { client.close(); } catch (Exception ignored) {}
                    break;
                } else {
                    System.out.println("Unknown command");
                }
            } catch (IOException ioe) {
                System.out.println("IO error: " + ioe.getMessage());
            } catch (IllegalStateException ise) {
                System.out.println("State error: " + ise.getMessage());
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        System.out.println("Bye.");
    }
}
