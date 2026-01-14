package server;

import java.io.*;
import java.nio.file.*;

/**
 * サーバー側のファイルシステム操作を担当するクラス。
 * <p>
 * 実際のディスクへの読み書き、ディレクトリ作成、およびパスの解決を行います。
 * <strong>セキュリティ機能:</strong> 指定されたルートディレクトリ以外へのアクセス（ディレクトリトラバーサル）を
 * 防ぐためのパス検証機能を備えています。
 * </p>
 */
public class FileManager {

    /** サーバーがファイルを保存するルートディレクトリ */
    private final Path rootDir;

    /**
     * ルートディレクトリを指定してFileManagerを初期化します。
     * <p>
     * 指定されたディレクトリが存在しない場合、自動的に作成します。
     * </p>
     *
     * @param rootPath ファイル保存先のルートパス（例: "./data"）
     * @throws IOException ディレクトリの作成に失敗した場合
     */
    public FileManager(String rootPath) throws IOException {
        this.rootDir = Paths.get(rootPath);
        if (!Files.exists(rootDir)) {
            Files.createDirectories(rootDir);
        }
    }

    /**
     * クライアントから送られた相対パスを、サーバー上の絶対パスに変換します。
     * <p>
     * <strong>セキュリティ対策:</strong>
     * パスの中に ".." などが含まれていても、正規化(normalize)を行い、
     * 最終的なパスがルートディレクトリ配下にあるかを厳密にチェックします。
     * これにより、ルートディレクトリ外へのアクセス（ディレクトリトラバーサル攻撃）を防ぎます。
     * </p>
     *
     * @param relativePath クライアントが指定したファイルパス
     * @return 検証済みのPathオブジェクト
     * @throws IOException パスがルートディレクトリの外側を指している場合（不正アクセス）
     */
    private Path resolvePath(String relativePath) throws IOException {
        // パスを結合し、".." などを解決してきれいにする
        Path p = rootDir.resolve(relativePath).normalize();
        // 生成されたパスが、設定されたルートディレクトリで始まっているか確認
        if (!p.startsWith(rootDir)) {
            throw new IOException("Invalid path: " + relativePath);
        }
        return p;
    }

    /**
     * 指定されたファイルの最終更新日時を取得します。
     *
     * @param path 対象ファイルパス
     * @return 更新日時のミリ秒 (エポックタイム)。ファイルが存在しない場合は -1L。
     * @throws IOException ディスクアクセスエラーの場合
     */
    public long getLastModified(String path) throws IOException {
        Path p = resolvePath(path);
        if (!Files.exists(p)) {
            return -1L;
        }
        return Files.getLastModifiedTime(p).toMillis();
    }

    /**
     * 指定されたファイルの内容をすべて読み込みます。
     * <p>
     * <strong>注意:</strong> ファイル全体を一度にメモリ（byte配列）に読み込みます。
     * 巨大なファイルを扱う場合は OutOfMemoryError に注意が必要です。
     * </p>
     *
     * @param path 対象ファイルパス
     * @return ファイルのバイトデータ
     * @throws FileNotFoundException ファイルが存在しない場合
     * @throws IOException 読み込みエラーの場合
     */
    public byte[] readFile(String path) throws IOException {
        Path p = resolvePath(path);
        if (!Files.exists(p)) {
            throw new FileNotFoundException("File not found: " + path);
        }
        return Files.readAllBytes(p);
    }

    /**
     * 指定されたパスにデータを書き込みます。
     * <p>
     * 以下の挙動をします：
     * 1. 親ディレクトリが存在しない場合は自動的に作成します。
     * 2. ファイルが既に存在する場合は、中身を完全に上書き（TRUNCATE）します。
     * 3. ファイルが存在しない場合は新規作成します。
     * </p>
     *
     * @param path 対象ファイルパス
     * @param data 書き込むバイトデータ
     * @throws IOException 書き込みエラーの場合
     */
    public void writeFile(String path, byte[] data) throws IOException {
        Path p = resolvePath(path);
        if (p.getParent() != null) {
            Files.createDirectories(p.getParent());
        }
        Files.write(p, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
