import java.util.concurrent.*;
import java.util.concurrent.locks.*;

class LockManager {
    private final ConcurrentHashMap<String, ReadWriteLock> table = new ConcurrentHashMap<>();

    private ReadWriteLock lockFor(String path) {
        return table.computeIfAbsent(path, k -> new ReentrantReadWriteLock(true)); // fair lock
    }

    // Blocking semantics (assignment方針): 空くまで待つ
    public void acquireRead(String path) {
        lockFor(path).readLock().lock();
    }
    public void acquireWrite(String path) {
        lockFor(path).writeLock().lock();
    }
    public void releaseRead(String path) {
        lockFor(path).readLock().unlock();
    }
    public void releaseWrite(String path) {
        lockFor(path).writeLock().unlock();
    }

    // 非ブロッキングにしたい場合：tryLock() を返すメソッドを別途用意すればOK
}
