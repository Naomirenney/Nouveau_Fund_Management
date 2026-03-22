package north.trading.model;

import java.time.LocalDateTime;

public record Transaction(
        Integer id,
        Integer userId,
        String ticker,
        Integer quantity,
        Double price,
        LocalDateTime createdAt
) {}