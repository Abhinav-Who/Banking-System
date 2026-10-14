package bank.repository.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Simulates crashes at each stage of the write protocol by leaving files behind by hand. */
class FileStorageTest {

    @TempDir
    Path dir;

    private void seedOldState() throws Exception {
        Files.writeString(dir.resolve("a.csv"), "old-a\n");
        Files.writeString(dir.resolve("b.csv"), "old-b\n");
    }

    @Test
    void writesAllFilesAndLeavesNoTemporaryFiles() throws Exception {
        FileStorage storage = new FileStorage(dir);
        Map<String, List<String>> files = new LinkedHashMap<>();
        files.put("a.csv", List.of("1", "2"));
        files.put("b.csv", List.of("3"));
        storage.write(files);

        assertEquals(List.of("1", "2"), storage.read("a.csv"));
        assertEquals(List.of("3"), storage.read("b.csv"));
        try (var listing = Files.list(dir)) {
            assertTrue(listing.noneMatch(p -> p.toString().endsWith(".tmp") || p.endsWith("commit.journal")));
        }
    }

    @Test
    void crashBeforeCommitRollsBack() throws Exception {
        seedOldState();
        // Crash after staging one file, before the journal was committed.
        Files.writeString(dir.resolve("a.csv.tmp"), "new-a\n");
        Files.writeString(dir.resolve("commit.journal.tmp"), "a.csv\nb.csv\n");

        FileStorage storage = new FileStorage(dir);

        assertEquals(List.of("old-a"), storage.read("a.csv"));
        assertEquals(List.of("old-b"), storage.read("b.csv"));
        assertFalse(Files.exists(dir.resolve("a.csv.tmp")));
        assertFalse(Files.exists(dir.resolve("commit.journal.tmp")));
    }

    @Test
    void crashAfterCommitRollsForward() throws Exception {
        seedOldState();
        // Crash after the commit point, after a.csv was already renamed but before b.csv was.
        Files.writeString(dir.resolve("a.csv"), "new-a\n");
        Files.writeString(dir.resolve("b.csv.tmp"), "new-b\n");
        Files.writeString(dir.resolve("commit.journal"), "a.csv\nb.csv\n");

        FileStorage storage = new FileStorage(dir);

        assertEquals(List.of("new-a"), storage.read("a.csv"));
        assertEquals(List.of("new-b"), storage.read("b.csv"));
        assertFalse(Files.exists(dir.resolve("commit.journal")));
        assertFalse(Files.exists(dir.resolve("b.csv.tmp")));
    }

    @Test
    void missingFileReadsAsEmpty() {
        assertEquals(List.of(), new FileStorage(dir).read("nothing.csv"));
    }

    @Test
    void refusesPathTraversal() {
        FileStorage storage = new FileStorage(dir);
        assertThrows(IllegalArgumentException.class, () -> storage.read("../secret"));
        assertThrows(IllegalArgumentException.class, () -> storage.read("sub/file.csv"));
    }

    @Test
    void damagedDataFileIsReportedNotSilentlyDropped() throws Exception {
        Files.writeString(dir.resolve("accounts.csv"), "# header\n1234567890,SAVINGS,A,999,not-a-number,ACTIVE,2026-01-01T00:00\n");
        FileStorage storage = new FileStorage(dir);
        FileUnitOfWork uow = new FileUnitOfWork(storage);
        var e = assertThrows(bank.repository.PersistenceException.class, () -> new FileAccountRepository(storage, uow));
        assertTrue(e.getMessage().contains("line 2"), e.getMessage());
    }

    @Test
    void csvRoundTripsSpecialCharacters() {
        List<String> fields = List.of("plain", "a,b", "say \"hi\"", "#hash", " padded ", "");
        assertEquals(fields, Csv.split(Csv.join(fields)));
    }
}
