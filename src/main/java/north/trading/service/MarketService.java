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

public class MarketService {

    private static final List<String> WATCHLIST = List.of(
            "AAPL", "TSLA", "MSFT", "GOOGL", "AMZN", "BTC"
    );

    private final String apiKey;
    private final SecurityRepository repo;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public MarketService(String apiKey, SecurityRepository repo) {
        this.apiKey = apiKey;
        this.repo = repo;
    }

    public void startPolling() {
        scheduler.scheduleAtFixedRate(this::refreshPrices, 2, 60, TimeUnit.SECONDS);
    }

    private void refreshPrices() {
        for (String symbol : WATCHLIST) {
            try {
                double price = fetchPrice(symbol);
                String name = getDisplayName(symbol);

                Security sec = new Security(
                        symbol.toUpperCase(),
                        name,
                        price,
                        LocalDateTime.now()
                );

                repo.upsert(sec);
                System.out.printf("Updated %s → $%.2f%n", symbol, price);
            } catch (Exception e) {
                System.err.println("Failed to update " + symbol + ": " + e.getMessage());
            }
        }
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Alpha Vantage HTTP " + response.statusCode());
        }

        JsonNode root = mapper.readTree(response.body());

        if (symbol.equals("BTC")) {
            JsonNode rateNode = root.path("Realtime Currency Exchange Rate");
            if (rateNode.isMissingNode()) throw new RuntimeException("No BTC rate found");
            return rateNode.path("5. Exchange Rate").asDouble();
        } else {
            JsonNode quote = root.path("Global Quote");
            if (quote.isMissingNode()) throw new RuntimeException("No quote found for " + symbol);
            String priceStr = quote.path("05. price").asText();
            return Double.parseDouble(priceStr);
        }
    }

    private String getDisplayName(String symbol) {
        return switch (symbol.toUpperCase()) {
            case "AAPL"  -> "Apple Inc.";
            case "TSLA"  -> "Tesla Inc.";
            case "MSFT"  -> "Microsoft Corporation";
            case "GOOGL" -> "Alphabet Inc.";
            case "AMZN"  -> "Amazon.com Inc.";
            case "BTC"   -> "Bitcoin (USD)";
            default      -> symbol;
        };
    }
}
