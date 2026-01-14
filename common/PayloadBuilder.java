package common;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * パケットのペイロード（データ部）を構築・解析するためのユーティリティクラス。
 * <p>
 * 複数のデータ（ファイル名、ファイル本体、モードなど）を単一のバイト配列にシリアライズ（直列化）、
 * およびデシリアライズ（復元）する機能を提供します。
 * </p>
 * <p>
 * <strong>データフォーマット:</strong><br>
 * 可変長データ（文字列やバイナリ）を扱うため、各フィールドの直前に
 * 4バイトの整数（int）でデータ長を付与する形式を採用しています。<br>
 * <code>[長さ(int)][データ(bytes)] ...</code>
 * </p>
 */
public class PayloadBuilder {

    // ==========================================
    // ビルダー (書き込み用)
    // ==========================================

    /**
     * ファイルアップロード(PUT_FILE)用のペイロードを作成します。
     * <p>
     * 構造: [パスの長さ][パス文字列][データの長さ][ファイルデータ]
     * </p>
     *
     * @param path    保存先ファイルパス
     * @param content ファイルの中身
     * @return フォーマット済みのバイト配列
     * @throws IOException ストリーム操作時の例外
     */
    public static byte[] buildPutFilePayload(String path, byte[] content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        writeString(dos, path);    // パス書き込み
        writeBytes(dos, content);  // データ書き込み

        return baos.toByteArray();
    }

    /**
     * ロック要求(LOCK_REQ)用のペイロードを作成します。
     * <p>
     * 構造: [パスの長さ][パス文字列][モードの長さ][モード文字列]
     * </p>
     *
     * @param path 対象ファイルパス
     * @param mode ロックモード ("ro", "rw" 等)
     * @return フォーマット済みのバイト配列
     * @throws IOException ストリーム操作時の例外
     */
    public static byte[] buildLockReqPayload(String path, String mode) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        writeString(dos, path);
        writeString(dos, mode);

        return baos.toByteArray();
    }

    /**
     * UNLOCK（ロック解放）用のペイロードを作成します。
     * <p>
     * 構造: [パスの長さ][パス文字列][モードの長さ][モード文字列]
     * LOCK_REQと同じ構造なので、内部で buildLockReqPayload を再利用します。
     * </p>
     */
    public static byte[] buildUnlockPayload(String path, String mode) throws IOException {
        // 構造が同じなので、既存のメソッドを使って作成します。
        return buildLockReqPayload(path, mode);
    }

    /**
     * 文字列を1つだけ含むペイロードを作成します。
     * <p>
     * 用途: GET_META, GET_FILEなど、パスのみを指定するコマンド用。
     * 構造: [文字列の長さ][文字列]
     * </p>
     *
     * @param text パスなどの文字列
     * @return フォーマット済みのバイト配列
     * @throws IOException ストリーム操作時の例外
     */
    public static byte[] buildStringPayload(String text) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        writeString(dos, text);

        return baos.toByteArray();
    }

    // ==========================================
    // パーサー (読み込み用)
    // ==========================================

    /**
     * PUT_FILE用のペイロードを解析して、パスとデータを取り出します。
     *
     * @param payload 受信したペイロード
     * @return パスとデータを含む {@link PutFileData} オブジェクト
     * @throws IOException データ形式が不正な場合
     */
    public static PutFileData parsePutFilePayload(byte[] payload) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload));

        String path = readString(dis);
        byte[] content = readBytes(dis);

        return new PutFileData(path, content);
    }

    /**
     * LOCK_REQ用のペイロードを解析して、パスとモードを取り出します。
     *
     * @param payload 受信したペイロード (byte[])
     * @return 解析されたパスとモードを含む {@link LockReqData} オブジェクト
     * @throws IOException データ形式が不正な場合
     */
    public static LockReqData parseLockReqPayload(byte[] payload) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload));

        // 内部ヘルパーメソッド readString を再利用して順番に読み出す
        String path = readString(dis);
        String mode = readString(dis);

        return new LockReqData(path, mode);
    }

    /**
     * UNLOCK（ロック解放）用のペイロードを解析します。
     * <p>
     * 構造は {@link #parseLockReqPayload} と完全に同じ（パス + モード）であるため、
     * 内部ロジックまたはメソッドを再利用します。
     * </p>
     * * @param payload 受信したペイロード
     * @return 解析結果（LockReqDataを再利用、あるいは汎用的なクラス名に変更してもOK）
     * @throws IOException データ形式が不正な場合
     */
    public static LockReqData parseUnlockPayload(byte[] payload) throws IOException {
        // 構造が同じなので、既存のメソッドをそのまま呼び出してます
        return parseLockReqPayload(payload);
    }

    /**
     * ペイロードから単一の文字列（パスなど）を取り出します。
     *
     * @param payload 受信したペイロード
     * @return 解析された文字列
     * @throws IOException データ形式が不正な場合
     */
    public static String parseStringPayload(byte[] payload) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload));
        return readString(dis);
    }

    // ==========================================
    // 内部ヘルパーメソッド
    // ==========================================

    private static void writeString(DataOutputStream dos, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        dos.writeInt(bytes.length);
        dos.write(bytes);
    }

    private static void writeBytes(DataOutputStream dos, byte[] bytes) throws IOException {
        dos.writeInt(bytes.length);
        dos.write(bytes);
    }

    private static String readString(DataInputStream dis) throws IOException {
        int len = dis.readInt();
        // セキュリティ対策: 異常に長い文字列が来たら例外を投げる
        if (len < 0 || len > 10_000) {
            throw new IOException("Invalid string length: " + len);
        }
        byte[] bytes = new byte[len];
        dis.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(DataInputStream dis) throws IOException {
        int len = dis.readInt();
        // セキュリティ対策: 巨大すぎるファイルはメモリ溢れを防ぐため制限する
        if (len < 0 || len > 100_000_000) { // 100MB制限
            throw new IOException("Invalid content length: " + len);
        }
        byte[] bytes = new byte[len];
        dis.readFully(bytes);
        return bytes;
    }


    // ==========================================
    // データ保持クラス
    // ==========================================

    /**
     * PUT_FILEコマンドの解析結果を保持するデータクラス。
     */
    public static class PutFileData {
        public final String path;
        public final byte[] content;

        public PutFileData(String path, byte[] content) {
            this.path = path;
            this.content = content;
        }
    }

    /**
     * LOCK_REQ（ロック要求）のペイロードを解析結果を保持するデータクラス。
     */
    public static class LockReqData {
        public final String path;
        public final String mode;

        public LockReqData(String path, String mode) {
            this.path = path;
            this.mode = mode;
        }
    }
}
