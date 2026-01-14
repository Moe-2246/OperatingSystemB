package client;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;

import common.Command;
import common.Packet;
import common.PayloadBuilder;
import common.SocketIO;

/**
 * サーバーとのネットワーク通信を管理するクライアント側通信クラス。
 * <p>
 * 共通ライブラリ（SocketIO, Packet, PayloadBuilder）を使用して、
 * サーバーへのリクエスト送信とレスポンス受信の手順（プロトコル）を隠蔽します。
 * 上位レイヤー（DFSClient）は、このクラスのメソッドを呼び出すだけで通信を行えます。
 * </p>
 */
public class NetworkClient {

    /**
     * デフォルトコンストラクタ。
     */
    public NetworkClient() {
    }

    private Socket socket;

    /**
     * 指定されたホストとポートのサーバーに接続します。
     *
     * @param host サーバーのホスト名またはIPアドレス
     * @param port ポート番号
     * @throws IOException 接続に失敗した場合
     */
    public void connect(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
    }

    /**
     * サーバーとの接続を切断します。
     *
     * @throws IOException 切断処理に失敗した場合
     */
    public void disconnect() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    /**
     * 現在接続中かどうかを確認します。
     * @return 接続中なら true
     */
    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    // ==========================================
    // プロトコル実装メソッド
    // ==========================================

    /**
     * サーバーにロック取得を要求します。
     * <p>
     * サーバー側でロックが取得できるまで待機し、取得できれば成功を返します。
     * </p>
     *
     * @param path ロックするファイルパス
     * @param mode ロックモード ("ro", "wo", "rw")
     * @return ロック取得に成功した場合は true、拒否された場合は false
     * @throws IOException 通信エラー
     */
    public boolean requestLock(String path, String mode) throws IOException {
        // ペイロード作成: [path][mode]
        byte[] payload = PayloadBuilder.buildLockReqPayload(path, mode);

        // 送信: LOCK_REQ
        SocketIO.send(socket, new Packet(Command.LOCK_REQ, payload));

        // 受信待機: RES_OK または RES_FAIL
        Packet res = SocketIO.receive(socket);
        return res.command == Command.RES_OK;
    }

    /**
     * サーバーにロック解放を要求します。
     *
     * @param path 解放するファイルパス
     * @param mode 解放するモード
     * @throws IOException 通信エラーまたは解放失敗時
     */
    public void requestUnlock(String path, String mode) throws IOException {
        // ペイロード作成: [path][mode] (PayloadBuilderの修正を反映)
        byte[] payload = PayloadBuilder.buildUnlockPayload(path, mode);

        // 送信: UNLOCK
        SocketIO.send(socket, new Packet(Command.UNLOCK, payload));

        Packet res = SocketIO.receive(socket);
        if (res.command != Command.RES_OK) {
            throw new IOException("Failed to unlock file on server.");
        }
    }

    /**
     * サーバー上のファイルの最終更新日時を問い合わせます。
     *
     * @param path ファイルパス
     * @return サーバー上の更新日時（ミリ秒）。ファイルが存在しない場合は -1L。
     * @throws IOException 通信エラー
     */
    public long getMetadata(String path) throws IOException {
        // ペイロード作成: [path]
        byte[] payload = PayloadBuilder.buildStringPayload(path);

        // 送信: GET_META
        SocketIO.send(socket, new Packet(Command.GET_META, payload));

        // 受信
        Packet res = SocketIO.receive(socket);

        if (res.command == Command.META_RES) {
            // ペイロード(byte[])をlongに変換
            return bytesToLong(res.payload);
        } else if (res.command == Command.RES_FAIL) {
            // ファイル取得失敗などの場合
            return -1L; // あるいは例外を投げても良い
        } else {
            throw new IOException("Unexpected response for GET_META: " + res.command);
        }
    }

    /**
     * サーバーからファイルをダウンロードします。
     *
     * @param path ファイルパス
     * @return ファイルのバイトデータ
     * @throws IOException ファイルが見つからない、または通信エラー
     */
    public byte[] downloadFile(String path) throws IOException {
        // ペイロード作成: [path]
        byte[] payload = PayloadBuilder.buildStringPayload(path);

        // 送信: GET_FILE
        SocketIO.send(socket, new Packet(Command.GET_FILE, payload));

        // 受信
        Packet res = SocketIO.receive(socket);

        if (res.command == Command.FILE_RES) {
            return res.payload;
        } else if (res.command == Command.RES_FAIL) {
            throw new FileNotFoundException("File not found on server: " + path);
        } else {
            throw new IOException("Unexpected response for GET_FILE: " + res.command);
        }
    }

    /**
     * サーバーへファイルをアップロード（上書き）します。
     *
     * @param path ファイルパス
     * @param data アップロードするデータ
     * @throws IOException アップロード失敗時
     */
    public void uploadFile(String path, byte[] data) throws IOException {
        // ペイロード作成: [path][data]
        byte[] payload = PayloadBuilder.buildPutFilePayload(path, data);

        // 送信: PUT_FILE
        SocketIO.send(socket, new Packet(Command.PUT_FILE, payload));

        // 受信
        Packet res = SocketIO.receive(socket);
        if (res.command != Command.RES_OK) {
             throw new IOException("Failed to upload file: " + path);
        }
    }

    // ==========================================
    // 内部ヘルパー
    // ==========================================

    /**
     * 8バイトのバイト配列をlong値に変換します（Big Endian）。
     */
    private long bytesToLong(byte[] bytes) {
        // ByteBufferはデフォルトでBig Endianなのでそのまま使用可能
        // サーバー側の実装（手動シフト演算）もBig Endian仕様です
        return ByteBuffer.wrap(bytes).getLong();
    }
}
