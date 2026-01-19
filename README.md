<<<<<<< HEAD
# OperatingSystemB

テスト
=======
# OS-B - 分散ファイルシステム (DFS)

分散ファイルシステムの実装課題。複数のクライアントがサーバー上のファイルを共有し、**Close-to-Open一貫性モデル**を実現。

## 目次

- [概要](#概要)
- [アーキテクチャ](#アーキテクチャ)
- [プロジェクト構造](#プロジェクト構造)
- [機能](#機能)
- [ビルドと実行](#ビルドと実行)
- [使用方法](#使用方法)
- [プロトコル仕様](#プロトコル仕様)
- [クラス一覧](#クラス一覧)
- [ドキュメント](#ドキュメント)

## 概要

このプロジェクトは、クライアント・サーバー型の分散ファイルシステムを実装しています。主な特徴は以下の通りです：

- **Close-to-Open一貫性**: ファイルを開く際にサーバーとローカルキャッシュの更新日時を比較し、必要に応じて同期
- **ロック機構**: 読み取りロック（共有）と書き込みロック（排他）をサポート
- **ローカルキャッシュ**: クライアント側でファイルをキャッシュし、オフライン操作を可能に
- **マルチクライアント対応**: 複数のクライアントが同時にファイルを操作可能

## アーキテクチャ

### システム構成

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│  Client A   │         │  Client B   │         │  Client C   │
│             │         │             │         │             │
│ DFSClient   │         │ DFSClient   │         │ DFSClient   │
│ NetworkClient│        │ NetworkClient│        │ NetworkClient│
└──────┬──────┘         └──────┬──────┘         └──────┬──────┘
       │                       │                       │
       └───────────────────────┼───────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │     FileServer       │
                    │                      │
                    │  ClientHandler       │
                    │  LockManager         │
                    │  FileManager         │
                    └──────────────────────┘
```

### 通信フロー

1. **ファイルオープン時**:
   - クライアントがサーバーに接続（必要に応じて）
   - クライアントがサーバーにロック要求（LOCK_REQ）
   - サーバーがロックを取得（取得できるまで待機）
   - クライアントがメタデータを取得（GET_META）
   - ローカルキャッシュと比較し、必要ならダウンロード（GET_FILE）

2. **ファイルクローズ時**:
   - 書き込みがあった場合、サーバーにアップロード（PUT_FILE）
   - サーバーにロック解放要求（UNLOCK）

## プロジェクト構造

```
OperatingSystemB/
├── client/              # クライアント側の実装
│   ├── DFSClient.java           # メインクライアントAPI
│   ├── DFSFileHandle.java       # ファイル操作ハンドル
│   ├── NetworkClient.java       # ネットワーク通信層
│   ├── ClientCacheManager.java  # ローカルキャッシュ管理
│   └── ClientMain.java          # CLIエントリーポイント
│
├── server/              # サーバー側の実装
│   ├── FileServer.java          # サーバーメインクラス
│   ├── ClientHandler.java       # クライアント接続ハンドラ
│   ├── LockManager.java         # ロック管理
│   ├── FileLockInfo.java        # ファイル単位のロック情報
│   └── FileManager.java         # ファイルシステム操作
│
├── common/              # 共通ライブラリ
│   ├── Command.java            # 通信コマンド定義
│   ├── Packet.java              # パケット構造
│   ├── PayloadBuilder.java      # ペイロード構築・解析
│   └── SocketIO.java            # ソケット通信ユーティリティ
│
└── doc/                 # Javadocドキュメント

```

## 機能

### ロックモード

- **`ro`** (Read-Only): 読み取り専用ロック（共有ロック）
  - 複数のクライアントが同時に読み取り可能
  - 書き込みロックとは排他

- **`wo`** (Write-Only): 書き込み専用ロック（排他ロック）
  - 他のクライアントは読み取りも書き込みも不可
  - 読み取り操作は禁止

- **`rw`** (Read-Write): 読み書きロック（排他ロック）
  - 他のクライアントは読み取りも書き込みも不可
  - 読み取りと書き込みの両方が可能

### Close-to-Open一貫性

ファイルを開く際に以下の処理を実行：

1. **ロック取得**: 指定されたモードでロックを取得（取得できるまで待機）
2. **メタデータ取得**: サーバー上の最終更新日時を取得
3. **一貫性チェック**: ローカルキャッシュの更新日時と比較
4. **同期**: サーバーが新しい、またはローカルにキャッシュがない場合、ダウンロード

ファイルを閉じる際に：

1. **ローカルファイルを閉じる**: RandomAccessFileをクローズ
2. **変更があればアップロード**: 書き込みモードで変更があった場合、サーバーにアップロード
3. **ロック解放**: サーバーにロック解放を要求

## ビルドと実行

### コンパイル

```bash
javac server/*.java client/*.java common/*.java
```

### 実行

#### サーバーの起動

```bash
# デフォルト設定（ポート9000、ディレクトリserver_storage）で起動
java server.FileServer

# ポート番号を指定
java server.FileServer 8080

# ポート番号と保存ディレクトリを指定
java server.FileServer 8080 my_storage
```

サーバーは指定されたポート（デフォルト: 9000）で待ち受けます。ファイルは指定されたディレクトリ（デフォルト: `server_storage/`）に保存されます。

#### クライアントの起動

```bash
# デフォルトのキャッシュディレクトリ（client_cache）を使用
java client.ClientMain

# カスタムキャッシュディレクトリを指定
java client.ClientMain cache_A
```

複数のクライアントを起動する場合は、異なるキャッシュディレクトリを指定してください。

## 使用方法

### CLIコマンド

クライアント起動後、以下のコマンドが使用できます：

```
open <host> <port> <path> <mode>  # サーバーに接続してファイルを開く (mode: ro, wo, rw)
read [length]                      # ファイルから読み込む（デフォルト: 1024バイト）
write <text>                       # ファイルに書き込む
seek <position>                    # ファイルポインタを移動
close                              # ファイルを閉じる（サーバーへ反映・ロック解除）
exit                               # プログラム終了
```

### 使用例

```bash
# サーバーに接続してファイルを読み書きモードで開く
> open localhost 9000 test.txt rw
[Client] Connected to server localhost:9000
[Client] Opening file: test.txt (rw)
  Server Time: 1234567890
  Local Time:  -1
  Cache miss. Downloading...
File opened successfully.

# ファイルに書き込む
> write Hello, World!
Wrote 13 bytes.

# ファイルポインタを先頭に移動
> seek 0
Seeked to position 0

# ファイルを読み込む
> read 100
Read 13 bytes:
--------------------------------------------------
Hello, World!
--------------------------------------------------

# ファイルを閉じる（サーバーへ反映）
> close
[Client] Uploading changes to server...
[Client] File closed and unlocked: test.txt

# 切断
> exit
Bye.
```

## プロトコル仕様

### パケット構造

```
[コマンドID (int: 4byte)]
[ペイロード長 (int: 4byte)]
[ペイロード本体 (byte[]: 可変長)]
```

### コマンド一覧

#### クライアント → サーバー

| コマンド | ID | 説明 | ペイロード |
|---------|----|----|-----------|
| LOCK_REQ | 1 | ロック要求 | [path][mode] |
| GET_META | 2 | 更新日時取得 | [path] |
| GET_FILE | 3 | ファイルダウンロード | [path] |
| PUT_FILE | 4 | ファイルアップロード | [path][data] |
| UNLOCK | 5 | ロック解放 | [path][mode] |

#### サーバー → クライアント

| コマンド | ID | 説明 | ペイロード |
|---------|----|----|-----------|
| RES_OK | 10 | 成功 | (空) |
| RES_FAIL | 11 | 失敗 | (空) |
| META_RES | 12 | 更新日時返却 | [timestamp (long)] |
| FILE_RES | 13 | ファイルデータ返却 | [data] |

### ペイロードフォーマット

可変長データは以下の形式でエンコードされます：

```
[データ長 (int: 4byte)][データ本体 (byte[])]
```

例：`LOCK_REQ`のペイロード
```
[path長 (4byte)][path文字列][mode長 (4byte)][mode文字列]
```

## クラス一覧

### クライアント側 (`client`パッケージ)

- **`DFSClient`**: 分散ファイルシステムのメインAPIクラス
- **`DFSFileHandle`**: オープンされたファイルの操作ハンドル
- **`NetworkClient`**: サーバーとのネットワーク通信を管理
- **`ClientCacheManager`**: ローカルキャッシュの管理
- **`ClientMain`**: コマンドラインインターフェース

### サーバー側 (`server`パッケージ)

- **`FileServer`**: サーバーのエントリーポイント
- **`ClientHandler`**: 各クライアント接続を処理するハンドラ
- **`LockManager`**: ファイルロックの管理（スレッドセーフ）
- **`FileLockInfo`**: 単一ファイルのロック状態
- **`FileManager`**: ファイルシステム操作とパス検証

### 共通ライブラリ (`common`パッケージ)

- **`Command`**: 通信コマンドの列挙型
- **`Packet`**: パケットデータ構造
- **`PayloadBuilder`**: ペイロードの構築・解析ユーティリティ
- **`SocketIO`**: ソケット通信の送受信ユーティリティ

## ドキュメント

詳細なAPIドキュメントは、Javadocで生成できます：

```bash
javadoc -d doc common/*.java server/*.java client/*.java
```

生成されたドキュメントは `doc/index.html` で確認できます。

## 注意事項

- サーバーは複数のクライアント接続を同時に処理できますが、各接続は独立したスレッドで処理されます
- ファイルパスにはディレクトリトラバーサル攻撃を防ぐための検証が実装されています
- 大きなファイル（100MB以上）の転送は制限されています
- ロックはサーバー側で管理され、クライアントが切断されても自動的に解放されません（実装の簡略化のため）
>>>>>>> origin
