package bank.service;

import bank.model.User;
import bank.repository.UserRepository;

/** Checks who is performing an operation. Shared by all services. */
final class Actors {

    private final UserRepository users;

    Actors(UserRepository users) {
        this.users = users;
    }

    /** The acting user, who must exist and be active. Returns the stored spelling of the username. */
    User requireActive(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new BankException("You must be signed in to do this.");
        }
        User user = users.findByUsername(actor.trim())
                .orElseThrow(() -> new BankException("You must be signed in as a valid user to do this."));
        if (!user.active()) {
            throw new BankException("User " + user.username() + " is disabled.");
        }
        return user;
    }

    User requireAdmin(String actor) {
        User user = requireActive(actor);
        if (!user.admin()) {
            throw new BankException("Only administrators can manage users.");
        }
        return user;
    }
}
