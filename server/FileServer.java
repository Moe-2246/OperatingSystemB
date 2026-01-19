package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * ファイル共有システムのサーバー側メインクラス。
 * <p>
 * このクラスはアプリケーションのエントリーポイント（起動地点）です。
 * 指定されたポートでTCP接続を待ち受け（Listen）、クライアントからの接続要求を受け付けます。
 * </p>
 * <p>
 * <strong>アーキテクチャ: Thread-per-Connection</strong><br>
 * クライアントが接続するたびに新しいスレッドを作成し、
 * 実際の処理を {@link ClientHandler} に委譲します。
 * これにより、複数のクライアントが同時にファイルを操作することを可能にしています。
 * </p>
 */
public class FileServer {

    /**
     * デフォルトコンストラクタ。
     */
    public FileServer() {
    }

    /** デフォルトのポート番号 */
    private static final int DEFAULT_PORT = 9000;

    /** デフォルトのファイル保存ルートディレクトリ名 */
    private static final String DEFAULT_ROOT_DIR = "server_storage";

    /**
     * サーバーのメインメソッド。
     * <p>
     * 以下の手順でサーバーを起動します：
     * <ol>
     * <li>ファイル保存用ディレクトリの初期化（存在しない場合は作成）</li>
     * <li>ServerSocketの開設（指定されたポート、またはデフォルト9000）</li>
     * <li>無限ループによる接続待ち受け（accept）</li>
     * <li>接続確立ごとの新規スレッド起動</li>
     * </ol>
     *
     * @param args コマンドライン引数
     *              <ul>
     *              <li>args[0]: ポート番号（オプション、デフォルト: 9000）</li>
     *              <li>args[1]: ファイル保存ディレクトリ（オプション、デフォルト: server_storage）</li>
     *              </ul>
     */
    public static void main(String[] args) {
        // コマンドライン引数の解析
        int port = DEFAULT_PORT;
        String rootDir = DEFAULT_ROOT_DIR;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0]);
                System.err.println("Usage: java server.FileServer [port] [rootDir]");
                System.exit(1);
            }
        }

        if (args.length > 1) {
            rootDir = args[1];
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            ClientHandler.initFileManager(rootDir);
            System.out.println("FileServer started on port " + port);
            System.out.println("Storage directory: " + rootDir);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Client connected: " + socket.getRemoteSocketAddress());
                new Thread(new ClientHandler(socket)).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
