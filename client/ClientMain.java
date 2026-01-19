package client;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * 分散ファイルシステム(DFS)のクライアント用CLIエントリーポイント。
 * <p>
 * コマンドラインから対話形式で以下のコマンドを実行できます。
 * <ul>
 * <li>open [host] [port] [path] [mode] : サーバーに接続してファイルを開く (mode: ro, wo, rw)</li>
 * <li>read [length]         : 開いているファイルから指定バイト数読み込む</li>
 * <li>write [text]          : 開いているファイルにテキストを書き込む</li>
 * <li>seek [position]       : ファイルポインタを移動する</li>
 * <li>close                 : ファイルを閉じる（サーバーへ反映・ロック解除）</li>
 * <li>exit                  : プログラム終了</li>
 * </ul>
 */
public class ClientMain {

    /**
     * デフォルトコンストラクタ。
     */
    public ClientMain() {
    }

    /**
     * クライアントのメインメソッド。
     *
     * @param args コマンドライン引数。第1引数にキャッシュディレクトリを指定可能
     */
    public static void main(String[] args) {
        // 1台のPCで複数クライアントを起動してテストする場合、
        // キャッシュディレクトリが被らないように引数で指定できるようにする
        // 実行例: java client.ClientMain cache_A
        String cacheDir = (args.length > 0) ? args[0] : "client_cache";

        try (Scanner scanner = new Scanner(System.in)) {
            DFSClient client = new DFSClient(cacheDir);
            // 現在オープンしているファイルのハンドル
            DFSFileHandle currentHandle = null;

            System.out.println("=== DFS Client Shell (Cache: " + cacheDir + ") ===");
            System.out.println("Commands: open, read, write, seek, close, exit");

            while (true) {
                System.out.print("> ");
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+");
                String cmd = parts[0].toLowerCase();

                try {
                    switch (cmd) {
                        case "exit":
                            if (currentHandle != null) currentHandle.close();
                            client.disconnect();
                            System.out.println("Bye.");
                            return;

                        case "open":
                            if (parts.length < 5) {
                                System.out.println("Usage: open <host> <port> <path> <mode>");
                            } else {
                                if (currentHandle != null) {
                                    System.out.println("Error: A file is already open. Close it first.");
                                } else {
                                    String host = parts[1];
                                    int port = Integer.parseInt(parts[2]);
                                    String path = parts[3];
                                    String mode = parts[4];
                                    currentHandle = client.open(host, port, path, mode);
                                    System.out.println("File opened successfully.");
                                }
                            }
                            break;

                        case "read":
                            if (currentHandle == null) {
                                System.out.println("Error: No file is open.");
                            } else {
                                int len = (parts.length > 1) ? Integer.parseInt(parts[1]) : 1024;
                                byte[] data = currentHandle.read(len);
                                if (data == null) {
                                    System.out.println("[EOF] End of file reached.");
                                } else {
                                    // デモ用に文字列として表示
                                    String text = new String(data, StandardCharsets.UTF_8);
                                    System.out.println("Read " + data.length + " bytes:");
                                    System.out.println("--------------------------------------------------");
                                    System.out.println(text);
                                    System.out.println("--------------------------------------------------");
                                }
                            }
                            break;

                        case "write":
                            if (currentHandle == null) {
                                System.out.println("Error: No file is open.");
                            } else if (parts.length < 2) {
                                System.out.println("Usage: write <text>");
                            } else {
                                // 空白を含むテキストに対応するため、コマンド部分以降を結合
                                String text = line.substring(line.indexOf(' ') + 1);
                                currentHandle.write(text.getBytes(StandardCharsets.UTF_8));
                                System.out.println("Wrote " + text.length() + " bytes.");
                            }
                            break;

                        case "seek":
                            if (currentHandle == null) {
                                System.out.println("Error: No file is open.");
                            } else if (parts.length < 2) {
                                System.out.println("Usage: seek <pos>");
                            } else {
                                long pos = Long.parseLong(parts[1]);
                                currentHandle.seek(pos);
                                System.out.println("Seeked to position " + pos);
                            }
                            break;

                        case "close":
                            if (currentHandle == null) {
                                System.out.println("Error: No file is open.");
                            } else {
                                currentHandle.close();
                                currentHandle = null;
                                // 成功メッセージはDFSFileHandle内で表示される
                            }
                            break;

                        default:
                            System.out.println("Unknown command: " + cmd);
                            break;
                    }

                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                    // e.printStackTrace(); // デバッグ時は有効化
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
