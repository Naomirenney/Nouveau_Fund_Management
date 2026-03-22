package north.trading.model;

import java.time.LocalDateTime;

public record Security(
        String ticker,
        String companyName,
        Double currentPrice,
        LocalDateTime lastUpdated
) {}