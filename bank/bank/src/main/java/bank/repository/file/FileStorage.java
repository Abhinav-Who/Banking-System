package bank.repository.file;

import bank.repository.PersistenceException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Line-based files in one data folder, written so that a crash can never leave
 * a half-written file or a half-applied multi-file change.
 *
 * <h2>Write protocol</h2>
 * <ol>
 *   <li>Write every new file as {@code name.tmp} and fsync it.</li>
 *   <li>Write the list of names to {@code commit.journal.tmp}, fsync, then atomically rename it to
 *       {@code commit.journal}. <b>This rename is the commit point.</b></li>
 *   <li>Atomically rename each {@code name.tmp} over {@code name}.</li>
 *   <li>Delete the journal.</li>
 * </ol>
 *
 * <h2>Recovery (on startup and before each write)</h2>
 * If a journal exists the change was committed, so the remaining renames are finished (roll forward).
 * Any other {@code .tmp} file belongs to an uncommitted change and is deleted (roll back).
 * Either way the folder ends up holding exactly the old state or exactly the new state.
 */
public class FileStorage {

    static final String JOURNAL = "commit.journal";
    static final String TMP_SUFFIX = ".tmp";
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*");

    private final Path dir;

    public FileStorage(Path directory) {
        this.dir = directory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            recover();
        } catch (IOException e) {
            throw new PersistenceException("Cannot open data folder " + dir + ": " + e.getMessage(), e);
        }
    }

    public Path directory() {
        return dir;
    }

    /** Lines of {@code fileName}, or an empty list if the file does not exist yet. */
    public List<String> read(String fileName) {
        Path path = resolve(fileName);
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PersistenceException("Cannot read " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * Replaces all given files as one atomic change.
     *
     * @throws PersistenceException if the change could not be committed; on-disk data is then unchanged
     */
    public synchronized void write(Map<String, List<String>> files) {
        if (files.isEmpty()) {
            return;
        }
        try {
            // A previous commit whose renames failed must be finished first, otherwise its
            // .tmp files could be overwritten or orphaned by this write.
            completePendingCommit();
        } catch (IOException e) {
            throw new PersistenceException("Could not finish a previous save: " + e.getMessage(), e);
        }

        // Phase 1: stage. Nothing visible has changed if this fails.
        try {
            for (Map.Entry<String, List<String>> entry : files.entrySet()) {
                writeSynced(tmpOf(entry.getKey()), entry.getValue());
            }
            Path journalTmp = resolve(JOURNAL + TMP_SUFFIX);
            writeSynced(journalTmp, new ArrayList<>(files.keySet()));
            moveAtomically(journalTmp, resolve(JOURNAL)); // commit point
            syncDirectory();
        } catch (IOException e) {
            deleteStagedFiles();
            throw new PersistenceException("Could not save data: " + e.getMessage(), e);
        }

        // Phase 2: apply. The change is already durable; a failure here is completed on the next
        // write or the next start, so it must not be reported as a failed operation.
        try {
            completePendingCommit();
        } catch (IOException e) {
            System.err.println("Warning: committed change not fully applied yet, will retry: " + e.getMessage());
        }
    }

    private void recover() throws IOException {
        completePendingCommit();
        deleteStagedFiles();
    }

    private void completePendingCommit() throws IOException {
        Path journal = resolve(JOURNAL);
        if (!Files.exists(journal)) {
            return;
        }
        for (String name : Files.readAllLines(journal, StandardCharsets.UTF_8)) {
            if (name.isBlank()) {
                continue;
            }
            Path tmp = tmpOf(name);
            if (Files.exists(tmp)) {
                moveAtomically(tmp, resolve(name));
            }
        }
        syncDirectory();
        Files.delete(journal);
        syncDirectory();
    }

    private void deleteStagedFiles() {
        try (DirectoryStream<Path> staged = Files.newDirectoryStream(dir, "*" + TMP_SUFFIX)) {
            for (Path p : staged) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            System.err.println("Warning: could not clean up temporary files: " + e.getMessage());
        }
    }

    private Path resolve(String fileName) {
        if (!SAFE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("Unsafe data file name: " + fileName);
        }
        return dir.resolve(fileName);
    }

    private Path tmpOf(String fileName) {
        return resolve(fileName + TMP_SUFFIX);
    }

    private static void writeSynced(Path path, List<String> lines) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        ByteBuffer buffer = ByteBuffer.wrap(sb.toString().getBytes(StandardCharsets.UTF_8));
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void moveAtomically(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Makes renames durable on POSIX systems. Not supported on Windows, where it is skipped. */
    private void syncDirectory() {
        try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Best effort only.
        }
    }
}
