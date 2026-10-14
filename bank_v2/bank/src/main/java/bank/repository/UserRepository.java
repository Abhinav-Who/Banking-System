package bank.repository;

import bank.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    /** Usernames are matched case-insensitively. */
    Optional<User> findByUsername(String username);

    /** All users, in the order they were created. */
    List<User> findAll();

    void save(User user);

    int count();
}
