package common;

/**
 * クライアントとサーバー間で送受信されるデータパケットを表すクラス。
 * <p>
 * このクラスは、実行すべき操作を示す {@link Command} と、
 * その操作に関連するバイナリデータ（ペイロード）をカプセル化します。
 * 通信の1単位（1リクエストまたは1レスポンス）として機能します。
 * </p>
 */
public class Packet {
    /** 何の命令か */
    public Command command;
    /** データの中身(不要時は空) */
    public byte[] payload;

    /**
     * コマンドとペイロードを指定してパケットを生成します。
     *
     * @param command 通信の目的を示す {@link Command}
     * @param payload コマンドに付随するデータ (nullであってはなりません)
     */
    public Packet(Command command, byte[] payload) {
        this.command = command;
        this.payload = payload;
    }

    /**
     * ペイロードを持たないパケットを生成します。
     * <p>
     * {@link Command#RES_OK} や {@link Command#RES_FAIL} など、
     * データ本体を伴わないコマンドで使用するためのコンビニエンス・コンストラクタです。
     * ペイロードは長さ0のバイト配列として初期化されます。
     * </p>
     *
     * @param command 通信の目的を示す {@link Command}
     */
    public Packet(Command command) {
        this(command, new byte[0]);
    }
}
