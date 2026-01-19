package common;

/**
 * クライアントとサーバー間の通信で使用されるコマンド（メッセージタイプ）を定義する列挙型。
 * <p>
 * 各コマンドにはネットワーク転送用のユニークなIDが割り当てられています。
 * 通信パケットのヘッダーに含まれ、リクエストの種類やレスポンスの状態を識別するために使用されます。
 * </p>
 */
public enum Command {
    /** 指定パスのファイルにロック要求 (path, mode) */
    LOCK_REQ(1),
    /** 指定パスファイルの最終更新日時の取得要求 (path) */
    GET_META(2),
    /** ダウンロード要求 (path) */
    GET_FILE(3),
    /** アップロード (path, data) */
    PUT_FILE(4),
    /** ロック解放 (path, mode) */
    UNLOCK(5),

    /** 成功 */
    RES_OK(10),
    /** 失敗 (ロック取得失敗、ファイルが見つからないなど) */
    RES_FAIL(11),
    /** GET_METAに対して更新日時返却 (long timestamp) */
    META_RES(12),
    /** GET_FILEに対してファイルデータ返却 (byte[] data) */
    FILE_RES(13);

    /** コマンドに対応するID値 */
    private final int id;

    /**
     * 指定されたIDを持つCommandを構築します。
     *
     * @param id 通信で使用するコマンドID
     */
    Command(int id) {
        this.id = id;
    }

    /**
     * このコマンドのIDを取得します。
     *
     * @return コマンドID (int)
     */
    public int getId() {
        return id;
    }

    /**
     * ID番号から対応するCommand列挙子を取得します。
     * <p>
     * 受信したパケットのIDを解析する際に使用します。
     * </p>
     *
     * @param id 検索するコマンドID
     * @return 対応するCommand
     * @throws IllegalArgumentException 未知のIDが指定された場合
     */
    public static Command fromId(int id) {
        for (Command c : values()) {
            if (c.id == id) return c;
        }
        throw new IllegalArgumentException("Unknown Command ID: " + id);
    }
}
