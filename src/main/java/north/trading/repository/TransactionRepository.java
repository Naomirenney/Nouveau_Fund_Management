package north.trading.repository;

import org.jdbi.v3.core.Jdbi;
import north.trading.model.Transaction;

import java.util.List;

public class TransactionRepository {

    private final Jdbi jdbi;

    public TransactionRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public void create(Transaction tx) {
        jdbi.withHandle(handle ->
                handle.createUpdate("""
                INSERT INTO transactions (user_id, ticker, quantity, price)
                VALUES (:userId, :ticker, :quantity, :price)
                """)
                        .bind("userId", tx.userId())
                        .bind("ticker", tx.ticker())
                        .bind("quantity", tx.quantity())
                        .bind("price", tx.price())
                        .execute()
        );
    }

    public List<Transaction> findByUserId(Integer userId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                SELECT id, user_id, ticker, quantity, price, created_at 
                FROM transactions
                WHERE user_id = :userId
                ORDER BY created_at DESC
                """)
                        .bind("userId", userId)
                        .map((rs, ctx) -> new Transaction(
                                rs.getInt("id"),
                                rs.getInt("user_id"),
                                rs.getString("ticker"),
                                rs.getInt("quantity"),
                                rs.getDouble("price"),
                                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null
                        ))
                        .list()
        );
    }
}