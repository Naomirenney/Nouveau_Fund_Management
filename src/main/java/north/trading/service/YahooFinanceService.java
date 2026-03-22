package north.trading.service;

import north.trading.model.Security;
import north.trading.repository.SecurityRepository;
import yahoofinance.YahooFinance;
import yahoofinance.Stock;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class YahooFinanceService {

    private static final List<String> WATCHLIST = List.of(
            "AAPL", "TSLA", "MSFT", "GOOGL", "AMZN", "NFLX"
    );

    private final SecurityRepository repo;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private int updateCount = 0;

    public YahooFinanceService(SecurityRepository repo) {
        this.repo = repo;
    }

    public void startPolling() {
        System.out.println("✅ Starting Yahoo Finance data polling");
        System.out.println("📊 Watchlist: " + WATCHLIST);
        System.out.println("⏰ First update in 5 seconds, then every 30 minutes");

        // First update in 5 seconds so you see it quickly
        scheduler.scheduleAtFixedRate(this::refreshPrices, 5, 1800, TimeUnit.SECONDS);
    }

    public void manualRefresh() {
        System.out.println("🔄 Manual price refresh requested by user");
        refreshPrices();
    }

    private void refreshPrices() {
        updateCount++;
        System.out.println("\n========================================");
        System.out.println("🔄 Price update cycle #" + updateCount);
        System.out.println("========================================");

        int successCount = 0;

        for (String symbol : WATCHLIST) {
            try {
                System.out.println("📊 Fetching price for " + symbol + "...");
                double price = fetchPrice(symbol);
                String name = getDisplayName(symbol);

                System.out.println("   ✅ Got price: $" + price);

                Security sec = new Security(
                        symbol,
                        name,
                        price,
                        LocalDateTime.now()
                );

                repo.upsert(sec);
                System.out.println("   💾 Saved to database");
                successCount++;

                // Small delay between requests
                Thread.sleep(500);

            } catch (Exception e) {
                System.err.println("❌ Failed to update " + symbol + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("========================================");
        System.out.println("🏁 Update complete: " + successCount + "/" + WATCHLIST.size() + " stocks updated");
        System.out.println("========================================\n");
    }

    private double fetchPrice(String symbol) throws IOException {
        Stock stock = YahooFinance.get(symbol);

        if (stock == null) {
            throw new RuntimeException("No stock data found for " + symbol);
        }

        BigDecimal price = stock.getQuote().getPrice();

        if (price == null) {
            throw new RuntimeException("Price is null for " + symbol);
        }

        return price.doubleValue();
    }

    private String getDisplayName(String symbol) {
        return switch (symbol.toUpperCase()) {
            case "AAPL"  -> "Apple Inc.";
            case "TSLA"  -> "Tesla Inc.";
            case "MSFT"  -> "Microsoft Corporation";
            case "GOOGL" -> "Alphabet Inc.";
            case "AMZN"  -> "Amazon.com Inc.";
            case "NFLX"  -> "Netflix Inc.";
            default      -> symbol;
        };
    }
}