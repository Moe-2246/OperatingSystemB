package client;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * 分散ファイルシステム（DFS）のクライアントAPIクラス。
 * <p>
 * ユーザーはこのクラスのメソッドを通じて、サーバーへの接続やファイルのオープンを行います。
 * <strong>Close-to-Open 一貫性モデル</strong> を実現するための主要なロジック
 * （ロック取得、メタデータ比較、キャッシュ同期）は、このクラスの {@link #open(String, String)} メソッドに集約されています。
 * </p>
 */
public class DFSClient {

    private final ClientCacheManager cacheManager;
    private final NetworkClient networkClient;

    /**
     * 指定されたキャッシュディレクトリを使用するクライアントを生成します。
     *
     * @param cacheDir ローカルキャッシュとして使用するディレクトリパス
     * @throws IOException ディレクトリの作成に失敗した場合
     */
    public DFSClient(String cacheDir) throws IOException {
        this.cacheManager = new ClientCacheManager(cacheDir);
        this.networkClient = new NetworkClient();
    }

    /**
     * DFSサーバーに接続します。
     * <p>
     * ファイル操作を行う前に必ず呼び出す必要があります。
     * </p>
     *
     * @param host サーバーのホスト名またはIPアドレス
     * @param port ポート番号
     * @throws IOException 接続に失敗した場合
     */
    public void connect(String host, int port) throws IOException {
        this.networkClient.connect(host, port);
        System.out.println("[Client] Connected to server " + host + ":" + port);
    }

    /**
     * サーバーとの接続を切断します。
     * すべてのファイル操作を終えた後に呼び出してください。
     *
     * @throws IOException 切断処理に失敗した場合
     */
    public void disconnect() throws IOException {
        if (networkClient.isConnected()) {
            networkClient.disconnect();
            System.out.println("[Client] Disconnected.");
        }
    }

    /**
     * 分散ファイルシステム上のファイルをオープンし、操作用のハンドルを返します。
     * <p>
     * このメソッドは以下の手順で <strong>Close-to-Open 一貫性</strong> を保証します：
     * <ol>
     * <li>サーバーに対して指定モードでのロックを要求します（取得できるまで待機）。</li>
     * <li>サーバー上の最終更新日時（メタデータ）を取得します。</li>
     * <li>ローカルキャッシュの最終更新日時と比較します。</li>
     * <li>サーバー側が新しい、またはローカルにキャッシュがない場合、サーバーから最新ファイルをダウンロードしてキャッシュを更新します。</li>
     * <li>ローカルキャッシュに対するファイル操作ハンドル({@link DFSFileHandle})を生成して返します。</li>
     * </ol>
     * </p>
     *
     * @param path 対象ファイルパス
     * @param mode アクセスモード ("ro", "wo", "rw")
     * @return ファイル操作用ハンドル。使用後は必ず close() してください。
     * @throws IOException 通信エラー、ロック取得失敗、またはファイル操作エラー
     */
    public DFSFileHandle open(String path, String mode) throws IOException {
        if (!networkClient.isConnected()) {
            throw new IOException("Client is not connected to server.");
        }

        System.out.println("[Client] Opening file: " + path + " (" + mode + ")");

        // 1. サーバーへロック要求 (Server Lock)
        // サーバー側の実装により、ロックが空くまでここでブロック（待機）します
        boolean locked = networkClient.requestLock(path, mode);
        if (!locked) {
            throw new IOException("Failed to acquire lock for file: " + path);
        }

        try {
            // 2. 一貫性チェック (Consistency Check)
            // サーバーとローカルの更新日時を比較
            long serverMtime = networkClient.getMetadata(path);
            long localMtime = cacheManager.getLastModified(path);

            System.out.println("  Server Time: " + serverMtime);
            System.out.println("  Local Time:  " + localMtime);

            boolean needDownload = false;

            if (serverMtime == -1) {
                // ケースA: サーバーにファイルが存在しない（新規作成）
                // ダウンロード不要。ローカルに空ファイルが作られることになる。
                System.out.println("  File does not exist on server. (New File)");
            } else {
                if (localMtime == -1) {
                    // ケースB: サーバーにはあるが、ローカルにキャッシュがない
                    needDownload = true;
                    System.out.println("  Cache miss. Downloading...");
                } else if (serverMtime > localMtime) {
                    // ケースC: サーバーの方が新しい（誰かが更新した）
                    needDownload = true;
                    System.out.println("  Cache is stale. Downloading update...");
                } else {
                    // ケースD: ローカルキャッシュが最新（変更なし）
                    System.out.println("  Cache is valid. No download needed.");
                }
            }

            // 3. 必要ならダウンロードしてキャッシュ更新
            if (needDownload) {
                byte[] data = networkClient.downloadFile(path);
                cacheManager.updateCache(path, data);
            }

            // 4. ローカルキャッシュを開いてハンドルを作成
            // (ClientCacheManager内で RandomAccessFile が作られる)
            RandomAccessFile raf = cacheManager.openFile(path, mode);

            // ハンドルを返す
            // ※ close() が呼ばれたときの同期処理に必要な情報を全て渡す
            return new DFSFileHandle(path, mode, raf, networkClient, cacheManager);

        } catch (IOException e) {
            // 途中でエラーが起きた場合、取りっぱなしになったロックを解放しておく必要がある
            try {
                networkClient.requestUnlock(path, mode);
            } catch (IOException ex) {
                // 無視 (元の例外を優先)
            }
            throw e;
        }
    }
}
