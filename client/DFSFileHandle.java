package client;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

/**
 * 分散ファイルシステム上のオープンされたファイルを操作するためのハンドルクラス。
 * <p>
 * ユーザーはこのクラスを通じて、ローカルキャッシュに対する読み書き（read/write）を行います。
 * ネットワーク通信やキャッシュの同期ロジックは、このクラスの {@link #close()} メソッド内で
 * 自動的に制御されます（Close-to-Open 一貫性モデル）。
 * </p>
 */
public class DFSFileHandle implements AutoCloseable {

    private final String path;
    private final String mode; // "ro", "wo", "rw"
    private final RandomAccessFile raf;

    // 委譲先のマネージャー
    private final NetworkClient networkClient;
    private final ClientCacheManager cacheManager;

    /**
     * ファイルに変更があったかどうかを追跡するフラグ。
     * write() が呼ばれると true になり、close() 時にアップロードのトリガーとなります。
     */
    private boolean isDirty = false;

    /**
     * 新しいファイルハンドルを生成します（通常は DFSClient から呼び出されます）。
     *
     * @param path ファイルパス
     * @param mode アクセスモード
     * @param raf ローカルキャッシュを開いたRandomAccessFile
     * @param networkClient 通信クライアント
     * @param cacheManager キャッシュマネージャー
     */
    public DFSFileHandle(String path, String mode, RandomAccessFile raf,
                         NetworkClient networkClient, ClientCacheManager cacheManager) {
        this.path = path;
        this.mode = mode;
        this.raf = raf;
        this.networkClient = networkClient;
        this.cacheManager = cacheManager;
    }

    /**
     * 現在のファイルポインタ位置からデータを読み込みます。
     *
     * @param length 読み込む最大バイト数
     * @return 読み込んだデータ。ファイルの終端(EOF)に達している場合は null。
     * @throws IOException 読み込みエラー、または "wo" モードで呼び出した場合
     */
    public byte[] read(int length) throws IOException {
        // モードチェック: Write-Onlyの場合は読み込み禁止
        if ("wo".equals(mode)) {
            throw new IOException("Read operation is not allowed in 'wo' (Write-Only) mode.");
        }

        byte[] buffer = new byte[length];
        int bytesRead = raf.read(buffer);

        if (bytesRead == -1) {
            return null; // EOF
        }

        // 要求サイズより実際に読めたサイズが小さい場合（ファイル末尾など）、
        // 配列のサイズを切り詰めて返す
        if (bytesRead < length) {
            return Arrays.copyOf(buffer, bytesRead);
        }

        return buffer;
    }

    /**
     * 現在のファイルポインタ位置にデータを書き込みます。
     *
     * @param data 書き込むデータ
     * @throws IOException 書き込みエラー、または "ro" モードで呼び出した場合
     */
    public void write(byte[] data) throws IOException {
        // モードチェック: Read-Onlyの場合は書き込み禁止
        if ("ro".equals(mode)) {
            throw new IOException("Write operation is not allowed in 'ro' (Read-Only) mode.");
        }

        raf.write(data);
        this.isDirty = true; // 変更ありとしてマーク
    }

    /**
     * ファイルポインタの位置を変更します（シーク）。
     *
     * @param pos 先頭からのオフセット位置（バイト）
     * @throws IOException シークエラー
     */
    public void seek(long pos) throws IOException {
        raf.seek(pos);
    }

    /**
     * ファイル操作を終了し、リソースを解放します。
     * <p>
     * 以下の処理を順に行います：
     * <ol>
     * <li>ローカルキャッシュファイル(RandomAccessFile)を閉じる。</li>
     * <li>書き込み権限があり、かつ変更が行われている場合(dirty)、サーバーへファイルをアップロードする。</li>
     * <li>サーバーへロック解放(UNLOCK)を要求する。</li>
     * </ol>
     * </p>
     *
     * @throws IOException 通信エラーまたはファイル操作エラー
     */
    @Override
    public void close() throws IOException {
        // 1. まずローカルのリソースを確実に閉じる（バッファをフラッシュさせるため）
        try {
            raf.close();
        } catch (IOException e) {
            // ここで失敗しても、サーバーのロック解放は試みるべきなのでログだけ出して続行するのが一般的だが、
            // 今回はシンプルに例外を投げる
            throw e;
        }

        // 2. 変更があった場合はサーバーにアップロード (Close-to-Open Consistency)
        // "ro" モードでは isDirty が true になることはないはずだが、念のため権限もチェック
        if (isDirty && ("rw".equals(mode) || "wo".equals(mode))) {
            try {
                System.out.println("[Client] Uploading changes to server...");
                // キャッシュマネージャーを使ってローカルファイルを全読み込み
                byte[] allData = cacheManager.readAllFromCache(path);
                // ネットワーククライアントでアップロード
                networkClient.uploadFile(path, allData);
            } catch (IOException e) {
                // アップロード失敗時は、ロック解放前に例外を投げて異常を知らせる
                // (ロックを残して管理者に知らせるか、解放してしまうかはポリシー次第。ここでは例外を優先)
                throw new IOException("Failed to sync file to server: " + e.getMessage(), e);
            }
        }

        // 3. サーバー上のロックを解放
        // アップロードが成功した後（または不要な場合）に実行される
        networkClient.requestUnlock(path, mode);
        System.out.println("[Client] File closed and unlocked: " + path);
    }
}
