package client;

import java.io.*;
import java.nio.file.*;

/**
 * クライアント側のローカルキャッシュ（ファイルシステム）を管理するクラス。
 * <p>
 * サーバーからダウンロードしたファイルを保存したり、
 * ユーザーが編集するためのファイルアクセスインターフェースを提供します。
 * </p>
 */
public class ClientCacheManager {

    /** キャッシュデータを保存するローカルディレクトリ */
    private final Path cacheDir;

    /**
     * 指定されたディレクトリをキャッシュルートとして初期化します。
     * ディレクトリが存在しない場合は作成します。
     *
     * @param cacheDirPath キャッシュディレクトリのパス
     * @throws IOException ディレクトリ作成に失敗した場合
     */
    public ClientCacheManager(String cacheDirPath) throws IOException {
        this.cacheDir = Paths.get(cacheDirPath).toAbsolutePath();
        if (!Files.exists(this.cacheDir)) {
            Files.createDirectories(this.cacheDir);
        }
    }

    /**
     * ローカルキャッシュ内の指定されたファイルの最終更新日時を取得します。
     *
     * @param path ファイルパス
     * @return 最終更新日時（ミリ秒）。ファイルが存在しない場合は -1L。
     */
    public long getLastModified(String path) {
        try {
            Path p = resolvePath(path);
            if (!Files.exists(p)) {
                return -1L;
            }
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return -1L;
        }
    }

    /**
     * サーバーからダウンロードしたデータをキャッシュに保存（上書き）します。
     * <p>
     * open時の同期処理で使用されます。
     * </p>
     *
     * @param path 対象ファイルパス
     * @param data ファイルデータ
     * @throws IOException 書き込みエラー
     */
    public void updateCache(String path, byte[] data) throws IOException {
        Path p = resolvePath(path);
        // 親ディレクトリがない場合は作成
        if (p.getParent() != null) {
            Files.createDirectories(p.getParent());
        }
        // CREATE, TRUNCATE_EXISTING: ファイルを作成し、中身があれば空にしてから書き込む
        Files.write(p, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * キャッシュ内のファイルデータをすべて読み込みます。
     * <p>
     * close時のサーバーへのアップロード（コミット）で使用されます。
     * </p>
     *
     * @param path 対象ファイルパス
     * @return ファイルの全バイトデータ
     * @throws IOException 読み込みエラー
     */
    public byte[] readAllFromCache(String path) throws IOException {
        Path p = resolvePath(path);
        if (!Files.exists(p)) {
            throw new FileNotFoundException("Cache file not found: " + path);
        }
        return Files.readAllBytes(p);
    }

    /**
     * 読み書き操作のためにファイルを開き、RandomAccessFileを返します。
     * <p>
     * これにより、シーク（seek）や部分的な読み書きが可能になります。
     * </p>
     *
     * @param path ファイルパス
     * @param mode "ro"(読み取り専用) または "rw"(読み書き)
     * @return 操作用のRandomAccessFileオブジェクト
     * @throws IOException ファイルが開けない場合
     */
    public RandomAccessFile openFile(String path, String mode) throws IOException {
        Path p = resolvePath(path);

        // ファイルが存在しない場合、"ro"ならエラーだが、"rw"なら空ファイルを作るべき
        // 今回はシンプルにするため、rwでファイルがない場合は作成するロジックを入れる
        // "wo"はRandomAccessFileには存在しないので"wo"の時も"rw"としてしまう。
        // "wo"なのに読めてしまうを防ぐのはDFSFileHandle.javaで制御する
        if (!Files.exists(p)) {
            if ("ro".equals(mode)) {
                 throw new FileNotFoundException("File not found in cache: " + path);
            } else {
                if (p.getParent() != null) {
                    Files.createDirectories(p.getParent());
                }
                Files.createFile(p);
            }
        }

        // RandomAccessFileのモード文字列に変換
        // "r": 読み取りのみ
        // "rw": 読み書き
        String rafMode = "ro".equals(mode) ? "r" : "rw";

        return new RandomAccessFile(p.toFile(), rafMode);
    }

    /**
     * 指定されたパスを削除します（Cleanup用）。
     * @param path 削除するファイルパス
     */
    public void deleteCache(String path) {
        try {
            Files.deleteIfExists(resolvePath(path));
        } catch (IOException e) {
            // 無視（ログ出力程度）
            System.err.println("Failed to delete cache: " + path);
        }
    }

    // --- 内部ヘルパー ---

    /**
     * 相対パスをキャッシュディレクトリ配下の絶対パスに変換し、検証します。
     */
    private Path resolvePath(String relativePath) throws IOException {
        Path p = cacheDir.resolve(relativePath).normalize();
        if (!p.startsWith(cacheDir)) {
            throw new IOException("Invalid path (Directory Traversal): " + relativePath);
        }
        return p;
    }
}
