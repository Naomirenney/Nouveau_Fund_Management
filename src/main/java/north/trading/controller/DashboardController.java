package north.trading.controller;

import io.javalin.Javalin;
import io.javalin.http.Context;
import north.trading.model.Security;
import north.trading.model.Transaction;
import north.trading.model.User;
import north.trading.repository.SecurityRepository;
import north.trading.repository.TransactionRepository;
import north.trading.repository.UserRepository;
import north.trading.service.MarketService;
import north.trading.service.TwelveDataService;
import north.trading.service.YahooFinanceService;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class DashboardController {
    private final UserRepository userRepo;
    private final SecurityRepository securityRepo;
    private final TransactionRepository txRepo;
    private final TwelveDataService marketService;
    public DashboardController(Javalin app, UserRepository userRepo,
                               SecurityRepository securityRepo,
                               TransactionRepository txRepo, TwelveDataService marketService){
        this.userRepo=userRepo;
        this.securityRepo=securityRepo;
        this.txRepo=txRepo;
        this.marketService = marketService;

        app.get("/dashboard", this::showDashboard);

        app.post("/refresh-prices", this::refreshPrices);

        // Add test endpoint
        app.get("/test-update", ctx -> {
            User user = ctx.sessionAttribute("user");
            if (user == null) {
                ctx.redirect("/login");
                return;
            }
            marketService.testUpdate();
            ctx.redirect("/dashboard?message=Test+update+applied!");
        });
    }

    private void refreshPrices(Context ctx) {
        System.out.println("🔵 Refresh button clicked!");  // Add this log
        User user = ctx.sessionAttribute("user");
        if (user == null) {
            System.out.println("❌ No user in session, redirecting to login");
            ctx.redirect("/login");
            return;
        }

        System.out.println("✅ User found: " + user.username());
        System.out.println("🔄 Calling marketService.manualRefresh()...");

        marketService.manualRefresh();

        System.out.println("✅ Manual refresh complete, redirecting to dashboard");
        ctx.redirect("/dashboard?message=Prices+refreshed!");
    }

    // In DashboardController, update the showDashboard method:
    private void showDashboard(Context ctx){
        User sessionUser = ctx.sessionAttribute("user");
        if (sessionUser == null) {
            ctx.redirect("/login");
            return;
        }

        // Get fresh user data from database to ensure latest balance
        User user = userRepo.findByUsername(sessionUser.username()).orElse(sessionUser);
        // Update session with fresh data
        ctx.sessionAttribute("user", user);


        List<Security> securities = securityRepo.findAllWatched();
        List<Transaction> transactions = txRepo.findByUserId(user.id());

        // Check if prices are from API or seed data
        boolean isSeedData = false;
        if (!securities.isEmpty()) {
            // Check if the first security's price is exactly the seed price
            Security firstSec = securities.get(0);
            // If the price is exactly the seed price and hasn't been updated by API
            // You can add a flag or check the last_updated timestamp
            isSeedData = firstSec.currentPrice() == 175.32 && firstSec.ticker().equals("AAPL");
        }

        // Calculate portfolio value correctly using current prices
        double totalCost = transactions.stream()
                .mapToDouble(t -> t.quantity() * t.price())
                .sum();

        double currentValue = transactions.stream()
                .mapToDouble(t -> {
                    var sec = securityRepo.findByTicker(t.ticker()).orElse(null);
                    return sec != null ? t.quantity() * sec.currentPrice() : 0;
                })
                .sum();

        double pnl = currentValue - totalCost;
        double roi = totalCost > 0 ? (pnl / totalCost) * 100 : 0;

        // Get current balance from database (it should update after trades)
        double currentBalance = user.balance();

        Map<String, Object> model = new HashMap<>();
        model.put("user", user);
        model.put("securities", securities);
        model.put("transactions", transactions.subList(0, Math.min(10, transactions.size())));
        model.put("balance", currentBalance);  // Current balance from DB
        model.put("portfolioValue", currentValue);
        model.put("pnl", pnl);
        model.put("roi", roi);
        model.put("isSeedData", isSeedData);  // Flag for data source

        ctx.render("dashboard.html", model);
    }






}
