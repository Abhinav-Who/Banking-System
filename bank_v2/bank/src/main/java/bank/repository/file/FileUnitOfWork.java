package bank.repository.file;

import bank.repository.UnitOfWork;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Runs work against the in-memory repositories, then writes every changed file in one
 * {@link FileStorage#write} call. On any failure all repositories are rolled back to the
 * snapshot taken before the work started, so memory and disk never disagree.
 */
public final class FileUnitOfWork implements UnitOfWork {

    private final FileStorage storage;
    private final List<FileBackedRepository> repositories = new ArrayList<>();
    private int depth;

    public FileUnitOfWork(FileStorage storage) {
        this.storage = storage;
    }

    synchronized void register(FileBackedRepository repository) {
        repositories.add(repository);
    }

    @Override
    public synchronized <T> T execute(Supplier<T> work) {
        if (depth > 0) {
            return work.get(); // join the enclosing unit of work
        }
        Map<FileBackedRepository, List<String>> snapshots = new IdentityHashMap<>();
        for (FileBackedRepository r : repositories) {
            snapshots.put(r, r.serialize());
        }
        depth++;
        try {
            T result = work.get();
            flush();
            return result;
        } catch (RuntimeException | Error e) {
            snapshots.forEach(FileBackedRepository::restore);
            throw e;
        } finally {
            depth--;
            repositories.forEach(FileBackedRepository::clearDirty);
        }
    }

    private void flush() {
        Map<String, List<String>> changed = new LinkedHashMap<>();
        for (FileBackedRepository r : repositories) {
            if (r.isDirty()) {
                changed.put(r.fileName(), r.serialize());
            }
        }
        storage.write(changed);
    }
}
