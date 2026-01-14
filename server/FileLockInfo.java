package server;

/**
 * 単一ファイルに対するロック状態（読み取り・書き込み）を管理するクラス。
 * <strong>注意:</strong>
 * このクラス自体はスレッドセーフではありません（同期化されていません）。
 * マルチスレッド環境（サーバー）で使用する場合は、呼び出し元で {@code synchronized} ブロックを使用するなど、
 * 適切な排他制御を行う必要があります。
 * </p>
 */
public class FileLockInfo {

    /**
     * 現在読み取りロックを取得しているクライアントの数。
     * 0の場合は誰も読み取っていないことを示す。
     */
    private int readerCount = 0;

    /**
     * 現在書き込みロックが取得されているかどうか。
     * trueの場合、排他的なロックがかかっている状態。
     */
    private boolean writer = false;

    /**
     * 読み取りロックが取得可能か判定します。
     * <p>
     * 書き込みが行われていなければ（writerがfalseなら）読み取り可能です。
     * </p>
     *
     * @return 読み取り可能な場合は true
     */
    public boolean canRead() {
        return !writer;
    }

    /**
     * 書き込みロックが取得可能か判定します。
     * <p>
     * 他に書き込んでいる人がおらず、かつ読んでいる人もいない場合のみ書き込み可能です。
     * </p>
     *
     * @return 書き込み可能な場合は true
     */
    public boolean canWrite() {
        return !writer && readerCount == 0;
    }

    /**
     * 読み取りロックを取得状態にします。
     * <p>
     * 内部のリーダーカウントをインクリメントします。
     * 事前に {@link #canRead()} で確認してから呼び出すことを想定しています。
     * </p>
     */
    public void acquireRead() {
        readerCount++;
    }

    /**
     * 書き込みロックを取得状態にします。
     * <p>
     * 内部のライターフラグを立てます。
     * 事前に {@link #canWrite()} で確認してから呼び出すことを想定しています。
     * </p>
     */
    public void acquireWrite() {
        writer = true;
    }

    /**
     * 読み取りロックを解放します。
     * <p>
     * 内部のリーダーカウントをデクリメントします。
     * カウントが0の場合は何もしません。
     * </p>
     */
    public void releaseRead() {
        if (readerCount > 0) {
            readerCount--;
        }
    }

    /**
     * 書き込みロックを解放します。
     * <p>
     * 内部のライターフラグを下ろします。
     * </p>
     */
    public void releaseWrite() {
        writer = false;
    }

    /**
     * 現在の読み取りロック保持数を取得します。
     *
     * @return 読み取り中のクライアント数
     */
    public int getReaderCount() {
        return readerCount;
    }

    /**
     * 現在書き込みロック中かどうかを取得します。
     *
     * @return 書き込み中の場合は true
     */
    public boolean hasWriter() {
        return writer;
    }
}
