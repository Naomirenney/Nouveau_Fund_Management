package north.trading.model;

public record User(
        Integer id,
        String username,
        double balance
) {}