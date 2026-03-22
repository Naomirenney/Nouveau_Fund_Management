package north.trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import north.trading.model.Security;
import north.trading.repository.SecurityRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TwelveDataService {

    private static final List<String> WATCHLIST = List.of(
            "AAPL", "TSLA", "MSFT", "GOOGL", "AMZN", "DUOL"
    );

    private final String apiKey;
    private final SecurityRepository repo;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private int apiCallsToday = 0;
    private int apiCallsThisMinute = 0;
    private long lastMinuteReset = System.currentTimeMillis();

    public TwelveDataService(String apiKey, SecurityRepository repo) {
        this.apiKey = apiKey;
        this.repo = repo;
    }

    public void startPolling() {
        if (apiKey.equals("dummy")) {
            System.out.println("⚠️ No Twelve Data API key found. Using seed data only.");
            System.out.println("💡 Add TWELVE_DATA_API_KEY to your .env file");
            return;
        }

        System.out.println("✅ Starting Twelve Data polling (800 requests/day free)");
        System.out.println("📊 Watchlist: " + WATCHLIST);
        System.out.println("⏰ First update in 10 seconds, then every 2 hours");

        scheduler.scheduleAtFixedRate(this::refreshPrices, 0, 7200, TimeUnit.SECONDS);
    }

    public void manualRefresh() {
        System.out.println("\n🔵 MANUAL REFRESH TRIGGERED BY USER");
        System.out.println("Current API calls today: " + apiCallsToday + "/800");
        refreshPrices();
    }

    private synchronized void refreshPrices() {
        if (apiCallsToday >= 800) {
            System.out.println("⚠️ Daily API limit reached. Try again tomorrow.");
            return;
        }

        System.out.println("\n🔄 Starting fast price update cycle...");

        for (String symbol : WATCHLIST) {
            try {
                // Reset minute counter if needed
                long now = System.currentTimeMillis();
                if (now - lastMinuteReset >= 60000) {
                    apiCallsThisMinute = 0;
                    lastMinuteReset = now;
                    System.out.println("📊 New minute started - rate limit reset");
                }

                // Check minute limit
                if (apiCallsThisMinute >= 8) {
                    System.out.println("⏰ Hit 8 calls/minute limit. Waiting 60 seconds...");
                    Thread.sleep(60000);
                    apiCallsThisMinute = 0;
                    lastMinuteReset = System.currentTimeMillis();
                }

                System.out.println("📊 Fetching " + symbol + "...");
                double price = fetchPrice(symbol);

                Security sec = new Security(
                        symbol,
                        getDisplayName(symbol),
                        price,
                        LocalDateTime.now()
                );

                repo.upsert(sec);
                System.out.printf("✅ %s → $%.2f (Call #%d this minute)%n",
                        symbol, price, apiCallsThisMinute + 1);
                apiCallsToday++;
                apiCallsThisMinute++;

                // No delay between stocks - will update as fast as API allows

            } catch (Exception e) {
                System.err.println("❌ Failed to update " + symbol + ": " + e.getMessage());
                if (e.getMessage().contains("limit")) {
                    System.out.println("⚠️ Rate limit reached. Waiting 60 seconds...");
                    try {
                        Thread.sleep(60000);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        }

        System.out.println("✅ Update complete. Total calls today: " + apiCallsToday + "/800\n");
    }

    private double fetchPrice(String symbol) throws Exception {
        String url = String.format(
                "https://api.twelvedata.com/price?symbol=%s&apikey=%s",
                symbol, apiKey
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode());
        }

        JsonNode root = mapper.readTree(response.body());

        if (root.has("price")) {
            return root.get("price").asDouble();
        }

        throw new RuntimeException("Price not found");
    }

    private String getDisplayName(String symbol) {
        return switch (symbol) {
            case "AAPL" -> "Apple Inc.";
            case "TSLA" -> "Tesla Inc.";
            case "MSFT" -> "Microsoft Corporation";
            case "GOOGL" -> "Alphabet Inc.";
            case "AMZN" -> "Amazon.com Inc.";
            case "DUOL" -> "Duolingo Inc.";
            default -> symbol;
        };
    }
    // Add this method for testing
    public void testUpdate() {
        System.out.println("🧪 TEST: Randomly updating prices for testing");

        for (String symbol : WATCHLIST) {
            var existing = repo.findByTicker(symbol);
            if (existing.isPresent()) {
                double oldPrice = existing.get().currentPrice();
                // Random change between -5% and +5%
                double change = (Math.random() - 0.5) * 0.10;
                double newPrice = oldPrice * (1 + change);
                newPrice = Math.round(newPrice * 100.0) / 100.0;

                Security sec = new Security(
                        symbol,
                        getDisplayName(symbol),
                        newPrice,
                        LocalDateTime.now()
                );
                repo.upsert(sec);
                System.out.printf("   TEST: %s: $%.2f → $%.2f (%.2f%%)%n",
                        symbol, oldPrice, newPrice, change * 100);
            }
        }
    }
}
