package bank.repository.file;

import bank.repository.PersistenceException;

import java.util.ArrayList;
import java.util.List;

/**
 * Base for repositories that keep their data in memory and persist it as one CSV file.
 * Every change goes through the shared {@link FileUnitOfWork}, which decides when the
 * file is written and restores memory if the write fails.
 */
abstract class FileBackedRepository {

    private final String fileName;
    protected final FileUnitOfWork unitOfWork;
    private boolean dirty;

    FileBackedRepository(FileUnitOfWork unitOfWork, String fileName) {
        this.unitOfWork = unitOfWork;
        this.fileName = fileName;
        unitOfWork.register(this);
    }

    /** Header line written at the top of the file (starts with '#'). */
    abstract String header();

    /** Current contents as CSV rows. */
    abstract List<List<String>> rows();

    abstract void clear();

    /** Adds one parsed row. May throw any RuntimeException on malformed input. */
    abstract void addRow(List<String> fields);

    final String fileName() {
        return fileName;
    }

    final void loadFrom(FileStorage storage) {
        restore(storage.read(fileName));
    }

    final List<String> serialize() {
        List<List<String>> rows = rows();
        List<String> lines = new ArrayList<>(rows.size() + 1);
        lines.add(header());
        for (List<String> row : rows) {
            lines.add(Csv.join(row));
        }
        return lines;
    }

    final void restore(List<String> lines) {
        clear();
        int lineNumber = 0;
        for (String line : lines) {
            lineNumber++;
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            try {
                addRow(Csv.split(line));
            } catch (RuntimeException e) {
                clear();
                throw new PersistenceException("Data file " + fileName + " is damaged at line " + lineNumber
                        + " (" + e.getMessage() + "). Restore it from a backup or fix the line by hand.", e);
            }
        }
    }

    static void requireFieldCount(List<String> fields, int expected) {
        requireFieldCount(fields, expected, expected);
    }

    /** Accepts older rows that predate newer trailing columns. */
    static void requireFieldCount(List<String> fields, int min, int max) {
        if (fields.size() < min || fields.size() > max) {
            String expected = min == max ? Integer.toString(min) : min + " to " + max;
            throw new IllegalArgumentException("expected " + expected + " fields but found " + fields.size());
        }
    }

    /** Field {@code index}, or null if the row is shorter or the field is empty. */
    static String optional(List<String> fields, int index) {
        return index < fields.size() && !fields.get(index).isEmpty() ? fields.get(index) : null;
    }

    final void markDirty() {
        dirty = true;
    }

    final boolean isDirty() {
        return dirty;
    }

    final void clearDirty() {
        dirty = false;
    }
}
