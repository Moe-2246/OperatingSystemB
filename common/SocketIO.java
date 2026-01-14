package common;

import java.io.*;
import java.net.Socket;

/**
 * ソケット通信におけるパケットの送受信を行うユーティリティクラス。
 * <p>
 * {@link Packet} オブジェクトをバイト列に変換して送信し、
 * 受信したバイト列を {@link Packet} オブジェクトに復元する機能を提供します。
 * クライアントとサーバー間で共通の通信プロトコル（データ形式）を強制します。
 * </p>
 * <p>
 * <strong>通信フォーマット:</strong>
 * </p>
 * <pre>
 * [コマンドID (int: 4byte)]
 * [ペイロード長 (int: 4byte)]
 * [ペイロード本体 (byte[]: 可変長)]
 * </pre>
 */
public class SocketIO {

    /**
     * デフォルトコンストラクタ。
     */
    private SocketIO() {
        // ユーティリティクラスのため、インスタンス化を禁止
    }

    /**
     * 指定されたソケットを通じてパケットを送信します。
     * <p>
     * コマンドID、データ長、データ本体の順にストリームへ書き込み、
     * 即座に送信 (flush) します。
     * </p>
     *
     * @param socket 通信相手との接続済みソケット
     * @param packet 送信するパケットデータ
     * @throws IOException 通信エラーが発生した場合（切断など）
     */
    public static void send(Socket socket, Packet packet) throws IOException {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        // 1. コマンドIDを送信 (ヘッダ1)
        out.writeInt(packet.command.getId());
        // 2. ペイロードの長さを送信 (ヘッダ2)
        out.writeInt(packet.payload.length);
        // 3. ペイロードの中身を送信 (ペイロード)
        if (packet.payload.length > 0) {
            out.write(packet.payload);
        }

        out.flush(); // 即時送信
    }

    /**
     * 指定されたソケットからパケットを受信します。
     * <p>
     * データが到着するまでブロック（待機）します。
     * ヘッダ情報を読み取り、指定された長さ分のデータを確実に読み込んでパケットを再構築します。
     * </p>
     *
     * @param socket 通信相手との接続済みソケット
     * @return 受信・復元された {@link Packet} オブジェクト
     * @throws IOException 通信エラー、または切断された場合
     * @throws EOFException 通信の途中で接続が切れた場合
     */
    public static Packet receive(Socket socket) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());

        // 1. コマンドIDを読み取る(データが来るまでここで待機)
        int cmdId = in.readInt();
        Command cmd = Command.fromId(cmdId);
        // 2. ペイロードの長さを読み取る
        int length = in.readInt();
        if (length < 0 || length > 100_000_000) { // 例: 100MB制限
             throw new IOException("Received payload length is invalid or too large: " + length);
        }
        // 3. その長さ分だけバイト配列を読み取る
        byte[] payload = new byte[length];
        if (length > 0) {
            in.readFully(payload); // データが分割されても指定バイト数読み切るまで待機する
        }

        return new Packet(cmd, payload);
    }
}
