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
import java.util.Map;

public class TradeController {

    private final UserRepository userRepo;
    private final TransactionRepository txRepo;
    private final SecurityRepository securityRepo;
    private final TwelveDataService marketService;
    public TradeController(Javalin app, UserRepository userRepo,
                           TransactionRepository txRepo,
                           SecurityRepository securityRepo,
                           TwelveDataService marketService) {
        this.userRepo = userRepo;
        this.txRepo = txRepo;
        this.securityRepo = securityRepo;
        this.marketService = marketService;

        app.get("/trade/{ticker}", this::showTradePage);
        app.post("/trade/{ticker}", this::executeTrade);
    }

    private void showTradePage(Context ctx) {
        User user = ctx.sessionAttribute("user");
        String ticker = ctx.pathParam("ticker").toUpperCase();

        var securityOpt = securityRepo.findByTicker(ticker);
        if (securityOpt.isEmpty()) {
            ctx.status(404).result("Symbol not found");
            return;
        }

        Security sec = securityOpt.get();

        Map<String, Object> model = new HashMap<>();
        model.put("user", user);
        model.put("security", sec);
        model.put("ticker", ticker);

        ctx.render("trade.html", model);
    }

    // In TradeController, update the executeTrade method:
    private void executeTrade(Context ctx) {
        User user = ctx.sessionAttribute("user");
        String ticker = ctx.pathParam("ticker").toUpperCase();
        String action = ctx.formParam("action");
        String qtyStr = ctx.formParam("quantity");

        if (qtyStr == null || action == null) {
            ctx.status(400).result("Missing parameters");
            return;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(qtyStr);
            if (quantity <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            ctx.status(400).result("Invalid quantity");
            return;
        }

        var securityOpt = securityRepo.findByTicker(ticker);
        if (securityOpt.isEmpty()) {
            ctx.status(404).result("Symbol not found");
            return;
        }

        Security sec = securityOpt.get();
        double price = sec.currentPrice();

        if ("buy".equalsIgnoreCase(action)) {
            double cost = quantity * price;
            if (cost > user.balance()) {
                ctx.status(400).result("Insufficient funds");
                return;
            }

            // Update balance in database
            userRepo.updateBalance(user.id(), user.balance() - cost);

            // Update the user object in session with new balance
            User updatedUser = userRepo.findByUsername(user.username()).get();
            ctx.sessionAttribute("user", updatedUser);

            // Record transaction
            txRepo.create(new Transaction(null, user.id(), ticker, quantity, price, null));

            ctx.redirect("/dashboard?message=Bought+" + quantity + "+" + ticker);
        } else if ("sell".equalsIgnoreCase(action)) {
            double proceeds = quantity * price;

            // Update balance in database
            userRepo.updateBalance(user.id(), user.balance() + proceeds);

            // Update the user object in session with new balance
            User updatedUser = userRepo.findByUsername(user.username()).get();
            ctx.sessionAttribute("user", updatedUser);

            txRepo.create(new Transaction(null, user.id(), ticker, -quantity, price, null));

            ctx.redirect("/dashboard?message=Sold+" + quantity + "+" + ticker);
        } else {
            ctx.status(400).result("Invalid action");
        }
    }
}