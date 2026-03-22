package north.trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import north.trading.model.Security;
import north.trading.repository.SecurityRepository;
import com.google.common.util.concurrent.RateLimiter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MarketService {

    private static final List<String> WATCHLIST = List.of(
            "AAPL", "TSLA", "MSFT", "GOOGL", "AMZN", "NFLX"
    );

    private final String apiKey;
    private final SecurityRepository repo;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Rate limiter: 1 request every 5 seconds (0.2 per second)
    // This keeps us under the 5 requests per minute limit
    private final RateLimiter rateLimiter = RateLimiter.create(0.2);

    // Track if we've already updated today to avoid exceeding daily limit
    private boolean hasUpdatedToday = false;

    public MarketService(String apiKey, SecurityRepository repo) {
        this.apiKey = apiKey;
        this.repo = repo;
    }

    public void startPolling() {
        // Don't start polling if it's a dummy key
        if (apiKey.equals("dummy")) {
            System.out.println("⚠️ Using dummy API key - prices will not update automatically");
            return;
        }

        System.out.println("✅ Starting market data polling with real API key");

        // Initial delay of 30 seconds to let the app start, then update every 3 hours
        // 3 hours = 10800 seconds
        scheduler.scheduleAtFixedRate(this::refreshPrices, 30, 10800, TimeUnit.SECONDS);
    }

    private void refreshPrices() {
        // Reset daily flag at midnight (simple version - check if we've updated in last 24 hours)
        if (hasUpdatedToday) {
            System.out.println("⏭️ Already updated prices today. Skipping to preserve API quota.");
            return;
        }

        System.out.println("🔄 Starting price update cycle...");

        for (String symbol : WATCHLIST) {
            try {
                // Wait 5 seconds between each API call
                rateLimiter.acquire();

                System.out.println("📊 Fetching price for " + symbol + "...");
                double price = fetchPrice(symbol);
                String name = getDisplayName(symbol);

                Security sec = new Security(
                        symbol.toUpperCase(),
                        name,
                        price,
                        LocalDateTime.now()
                );

                repo.upsert(sec);
                System.out.printf("✅ Updated %s → $%.2f%n", symbol, price);

                // Mark that we've updated today
                hasUpdatedToday = true;

            } catch (Exception e) {
                System.err.println("❌ Failed to update " + symbol + ": " + e.getMessage());
                // If it's a rate limit error, stop the entire cycle
                if (e.getMessage().contains("rate limit") || e.getMessage().contains("25 requests")) {
                    System.err.println("⚠️ Rate limit reached! Stopping this update cycle.");
                    break;
                }
            }
        }

        System.out.println("🏁 Price update cycle complete");
    }

    private double fetchPrice(String symbol) throws Exception {
        String function = symbol.equals("BTC") ? "CURRENCY_EXCHANGE_RATE" : "GLOBAL_QUOTE";
        String from = symbol.equals("BTC") ? "BTC" : symbol;
        String to = symbol.equals("BTC") ? "USD" : null;

        String url;
        if (symbol.equals("BTC")) {
            url = "https://www.alphavantage.co/query?function=%s&from_currency=%s&to_currency=%s&apikey=%s"
                    .formatted(function, from, to, apiKey);
        } else {
            url = "https://www.alphavantage.co/query?function=%s&symbol=%s&apikey=%s"
                    .formatted(function, symbol, apiKey);
        }

        System.out.println("🌐 Calling API for " + symbol);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();

        if (response.statusCode() != 200) {
            throw new RuntimeException("Alpha Vantage HTTP " + response.statusCode() + ": " + body);
        }

        JsonNode root = mapper.readTree(body);

        // Check for error messages or notes (rate limits) from Alpha Vantage
        if (root.has("Error Message")) {
            throw new RuntimeException("Alpha Vantage Error: " + root.get("Error Message").asText());
        }
        if (root.has("Note")) {
            throw new RuntimeException("Alpha Vantage Note (rate limit): " + root.get("Note").asText());
        }
        if (root.has("Information")) {
            throw new RuntimeException("Alpha Vantage Info: " + root.get("Information").asText());
        }

        if (symbol.equals("BTC")) {
            JsonNode rateNode = root.path("Realtime Currency Exchange Rate");
            if (rateNode.isMissingNode()) {
                throw new RuntimeException("No BTC rate found in response: " + body);
            }
            return rateNode.path("5. Exchange Rate").asDouble();
        } else {
            JsonNode quote = root.path("Global Quote");
            if (quote.isMissingNode() || quote.isEmpty()) {
                throw new RuntimeException("No quote found for " + symbol + " in response: " + body);
            }
            JsonNode priceNode = quote.path("05. price");
            if (priceNode.isMissingNode()) {
                throw new RuntimeException("Price node missing for " + symbol + " in 'Global Quote'");
            }
            return Double.parseDouble(priceNode.asText());
        }
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
    public void manualRefresh() {
        System.out.println("🔄 Manual price refresh requested");
        refreshPrices();
    }
}