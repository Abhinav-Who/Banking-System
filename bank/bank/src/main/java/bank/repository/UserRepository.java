package bank.repository;

import bank.model.User;

import java.util.Optional;

public interface UserRepository {

    /** Usernames are matched case-insensitively. */
    Optional<User> findByUsername(String username);

    void save(User user);

    int count();
}
