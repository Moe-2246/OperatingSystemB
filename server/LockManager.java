package server;

import java.util.HashMap;
import java.util.Map;

/**
 * サーバー上の全ファイルに対するロック管理を行うクラス。
 * <p>
 * ファイルパスごとの {@link FileLockInfo} を保持し、
 * Javaの標準同期機構（wait/notifyAll）を使用してスレッド間の調整を行います。
 * </p>
 * <p>
 * <strong>スレッドセーフ性:</strong>
 * 全てのメソッドは synchronized されており、競合状態を防ぎます。
 * ロックが取得できない場合、呼び出し元のスレッドは待機状態（ブロック）に入ります。
 * </p>
 */
public class LockManager {

    /**
     * デフォルトコンストラクタ。
     */
    public LockManager() {
    }

    /**
     * ファイルパスと、そのファイルのロック情報を紐付けるマップ。
     * キー: ファイルパス (String)
     * 値: ロック情報 (FileLockInfo)
     */
    private final Map<String, FileLockInfo> lockTable = new HashMap<>();

    /**
     * 指定されたファイルのロック取得を試みます。
     * <p>
     * 指定されたモードでロックが取得できるまで、現在のスレッドを待機（ブロック）させます。
     * 待機中はCPUリソースを消費しません。
     * </p>
     *
     * @param path 対象のファイルパス
     * @param mode ロックモード ("ro": 読み取り専用, "wo"/"rw": 書き込み/読み書き)
     * @throws InterruptedException 待機中にスレッドが割り込みを受けた場合
     * @throws IllegalArgumentException 未知のモードが指定された場合
     */
    public synchronized boolean lock(String path, String mode) {
        FileLockInfo info = lockTable.computeIfAbsent(path, p -> new FileLockInfo());

        if (mode.equals("ro")) {
            // --- 読み取りロック要求 ---

            // 書き込み中(writer=true)なら、終わるまで寝て待つ
            while (!info.canRead()) {
                return false;
            }
            // 起きたら（書き込みが終わっていたら）自分のカウントを増やす
            info.acquireRead();
            return true;

        } else if (mode.equals("wo") || mode.equals("rw")) {
            // --- 書き込みロック要求 ---

            while (!info.canWrite()) {
                return false;
            }
            info.acquireWrite();
            return true;

        } else {
            throw new IllegalArgumentException("Unknown lock mode: " + mode);
        }
    }

    /**
     * 指定されたファイルのロックを解放します。
     * <p>
     * ロック状態を更新し、待機中の他のスレッド（lockメソッドで止まっているスレッド）を
     * 全て起床させます。
     * </p>
     *
     * @param path 対象のファイルパス
     * @param mode 解放するロックのモード (取得時と同じモードを指定する必要があります)
     */
    public synchronized void unlock(String path, String mode) {
        FileLockInfo info = lockTable.get(path);
        if (info == null) return;

        if (mode.equals("ro")) {
            info.releaseRead();
        } else if (mode.equals("wo") || mode.equals("rw")) {
            info.releaseWrite();
        }

        // もう誰も使っていなければエントリをMapから削除
        if (info.getReaderCount() == 0 && !info.hasWriter()) {
            lockTable.remove(path);
        }

        //notifyAll(); // 待っているスレッドを起こす
    }
}
