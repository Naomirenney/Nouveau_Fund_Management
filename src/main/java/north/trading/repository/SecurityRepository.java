package north.trading.repository;



import org.jdbi.v3.core.Jdbi;
import north.trading.model.Security;

import java.util.List;
import java.util.Optional;

public class SecurityRepository {

    private final Jdbi jdbi;

    public SecurityRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public void upsert(Security security) {
        jdbi.withHandle(handle ->
                handle.createUpdate("""
                    MERGE INTO securities (ticker, company_name, current_price, last_updated)
                    KEY(ticker)
                    VALUES (:ticker, :companyName, :currentPrice, :lastUpdated)
                """)
                        .bindBean(security)
                        .execute()
        );
    }

    public Optional<Security> findByTicker(String ticker) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM securities WHERE ticker = :ticker")
                        .bind("ticker", ticker)
                        .mapToBean(Security.class)
                        .findOne()
        );
    }

    public List<Security> findAllWatched() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM securities ORDER BY ticker")
                        .mapToBean(Security.class)
                        .list()
        );
    }
}
