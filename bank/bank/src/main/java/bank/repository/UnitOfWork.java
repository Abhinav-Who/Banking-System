package bank.repository;

import java.util.function.Supplier;

/**
 * Groups repository changes so they are persisted all together or not at all.
 *
 * <p>If {@code work} throws, or saving to disk fails, every repository is restored to the state
 * it had before {@code execute} started and the exception propagates. Nested calls join the
 * outermost unit of work.
 */
public interface UnitOfWork {

    <T> T execute(Supplier<T> work);

    default void run(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }
}
