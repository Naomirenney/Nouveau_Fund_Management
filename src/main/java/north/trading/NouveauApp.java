package north.trading;

import io.javalin.Javalin;
import io.javalin.rendering.template.JavalinThymeleaf;
import north.trading.model.User;
import north.trading.service.TwelveDataService;
import org.jdbi.v3.core.Jdbi;
import north.trading.controller.AuthController;
import north.trading.controller.DashboardController;
import north.trading.controller.TradeController;
import north.trading.repository.SecurityRepository;
import north.trading.repository.TransactionRepository;
import north.trading.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NouveauApp {
    private static final Logger log = LoggerFactory.getLogger(NouveauApp.class);

    public static void main(String[] args){
        // Database
        Jdbi jdbi = Jdbi.create("jdbc:h2:./nouveau_data;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "sa", "");
        initializeDatabase(jdbi);

        // Repositories
        UserRepository userRepo = new UserRepository(jdbi);
        SecurityRepository securityRepo = new SecurityRepository(jdbi);
        TransactionRepository transactionRepo = new TransactionRepository(jdbi);

        // Get Twelve Data API key from .env file
        String apiKey = System.getenv("TWELVEDATA");

        if (apiKey == null) {
            throw new IllegalStateException("API key is not set!");
        }

        TwelveDataService marketService = new TwelveDataService(apiKey, securityRepo);
        marketService.startPolling();

        // Javalin App
        Javalin app = Javalin.create(config -> {
            org.thymeleaf.templateresolver.ClassLoaderTemplateResolver templateResolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
            templateResolver.setPrefix("templates/");
            templateResolver.setSuffix(".html");
            templateResolver.setTemplateMode(org.thymeleaf.templatemode.TemplateMode.HTML);
            templateResolver.setCharacterEncoding("UTF-8");

            org.thymeleaf.TemplateEngine templateEngine = new org.thymeleaf.TemplateEngine();
            templateEngine.setTemplateResolver(templateResolver);
            templateEngine.addDialect(new org.thymeleaf.extras.java8time.dialect.Java8TimeDialect());

            config.fileRenderer(new io.javalin.rendering.template.JavalinThymeleaf(templateEngine));
            config.showJavalinBanner = false;

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

        // Session-based authentication
        app.before(ctx -> {
            String path = ctx.path();
            if (path.equals("/login") ||
                    path.equals("/register") ||
                    path.startsWith("/static/") ||
                    path.equals("/favicon.ico")) {
                return;
            }
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.redirect("/login");
            }
        });

        // Register controllers
        new AuthController(app, userRepo);
        new DashboardController(app, userRepo, securityRepo, transactionRepo, marketService);
        new TradeController(app, userRepo, transactionRepo, securityRepo, marketService);

        app.exception(Exception.class, (e, ctx) -> {
            log.error("Unhandled exception during request: {}", ctx.path(), e);
            ctx.status(500).result("Internal Server Error: " + e.getMessage());
        });

        app.start(6777);
        System.out.println("🚀 Nouveau Trading Platform started on http://localhost:6777");
    }

    // Helper method to read Twelve Data API key from .env file
    private static String getApiKeyFromEnv() {
        // First try system environment
        String value = System.getenv("TWELVE_DATA_API_KEY");
        if (value != null && !value.trim().isEmpty()) {
            System.out.println("✅ Loaded TWELVE_DATA_API_KEY from system environment");
            return value;
        }

        // Then try .env file
        try {
            java.nio.file.Path envPath = java.nio.file.Paths.get(".env");
            if (java.nio.file.Files.exists(envPath)) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(envPath);
                for (String line : lines) {
                    if (line.startsWith("TWELVE_DATA_API_KEY=")) {
                        value = line.split("=", 2)[1].trim();
                        System.out.println("✅ Loaded TWELVE_DATA_API_KEY from .env file");
                        return value;
                    }
                }
            } else {
                System.out.println("⚠️ .env file not found in: " + java.nio.file.Paths.get(".").toAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Failed to read .env file: " + e.getMessage());
        }

        System.out.println("⚠️ TWELVE_DATA_API_KEY not found. Using mock/demo data.");
        System.out.println("💡 Get a free API key at: https://twelvedata.com/");
        System.out.println("💡 Add it to your .env file: TWELVE_DATA_API_KEY=your_key_here");
        return "dummy";
    }

    private static void initializeDatabase(Jdbi jdbi) {
        jdbi.useHandle(handle -> {
            handle.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    username    VARCHAR(50) UNIQUE NOT NULL,
                    balance     DECIMAL(15,2) DEFAULT 100000.00
                )
            """);

            handle.execute("""
                CREATE TABLE IF NOT EXISTS securities (
                    ticker        VARCHAR(10) PRIMARY KEY,
                    company_name  VARCHAR(100),
                    current_price DECIMAL(12,4),
                    last_updated  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

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

            long count = handle.createQuery("SELECT COUNT(*) FROM securities")
                    .mapTo(Long.class)
                    .one();

            if (count == 0) {
                handle.execute("""
        INSERT INTO securities (ticker, company_name, current_price) VALUES
        ('AAPL', 'Apple Inc.', 175.32),
        ('TSLA', 'Tesla Inc.', 245.67),
        ('MSFT', 'Microsoft Corp.', 420.15),
        ('GOOGL', 'Alphabet Inc.', 145.89),
        ('DUOL', 'Duolingo Inc.', 185.50)
    """);
                System.out.println("✅ Seeded initial security data");
            }
        });
    }
}