package north.trading.repository;

import org.jdbi.v3.core.Jdbi;
import north.trading.model.User;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Optional;

public class UserRepository {

    private final Jdbi jdbi;

    public UserRepository(Jdbi jdbi){
        this.jdbi= jdbi;
    }

    public void createUser(String username, String password){
        String hash= BCrypt.hashpw(password, BCrypt.gensalt(12));
        jdbi.withHandle(handle ->
                handle.createUpdate("INSERT INTO users (username,password_hash) VALUES (:username, :hash)")
                        .bind("username", username)
                        .bind("hash", hash)
                        .execute()
        );

    }
    public Optional<User> findByUsername(String username) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM users WHERE username = :username")
                        .bind("username", username)
                        .mapToBean(User.class)
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
