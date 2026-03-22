package north.trading.controller;

import io.javalin.Javalin;
import io.javalin.http.Context;
import north.trading.model.Security;
import north.trading.model.Transaction;
import north.trading.model.User;
import north.trading.repository.SecurityRepository;
import north.trading.repository.TransactionRepository;
import north.trading.repository.UserRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class DashboardController {
    private final UserRepository userRepo;
    private final SecurityRepository securityRepo;
    private final TransactionRepository txRepo;

    public DashboardController(Javalin app, UserRepository userRepo,
                               SecurityRepository securityRepo,
                               TransactionRepository txRepo){
        this.userRepo=userRepo;
        this.securityRepo=securityRepo;
        this.txRepo=txRepo;

        app.get("/dashboard", this::showDashboard);

    }

    private void showDashboard(Context ctx){
        User user= ctx.sessionAttribute("user");
        if (user==null){
            ctx.redirect("/login");
            return;
        }

        List<Security> securities = securityRepo.findAllWatched();
        List<Transaction> transactions = txRepo.findByUserId(user.id());

        // Very simple portfolio summary
        double totalCost = transactions.stream()
                .mapToDouble(t -> t.quantity() * t.price())
                .sum();

        double currentValue = transactions.stream()
                .mapToDouble(t -> {
                    var sec = securityRepo.findByTicker(t.ticker()).orElse(null);
                    return sec != null ? t.quantity() * sec.currentPrice() : 0;
                })
                .sum();
        //calculate pnl and roi
        double pnl = currentValue - totalCost;
        double roi = totalCost > 0 ? (pnl / totalCost) * 100 : 0;

        Map<String, Object> model = new HashMap<>();
        model.put("user", user);
        model.put("securities", securities);
        model.put("transactions", transactions.subList(0, Math.min(10, transactions.size())));
        model.put("balance", String.format("%.2f", user.balance()));
        model.put("portfolioValue", String.format("%.2f", currentValue));
        model.put("pnl", String.format("%.2f", pnl));
        model.put("roi", String.format("%.2f", roi));
        //put into html
        ctx.render("dashboard.html", model);
    }




}
