package bank.repository.file;

import bank.model.User;
import bank.repository.UserRepository;

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
        return "# username,saltBase64,passwordHashBase64,iterations,mustChangePassword";
    }

    @Override
    synchronized List<List<String>> rows() {
        List<List<String>> rows = new ArrayList<>(users.size());
        for (User u : users.values()) {
            rows.add(List.of(u.username(), u.saltBase64(), u.passwordHashBase64(),
                    Integer.toString(u.iterations()), Boolean.toString(u.mustChangePassword())));
        }
        return rows;
    }

    @Override
    synchronized void clear() {
        users.clear();
    }

    @Override
    synchronized void addRow(List<String> f) {
        requireFieldCount(f, 5);
        User u = new User(f.get(0), f.get(1), f.get(2), Integer.parseInt(f.get(3)), Boolean.parseBoolean(f.get(4)));
        users.put(key(u.username()), u);
    }
}
