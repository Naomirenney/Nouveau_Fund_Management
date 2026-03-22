package north.trading.repository;

import org.jdbi.v3.core.Jdbi;
import north.trading.model.User;

import java.util.Optional;

public class UserRepository {

    private final Jdbi jdbi;

    public UserRepository(Jdbi jdbi){
        this.jdbi = jdbi;
    }

    // Create a new user with default balance
    public void createUser(String username) {
        jdbi.withHandle(handle ->
                handle.createUpdate("INSERT INTO users (username) VALUES (:username)")
                        .bind("username", username)
                        .execute()
        );
    }

    // Check if user exists
    public boolean existsByUsername(String username) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT COUNT(*) FROM users WHERE username = :username")
                        .bind("username", username)
                        .mapTo(Long.class)
                        .one() > 0
        );
    }

    // Find user by username
    public Optional<User> findByUsername(String username) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT id, username, balance FROM users WHERE username = :username")
                        .bind("username", username)
                        .map((rs, ctx) -> new User(
                                rs.getInt("id"),
                                rs.getString("username"),
                                rs.getDouble("balance")
                        ))
                        .findOne()
        );
    }

    public void updateBalance(int userId, double newBalance) {
        jdbi.withHandle(handle ->
                handle.createUpdate("UPDATE users SET balance = :balance WHERE id = :id")
                        .bind("balance", newBalance)
                        .bind("id", userId)
                        .execute()
        );
    }
}