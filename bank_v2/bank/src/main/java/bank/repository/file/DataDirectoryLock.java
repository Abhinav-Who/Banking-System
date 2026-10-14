package bank.repository.file;

import bank.repository.PersistenceException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Prevents two copies of the app from using the same data folder at once. Each instance keeps
 * all data in memory and rewrites whole files, so a second instance would silently overwrite
 * the first one's changes.
 */
public final class DataDirectoryLock implements AutoCloseable {

    private static final String LOCK_FILE = "bank.lock";

    private final FileChannel channel;
    private final FileLock lock;

    private DataDirectoryLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static DataDirectoryLock acquire(Path directory) {
        FileChannel channel = null;
        try {
            Files.createDirectories(directory);
            channel = FileChannel.open(directory.resolve(LOCK_FILE),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();
            if (lock == null) {
                throw alreadyRunning(directory);
            }
            return new DataDirectoryLock(channel, lock);
        } catch (OverlappingFileLockException e) {
            closeQuietly(channel);
            throw alreadyRunning(directory);
        } catch (IOException e) {
            closeQuietly(channel);
            throw new PersistenceException("Cannot lock data folder " + directory + ": " + e.getMessage(), e);
        } catch (PersistenceException e) {
            closeQuietly(channel);
            throw e;
        }
    }

    private static PersistenceException alreadyRunning(Path directory) {
        return new PersistenceException("The bank application is already running with data folder "
                + directory.toAbsolutePath() + ". Close the other window first.");
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ignored) {
                // nothing useful to do
            }
        }
    }

    @Override
    public void close() throws IOException {
        lock.release();
        channel.close();
    }
}
