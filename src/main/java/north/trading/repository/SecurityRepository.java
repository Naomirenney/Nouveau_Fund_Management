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
                        .bind("ticker", security.ticker())
                        .bind("companyName", security.companyName())
                        .bind("currentPrice", security.currentPrice())
                        .bind("lastUpdated", security.lastUpdated())
                        .execute()
        );
    }

    public Optional<Security> findByTicker(String ticker) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM securities WHERE ticker = :ticker")
                        .bind("ticker", ticker)
                        .map((rs, ctx) -> new Security(
                                rs.getString("ticker"),
                                rs.getString("company_name"),
                                rs.getDouble("current_price"),
                                rs.getTimestamp("last_updated") != null ? rs.getTimestamp("last_updated").toLocalDateTime() : null
                        ))
                        .findOne()
        );
    }

    public List<Security> findAllWatched() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM securities ORDER BY ticker")
                        .map((rs, ctx) -> new Security(
                                rs.getString("ticker"),
                                rs.getString("company_name"),
                                rs.getDouble("current_price"),
                                rs.getTimestamp("last_updated") != null ? rs.getTimestamp("last_updated").toLocalDateTime() : null
                        ))
                        .list()
        );
    }
}