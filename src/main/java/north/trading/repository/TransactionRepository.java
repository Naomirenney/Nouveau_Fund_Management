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
                        .bindBean(tx)
                        .execute()
        );
    }

    public List<Transaction> findByUserId(int userId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                SELECT * FROM transactions
                WHERE user_id = :userId
                ORDER BY created_at DESC
                """)
                        .bind("userId", userId)
                        .mapToBean(Transaction.class)
                        .list()
        );
    }
}
