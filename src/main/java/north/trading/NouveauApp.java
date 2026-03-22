package north.trading;

import io.javalin.Javalin;
import io.javalin.rendering.template.JavalinThymeleaf;
import north.trading.model.User;
import org.eclipse.jetty.server.Authentication;
import org.jdbi.v3.core.Jdbi;
import north.trading.controller.AuthController;
import north.trading.controller.DashboardController;
import north.trading.controller.TradeController;
import north.trading.repository.SecurityRepository;
import north.trading.repository.TransactionRepository;
import north.trading.repository.UserRepository;
import north.trading.service.MarketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLOutput;

public class NouveauApp {
    private static final Logger log = LoggerFactory.getLogger(NouveauApp.class);

    public static void main(String[] args){
        String apiKey= System.getenv("Nouveau_API_KEY");


        if (apiKey ==null || apiKey.trim().isEmpty()) {
            System.err.println("Error: Environment variable is not set");
            System.exit(1);
        }
        //DataBase
        Jdbi jdbi= Jdbi.create("jdbc:h2:mem:nouveau;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "sa", "");

        initializeDatabase(jdbi);

        //Repositories that stores every information
        UserRepository userRepo= new UserRepository(jdbi);
        SecurityRepository securityRepo= new SecurityRepository(jdbi);
        TransactionRepository transactionRepo= new TransactionRepository(jdbi);

        //Get the latest prices of stocks in repo
        MarketService marketService =new MarketService(apiKey,securityRepo);
        marketService.startPolling();

        //Javalin App
        // Javalin App
        Javalin app = Javalin.create(config -> {
            config.fileRenderer(new JavalinThymeleaf());
            config.showJavalinBanner = false;

            // Simple request logger (http logger style)
            config.requestLogger.http((ctx, executionTimeMs) -> {
                String timeFormatted = String.format("%.2f", executionTimeMs);
                log.info("{} {} - {} ({} ms)",
                        ctx.method(),
                        ctx.fullUrl(),
                        ctx.statusCode(),
                        timeFormatted
                );
            });
        });

// Session-based "authentication" using beforeMatched
// (runs for matched routes / endpoints, but not static files unless you use before())
        app.beforeMatched(ctx -> {
            String path = ctx.path();

            // Public paths - allow without login
            if (path.equals("/login") ||
                    path.equals("/register") ||
                    path.startsWith("/static/") ||   // if you later add static files
                    path.equals("/favicon.ico")) {    // optional
                return;  // continue to the handler
            }

            // Protected routes - check session
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.redirect("/login");
                // Important: stop further processing
                return;
            }

            // Optional: you can store user in ctx for easy access in handlers
            // ctx.attribute("currentUser", currentUser);
        });

// Now register your controllers / routes
        new AuthController(app, userRepo);
        new DashboardController(app, userRepo, securityRepo, transactionRepo);
        new TradeController(app, userRepo, transactionRepo, securityRepo, marketService);

        app.start(8080);
        System.out.println("Nouveau Trading Platform started on http://localhost:8080");
    }

    private static void initializeDatabase(Jdbi jdbi) {
        jdbi.useHandle(handle -> {
            // Users table – use GENERATED ALWAYS AS IDENTITY (or BY DEFAULT)
            handle.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                username    VARCHAR(50) UNIQUE NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                balance     DECIMAL(15,2) DEFAULT 100000.00
            )
        """);

            // Securities table – same pattern
            handle.execute("""
            CREATE TABLE IF NOT EXISTS securities (
                ticker        VARCHAR(10) PRIMARY KEY,
                company_name  VARCHAR(100),
                current_price DECIMAL(12,4),
                last_updated  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

            // Transactions table – foreign key stays the same
            handle.execute("""
            CREATE TABLE IF NOT EXISTS transactions (
                id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                user_id     BIGINT NOT NULL,
                ticker      VARCHAR(10) NOT NULL,
                quantity    INT NOT NULL,
                price       DECIMAL(12,4) NOT NULL,
                created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id)
            )
        """);
        });

    }



}
