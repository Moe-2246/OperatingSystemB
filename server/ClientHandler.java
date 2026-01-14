package server;

import java.io.IOException;
import java.net.Socket;

import common.Command;
import common.Packet;
import common.SocketIO;
import common.PayloadBuilder;
import common.PayloadBuilder.PutFileData;

/**
 * 個々のクライアント接続を処理するハンドラクラス。
 * <p>
 * クライアントごとに1つのスレッドとして実行され（Runnable実装）、
 * ソケットからのリクエスト受信、コマンドの解析、適切なマネージャーへの委譲、
 * そしてレスポンスの送信までの一連のフローを制御します。
 * </p>
 */
public class ClientHandler implements Runnable {

    private Socket socket;

    // 全スレッドで共有するリソース（static）
    private static LockManager lockManager = new LockManager();
    private static FileManager fileManager;

    /**
     * ファイルマネージャーを初期化します。サーバー起動時に一度だけ呼び出してください。
     * @param rootDir サーバーのルートディレクトリパス
     * @throws IOException ディレクトリ作成失敗時など
     */
    public static void initFileManager(String rootDir) throws IOException {
        fileManager = new FileManager(rootDir);
    }

    /**
     * 指定されたソケットを処理するハンドラを生成します。
     *
     * @param socket 接続済みクライアントソケット
     */
    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    /**
     * スレッドのメイン処理。
     * <p>
     * クライアントからのパケットをループで待ち受け、
     * コマンドに応じて処理を振り分けます。
     * 切断されるかエラーが発生するまでループし続けます。
     * </p>
     */
    @Override
    public void run() {
        try {
            while (true) {
                // 1. パケットを受信 (SocketIO利用)
                Packet packet = SocketIO.receive(socket);

                if (packet == null) {
                    System.out.println("Client disconnected: " + socket.getRemoteSocketAddress());
                    break;
                }

                System.out.println("[Server] Received command: " + packet.command);

                // 2. コマンドごとに処理を分岐
                switch (packet.command) {
                    case LOCK_REQ:
                        handleLockReq(packet);
                        break;
                    case GET_META:
                        handleGetMeta(packet);
                        break;
                    case GET_FILE:
                        handleGetFile(packet);
                        break;
                    case PUT_FILE:
                        handlePutFile(packet);
                        break;
                    case UNLOCK:
                        handleUnlock(packet);
                        break;
                    default:
                        System.out.println("[Server] Unknown command: " + packet.command);
                        SocketIO.send(socket, new Packet(Command.RES_FAIL));
                        break;
                }
            }
        } catch (IOException e) {
            System.out.println("Connection closed (IOError): " + socket.getRemoteSocketAddress());
        } finally {
            // 終了処理: ソケットを確実に閉じる
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ==========================================
    // 各コマンドのハンドラメソッド
    // ==========================================

    /**
     * LOCK_REQ (ロック要求) を処理します。
     * ペイロード構造: [path][mode]
     */
    private void handleLockReq(Packet packet) throws IOException {

        // 1. パース処理 (警告が出ていた変数や複雑なdis処理は削除)
        PayloadBuilder.LockReqData data = PayloadBuilder.parseLockReqPayload(packet.payload);

        System.out.println("  [LOCK_REQ] path=" + data.path + " mode=" + data.mode);

        try {
            // 2. ロック取得（取得できるまでブロック待機）
            lockManager.lock(data.path, data.mode);

            System.out.println("  -> LOCK GRANTED");
            SocketIO.send(socket, new Packet(Command.RES_OK));

        } catch (InterruptedException e) {
            // 待機中に割り込みが入った場合
            SocketIO.send(socket, new Packet(Command.RES_FAIL));
        }
    }

    /**
     * GET_META (更新日時確認) を処理します。
     * ペイロード構造: [path]
     */
    private void handleGetMeta(Packet packet) throws IOException {
        String path = PayloadBuilder.parseStringPayload(packet.payload);
        System.out.println("  [GET_META] path=" + path);

        try {
            long lastModified = fileManager.getLastModified(path);
            byte[] payload = longToBytes(lastModified);
            SocketIO.send(socket, new Packet(Command.META_RES, payload));
        } catch (IOException e) {
            SocketIO.send(socket, new Packet(Command.RES_FAIL));
        }
    }

    /**
     * GET_FILE (ダウンロード要求) を処理します。
     * ペイロード構造: [path]
     */
    private void handleGetFile(Packet packet) throws IOException {
        String path = PayloadBuilder.parseStringPayload(packet.payload);
        System.out.println("  [GET_FILE] path=" + path);

        try {
            byte[] data = fileManager.readFile(path);
            // ファイルの中身をペイロードにして返す
            SocketIO.send(socket, new Packet(Command.FILE_RES, data));
        } catch (IOException e) {
            System.out.println("  -> FILE NOT FOUND");
            SocketIO.send(socket, new Packet(Command.RES_FAIL));
        }
    }

    /**
     * PUT_FILE (アップロード要求) を処理します。
     * ペイロード構造: [path][content]
     */
    private void handlePutFile(Packet packet) throws IOException {
        System.out.println("  [PUT_FILE]");

        try {
            PutFileData data = PayloadBuilder.parsePutFilePayload(packet.payload);

            System.out.println("    path=" + data.path + " size=" + data.content.length);

            fileManager.writeFile(data.path, data.content);
            SocketIO.send(socket, new Packet(Command.RES_OK));

        } catch (IOException e) {
            e.printStackTrace();
            SocketIO.send(socket, new Packet(Command.RES_FAIL));
        }
    }

    /**
     * UNLOCK (ロック解放) を処理します。
     * ペイロード構造: [path]
     */
    private void handleUnlock(Packet packet) throws IOException {
        PayloadBuilder.LockReqData data = PayloadBuilder.parseUnlockPayload(packet.payload);

        System.out.println("  [UNLOCK] path=" + data.path + " mode=" + data.mode);

        lockManager.unlock(data.path, data.mode);

        SocketIO.send(socket, new Packet(Command.RES_OK));
    }

    // =========================
    // 補助メソッド
    // =========================

    /**
     * long値を8バイトの配列に変換します。
     * (DataOutputStream.writeLong と同じビッグエンディアン形式)
     */
    private byte[] longToBytes(long value) {
        byte[] buf = new byte[8];
        for (int i = 7; i >= 0; i--) {
            buf[i] = (byte)(value & 0xff);
            value >>= 8;
        }
        return buf;
    }
}
