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

    /** サーバーが待ち受けるポート番号 (TCP) */
    private static final int PORT = 9000;

    /** * サーバー側のファイル保存ルートディレクトリ名。
     * カレントディレクトリ直下に作成されます。
     */
    private static final String ROOT_DIR = "server_storage";

    /**
     * サーバーのメインメソッド。
     * <p>
     * 以下の手順でサーバーを起動します：
     * <ol>
     * <li>ファイル保存用ディレクトリの初期化（存在しない場合は作成）</li>
     * <li>ServerSocketの開設（ポート9000）</li>
     * <li>無限ループによる接続待ち受け（accept）</li>
     * <li>接続確立ごとの新規スレッド起動</li>
     * </ol>
     *
     * @param args コマンドライン引数（現在は使用していません）
     */
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            ClientHandler.initFileManager(ROOT_DIR);
            System.out.println("FileServer started on port " + PORT);

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
