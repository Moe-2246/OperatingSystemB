#!/bin/bash

SERVER_PORT=8080
STORAGE_DIR="server_storage"
CACHE_A="cache_a"
CACHE_B="cache_b"

PIPE_A="pipe_client_a"
PIPE_B="pipe_client_b"

LOG_DIR="logs"
SERVER_LOG="$LOG_DIR/server.log"
CLIENT_A_LOG="$LOG_DIR/client_a.log"
CLIENT_B_LOG="$LOG_DIR/client_b.log"

echo "=== Test Start ==="

echo "[Setup] Cleaning up..."
rm -rf $STORAGE_DIR $CACHE_A $CACHE_B
rm -f $PIPE_A $PIPE_B
rm -rf $LOG_DIR
mkdir $LOG_DIR
lsof -ti:$SERVER_PORT | xargs -r kill -9

echo "[Setup] Compiling Java files..."
javac -encoding UTF-8 server/*.java client/*.java common/*.java
if [ $? -ne 0 ]; then
    echo "Compilation failed."
    exit 1
fi

mkfifo $PIPE_A
mkfifo $PIPE_B

echo "[Server] Starting Server..."
java server.FileServer $SERVER_PORT $STORAGE_DIR \
    > "$SERVER_LOG" 2>&1 &
SERVER_PID=$!
sleep 2

echo "[Client A] Starting..."
java client.ClientMain $CACHE_A \
    < $PIPE_A > "$CLIENT_A_LOG" 2>&1 &
CLIENT_A_PID=$!

echo "[Client B] Starting..."
java client.ClientMain $CACHE_B \
    < $PIPE_B > "$CLIENT_B_LOG" 2>&1 &
CLIENT_B_PID=$!

exec 3> $PIPE_A
exec 4> $PIPE_B

echo "--- Step: Client A opens file (RW) ---"
echo "open localhost $SERVER_PORT test.txt rw" >&3
sleep 1

echo "--- Step: Client B tries to open file (RW) - Expect ERROR (Not Wait) ---"
echo "open localhost $SERVER_PORT test.txt rw" >&4
sleep 1

echo "--- Step: Client A writes and closes ---"
echo "write Writed by Client A!" >&3
echo "close" >&3
sleep 1

echo "--- Step: Client B opens file again (RW) - Expect SUCCESS ---"
echo "open localhost $SERVER_PORT test.txt rw" >&4
sleep 1

echo "--- Step: Client B reads, seeks, writes, and closes ---"
echo "read" >&4
echo "seek 0" >&4
echo "write Updated by Client B!" >&4
echo "close" >&4
sleep 1

echo "--- Step: Client A opens (RO) and verifies update ---"
echo "open localhost $SERVER_PORT test.txt ro" >&3
sleep 1
echo "read" >&3
echo "close" >&3
sleep 1

echo "--- Cleanup ---"
echo "exit" >&3
echo "exit" >&4

sleep 2
kill $SERVER_PID 2>/dev/null

rm -f $PIPE_A $PIPE_B
exec 3>&-
exec 4>&-

echo "=== Test Finished ==="
echo "Logs:"
echo "  Server   -> $SERVER_LOG"
echo "  Client A -> $CLIENT_A_LOG"
echo "  Client B -> $CLIENT_B_LOG"
