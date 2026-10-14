package bank.repository.file;

import bank.model.User;
import bank.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Users in {@code users.csv} (salted hashes only, never plaintext passwords). */
public final class FileUserRepository extends FileBackedRepository implements UserRepository {

    private final Map<String, User> users = new LinkedHashMap<>();

    public FileUserRepository(FileStorage storage, FileUnitOfWork unitOfWork) {
        super(unitOfWork, "users.csv");
        loadFrom(storage);
    }

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    @Override
    public synchronized Optional<User> findByUsername(String username) {
        return Optional.ofNullable(users.get(key(username)));
    }

    @Override
    public synchronized List<User> findAll() {
        return List.copyOf(users.values());
    }

    @Override
    public void save(User user) {
        unitOfWork.run(() -> {
            synchronized (this) {
                users.put(key(user.username()), user);
                markDirty();
            }
        });
    }

    @Override
    public synchronized int count() {
        return users.size();
    }

    @Override
    String header() {
        return "# username,saltBase64,passwordHashBase64,iterations,mustChangePassword,admin,active,createdAt";
    }

    @Override
    synchronized List<List<String>> rows() {
        List<List<String>> rows = new ArrayList<>(users.size());
        for (User u : users.values()) {
            rows.add(List.of(u.username(), u.saltBase64(), u.passwordHashBase64(),
                    Integer.toString(u.iterations()), Boolean.toString(u.mustChangePassword()),
                    Boolean.toString(u.admin()), Boolean.toString(u.active()),
                    u.createdAt() == null ? "" : u.createdAt().toString()));
        }
        return rows;
    }

    @Override
    synchronized void clear() {
        users.clear();
    }

    @Override
    synchronized void addRow(List<String> f) {
        // 5 fields = file written before multiple users existed. The only user then was the
        // built-in administrator, so such rows load as an active admin.
        requireFieldCount(f, 5, 8);
        boolean legacy = f.size() == 5;
        String createdAt = optional(f, 7);
        User u = new User(f.get(0), f.get(1), f.get(2), Integer.parseInt(f.get(3)), parseBoolean(f.get(4)),
                legacy || parseBoolean(f.get(5)),
                legacy || parseBoolean(f.get(6)),
                createdAt == null ? null : LocalDateTime.parse(createdAt));
        if (users.putIfAbsent(key(u.username()), u) != null) {
            throw new IllegalArgumentException("duplicate username " + u.username());
        }
    }

    private static boolean parseBoolean(String s) {
        return switch (s) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("expected true or false but found '" + s + "'");
        };
    }
}
